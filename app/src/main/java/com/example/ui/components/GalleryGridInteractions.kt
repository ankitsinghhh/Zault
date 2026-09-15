package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.model.MediaItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Uses actual thumbnail bounds for grids with scrolling headers and content insets. */
class GalleryHitGeometry {
    private var gridCoordinates: LayoutCoordinates? = null
    private val itemBounds = HashMap<Long, Rect>()

    fun recordGrid(coordinates: LayoutCoordinates) {
        gridCoordinates = coordinates
    }

    fun recordItem(id: Long, boundsInWindow: Rect) {
        itemBounds[id] = boundsInWindow
    }

    fun itemIdAt(position: Offset, visibleItems: List<androidx.compose.foundation.lazy.grid.LazyGridItemInfo>): Long? {
        val coordinates = gridCoordinates ?: return null
        if (!coordinates.isAttached) return null
        val windowPosition = coordinates.localToWindow(position)
        for (item in visibleItems) {
            val id = item.key as? Long ?: continue
            if (itemBounds[id]?.contains(windowPosition) == true) return id
        }
        return null
    }
}

fun Modifier.dragSelectMedia(
    gridState: LazyGridState,
    media: List<MediaItem>,
    selectedIds: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    hitGeometry: GalleryHitGeometry? = null
): Modifier = dragSelectItems(gridState, media, selectedIds, onSelectionChange, hitGeometry) { it.id }

fun <T> Modifier.dragSelectItems(
    gridState: LazyGridState,
    items: List<T>,
    selectedIds: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    hitGeometry: GalleryHitGeometry? = null,
    itemId: (T) -> Long
): Modifier = composed {
    val latestSelectedIds by rememberUpdatedState(selectedIds)
    val latestIdOf by rememberUpdatedState(itemId)
    val itemIndexById = remember(items, itemId) {
        items.mapIndexed { index, item -> itemId(item) to index }.toMap()
    }
    val dragHaptic = LocalHapticFeedback.current
    val autoScrollScope = rememberCoroutineScope()
    val geometryModifier = if (hitGeometry != null) {
        Modifier.onGloballyPositioned(hitGeometry::recordGrid)
    } else Modifier
    geometryModifier.pointerInput(gridState, items, hitGeometry) {
    var anchorIndex = -1
    var baseline = emptySet<Long>()
    var lastIndex = -1
    var autoScrollJob: kotlinx.coroutines.Job? = null

    fun mediaIndexAt(position: Offset): Int {
        val visibleItems = gridState.layoutInfo.visibleItemsInfo
        if (hitGeometry != null) {
            return hitGeometry.itemIdAt(position, visibleItems)?.let(itemIndexById::get) ?: -1
        }
        val visible = visibleItems.firstOrNull { info ->
            info.key is Long &&
                position.x >= info.offset.x && position.x <= info.offset.x + info.size.width &&
                position.y >= info.offset.y && position.y <= info.offset.y + info.size.height
        } ?: return -1
        return itemIndexById[visible.key as Long] ?: -1
    }

    detectDragGesturesAfterLongPress(
        onDragStart = { position ->
            dragHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
            baseline = latestSelectedIds
            anchorIndex = mediaIndexAt(position)
            lastIndex = anchorIndex
            if (anchorIndex >= 0) {
                onSelectionChange(baseline + latestIdOf(items[anchorIndex]))
            }
        },
        onDragCancel = {
            autoScrollJob?.cancel()
            anchorIndex = -1
            lastIndex = -1
        },
        onDragEnd = {
            autoScrollJob?.cancel()
            anchorIndex = -1
            lastIndex = -1
        },
        onDrag = { change, _ ->
            change.consume()
            val currentIndex = mediaIndexAt(change.position)
            if (anchorIndex >= 0 && currentIndex >= 0 && currentIndex != lastIndex) {
                val range = if (anchorIndex <= currentIndex) anchorIndex..currentIndex
                    else currentIndex..anchorIndex
                onSelectionChange(baseline + range.map { latestIdOf(items[it]) })
                dragHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                lastIndex = currentIndex
            }
            val edge = 120f
            val delta = when {
                change.position.y < edge -> -34f
                change.position.y > size.height - edge -> 34f
                else -> 0f
            }
            if (delta != 0f && autoScrollJob?.isActive != true) {
                autoScrollJob = autoScrollScope.launch { gridState.scrollBy(delta) }
            }
        }
    )
    }
}

@Composable
fun BoxScope.GalleryFastScroller(
    gridState: LazyGridState,
    media: List<MediaItem>,
    modifier: Modifier = Modifier
) {
    if (media.size < 8) return
    val scrollScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    val layoutInfo = gridState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount.coerceAtLeast(1)
    val firstIndex = gridState.firstVisibleItemIndex.coerceAtMost(totalItems - 1)
    val fraction = if (totalItems <= 1) 0f else firstIndex.toFloat() / (totalItems - 1).toFloat()
    val mediaById = remember(media) { media.associateBy { it.id } }
    val currentMedia = layoutInfo.visibleItemsInfo
        .firstNotNullOfOrNull { info -> (info.key as? Long)?.let(mediaById::get) }
        ?: media.firstOrNull()
    val dateLabel = currentMedia?.let { galleryDateLabel(it.dateTaken) }.orEmpty()

    AnimatedVisibility(
        visible = gridState.isScrollInProgress || dragging,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(450)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 10.dp)
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.82f),
            contentColor = Color.White,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 6.dp
        ) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight(0.7f)
            .width(52.dp)
            .padding(vertical = 8.dp)
    ) {
        // Keep geometry stable for the entire gesture. Re-keying pointerInput from
        // an animated thumb height cancelled some presses in the previous version.
        val thumbHeight = 52.dp
        val thumbWidth by animateDpAsState(
            targetValue = if (dragging) 36.dp else 30.dp,
            animationSpec = tween(160, easing = FastOutSlowInEasing),
            label = "fastScrollerWidth"
        )
        val availablePx = with(density) { (maxHeight - thumbHeight).toPx().coerceAtLeast(1f) }
        val thumbHeightPx = with(density) { thumbHeight.toPx() }
        val currentItemCount by rememberUpdatedState(totalItems)
        val currentAvailablePx by rememberUpdatedState(availablePx)
        val currentThumbHeightPx by rememberUpdatedState(thumbHeightPx)
        val yPx = availablePx * fraction

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var scrollJob: kotlinx.coroutines.Job? = null
                    var lastTarget = -1
                    fun scrollForPointer(pointerY: Float) {
                        val thumbTop = (pointerY - currentThumbHeightPx / 2f)
                            .coerceIn(0f, currentAvailablePx)
                        val targetFraction = thumbTop / currentAvailablePx
                        val target = (targetFraction * (currentItemCount - 1)).roundToInt()
                        if (target != lastTarget) {
                            lastTarget = target
                            scrollJob?.cancel()
                            scrollJob = scrollScope.launch { gridState.scrollToItem(target) }
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        dragging = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scrollForPointer(down.position.y)
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                pressed = false
                            } else {
                                if (change.positionChanged()) change.consume()
                                scrollForPointer(change.position.y)
                            }
                        }
                        dragging = false
                    }
                }
        ) {
            Surface(
                color = if (dragging) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                contentColor = if (dragging) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                shape = CircleShape,
                shadowElevation = if (dragging) 8.dp else 3.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, yPx.roundToInt()) }
                    .width(thumbWidth)
                    .height(thumbHeight)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.UnfoldMore,
                        contentDescription = "Fast scroll",
                        tint = if (dragging) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                    )
                }
            }
        }
    }
}

private fun galleryDateLabel(timeMillis: Long): String {
    if (timeMillis <= 0L) return "Unknown date"
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timeMillis }
    val sameDay = now.get(Calendar.ERA) == target.get(Calendar.ERA) &&
        now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "Today"
    now.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    if (yesterday) return "Yesterday"
    return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timeMillis))
}
