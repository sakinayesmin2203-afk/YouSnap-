package com.example.ui.components

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun CdnVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    isLooping: Boolean = true,
    isMuted: Boolean = false,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    trackProgress: Boolean = false,
    onFirstFrameRendered: () -> Unit = {},
    onBufferingChanged: (Boolean) -> Unit = {},
    onVideoSizeChanged: (width: Int, height: Int) -> Unit = { _, _ -> },
    onProgressChanged: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    onVideoCompleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentOnFirstFrame by rememberUpdatedState(onFirstFrameRendered)
    val currentOnBufferingChanged by rememberUpdatedState(onBufferingChanged)
    val currentOnVideoSizeChanged by rememberUpdatedState(onVideoSizeChanged)
    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnVideoCompleted by rememberUpdatedState(onVideoCompleted)

    val audioAttributes = remember {
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }

    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)
            repeatMode = if (isLooping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            setAudioAttributes(audioAttributes, !isMuted)
            volume = if (isMuted) 0f else 1f
            prepare()
            playWhenReady = autoPlay
        }
    }

    LaunchedEffect(exoPlayer, autoPlay) {
        exoPlayer.playWhenReady = autoPlay
    }

    LaunchedEffect(exoPlayer, isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
        exoPlayer.setAudioAttributes(audioAttributes, !isMuted)
    }

    LaunchedEffect(exoPlayer, isLooping) {
        exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    LaunchedEffect(exoPlayer, trackProgress, autoPlay) {
        if (trackProgress) {
            while (true) {
                val duration = exoPlayer.duration.coerceAtLeast(0L)
                val position = exoPlayer.currentPosition.coerceAtLeast(0L)
                if (duration > 0L) {
                    currentOnProgressChanged(position, duration)
                }
                delay(200L)
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                currentOnFirstFrame()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    currentOnVideoSizeChanged(videoSize.width, videoSize.height)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                currentOnBufferingChanged(playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    currentOnFirstFrame()
                    val duration = exoPlayer.duration.coerceAtLeast(0L)
                    if (duration > 0L) {
                        currentOnProgressChanged(exoPlayer.currentPosition.coerceAtLeast(0L), duration)
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    currentOnVideoCompleted()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                this.resizeMode = resizeMode
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setKeepContentOnPlayerReset(true)
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { playerView ->
            if (playerView.player !== exoPlayer) {
                playerView.player = exoPlayer
            }
            if (playerView.resizeMode != resizeMode) {
                playerView.resizeMode = resizeMode
            }
        }
    )
}
