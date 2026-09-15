package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.activity.result.IntentSenderRequest
import com.example.data.database.VaultDatabase
import com.example.data.database.entity.VaultItemEntity
import com.example.data.database.entity.VaultTransferEntity
import com.example.data.model.MediaItem
import com.example.data.security.CryptoManager
import com.example.data.security.VaultStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID

sealed class TransferProgress {
    data class Progress(val current: Int, val total: Int, val currentFileName: String) : TransferProgress()
    data class AwaitingDeleteConsent(
        val intentSenderRequest: IntentSenderRequest,
        val transactionId: String,
        val totalItems: Int
    ) : TransferProgress()
    data class Success(val count: Int, val vaultName: String) : TransferProgress()
    data class Cancelled(val message: String) : TransferProgress()
    data class Error(val message: String) : TransferProgress()
}

class VaultTransferManager(
    private val context: Context,
    private val database: VaultDatabase,
    private val cryptoManager: CryptoManager,
    private val storageManager: VaultStorageManager,
    private val mediaStoreRepository: MediaStoreRepository
) {
    companion object {
        private const val TAG = "VaultTransferManager"
    }

    private val thumbnailMemoryCache = androidx.collection.LruCache<Long, ByteArray>(120)
    private val restoreMutex = Mutex()

    private val vaultItemDao = database.vaultItemDao()
    private val vaultTransferDao = database.vaultTransferDao()

    private enum class SourceStatus { EXISTS, MISSING, UNKNOWN }

    // In-memory tracker for active transaction awaiting delete consent
    private val pendingMoveTransactions = mutableMapOf<String, StagedBatch>()

    data class StagedItem(
        val mediaItem: MediaItem,
        val encryptedFileIdentifier: String,
        val vaultItemEntity: VaultItemEntity,
        val transferEntityId: Long,
        val verifiedPlaintextSize: Long
    )

    data class StagedBatch(
        val transactionId: String,
        val targetVaultId: Long,
        val targetVaultName: String,
        val stagedItems: List<StagedItem>,
        val uris: List<Uri>
    )

    /**
     * Executes the preparation and encryption phase for moving media into a Locked Folder.
     * Staging -> Encryption -> Verification -> Database record.
     * If user consent is required for delete, returns AwaitingDeleteConsent.
     */
    suspend fun stageMoveToVault(
        mediaItems: List<MediaItem>,
        targetVaultId: Long,
        targetVaultName: String,
        onProgress: (TransferProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (mediaItems.isEmpty()) {
            onProgress(TransferProgress.Error("No items selected"))
            return@withContext
        }

        // Initialize/recover the key before creating any temporary item file. A previous
        // implementation created *.tmp first and then mistook it for unrecoverable media.
        try {
            cryptoManager.getVaultDataKey()
        } catch (e: Exception) {
            onProgress(TransferProgress.Error("Failed to initialize vault encryption: ${e.message}"))
            return@withContext
        }

        // Storage sanity check
        val totalBytesNeeded = mediaItems.sumOf { it.sizeBytes }
        val availableBytes = storageManager.getAvailableStorageBytes()
        if (availableBytes in 1 until totalBytesNeeded) {
            onProgress(TransferProgress.Error("Not enough storage to move these items."))
            return@withContext
        }

        val transactionId = UUID.randomUUID().toString()
        val stagedList = mutableListOf<StagedItem>()
        val total = mediaItems.size

        for ((index, item) in mediaItems.withIndex()) {
            onProgress(TransferProgress.Progress(index + 1, total, item.displayName))

            val encryptedIdentifier = storageManager.generateItemIdentifier()
            val tempItemFile = File(storageManager.itemsDir, "$encryptedIdentifier.tmp")
            val finalItemFile = storageManager.getItemFile(encryptedIdentifier)

            val transferRecord = VaultTransferEntity(
                sourceUri = item.uri.toString(),
                destinationVaultId = targetVaultId,
                stagingIdentifier = "",
                encryptedFileIdentifier = encryptedIdentifier,
                originalFileName = item.displayName,
                mimeType = item.mimeType,
                mediaType = if (item.isVideo) 2 else 1,
                originalSize = item.sizeBytes,
                dateTaken = item.dateTaken,
                width = item.width,
                height = item.height,
                durationMs = item.durationMs,
                originalRelativePath = item.relativePath,
                state = "COPYING"
            )

            val transferId = vaultTransferDao.insertTransfer(transferRecord)

            var insertedItem: VaultItemEntity? = null
            try {
                // Read original content from MediaStore
                val inputStream: InputStream? = context.contentResolver.openInputStream(item.uri)
                if (inputStream == null) {
                    throw IllegalStateException("Cannot read source media stream")
                }

                // Pre-generate and cache thumbnail for instant locked folder grid display
                saveThumbnail(item, encryptedIdentifier)

                // Encrypt directly to final directory with .tmp extension
                val sourceDigest = MessageDigest.getInstance("SHA-256")
                val bytesWritten = FileOutputStream(tempItemFile).use { output ->
                    inputStream.use { input ->
                        val written = cryptoManager.encryptStream(
                            DigestInputStream(input, sourceDigest),
                            output,
                            mediaAad(encryptedIdentifier)
                        )
                        output.fd.sync()
                        written
                    }
                }

                // Read the complete staged ciphertext back, authenticate every chunk and
                // compare its plaintext digest before Android is allowed to delete the source.
                if (tempItemFile.length() < 16L || bytesWritten < 16L) {
                    throw IllegalStateException("Encrypted file verification failed")
                }
                val verifiedPlaintextSize = verifyEncryptedFile(
                    tempItemFile,
                    encryptedIdentifier,
                    sourceDigest.digest(),
                    item.sizeBytes
                )

                // Instant atomic rename in same directory
                if (!tempItemFile.renameTo(finalItemFile)) {
                    tempItemFile.copyTo(finalItemFile, overwrite = true)
                    tempItemFile.delete()
                }

                // Update transfer state
                vaultTransferDao.updateTransfer(
                    transferRecord.copy(
                        id = transferId,
                        originalSize = verifiedPlaintextSize,
                        state = "ENCRYPTED"
                    )
                )

                // Insert into VaultItem database
                val vaultItem = VaultItemEntity(
                    vaultId = targetVaultId,
                    encryptedFileIdentifier = encryptedIdentifier,
                    originalFileName = item.displayName,
                    mimeType = item.mimeType,
                    mediaType = if (item.isVideo) 2 else 1,
                    dateTaken = item.dateTaken,
                    originalSize = verifiedPlaintextSize,
                    width = item.width,
                    height = item.height,
                    durationMs = item.durationMs,
                    originalRelativePath = item.relativePath
                )
                val insertedItemId = vaultItemDao.insertItem(vaultItem)
                insertedItem = vaultItem.copy(id = insertedItemId)

                vaultTransferDao.updateTransfer(
                    transferRecord.copy(
                        id = transferId,
                        originalSize = verifiedPlaintextSize,
                        state = "WAITING_FOR_DELETE"
                    )
                )

                stagedList.add(
                    StagedItem(
                        mediaItem = item,
                        encryptedFileIdentifier = encryptedIdentifier,
                        vaultItemEntity = insertedItem,
                        transferEntityId = transferId,
                        verifiedPlaintextSize = verifiedPlaintextSize
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error staging media item ${item.displayName}: ${e.message}")
                insertedItem?.let { runCatching { vaultItemDao.deleteItem(it) } }
                tempItemFile.delete()
                storageManager.deleteItemFile(encryptedIdentifier)
                vaultTransferDao.deleteTransferById(transferId)

                // Roll back any previously staged items in this batch
                rollbackBatch(stagedList)
                onProgress(TransferProgress.Error("Failed to lock media: ${e.message}"))
                return@withContext
            }
        }

        val stagedBatch = StagedBatch(
            transactionId = transactionId,
            targetVaultId = targetVaultId,
            targetVaultName = targetVaultName,
            stagedItems = stagedList,
            uris = stagedList.map { it.mediaItem.uri }
        )

        // Store batch in memory for consent callback
        synchronized(pendingMoveTransactions) {
            pendingMoveTransactions[transactionId] = stagedBatch
        }

        // Request MediaStore delete consent
        val deleteRequest = try {
            mediaStoreRepository.createDeleteRequest(stagedBatch.uris)
        } catch (e: Exception) {
            Log.w(TAG, "createDeleteRequest error: ${e.message}")
            null
        }

        if (deleteRequest != null) {
            onProgress(
                TransferProgress.AwaitingDeleteConsent(
                    intentSenderRequest = deleteRequest,
                    transactionId = transactionId,
                    totalItems = stagedBatch.stagedItems.size
                )
            )
        } else {
            // Direct delete fallback
            for (uri in stagedBatch.uris) {
                runCatching { mediaStoreRepository.deleteDirect(uri) }
            }
            onProgress(reconcileDeletedSources(transactionId))
        }
    }

    /**
     * Called when the Android system deletion prompt finishes.
     */
    suspend fun handleConsentResult(
        transactionId: String,
        granted: Boolean
    ): TransferProgress = withContext(Dispatchers.IO) {
        val batch = synchronized(pendingMoveTransactions) {
            pendingMoveTransactions[transactionId]
        } ?: return@withContext TransferProgress.Error("Transaction expired or not found")

        if (granted) {
            for (uri in batch.uris) {
                runCatching { mediaStoreRepository.deleteDirect(uri) }
            }
            reconcileDeletedSources(transactionId)
        } else {
            rollbackTransaction(transactionId)
            mediaStoreRepository.refresh()
            TransferProgress.Cancelled("Move cancelled. The original media was not removed.")
        }
    }

    private suspend fun reconcileDeletedSources(transactionId: String): TransferProgress {
        val batch = synchronized(pendingMoveTransactions) {
            pendingMoveTransactions.remove(transactionId)
        } ?: return TransferProgress.Error("Transaction expired or not found")

        var movedCount = 0
        var unresolvedCount = 0
        for (staged in batch.stagedItems) {
            when (sourceStatus(staged.mediaItem.uri)) {
                SourceStatus.EXISTS -> {
                    // Only this item's original survived. Never remove copies whose source
                    // was already deleted by a partially completed Android batch request.
                    if (!rollbackBatch(listOf(staged))) unresolvedCount++
                    continue
                }
                SourceStatus.UNKNOWN -> {
                    // Keep the encrypted copy and journal for startup recovery.
                    unresolvedCount++
                    continue
                }
                SourceStatus.MISSING -> Unit
            }
            try {
                vaultTransferDao.updateTransfer(
                    VaultTransferEntity(
                        id = staged.transferEntityId,
                        sourceUri = staged.mediaItem.uri.toString(),
                        destinationVaultId = batch.targetVaultId,
                        stagingIdentifier = "",
                        encryptedFileIdentifier = staged.encryptedFileIdentifier,
                        originalFileName = staged.mediaItem.displayName,
                        mimeType = staged.mediaItem.mimeType,
                        mediaType = if (staged.mediaItem.isVideo) 2 else 1,
                        originalSize = staged.verifiedPlaintextSize,
                        dateTaken = staged.mediaItem.dateTaken,
                        width = staged.mediaItem.width,
                        height = staged.mediaItem.height,
                        durationMs = staged.mediaItem.durationMs,
                        originalRelativePath = staged.mediaItem.relativePath,
                        state = "COMPLETED"
                    )
                )
                movedCount++
            } catch (e: Exception) {
                // The journal remains pending and recovery will finish this item.
                Log.w(TAG, "Could not commit staged item ${staged.encryptedFileIdentifier}", e)
                unresolvedCount++
            }
        }
        vaultTransferDao.cleanupCompletedTransfers()
        mediaStoreRepository.refresh()
        return if (movedCount == batch.stagedItems.size && unresolvedCount == 0) {
            TransferProgress.Success(movedCount, batch.targetVaultName)
        } else {
            TransferProgress.Error(
                "$movedCount item(s) moved to ${batch.targetVaultName}; " +
                    "${batch.stagedItems.size - movedCount} item(s) kept or awaiting verification."
            )
        }
    }

    private suspend fun rollbackTransaction(transactionId: String) {
        val batch = synchronized(pendingMoveTransactions) {
            pendingMoveTransactions.remove(transactionId)
        } ?: return
        rollbackBatch(batch.stagedItems)
    }

    private suspend fun rollbackBatch(items: List<StagedItem>): Boolean {
        var allRolledBack = true
        for (item in items) {
            try {
                check(storageManager.deleteItemFile(item.encryptedFileIdentifier)) {
                    "Could not remove staged encrypted file"
                }
                vaultItemDao.deleteItem(item.vaultItemEntity)
                vaultTransferDao.deleteTransferById(item.transferEntityId)
            } catch (e: Exception) {
                Log.w(TAG, "Error rolling back staged item: ${e.message}")
                allRolledBack = false
            }
        }
        return allRolledBack
    }

    /**
     * Resolves durable transfer journal entries left by process death. If the shared
     * source still exists, the uncommitted vault copy is rolled back. If Android has
     * already deleted the source, a fully authenticated vault copy is retained or
     * reconstructed. Unknown source status is deliberately left untouched.
     */
    suspend fun recoverInterruptedTransfers() = withContext(Dispatchers.IO) {
        for (transfer in vaultTransferDao.getPendingTransfers()) {
            val sourceStatus = sourceStatus(Uri.parse(transfer.sourceUri))
            if (sourceStatus == SourceStatus.UNKNOWN) continue

            val item = vaultItemDao.getItemByEncryptedIdentifier(transfer.encryptedFileIdentifier)
            val finalFile = storageManager.getItemFile(transfer.encryptedFileIdentifier)
            val tempFile = File(storageManager.itemsDir, "${transfer.encryptedFileIdentifier}.tmp")

            if (sourceStatus == SourceStatus.EXISTS) {
                item?.let { vaultItemDao.deleteItem(it) }
                finalFile.delete()
                tempFile.delete()
                File(storageManager.thumbsDir, "${transfer.encryptedFileIdentifier}.thumb").delete()
                vaultTransferDao.deleteTransferById(transfer.id)
                continue
            }

            val candidate = when {
                finalFile.exists() -> finalFile
                tempFile.exists() -> tempFile
                else -> null
            }
            if (candidate == null || !validateEncryptedFile(
                    candidate,
                    transfer.encryptedFileIdentifier,
                    transfer.originalSize
                )) {
                Log.e(TAG, "Interrupted transfer ${transfer.id} has no verified surviving copy")
                continue
            }

            if (candidate != finalFile) {
                if (!candidate.renameTo(finalFile)) {
                    candidate.copyTo(finalFile, overwrite = true)
                    candidate.delete()
                }
            }
            if (item == null) {
                vaultItemDao.insertItem(
                    VaultItemEntity(
                        vaultId = transfer.destinationVaultId,
                        encryptedFileIdentifier = transfer.encryptedFileIdentifier,
                        originalFileName = transfer.originalFileName,
                        mimeType = transfer.mimeType,
                        mediaType = transfer.mediaType,
                        dateTaken = transfer.dateTaken,
                        originalSize = transfer.originalSize,
                        width = transfer.width,
                        height = transfer.height,
                        durationMs = transfer.durationMs,
                        originalRelativePath = transfer.originalRelativePath
                    )
                )
            }
            vaultTransferDao.deleteTransferById(transfer.id)
        }
    }

    /**
     * Restores media items from Locked Folder back into Android MediaStore.
     */
    suspend fun restoreItems(
        items: List<VaultItemEntity>,
        destination: com.example.data.preferences.VaultRestoreDestination = com.example.data.preferences.VaultRestoreDestination.PrivateGallery,
        onProgress: (Int, Int) -> Unit
    ): Pair<Int, String?> = restoreMutex.withLock {
        withContext(Dispatchers.IO) {
        var restoredCount = 0
        var errorMsg: String? = null

        for ((index, item) in items.withIndex()) {
            onProgress(index + 1, items.size)
            if (destination == com.example.data.preferences.VaultRestoreDestination.OriginalLocation &&
                item.originalRelativePath.isBlank()) {
                errorMsg = "Original folder was not recorded for ${item.originalFileName}"
                continue
            }
            val requestedPath = when (destination) {
                com.example.data.preferences.VaultRestoreDestination.OriginalLocation -> item.originalRelativePath
                is com.example.data.preferences.VaultRestoreDestination.Album -> destination.relativePath
                com.example.data.preferences.VaultRestoreDestination.PrivateGallery -> null
            }
            val targetPath = requestedPath?.let {
                MediaStoreRepository.writableRelativePath(it, item.mediaType == 2)
            }
            if (requestedPath != null && targetPath == null) {
                errorMsg = "${item.originalFileName} cannot be restored to that album"
                continue
            }
            val encryptedFile = storageManager.getItemFile(item.encryptedFileIdentifier)
            if (!encryptedFile.exists()) {
                errorMsg = "Encrypted file not found on disk"
                continue
            }

            var decryptedTemp: File? = null
            try {
                decryptedTemp = File(context.cacheDir, "restore_${UUID.randomUUID()}")
                FileOutputStream(decryptedTemp).use { out ->
                    FileInputStream(encryptedFile).use { input ->
                        cryptoManager.decryptStream(input, out, mediaAad(item.encryptedFileIdentifier))
                    }
                }

                if (!decryptedTemp.exists() || decryptedTemp.length() == 0L ||
                    (item.originalSize > 0 && decryptedTemp.length() != item.originalSize)) {
                    errorMsg = "Failed to decrypt ${item.originalFileName}"
                    continue
                }

                // Explicit destinations stay explicit. A failed insert leaves the vault copy intact.
                val restoredUri = mediaStoreRepository.insertRestoredMedia(
                    displayName = item.originalFileName,
                    mimeType = item.mimeType,
                    isVideo = item.mediaType == 2,
                    dateTaken = item.dateTaken,
                    targetRelativePath = targetPath,
                    strictTarget = true
                ) {
                    FileInputStream(decryptedTemp)
                }

                if (restoredUri != null && mediaStoreRepository.checkUriExists(restoredUri)) {
                    // Complete the move only when the encrypted source is removed too.
                    // If that fails, remove our newly-created MediaStore copy and keep
                    // the vault record so retrying cannot silently create duplicates.
                    if (storageManager.deleteItemFile(item.encryptedFileIdentifier)) {
                        vaultItemDao.deleteItem(item)
                        restoredCount++
                    } else {
                        runCatching { context.contentResolver.delete(restoredUri, null, null) }
                        errorMsg = "Restored copy was rolled back because the vault source could not be removed"
                    }
                } else {
                    errorMsg = "Unable to write restored file to MediaStore"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed restoring item ${item.originalFileName}: ${e.message}")
                errorMsg = e.message ?: "Failed to restore item"
            } finally {
                try {
                    decryptedTemp?.delete()
                } catch (ignored: Exception) {}
            }
        }

        if (restoredCount > 0) {
            mediaStoreRepository.refresh()
        }
            restoredCount to errorMsg
        }
    }

    private fun saveThumbnail(item: MediaItem, encryptedIdentifier: String) {
        val thumbFile = File(storageManager.thumbsDir, "$encryptedIdentifier.thumb")
        var bitmap: Bitmap? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    bitmap = context.contentResolver.loadThumbnail(item.uri, Size(360, 360), null)
                } catch (ignored: Exception) {}
            }
            if (bitmap == null) {
                if (item.isVideo) {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, item.uri)
                    bitmap = retriever.getFrameAtTime(0)
                    retriever.release()
                } else {
                    context.contentResolver.openInputStream(item.uri)?.use { input ->
                        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                        bitmap = BitmapFactory.decodeStream(input, null, opts)
                    }
                }
            }
            if (bitmap != null) {
                val plainThumbnail = ByteArrayOutputStream().use { out ->
                    bitmap!!.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.toByteArray()
                }
                persistEncryptedThumbnail(thumbFile, encryptedIdentifier, plainThumbnail)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed pre-generating thumbnail for ${item.displayName}: ${e.message}")
        } finally {
            bitmap?.recycle()
        }
    }

    /**
     * Decrypts a vault media item to a temporary private file in cacheDir
     * for full-screen viewing or ExoPlayer video playback.
     * Caches the decrypted file for fast swiping during the viewer session.
     */
    suspend fun prepareDecryptedViewerFile(item: VaultItemEntity): File? = withContext(Dispatchers.IO) {
        val encryptedFile = storageManager.getItemFile(item.encryptedFileIdentifier)
        if (!encryptedFile.exists()) return@withContext null

        val extension = when {
            item.mimeType.contains("png") -> ".png"
            item.mimeType.contains("mp4") -> ".mp4"
            item.mimeType.contains("webp") -> ".webp"
            else -> ".jpg"
        }

        val cachedViewerFile = File(storageManager.viewerTempDir, "view_${item.id}_${item.encryptedFileIdentifier}$extension")
        if (cachedViewerFile.exists() && cachedViewerFile.length() > 0L &&
            (item.originalSize <= 0 || cachedViewerFile.length() == item.originalSize)) {
            return@withContext cachedViewerFile
        }

        val tempFile = File(storageManager.viewerTempDir, "tmp_${UUID.randomUUID()}$extension")
        try {
            FileOutputStream(tempFile).use { out ->
                FileInputStream(encryptedFile).use { input ->
                    cryptoManager.decryptStream(input, out, mediaAad(item.encryptedFileIdentifier))
                }
            }
            if (tempFile.exists() && tempFile.length() > 0L &&
                (item.originalSize <= 0 || tempFile.length() == item.originalSize)) {
                if (!tempFile.renameTo(cachedViewerFile)) {
                    tempFile.copyTo(cachedViewerFile, overwrite = true)
                    tempFile.delete()
                }
                cachedViewerFile
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting viewer file: ${e.message}", e)
            tempFile.delete()
            null
        }
    }

    /**
     * Fast retrieval of decrypted thumbnail bytes for displaying securely in Compose grid.
     */
    suspend fun loadDecryptedThumbnailBytes(item: VaultItemEntity): ByteArray? = withContext(Dispatchers.IO) {
        thumbnailMemoryCache.get(item.id)?.let { return@withContext it }

        val thumbFile = File(storageManager.thumbsDir, "${item.encryptedFileIdentifier}.thumb")
        if (thumbFile.exists() && thumbFile.length() > 0L) {
            try {
                val stored = thumbFile.readBytes()
                val bytes = if (cryptoManager.isEncryptedPayload(stored)) {
                    cryptoManager.decryptBytes(stored, thumbnailAad(item.encryptedFileIdentifier))
                } else {
                    // Old thumbnails are JPEGs. A damaged encrypted header must never
                    // be treated as plaintext and silently migrated.
                    check(stored.size >= 3 &&
                        stored[0] == 0xFF.toByte() &&
                        stored[1] == 0xD8.toByte() &&
                        stored[2] == 0xFF.toByte()) { "Unknown thumbnail format" }
                    persistEncryptedThumbnail(thumbFile, item.encryptedFileIdentifier, stored)
                    stored
                }
                thumbnailMemoryCache.put(item.id, bytes)
                return@withContext bytes
            } catch (ignored: Exception) {}
        }

        // On-demand thumbnail generation fallback if not pre-cached
        val decryptedFile = prepareDecryptedViewerFile(item) ?: return@withContext null
        var bitmap: Bitmap? = null
        try {
            if (item.mediaType == 2) {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(decryptedFile.absolutePath)
                bitmap = retriever.getFrameAtTime(0)
                retriever.release()
            } else {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                bitmap = BitmapFactory.decodeFile(decryptedFile.absolutePath, opts)
            }
            if (bitmap != null) {
                val bytes = ByteArrayOutputStream().use { out ->
                    bitmap!!.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.toByteArray()
                }
                persistEncryptedThumbnail(thumbFile, item.encryptedFileIdentifier, bytes)
                thumbnailMemoryCache.put(item.id, bytes)
                bytes
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error on-demand thumbnail generation: ${e.message}")
            null
        } finally {
            bitmap?.recycle()
        }
    }

    fun clearSensitiveMemoryCaches() {
        thumbnailMemoryCache.evictAll()
    }

    private fun mediaAad(identifier: String): ByteArray =
        "private-gallery/media/$identifier".toByteArray(Charsets.UTF_8)

    private fun thumbnailAad(identifier: String): ByteArray =
        "private-gallery/thumbnail/$identifier".toByteArray(Charsets.UTF_8)

    private fun persistEncryptedThumbnail(file: File, identifier: String, plaintext: ByteArray) {
        val encrypted = cryptoManager.encryptBytes(plaintext, thumbnailAad(identifier))
        val temp = File(file.parentFile, "${file.name}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temp).use { output ->
                output.write(encrypted)
                output.fd.sync()
            }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        } finally {
            temp.delete()
        }
    }

    private fun verifyEncryptedFile(
        file: File,
        identifier: String,
        expectedDigest: ByteArray,
        expectedSize: Long = 0L
    ): Long {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        val digestSink = object : OutputStream() {
            override fun write(value: Int) {
                digest.update(value.toByte())
                count++
            }

            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                digest.update(buffer, offset, length)
                count += length
            }
        }
        FileInputStream(file).use { input ->
            cryptoManager.decryptStream(input, digestSink, mediaAad(identifier))
        }
        check(expectedSize <= 0L || count == expectedSize) { "Source media size changed while it was being locked" }
        check(digest.digest().contentEquals(expectedDigest)) { "Encrypted media verification failed" }
        return count
    }

    private fun validateEncryptedFile(file: File, identifier: String, expectedSize: Long): Boolean {
        return try {
            var count = 0L
            val sink = object : OutputStream() {
                override fun write(value: Int) { count++ }
                override fun write(buffer: ByteArray, offset: Int, length: Int) { count += length }
            }
            FileInputStream(file).use { input ->
                cryptoManager.decryptStream(input, sink, mediaAad(identifier))
            }
            expectedSize <= 0L || count == expectedSize
        } catch (e: Exception) {
            Log.e(TAG, "Vault ciphertext validation failed for $identifier: ${e.message}")
            false
        }
    }

    private fun sourceStatus(uri: Uri): SourceStatus {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { SourceStatus.EXISTS }
                ?: SourceStatus.MISSING
        } catch (_: FileNotFoundException) {
            SourceStatus.MISSING
        } catch (_: SecurityException) {
            SourceStatus.UNKNOWN
        } catch (_: Exception) {
            SourceStatus.UNKNOWN
        }
    }
}
