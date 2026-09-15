package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.database.entity.VaultItemEntity
import kotlinx.coroutines.flow.Flow

data class VaultFolderStats(
    val vaultId: Long,
    val itemCount: Int,
    val totalBytes: Long
)

@Dao
interface VaultItemDao {
    @Query("SELECT * FROM vault_items WHERE isDeleted = 1")
    suspend fun getTrashSnapshot(): List<VaultItemEntity>

    @Query("UPDATE vault_items SET vaultId = :targetId, isDeleted = 1, deletedAt = CASE WHEN isDeleted = 1 THEN deletedAt ELSE :timestamp END WHERE vaultId = :vaultId")
    suspend fun trashFolderContents(vaultId: Long, targetId: Long, timestamp: Long)

    @Query("SELECT * FROM vault_items WHERE vaultId = :vaultId AND isDeleted = 0 ORDER BY dateTaken DESC, createdAt DESC")
    fun getItemsByVault(vaultId: Long): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE isDeleted = 0 ORDER BY dateTaken DESC")
    fun getAllItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getTrashItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT COUNT(*) FROM vault_items WHERE isDeleted = 1")
    fun getTrashCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(originalSize), 0) FROM vault_items WHERE isDeleted = 1")
    fun getTrashStorage(): Flow<Long>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Long): VaultItemEntity?

    @Query("SELECT * FROM vault_items WHERE id IN (:ids)")
    suspend fun getItemsByIds(ids: List<Long>): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE encryptedFileIdentifier = :identifier LIMIT 1")
    suspend fun getItemByEncryptedIdentifier(identifier: String): VaultItemEntity?

    @Query("SELECT COUNT(*) FROM vault_items WHERE vaultId = :vaultId AND isDeleted = 0")
    fun getItemCountInVault(vaultId: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(originalSize), 0) FROM vault_items WHERE vaultId = :vaultId AND isDeleted = 0")
    fun getTotalStorageInVault(vaultId: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(originalSize), 0) FROM vault_items WHERE isDeleted = 0")
    fun getTotalStorageAcrossAllVaults(): Flow<Long>

    @Query("SELECT COUNT(*) FROM vault_items WHERE isDeleted = 0")
    fun getTotalActiveItemsCount(): Flow<Int>

    @Query("SELECT vaultId, COUNT(*) as itemCount, COALESCE(SUM(originalSize), 0) as totalBytes FROM vault_items WHERE isDeleted = 0 GROUP BY vaultId")
    fun getVaultStats(): Flow<List<VaultFolderStats>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: VaultItemEntity): Long

    @Update
    suspend fun updateItem(item: VaultItemEntity)

    @Query("UPDATE vault_items SET vaultId = :targetVaultId WHERE id IN (:itemIds)")
    suspend fun moveItemsToVault(itemIds: List<Long>, targetVaultId: Long)

    @Query("UPDATE vault_items SET isDeleted = 1, deletedAt = :timestamp WHERE id IN (:itemIds)")
    suspend fun moveToTrash(itemIds: List<Long>, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE vault_items SET isDeleted = 0, deletedAt = 0 WHERE id IN (:itemIds)")
    suspend fun restoreFromTrash(itemIds: List<Long>)

    @Delete
    suspend fun deleteItem(item: VaultItemEntity)

    @Delete
    suspend fun deleteItems(items: List<VaultItemEntity>)

    @Query("DELETE FROM vault_items WHERE vaultId = :vaultId")
    suspend fun deleteAllInVault(vaultId: Long)
}
