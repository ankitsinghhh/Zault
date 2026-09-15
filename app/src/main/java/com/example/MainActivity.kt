@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.example

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.security.BiometricAuthResult
import com.example.ui.albums.AlbumDetailScreen
import com.example.ui.albums.AlbumsScreen
import com.example.ui.albums.AlbumsViewModel
import com.example.ui.albums.SpecialCollectionScreen
import com.example.ui.albums.SpecialCollectionType
import com.example.ui.components.AppLockFullScreen
import com.example.ui.components.RestoreAlbumPickerDialog
import com.example.ui.components.RestoreDestinationDialog
import com.example.data.preferences.VaultRestoreDestination
import com.example.data.preferences.VaultRestoreMode
import com.example.ui.navigation.Screen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.photos.PhotosScreen
import com.example.ui.photos.photosGridIndexOf
import com.example.ui.photos.PhotosViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.PrivateGalleryTheme
import com.example.ui.vault.VaultScreen
import com.example.ui.vault.VaultViewModel
import com.example.ui.viewer.MediaViewerScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : FragmentActivity() {
    private var isAppUnlocked by mutableStateOf(false)
    private var isAppReady by mutableStateOf(false)
    private var lockSessionId by mutableIntStateOf(0)

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            isAppUnlocked = false
            isAppReady = false
            lockSessionId++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        val app = application as PrivateGalleryApplication

        setContent {
            val scope = rememberCoroutineScope()
            val secureScreen by app.preferencesRepository.secureScreenFlow.collectAsState(initial = true)
            val themeMode by app.preferencesRepository.themeModeFlow.collectAsState(initial = 0)
            val themePalette by app.preferencesRepository.themePaletteFlow.collectAsState(initial = 0)
            val appLockConfig by app.preferencesRepository.appLockConfigFlow.collectAsState(initial = null)

            val isDarkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            // Handle FLAG_SECURE on window
            LaunchedEffect(secureScreen) {
                if (secureScreen) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            PrivateGalleryTheme(
                darkTheme = isDarkTheme,
                paletteId = themePalette
            ) {
                val lockConfig = appLockConfig
                if (lockConfig == null) {
                    Box(Modifier.fillMaxSize())
                } else {
                    val lockRequired = lockConfig.first
                    Box(Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = !lockRequired || isAppUnlocked,
                        enter = if (lockRequired) fadeIn(tween(260, easing = FastOutSlowInEasing))
                            else EnterTransition.None,
                        exit = ExitTransition.None
                    ) {
                    val haptic = LocalHapticFeedback.current
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val photosGridState = rememberLazyGridState()
                    val favoritesGridState = rememberLazyGridState()
                    val activeAlbumId = when (currentRoute) {
                        Screen.AlbumDetail.route -> navBackStackEntry?.arguments?.getLong("bucketId")
                        Screen.MediaViewer.route -> navBackStackEntry?.arguments
                            ?.getString("bucketId")?.toLongOrNull()
                        else -> null
                    }
                    val albumGridState = remember(activeAlbumId) { LazyGridState() }

                    val photosViewModel: PhotosViewModel = viewModel()
                    val albumsViewModel: AlbumsViewModel = viewModel()
                    val vaultViewModel: VaultViewModel = viewModel()
                    var sharedMediaId by rememberSaveable { mutableStateOf<Long?>(null) }
                    var sharedReturnAttempt by remember { mutableIntStateOf(0) }
                    var readySharedMedia by remember { mutableStateOf<Pair<Long, Int>?>(null) }
                    var sharedTileSize by remember { mutableStateOf(IntSize.Zero) }
                    var galleryViewportSize by remember { mutableStateOf(IntSize.Zero) }
                    var viewerReturnInProgress by remember { mutableStateOf(false) }
                    LaunchedEffect(currentRoute) {
                        if (currentRoute != Screen.MediaViewer.route) {
                            // Keep the return guarded and the shared key alive until
                            // the viewer's exit animation has completed.
                            delay(360)
                            viewerReturnInProgress = false
                            sharedMediaId = null
                            readySharedMedia = null
                        }
                    }
                    val pendingRestoreItems by vaultViewModel.pendingRestoreItems.collectAsState()
                    var showRestoreAlbumPicker by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(lockRequired, isAppUnlocked, lockSessionId) {
                        if (lockRequired && isAppUnlocked && !isAppReady) {
                            withTimeoutOrNull(5000L) {
                                photosViewModel.uiState.first { !it.isLoading }
                            }
                            withFrameNanos { }
                            isAppReady = true
                        }
                    }
                    var compactBottomNav by rememberSaveable { mutableStateOf(false) }
                    val navHorizontalPadding by animateDpAsState(
                        targetValue = if (compactBottomNav) 30.dp else 12.dp,
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        label = "bottomNavWidth"
                    )
                    val navHeight by animateDpAsState(
                        targetValue = if (compactBottomNav) 62.dp else 80.dp,
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                        label = "bottomNavHeight"
                    )
                    val nestedScrollConnection = androidx.compose.runtime.remember {
                        object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                if (available.y < -2f) compactBottomNav = true
                                else if (available.y > 2f) compactBottomNav = false
                                return Offset.Zero
                            }
                        }
                    }

                    val showBottomNav = currentRoute in listOf(
                        Screen.Photos.route,
                        Screen.Albums.route,
                        Screen.Vault.route,
                        Screen.Settings.route
                    )

                    Scaffold(
                        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            if (showBottomNav) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                    tonalElevation = 6.dp,
                                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                                    modifier = Modifier
                                        .padding(horizontal = navHorizontalPadding, vertical = 8.dp)
                                        .height(navHeight)
                                        .clip(RoundedCornerShape(36.dp))
                                ) {
                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                if (currentRoute == Screen.Photos.route) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                                                contentDescription = "Photos"
                                            )
                                        },
                                        label = if (compactBottomNav) null else ({ Text("Photos", fontWeight = if (currentRoute == Screen.Photos.route) FontWeight.Bold else FontWeight.Normal) }),
                                        selected = currentRoute == Screen.Photos.route,
                                        onClick = {
                                            if (currentRoute != Screen.Photos.route) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            navController.navigate(Screen.Photos.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        modifier = Modifier.testTag("nav_photos")
                                    )

                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                if (currentRoute == Screen.Albums.route) Icons.Filled.Folder else Icons.Outlined.Folder,
                                                contentDescription = "Albums"
                                            )
                                        },
                                        label = if (compactBottomNav) null else ({ Text("Albums", fontWeight = if (currentRoute == Screen.Albums.route) FontWeight.Bold else FontWeight.Normal) }),
                                        selected = currentRoute == Screen.Albums.route,
                                        onClick = {
                                            if (currentRoute != Screen.Albums.route) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            navController.navigate(Screen.Albums.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        modifier = Modifier.testTag("nav_albums")
                                    )

                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                if (currentRoute == Screen.Vault.route) Icons.Filled.Lock else Icons.Outlined.Lock,
                                                contentDescription = "Vault"
                                            )
                                        },
                                        label = if (compactBottomNav) null else ({ Text("Vault", fontWeight = if (currentRoute == Screen.Vault.route) FontWeight.Bold else FontWeight.Normal) }),
                                        selected = currentRoute == Screen.Vault.route,
                                        onClick = {
                                            if (currentRoute != Screen.Vault.route) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            navController.navigate(Screen.Vault.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        modifier = Modifier.testTag("nav_vault")
                                    )

                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                if (currentRoute == Screen.Settings.route) Icons.Filled.Settings else Icons.Outlined.Settings,
                                                contentDescription = "Settings"
                                            )
                                        },
                                        label = if (compactBottomNav) null else ({ Text("Settings", fontWeight = if (currentRoute == Screen.Settings.route) FontWeight.Bold else FontWeight.Normal) }),
                                        selected = currentRoute == Screen.Settings.route,
                                        onClick = {
                                            if (currentRoute != Screen.Settings.route) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            navController.navigate(Screen.Settings.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        modifier = Modifier.testTag("nav_settings")
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)
                    ) { _ ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // Main tab content deliberately continues behind the floating bar.
                        ) {
                            SharedTransitionLayout(
                                modifier = Modifier.fillMaxSize()
                                    .onSizeChanged { galleryViewportSize = it }
                            ) {
                            NavHost(
                                navController = navController,
                                startDestination = Screen.Photos.route,
                                enterTransition = {
                                    fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                                        scaleIn(
                                            initialScale = 0.97f,
                                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                                        )
                                },
                                exitTransition = {
                                    if (targetState.destination.route == Screen.MediaViewer.route) {
                                        fadeOut(tween(220, easing = FastOutSlowInEasing))
                                    } else fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                                        scaleOut(
                                            targetScale = 0.985f,
                                            animationSpec = tween(180, easing = FastOutSlowInEasing)
                                        )
                                },
                                popEnterTransition = {
                                    if (initialState.destination.route == Screen.MediaViewer.route) {
                                        fadeIn(tween(260, easing = FastOutSlowInEasing))
                                    } else fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                                        scaleIn(
                                            initialScale = 0.985f,
                                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                                        )
                                },
                                popExitTransition = {
                                    fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                                        scaleOut(
                                            targetScale = 0.97f,
                                            animationSpec = tween(180, easing = FastOutSlowInEasing)
                                        )
                                }
                            ) {
                                composable(Screen.Photos.route) {
                                    PhotosScreen(
                                        viewModel = photosViewModel,
                                        onMediaClick = { index, mediaId, tileSize ->
                                            if (BuildConfig.DEBUG) Log.d("GalleryTransition", "open id=$mediaId source=photos index=$index")
                                            sharedMediaId = mediaId
                                            readySharedMedia = null
                                            sharedTileSize = tileSize
                                            navController.navigate(Screen.MediaViewer.createRoute(source = "photos", initialIndex = index, mediaId = mediaId))
                                        },
                                        sharedMediaId = sharedMediaId,
                                        sharedTileSize = sharedTileSize,
                                        sharedViewerSize = galleryViewportSize,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@composable,
                                        gridState = photosGridState,
                                        onNavigateToSettings = {
                                            navController.navigate(Screen.Settings.route)
                                        }
                                    )
                                }

                                composable(Screen.Albums.route) {
                                    AlbumsScreen(
                                        viewModel = albumsViewModel,
                                        onAlbumClick = { bucketId, name ->
                                            navController.navigate(Screen.AlbumDetail.createRoute(bucketId, name))
                                        },
                                        onFavoritesClick = { navController.navigate(Screen.Favorites.route) },
                                        onTrashClick = { navController.navigate(Screen.Trash.route) }
                                    )
                                }

                                composable(Screen.Favorites.route) {
                                    SpecialCollectionScreen(
                                        type = SpecialCollectionType.Favorites,
                                        albumsViewModel = albumsViewModel,
                                        photosViewModel = photosViewModel,
                                        sharedMediaId = sharedMediaId,
                                        sharedTileSize = sharedTileSize,
                                        sharedViewerSize = galleryViewportSize,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@composable,
                                        gridState = favoritesGridState,
                                        onBack = { navController.popBackStack() },
                                        onMediaClick = { id, tileSize ->
                                            sharedMediaId = id
                                            readySharedMedia = null
                                            sharedTileSize = tileSize
                                            navController.navigate(
                                                Screen.MediaViewer.createRoute("favorites", 0, mediaId = id)
                                            )
                                        }
                                    )
                                }

                                composable(Screen.Trash.route) {
                                    SpecialCollectionScreen(
                                        type = SpecialCollectionType.Trash,
                                        albumsViewModel = albumsViewModel,
                                        photosViewModel = photosViewModel,
                                        onBack = { navController.popBackStack() },
                                        onMediaClick = { _, _ -> }
                                    )
                                }

                                composable(
                                    route = Screen.AlbumDetail.route,
                                    arguments = listOf(
                                        navArgument("bucketId") { type = NavType.LongType },
                                        navArgument("albumName") { type = NavType.StringType }
                                    )
                                ) { backStackEntry ->
                                    val bucketId = backStackEntry.arguments?.getLong("bucketId") ?: 0L
                                    val albumName = android.net.Uri.decode(backStackEntry.arguments?.getString("albumName") ?: "")
                                    AlbumDetailScreen(
                                        bucketId = bucketId,
                                        albumName = albumName,
                                        albumsViewModel = albumsViewModel,
                                        photosViewModel = photosViewModel,
                                        sharedMediaId = sharedMediaId,
                                        sharedTileSize = sharedTileSize,
                                        sharedViewerSize = galleryViewportSize,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@composable,
                                        gridState = albumGridState,
                                        onBack = { navController.popBackStack() },
                                        onMediaClick = { index, mediaId, tileSize ->
                                            sharedMediaId = mediaId
                                            readySharedMedia = null
                                            sharedTileSize = tileSize
                                            navController.navigate(
                                                Screen.MediaViewer.createRoute(
                                                    source = "photos",
                                                    initialIndex = index,
                                                    bucketId = bucketId,
                                                    mediaId = mediaId
                                                )
                                            )
                                        }
                                    )
                                }

                                composable(Screen.Vault.route) {
                                    VaultScreen(
                                        viewModel = vaultViewModel,
                                        onMediaClick = { vaultId, index, mediaId ->
                                            sharedMediaId = null
                                            navController.navigate(
                                                Screen.MediaViewer.createRoute(
                                                    source = "vault",
                                                    initialIndex = index,
                                                    vaultId = vaultId,
                                                    mediaId = mediaId
                                                )
                                            )
                                        }
                                    )
                                }

                                composable(Screen.Settings.route) {
                                    SettingsScreen(
                                        onBack = null
                                    )
                                }

                                composable(Screen.Onboarding.route) {
                                    OnboardingScreen(
                                        onGetStarted = {
                                            scope.launch {
                                                app.preferencesRepository.setOnboardingCompleted(true)
                                                navController.popBackStack()
                                            }
                                        }
                                    )
                                }

                                composable(
                                    route = Screen.MediaViewer.route,
                                    arguments = listOf(
                                        navArgument("source") { type = NavType.StringType },
                                        navArgument("initialIndex") { type = NavType.IntType },
                                        navArgument("bucketId") {
                                            type = NavType.StringType
                                            nullable = true
                                            defaultValue = null
                                        },
                                        navArgument("vaultId") {
                                            type = NavType.StringType
                                            nullable = true
                                            defaultValue = null
                                        },
                                        navArgument("mediaId") {
                                            type = NavType.StringType
                                            nullable = true
                                            defaultValue = null
                                        }
                                    ),
                                    enterTransition = {
                                        fadeIn(tween(260, easing = FastOutSlowInEasing))
                                    },
                                    popExitTransition = {
                                        fadeOut(tween(260, easing = FastOutSlowInEasing))
                                    }
                                ) { backStackEntry ->
                                    val source = backStackEntry.arguments?.getString("source") ?: "photos"
                                    val initialIndex = backStackEntry.arguments?.getInt("initialIndex") ?: 0
                                    val bucketIdStr = backStackEntry.arguments?.getString("bucketId")
                                    val vaultIdStr = backStackEntry.arguments?.getString("vaultId")
                                    val mediaIdStr = backStackEntry.arguments?.getString("mediaId")

                                    MediaViewerScreen(
                                        source = source,
                                        initialIndex = initialIndex,
                                        bucketId = bucketIdStr?.toLongOrNull(),
                                        vaultId = vaultIdStr?.toLongOrNull(),
                                        mediaId = mediaIdStr?.toLongOrNull(),
                                        photosViewModel = photosViewModel,
                                        vaultViewModel = vaultViewModel,
                                        sharedMediaId = sharedMediaId,
                                        sharedTileSize = sharedTileSize,
                                        sharedViewerSize = galleryViewportSize,
                                        returnAttempt = sharedReturnAttempt,
                                        onSharedImageReady = { id, attempt ->
                                            val token = id to attempt
                                            if (readySharedMedia != token) {
                                                readySharedMedia = token
                                                if (BuildConfig.DEBUG) Log.d("GalleryTransition", "viewer ready id=$id attempt=$attempt")
                                            }
                                        },
                                        onSettledMediaChanged = { settledId ->
                                            if (!viewerReturnInProgress && settledId != mediaIdStr?.toLongOrNull()) {
                                                // The opening tile must stop owning the overlay after a swipe.
                                                // Back explicitly installs a new link for the settled photo.
                                                sharedMediaId = null
                                                if (BuildConfig.DEBUG) Log.d("GalleryTransition", "clear opening key settled=$settledId opened=$mediaIdStr")
                                            }
                                        },
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@composable,
                                        onBack = { visibleMediaId ->
                                            if (!viewerReturnInProgress) {
                                                viewerReturnInProgress = true
                                                scope.launch {
                                                    val originRoute = navController.previousBackStackEntry?.destination?.route
                                                    val currentPhoto = visibleMediaId?.let { id ->
                                                        photosViewModel.uiState.value.mediaItems
                                                            .firstOrNull { it.id == id && !it.isVideo }
                                                    }
                                                    val returnGrid: LazyGridState?
                                                    val returnIndex: Int
                                                    when {
                                                        source == "vault" || currentPhoto == null -> {
                                                            returnGrid = null
                                                            returnIndex = -1
                                                        }
                                                        originRoute == Screen.Photos.route -> {
                                                            returnGrid = photosGridState
                                                            returnIndex = photosGridIndexOf(
                                                                photosViewModel.uiState.value.mediaItems,
                                                                currentPhoto.id
                                                            )
                                                        }
                                                        originRoute == Screen.AlbumDetail.route && bucketIdStr != null -> {
                                                            returnGrid = albumGridState
                                                            returnIndex = albumsViewModel.uiState.value.allMedia
                                                                .filter { it.bucketId == bucketIdStr.toLongOrNull() }
                                                                .indexOfFirst { it.id == currentPhoto.id }
                                                        }
                                                        originRoute == Screen.Favorites.route -> {
                                                            returnGrid = favoritesGridState
                                                            returnIndex = albumsViewModel.uiState.value.favoriteMedia
                                                                .indexOfFirst { it.id == currentPhoto.id }
                                                        }
                                                        else -> {
                                                            returnGrid = null
                                                            returnIndex = -1
                                                        }
                                                    }
                                                    if (returnGrid != null && returnIndex >= 0 && currentPhoto != null) {
                                                        if (BuildConfig.DEBUG) Log.d("GalleryTransition", "return id=${currentPhoto.id} origin=$originRoute index=$returnIndex visible=${returnGrid.layoutInfo.visibleItemsInfo.any { it.key == currentPhoto.id }}")
                                                        sharedReturnAttempt++
                                                        val attempt = sharedReturnAttempt
                                                        readySharedMedia = null
                                                        sharedMediaId = currentPhoto.id
                                                        if (returnGrid.layoutInfo.visibleItemsInfo.none { it.key == currentPhoto.id }) {
                                                            val columns = photosViewModel.uiState.value.gridColumns
                                                            returnGrid.requestScrollToItem(
                                                                (returnIndex - columns * 2).coerceAtLeast(0)
                                                            )
                                                            if (BuildConfig.DEBUG) Log.d("GalleryTransition", "grid requested first=${returnGrid.firstVisibleItemIndex}")
                                                        }
                                                        // Compose the newly selected viewer key before popping.
                                                        val ready = withTimeoutOrNull(220L) {
                                                            snapshotFlow { readySharedMedia }
                                                                .first { it?.first == currentPhoto.id && it.second == attempt }
                                                        }
                                                        if (BuildConfig.DEBUG) Log.d("GalleryTransition", "ready before pop id=${currentPhoto.id} success=${ready != null}")
                                                        if (BuildConfig.DEBUG) Log.d("GalleryTransition", "pop shared=$sharedMediaId")
                                                    } else {
                                                        sharedMediaId = null
                                                    }
                                                    navController.popBackStack()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                            }
                        }
                    }
                    if (pendingRestoreItems != null && !showRestoreAlbumPicker) {
                        RestoreDestinationDialog(
                            selected = null,
                            includeAlwaysAsk = false,
                            onChoose = { mode ->
                                when (mode) {
                                    VaultRestoreMode.ORIGINAL_LOCATION -> vaultViewModel.confirmRestoreDestination(VaultRestoreDestination.OriginalLocation)
                                    VaultRestoreMode.PRIVATE_GALLERY -> vaultViewModel.confirmRestoreDestination(VaultRestoreDestination.PrivateGallery)
                                    VaultRestoreMode.SELECTED_ALBUM -> showRestoreAlbumPicker = true
                                    VaultRestoreMode.ALWAYS_ASK -> Unit
                                }
                            },
                            onDismiss = { vaultViewModel.cancelRestoreDestination() }
                        )
                    }
                    if (pendingRestoreItems != null && showRestoreAlbumPicker) {
                        RestoreAlbumPickerDialog(
                            containsPhotos = pendingRestoreItems!!.any { it.mediaType != 2 },
                            containsVideos = pendingRestoreItems!!.any { it.mediaType == 2 },
                            onChoose = { album ->
                                showRestoreAlbumPicker = false
                                vaultViewModel.confirmRestoreDestination(VaultRestoreDestination.Album(album.relativePath))
                            },
                            onDismiss = { showRestoreAlbumPicker = false }
                        )
                    }
                    }
                    AnimatedVisibility(
                        visible = lockRequired && !isAppReady,
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(260, easing = FastOutSlowInEasing))
                    ) {
                        key(lockSessionId) {
                            AppLockFullScreen(
                                verifyPin = { pin -> app.preferencesRepository.verifyAppLockPin(pin, lockConfig.second) },
                                onSuccess = { isAppUnlocked = true },
                                onBiometricClick = null
                            )
                        }
                    }
                    }
                }
            }
        }
    }
}
