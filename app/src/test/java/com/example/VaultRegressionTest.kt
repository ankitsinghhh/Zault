package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.VaultDatabase
import com.example.data.database.entity.VaultEntity
import com.example.data.database.entity.VaultItemEntity
import com.example.data.repository.VaultRepository
import com.example.data.security.CryptoManager
import com.example.data.security.VaultStorageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VaultRegressionTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `trash retains media through folder deletion and permits recovery`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java).build()
        try {
            val storage = VaultStorageManager(context)
            val repository = VaultRepository(db, storage)
            val folder = db.vaultDao().insertVault(VaultEntity(displayName = "Original"))
            val identifier = storage.generateItemIdentifier()
            val file = storage.getItemFile(identifier).apply { writeBytes(ByteArray(1234)) }
            val id = db.vaultItemDao().insertItem(VaultItemEntity(vaultId = folder,
                encryptedFileIdentifier = identifier, originalFileName = "photo.jpg",
                mimeType = "image/jpeg", mediaType = 1, dateTaken = 10, originalSize = 1200))
            repository.moveToTrash(listOf(id))
            assertTrue(repository.getItemsInVault(folder).first().isEmpty())
            assertEquals(1, repository.getTrashCount().first())
            assertEquals(1234L, repository.getTotalStorage().first())
            repository.deleteVaultToTrash(folder)
            assertTrue(file.exists())
            val trashed = db.vaultItemDao().getItemById(id)!!
            assertTrue(trashed.isDeleted)
            assertNotEquals(folder, trashed.vaultId)
            assertNotNull(db.vaultDao().getVaultById(trashed.vaultId))
            repository.restoreFromTrash(listOf(id))
            assertEquals(id, repository.getItemsInVault(trashed.vaultId).first().single().id)
            try {
                repository.deleteItemsPermanently(listOf(trashed))
                fail("An active item must never be permanently deleted")
            } catch (_: IllegalStateException) { }
            assertTrue(file.exists())
            repository.moveToTrash(listOf(id))
            repository.emptyTrashPermanently()
            assertFalse(file.exists())
            assertNull(db.vaultItemDao().getItemById(id))
        } finally { db.close() }
    }

    @Test fun `truncated and corrupted ciphertext is rejected`() {
        val crypto = CryptoManager()
        val encrypted = crypto.encryptBytes(ByteArray(600 * 1024) { (it % 251).toByte() })
        for (bad in listOf(encrypted.copyOf(encrypted.size - 4),
            encrypted.copyOf(20), encrypted.copyOf().apply { this[30] = (this[30].toInt() xor 1).toByte() })) {
            try { crypto.decryptBytes(bad); fail("Damaged media was accepted") }
            catch (_: Exception) { }
        }
    }

    @Test fun `v3 ciphertext is bound to its associated media identity`() {
        val crypto = CryptoManager()
        val original = ByteArray(700_000) { (it % 239).toByte() }
        val encrypted = crypto.encryptBytes(original, "media/item-a".toByteArray())
        assertArrayEquals(original, crypto.decryptBytes(encrypted, "media/item-a".toByteArray()))
        try {
            crypto.decryptBytes(encrypted, "media/item-b".toByteArray())
            fail("Ciphertext was accepted for the wrong media identity")
        } catch (_: Exception) { }
    }

    @Test fun `test key survives fresh manager and invalid existing key is preserved`() {
        val first = CryptoManager(context, allowTestKeyStorage = true)
        val original = ByteArray(600 * 1024) { (it % 255).toByte() }
        val encrypted = first.encryptBytes(original)
        assertArrayEquals(original, CryptoManager(context, allowTestKeyStorage = true).decryptBytes(encrypted))
        val seed = File(context.filesDir, "vault_dek_seed.bin")
        seed.writeBytes(byteArrayOf(1, 2, 3))
        try {
            CryptoManager(context, allowTestKeyStorage = true).getVaultDataKey()
            fail("Invalid key was silently replaced")
        } catch (_: IllegalStateException) { }
        assertArrayEquals(byteArrayOf(1, 2, 3), seed.readBytes())
    }

    @Test fun `temporary encryption file does not block first key creation`() {
        File(context.filesDir, "vault/items").apply { mkdirs() }
            .resolve("in-progress.tmp").writeBytes(byteArrayOf(1))
        val key = CryptoManager(context, allowTestKeyStorage = true).getVaultDataKey()
        assertEquals(32, key.encoded.size)
    }
}
