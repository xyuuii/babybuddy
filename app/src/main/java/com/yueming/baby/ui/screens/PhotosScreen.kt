package com.yueming.baby.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yueming.baby.ui.components.BabyBrandWordmark
import com.yueming.baby.ui.components.BabyMetricChip
import com.yueming.baby.ui.components.BabyPalette
import com.yueming.baby.ui.components.BabyPill
import com.yueming.baby.ui.components.BabySectionHeader
import com.yueming.baby.ui.components.LocalBabyBottomBarClearance
import com.yueming.baby.data.DataManager
import com.yueming.baby.data.PhotoEntry
import com.yueming.baby.data.belongsToBaby
import com.yueming.baby.ui.components.AuthenticatedAsyncImage
import com.yueming.baby.ui.components.ThumbnailManager
import com.yueming.baby.ui.components.VideoPlayerDialog
import com.yueming.baby.ui.components.VideoThumbnail
import com.yueming.baby.ui.components.babyPageBackground
import com.yueming.baby.ui.motion.BabyMotion
import com.yueming.baby.ui.motion.miuixFadeSlideIn
import com.yueming.baby.ui.motion.motionCardPress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

private sealed interface GalleryGridItem {
    val key: String

    data class Header(
        val rawDate: String,
        val label: String,
        val count: Int
    ) : GalleryGridItem {
        override val key: String = "header-$rawDate"
    }

    data class Media(
        val photo: PhotoEntry,
        val sortedIndex: Int
    ) : GalleryGridItem {
        override val key: String = photo.id
    }
}

private fun PhotoEntry.isVideoMedia(): Boolean {
    if (mediaType.equals("video", ignoreCase = true)) {
        return true
    }
    if (tags.any { it.contains("视频") || it.equals("video", ignoreCase = true) }) {
        return true
    }
    val cleanUrl = url.substringBefore('?').substringBefore('#').lowercase()
    return listOf(".mp4", ".webm", ".mov", ".m4v", ".3gp", ".mkv", ".avi").any {
        cleanUrl.endsWith(it)
    }
}

private fun PhotoEntry.previewModel(): String = thumbnailPath ?: url

private fun Uri.isVideoMediaUri(context: Context): Boolean {
    val mimeType = runCatching { context.contentResolver.getType(this) }
        .getOrNull()
        .orEmpty()
        .lowercase()
    if (mimeType.startsWith("video/")) return true
    if (mimeType.startsWith("image/")) return false

    val cleanUri = toString().substringBefore('?').substringBefore('#').lowercase()
    return listOf(".mp4", ".webm", ".mov", ".m4v", ".3gp", ".mkv", ".avi").any {
        cleanUri.endsWith(it)
    }
}

private fun parsePhotoDate(raw: String): LocalDate? {
    return runCatching { LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
}

private fun formatGalleryDateLabel(raw: String): String {
    val date = parsePhotoDate(raw) ?: return raw.ifBlank { "未标记日期" }
    val today = LocalDate.now()
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
    }
}

private fun formatGalleryDateMeta(raw: String): String {
    val date = parsePhotoDate(raw) ?: return raw.ifBlank { "未标记日期" }
    return date.format(DateTimeFormatter.ofPattern("EEEE"))
}

private fun formatMemoryDate(raw: String): String {
    val date = parsePhotoDate(raw) ?: return raw.ifBlank { "未标记日期" }
    return date.format(DateTimeFormatter.ofPattern("M月d日"))
}

private fun buildGalleryGridItems(sortedPhotos: List<PhotoEntry>): List<GalleryGridItem> {
    if (sortedPhotos.isEmpty()) return emptyList()
    val grouped = sortedPhotos.groupBy { it.date.ifBlank { "unknown" } }
    val orderedDates = sortedPhotos.map { it.date.ifBlank { "unknown" } }.distinct()
    val indexById = sortedPhotos.mapIndexed { index, photo -> photo.id to index }.toMap()
    return buildList {
        orderedDates.forEach { rawDate ->
            val itemsForDate = grouped[rawDate].orEmpty()
            add(
                GalleryGridItem.Header(
                    rawDate = rawDate,
                    label = formatGalleryDateLabel(rawDate),
                    count = itemsForDate.size
                )
            )
            itemsForDate.forEach { photo ->
                add(
                    GalleryGridItem.Media(
                        photo = photo,
                        sortedIndex = indexById[photo.id] ?: 0
                    )
                )
            }
        }
    }
}

@Composable
private fun GalleryDateHeader(header: GalleryGridItem.Header) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = header.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatGalleryDateMeta(header.rawDate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = "${header.count} 项",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MediaGridCard(
    photo: PhotoEntry,
    selectionMode: Boolean,
    isSelected: Boolean,
    isUploading: Boolean,
    uploadError: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isVideo = photo.isVideoMedia()
    val interactionSource = remember { MutableInteractionSource() }
    val cardCorner by animateDpAsState(
        targetValue = if (isSelected) 28.dp else 24.dp,
        animationSpec = BabyMotion.cardShapeSpring(),
        label = "mediaGridCorner"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .motionCardPress(interactionSource, pressedScale = 0.965f)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = if (isSelected) 1.dp else 0.5.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
            }
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isVideo) {
                if (photo.thumbnailPath != null) {
                    AuthenticatedAsyncImage(
                        model = photo.previewModel(),
                        contentDescription = photo.caption,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    VideoThumbnail(
                        filePath = photo.url,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                AuthenticatedAsyncImage(
                    model = photo.previewModel(),
                    contentDescription = photo.caption,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            if (isUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.42f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("上传中", color = Color.White, fontSize = 11.sp)
                    }
                }
            }

            if (uploadError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xAAE53935)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("上传失败", color = Color.White, fontSize = 11.sp)
                    }
                }
            }

            if (isVideo && !selectionMode && !isUploading) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.66f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("视频", color = Color.White, fontSize = 11.sp)
                    }
                }
            }

            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isSelected) Color(0x42000000) else Color.Transparent)
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp),
                    shape = CircleShape,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White.copy(alpha = 0.82f)
                    },
                    border = if (isSelected) {
                        null
                    } else {
                        BorderStroke(1.5.dp, Color.White)
                    }
                ) {
                    if (isSelected) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmstripThumbnail(
    photo: PhotoEntry,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 62.dp, height = 74.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) Color.White else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = {})
    ) {
        if (photo.isVideoMedia()) {
            if (photo.thumbnailPath != null) {
                AuthenticatedAsyncImage(
                    model = photo.previewModel(),
                    contentDescription = photo.caption,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                VideoThumbnail(
                    filePath = photo.url,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            AuthenticatedAsyncImage(
                model = photo.previewModel(),
                contentDescription = photo.caption,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (photo.isVideoMedia()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .padding(3.dp)
                        .size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun GalleryBottomSelectionBar(
    selectedCount: Int,
    onDeleteClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val bottomBarClearance = LocalBabyBottomBarClearance.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp, bottom = bottomBarClearance),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDeleteClick, enabled = selectedCount > 0) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = if (selectedCount > 0) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "已选择 $selectedCount 项",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onCancelClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "取消选择"
                )
            }
        }
    }
}

@Composable
private fun MemoryBrandHeader(
    nickname: String,
    totalCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp, bottom = 6.dp)
            .miuixFadeSlideIn(initialTranslationY = 10f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BabyBrandWordmark(subtitle = "${nickname}的成长回忆 · $totalCount 项")
        Surface(
            shape = CircleShape,
            color = BabyPalette.RoseSoft,
            border = BorderStroke(0.5.dp, BabyPalette.Rose.copy(alpha = 0.22f))
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                modifier = Modifier.padding(12.dp).size(20.dp),
                tint = BabyPalette.Rose
            )
        }
    }
}

@Composable
private fun FeaturedMemoryCard(
    photo: PhotoEntry?,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(144.dp)
            .motionCardPress(interactionSource, pressedScale = 0.985f)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {}
            ),
        shape = RoundedCornerShape(30.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(0.5.dp, Color(0xFFFFB6C6).copy(alpha = 0.38f)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEEF2))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFFFFF4F6), Color(0xFFFFDCE6), Color(0xFFFFF8EE))
                    )
                )
        ) {
            if (photo != null) {
                if (photo.isVideoMedia()) {
                    if (photo.thumbnailPath != null) {
                        AuthenticatedAsyncImage(
                            model = photo.previewModel(),
                            contentDescription = photo.caption,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = 0.22f },
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    AuthenticatedAsyncImage(
                        model = photo.previewModel(),
                        contentDescription = photo.caption,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 0.22f },
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = Color(0xFFFF7E9A))
                        Text("Today's Memory", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF6F8E), fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = photo?.caption?.takeIf { it.isNotBlank() } ?: "Every little smile makes life beautiful.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = photo?.date?.let(::formatMemoryDate) ?: "记录今天的珍贵瞬间",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecentMemoriesRow(
    photos: List<PhotoEntry>,
    onOpen: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .miuixFadeSlideIn(delayMillis = 60, initialTranslationY = 10f),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("最近回忆", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("View All", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFF7E9A), fontWeight = FontWeight.Bold)
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            itemsIndexed(photos.take(8), key = { _, photo -> photo.id }) { index, photo ->
                val interactionSource = remember { MutableInteractionSource() }
                Card(
                    modifier = Modifier
                        .width(112.dp)
                        .height(142.dp)
                        .motionCardPress(interactionSource, pressedScale = 0.965f)
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onOpen(index) },
                            onLongClick = {}
                        ),
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(86.dp)
                                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        ) {
                            if (photo.isVideoMedia()) {
                                if (photo.thumbnailPath != null) {
                                    AuthenticatedAsyncImage(photo.previewModel(), photo.caption, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    VideoThumbnail(filePath = photo.url, modifier = Modifier.fillMaxSize())
                                }
                                Surface(
                                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.Black.copy(alpha = 0.62f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, Modifier.padding(4.dp).size(12.dp), tint = Color.White)
                                }
                            } else {
                                AuthenticatedAsyncImage(photo.previewModel(), photo.caption, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(
                                text = photo.caption.ifBlank { if (photo.isVideoMedia()) "视频" else "照片" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatMemoryDate(photo.date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryOverviewRow(
    photoCount: Int,
    videoCount: Int,
    totalCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .miuixFadeSlideIn(delayMillis = 100, initialTranslationY = 10f),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BabyMetricChip("照片", photoCount.toString(), Modifier.weight(1f), BabyPalette.Blue)
        BabyMetricChip("视频", videoCount.toString(), Modifier.weight(1f), BabyPalette.Rose)
        BabyMetricChip("回忆", totalCount.toString(), Modifier.weight(1f), BabyPalette.Gold)
    }
}

@Composable
private fun MemoryOverviewCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(82.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = accent)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen() {
    val babyInfo by DataManager.babyInfo.collectAsState()
    val photos by DataManager.photos.collectAsState()
    val isLoading by DataManager.isLoading.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bottomBarClearance = LocalBabyBottomBarClearance.current

    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    var viewerVisible by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var uploadingIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var uploadErrors by remember { mutableStateOf(mapOf<String, String>()) }
    var editDialog by remember { mutableStateOf(false) }
    var editCaption by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }
    var editPhotoId by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var gridThumbSize by rememberSaveable { mutableFloatStateOf(128f) }

    val scopedPhotos = remember(photos, babyInfo.id) {
        photos.filter { belongsToBaby(it.babyId, babyInfo.id) }
    }
    val sortedPhotos = remember(scopedPhotos) {
        scopedPhotos.sortedWith(
            compareByDescending<PhotoEntry> { parsePhotoDate(it.date) ?: LocalDate.MIN }
                .thenByDescending { it.id }
        )
    }
    val gridItems = remember(sortedPhotos) { buildGalleryGridItems(sortedPhotos) }
    val photoCount = remember(sortedPhotos) { sortedPhotos.count { !it.isVideoMedia() } }
    val videoCount = remember(sortedPhotos) { sortedPhotos.count { it.isVideoMedia() } }
    val gridState = rememberLazyGridState()

    val pinchZoomState = rememberTransformableState { zoomChange, _, _ ->
        gridThumbSize = (gridThumbSize * zoomChange).coerceIn(92f, 188f)
    }

    val firstVisibleDateLabel by remember(gridState, gridItems) {
        derivedStateOf {
            val firstIndex = gridState.firstVisibleItemIndex.coerceAtLeast(0)
            val candidate = gridItems.getOrNull(firstIndex)
            when (candidate) {
                is GalleryGridItem.Header -> candidate.label
                is GalleryGridItem.Media -> {
                    val headerIndex = (firstIndex downTo 0).firstOrNull { gridItems[it] is GalleryGridItem.Header }
                    (gridItems.getOrNull(headerIndex ?: 0) as? GalleryGridItem.Header)?.label.orEmpty()
                }
                else -> ""
            }
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (babyInfo.id.isEmpty()) {
            Toast.makeText(context, "请先添加宝宝信息", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        uris.forEach { uri ->
            val isVideo = uri.isVideoMediaUri(context)
            val id = "photo-${UUID.randomUUID().toString().take(8)}"
            uploadingIds = uploadingIds + id
            scope.launch {
                val (localUrl, thumbnailPath) = withContext(Dispatchers.IO) {
                    val localPath = if (isVideo) {
                        DataManager.copyVideoToInternalStorage(uri)
                    } else {
                        DataManager.copyPhotoToInternalStorage(uri)
                    }
                    val thumb = ThumbnailManager.generateThumbnail(context, uri, isVideo = isVideo)
                    (localPath ?: uri.toString()) to thumb
                }
                DataManager.addPhoto(
                    PhotoEntry(
                        id = id,
                        url = localUrl,
                        caption = if (isVideo) "视频" else "照片",
                        date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                        tags = if (isVideo) listOf("视频") else emptyList(),
                        thumbnailPath = thumbnailPath,
                        mediaType = if (isVideo) "video" else "photo"
                    )
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        DataManager.mediaUploadEvents.collect { event ->
            uploadingIds = uploadingIds - event.photoId
            if (!event.success) {
                uploadErrors = uploadErrors + (event.photoId to event.message)
            }
        }
    }

    LaunchedEffect(uploadErrors) {
        if (uploadErrors.isNotEmpty()) {
            delay(5000)
            uploadErrors = emptyMap()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(
                    onClick = {
                        if (babyInfo.id.isEmpty()) {
                            Toast.makeText(context, "请先添加宝宝信息", Toast.LENGTH_SHORT).show()
                        } else {
                            mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                    },
                    modifier = Modifier
                        .padding(bottom = bottomBarClearance)
                        .miuixFadeSlideIn(delayMillis = 120, initialTranslationY = 22f),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(22.dp),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 1.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selectionMode,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                GalleryBottomSelectionBar(
                    selectedCount = selectedIds.size,
                    onDeleteClick = { showDeleteConfirm = true },
                    onCancelClick = {
                        selectionMode = false
                        selectedIds = emptySet()
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .babyPageBackground()
                .padding(horizontal = 18.dp)
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier.miuixFadeSlideIn(initialTranslationY = 10f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "加载中…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                sortedPhotos.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 10.dp, bottom = bottomBarClearance),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.miuixFadeSlideIn(initialTranslationY = 10f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            BabyBrandWordmark(
                                subtitle = "${babyInfo.nickname.ifBlank { "宝宝" }}的成长相册"
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                shape = RoundedCornerShape(30.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                border = BorderStroke(
                                    0.6.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 26.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clip(RoundedCornerShape(28.dp))
                                            .background(BabyPalette.Rose.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Collections,
                                            contentDescription = null,
                                            modifier = Modifier.size(34.dp),
                                            tint = BabyPalette.Rose
                                        )
                                    }
                                    Text(
                                        text = "还没有照片或视频",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "点击右下角 +，一次选择照片和视频，把今天的小瞬间放进相册。",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(gridThumbSize.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .miuixFadeSlideIn(delayMillis = 80, initialTranslationY = 14f)
                            .transformable(state = pinchZoomState),
                        contentPadding = PaddingValues(top = 10.dp, bottom = bottomBarClearance),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(
                            key = "memory-brand",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            MemoryBrandHeader(
                                nickname = babyInfo.nickname.ifBlank { "宝宝" },
                                totalCount = sortedPhotos.size
                            )
                        }
                        item(
                            key = "memory-featured",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            FeaturedMemoryCard(
                                photo = sortedPhotos.firstOrNull(),
                                onClick = {
                                    selectedIndex = 0
                                    viewerVisible = true
                                }
                            )
                        }
                        item(
                            key = "memory-recent",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            RecentMemoriesRow(
                                photos = sortedPhotos,
                                onOpen = { index ->
                                    selectedIndex = index
                                    viewerVisible = true
                                }
                            )
                        }
                        item(
                            key = "memory-overview",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            MemoryOverviewRow(
                                photoCount = photoCount,
                                videoCount = videoCount,
                                totalCount = sortedPhotos.size
                            )
                        }
                        item(
                            key = "memory-wall-title",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BabySectionHeader(title = "照片墙", modifier = Modifier.weight(1f))
                                BabyPill(
                                    text = firstVisibleDateLabel.ifBlank { "相册时间线" },
                                    icon = Icons.Outlined.CalendarMonth,
                                    accent = BabyPalette.Blue,
                                    containerColor = BabyPalette.BlueSoft
                                )
                            }
                        }
                        items(
                            count = gridItems.size,
                            key = { index -> gridItems[index].key },
                            span = { index ->
                                if (gridItems[index] is GalleryGridItem.Header) {
                                    GridItemSpan(maxLineSpan)
                                } else {
                                    GridItemSpan(1)
                                }
                            },
                            contentType = { index ->
                                when (val item = gridItems[index]) {
                                    is GalleryGridItem.Header -> "header"
                                    is GalleryGridItem.Media -> if (item.photo.isVideoMedia()) "video" else "photo"
                                }
                            }
                        ) { index ->
                            when (val item = gridItems[index]) {
                                is GalleryGridItem.Header -> GalleryDateHeader(item)
                                is GalleryGridItem.Media -> {
                                    val photo = item.photo
                                    val isSelected = photo.id in selectedIds
                                    MediaGridCard(
                                        photo = photo,
                                        selectionMode = selectionMode,
                                        isSelected = isSelected,
                                        isUploading = photo.id in uploadingIds,
                                        uploadError = uploadErrors[photo.id],
                                        onClick = {
                                            if (selectionMode) {
                                                selectedIds = if (isSelected) {
                                                    selectedIds - photo.id
                                                } else {
                                                    selectedIds + photo.id
                                                }
                                                if (selectedIds.isEmpty()) {
                                                    selectionMode = false
                                                }
                                            } else {
                                                selectedIndex = item.sortedIndex
                                                viewerVisible = true
                                            }
                                        },
                                        onLongClick = {
                                            if (!selectionMode) {
                                                selectionMode = true
                                                selectedIds = setOf(photo.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (viewerVisible && sortedPhotos.isNotEmpty()) {
        val pagerState = rememberPagerState(
            initialPage = selectedIndex.coerceIn(0, sortedPhotos.lastIndex),
            pageCount = { sortedPhotos.size }
        )
        val filmstripState = rememberLazyListState()
        var zoomScale by remember { mutableFloatStateOf(1f) }
        var zoomOffset by remember { mutableStateOf(Offset.Zero) }
        var videoPlayPath by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(pagerState.currentPage) {
            zoomScale = 1f
            zoomOffset = Offset.Zero
            filmstripState.animateScrollToItem((pagerState.currentPage - 2).coerceAtLeast(0))
        }

        val currentPhoto = sortedPhotos[pagerState.currentPage]
        val currentDateLabel = formatGalleryDateLabel(currentPhoto.date)
        val immersiveViewer = zoomScale > 1f

        Dialog(
            onDismissRequest = { viewerVisible = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (immersiveViewer) Modifier.background(Color.Black) else Modifier.babyPageBackground())
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (zoomScale > 1f) {
                                    zoomScale = 1f
                                    zoomOffset = Offset.Zero
                                } else {
                                    zoomScale = 2.2f
                                }
                            }
                        )
                    }
                    .pointerInput(zoomScale > 1f) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (zoomScale * zoom).coerceIn(1f, 5f)
                            if (newScale > 1f) {
                                zoomOffset = Offset(
                                    x = zoomOffset.x + pan.x,
                                    y = zoomOffset.y + pan.y
                                )
                            } else {
                                zoomOffset = Offset.Zero
                            }
                            zoomScale = newScale
                        }
                    }
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (immersiveViewer) {
                                Modifier
                            } else {
                                Modifier.padding(start = 14.dp, top = 96.dp, end = 14.dp, bottom = 158.dp)
                            }
                        ),
                    userScrollEnabled = zoomScale == 1f
                ) { page ->
                    val photo = sortedPhotos[page]
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(if (immersiveViewer) 0.dp else 30.dp))
                            .background(Color.Black)
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = zoomOffset.x
                                translationY = zoomOffset.y
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (photo.isVideoMedia()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (photo.thumbnailPath != null) {
                                    AuthenticatedAsyncImage(
                                        model = photo.previewModel(),
                                        contentDescription = photo.caption,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    VideoThumbnail(
                                        filePath = photo.url,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                Surface(
                                    modifier = Modifier.size(68.dp),
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.86f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .combinedClickable(
                                                onClick = { videoPlayPath = photo.url },
                                                onLongClick = {}
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "播放",
                                            tint = Color.Black,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            AuthenticatedAsyncImage(
                                model = photo.url,
                                contentDescription = photo.caption,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                if (zoomScale == 1f) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                        border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewerVisible = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = currentDateLabel,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = currentPhoto.caption.ifBlank { if (currentPhoto.isVideoMedia()) "视频" else "照片" },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        editPhotoId = currentPhoto.id
                                        editCaption = currentPhoto.caption
                                        editDate = currentPhoto.date
                                        editDialog = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "编辑",
                                        tint = BabyPalette.Blue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        DataManager.deletePhoto(currentPhoto.id)
                                        viewerVisible = false
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "删除",
                                        tint = BabyPalette.RoseDeep,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (zoomScale == 1f) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${sortedPhotos.size}",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
                        ) {
                            LazyRow(
                                state = filmstripState,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(
                                    items = sortedPhotos,
                                    key = { _, photo -> photo.id },
                                    contentType = { _, photo -> if (photo.isVideoMedia()) "video-thumb" else "photo-thumb" }
                                ) { index, photo ->
                                    FilmstripThumbnail(
                                        photo = photo,
                                        selected = index == pagerState.currentPage,
                                        onClick = {
                                            scope.launch { pagerState.animateScrollToPage(index) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (videoPlayPath != null) {
            VideoPlayerDialog(
                videoPath = videoPlayPath!!,
                onDismiss = { videoPlayPath = null }
            )
        }
    }

    if (editDialog) {
        AlertDialog(
            onDismissRequest = { editDialog = false },
            title = { Text("编辑") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editCaption,
                        onValueChange = { editCaption = it },
                        label = { Text("描述") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = editDate,
                        onValueChange = { candidate ->
                            editDate = if (candidate.isBlank()) {
                                candidate
                            } else {
                                try {
                                    LocalDate.parse(candidate, DateTimeFormatter.ISO_LOCAL_DATE)
                                    candidate
                                } catch (_: DateTimeParseException) {
                                    candidate
                                }
                            }
                        },
                        label = { Text("日期（yyyy-MM-dd）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        DataManager.updatePhoto(editPhotoId, editCaption, editDate)
                        editDialog = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除选中的 ${selectedIds.size} 个媒体吗？此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        DataManager.deletePhotos(selectedIds)
                        selectedIds = emptySet()
                        selectionMode = false
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("删除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}
