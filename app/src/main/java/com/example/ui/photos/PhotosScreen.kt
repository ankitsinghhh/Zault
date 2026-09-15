@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.example.ui.photos

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MediaItem
import com.example.ui.components.CreateVaultDialog
import com.example.ui.components.DateHeader
import com.example.ui.components.EmptyState
import com.example.ui.components.FirstLockExplanationDialog
import com.example.ui.components.MediaThumbnail
import com.example.ui.components.GalleryFastScroller
import com.example.ui.components.GalleryHitGeometry
import com.example.ui.components.dragSelectMedia
import com.example.ui.components.VaultFolderPickerSheet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    viewModel: PhotosViewModel,
    onMediaClick: (index: Int, mediaId: Long, tileSize: IntSize) -> Unit,
    onNavigateToSettings: () -> Unit,
    sharedMediaId: Long? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedTileSize: IntSize = IntSize.Zero,
    sharedViewerSize: IntSize = IntSize.Zero,
    gridState: LazyGridState = rememberLazyGridState(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectionHitGeometry = remember { GalleryHitGeometry() }
    var tappedTileSize by remember { mutableStateOf(IntSize.Zero) }
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Android Delete Consent Launcher
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDeletionConsentResult(result.resultCode == Activity.RESULT_OK)
    }

    // Modern Android permissions check
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_IMAGES
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                        androidx.core.content.ContextCompat.checkSelfPermission(context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.any { it }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(perms)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PhotosUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is PhotosUiEvent.LaunchIntentSender -> {
                    consentLauncher.launch(event.intentSenderRequest)
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize().nestedScroll(topBarScrollBehavior.nestedScrollConnection)
    ) { _ ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (!hasPermission && uiState.mediaItems.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.PhotoLibrary,
                    title = "Access your Photos",
                    description = "Allow Zault access to photos and videos on your device.",
                    action = {
                        androidx.compose.material3.Button(
                            onClick = {
                                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    arrayOf(
                                        Manifest.permission.READ_MEDIA_IMAGES,
                                        Manifest.permission.READ_MEDIA_VIDEO
                                    )
                                } else {
                                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                                permissionLauncher.launch(perms)
                            },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Grant Access")
                        }
                    }
                )
            } else if (uiState.mediaItems.isEmpty() && !uiState.isLoading) {
                EmptyState(
                    icon = Icons.Default.PhotoLibrary,
                    title = "No photos yet",
                    description = "Photos and videos taken with your camera or downloaded will appear here."
                )
            } else {
                val groupedMedia = remember(uiState.mediaItems) {
                    groupMediaByDay(uiState.mediaItems)
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(uiState.gridColumns),
                    state = gridState,
                    // This leading space belongs to the scroll content. It disappears with the
                    // first rows so media can continue beneath the status bar instead of leaving
                    // behind the fixed blank strip produced by Scaffold's top-bar inset.
                    contentPadding = PaddingValues(top = 104.dp, bottom = 12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .dragSelectMedia(
                            gridState = gridState,
                            media = uiState.mediaItems,
                            selectedIds = uiState.selectedItemIds,
                            onSelectionChange = viewModel::setSelectedItems,
                            hitGeometry = selectionHitGeometry
                        )
                        .testTag("photos_grid")
                ) {
                    groupedMedia.forEach { (dateKey, items) ->
                        item(
                            span = { GridItemSpan(uiState.gridColumns) },
                            key = "header_$dateKey",
                            contentType = "date_header"
                        ) {
                            DateHeader(
                                dateMillis = items.firstOrNull()?.dateTaken ?: 0L,
                                itemCount = items.size
                            )
                        }

                        items(
                            items = items,
                            key = { it.id },
                            contentType = { "media" }
                        ) { item ->
                            val isSelected = item.id in uiState.selectedItemIds
                            MediaThumbnail(
                                item = item,
                                isSelected = isSelected,
                                isSelectionMode = uiState.isSelectionMode,
                                dragSelectionEnabled = true,
                                isScrolling = gridState.isScrollInProgress,
                                sharedMediaId = sharedMediaId,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                sharedTileSize = sharedTileSize,
                                sharedViewerSize = sharedViewerSize,
                                onTileSizeChanged = { tappedTileSize = it },
                                onClick = {
                                    if (uiState.isSelectionMode) {
                                        viewModel.toggleItemSelection(item.id)
                                    } else {
                                        val index = uiState.mediaItems.indexOf(item)
                                        if (index >= 0) onMediaClick(index, item.id, tappedTileSize)
                                    }
                                },
                                onLongClick = {
                                    viewModel.selectItem(item.id)
                                },
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    selectionHitGeometry.recordItem(item.id, coordinates.boundsInWindow())
                                }
                            )
                        }
                    }
                }
                GalleryFastScroller(
                    gridState = gridState,
                    media = uiState.mediaItems
                )
            }

            // A fixed tint under the status icons lets rows fade into the top edge
            // without the delayed frames and jumps of a live blur effect.
            Box(
                modifier = Modifier.align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.76f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.24f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                if (uiState.isSelectionMode) {
                    SelectionTopAppBar(
                        selectedCount = uiState.selectedCount,
                        totalCount = uiState.mediaItems.size,
                        onClearSelection = { viewModel.clearSelection() },
                        onSelectAll = { viewModel.selectAll() },
                        onMoveToVault = { viewModel.openMovePicker() },
                        onFavorite = { viewModel.requestFavorite(uiState.selectedItems, favorite = true) },
                        onShare = {
                            val uris = ArrayList(uiState.selectedItems.map { it.uri })
                            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "image/*"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                        },
                        onDelete = { viewModel.requestDeleteSelected() }
                    )
                } else {
                    StandardTopAppBar(
                        gridColumns = uiState.gridColumns,
                        scrollBehavior = topBarScrollBehavior,
                        onColumnsSelected = { viewModel.setGridColumns(it) },
                        onSettingsClick = onNavigateToSettings
                    )
                }
            }

            // Transfer in progress floating overlay
            AnimatedVisibility(
                visible = uiState.isTransferring && uiState.transferStatusMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = uiState.transferStatusMessage ?: "Securing media...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // Vault Folder Picker BottomSheet
    if (uiState.isMovePickerOpen) {
        VaultFolderPickerSheet(
            vaults = uiState.vaults,
            itemCount = uiState.selectedCount,
            sheetState = sheetState,
            onDismiss = { viewModel.closeMovePicker() },
            onCreateNewFolder = {
                viewModel.closeMovePicker()
                viewModel.openCreateVaultDialog()
            },
            onConfirmMove = { vault ->
                viewModel.onVaultSelectedToMove(vault)
            }
        )
    }

    // Create New Vault Dialog
    if (uiState.isCreateVaultDialogOpen) {
        CreateVaultDialog(
            title = "Create Locked Folder",
            onDismiss = { viewModel.closeCreateVaultDialog() },
            onConfirm = { name -> viewModel.createVault(name) }
        )
    }

    // First Lock Explanation Dialog
    if (uiState.showFirstLockExplanation) {
        FirstLockExplanationDialog(
            onDismiss = { viewModel.onDismissFirstLockExplanation() },
            onProceed = { dontShowAgain -> viewModel.onProceedFirstLockExplanation(dontShowAgain) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StandardTopAppBar(
    gridColumns: Int,
    scrollBehavior: TopAppBarScrollBehavior,
    onColumnsSelected: (Int) -> Unit,
    onSettingsClick: () -> Unit
) {
    var showGridMenu by remember { mutableStateOf(false) }

    TopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
            Text(
                text = "Photos",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium
            )
        },
        actions = {
            Box {
                IconButton(
                    onClick = { showGridMenu = true },
                    modifier = Modifier.testTag("grid_columns_button")
                ) {
                    Icon(imageVector = Icons.Default.GridView, contentDescription = "Grid layout")
                }
                DropdownMenu(
                    expanded = showGridMenu,
                    onDismissRequest = { showGridMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Large (2 columns)") },
                        onClick = {
                            onColumnsSelected(2)
                            showGridMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Standard (3 columns)") },
                        onClick = {
                            onColumnsSelected(3)
                            showGridMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Compact (4 columns)") },
                        onClick = {
                            onColumnsSelected(4)
                            showGridMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Dense (5 columns)") },
                        onClick = {
                            onColumnsSelected(5)
                            showGridMenu = false
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopAppBar(
    selectedCount: Int,
    totalCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onMoveToVault: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showMoreActions by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onClearSelection,
                modifier = Modifier.testTag("clear_selection_button")
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel")
            }
        },
        actions = {
            IconButton(onClick = onFavorite) {
                Icon(imageVector = Icons.Default.Star, contentDescription = "Add to Favorites")
            }
            IconButton(
                onClick = onMoveToVault,
                modifier = Modifier.testTag("move_to_locked_folder_action")
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Move to Locked Folder",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Move to Trash")
            }
            Box {
                IconButton(onClick = { showMoreActions = true }) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(
                    expanded = showMoreActions,
                    onDismissRequest = { showMoreActions = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMoreActions = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Select all") },
                        leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
                        onClick = {
                            showMoreActions = false
                            onSelectAll()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
        )
    )
}

fun groupMediaByDay(mediaList: List<MediaItem>): Map<Long, List<MediaItem>> {
    val cal = Calendar.getInstance()
    val map = linkedMapOf<Long, MutableList<MediaItem>>()

    for (item in mediaList) {
        val date = if (item.dateTaken > 0) item.dateTaken else item.dateModified * 1000L
        cal.timeInMillis = date
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis

        map.getOrPut(dayStart) { mutableListOf() }.add(item)
    }
    return map
}

/** Counts the date headers as well as media tiles in the Photos lazy grid. */
fun photosGridIndexOf(mediaList: List<MediaItem>, mediaId: Long): Int {
    var gridIndex = 0
    for (items in groupMediaByDay(mediaList).values) {
        gridIndex++ // Day header spans the full row.
        val position = items.indexOfFirst { it.id == mediaId }
        if (position >= 0) return gridIndex + position
        gridIndex += items.size
    }
    return -1
}
