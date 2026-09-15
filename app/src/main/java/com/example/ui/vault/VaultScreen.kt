package com.example.ui.vault

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.PrivateGalleryApplication
import com.example.data.database.entity.VaultItemEntity
import com.example.data.model.VaultFolder
import com.example.ui.components.CreateVaultDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.formatDuration
import com.example.ui.components.dragSelectItems
import com.example.ui.viewer.formatFileSize
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onMediaClick: (vaultId: Long, index: Int, mediaId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PrivateGalleryApplication
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val blurVaultThumbnails by app.preferencesRepository.blurVaultThumbnailsFlow.collectAsState(initial = false)
    val blurRadius by app.preferencesRepository.blurRadiusFlow.collectAsState(initial = 22f)
    val snackbarHostState = remember { SnackbarHostState() }
    val activeVault = uiState.currentVault

    androidx.activity.compose.BackHandler(enabled = uiState.isSelectionMode || uiState.isInTrashView || uiState.currentVault != null) {
        when {
            uiState.isSelectionMode -> viewModel.clearSelection()
            uiState.isInTrashView -> viewModel.closeTrash()
            else -> viewModel.clearSelectedVaultFolder()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is VaultUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is VaultUiEvent.RequestAuth -> {
                    val activity = context as? FragmentActivity
                    activity?.let { viewModel.authenticate(it) }
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (!uiState.isUnlocked) {
            // Locked State view with Pixel biometric button
            LockedVaultAuthView(
                onAuthenticate = {
                    val activity = context as? FragmentActivity
                    activity?.let { viewModel.authenticate(it) }
                }
            )
        } else if (uiState.isInTrashView) {
            // Vault Trash View
            VaultTrashContentView(
                viewModel = viewModel,
                uiState = uiState,
                blurThumbnail = blurVaultThumbnails,
                blurRadius = blurRadius,
                onBack = { viewModel.closeTrash() }
            )
        } else if (activeVault != null) {
            // Inside a specific Vault folder
            VaultFolderContentView(
                vault = activeVault,
                viewModel = viewModel,
                uiState = uiState,
                blurThumbnail = blurVaultThumbnails,
                blurRadius = blurRadius,
                onToggleBlur = {
                    scope.launch {
                        app.preferencesRepository.setBlurVaultThumbnails(!blurVaultThumbnails)
                    }
                },
                onBack = { viewModel.clearSelectedVaultFolder() },
                onMediaClick = { index, mediaId -> onMediaClick(activeVault.id, index, mediaId) }
            )
        } else {
            // Vault Folders Root Dashboard
            VaultDashboardView(
                uiState = uiState,
                viewModel = viewModel,
                onFolderClick = { vault -> viewModel.selectVaultFolder(vault.id) },
                onOpenTrash = { viewModel.openTrash() }
            )
        }

        // Restore in progress overlay
        AnimatedVisibility(
            visible = uiState.isRestoring && uiState.restoreProgressMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
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
                        text = uiState.restoreProgressMessage ?: "Restoring...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    // Dialogs
    if (uiState.isCreateFolderDialogOpen) {
        CreateVaultDialog(
            title = "New Locked Folder",
            confirmButtonText = "Create",
            onDismiss = { viewModel.closeCreateFolderDialog() },
            onConfirm = { name -> viewModel.createFolder(name) }
        )
    }

    uiState.folderToRename?.takeIf { uiState.isRenameFolderDialogOpen }?.let { folder ->
        CreateVaultDialog(
            initialName = folder.displayName,
            title = "Rename Locked Folder",
            confirmButtonText = "Save",
            onDismiss = { viewModel.closeRenameFolderDialog() },
            onConfirm = { newName -> viewModel.renameFolder(newName) }
        )
    }

    if (uiState.isMoveItemsDialogOpen) {
        MoveItemsFolderDialog(
            vaults = uiState.vaults.filter { it.id != uiState.currentVault?.id },
            onDismiss = { viewModel.closeMoveItemsDialog() },
            onConfirm = { targetVaultId -> viewModel.moveItemsToTargetVault(targetVaultId) }
        )
    }
}

@Composable
fun LockedVaultAuthView(
    onAuthenticate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Locked Folders",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Media in Locked Folders is protected with AES-256-GCM hardware encryption and hidden from other apps.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onAuthenticate,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("unlock_vault_button")
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Unlock with Biometrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Android Keystore Verified",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDashboardView(
    uiState: VaultUiState,
    viewModel: VaultViewModel,
    onFolderClick: (VaultFolder) -> Unit,
    onOpenTrash: () -> Unit
) {
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Locked Folders",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.openCreateFolderDialog() },
                        modifier = Modifier.testTag("create_vault_folder_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Folder")
                    }
                    IconButton(
                        onClick = { viewModel.lockNow() },
                        modifier = Modifier.testTag("lock_vault_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Lock Now",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Overall Vault Storage Card
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("vault_storage_overview_card")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Vault Storage: ${formatFileSize(uiState.totalStorageBytes)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${uiState.vaults.sumOf { it.itemCount }} items in ${uiState.vaults.size} folders • Includes trash and thumbnails",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Vault Trash Entry Card
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                shape = RoundedCornerShape(20.dp),
                onClick = onOpenTrash,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("vault_trash_card")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Vault Trash",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (uiState.trashCount == 0) "Empty • Items delete to trash first" else "${uiState.trashCount} items • ${formatFileSize(uiState.trashStorageBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Trash",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Grid of Locked Folders
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("vault_folders_grid")
            ) {
                items(uiState.vaults, key = { it.id }, contentType = { "vault_folder" }) { vault ->
                    VaultFolderCard(
                        vault = vault,
                        onClick = { onFolderClick(vault) },
                        onRename = { viewModel.openRenameFolderDialog(vault) },
                        onDelete = { viewModel.deleteFolder(vault) }
                    )
                }
            }
        }
    }
}

@Composable
fun VaultFolderCard(
    vault: VaultFolder,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("vault_card_${vault.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        if (!vault.isDefault) {
                            DropdownMenuItem(
                                text = { Text("Delete Folder") },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = vault.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${vault.itemCount} ${if (vault.itemCount == 1) "item" else "items"} • ${formatFileSize(vault.totalSizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultFolderContentView(
    vault: VaultFolder,
    viewModel: VaultViewModel,
    uiState: VaultUiState,
    blurThumbnail: Boolean,
    blurRadius: Float,
    onToggleBlur: () -> Unit,
    onBack: () -> Unit,
    onMediaClick: (index: Int, mediaId: Long) -> Unit
) {
    var showTrashConfirmDialog by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = { Text("${uiState.selectedCount} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.restoreSelected() },
                            modifier = Modifier.testTag("vault_restore_action")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = "Restore to Gallery",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { viewModel.openMoveItemsDialog() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                                contentDescription = "Move to folder"
                            )
                        }
                        IconButton(
                            onClick = { showTrashConfirmDialog = true },
                            modifier = Modifier.testTag("vault_move_to_trash_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Move to Trash",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = vault.displayName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "${uiState.itemsInCurrentVault.size} items • AES-256 Encrypted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onToggleBlur,
                            modifier = Modifier.testTag("toggle_blur_vault_items")
                        ) {
                            Icon(
                                imageVector = if (blurThumbnail) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (blurThumbnail) "Show Previews" else "Blur Previews",
                                tint = if (blurThumbnail) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { viewModel.lockNow() },
                            modifier = Modifier.testTag("lock_vault_button_in_folder")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Lock Now",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.itemsInCurrentVault.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Lock,
                    title = "Folder is empty",
                    description = "Select photos or videos in the Photos tab and choose 'Move to Locked Folder' to protect them here."
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding = PaddingValues(bottom = 12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .dragSelectItems(
                            gridState = gridState,
                            items = uiState.itemsInCurrentVault,
                            selectedIds = uiState.selectedItemIds,
                            onSelectionChange = viewModel::setSelectedItems,
                            itemId = { it.id }
                        )
                        .testTag("vault_items_grid")
                ) {
                    items(uiState.itemsInCurrentVault, key = { it.id }, contentType = { "vault_media" }) { item ->
                        val isSelected = item.id in uiState.selectedItemIds
                        VaultItemThumbnail(
                            item = item,
                            isSelected = isSelected,
                            isSelectionMode = uiState.isSelectionMode,
                            blurThumbnail = blurThumbnail,
                            blurRadius = blurRadius,
                            dragSelectionEnabled = true,
                            viewModel = viewModel,
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleItemSelection(item.id)
                                } else {
                                    val index = uiState.itemsInCurrentVault.indexOf(item)
                                    if (index >= 0) onMediaClick(index, item.id)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleItemSelection(item.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showTrashConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showTrashConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Move to Vault Trash?") },
            text = {
                Text("These ${uiState.selectedCount} items will be moved to Vault Trash. You can restore them anytime or permanently delete them from Trash.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTrashConfirmDialog = false
                        viewModel.deleteSelectedItems()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Move to Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrashConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultTrashContentView(
    viewModel: VaultViewModel,
    uiState: VaultUiState,
    blurThumbnail: Boolean,
    blurRadius: Float,
    onBack: () -> Unit
) {
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var showPermanentDeleteDialog by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = { Text("${uiState.selectedCount} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.restoreSelectedFromTrash() },
                            modifier = Modifier.testTag("vault_trash_restore_action")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = "Restore to Folder",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { showPermanentDeleteDialog = true },
                            modifier = Modifier.testTag("vault_trash_delete_perm_action")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = "Delete Permanently",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Vault Trash",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = if (uiState.trashItems.isEmpty()) "Empty" else "${uiState.trashItems.size} items • ${formatFileSize(uiState.trashStorageBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (uiState.trashItems.isNotEmpty()) {
                            IconButton(
                                onClick = { showEmptyTrashDialog = true },
                                modifier = Modifier.testTag("empty_vault_trash_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Empty Trash",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.trashItems.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Delete,
                    title = "Trash is Empty",
                    description = "When you delete items from your locked folders, they are moved here. You can restore them or permanently delete them."
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding = PaddingValues(bottom = 12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .dragSelectItems(
                            gridState = gridState,
                            items = uiState.trashItems,
                            selectedIds = uiState.selectedItemIds,
                            onSelectionChange = viewModel::setSelectedItems,
                            itemId = { it.id }
                        )
                        .testTag("vault_trash_grid")
                ) {
                    items(uiState.trashItems, key = { it.id }, contentType = { "vault_media" }) { item ->
                        val isSelected = item.id in uiState.selectedItemIds
                        VaultItemThumbnail(
                            item = item,
                            isSelected = isSelected,
                            isSelectionMode = uiState.isSelectionMode,
                            blurThumbnail = blurThumbnail,
                            blurRadius = blurRadius,
                            dragSelectionEnabled = true,
                            viewModel = viewModel,
                            onClick = {
                                viewModel.toggleItemSelection(item.id)
                            },
                            onLongClick = {
                                viewModel.toggleItemSelection(item.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showPermanentDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showPermanentDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete permanently?") },
            text = {
                Text("These ${uiState.selectedCount} items will be permanently erased from your device storage. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermanentDeleteDialog = false
                        viewModel.deletePermanentlySelected()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            icon = {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Empty Vault Trash?") },
            text = {
                Text("All ${uiState.trashItems.size} items in the trash (${formatFileSize(uiState.trashStorageBytes)}) will be permanently erased. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptyTrashDialog = false
                        viewModel.emptyTrash()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Empty Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultItemThumbnail(
    item: VaultItemEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    blurThumbnail: Boolean,
    blurRadius: Float,
    dragSelectionEnabled: Boolean = false,
    viewModel: VaultViewModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val decryptedBytes by produceState<ByteArray?>(initialValue = null, item.id) {
        value = viewModel.loadThumbnailBytes(item)
    }
    val blurredPreview by produceState<android.graphics.Bitmap?>(
        initialValue = null, decryptedBytes, blurThumbnail, blurRadius
    ) {
        value = null
        if (blurThumbnail && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                decryptedBytes?.let { com.example.ui.vault.blurThumbnail(it, blurRadius) }
            }
        }
    }
    val thumbnailRequest = remember(decryptedBytes, blurredPreview, blurThumbnail) {
        val model = if (blurThumbnail && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            blurredPreview
        } else {
            decryptedBytes
        }
        model?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(false)
                .size(300, 300)
                .build()
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.5.dp)
            .clip(RoundedCornerShape(if (isSelected) 12.dp else 4.dp))
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(if (isSelected) 12.dp else 4.dp)
            )
            .then(
                if (dragSelectionEnabled) {
                    Modifier.clickable {
                        if (isSelectionMode) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        }
                        onClick()
                    }
                } else {
                    Modifier.combinedClickable(
                        onClick = {
                            if (isSelectionMode) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            }
                            onClick()
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onLongClick()
                        }
                    )
                }
            )
            .testTag("vault_item_${item.id}")
    ) {
        if (decryptedBytes != null) {
            AsyncImage(
                model = thumbnailRequest,
                contentDescription = item.originalFileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (blurThumbnail && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(blurRadius.dp)
                        } else Modifier
                    )
            )

            // When blur is requested, overlay frosted blur scrim and prominent Lock icon
            if (blurThumbnail) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted Preview",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Video Badge
        if (item.mediaType == 2 && !blurThumbnail) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = formatDuration(item.durationMs),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }

        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else Color.Transparent
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.4f)
                    )
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MoveItemsFolderDialog(
    vaults: List<VaultFolder>,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var selectedVault by remember { mutableStateOf(vaults.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to another Locked Folder") },
        text = {
            if (vaults.isEmpty()) {
                Text("No other locked folders exist. Create another folder first.")
            } else {
                Column {
                    vaults.forEach { vault ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedVault = vault }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = selectedVault?.id == vault.id,
                                onClick = { selectedVault = vault }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(vault.displayName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedVault?.let { onConfirm(it.id) } },
                enabled = selectedVault != null
            ) {
                Text("Move")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
