package com.yueming.baby.ui.screens

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.yueming.baby.ui.components.BabyDangerButton
import com.yueming.baby.ui.components.BabyGlassAlertDialog
import com.yueming.baby.ui.components.BabyGlassIconButton
import com.yueming.baby.ui.components.BabyGlassRole
import com.yueming.baby.ui.components.BabyGlassSurface
import com.yueming.baby.ui.components.BabyGlassTextField
import com.yueming.baby.ui.components.BabyGlassTitle
import com.yueming.baby.ui.components.BabyLiquidFab
import com.yueming.baby.ui.components.BabyPalette
import com.yueming.baby.ui.components.BabyPill
import com.yueming.baby.ui.components.BabyPrimaryButton
import com.yueming.baby.ui.components.BabySecondaryButton
import com.yueming.baby.ui.components.LocalBabyBottomBarClearance
import com.yueming.baby.ui.components.LocalBabyStatusBarClearance
import com.yueming.baby.data.DataManager
import com.yueming.baby.data.PhotoEntry
import com.yueming.baby.data.belongsToBaby
import com.yueming.baby.ui.components.AuthenticatedAsyncImage
import com.yueming.baby.ui.components.ThumbnailManager
import com.yueming.baby.ui.components.VideoPlayer
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

private fun Window.applyMediaViewerSystemBars(view: android.view.View, barColor: Color) {
    addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    WindowCompat.setDecorFitsSystemWindows(this, false)
    setBackgroundDrawable(ColorDrawable(barColor.toArgb()))
    decorView.setBackgroundColor(barColor.toArgb())
    setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    @Suppress("DEPRECATION")
    statusBarColor = barColor.toArgb()
    @Suppress("DEPRECATION")
    navigationBarColor = barColor.toArgb()
    val useDarkIcons = barColor.luminance() > 0.5f
    WindowCompat.getInsetsController(this, view).isAppearanceLightStatusBars = useDarkIcons
    WindowCompat.getInsetsController(this, view).isAppearanceLightNavigationBars = useDarkIcons
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
                        .align(Alignment.TopStart)
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

            if (!selectionMode && !isUploading && uploadError == null) {
                val caption = photo.caption.ifBlank { if (isVideo) "视频" else "照片" }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(62.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.54f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatGalleryDateLabel(photo.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
private fun GalleryBottomSelectionBar(
    selectedCount: Int,
    onDeleteClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val bottomBarClearance = LocalBabyBottomBarClearance.current
    BabyGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp, bottom = bottomBarClearance),
        shape = RoundedCornerShape(26.dp),
        role = BabyGlassRole.NavigationChrome
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BabyGlassIconButton(
                icon = Icons.Outlined.Delete,
                onClick = onDeleteClick,
                enabled = selectedCount > 0,
                contentDescription = "删除",
                accent = if (selectedCount > 0) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "已选择 $selectedCount 项",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            BabyGlassIconButton(
                icon = Icons.Default.Close,
                onClick = onCancelClick,
                contentDescription = "取消选择"
            )
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
    val statusBarClearance = LocalBabyStatusBarClearance.current

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
                BabyLiquidFab(
                    onClick = {
                        if (babyInfo.id.isEmpty()) {
                            Toast.makeText(context, "请先添加宝宝信息", Toast.LENGTH_SHORT).show()
                        } else {
                            mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                    },
                    icon = Icons.Default.Add,
                    contentDescription = "添加",
                    modifier = Modifier
                        .padding(bottom = bottomBarClearance)
                        .miuixFadeSlideIn(delayMillis = 120, initialTranslationY = 22f)
                )
                /*
                        contentDescription = "添加",
                */
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
                            .padding(top = statusBarClearance + 10.dp, bottom = bottomBarClearance),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .miuixFadeSlideIn(initialTranslationY = 10f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            BabyGlassTitle(
                                title = "照片墙",
                                subtitle = "${babyInfo.nickname.ifBlank { "宝宝" }}的成长相册",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
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
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)),
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
                            Spacer(modifier = Modifier.weight(1f))
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
                        contentPadding = PaddingValues(top = statusBarClearance + 10.dp, bottom = bottomBarClearance),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(
                            key = "memory-wall-title",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BabyGlassTitle(
                                    title = "照片墙",
                                    subtitle = "${photoCount} 张照片 · ${videoCount} 个视频",
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(10.dp))
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
        var zoomScale by remember { mutableFloatStateOf(1f) }
        var zoomOffset by remember { mutableStateOf(Offset.Zero) }

        LaunchedEffect(pagerState.currentPage) {
            zoomScale = 1f
            zoomOffset = Offset.Zero
        }

        val currentPhoto = sortedPhotos[pagerState.currentPage]
        val currentDateLabel = formatGalleryDateLabel(currentPhoto.date)
        val currentIsVideo = currentPhoto.isVideoMedia()
        val immersiveViewer = !currentIsVideo && zoomScale > 1f

        Dialog(
            onDismissRequest = { viewerVisible = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false
            )
        ) {
            val dialogView = LocalView.current
            val viewerChromeColor = if (immersiveViewer) Color.Black else MaterialTheme.colorScheme.background
            if (!dialogView.isInEditMode) {
                SideEffect {
                    (dialogView.parent as? DialogWindowProvider)?.window
                        ?.applyMediaViewerSystemBars(dialogView, viewerChromeColor)
                }
            }

            var viewerEntered by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                viewerEntered = true
            }

            AnimatedVisibility(
                visible = viewerEntered,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(150)) +
                    scaleIn(initialScale = 0.965f, animationSpec = BabyMotion.pageScaleSpec()),
                exit = fadeOut(tween(120)) +
                    scaleOut(targetScale = 0.985f, animationSpec = tween(160))
            ) {
                Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (immersiveViewer) Modifier.background(Color.Black) else Modifier.babyPageBackground())
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (immersiveViewer) {
                                Modifier
                            } else {
                                Modifier.padding(start = 14.dp, top = 96.dp, end = 14.dp, bottom = 74.dp)
                            }
                        ),
                    userScrollEnabled = zoomScale == 1f && !currentIsVideo
                ) { page ->
                    val photo = sortedPhotos[page]
                    val pageBackground = when {
                        immersiveViewer -> Color.Black
                        photo.isVideoMedia() -> MaterialTheme.colorScheme.background
                        else -> Color.Transparent
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(if (immersiveViewer) 0.dp else 30.dp))
                            .background(pageBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photo.isVideoMedia()) {
                            VideoPlayer(
                                filePath = photo.url,
                                onClose = { viewerVisible = false },
                                modifier = Modifier.fillMaxSize(),
                                showCloseButton = false,
                                backgroundColor = MaterialTheme.colorScheme.background,
                                onSwipeToPrevious = {
                                    val targetPage = (pagerState.currentPage - 1).coerceAtLeast(0)
                                    if (targetPage != pagerState.currentPage) {
                                        scope.launch { pagerState.animateScrollToPage(targetPage) }
                                    }
                                },
                                onSwipeToNext = {
                                    val targetPage = (pagerState.currentPage + 1).coerceAtMost(sortedPhotos.lastIndex)
                                    if (targetPage != pagerState.currentPage) {
                                        scope.launch { pagerState.animateScrollToPage(targetPage) }
                                    }
                                }
                            )
                        } else {
                            AuthenticatedAsyncImage(
                                model = photo.url,
                                contentDescription = photo.caption,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoomScale
                                        scaleY = zoomScale
                                        translationX = zoomOffset.x
                                        translationY = zoomOffset.y
                                    }
                                    .pointerInput(photo.id) {
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
                                    .pointerInput(photo.id, zoomScale) {
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
                                    },
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                if (zoomScale == 1f) {
                    BabyGlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .miuixFadeSlideIn(delayMillis = 70, initialTranslationY = -10f),
                        shape = RoundedCornerShape(28.dp),
                        role = BabyGlassRole.Regular
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BabyGlassIconButton(
                                icon = Icons.Default.Close,
                                onClick = { viewerVisible = false },
                                contentDescription = "关闭"
                            )
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
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                BabyGlassIconButton(
                                    icon = Icons.Default.Edit,
                                    onClick = {
                                        editPhotoId = currentPhoto.id
                                        editCaption = currentPhoto.caption
                                        editDate = currentPhoto.date
                                        editDialog = true
                                    },
                                    contentDescription = "编辑",
                                    accent = BabyPalette.Blue
                                )
                                BabyGlassIconButton(
                                    icon = Icons.Default.Delete,
                                    onClick = {
                                        DataManager.deletePhoto(currentPhoto.id)
                                        viewerVisible = false
                                    },
                                    contentDescription = "删除",
                                    accent = BabyPalette.RoseDeep
                                )
                            }
                        }
                    }
                }

                if (zoomScale == 1f) {
                    BabyGlassSurface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                            .miuixFadeSlideIn(delayMillis = 90, initialTranslationY = 10f),
                        shape = RoundedCornerShape(999.dp),
                        role = BabyGlassRole.Clear
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${sortedPhotos.size}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
    }

    if (editDialog) {
        BabyGlassAlertDialog(
            onDismissRequest = { editDialog = false },
            title = { Text("编辑") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BabyGlassTextField(
                        value = editCaption,
                        onValueChange = { editCaption = it },
                        label = "描述",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    BabyGlassTextField(
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
                        label = "日期（yyyy-MM-dd）",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                BabyPrimaryButton(
                    text = "保存",
                    onClick = {
                        DataManager.updatePhoto(editPhotoId, editCaption, editDate)
                        editDialog = false
                    }
                )
            },
            dismissButton = {
                BabySecondaryButton(text = "取消", onClick = { editDialog = false })
            }
        )
    }

    if (showDeleteConfirm) {
        BabyGlassAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除选中的 ${selectedIds.size} 个媒体吗？此操作不可撤销。") },
            confirmButton = {
                BabyDangerButton(
                    text = "删除",
                    onClick = {
                        DataManager.deletePhotos(selectedIds)
                        selectedIds = emptySet()
                        selectionMode = false
                        showDeleteConfirm = false
                    }
                )
            },
            dismissButton = {
                BabySecondaryButton(text = "取消", onClick = { showDeleteConfirm = false })
            }
        )
    }
}
