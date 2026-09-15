@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.example.ui.viewer

import android.util.Log

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import coil.memory.MemoryCache
import com.example.PrivateGalleryApplication
import com.example.data.database.entity.VaultItemEntity
import com.example.data.model.MediaItem
import com.example.ui.components.CreateVaultDialog
import com.example.ui.components.thumbnailCacheKey
import com.example.ui.components.gallerySharedImageScale
import com.example.BuildConfig
import com.example.ui.components.FirstLockExplanationDialog
import com.example.ui.components.VaultFolderPickerSheet
import com.example.ui.photos.PhotosUiEvent
import com.example.ui.photos.PhotosViewModel
import com.example.ui.vault.MoveItemsFolderDialog
import com.example.ui.vault.VaultViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    source: String, // "photos" or "vault"
    initialIndex: Int,
    bucketId: Long?,
    vaultId: Long?,
    mediaId: Long? = null,
    photosViewModel: PhotosViewModel,
    vaultViewModel: VaultViewModel,
    onBack: (Long?) -> Unit,
    sharedMediaId: Long? = null,
    sharedTileSize: IntSize = IntSize.Zero,
    sharedViewerSize: IntSize = IntSize.Zero,
    returnAttempt: Int = 0,
    onSharedImageReady: (Long, Int) -> Unit = { _, _ -> },
    onSettledMediaChanged: (Long?) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PrivateGalleryApplication
    val showViewerDetails by app.preferencesRepository.viewerDetailsFlow.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val infoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val moveSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val photosState by photosViewModel.uiState.collectAsStateWithLifecycle()
    val vaultState by vaultViewModel.uiState.collectAsStateWithLifecycle()

    if (source == "vault" && !vaultState.isUnlocked) {
        LaunchedEffect(Unit) { onBack(null) }
        return
    }

    var showControls by remember { mutableStateOf(true) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var displayedMediaId by remember { mutableStateOf(mediaId) }
    var settledPhotoId by remember { mutableStateOf(mediaId) }
    var photoPagerForBack by remember { mutableStateOf<PagerState?>(null) }
    var photoItemsForBack by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    val requestBack: () -> Unit = {
        val pager = photoPagerForBack
        if (source != "vault" && pager != null) {
            scope.launch {
                if (BuildConfig.DEBUG) Log.d("GalleryTransition", "back request scrolling=${pager.isScrollInProgress} page=${pager.currentPage} settled=${pager.settledPage}")
                snapshotFlow { pager.isScrollInProgress }.first { !it }
                withFrameNanos { }
                val settledId = photoItemsForBack.getOrNull(pager.settledPage)?.id
                    ?: settledPhotoId ?: displayedMediaId
                if (BuildConfig.DEBUG) Log.d("GalleryTransition", "back settled id=$settledId page=${pager.settledPage} cached=$settledPhotoId")
                onBack(settledId)
            }
        } else {
            onBack(displayedMediaId)
        }
    }

    // Consent Launcher
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

    LaunchedEffect(vaultViewModel) {
        vaultViewModel.events.collectLatest { event ->
            if (event is com.example.ui.vault.VaultUiEvent.ShowSnackbar) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // Clean viewer temp files when leaving viewer screen
    DisposableEffect(Unit) {
        onDispose {
            app.storageManager.cleanViewerTemp()
        }
    }

    BackHandler {
        requestBack()
    }

    if (source == "vault") {
        val vaultItems = vaultState.itemsInCurrentVault
        if (vaultItems.isEmpty()) {
            LaunchedEffect(Unit) { onBack(null) }
            return
        }

        val resolvedIndex = remember(vaultItems, initialIndex, mediaId) {
            if (mediaId != null) {
                val foundIndex = vaultItems.indexOfFirst { it.id == mediaId }
                if (foundIndex >= 0) foundIndex else initialIndex.coerceIn(0, (vaultItems.size - 1).coerceAtLeast(0))
            } else {
                initialIndex.coerceIn(0, (vaultItems.size - 1).coerceAtLeast(0))
            }
        }
        val pagerState = rememberPagerState(initialPage = resolvedIndex, pageCount = { vaultItems.size })
        val currentItem = vaultItems.getOrNull(pagerState.currentPage)
        SideEffect { displayedMediaId = currentItem?.id }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = vaultItems[page]
                VaultMediaItemView(
                    item = item,
                    app = app,
                    isActive = page == pagerState.currentPage,
                    showControls = showControls,
                    onTap = { showControls = !showControls }
                )
            }

            if (showViewerDetails && currentItem != null) {
                ViewerDetailsBadge(
                    sizeBytes = currentItem.originalSize,
                    width = currentItem.width,
                    height = currentItem.height,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 16.dp, top = 68.dp)
                )
            }
            // Top Bar
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { -it / 5 },
                exit = fadeOut(tween(160)) +
                    slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { -it / 5 },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.30f))
                            )
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        IconButton(onClick = requestBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${pagerState.currentPage + 1} of ${vaultItems.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = currentItem?.originalFileName ?: "",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { showInfoSheet = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                        }
                    }
                }
            }

            // Bottom Bar
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 5 },
                exit = fadeOut(tween(160)) +
                    slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it / 5 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.82f))
                            )
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 12.dp)
                    ) {
                        // Restore Button
                        ViewerActionButton(
                            icon = Icons.Default.Restore,
                            label = "Restore",
                            onClick = {
                                currentItem?.let {
                                    vaultViewModel.clearSelection()
                                    vaultViewModel.toggleItemSelection(it.id)
                                    vaultViewModel.restoreSelected()
                                }
                            },
                            modifier = Modifier.testTag("viewer_restore_button")
                        )

                        // Move to another folder
                        ViewerActionButton(
                            icon = Icons.AutoMirrored.Filled.DriveFileMove,
                            label = "Move",
                            onClick = {
                                currentItem?.let {
                                    vaultViewModel.clearSelection()
                                    vaultViewModel.toggleItemSelection(it.id)
                                    vaultViewModel.openMoveItemsDialog()
                                }
                            },
                            modifier = Modifier.testTag("viewer_move_button")
                        )

                        // Vault deletion always goes through trash.
                        ViewerActionButton(
                            icon = Icons.Default.Delete,
                            label = "Trash",
                            tint = MaterialTheme.colorScheme.error,
                            textColor = MaterialTheme.colorScheme.error,
                            onClick = {
                                currentItem?.let {
                                    vaultViewModel.clearSelection()
                                    vaultViewModel.toggleItemSelection(it.id)
                                    vaultViewModel.deleteSelectedItems()
                                }
                            },
                            modifier = Modifier.testTag("viewer_delete_button")
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp)
            )
        }

        if (showInfoSheet && currentItem != null) {
            MediaInfoSheet(
                fileName = currentItem.originalFileName,
                mimeType = currentItem.mimeType,
                sizeBytes = currentItem.originalSize,
                dateTaken = currentItem.dateTaken,
                width = currentItem.width,
                height = currentItem.height,
                durationMs = currentItem.durationMs,
                albumName = vaultState.currentVault?.displayName ?: "Locked Folder",
                sheetState = infoSheetState,
                onDismiss = { showInfoSheet = false }
            )
        }
    } else {
        // Source is "photos" (local MediaStore)
        val mediaList = if (source == "favorites") {
            photosState.mediaItems.filter { it.isFavorite }
        } else if (bucketId != null) {
            photosState.mediaItems.filter { it.bucketId == bucketId }
        } else {
            photosState.mediaItems
        }

        if (mediaList.isEmpty()) {
            LaunchedEffect(Unit) { onBack(null) }
            return
        }

        val resolvedIndex = remember(mediaList, initialIndex, mediaId) {
            if (mediaId != null) {
                val foundIndex = mediaList.indexOfFirst { it.id == mediaId }
                if (foundIndex >= 0) foundIndex else initialIndex.coerceIn(0, (mediaList.size - 1).coerceAtLeast(0))
            } else {
                initialIndex.coerceIn(0, (mediaList.size - 1).coerceAtLeast(0))
            }
        }
        val pagerState = rememberPagerState(initialPage = resolvedIndex, pageCount = { mediaList.size })
        val currentItem = mediaList.getOrNull(pagerState.currentPage)
        LaunchedEffect(pagerState, mediaList) {
            snapshotFlow { pagerState.settledPage }
                .collect { page ->
                    val settledId = mediaList.getOrNull(page)?.id
                    settledPhotoId = settledId
                    onSettledMediaChanged(settledId)
                }
        }
        SideEffect {
            displayedMediaId = currentItem?.id
            photoPagerForBack = pagerState
            photoItemsForBack = mediaList
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = mediaList[page]
                if (item.isVideo) {
                    VideoPlayer(
                        uri = item.uri,
                        playWhenReady = page == pagerState.currentPage,
                        showControls = showControls,
                        onToggleControls = { showControls = !showControls }
                    )
                } else {
                    val sharpPreviewKey = thumbnailCacheKey(item, "sharp")
                    val quickPreviewKey = thumbnailCacheKey(item, "quick")
                    val previewKey = remember(item.id, item.dateModified, item.sizeBytes) {
                        val cache = context.imageLoader.memoryCache
                        if (cache?.get(MemoryCache.Key(sharpPreviewKey)) != null) sharpPreviewKey
                        else quickPreviewKey
                    }
                    val canShare = sharedMediaId == item.id && page == pagerState.settledPage &&
                        !pagerState.isScrollInProgress && sharedTransitionScope != null &&
                        animatedVisibilityScope != null
                    val sharedImageModifier = if (canShare) {
                        SideEffect { onSharedImageReady(item.id, returnAttempt) }
                        with(sharedTransitionScope) {
                            val sharedState = rememberSharedContentState(key = "gallery-image-${item.id}")
                            if (BuildConfig.DEBUG) {
                                LaunchedEffect(item.id, sharedState.isMatchFound) {
                                    Log.d("GalleryTransition", "viewer id=${item.id} matched=${sharedState.isMatchFound}")
                                }
                            }
                            Modifier.sharedElement(
                                sharedState,
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ -> tween(260, easing = FastOutSlowInEasing) }
                            )
                        }
                    } else Modifier
                    ZoomableImage(
                        model = item.uri,
                        contentDescription = item.displayName,
                        imageModifier = sharedImageModifier,
                        imageContentScale = gallerySharedImageScale(
                            visibilityScope = animatedVisibilityScope,
                            inViewer = true,
                            isShared = canShare,
                            tileSize = sharedTileSize,
                            viewerSize = sharedViewerSize
                        ),
                        previewCacheKey = previewKey,
                        suppressPreviewCrossfade = sharedMediaId == item.id,
                        onTap = { showControls = !showControls }
                    )
                }
            }

            if (showViewerDetails && currentItem != null) {
                ViewerDetailsBadge(
                    sizeBytes = currentItem.sizeBytes,
                    width = currentItem.width,
                    height = currentItem.height,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 16.dp, top = 68.dp)
                )
            }
            // Top Bar
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { -it / 5 },
                exit = fadeOut(tween(160)) +
                    slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { -it / 5 },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.30f))
                            )
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        IconButton(onClick = requestBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${pagerState.currentPage + 1} of ${mediaList.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = currentItem?.displayName ?: "",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { showInfoSheet = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                        }
                    }
                }
            }

            // Bottom Bar
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 5 },
                exit = fadeOut(tween(160)) +
                    slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it / 5 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.82f))
                            )
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 12.dp)
                    ) {
                        ViewerActionButton(
                            icon = if (currentItem?.isFavorite == true) Icons.Default.Star else Icons.Default.StarBorder,
                            label = if (currentItem?.isFavorite == true) "Unfavorite" else "Favorite",
                            onClick = {
                                currentItem?.let {
                                    photosViewModel.requestFavorite(listOf(it), !it.isFavorite)
                                }
                            }
                        )
                        // Move to Locked Folder
                        ViewerActionButton(
                            icon = Icons.Default.Lock,
                            label = "Lock",
                            onClick = {
                                currentItem?.let {
                                    photosViewModel.clearSelection()
                                    photosViewModel.toggleItemSelection(it.id)
                                    photosViewModel.openMovePicker()
                                }
                            },
                            modifier = Modifier.testTag("viewer_move_to_vault_button")
                        )

                        // Share
                        ViewerActionButton(
                            icon = Icons.Default.Share,
                            label = "Share",
                            onClick = {
                                currentItem?.let {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = it.mimeType
                                        putExtra(Intent.EXTRA_STREAM, it.uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share"))
                                }
                            },
                            modifier = Modifier.testTag("viewer_share_button")
                        )

                        // Delete
                        ViewerActionButton(
                            icon = Icons.Default.Delete,
                            label = "Trash",
                            onClick = {
                                currentItem?.let {
                                    photosViewModel.clearSelection()
                                    photosViewModel.toggleItemSelection(it.id)
                                    photosViewModel.requestDeleteSelected()
                                }
                            },
                            modifier = Modifier.testTag("viewer_delete_button")
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp)
            )
        }

        if (showInfoSheet && currentItem != null) {
            MediaInfoSheet(
                fileName = currentItem.displayName,
                mimeType = currentItem.mimeType,
                sizeBytes = currentItem.sizeBytes,
                dateTaken = currentItem.dateTaken,
                width = currentItem.width,
                height = currentItem.height,
                durationMs = currentItem.durationMs,
                albumName = currentItem.bucketName,
                sheetState = infoSheetState,
                onDismiss = { showInfoSheet = false }
            )
        }

        if (photosState.isMovePickerOpen) {
            VaultFolderPickerSheet(
                vaults = photosState.vaults,
                itemCount = 1,
                sheetState = moveSheetState,
                onDismiss = { photosViewModel.closeMovePicker() },
                onCreateNewFolder = {
                    photosViewModel.closeMovePicker()
                    photosViewModel.openCreateVaultDialog()
                },
                onConfirmMove = { vault -> photosViewModel.onVaultSelectedToMove(vault) }
            )
        }

        if (photosState.isCreateVaultDialogOpen) {
            CreateVaultDialog(
                title = "Create Locked Folder",
                onDismiss = { photosViewModel.closeCreateVaultDialog() },
                onConfirm = { name -> photosViewModel.createVault(name) }
            )
        }

        if (photosState.showFirstLockExplanation) {
            FirstLockExplanationDialog(
                onDismiss = { photosViewModel.onDismissFirstLockExplanation() },
                onProceed = { dontShowAgain -> photosViewModel.onProceedFirstLockExplanation(dontShowAgain) }
            )
        }
    }
}

@Composable
fun VaultMediaItemView(
    item: VaultItemEntity,
    app: PrivateGalleryApplication,
    isActive: Boolean,
    showControls: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoading by remember(item.id) { mutableStateOf(true) }
    var decryptedFile by remember(item.id) { mutableStateOf<File?>(null) }
    var hasError by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        isLoading = true
        hasError = false
        val file = app.vaultTransferManager.prepareDecryptedViewerFile(item)
        if (file != null && file.exists() && file.length() > 0) {
            decryptedFile = file
            isLoading = false
        } else {
            hasError = true
            isLoading = false
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            decryptedFile != null -> {
                val readyFile = decryptedFile ?: return@Box
                if (item.mediaType == 2) {
                    VideoPlayer(
                        uri = Uri.fromFile(readyFile),
                        playWhenReady = isActive,
                        showControls = showControls,
                        onToggleControls = onTap
                    )
                } else {
                    ZoomableImage(
                        model = readyFile,
                        contentDescription = item.originalFileName,
                        onTap = onTap
                    )
                }
            }
            isLoading -> {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            hasError -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Unable to decrypt media",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun ViewerActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    textColor: Color = Color.White
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ViewerDetailsBadge(
    sizeBytes: Long,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier
) {
    val resolution = if (width > 0 && height > 0) "$width × $height" else null
    val size = if (sizeBytes > 0L) formatFileSize(sizeBytes) else null
    val details = listOfNotNull(resolution, size).joinToString("  •  ")
    if (details.isEmpty()) return

    Surface(
        color = Color.Black.copy(alpha = 0.64f),
        contentColor = Color.White,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Text(
            text = details,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}
