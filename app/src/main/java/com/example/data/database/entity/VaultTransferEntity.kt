package com.example.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_transfers")
data class VaultTransferEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceUri: String,
    val destinationVaultId: Long,
    val stagingIdentifier: String,
    val encryptedFileIdentifier: String,
    val originalFileName: String,
    val mimeType: String,
    val mediaType: Int,
    val originalSize: Long,
    val dateTaken: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val originalRelativePath: String = "",
    val state: String, // COPYING, ENCRYPTED, VERIFYING, WAITING_FOR_DELETE, COMPLETED, ROLLBACK_REQUIRED
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
