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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.ui.components.EmptyState
import com.example.ui.components.GalleryFastScroller
import com.example.ui.components.MediaThumbnail
import com.example.ui.components.dragSelectMedia
import com.example.ui.photos.PhotosUiEvent
import com.example.ui.photos.PhotosViewModel
import kotlinx.coroutines.flow.collectLatest

enum class SpecialCollectionType { Favorites, Trash }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialCollectionScreen(
    type: SpecialCollectionType,
    albumsViewModel: AlbumsViewModel,
    photosViewModel: PhotosViewModel,
    onBack: () -> Unit,
    onMediaClick: (mediaId: Long, tileSize: IntSize) -> Unit,
    sharedMediaId: Long? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedTileSize: IntSize = IntSize.Zero,
    sharedViewerSize: IntSize = IntSize.Zero,
    gridState: LazyGridState = rememberLazyGridState(),
    modifier: Modifier = Modifier
) {
    val albumsState by albumsViewModel.uiState.collectAsStateWithLifecycle()
    val photosState by photosViewModel.uiState.collectAsStateWithLifecycle()
    val media = if (type == SpecialCollectionType.Favorites) albumsState.favoriteMedia else albumsState.trashMedia
    val selected = media.filter { it.id in photosState.selectedItemIds }
    val snackbar = remember { SnackbarHostState() }
    var tappedTileSize by remember { mutableStateOf(IntSize.Zero) }
    var confirmPermanentDelete by remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> photosViewModel.onDeletionConsentResult(result.resultCode == Activity.RESULT_OK) }

    LaunchedEffect(type) { photosViewModel.clearSelection() }
    LaunchedEffect(photosViewModel) {
        photosViewModel.events.collectLatest { event ->
            when (event) {
                is PhotosUiEvent.ShowSnackbar -> snackbar.showSnackbar(event.message)
                is PhotosUiEvent.LaunchIntentSender -> consentLauncher.launch(event.intentSenderRequest)
            }
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (photosState.isSelectionMode) "${selected.size} selected"
                        else if (type == SpecialCollectionType.Favorites) "Favorites" else "Trash",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = if (photosState.isSelectionMode) photosViewModel::clearSelection else onBack) {
                        Icon(
                            if (photosState.isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (photosState.isSelectionMode) "Cancel selection" else "Back"
                        )
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        if (type == SpecialCollectionType.Favorites) {
                            IconButton(onClick = { photosViewModel.requestFavorite(selected, false) }) {
                                Icon(Icons.Default.StarBorder, contentDescription = "Remove from Favorites")
                            }
                            IconButton(onClick = { photosViewModel.requestTrashItems(selected) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Move to Trash")
                            }
                        } else {
                            IconButton(onClick = { photosViewModel.requestRestoreFromTrash(selected) }) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore")
                            }
                            IconButton(onClick = { confirmPermanentDelete = true }) {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = "Delete permanently",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (media.isEmpty()) {
                EmptyState(
                    icon = if (type == SpecialCollectionType.Favorites) Icons.Default.Star else Icons.Default.Delete,
                    title = if (type == SpecialCollectionType.Favorites) "No favorites yet" else "Trash is empty",
                    description = if (type == SpecialCollectionType.Favorites) {
                        "Select photos or videos and tap the star to keep them here."
                    } else {
                        "Deleted gallery items stay here until you permanently delete or restore them."
                    }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(photosState.gridColumns),
                    state = gridState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .dragSelectMedia(gridState, media, photosState.selectedItemIds, photosViewModel::setSelectedItems)
                        .testTag(if (type == SpecialCollectionType.Favorites) "favorites_grid" else "trash_grid")
                ) {
                    items(media, key = { it.id }, contentType = { "media" }) { item ->
                        val isSelected = item.id in photosState.selectedItemIds
                        MediaThumbnail(
                            item = item,
                            isSelected = isSelected,
                            isSelectionMode = photosState.isSelectionMode || type == SpecialCollectionType.Trash,
                            dragSelectionEnabled = true,
                            isScrolling = gridState.isScrollInProgress,
                            sharedMediaId = sharedMediaId,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            sharedTileSize = sharedTileSize,
                            sharedViewerSize = sharedViewerSize,
                            onTileSizeChanged = { tappedTileSize = it },
                            onClick = {
                                if (photosState.isSelectionMode || type == SpecialCollectionType.Trash) {
                                    photosViewModel.toggleItemSelection(item.id)
                                } else onMediaClick(item.id, tappedTileSize)
                            },
                            onLongClick = { photosViewModel.selectItem(item.id) }
                        )
                    }
                }
                GalleryFastScroller(gridState, media)
            }
        }
    }

    if (confirmPermanentDelete) {
        AlertDialog(
            onDismissRequest = { confirmPermanentDelete = false },
            title = { Text("Delete permanently?") },
            text = { Text("${selected.size} items will be erased from this device and cannot be recovered.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmPermanentDelete = false
                    photosViewModel.requestPermanentDelete(selected)
                }) {
                    Text("Delete forever", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPermanentDelete = false }) { Text("Cancel") }
            }
        )
    }
}
