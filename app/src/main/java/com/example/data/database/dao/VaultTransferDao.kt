package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.database.entity.VaultTransferEntity

@Dao
interface VaultTransferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: VaultTransferEntity): Long

    @Update
    suspend fun updateTransfer(transfer: VaultTransferEntity)

    @Query("SELECT * FROM vault_transfers WHERE id = :id LIMIT 1")
    suspend fun getTransferById(id: Long): VaultTransferEntity?

    @Query("SELECT * FROM vault_transfers WHERE state != 'COMPLETED'")
    suspend fun getPendingTransfers(): List<VaultTransferEntity>

    @Delete
    suspend fun deleteTransfer(transfer: VaultTransferEntity)

    @Query("DELETE FROM vault_transfers WHERE id = :id")
    suspend fun deleteTransferById(id: Long)

    @Query("DELETE FROM vault_transfers WHERE state = 'COMPLETED'")
    suspend fun cleanupCompletedTransfers()
}
