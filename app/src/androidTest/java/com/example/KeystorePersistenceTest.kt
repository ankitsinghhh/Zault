package com.example

import android.content.ContextWrapper
import android.content.ContentValues
import android.content.ContentUris
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.security.CryptoManager
import com.example.data.model.MediaItem
import com.example.data.repository.TransferProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises AndroidKeyStore itself; JVM tests cannot reproduce its IV restrictions. */
@RunWith(AndroidJUnit4::class)
class KeystorePersistenceTest {
    @Test fun wrappedKeySurvivesRecreationAndMigratesLegacySeed() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "key-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getFilesDir() = directory }
        try {
            val raw = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            val mask = java.security.MessageDigest.getInstance("SHA-256")
                .digest("ZaultVaultKey_Protected_v2_${context.packageName}".toByteArray())
            File(directory, "vault_dek_seed.bin").writeBytes(ByteArray(32) {
                (raw[it].toInt() xor mask[it].toInt()).toByte()
            })
            val first = CryptoManager(context)
            assertArrayEquals(raw, first.getVaultDataKey().encoded)
            val photo = ByteArray(700_000) { (it % 251).toByte() }
            val encrypted = first.encryptBytes(photo)
            assertFalse(File(directory, "vault_dek_seed.bin").exists())
            assertEquals(60L, File(directory, "vault_dek_v2.bin").length())
            assertArrayEquals(photo, CryptoManager(context).decryptBytes(encrypted))
            val wrapped = File(directory, "vault_dek_v2.bin")
            val damaged = wrapped.readBytes().apply { this[20] = (this[20].toInt() xor 1).toByte() }
            wrapped.writeBytes(damaged)
            try { CryptoManager(context).getVaultDataKey(); fail("Replaced unreadable key") }
            catch (_: IllegalStateException) { }
            assertArrayEquals(damaged, wrapped.readBytes())
        } finally { directory.deleteRecursively() }
    }

    @Test fun realMovePipelineEncryptsAndDecryptsMediaWithoutKeyRecoveryError() = runBlocking<Unit> {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val app = base.applicationContext as PrivateGalleryApplication
        val name = "zault-device-test-${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ZaultTests/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(base.contentResolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values))
        val bytes = java.io.ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.rgb(27, 91, 173))
                compress(Bitmap.CompressFormat.JPEG, 95, output)
                recycle()
            }
            output.toByteArray()
        }
        try {
            base.contentResolver.openOutputStream(uri, "w")!!.use { it.write(bytes) }
            base.contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            val vaultId = app.vaultRepository.createVault("Device pipeline test ${System.currentTimeMillis()}")
            var result: TransferProgress? = null
            app.vaultTransferManager.stageMoveToVault(
                listOf(MediaItem(ContentUris.parseId(uri), uri, name, "image/jpeg", false,
                    sizeBytes = bytes.size.toLong(), width = 32, height = 32,
                    relativePath = "Pictures/ZaultTests/")),
                vaultId, "Device test"
            ) { result = it }
            val awaiting = result as? TransferProgress.AwaitingDeleteConsent
            assertNotNull("Expected delete-consent stage, got $result", awaiting)
            val vaulted = app.vaultRepository.getItemsInVault(vaultId).first().single()
            val decrypted = requireNotNull(app.vaultTransferManager.prepareDecryptedViewerFile(vaulted))
            assertArrayEquals(bytes, decrypted.readBytes())
            assertTrue(app.vaultTransferManager.handleConsentResult(awaiting!!.transactionId, false)
                is TransferProgress.Cancelled)
            assertTrue(app.vaultRepository.getItemsInVault(vaultId).first().isEmpty())
            app.database.vaultDao().getVaultById(vaultId)?.let { app.database.vaultDao().deleteVault(it) }
        } finally {
            base.contentResolver.delete(uri, null, null)
        }
    }
}
