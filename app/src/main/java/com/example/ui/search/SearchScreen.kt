@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.example.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MediaItem
import com.example.ui.components.EmptyState
import com.example.ui.components.MediaThumbnail
import com.example.ui.photos.PhotosViewModel

enum class SearchFilter(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    SCREENSHOTS("Screenshots"),
    DOWNLOADS("Downloads")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    photosViewModel: PhotosViewModel,
    onMediaClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val photosState by photosViewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(SearchFilter.ALL) }

    val filteredMedia = remember(photosState.mediaItems, searchQuery, selectedFilter) {
        photosState.mediaItems.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                    item.displayName.contains(searchQuery, ignoreCase = true) ||
                    item.bucketName.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                SearchFilter.ALL -> true
                SearchFilter.PHOTOS -> !item.isVideo
                SearchFilter.VIDEOS -> item.isVideo
                SearchFilter.SCREENSHOTS -> item.bucketName.contains("Screenshot", ignoreCase = true) || item.displayName.contains("Screenshot", ignoreCase = true)
                SearchFilter.DOWNLOADS -> item.bucketName.contains("Download", ignoreCase = true) || item.displayName.contains("Download", ignoreCase = true)
            }

            matchesQuery && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Search",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search photos, videos, albums...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_text_field")
            )

            // Category Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SearchFilter.values()) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Results Grid
            Box(modifier = Modifier.fillMaxSize()) {
                if (filteredMedia.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Search,
                        title = "No results found",
                        description = "Try searching for a different name, date, or category."
                    )
                } else {
                    val resultsGridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = resultsGridState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredMedia, key = { it.id }, contentType = { "media" }) { item ->
                            MediaThumbnail(
                                item = item,
                                isSelected = false,
                                isSelectionMode = false,
                                isScrolling = resultsGridState.isScrollInProgress,
                                onClick = {
                                    val index = photosState.mediaItems.indexOf(item)
                                    if (index >= 0) onMediaClick(index)
                                },
                                onLongClick = {
                                    val index = photosState.mediaItems.indexOf(item)
                                    if (index >= 0) onMediaClick(index)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
