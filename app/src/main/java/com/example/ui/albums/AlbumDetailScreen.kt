@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.example.ui.albums

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MediaItem
import com.example.ui.components.CreateVaultDialog
import com.example.ui.components.FirstLockExplanationDialog
import com.example.ui.components.MediaThumbnail
import com.example.ui.components.GalleryFastScroller
import com.example.ui.components.dragSelectMedia
import com.example.ui.components.VaultFolderPickerSheet
import com.example.ui.photos.PhotosUiEvent
import com.example.ui.photos.PhotosViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    bucketId: Long,
    albumName: String,
    albumsViewModel: AlbumsViewModel,
    photosViewModel: PhotosViewModel,
    onBack: () -> Unit,
    onMediaClick: (index: Int, mediaId: Long, tileSize: IntSize) -> Unit,
    sharedMediaId: Long? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedTileSize: IntSize = IntSize.Zero,
    sharedViewerSize: IntSize = IntSize.Zero,
    gridState: LazyGridState = rememberLazyGridState(),
    modifier: Modifier = Modifier
) {
    val albumsState by albumsViewModel.uiState.collectAsStateWithLifecycle()
    val photosUiState by photosViewModel.uiState.collectAsStateWithLifecycle()
    val albumMedia = remember(albumsState.allMedia, bucketId) {
        albumsState.allMedia.filter { it.bucketId == bucketId }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tappedTileSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(bucketId) { photosViewModel.clearSelection() }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        photosViewModel.onDeletionConsentResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(photosViewModel) {
        photosViewModel.events.collectLatest { event ->
            when (event) {
                is PhotosUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is PhotosUiEvent.LaunchIntentSender -> consentLauncher.launch(event.intentSenderRequest)
            }
        }
    }

    Scaffold(
        topBar = {
            if (photosUiState.isSelectionMode) {
                TopAppBar(
                    title = { Text("${photosUiState.selectedCount} selected") },
                    navigationIcon = {
                        IconButton(onClick = { photosViewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        IconButton(onClick = { photosViewModel.openMovePicker() }) {
                            Icon(Icons.Default.Lock, contentDescription = "Move to Locked Folder", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { photosViewModel.requestFavorite(photosUiState.selectedItems, true) }) {
                            Icon(Icons.Default.Star, contentDescription = "Add to Favorites")
                        }
                        IconButton(onClick = { photosViewModel.requestDeleteSelected() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Move to Trash")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = albumName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(photosUiState.gridColumns),
                state = gridState,
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .dragSelectMedia(
                        gridState,
                        albumMedia,
                        photosUiState.selectedItemIds,
                        photosViewModel::setSelectedItems
                    )
            ) {
                items(albumMedia, key = { it.id }, contentType = { "media" }) { item ->
                    val isSelected = item.id in photosUiState.selectedItemIds
                    MediaThumbnail(
                        item = item,
                        isSelected = isSelected,
                        isSelectionMode = photosUiState.isSelectionMode,
                        dragSelectionEnabled = true,
                        isScrolling = gridState.isScrollInProgress,
                        sharedMediaId = sharedMediaId,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        sharedTileSize = sharedTileSize,
                        sharedViewerSize = sharedViewerSize,
                        onTileSizeChanged = { tappedTileSize = it },
                        onClick = {
                            if (photosUiState.isSelectionMode) {
                                photosViewModel.toggleItemSelection(item.id)
                            } else {
                                val index = albumMedia.indexOf(item)
                                if (index >= 0) onMediaClick(index, item.id, tappedTileSize)
                            }
                        },
                        onLongClick = {
                            photosViewModel.selectItem(item.id)
                        }
                    )
                }
            }
            GalleryFastScroller(gridState = gridState, media = albumMedia)
        }
    }

    if (photosUiState.isMovePickerOpen) {
        VaultFolderPickerSheet(
            vaults = photosUiState.vaults,
            itemCount = photosUiState.selectedCount,
            sheetState = sheetState,
            onDismiss = { photosViewModel.closeMovePicker() },
            onCreateNewFolder = {
                photosViewModel.closeMovePicker()
                photosViewModel.openCreateVaultDialog()
            },
            onConfirmMove = { vault -> photosViewModel.onVaultSelectedToMove(vault) }
        )
    }

    if (photosUiState.isCreateVaultDialogOpen) {
        CreateVaultDialog(
            title = "Create Locked Folder",
            onDismiss = { photosViewModel.closeCreateVaultDialog() },
            onConfirm = { name -> photosViewModel.createVault(name) }
        )
    }

    if (photosUiState.showFirstLockExplanation) {
        FirstLockExplanationDialog(
            onDismiss = { photosViewModel.onDismissFirstLockExplanation() },
            onProceed = { dontShowAgain -> photosViewModel.onProceedFirstLockExplanation(dontShowAgain) }
        )
    }
}
