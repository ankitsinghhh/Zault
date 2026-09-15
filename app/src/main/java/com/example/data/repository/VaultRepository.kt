package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.database.VaultDatabase
import com.example.data.database.entity.VaultEntity
import com.example.data.database.entity.VaultItemEntity
import com.example.data.model.VaultFolder
import com.example.data.security.VaultStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class VaultRepository(
    private val database: VaultDatabase,
    private val storageManager: VaultStorageManager
) {
    private val vaultDao = database.vaultDao()
    private val vaultItemDao = database.vaultItemDao()

    val vaultsFlow: Flow<List<VaultFolder>> = combine(
        vaultDao.getAllVaults(),
        vaultItemDao.getVaultStats()
    ) { vaults, statsList ->
        val statsMap = statsList.associateBy { it.vaultId }
        vaults.map { entity ->
            val stat = statsMap[entity.id]
            VaultFolder(
                id = entity.id,
                uuid = entity.uuid,
                displayName = entity.displayName,
                iconName = entity.iconName,
                createdAt = entity.createdAt,
                modifiedAt = entity.modifiedAt,
                itemCount = stat?.itemCount ?: 0,
                totalSizeBytes = stat?.totalBytes ?: 0L,
                isDefault = entity.isDefault
            )
        }
    }.flowOn(Dispatchers.IO)

    fun getItemsInVault(vaultId: Long): Flow<List<VaultItemEntity>> {
        return vaultItemDao.getItemsByVault(vaultId).flowOn(Dispatchers.IO)
    }

    suspend fun getVaultById(vaultId: Long): VaultEntity? = withContext(Dispatchers.IO) {
        vaultDao.getVaultById(vaultId)
    }

    suspend fun getItemById(itemId: Long): VaultItemEntity? = withContext(Dispatchers.IO) {
        vaultItemDao.getItemById(itemId)
    }

    suspend fun createVault(name: String, iconName: String = "folder"): Long = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) throw IllegalArgumentException("Vault name cannot be blank")
        val existing = vaultDao.getVaultByName(trimmed)
        if (existing != null) {
            throw IllegalArgumentException("A locked folder named '$trimmed' already exists")
        }
        vaultDao.insertVault(
            VaultEntity(
                displayName = trimmed,
                iconName = iconName
            )
        )
    }

    suspend fun renameVault(vaultId: Long, newName: String) = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) throw IllegalArgumentException("Vault name cannot be blank")
        val vault = vaultDao.getVaultById(vaultId) ?: return@withContext
        vaultDao.updateVault(vault.copy(displayName = trimmed, modifiedAt = System.currentTimeMillis()))
    }

    suspend fun deleteVaultToTrash(vaultId: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val vault = vaultDao.getVaultById(vaultId) ?: return@withTransaction
            // Reparent trash before deleting the folder so the foreign-key cascade cannot erase it.
            val destination = vaultDao.getAllVaults().let { flow ->
                flow.first()
            }.firstOrNull { it.id != vaultId }
            val destinationId = destination?.id ?: vaultDao.insertVault(
                VaultEntity(displayName = "Recovered items", iconName = "folder")
            )
            vaultItemDao.trashFolderContents(vaultId, destinationId, System.currentTimeMillis())
            vaultDao.deleteVault(vault)
        }
    }
    suspend fun moveItemsToVault(itemIds: List<Long>, targetVaultId: Long) = withContext(Dispatchers.IO) {
        vaultItemDao.moveItemsToVault(itemIds, targetVaultId)
    }

    suspend fun moveToTrash(itemIds: List<Long>) = withContext(Dispatchers.IO) {
        vaultItemDao.moveToTrash(itemIds, System.currentTimeMillis())
    }

    suspend fun restoreFromTrash(itemIds: List<Long>) = withContext(Dispatchers.IO) {
        vaultItemDao.restoreFromTrash(itemIds)
    }

    fun getTrashItems(): Flow<List<VaultItemEntity>> = vaultItemDao.getTrashItems().flowOn(Dispatchers.IO)

    fun getTrashCount(): Flow<Int> = vaultItemDao.getTrashCount().flowOn(Dispatchers.IO)

    fun getTrashStorage(): Flow<Long> = vaultItemDao.getTrashStorage().flowOn(Dispatchers.IO)

    suspend fun emptyTrashPermanently() = withContext(Dispatchers.IO) {
        deleteItemsPermanently(vaultItemDao.getTrashSnapshot())
    }

    suspend fun deleteItemsPermanently(items: List<VaultItemEntity>) = withContext(Dispatchers.IO) {
        for (item in items) {
            val current = vaultItemDao.getItemById(item.id) ?: continue
            check(current.isDeleted) { "Only items in Vault Trash can be permanently deleted" }
            check(storageManager.deleteItemFile(current.encryptedFileIdentifier)) {
                "Unable to delete vault file; item remains in Trash"
            }
            vaultItemDao.deleteItem(current)
        }
        storageManager.cleanViewerTemp()
    }
    fun getTotalStorage(): Flow<Long> = combine(vaultItemDao.getTotalStorageAcrossAllVaults(), vaultItemDao.getTrashStorage()) { _, _ -> storageManager.getVaultStorageUsedBytes() }.flowOn(Dispatchers.IO)
}
