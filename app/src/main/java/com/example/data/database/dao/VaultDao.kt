package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.database.entity.VaultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vaults ORDER BY isDefault DESC, createdAt ASC")
    fun getAllVaults(): Flow<List<VaultEntity>>

    @Query("SELECT * FROM vaults WHERE id = :id LIMIT 1")
    suspend fun getVaultById(id: Long): VaultEntity?

    @Query("SELECT * FROM vaults WHERE displayName = :name LIMIT 1")
    suspend fun getVaultByName(name: String): VaultEntity?

    @Query("SELECT COUNT(*) FROM vaults")
    suspend fun getVaultCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVault(vault: VaultEntity): Long

    @Update
    suspend fun updateVault(vault: VaultEntity)

    @Delete
    suspend fun deleteVault(vault: VaultEntity)
}
