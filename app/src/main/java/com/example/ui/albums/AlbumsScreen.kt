package com.example.ui.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Album
import com.example.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    viewModel: AlbumsViewModel,
    onAlbumClick: (bucketId: Long, albumName: String) -> Unit,
    onFavoritesClick: () -> Unit,
    onTrashClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val gridState = rememberLazyGridState()

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = "Albums",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize().testTag("albums_grid")
                ) {
                    item(key = "favorites") {
                        SpecialCollectionCard(
                            title = "Favorites",
                            count = uiState.favoriteMedia.size,
                            icon = { Icon(Icons.Default.Star, contentDescription = null) },
                            onClick = onFavoritesClick
                        )
                    }
                    item(key = "trash") {
                        SpecialCollectionCard(
                            title = "Trash",
                            count = uiState.trashMedia.size,
                            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = onTrashClick
                        )
                    }
                    items(uiState.albums, key = { it.bucketId }, contentType = { "album" }) { album ->
                        AlbumCard(
                            album = album,
                            isScrolling = gridState.isScrollInProgress,
                            onClick = { onAlbumClick(album.bucketId, album.name) }
                        )
                    }
            }
        }
    }
}

@Composable
private fun SpecialCollectionCard(
    title: String,
    count: Int,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) { icon() }
            }
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    "$count ${if (count == 1) "item" else "items"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    isScrolling: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var useSharpPreview by remember(album.bucketId, album.coverUri) { mutableStateOf(!isScrolling) }
    LaunchedEffect(isScrolling) { if (!isScrolling) useSharpPreview = true }
    val coverKey = "album-cover-${album.bucketId}-${album.coverUri}"
    val coverRequest = remember(album.coverUri, useSharpPreview) {
        album.coverUri?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(if (useSharpPreview) 140 else 0)
                .memoryCacheKey("$coverKey-${if (useSharpPreview) "sharp" else "quick"}")
                .apply { if (useSharpPreview) placeholderMemoryCacheKey("$coverKey-quick") }
                .size(if (useSharpPreview) 400 else 180, if (useSharpPreview) 400 else 180)
                .build()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("album_card_${album.bucketId}")
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (album.coverUri != null) {
                    AsyncImage(
                        model = coverRequest,
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Text(
            text = album.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = "${album.itemCount} ${if (album.itemCount == 1) "item" else "items"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
