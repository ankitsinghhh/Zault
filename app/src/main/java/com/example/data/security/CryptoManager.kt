package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoManager(private val context: Context? = null, private val allowTestKeyStorage: Boolean = false) {

    companion object {
        private const val TAG = "CryptoManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "PrivateGalleryMasterKey_v2"
        private const val DEK_FILE_NAME = "vault_dek_v2.bin"
        private const val DEK_SEED_FILE_NAME = "vault_dek_seed.bin"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val CHUNK_SIZE = 256 * 1024 // 256 KB streaming chunks for high throughput & low memory
        private val MAGIC_V2 = byteArrayOf('P'.code.toByte(), 'G'.code.toByte(), 'V'.code.toByte(), 2.toByte())
        private val MAGIC_V3 = byteArrayOf('P'.code.toByte(), 'G'.code.toByte(), 'V'.code.toByte(), 3.toByte())
    }

    private val secureRandom = SecureRandom()

    private val keyStore: KeyStore? = try {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    } catch (e: Exception) {
        Log.w(TAG, "AndroidKeyStore not available (e.g. JVM/Robolectric): ${e.message}")
        null
    }

    @Volatile
    private var cachedDataKey: SecretKey? = null

    @Synchronized
    fun clearCachedDataKey() {
        cachedDataKey = null
    }

    /** Loads the same key after restart; never replaces an unreadable existing key. */
    @Synchronized
    fun getVaultDataKey(): SecretKey {
        cachedDataKey?.let { return it }
        val ctx = context
        if (ctx == null) {
            return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
                .also { cachedDataKey = it }
        }
        val dekFile = File(ctx.filesDir, DEK_FILE_NAME)
        val seedFile = File(ctx.filesDir, DEK_SEED_FILE_NAME)
        val atomicDek = android.util.AtomicFile(dekFile)
        val ks = keyStore
        if (ks == null) {
            check(allowTestKeyStorage) { "Android Keystore is unavailable; vault key was not changed" }
            val bytes = if (seedFile.exists()) readPersistentSeed(seedFile)
                ?: error("Invalid test key") else ByteArray(32).also {
                    secureRandom.nextBytes(it)
                    atomicWrite(seedFile, it.mapIndexed { i, b ->
                        (b.toInt() xor getObfuscationMask()[i].toInt()).toByte()
                    }.toByteArray())
                }
            return SecretKeySpec(bytes, "AES").also { cachedDataKey = it }
        }

        // AtomicFile also recovers an interrupted write from its backup.
        val hasWrappedKey = dekFile.exists() || File(dekFile.path + ".bak").exists()
        var wrappedFailure: Exception? = null
        if (hasWrappedKey) {
            try {
                check(ks.containsAlias(MASTER_KEY_ALIAS)) { "Vault master key is missing" }
                val encrypted = atomicDek.openRead().use { it.readBytes() }
                require(encrypted.size == 60) { "Invalid wrapped vault key" }
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateHardwareMasterKey(),
                    GCMParameterSpec(GCM_TAG_LENGTH_BITS, encrypted.copyOfRange(0, 12)))
                val raw = cipher.doFinal(encrypted.copyOfRange(12, encrypted.size))
                require(raw.size == 32)
                seedFile.delete() // Remove the old obfuscated, unprotected backup after recovery.
                return SecretKeySpec(raw, "AES").also { cachedDataKey = it }
            } catch (e: Exception) {
                wrappedFailure = e
            }
        }
        val legacySeed = readPersistentSeed(seedFile)
        if (legacySeed == null) {
            val existingVaultFiles = File(ctx.filesDir, "vault/items").listFiles()
                ?.any { it.isFile && !it.name.endsWith(".tmp") } == true
            check(!hasWrappedKey && !seedFile.exists() && !existingVaultFiles) {
                "Existing vault key cannot be recovered. No key or media was overwritten. ${wrappedFailure?.message.orEmpty()}"
            }
        }
        val raw = legacySeed ?: ByteArray(32).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Android Keystore must generate its own IV when randomized encryption is required.
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateHardwareMasterKey())
        val wrapped = cipher.doFinal(raw)
        atomicWrite(dekFile, cipher.iv + wrapped)
        // Verify persisted bytes before trusting this key or removing the legacy seed.
        val saved = atomicDek.openRead().use { it.readBytes() }
        val verify = Cipher.getInstance(TRANSFORMATION)
        verify.init(Cipher.DECRYPT_MODE, getOrCreateHardwareMasterKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, saved.copyOfRange(0, 12)))
        check(verify.doFinal(saved.copyOfRange(12, saved.size)).contentEquals(raw))
        seedFile.delete()
        return SecretKeySpec(raw, "AES").also { cachedDataKey = it }
    }

    private fun atomicWrite(file: File, bytes: ByteArray) {
        val atomic = android.util.AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }

    private fun readPersistentSeed(file: File): ByteArray? {
        if (!file.exists() || file.length() != 32L) return null
        val masked = file.readBytes()
        val mask = getObfuscationMask()
        return ByteArray(masked.size) { i -> (masked[i].toInt() xor mask[i].toInt()).toByte() }
    }
    private fun getObfuscationMask(): ByteArray {
        val seedString = "ZaultVaultKey_Protected_v2_${context?.packageName ?: "com.example"}"
        return java.security.MessageDigest.getInstance("SHA-256").digest(seedString.toByteArray())
    }

    /**
     * Backward-compatible alias for existing calls
     */
    fun getOrCreateMasterKey(): SecretKey = getVaultDataKey()

    private fun getOrCreateHardwareMasterKey(): SecretKey {
        val ks = keyStore ?: throw IllegalStateException("AndroidKeyStore not available")
        if (ks.containsAlias(MASTER_KEY_ALIAS)) {
            try {
                val key = (ks.getKey(MASTER_KEY_ALIAS, null) as? SecretKey)
                    ?: (ks.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
                if (key != null) {
                    return key
                }
            } catch (e: Exception) {
                throw IllegalStateException("Existing master key is unavailable; refusing to replace it", e)
            }
            error("Existing master key cannot be loaded; refusing to replace it")
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * High-speed chunked AES-256-GCM stream encryption.
     * Writes 4-byte magic (PGV3), 12-byte base IV, followed by independently authenticated chunks.
     * Highly performant, low memory consumption, instantaneous on modern devices.
     */
    fun encryptStream(input: InputStream, output: OutputStream, associatedData: ByteArray = byteArrayOf()): Long {
        val key = deriveV3FileKey(getVaultDataKey(), associatedData)
        val baseIv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(baseIv)

        // Write Magic Header + Base IV
        output.write(MAGIC_V3)
        output.write(baseIv)

        val buffer = ByteArray(CHUNK_SIZE)
        var totalBytesWritten = (MAGIC_V3.size + baseIv.size).toLong()
        var chunkIndex = 0

        val cipher = Cipher.getInstance(TRANSFORMATION)

        while (true) {
            var bytesRead = 0
            while (bytesRead < CHUNK_SIZE) {
                val r = input.read(buffer, bytesRead, CHUNK_SIZE - bytesRead)
                if (r == -1) break
                bytesRead += r
            }

            if (bytesRead == 0) {
                // V3 authenticates the end of the stream, so a ciphertext cannot be
                // shortened at a valid chunk boundary and accepted as a complete file.
                val chunkIv = deriveChunkIv(baseIv, chunkIndex)
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIv))
                cipher.updateAAD(chunkAad(baseIv, chunkIndex, true, associatedData))
                val terminalTag = cipher.doFinal()
                writeBigEndianInt(output, -terminalTag.size)
                output.write(terminalTag)
                totalBytesWritten += 4 + terminalTag.size
                break
            }

            val chunkIv = deriveChunkIv(baseIv, chunkIndex)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIv))
            cipher.updateAAD(chunkAad(baseIv, chunkIndex, false, associatedData))
            val cipherText = cipher.doFinal(buffer, 0, bytesRead)

            writeBigEndianInt(output, cipherText.size)
            output.write(cipherText)
            totalBytesWritten += 4 + cipherText.size
            chunkIndex++

            if (bytesRead < CHUNK_SIZE) {
                val terminalIv = deriveChunkIv(baseIv, chunkIndex)
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, terminalIv))
                cipher.updateAAD(chunkAad(baseIv, chunkIndex, true, associatedData))
                val terminalTag = cipher.doFinal()
                writeBigEndianInt(output, -terminalTag.size)
                output.write(terminalTag)
                totalBytesWritten += 4 + terminalTag.size
                break
            }
        }
        output.flush()
        return totalBytesWritten
    }

    /**
     * High-speed chunked AES-256-GCM stream decryption.
     * Streams decrypted plaintext chunk-by-chunk without buffer overflow or Conscrypt AEAD bugs.
     * Includes seamless fallback for legacy single-block files.
     */
    fun decryptStream(input: InputStream, output: OutputStream, associatedData: ByteArray = byteArrayOf()): Long {
        val rootKey = getVaultDataKey()
        val header = ByteArray(4)
        val headerRead = readFully(input, header)

        if (headerRead == 4 && header.contentEquals(MAGIC_V3)) {
            val key = deriveV3FileKey(rootKey, associatedData)
            val baseIv = ByteArray(GCM_IV_LENGTH_BYTES)
            require(readFully(input, baseIv) == GCM_IV_LENGTH_BYTES) { "Invalid encrypted file: Incomplete IV" }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            var totalDecrypted = 0L
            var chunkIndex = 0
            var sawTerminal = false
            while (!sawTerminal) {
                val encodedSize = readBigEndianInt(input)
                require(encodedSize != Int.MIN_VALUE && encodedSize != 0) { "Invalid encrypted chunk size" }
                val isTerminal = encodedSize < 0
                val chunkSize = if (isTerminal) -encodedSize else encodedSize
                require(chunkSize in 16..(CHUNK_SIZE + 16)) { "Invalid or truncated encrypted chunk" }
                val cipherBuffer = ByteArray(chunkSize)
                require(readFully(input, cipherBuffer) == chunkSize) { "Truncated chunk at index $chunkIndex" }
                val chunkIv = deriveChunkIv(baseIv, chunkIndex)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIv))
                cipher.updateAAD(chunkAad(baseIv, chunkIndex, isTerminal, associatedData))
                val plainText = cipher.doFinal(cipherBuffer)
                if (isTerminal) {
                    require(plainText.isEmpty()) { "Invalid encrypted stream terminator" }
                    sawTerminal = true
                    require(input.read() == -1) { "Unexpected data after encrypted stream" }
                } else {
                    output.write(plainText)
                    totalDecrypted += plainText.size
                    chunkIndex++
                }
            }
            output.flush()
            return totalDecrypted
        } else if (headerRead == 4 && header.contentEquals(MAGIC_V2)) {
            val key = rootKey
            val baseIv = ByteArray(GCM_IV_LENGTH_BYTES)
            if (readFully(input, baseIv) != GCM_IV_LENGTH_BYTES) {
                throw IllegalArgumentException("Invalid encrypted file: Incomplete IV")
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            var totalDecrypted = 0L
            var chunkIndex = 0

            while (true) {
                val chunkSize = readBigEndianInt(input)
                if (chunkSize == 0) break
                require(chunkSize in 16..(CHUNK_SIZE + 16)) { "Invalid or truncated encrypted chunk" }

                val cipherBuffer = ByteArray(chunkSize)
                if (readFully(input, cipherBuffer) != chunkSize) {
                    throw IllegalArgumentException("Truncated chunk at index $chunkIndex")
                }

                val chunkIv = deriveChunkIv(baseIv, chunkIndex)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIv))
                val plainText = cipher.doFinal(cipherBuffer)

                output.write(plainText)
                totalDecrypted += plainText.size
                chunkIndex++
            }
            output.flush()
            return totalDecrypted
        } else {
            val key = rootKey
            // Legacy V1 fallback
            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            if (headerRead > 0) {
                System.arraycopy(header, 0, iv, 0, headerRead)
            }
            val remainingIv = GCM_IV_LENGTH_BYTES - headerRead
            if (remainingIv > 0) {
                require(readFully(input, iv, headerRead, remainingIv) == remainingIv) { "Incomplete legacy IV" }
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            val remainder = input.readBytes()
            val plainText = cipher.doFinal(remainder)
            output.write(plainText)
            output.flush()
            return plainText.size.toLong()
        }
    }

    fun decryptBytes(encryptedData: ByteArray, associatedData: ByteArray = byteArrayOf()): ByteArray {
        val bais = ByteArrayInputStream(encryptedData)
        val baos = ByteArrayOutputStream(encryptedData.size)
        decryptStream(bais, baos, associatedData)
        return baos.toByteArray()
    }

    fun encryptBytes(plainData: ByteArray, associatedData: ByteArray = byteArrayOf()): ByteArray {
        val bais = ByteArrayInputStream(plainData)
        val baos = ByteArrayOutputStream(plainData.size + 128)
        encryptStream(bais, baos, associatedData)
        return baos.toByteArray()
    }

    private fun deriveChunkIv(baseIv: ByteArray, chunkIndex: Int): ByteArray {
        val iv = baseIv.copyOf()
        iv[8] = (iv[8].toInt() xor ((chunkIndex ushr 24) and 0xFF)).toByte()
        iv[9] = (iv[9].toInt() xor ((chunkIndex ushr 16) and 0xFF)).toByte()
        iv[10] = (iv[10].toInt() xor ((chunkIndex ushr 8) and 0xFF)).toByte()
        iv[11] = (iv[11].toInt() xor (chunkIndex and 0xFF)).toByte()
        return iv
    }

    private fun chunkAad(baseIv: ByteArray, chunkIndex: Int, terminal: Boolean, associatedData: ByteArray): ByteArray {
        return ByteArrayOutputStream(MAGIC_V3.size + baseIv.size + 5 + associatedData.size).use { out ->
            out.write(MAGIC_V3)
            out.write(baseIv)
            out.write(ByteBuffer.allocate(4).putInt(chunkIndex).array())
            out.write(if (terminal) 1 else 0)
            out.write(associatedData)
            out.toByteArray()
        }
    }

    /** RFC 5869 HKDF-SHA256 derivation gives every media/thumbnail identity a
     * distinct AES key while keeping the root DEK wrapped by Android Keystore. */
    private fun deriveV3FileKey(rootKey: SecretKey, associatedData: ByteArray): SecretKey {
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec("PrivateGallery-PGV3-HKDF".toByteArray(), "HmacSHA256"))
        val pseudoRandomKey = hmac.doFinal(rootKey.encoded)
        hmac.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
        val info = "aes-256-gcm-file-key".toByteArray() + associatedData + byteArrayOf(1)
        return SecretKeySpec(hmac.doFinal(info).copyOf(32), "AES")
    }

    fun isEncryptedPayload(data: ByteArray): Boolean =
        data.size >= 4 && (data.copyOfRange(0, 4).contentEquals(MAGIC_V2) ||
            data.copyOfRange(0, 4).contentEquals(MAGIC_V3))

    private fun writeBigEndianInt(out: OutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun readBigEndianInt(input: InputStream): Int {
        val b0 = input.read()
        if (b0 == -1) return -1
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        if ((b1 or b2 or b3) < 0) return -1
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Int {
        var total = 0
        while (total < length) {
            val r = input.read(buffer, offset + total, length - total)
            if (r == -1) break
            total += r
        }
        return total
    }
}
