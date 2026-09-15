package com.example.data.security

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class VaultStorageManager(private val context: Context) {

    companion object {
        private const val TAG = "VaultStorageManager"
        private const val VAULT_DIR = "vault"
        private const val ITEMS_DIR = "items"
        private const val STAGING_DIR = "staging"
        private const val VIEWER_TEMP_DIR = "viewer_temp"
        private const val THUMBS_DIR = "thumbs"
    }

    private val baseVaultDir: File
        get() = File(context.filesDir, VAULT_DIR).apply { if (!exists()) mkdirs() }

    val itemsDir: File
        get() = File(baseVaultDir, ITEMS_DIR).apply { if (!exists()) mkdirs() }

    val stagingDir: File
        get() = File(baseVaultDir, STAGING_DIR).apply { if (!exists()) mkdirs() }

    val viewerTempDir: File
        get() = File(context.cacheDir, VIEWER_TEMP_DIR).apply { if (!exists()) mkdirs() }

    val thumbsDir: File
        get() = File(baseVaultDir, THUMBS_DIR).apply { if (!exists()) mkdirs() }

    fun generateItemIdentifier(): String = UUID.randomUUID().toString()

    fun getItemFile(identifier: String): File = File(itemsDir, identifier)

    fun getStagingFile(stagingId: String): File = File(stagingDir, stagingId)

    fun createStagingFile(): Pair<String, File> {
        val id = "stage_${UUID.randomUUID()}"
        val file = File(stagingDir, id)
        return id to file
    }

    fun promoteStagingToItem(stagingFile: File, itemIdentifier: String): File {
        val destFile = getItemFile(itemIdentifier)
        if (destFile.exists()) {
            destFile.delete()
        }
        val renamed = stagingFile.renameTo(destFile)
        if (!renamed) {
            // Fallback to copy and delete if rename fails across partitions
            stagingFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            stagingFile.delete()
        }
        return destFile
    }

    fun deleteItemFile(identifier: String): Boolean {
        val file = getItemFile(identifier)
        val thumb = File(thumbsDir, "$identifier.thumb")
        val thumbDeleted = !thumb.exists() || thumb.delete()
        if (!thumbDeleted) return false
        val mediaDeleted = !file.exists() || file.delete()
        return thumbDeleted && mediaDeleted
    }
    fun deleteStagingFile(stagingId: String): Boolean {
        val file = getStagingFile(stagingId)
        return if (file.exists()) file.delete() else false
    }

    fun cleanStagingDirectory() {
        try {
            stagingDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning staging: ${e.message}")
        }
    }

    fun cleanTemporaryItemFiles() {
        try {
            itemsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".tmp") }
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning temporary vault items: ${e.message}")
        }
    }

    fun cleanViewerTemp() {
        try {
            viewerTempDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning viewer temp: ${e.message}")
        }
    }

    fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }

    fun getTotalStorageBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.path)
            stat.blockCountLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }

    fun getVaultStorageUsedBytes(): Long {
        return calculateDirSize(itemsDir) + calculateDirSize(thumbsDir) + calculateDirSize(stagingDir)
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }
}
