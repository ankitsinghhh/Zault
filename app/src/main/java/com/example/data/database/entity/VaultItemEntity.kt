package com.example.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "vault_items",
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("vaultId"),
        Index("encryptedFileIdentifier", unique = true)
    ]
)
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val vaultId: Long,
    val encryptedFileIdentifier: String,
    val originalFileName: String,
    val mimeType: String,
    val mediaType: Int, // 1 for Image, 2 for Video
    val dateTaken: Long,
    val originalSize: Long,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val originalRelativePath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedAt: Long = 0L
)
