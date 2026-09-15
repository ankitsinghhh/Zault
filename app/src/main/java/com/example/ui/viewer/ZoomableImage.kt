package com.example.ui.viewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    imageContentScale: ContentScale = ContentScale.Fit,
    previewCacheKey: String? = null,
    suppressPreviewCrossfade: Boolean = false,
    onTap: () -> Unit = {}
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isLoaded by remember(model) { mutableStateOf(false) }
    val revealProgress by animateFloatAsState(
        targetValue = if (isLoaded) 1f else 0f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "viewerImageReveal"
    )

    LaunchedEffect(model) {
        scale = 1f
        offset = Offset.Zero
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            // Center zoom on tap location
                            offset = Offset(
                                (size.width / 2f - tapOffset.x) * 1.5f,
                                (size.height / 2f - tapOffset.y) * 1.5f
                            )
                        }
                    },
                    onTap = { onTap() }
                )
            }
            .pointerInput(Unit) {
                // Keep one gesture handler alive throughout a pinch. Restarting it as
                // soon as scale crossed 1f lost the original down event mid-gesture.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        val wasZoomed = scale > 1.05f
                        val pinch = pressed.size >= 2
                        if (wasZoomed || pinch) {
                            val newScale = (scale * if (pinch) event.calculateZoom() else 1f)
                                .coerceIn(1f, 5f)
                            if (newScale > 1.05f) {
                                val pan = event.calculatePan()
                                val maxPanX = size.width * (newScale - 1f) / 2f
                                val maxPanY = size.height * (newScale - 1f) / 2f
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                    y = (offset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                )
                                scale = newScale
                                pressed.forEach { it.consume() }
                            } else if (wasZoomed) {
                                scale = 1f
                                offset = Offset.Zero
                                pressed.forEach { it.consume() }
                            }
                        }
                        // At 1x a one-finger drag remains unconsumed for the pager.
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(model)
                // A shared image already has its own motion. Fading a cached preview
                // into the full image at the same time draws both bitmaps in flight.
                .crossfade(if (previewCacheKey != null && !suppressPreviewCrossfade) 160 else 0)
                .apply {
                    if (previewCacheKey != null) placeholderMemoryCacheKey(previewCacheKey)
                }
                .build(),
            contentDescription = contentDescription,
            contentScale = imageContentScale,
            onSuccess = { isLoaded = true },
            modifier = Modifier
                .then(imageModifier)
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = if (previewCacheKey != null) scale else scale * (0.985f + revealProgress * 0.015f),
                    scaleY = if (previewCacheKey != null) scale else scale * (0.985f + revealProgress * 0.015f),
                    translationX = offset.x,
                    translationY = offset.y,
                    alpha = if (previewCacheKey != null) 1f else revealProgress
                )
        )
    }
}
