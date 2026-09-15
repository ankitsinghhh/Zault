package com.example.ui.photos

import android.app.Application
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PrivateGalleryApplication
import com.example.data.model.MediaItem
import com.example.data.model.VaultFolder
import com.example.data.repository.TransferProgress
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PhotosUiState(
    val mediaItems: List<MediaItem> = emptyList(),
    val selectedItemIds: Set<Long> = emptySet(),
    val gridColumns: Int = 3,
    val isLoading: Boolean = true,
    val vaults: List<VaultFolder> = emptyList(),
    val isMovePickerOpen: Boolean = false,
    val isCreateVaultDialogOpen: Boolean = false,
    val showFirstLockExplanation: Boolean = false,
    val pendingConsentRequest: IntentSenderRequest? = null,
    val activeTransactionId: String? = null,
    val transferStatusMessage: String? = null,
    val isTransferring: Boolean = false
) {
    val isSelectionMode: Boolean get() = selectedItemIds.isNotEmpty()
    val selectedCount: Int get() = selectedItemIds.size
    val selectedItems: List<MediaItem>
        get() = mediaItems.filter { it.id in selectedItemIds }
}

sealed class PhotosUiEvent {
    data class ShowSnackbar(val message: String) : PhotosUiEvent()
    data class LaunchIntentSender(val intentSenderRequest: IntentSenderRequest) : PhotosUiEvent()
}

private sealed interface PendingMediaConsent {
    data class VaultMove(val transactionId: String) : PendingMediaConsent
    data class Trash(val count: Int) : PendingMediaConsent
    data class RestoreFromTrash(val count: Int) : PendingMediaConsent
    data class PermanentDelete(val count: Int) : PendingMediaConsent
    data class Favorite(val count: Int, val favorite: Boolean) : PendingMediaConsent
}

private data class PhotosBaseState(
    val media: List<MediaItem>,
    val selectedIds: Set<Long>,
    val columns: Int,
    val vaults: List<VaultFolder>,
    val isPickerOpen: Boolean
)

private data class PhotosOverlayState(
    val isCreateOpen: Boolean,
    val showExplanation: Boolean,
    val statusMessage: String?,
    val isTransferring: Boolean
)

class PhotosViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PrivateGalleryApplication
    private val mediaStoreRepository = app.mediaStoreRepository
    private val vaultRepository = app.vaultRepository
    private val vaultTransferManager = app.vaultTransferManager
    private val preferencesRepository = app.preferencesRepository

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isMovePickerOpen = MutableStateFlow(false)
    private val _isCreateVaultDialogOpen = MutableStateFlow(false)
    private val _showFirstLockExplanation = MutableStateFlow(false)
    private val _pendingConsentRequest = MutableStateFlow<IntentSenderRequest?>(null)
    private val _activeTransactionId = MutableStateFlow<String?>(null)
    private val _transferStatusMessage = MutableStateFlow<String?>(null)
    private val _isTransferring = MutableStateFlow(false)
    private var pendingMediaConsent: PendingMediaConsent? = null

    private val _events = MutableSharedFlow<PhotosUiEvent>()
    val events: SharedFlow<PhotosUiEvent> = _events.asSharedFlow()

    private var targetVaultAfterExplanation: VaultFolder? = null

    private val baseState = combine(
        mediaStoreRepository.observeAllMedia(),
        _selectedIds,
        preferencesRepository.gridColumnsFlow,
        vaultRepository.vaultsFlow,
        _isMovePickerOpen
    ) { media, selectedIds, columns, vaults, isPickerOpen ->
        PhotosBaseState(media, selectedIds, columns.coerceIn(2, 5), vaults, isPickerOpen)
    }

    private val overlayState = combine(
        _isCreateVaultDialogOpen,
        _showFirstLockExplanation,
        _transferStatusMessage,
        _isTransferring
    ) { isCreateOpen, showExplanation, statusMessage, isTransferring ->
        PhotosOverlayState(isCreateOpen, showExplanation, statusMessage, isTransferring)
    }

    val uiState: StateFlow<PhotosUiState> = combine(baseState, overlayState) { base, overlay ->
        PhotosUiState(
            mediaItems = base.media,
            selectedItemIds = base.selectedIds,
            gridColumns = base.columns,
            isLoading = false,
            vaults = base.vaults,
            isMovePickerOpen = base.isPickerOpen,
            isCreateVaultDialogOpen = overlay.isCreateOpen,
            showFirstLockExplanation = overlay.showExplanation,
            transferStatusMessage = overlay.statusMessage,
            isTransferring = overlay.isTransferring
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PhotosUiState()
    )

    fun toggleItemSelection(id: Long) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedIds.value = current
    }

    fun selectItem(id: Long) {
        _selectedIds.value = _selectedIds.value + id
    }

    fun setSelectedItems(ids: Set<Long>) {
        _selectedIds.value = ids
    }

    fun selectAll() {
        _selectedIds.value = uiState.value.mediaItems.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch {
            preferencesRepository.setGridColumns(columns.coerceIn(2, 5))
        }
    }

    fun openMovePicker() {
        _isMovePickerOpen.value = true
    }

    fun closeMovePicker() {
        _isMovePickerOpen.value = false
    }

    fun openCreateVaultDialog() {
        _isCreateVaultDialogOpen.value = true
    }

    fun closeCreateVaultDialog() {
        _isCreateVaultDialogOpen.value = false
    }

    fun createVault(name: String) {
        viewModelScope.launch {
            try {
                vaultRepository.createVault(name)
                _isCreateVaultDialogOpen.value = false
                _events.emit(PhotosUiEvent.ShowSnackbar("Created locked folder '$name'"))
            } catch (e: Exception) {
                _events.emit(PhotosUiEvent.ShowSnackbar(e.message ?: "Failed to create folder"))
            }
        }
    }

    fun onVaultSelectedToMove(vault: VaultFolder) {
        _isMovePickerOpen.value = false
        viewModelScope.launch {
            val dismissed = try {
                preferencesRepository.vaultExplanationDismissedFlow.first()
            } catch (e: Exception) {
                false
            }

            if (!dismissed) {
                targetVaultAfterExplanation = vault
                _showFirstLockExplanation.value = true
            } else {
                executeMoveToVault(vault)
            }
        }
    }

    fun onDismissFirstLockExplanation() {
        _showFirstLockExplanation.value = false
        targetVaultAfterExplanation = null
    }

    fun onProceedFirstLockExplanation(dontShowAgain: Boolean) {
        _showFirstLockExplanation.value = false
        viewModelScope.launch {
            if (dontShowAgain) {
                preferencesRepository.setVaultExplanationDismissed(true)
            }
            targetVaultAfterExplanation?.let { vault ->
                executeMoveToVault(vault)
            }
            targetVaultAfterExplanation = null
        }
    }

    private fun executeMoveToVault(targetVault: VaultFolder) {
        val selectedIds = _selectedIds.value
        val itemsToMove = if (uiState.value.selectedItems.isNotEmpty()) {
            uiState.value.selectedItems
        } else {
            mediaStoreRepository.getAllMedia().filter { it.id in selectedIds }
        }

        if (itemsToMove.isEmpty()) {
            viewModelScope.launch {
                _events.emit(PhotosUiEvent.ShowSnackbar("No items selected to move"))
            }
            return
        }

        _isTransferring.value = true
        _transferStatusMessage.value = "Preparing ${itemsToMove.size} items..."

        viewModelScope.launch {
            try {
                vaultTransferManager.stageMoveToVault(
                    mediaItems = itemsToMove,
                    targetVaultId = targetVault.id,
                    targetVaultName = targetVault.displayName
                ) { progress ->
                    when (progress) {
                        is TransferProgress.Progress -> {
                            _transferStatusMessage.value = "Encrypting ${progress.current} of ${progress.total}"
                        }
                        is TransferProgress.AwaitingDeleteConsent -> {
                            _pendingConsentRequest.value = progress.intentSenderRequest
                            _activeTransactionId.value = progress.transactionId
                            pendingMediaConsent = PendingMediaConsent.VaultMove(progress.transactionId)
                            _transferStatusMessage.value = "Waiting for deletion consent..."
                            viewModelScope.launch {
                                _events.emit(PhotosUiEvent.LaunchIntentSender(progress.intentSenderRequest))
                            }
                        }
                        is TransferProgress.Success -> {
                            _isTransferring.value = false
                            _transferStatusMessage.value = null
                            clearSelection()
                            mediaStoreRepository.refresh()
                            viewModelScope.launch {
                                _events.emit(
                                    PhotosUiEvent.ShowSnackbar(
                                        "Moved ${progress.count} ${if (progress.count == 1) "item" else "items"} to ${progress.vaultName}"
                                    )
                                )
                            }
                        }
                        is TransferProgress.Cancelled -> {
                            _isTransferring.value = false
                            _transferStatusMessage.value = null
                            viewModelScope.launch {
                                _events.emit(PhotosUiEvent.ShowSnackbar(progress.message))
                            }
                        }
                        is TransferProgress.Error -> {
                            _isTransferring.value = false
                            _transferStatusMessage.value = null
                            viewModelScope.launch {
                                _events.emit(PhotosUiEvent.ShowSnackbar(progress.message))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _isTransferring.value = false
                _transferStatusMessage.value = null
                _events.emit(PhotosUiEvent.ShowSnackbar("Move failed: ${e.message}"))
            }
        }
    }

    fun onDeletionConsentResult(granted: Boolean) {
        val action = pendingMediaConsent ?: _activeTransactionId.value?.let(PendingMediaConsent::VaultMove)
            ?: return
        pendingMediaConsent = null
        _activeTransactionId.value = null
        _pendingConsentRequest.value = null

        viewModelScope.launch {
            if (action is PendingMediaConsent.VaultMove) {
                val result = vaultTransferManager.handleConsentResult(action.transactionId, granted)
                _isTransferring.value = false
                _transferStatusMessage.value = null
                when (result) {
                    is TransferProgress.Success -> {
                        clearSelection()
                        mediaStoreRepository.refresh()
                        _events.emit(PhotosUiEvent.ShowSnackbar("Moved ${result.count} items to ${result.vaultName}"))
                    }
                    is TransferProgress.Cancelled -> _events.emit(PhotosUiEvent.ShowSnackbar(result.message))
                    is TransferProgress.Error -> _events.emit(PhotosUiEvent.ShowSnackbar(result.message))
                    else -> Unit
                }
            } else if (granted) {
                mediaStoreRepository.refresh()
                when (action) {
                    is PendingMediaConsent.Trash -> _events.emit(PhotosUiEvent.ShowSnackbar("Moved ${action.count} items to Trash"))
                    is PendingMediaConsent.RestoreFromTrash -> _events.emit(PhotosUiEvent.ShowSnackbar("Restored ${action.count} items"))
                    is PendingMediaConsent.PermanentDelete -> _events.emit(PhotosUiEvent.ShowSnackbar("Permanently deleted ${action.count} items"))
                    is PendingMediaConsent.Favorite -> _events.emit(
                        PhotosUiEvent.ShowSnackbar(
                            if (action.favorite) "Added ${action.count} items to Favorites"
                            else "Removed ${action.count} items from Favorites"
                        )
                    )
                    else -> Unit
                }
                clearSelection()
            } else {
                if (action is PendingMediaConsent.VaultMove) {
                    clearSelection()
                }
                _events.emit(PhotosUiEvent.ShowSnackbar("Action cancelled"))
            }
        }
    }

    fun requestDeleteSelected() {
        val selectedItems = uiState.value.selectedItems
        if (selectedItems.isEmpty()) return

        requestTrashItems(selectedItems)
    }

    fun requestTrashItems(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val request = mediaStoreRepository.createTrashRequest(items.map { it.uri }, trashed = true)
        if (request == null) {
            viewModelScope.launch {
                _events.emit(PhotosUiEvent.ShowSnackbar("Gallery Trash requires Android 11 or newer"))
            }
            return
        }
        pendingMediaConsent = PendingMediaConsent.Trash(items.size)
        viewModelScope.launch { _events.emit(PhotosUiEvent.LaunchIntentSender(request)) }
    }

    fun requestRestoreFromTrash(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val request = mediaStoreRepository.createTrashRequest(items.map { it.uri }, trashed = false) ?: return
        pendingMediaConsent = PendingMediaConsent.RestoreFromTrash(items.size)
        viewModelScope.launch { _events.emit(PhotosUiEvent.LaunchIntentSender(request)) }
    }

    fun requestPermanentDelete(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val request = mediaStoreRepository.createDeleteRequest(items.map { it.uri }) ?: return
        pendingMediaConsent = PendingMediaConsent.PermanentDelete(items.size)
        viewModelScope.launch { _events.emit(PhotosUiEvent.LaunchIntentSender(request)) }
    }

    fun requestFavorite(items: List<MediaItem>, favorite: Boolean) {
        if (items.isEmpty()) return
        val request = mediaStoreRepository.createFavoriteRequest(items.map { it.uri }, favorite)
        if (request == null) {
            viewModelScope.launch {
                _events.emit(PhotosUiEvent.ShowSnackbar("Favorites requires Android 11 or newer"))
            }
            return
        }
        pendingMediaConsent = PendingMediaConsent.Favorite(items.size, favorite)
        viewModelScope.launch { _events.emit(PhotosUiEvent.LaunchIntentSender(request)) }
    }
}
