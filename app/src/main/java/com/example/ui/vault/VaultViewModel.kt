package com.example.ui.vault

import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PrivateGalleryApplication
import com.example.data.database.entity.VaultEntity
import com.example.data.database.entity.VaultItemEntity
import com.example.data.model.VaultFolder
import com.example.data.preferences.VaultRestoreDestination
import com.example.data.preferences.VaultRestoreMode
import com.example.data.security.BiometricAuthResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class VaultUiState(
    val isUnlocked: Boolean = false,
    val vaults: List<VaultFolder> = emptyList(),
    val currentVault: VaultFolder? = null,
    val itemsInCurrentVault: List<VaultItemEntity> = emptyList(),
    val selectedItemIds: Set<Long> = emptySet(),
    val isCreateFolderDialogOpen: Boolean = false,
    val isRenameFolderDialogOpen: Boolean = false,
    val folderToRename: VaultFolder? = null,
    val isMoveItemsDialogOpen: Boolean = false,
    val isRestoring: Boolean = false,
    val restoreProgressMessage: String? = null,
    val canAuthenticateWithBiometrics: Boolean = true,
    val totalStorageBytes: Long = 0L,
    val isInTrashView: Boolean = false,
    val trashItems: List<VaultItemEntity> = emptyList(),
    val trashStorageBytes: Long = 0L,
    val trashCount: Int = 0
) {
    val isSelectionMode: Boolean get() = selectedItemIds.isNotEmpty()
    val selectedCount: Int get() = selectedItemIds.size
    val selectedItems: List<VaultItemEntity>
        get() {
            val pool = if (isInTrashView) trashItems else itemsInCurrentVault
            return pool.filter { it.id in selectedItemIds }
        }
}

sealed class VaultUiEvent {
    data class ShowSnackbar(val message: String) : VaultUiEvent()
    data object RequestAuth : VaultUiEvent()
}

private data class BaseVaultState(
    val isUnlocked: Boolean,
    val vaults: List<VaultFolder>,
    val currentVaultId: Long?,
    val currentItems: List<VaultItemEntity>,
    val selectedIds: Set<Long>
)

private data class DialogsVaultState(
    val isCreateOpen: Boolean,
    val isRenameOpen: Boolean,
    val renameTarget: VaultFolder?,
    val isMoveOpen: Boolean,
    val isRestoring: Boolean,
    val restoreMsg: String?
)

private data class TrashAndStorageState(
    val storage: Long,
    val inTrash: Boolean,
    val trashItems: List<VaultItemEntity>,
    val trashStorage: Long,
    val trashCount: Int
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PrivateGalleryApplication
    private val vaultRepository = app.vaultRepository
    private val sessionManager = app.sessionManager
    private val biometricAuthenticator = app.biometricAuthenticator
    private val vaultTransferManager = app.vaultTransferManager
    private val preferencesRepository = app.preferencesRepository

    private val _currentVaultId = MutableStateFlow<Long?>(null)
    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isCreateDialogOpen = MutableStateFlow(false)
    private val _isRenameDialogOpen = MutableStateFlow(false)
    private val _folderToRename = MutableStateFlow<VaultFolder?>(null)
    private val _isMoveItemsDialogOpen = MutableStateFlow(false)
    private val _isRestoring = MutableStateFlow(false)
    private val _restoreProgressMessage = MutableStateFlow<String?>(null)
    private val _pendingRestoreItems = MutableStateFlow<List<VaultItemEntity>?>(null)
    val pendingRestoreItems: StateFlow<List<VaultItemEntity>?> = _pendingRestoreItems

    private val _isInTrashView = MutableStateFlow(false)
    private val _trashItems = MutableStateFlow<List<VaultItemEntity>>(emptyList())
    private val _totalStorageBytes = MutableStateFlow(0L)
    private val _trashCount = MutableStateFlow(0)
    private val _trashStorageBytes = MutableStateFlow(0L)

    private val _events = MutableSharedFlow<VaultUiEvent>()
    val events: SharedFlow<VaultUiEvent> = _events.asSharedFlow()

    private val _currentVaultItems = MutableStateFlow<List<VaultItemEntity>>(emptyList())

    private fun selectedItemsSnapshot(): List<VaultItemEntity> {
        val pool = if (_isInTrashView.value) _trashItems.value else _currentVaultItems.value
        return pool.filter { it.id in _selectedItemIds.value }
    }

    private fun launchVaultAction(action: suspend () -> Unit) = viewModelScope.launch {
        try { action() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            _events.emit(VaultUiEvent.ShowSnackbar(e.message ?: "Vault operation failed"))
        }
    }

    val uiState: StateFlow<VaultUiState> = combine(
        combine(
            sessionManager.isUnlocked,
            vaultRepository.vaultsFlow,
            _currentVaultId,
            _currentVaultItems,
            _selectedItemIds
        ) { isUnlocked, vaults, currentVaultId, currentItems, selectedIds ->
            BaseVaultState(isUnlocked, vaults, currentVaultId, currentItems, selectedIds)
        },
        combine(
            combine(_isCreateDialogOpen, _isRenameDialogOpen) { create, rename -> Pair(create, rename) },
            _folderToRename,
            _isMoveItemsDialogOpen,
            _isRestoring,
            _restoreProgressMessage
        ) { dialogs, renameTarget, isMoveOpen, isRestoring, restoreMsg ->
            DialogsVaultState(dialogs.first, dialogs.second, renameTarget, isMoveOpen, isRestoring, restoreMsg)
        },
        combine(
            _totalStorageBytes,
            _isInTrashView,
            _trashItems,
            _trashStorageBytes,
            _trashCount
        ) { storage, inTrash, trashItems, trashStorage, trashCount ->
            TrashAndStorageState(storage, inTrash, trashItems, trashStorage, trashCount)
        }
    ) { base, dialogs, trashState ->
        val currentVault = base.vaults.firstOrNull { it.id == base.currentVaultId }

        VaultUiState(
            isUnlocked = base.isUnlocked,
            vaults = base.vaults,
            currentVault = currentVault,
            itemsInCurrentVault = base.currentItems,
            selectedItemIds = base.selectedIds,
            isCreateFolderDialogOpen = dialogs.isCreateOpen,
            isRenameFolderDialogOpen = dialogs.isRenameOpen,
            folderToRename = dialogs.renameTarget,
            isMoveItemsDialogOpen = dialogs.isMoveOpen,
            isRestoring = dialogs.isRestoring,
            restoreProgressMessage = dialogs.restoreMsg,
            canAuthenticateWithBiometrics = biometricAuthenticator.canAuthenticate(),
            totalStorageBytes = trashState.storage,
            isInTrashView = trashState.inTrash,
            trashItems = trashState.trashItems,
            trashStorageBytes = trashState.trashStorage,
            trashCount = trashState.trashCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultUiState()
    )

    init {
        // Observe items in the selected folder
        viewModelScope.launch {
            _currentVaultId.collectLatest { vaultId ->
                _currentVaultItems.value = emptyList()
                if (vaultId != null) {
                    vaultRepository.getItemsInVault(vaultId).collect { items ->
                        _currentVaultItems.value = items
                    }
                } else {
                    _currentVaultItems.value = emptyList()
                }
            }
        }

        viewModelScope.launch {
            vaultRepository.getTotalStorage().collect { _totalStorageBytes.value = it }
        }

        viewModelScope.launch {
            vaultRepository.getTrashItems().collect { _trashItems.value = it }
        }

        viewModelScope.launch {
            vaultRepository.getTrashCount().collect { _trashCount.value = it }
        }

        viewModelScope.launch {
            vaultRepository.getTrashStorage().collect { _trashStorageBytes.value = it }
        }
    }

    fun openTrash() {
        _isInTrashView.value = true
        _currentVaultId.value = null
        _selectedItemIds.value = emptySet()
    }

    fun closeTrash() {
        _isInTrashView.value = false
        _selectedItemIds.value = emptySet()
    }

    fun selectVaultFolder(vaultId: Long) {
        _isInTrashView.value = false
        _currentVaultId.value = vaultId
        _selectedItemIds.value = emptySet()
    }

    fun clearSelectedVaultFolder() {
        _currentVaultId.value = null
        _selectedItemIds.value = emptySet()
    }

    fun authenticate(activity: FragmentActivity) {
        biometricAuthenticator.promptBiometric(
            activity = activity,
            title = "Unlock Private Vault",
            subtitle = "Use your fingerprint or face unlock"
        ) { result ->
            when (result) {
                is BiometricAuthResult.Success -> {
                    sessionManager.unlock()
                }
                is BiometricAuthResult.Error -> {
                    viewModelScope.launch {
                        _events.emit(VaultUiEvent.ShowSnackbar(result.errString))
                    }
                }
                is BiometricAuthResult.Failed -> {
                    viewModelScope.launch {
                        _events.emit(VaultUiEvent.ShowSnackbar("Authentication failed"))
                    }
                }
                is BiometricAuthResult.NotAvailable -> {
                    viewModelScope.launch {
                        _events.emit(VaultUiEvent.ShowSnackbar("Biometrics not enrolled on device"))
                    }
                }
            }
        }
    }

    fun lockNow() {
        sessionManager.lock()
        clearSelectedVaultFolder()
    }

    fun toggleItemSelection(id: Long) {
        val current = _selectedItemIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedItemIds.value = current
    }

    fun setSelectedItems(ids: Set<Long>) {
        val validIds = if (_isInTrashView.value) {
            _trashItems.value.asSequence().map { it.id }.toSet()
        } else {
            _currentVaultItems.value.asSequence().map { it.id }.toSet()
        }
        _selectedItemIds.value = ids.intersect(validIds)
    }

    fun selectAll() {
        val items = if (_isInTrashView.value) _trashItems.value else _currentVaultItems.value
        _selectedItemIds.value = items.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
    }

    fun openCreateFolderDialog() {
        _isCreateDialogOpen.value = true
    }

    fun closeCreateFolderDialog() {
        _isCreateDialogOpen.value = false
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            try {
                vaultRepository.createVault(name)
                _isCreateDialogOpen.value = false
                _events.emit(VaultUiEvent.ShowSnackbar("Folder '$name' created"))
            } catch (e: Exception) {
                _events.emit(VaultUiEvent.ShowSnackbar(e.message ?: "Failed to create folder"))
            }
        }
    }

    fun openRenameFolderDialog(vault: VaultFolder) {
        _folderToRename.value = vault
        _isRenameDialogOpen.value = true
    }

    fun closeRenameFolderDialog() {
        _folderToRename.value = null
        _isRenameDialogOpen.value = false
    }

    fun renameFolder(newName: String) {
        val vault = _folderToRename.value ?: return
        viewModelScope.launch {
            try {
                vaultRepository.renameVault(vault.id, newName)
                closeRenameFolderDialog()
                _events.emit(VaultUiEvent.ShowSnackbar("Renamed to '$newName'"))
            } catch (e: Exception) {
                _events.emit(VaultUiEvent.ShowSnackbar(e.message ?: "Failed to rename folder"))
            }
        }
    }

    fun deleteFolder(vault: VaultFolder) {
        viewModelScope.launch {
            try {
                vaultRepository.deleteVaultToTrash(vault.id)
                if (_currentVaultId.value == vault.id) {
                    clearSelectedVaultFolder()
                }
                _events.emit(VaultUiEvent.ShowSnackbar("Deleted folder '${vault.displayName}'"))
            } catch (e: Exception) {
                _events.emit(VaultUiEvent.ShowSnackbar(e.message ?: "Failed to delete folder"))
            }
        }
    }

    fun restoreSelected() {
        if (_isRestoring.value || _pendingRestoreItems.value != null) return
        val items = selectedItemsSnapshot()
        if (items.isEmpty()) return
        _isRestoring.value = true
        launchVaultAction {
            try {
                val preference = preferencesRepository.vaultRestorePreferenceFlow.first()
                val destination = when (preference.mode) {
                    VaultRestoreMode.ALWAYS_ASK -> {
                        _pendingRestoreItems.value = items
                        return@launchVaultAction
                    }
                    VaultRestoreMode.ORIGINAL_LOCATION -> VaultRestoreDestination.OriginalLocation
                    VaultRestoreMode.SELECTED_ALBUM -> {
                        if (preference.albumRelativePath.isBlank()) {
                            _events.emit(VaultUiEvent.ShowSnackbar("Choose a restore album in Settings"))
                            return@launchVaultAction
                        }
                        VaultRestoreDestination.Album(preference.albumRelativePath)
                    }
                    VaultRestoreMode.PRIVATE_GALLERY -> VaultRestoreDestination.PrivateGallery
                }
                performRestore(items, destination)
            } finally {
                _isRestoring.value = false
            }
        }
    }

    fun cancelRestoreDestination() {
        _pendingRestoreItems.value = null
    }

    fun confirmRestoreDestination(destination: VaultRestoreDestination) {
        val items = _pendingRestoreItems.value ?: return
        _pendingRestoreItems.value = null
        _isRestoring.value = true
        launchVaultAction {
            try {
                performRestore(items, destination)
            } finally {
                _isRestoring.value = false
                _restoreProgressMessage.value = null
            }
        }
    }

    private suspend fun performRestore(items: List<VaultItemEntity>, destination: VaultRestoreDestination) {
        _restoreProgressMessage.value = "Restoring ${items.size} items..."
        try {
            val (restoredCount, err) = vaultTransferManager.restoreItems(items, destination) { current, total ->
                _restoreProgressMessage.value = "Restoring $current of $total..."
            }
            clearSelection()
            val message = when {
                restoredCount == 0 && err != null -> "Restore failed: $err"
                err != null -> "Restored $restoredCount of ${items.size}; remaining in vault: $err"
                else -> "Restored $restoredCount ${if (restoredCount == 1) "item" else "items"}"
            }
            _events.emit(VaultUiEvent.ShowSnackbar(message))
        } finally {
            _restoreProgressMessage.value = null
        }
    }

    fun deleteSelectedItems() {
        val items = selectedItemsSnapshot()
        if (items.isEmpty()) return

        launchVaultAction {
            if (_isInTrashView.value) {
                vaultRepository.deleteItemsPermanently(items)
                val count = items.size
                clearSelection()
                _events.emit(VaultUiEvent.ShowSnackbar("Permanently deleted $count items"))
            } else {
                vaultRepository.moveToTrash(items.map { it.id })
                val count = items.size
                clearSelection()
                _events.emit(VaultUiEvent.ShowSnackbar("Moved $count items to Vault Trash"))
            }
        }
    }

    fun restoreSelectedFromTrash() {
        val items = selectedItemsSnapshot()
        if (items.isEmpty()) return

        launchVaultAction {
            vaultRepository.restoreFromTrash(items.map { it.id })
            val count = items.size
            clearSelection()
            _events.emit(VaultUiEvent.ShowSnackbar("Restored $count items to Vault"))
        }
    }

    fun emptyTrash() {
        launchVaultAction {
            vaultRepository.emptyTrashPermanently()
            clearSelection()
            _events.emit(VaultUiEvent.ShowSnackbar("Vault Trash emptied"))
        }
    }

    fun deletePermanentlySelected() {
        if (!_isInTrashView.value) return
        val items = selectedItemsSnapshot()
        if (items.isEmpty()) return

        launchVaultAction {
            vaultRepository.deleteItemsPermanently(items)
            val count = items.size
            clearSelection()
            _events.emit(VaultUiEvent.ShowSnackbar("Permanently deleted $count items"))
        }
    }

    fun openMoveItemsDialog() {
        _isMoveItemsDialogOpen.value = true
    }

    fun closeMoveItemsDialog() {
        _isMoveItemsDialogOpen.value = false
    }

    fun moveItemsToTargetVault(targetVaultId: Long) {
        val items = selectedItemsSnapshot()
        if (items.isEmpty()) return

        launchVaultAction {
            vaultRepository.moveItemsToVault(items.map { it.id }, targetVaultId)
            _isMoveItemsDialogOpen.value = false
            val count = items.size
            clearSelection()
            _events.emit(VaultUiEvent.ShowSnackbar("Moved $count items"))
        }
    }

    suspend fun loadThumbnailBytes(item: VaultItemEntity): ByteArray? {
        return vaultTransferManager.loadDecryptedThumbnailBytes(item)
    }
}
