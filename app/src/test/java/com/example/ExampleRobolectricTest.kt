package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.formatDuration
import com.example.ui.components.formatHeaderDate
import com.example.ui.viewer.formatFileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Zault", appName)
    }

    @Test
    fun `crypto manager persists key across restarts and decrypts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto1 = com.example.data.security.CryptoManager(context, allowTestKeyStorage = true)
        val original = "Sensitive photo bytes across app restarts".toByteArray()

        val encryptedOut = java.io.ByteArrayOutputStream()
        crypto1.encryptStream(java.io.ByteArrayInputStream(original), encryptedOut)
        val cipherBytes = encryptedOut.toByteArray()

        // Simulate app kill & reopen by creating a completely new CryptoManager instance with the same context
        val crypto2 = com.example.data.security.CryptoManager(context, allowTestKeyStorage = true)
        val decryptedOut = java.io.ByteArrayOutputStream()
        crypto2.decryptStream(java.io.ByteArrayInputStream(cipherBytes), decryptedOut)

        org.junit.Assert.assertArrayEquals(original, decryptedOut.toByteArray())
    }

    @Test
    fun `format duration helper`() {
        assertEquals("0:05", formatDuration(5000L))
        assertEquals("1:15", formatDuration(75000L))
        assertEquals("1:01:05", formatDuration(3665000L))
    }

    @Test
    fun `format file size helper`() {
        assertTrue(formatFileSize(1024L).contains("KB"))
        assertTrue(formatFileSize(1048576L).contains("MB"))
    }

    @Test
    fun `crypto manager stream encryption and decryption`() {
        val crypto = com.example.data.security.CryptoManager()
        val originalData = "Hello Secure World! Testing stream encryption and decryption with AES-GCM.".toByteArray()
        val encryptedOut = java.io.ByteArrayOutputStream()
        crypto.encryptStream(java.io.ByteArrayInputStream(originalData), encryptedOut)
        val encryptedBytes = encryptedOut.toByteArray()

        val decryptedOut = java.io.ByteArrayOutputStream()
        crypto.decryptStream(java.io.ByteArrayInputStream(encryptedBytes), decryptedOut)
        val decryptedBytes = decryptedOut.toByteArray()

        org.junit.Assert.assertArrayEquals(originalData, decryptedBytes)
    }

    @Test
    fun `crypto manager large payload multi-chunk stream encryption and decryption`() {
        val crypto = com.example.data.security.CryptoManager()
        // 600 KB payload (spans 3 chunks of 256KB)
        val originalData = ByteArray(600 * 1024)
        java.util.Random(42).nextBytes(originalData)

        val encryptedOut = java.io.ByteArrayOutputStream()
        crypto.encryptStream(java.io.ByteArrayInputStream(originalData), encryptedOut)
        val encryptedBytes = encryptedOut.toByteArray()

        val decryptedOut = java.io.ByteArrayOutputStream()
        crypto.decryptStream(java.io.ByteArrayInputStream(encryptedBytes), decryptedOut)
        val decryptedBytes = decryptedOut.toByteArray()

        org.junit.Assert.assertArrayEquals(originalData, decryptedBytes)
    }

    @Test
    fun `crypto manager byte array encryption and decryption`() {
        val crypto = com.example.data.security.CryptoManager()
        val sample = "Confidential data payload for memory encryption".toByteArray()
        val encrypted = crypto.encryptBytes(sample)
        val decrypted = crypto.decryptBytes(encrypted)
        org.junit.Assert.assertArrayEquals(sample, decrypted)
    }
}
