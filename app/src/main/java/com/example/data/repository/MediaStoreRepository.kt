package com.example.data.repository

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import com.example.data.model.Album
import com.example.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

data class RestoreAlbumOption(
    val displayName: String,
    val relativePath: String,
    val itemCount: Int,
    val coverUri: Uri?
)

class MediaStoreRepository(private val context: Context) {

    companion object {
        private const val TAG = "MediaStoreRepo"

        /** MediaStore accepts these shared-media roots for the matching collection. */
        fun writableRelativePath(path: String?, isVideo: Boolean): String? {
            val requested = path?.trim()?.replace('\\', '/')?.trim('/') ?: return null
            val roots = if (isVideo) setOf("DCIM", "Movies", "Pictures") else setOf("DCIM", "Pictures")
            val parts = requested.split('/')
            if (requested.isEmpty() || parts.first() !in roots ||
                parts.any { it.isEmpty() || it == "." || it == ".." || it.any { char -> char.code < 32 || char.code == 127 } }) {
                return null
            }
            return "$requested/"
        }
    }

    private val contentResolver: ContentResolver get() = context.contentResolver
    private val refreshTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    /**
     * Flow of all local media (Images & Videos) ordered chronologically.
     * Reacts in real-time to MediaStore changes via ContentObserver and manual refresh calls.
     */
    fun observeAllMedia(): Flow<List<MediaItem>> = observeMediaChanges(::queryAllMedia)

    fun observeTrashMedia(): Flow<List<MediaItem>> = observeMediaChanges(::queryTrashMedia)

    fun getWritableRestoreAlbums(containsPhotos: Boolean, containsVideos: Boolean): List<RestoreAlbumOption> {
        val grouped = linkedMapOf<String, MutableList<MediaItem>>()
        for (item in queryAllMedia()) {
            val path = writableRelativePath(item.relativePath, isVideo = containsVideos)
                ?: continue
            if (containsPhotos && writableRelativePath(path, isVideo = false) == null) continue
            grouped.getOrPut(path) { mutableListOf() }.add(item)
        }
        return grouped.map { (path, media) ->
            RestoreAlbumOption(
                displayName = media.firstOrNull()?.bucketName?.ifBlank { path.trimEnd('/').substringAfterLast('/') }
                    ?: path.trimEnd('/').substringAfterLast('/'),
                relativePath = path,
                itemCount = media.size,
                coverUri = media.firstOrNull()?.uri
            )
        }.sortedWith(compareByDescending<RestoreAlbumOption> { it.itemCount }.thenBy { it.displayName })
    }

    private fun observeMediaChanges(query: () -> List<MediaItem>): Flow<List<MediaItem>> = callbackFlow<Unit> {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                // ContentObserver callbacks run on the main thread. Only signal here;
                // the potentially expensive MediaStore query is performed on Dispatchers.IO.
                trySend(Unit)
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )

        val refreshJob = launch {
            refreshTrigger.collect {
                trySend(Unit)
            }
        }

        // Initial emission
        trySend(Unit)

        awaitClose {
            refreshJob.cancel()
            contentResolver.unregisterContentObserver(observer)
        }
    }
        .conflate()
        .map { query() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    fun getAllMedia(): List<MediaItem> = queryAllMedia()

    fun queryAllMedia(): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()
        mediaList.addAll(queryImages())
        mediaList.addAll(queryVideos())
        // DATE_MODIFIED changes when an old item is restored. DATE_TAKEN is the
        // gallery timeline value and must remain authoritative when it exists.
        return mediaList.sortedWith(
            compareByDescending<MediaItem> {
                it.dateTaken.takeIf { taken -> taken > 0L } ?: it.dateModified * 1000L
            }
                .thenByDescending { it.id }
        )
    }

    fun queryTrashMedia(): List<MediaItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return (queryImages(trashedOnly = true) + queryVideos(trashedOnly = true))
            .sortedWith(compareByDescending<MediaItem> { it.dateTaken }.thenByDescending { it.id })
    }

    private fun queryImages(trashedOnly: Boolean = false): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val projection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projection.add(MediaStore.MediaColumns.IS_FAVORITE)
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "${MediaStore.MediaColumns.IS_TRASHED} = 0 AND ${MediaStore.MediaColumns.IS_PENDING} = 0 AND ${MediaStore.MediaColumns.SIZE} > 0"
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.IS_PENDING} = 0 AND ${MediaStore.MediaColumns.SIZE} > 0"
        } else {
            "${MediaStore.MediaColumns.SIZE} > 0"
        }

        try {
            queryMedia(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                selection,
                sortOrder,
                trashedOnly
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1
                val favoriteCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val displayName = cursor.getString(nameCol) ?: "IMG_$id"
                    val mimeType = cursor.getString(mimeCol) ?: "image/jpeg"
                    val dateTaken = cursor.getLong(dateTakenCol)
                    val dateModified = cursor.getLong(dateModCol)
                    val size = cursor.getLong(sizeCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val bucketId = cursor.getLong(bucketIdCol)
                    val bucketName = cursor.getString(bucketNameCol) ?: "Images"
                    val relativePath = if (relPathCol != -1) {
                        cursor.getString(relPathCol) ?: ""
                    } else {
                        ""
                    }

                    items.add(
                        MediaItem(
                            id = stableMediaId(id, isVideo = false),
                            uri = uri,
                            displayName = displayName,
                            mimeType = mimeType,
                            isVideo = false,
                            dateTaken = if (dateTaken > 0) dateTaken else dateModified * 1000L,
                            dateModified = dateModified,
                            sizeBytes = size,
                            width = width,
                            height = height,
                            bucketId = bucketId,
                            bucketName = bucketName,
                            relativePath = relativePath,
                            isFavorite = favoriteCol >= 0 && cursor.getInt(favoriteCol) == 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying images: ${e.message}")
        }
        return items
    }

    private fun queryVideos(trashedOnly: Boolean = false): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projection.add(MediaStore.MediaColumns.IS_FAVORITE)
        }

        val sortOrder = "${MediaStore.Video.Media.DATE_TAKEN} DESC"
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "${MediaStore.MediaColumns.IS_TRASHED} = 0 AND ${MediaStore.MediaColumns.IS_PENDING} = 0 AND ${MediaStore.MediaColumns.SIZE} > 0"
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.IS_PENDING} = 0 AND ${MediaStore.MediaColumns.SIZE} > 0"
        } else {
            "${MediaStore.MediaColumns.SIZE} > 0"
        }

        try {
            queryMedia(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                selection,
                sortOrder,
                trashedOnly
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1
                val favoriteCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val displayName = cursor.getString(nameCol) ?: "VID_$id"
                    val mimeType = cursor.getString(mimeCol) ?: "video/mp4"
                    val dateTaken = cursor.getLong(dateTakenCol)
                    val dateModified = cursor.getLong(dateModCol)
                    val size = cursor.getLong(sizeCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val duration = cursor.getLong(durationCol)
                    val bucketId = cursor.getLong(bucketIdCol)
                    val bucketName = cursor.getString(bucketNameCol) ?: "Videos"
                    val relativePath = if (relPathCol != -1) {
                        cursor.getString(relPathCol) ?: ""
                    } else {
                        ""
                    }

                    items.add(
                        MediaItem(
                            id = stableMediaId(id, isVideo = true),
                            uri = uri,
                            displayName = displayName,
                            mimeType = mimeType,
                            isVideo = true,
                            durationMs = duration,
                            dateTaken = if (dateTaken > 0) dateTaken else dateModified * 1000L,
                            dateModified = dateModified,
                            sizeBytes = size,
                            width = width,
                            height = height,
                            bucketId = bucketId,
                            bucketName = bucketName,
                            relativePath = relativePath,
                            isFavorite = favoriteCol >= 0 && cursor.getInt(favoriteCol) == 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying videos: ${e.message}")
        }
        return items
    }

    fun deriveAlbums(mediaList: List<MediaItem>): List<Album> {
        val map = linkedMapOf<Long, MutableList<MediaItem>>()
        for (item in mediaList) {
            map.getOrPut(item.bucketId) { mutableListOf() }.add(item)
        }

        return map.map { (bucketId, items) ->
            val first = items.first()
            Album(
                bucketId = bucketId,
                name = first.bucketName.ifBlank { "Album $bucketId" },
                coverUri = first.uri,
                itemCount = items.size
            )
        }.sortedByDescending { it.itemCount }
    }

    /** MediaStore image and video collections have independent numeric ID spaces. */
    private fun stableMediaId(mediaStoreId: Long, isVideo: Boolean): Long =
        (mediaStoreId shl 1) or if (isVideo) 1L else 0L

    private fun queryMedia(
        collection: Uri,
        projection: Array<String>,
        selection: String?,
        sortOrder: String,
        trashedOnly: Boolean
    ): Cursor? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && trashedOnly) {
            val args = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.MediaColumns.SIZE} > 0 AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
                )
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
            }
            return contentResolver.query(collection, projection, args, null)
        }
        return contentResolver.query(collection, projection, selection, null, sortOrder)
    }

    /**
     * Creates an IntentSenderRequest for deleting MediaStore items using official Android API 30+ consent flow.
     */
    fun createDeleteRequest(uris: List<Uri>): IntentSenderRequest? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        } else {
            null
        }
    }

    fun createTrashRequest(uris: List<Uri>, trashed: Boolean): IntentSenderRequest? {
        if (uris.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return IntentSenderRequest.Builder(
            MediaStore.createTrashRequest(contentResolver, uris, trashed).intentSender
        ).build()
    }

    fun createFavoriteRequest(uris: List<Uri>, favorite: Boolean): IntentSenderRequest? {
        if (uris.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return IntentSenderRequest.Builder(
            MediaStore.createFavoriteRequest(contentResolver, uris, favorite).intentSender
        ).build()
    }

    /**
     * Gets absolute file path for a MediaStore Uri if available.
     */
    fun getFilePath(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (idx != -1) cursor.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Direct delete for older Android versions or when app owns the file.
     */
    suspend fun deleteDirect(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val filePath = getFilePath(uri)
            val rows = contentResolver.delete(uri, null, null)
            if (rows > 0) {
                contentResolver.notifyChange(uri, null)
                if (filePath != null) {
                    try {
                        val file = File(filePath)
                        if (file.exists()) {
                            file.delete()
                        }
                        MediaScannerConnection.scanFile(context, arrayOf(filePath), null, null)
                    } catch (ignored: Exception) {}
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct delete failed: ${e.message}")
            false
        }
    }

    /**
     * Checks if a MediaStore item still exists on disk / MediaStore.
     */
    fun checkUriExists(uri: Uri): Boolean {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Inserts a restored media file back into MediaStore.
     * Uses IS_PENDING on Android 10+ so other apps don't read partial data during write.
     * Preserves original relative path (e.g. DCIM/Camera/) and original dates so it
     * appears seamlessly in Google Photos' camera roll and timeline.
     */
    suspend fun insertRestoredMedia(
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
        dateTaken: Long,
        targetRelativePath: String? = null,
        strictTarget: Boolean = false,
        inputStreamProvider: () -> InputStream
    ): Uri? = withContext(Dispatchers.IO) {
        val fallback = if (isVideo) "Movies/PrivateGallery/" else "Pictures/PrivateGallery/"
        val safePath = writableRelativePath(targetRelativePath, isVideo)
        val paths = when {
            safePath != null && strictTarget -> listOf(safePath)
            safePath != null -> listOf(safePath, fallback).distinct()
            targetRelativePath != null && strictTarget -> emptyList()
            else -> listOf(fallback)
        }
        val name = displayName.substringAfterLast('/').substringAfterLast('\\')
            .takeIf { it.isNotBlank() && it != "." && it != ".." }
            ?: if (isVideo) "restored.mp4" else "restored.jpg"
        val type = mimeType.takeIf { it.startsWith(if (isVideo) "video/" else "image/") }
            ?: if (isVideo) "video/mp4" else "image/jpeg"
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        for (relativePath in paths) {
            var insertedUri: Uri? = null
            var legacyFile: File? = null
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, type)
                    if (dateTaken > 0) put(MediaStore.MediaColumns.DATE_TAKEN, dateTaken)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    } else {
                        val directory = File(android.os.Environment.getExternalStorageDirectory(), relativePath)
                        check(directory.exists() || directory.mkdirs()) { "Cannot create restore folder" }
                        var target = File(directory, name)
                        if (target.exists()) target = File(directory, "${System.currentTimeMillis()}_$name")
                        check(target.createNewFile()) { "Cannot create restored file" }
                        legacyFile = target
                        put(MediaStore.MediaColumns.DATA, target.absolutePath)
                    }
                }
                val uri = contentResolver.insert(collection, values) ?: error("MediaStore rejected restore")
                insertedUri = uri
                val expectedDigest = java.security.MessageDigest.getInstance("SHA-256")
                val written = (contentResolver.openOutputStream(uri, "w")
                    ?: error("Unable to open restored file for writing")).use { output ->
                    inputStreamProvider().use { input ->
                        java.security.DigestInputStream(input, expectedDigest).copyTo(output)
                    }
                }
                check(written > 0) { "Restored media is empty" }
                val actualDigest = java.security.MessageDigest.getInstance("SHA-256")
                var verified = 0L
                (contentResolver.openInputStream(uri) ?: error("Cannot verify restored file")).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        actualDigest.update(buffer, 0, count)
                        verified += count
                    }
                }
                check(written == verified && expectedDigest.digest().contentEquals(actualDigest.digest())) {
                    "Restored file verification failed"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    check(contentResolver.update(uri, ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }, null, null) == 1) { "Could not publish restored media" }
                }
                return@withContext uri
            } catch (e: kotlinx.coroutines.CancellationException) {
                insertedUri?.let { runCatching { contentResolver.delete(it, null, null) } }
                legacyFile?.delete()
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Restore into $relativePath failed: ${e.message}")
                insertedUri?.let { runCatching { contentResolver.delete(it, null, null) } }
                legacyFile?.delete()
            }
        }
        null
    }
}
