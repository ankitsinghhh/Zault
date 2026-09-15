package com.example.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.IntSize

/** Morphs the image itself between the square crop and the fitted viewer image. */
@Composable
fun gallerySharedImageScale(
    visibilityScope: AnimatedVisibilityScope?,
    inViewer: Boolean,
    isShared: Boolean,
    tileSize: IntSize = IntSize.Zero,
    viewerSize: IntSize = IntSize.Zero
): ContentScale {
    if (!isShared || visibilityScope == null) {
        return if (inViewer) ContentScale.Fit else ContentScale.Crop
    }
    val fitFraction by visibilityScope.transition.animateFloat(
        transitionSpec = { tween(260, easing = FastOutSlowInEasing) },
        label = if (inViewer) "viewerImageScale" else "tileImageScale"
    ) { state ->
        if (state == EnterExitState.Visible) {
            if (inViewer) 1f else 0f
        } else {
            if (inViewer) 0f else 1f
        }
    }
    return remember(fitFraction, tileSize, viewerSize) {
        MorphingContentScale(fitFraction, tileSize, viewerSize)
    }
}

private class MorphingContentScale(
    private val fitFraction: Float,
    private val tileSize: IntSize,
    private val viewerSize: IntSize
) : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
        // Scale the bitmap between its actual endpoint sizes, rather than
        // recalculating Crop/Fit inside the moving shared bounds. On a tall
        // viewport, doing the latter zooms the bitmap in before shrinking it.
        val hasEndpoints = tileSize.width > 0 && tileSize.height > 0 &&
            viewerSize.width > 0 && viewerSize.height > 0
        val tileBounds = if (hasEndpoints) {
            Size(tileSize.width.toFloat(), tileSize.height.toFloat())
        } else dstSize
        val viewerBounds = if (hasEndpoints) {
            Size(viewerSize.width.toFloat(), viewerSize.height.toFloat())
        } else dstSize
        val crop = ContentScale.Crop.computeScaleFactor(srcSize, tileBounds)
        val fit = ContentScale.Fit.computeScaleFactor(srcSize, viewerBounds)
        val fraction = fitFraction.coerceIn(0f, 1f)
        return ScaleFactor(
            crop.scaleX + (fit.scaleX - crop.scaleX) * fraction,
            crop.scaleY + (fit.scaleY - crop.scaleY) * fraction
        )
    }
}
