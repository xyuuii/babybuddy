package com.yueming.baby.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yueming.baby.data.DataManager
import com.yueming.baby.data.cloud.CloudStorageConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class VideoThumbnailResult(
    val bitmap: Bitmap?,
    val durationMs: Long
)

@Composable
fun VideoThumbnail(
    filePath: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val cloudConfig by DataManager.cloudStorageConfig.collectAsState()

    val thumbnail by produceState(
        initialValue = VideoThumbnailResult(bitmap = null, durationMs = 0L),
        key1 = filePath,
        key2 = cloudConfig
    ) {
        value = withContext(Dispatchers.IO) {
            loadVideoThumbnail(context.applicationContext, filePath, cloudConfig)
        }
    }

    val bitmap = thumbnail.bitmap
    val durationMs = thumbnail.durationMs

    DisposableEffect(bitmap) {
        onDispose {
            bitmap?.recycle()
        }
    }

    val durationText = if (durationMs > 0) {
        val totalSeconds = durationMs / 1000
        "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
    } else ""

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.DarkGray)
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            )
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "video thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = "video",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Duration badge at bottom-end with semi-transparent black background
        if (durationText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    durationText,
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }

        // Play overlay icon at center
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "play",
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.Center)
                .size(28.dp)
        )

        if (bitmap == null) {
            // Show "视频" text below the videocam icon if extraction failed
            Text(
                "视频",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            )
        }
    }
}

private fun loadVideoThumbnail(
    context: android.content.Context,
    filePath: String,
    cloudConfig: CloudStorageConfig
): VideoThumbnailResult {
    val uri = Uri.parse(filePath)
    val retriever = MediaMetadataRetriever()
    return try {
        when (uri.scheme) {
            "content", "android.resource" -> retriever.setDataSource(context, uri)
            "http", "https" -> retriever.setDataSource(filePath, webDavAuthHeadersFor(filePath, cloudConfig))
            else -> retriever.setDataSource(filePath)
        }
        val bmp = retriever.getScaledFrameAtTime(
            -1,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            320,
            320
        )
        val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        VideoThumbnailResult(bmp, dur)
    } catch (_: Exception) {
        VideoThumbnailResult(bitmap = null, durationMs = 0L)
    } finally {
        runCatching { retriever.release() }
    }
}
