package com.example.ui.albums

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PrivateGalleryApplication
import com.example.data.model.Album
import com.example.data.model.MediaItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AlbumsUiState(
    val albums: List<Album> = emptyList(),
    val allMedia: List<MediaItem> = emptyList(),
    val favoriteMedia: List<MediaItem> = emptyList(),
    val trashMedia: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true
)

class AlbumsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PrivateGalleryApplication
    private val mediaStoreRepository = app.mediaStoreRepository

    val uiState: StateFlow<AlbumsUiState> = combine(
        mediaStoreRepository.observeAllMedia(),
        mediaStoreRepository.observeTrashMedia()
    ) { media, trash ->
            val derived = mediaStoreRepository.deriveAlbums(media)
            AlbumsUiState(
                albums = derived,
                allMedia = media,
                favoriteMedia = media.filter { it.isFavorite },
                trashMedia = trash,
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AlbumsUiState()
        )

    fun getMediaForAlbum(bucketId: Long): List<MediaItem> {
        return uiState.value.allMedia.filter { it.bucketId == bucketId }
    }
}
