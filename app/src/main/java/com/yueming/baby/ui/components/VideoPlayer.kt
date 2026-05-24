package com.yueming.baby.ui.components

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.yueming.baby.R
import com.yueming.baby.data.DataManager
import com.yueming.baby.data.cloud.CloudStorageConfig
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    filePath: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showCloseButton: Boolean = true,
    backgroundColor: Color = Color.Black,
    onSwipeToPrevious: (() -> Unit)? = null,
    onSwipeToNext: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val cloudConfig by DataManager.cloudStorageConfig.collectAsState()
    val legacyWebDavConfig by DataManager.webDavConfig.collectAsState()
    val swipeToPreviousState = rememberUpdatedState(onSwipeToPrevious)
    val swipeToNextState = rememberUpdatedState(onSwipeToNext)
    val exoPlayer = remember(filePath, cloudConfig, legacyWebDavConfig) {
        ExoPlayer.Builder(context).build().apply {
            setAuthenticatedMediaItem(context, filePath, cloudConfig, legacyWebDavConfig)
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(filePath) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        AndroidView(
            factory = { ctx ->
                val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop
                var downX = 0f
                var downY = 0f
                var handledSwipe = false
                (LayoutInflater.from(ctx).inflate(
                    R.layout.baby_video_player_texture_view,
                    null,
                    false
                ) as PlayerView).apply {
                    player = exoPlayer
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(backgroundColor.toArgb())
                    setShutterBackgroundColor(backgroundColor.toArgb())
                    setOnTouchListener { view, event ->
                        val canPageBySwipe = swipeToPreviousState.value != null || swipeToNextState.value != null
                        if (!canPageBySwipe) {
                            return@setOnTouchListener false
                        }

                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                downX = event.x
                                downY = event.y
                                handledSwipe = false
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                            }

                            MotionEvent.ACTION_MOVE -> {
                                val deltaX = event.x - downX
                                val deltaY = event.y - downY
                                val isHorizontalSwipe = abs(deltaX) > touchSlop * 3 &&
                                    abs(deltaX) > abs(deltaY) * 1.35f
                                if (!handledSwipe && isHorizontalSwipe) {
                                    handledSwipe = true
                                    view.parent?.requestDisallowInterceptTouchEvent(true)
                                    if (deltaX > 0) {
                                        swipeToPreviousState.value?.invoke()
                                    } else {
                                        swipeToNextState.value?.invoke()
                                    }
                                }
                                if (handledSwipe) {
                                    return@setOnTouchListener true
                                }
                            }

                            MotionEvent.ACTION_UP -> {
                                val wasHandled = handledSwipe
                                handledSwipe = false
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                                if (wasHandled) {
                                    return@setOnTouchListener true
                                }
                            }

                            MotionEvent.ACTION_CANCEL -> {
                                handledSwipe = false
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false
                    }
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.setBackgroundColor(backgroundColor.toArgb())
                view.setShutterBackgroundColor(backgroundColor.toArgb())
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showCloseButton) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun ExoPlayer.setAuthenticatedMediaItem(
    context: Context,
    filePath: String,
    cloudConfig: CloudStorageConfig,
    legacyWebDavConfig: com.yueming.baby.data.WebDavManager.WebDavConfig?
) {
    val mediaItem = MediaItem.fromUri(filePath)
    val uri = Uri.parse(filePath)
    val shouldUseWebDavAuth = isConfiguredWebDavUrl(uri, cloudConfig, legacyWebDavConfig)

    if (!shouldUseWebDavAuth) {
        setMediaItem(mediaItem)
        return
    }

    val httpFactory = DefaultHttpDataSource.Factory()
        .setDefaultRequestProperties(webDavAuthHeadersFor(filePath, cloudConfig, legacyWebDavConfig))
    val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
    val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
        .createMediaSource(mediaItem)
    setMediaSource(mediaSource)
}

@Composable
fun VideoPlayerDialog(videoPath: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        VideoPlayer(
            filePath = videoPath,
            onClose = onDismiss,
            modifier = Modifier.fillMaxSize()
        )
    }
}
