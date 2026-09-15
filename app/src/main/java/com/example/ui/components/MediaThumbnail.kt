@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.example.ui.components

import android.util.Log

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.data.model.MediaItem
import com.example.BuildConfig
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumbnail(
    item: MediaItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTileSizeChanged: (IntSize) -> Unit = {},
    dragSelectionEnabled: Boolean = false,
    isScrolling: Boolean = false,
    sharedMediaId: Long? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedTileSize: IntSize = IntSize.Zero,
    sharedViewerSize: IntSize = IntSize.Zero,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // A newly composed tile in a fling gets a lightweight preview first. Already
    // sharp visible tiles are never downgraded when the user starts scrolling.
    var useSharpPreview by remember(item.id) { mutableStateOf(!isScrolling) }
    LaunchedEffect(isScrolling) {
        if (!isScrolling) useSharpPreview = true
    }
    val quality = if (useSharpPreview) "sharp" else "quick"
    val cacheKey = thumbnailCacheKey(item, quality)
    val isSharedImage = sharedMediaId == item.id && !item.isVideo
    val imageRequest = remember(item.uri, item.isVideo, item.durationMs, item.dateModified, item.sizeBytes, quality, isSharedImage) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .crossfade(if (useSharpPreview && !isSharedImage) 140 else 0)
            .memoryCacheKey(cacheKey)
            .apply {
                if (useSharpPreview) placeholderMemoryCacheKey(thumbnailCacheKey(item, "quick"))
            }
            .size(if (useSharpPreview) 300 else 128, if (useSharpPreview) 300 else 128)
            .apply {
                if (item.isVideo) {
                    videoFrameMillis((item.durationMs / 10L).coerceIn(250L, 2_000L))
                }
            }
            .build()
    }
    val selectedScale by animateFloatAsState(
        targetValue = if (isSelected) 0.92f else 1f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "mediaSelectionScale"
    )
    val sharedImageModifier = if (sharedMediaId == item.id && sharedTransitionScope != null &&
        animatedVisibilityScope != null && !item.isVideo) {
        with(sharedTransitionScope) {
            val sharedState = rememberSharedContentState(key = "gallery-image-${item.id}")
            if (BuildConfig.DEBUG) {
                LaunchedEffect(item.id, sharedState.isMatchFound) {
                    Log.d("GalleryTransition", "tile id=${item.id} matched=${sharedState.isMatchFound}")
                }
            }
            Modifier.sharedElement(
                sharedState,
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> tween(260, easing = FastOutSlowInEasing) }
            )
        }
    } else Modifier

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.5.dp)
            .onSizeChanged(onTileSizeChanged)
            .graphicsLayer {
                scaleX = selectedScale
                scaleY = selectedScale
            }
            .clip(RoundedCornerShape(if (isSelected) 12.dp else 4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(if (isSelected) 12.dp else 4.dp)
            )
            .then(
                if (dragSelectionEnabled) {
                    Modifier.clickable {
                        if (isSelectionMode) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                } else {
                    Modifier.combinedClickable(
                        onClick = {
                            if (isSelectionMode) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick()
                        }
                    )
                }
            )
            .testTag("media_item_${item.id}")
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.displayName,
            contentScale = gallerySharedImageScale(
                visibilityScope = animatedVisibilityScope,
                inViewer = false,
                isShared = isSharedImage && sharedTransitionScope != null,
                tileSize = sharedTileSize,
                viewerSize = sharedViewerSize
            ),
            modifier = Modifier.then(sharedImageModifier).fillMaxSize()
        )

        // Video Duration Badge
        if (item.isVideo) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically
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
        }

        if (item.isFavorite && !isSelectionMode) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Favorite",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(3.dp)
            )
        }

        // Selection overlay and badge
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else Color.Transparent
                    )
            )

            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.65f),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.65f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Box(
                    modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(
                        width = 1.5.dp,
                        color = Color.White,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

fun thumbnailCacheKey(item: MediaItem, quality: String): String =
    "gallery-${item.uri}-${item.dateModified}-${item.sizeBytes}-${item.durationMs}-$quality"

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        val remMinutes = minutes % 60
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, remMinutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
