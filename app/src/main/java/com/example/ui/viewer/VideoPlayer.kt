package com.example.ui.viewer

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    playWhenReady: Boolean = true,
    showControls: Boolean = true,
    onToggleControls: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var positionMs by remember(uri) { mutableLongStateOf(0L) }
    var durationMs by remember(uri) { mutableLongStateOf(0L) }
    var isPlaying by remember(uri) { mutableStateOf(false) }
    var isSeeking by remember(uri) { mutableStateOf(false) }
    var seekFraction by remember(uri) { mutableFloatStateOf(0f) }
    var hasRenderedFrame by remember(uri) { mutableStateOf(false) }
    val videoReveal by animateFloatAsState(
        targetValue = if (hasRenderedFrame) 1f else 0f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "viewerVideoReveal"
    )

    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(uri))
            prepare()
            this.playWhenReady = playWhenReady
        }
    }

    LaunchedEffect(exoPlayer, playWhenReady) {
        exoPlayer.playWhenReady = playWhenReady
        if (!playWhenReady) exoPlayer.pause()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFrame = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            if (!isSeeking && durationMs > 0L) {
                seekFraction = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            }
            delay(200)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    keepScreenOn = true
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = videoReveal
                    scaleX = 0.985f + videoReveal * 0.015f
                    scaleY = 0.985f + videoReveal * 0.015f
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleControls
                )
        )

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(240, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.52f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause video" else "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(240, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 86.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.34f), CircleShape)
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(seekFraction.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(Color.White, CircleShape)
                    )
                    Box(
                        Modifier
                            .offset(x = (maxWidth - 12.dp) * seekFraction.coerceIn(0f, 1f))
                            .size(12.dp)
                            .background(Color.White, CircleShape)
                    )
                    Slider(
                        value = seekFraction,
                        onValueChange = {
                            isSeeking = true
                            seekFraction = it
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo((durationMs * seekFraction).toLong())
                            isSeeking = false
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        valueRange = 0f..1f,
                        enabled = durationMs > 0L,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Transparent,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                            disabledThumbColor = Color.Transparent,
                            disabledActiveTrackColor = Color.Transparent,
                            disabledInactiveTrackColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth().alpha(0.01f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        formatPlayerTime(if (isSeeking) (durationMs * seekFraction).toLong() else positionMs),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        formatPlayerTime(durationMs),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun formatPlayerTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
