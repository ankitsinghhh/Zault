package com.example.data.model

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val dateTaken: Long = 0L,
    val dateModified: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val bucketId: Long = 0L,
    val bucketName: String = "",
    val relativePath: String = "",
    val isFavorite: Boolean = false
) {
    val isScreenshot: Boolean
        get() = bucketName.equals("Screenshots", ignoreCase = true) ||
                displayName.contains("screenshot", ignoreCase = true)
}

data class Album(
    val bucketId: Long,
    val name: String,
    val coverUri: Uri?,
    val itemCount: Int
)

data class VaultFolder(
    val id: Long,
    val uuid: String,
    val displayName: String,
    val iconName: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val itemCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val isDefault: Boolean = false
)

data class DecryptedVaultMedia(
    val id: Long,
    val uuid: String,
    val vaultId: Long,
    val originalFileName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val dateTaken: Long,
    val originalSize: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val tempDecryptedFile: java.io.File? = null
)
