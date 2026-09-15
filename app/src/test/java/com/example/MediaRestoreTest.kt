package com.example

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.MediaStoreRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MediaRestoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun register(provider: MediaProvider) {
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply {
            authority = "media"
            exported = true
        })
        ShadowContentResolver.registerProviderInternal("media", provider)
    }

    private class MediaProvider(private val file: File) : ContentProvider() {
        var failWrites = false
        var failPublish = false
        var corruptRead = false
        var published = false
        var deleted = 0
        val attemptedPaths = mutableListOf<String>()
        override fun onCreate() = true
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            val path = values!!.getAsString(MediaStore.MediaColumns.RELATIVE_PATH)
            attemptedPaths.add(path)
            if (path == "DCIM/Unavailable/") error("Folder rejected by provider")
            return Uri.parse("content://media/external_primary/images/media/123")
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (mode == "w" && failWrites) throw java.io.FileNotFoundException("Write unavailable")
            if (mode == "r" && corruptRead) file.writeBytes(byteArrayOf(0))
            return ParcelFileDescriptor.open(file, if (mode == "w")
                ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
                else ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int {
            if (failPublish) return 0
            published = true
            return 1
        }
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int {
            deleted++
            file.delete()
            return 1
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? = null
        override fun getType(uri: Uri) = "image/jpeg"
    }

    @Test fun `restore retries rejected location and publishes verified bytes`() = runBlocking {
        val file = File(context.cacheDir, "provider.jpg")
        val provider = MediaProvider(file)
        register(provider)
        val bytes = ByteArray(8192) { (it % 251).toByte() }
        val uri = MediaStoreRepository(context).insertRestoredMedia(
            "photo.jpg", "image/jpeg", false, 1234, "DCIM/Unavailable/"
        ) { bytes.inputStream() }
        assertNotNull(org.robolectric.shadows.ShadowLog.getLogsForTag("MediaStoreRepo").joinToString { it.msg }, uri)
        assertEquals(listOf("DCIM/Unavailable/", "Pictures/PrivateGallery/"), provider.attemptedPaths)
        assertArrayEquals(bytes, file.readBytes())
        assertTrue(provider.published)
    }

    @Test fun `chosen album failure never writes into PrivateGallery`() = runBlocking {
        val file = File(context.cacheDir, "strict-provider.jpg")
        val provider = MediaProvider(file)
        register(provider)
        val result = MediaStoreRepository(context).insertRestoredMedia(
            "photo.jpg", "image/jpeg", false, 1234, "DCIM/Unavailable/", strictTarget = true
        ) { byteArrayOf(1, 2, 3).inputStream() }
        assertNull(result)
        assertEquals(listOf("DCIM/Unavailable/"), provider.attemptedPaths)
        assertFalse(file.exists())
    }

    @Test fun `write verification and publish failures clean pending rows`() = runBlocking {
        for (failure in 0..2) {
            val file = File(context.cacheDir, "failed-$failure.jpg")
            val provider = MediaProvider(file).apply {
                failWrites = failure == 0
                corruptRead = failure == 1
                failPublish = failure == 2
            }
            register(provider)
            val result = MediaStoreRepository(context).insertRestoredMedia(
                "photo.jpg", "image/jpeg", false, 0, "Android/media/other.app/"
            ) { byteArrayOf(1, 2, 3, 4).inputStream() }
            assertNull(result)
            assertEquals(listOf("Pictures/PrivateGallery/"), provider.attemptedPaths)
            assertEquals(1, provider.deleted)
            assertFalse(file.exists())
            assertFalse(provider.published)
        }
    }

    @Test fun `vault source is removed only after successful verified restore`() = runBlocking {
        for (failWrite in listOf(true, false)) {
            val db = androidx.room.Room.inMemoryDatabaseBuilder(context,
                com.example.data.database.VaultDatabase::class.java).build()
            try {
                val folder = db.vaultDao().insertVault(com.example.data.database.entity.VaultEntity(displayName = "Vault"))
                val storage = com.example.data.security.VaultStorageManager(context)
                val crypto = com.example.data.security.CryptoManager()
                val payload = ByteArray(1000) { (it % 251).toByte() }
                val identifier = storage.generateItemIdentifier()
                val encrypted = storage.getItemFile(identifier).apply {
                    writeBytes(crypto.encryptBytes(payload, "private-gallery/media/$identifier".toByteArray()))
                }
                val id = db.vaultItemDao().insertItem(com.example.data.database.entity.VaultItemEntity(
                    vaultId = folder, encryptedFileIdentifier = identifier, originalFileName = "photo.jpg",
                    mimeType = "image/jpeg", mediaType = 1, dateTaken = 0, originalSize = payload.size.toLong()))
                val restoredFile = File(context.cacheDir, "transfer-$failWrite.jpg")
                register(MediaProvider(restoredFile).apply { failWrites = failWrite })
                val transfer = com.example.data.repository.VaultTransferManager(context, db, crypto,
                    storage, MediaStoreRepository(context))
                val (count, error) = transfer.restoreItems(listOf(db.vaultItemDao().getItemById(id)!!)) { _, _ -> }
                if (failWrite) {
                    assertEquals(0, count)
                    assertNotNull(error)
                    assertTrue(encrypted.exists())
                    assertNotNull(db.vaultItemDao().getItemById(id))
                } else {
                    assertEquals(1, count)
                    assertNull(error)
                    assertFalse(encrypted.exists())
                    assertNull(db.vaultItemDao().getItemById(id))
                    assertArrayEquals(payload, restoredFile.readBytes())
                }
            } finally { db.close() }
        }
    }
}
