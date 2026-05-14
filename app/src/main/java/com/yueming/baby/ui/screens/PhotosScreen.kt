package com.yueming.baby.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yueming.baby.data.*
import com.yueming.baby.ui.components.AuthenticatedAsyncImage
import com.yueming.baby.ui.components.ThumbnailManager
import com.yueming.baby.ui.components.VideoThumbnail
import com.yueming.baby.ui.components.VideoPlayerDialog
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen() {
    val babyInfo by DataManager.babyInfo.collectAsState()
    val photos by DataManager.photos.collectAsState()
    val isLoading by DataManager.isLoading.collectAsState()
    var showUpload by remember { mutableStateOf(false) }
    var photoCaption by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableStateOf(0) }
    var viewerVisible by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var uploadingIds by remember { mutableStateOf(setOf<String>()) }
    var uploadErrors by remember { mutableStateOf(mapOf<String, String>()) }
    var editDialog by remember { mutableStateOf(false) }
    var editCaption by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }
    var editPhotoId by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val sortedPhotos = remember(photos) { photos.sortedByDescending { it.date } }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (babyInfo.id.isEmpty()) { Toast.makeText(context, "请先添加宝宝信息", Toast.LENGTH_SHORT).show(); return@rememberLauncherForActivityResult }
        if (uri != null) {
            val id = "photo-${UUID.randomUUID().toString().take(8)}"
            uploadingIds = uploadingIds + id
            DataManager.addPhoto(PhotoEntry(
                id = id,
                url = DataManager.copyPhotoToInternalStorage(uri) ?: uri.toString(),
                caption = photoCaption.ifBlank { "照片" },
                date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                tags = emptyList(),
                thumbnailPath = ThumbnailManager.generateThumbnail(context, uri, isVideo = false)
            ))
            photoCaption = ""; showUpload = false
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (babyInfo.id.isEmpty()) { Toast.makeText(context, "请先添加宝宝信息", Toast.LENGTH_SHORT).show(); return@rememberLauncherForActivityResult }
        if (uri != null) {
            val id = "photo-${UUID.randomUUID().toString().take(8)}"
            uploadingIds = uploadingIds + id
            DataManager.addPhoto(PhotoEntry(
                id = id,
                url = DataManager.copyVideoToInternalStorage(uri) ?: uri.toString(),
                caption = photoCaption.ifBlank { "视频" },
                date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                tags = listOf("视频"),
                thumbnailPath = ThumbnailManager.generateThumbnail(context, uri, isVideo = true)
            ))
            photoCaption = ""; showUpload = false
        }
    }

    // Track upload events
    LaunchedEffect(Unit) {
        DataManager.mediaUploadEvents.collect { event ->
            uploadingIds = uploadingIds - event.photoId
            if (!event.success) {
                uploadErrors = uploadErrors + (event.photoId to event.message)
            }
        }
    }

    // Auto-clear upload errors after 5s
    LaunchedEffect(uploadErrors) {
        if (uploadErrors.isNotEmpty()) {
            delay(5000)
            uploadErrors = emptyMap()
        }
    }

    Scaffold(
        floatingActionButton = {
            if (!showUpload && !selectionMode) {
                FloatingActionButton(
                    onClick = { showUpload = true },
                    containerColor = Color(0xFFEC407A),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, "添加", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            // Header
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("${babyInfo.nickname}的照片",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${photos.size} 个媒体",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${selectedIds.size} 项",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        IconButton(onClick = { showDeleteConfirm = true }, enabled = selectedIds.isNotEmpty()) {
                            Icon(Icons.Default.Delete, "删除", tint = if (selectedIds.isNotEmpty()) Color(0xFFEF5350) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { selectionMode = false; selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, "取消")
                        }
                    }
                }
            }

            // Upload panel
            AnimatedVisibility(visible = showUpload,
                enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()
            ) {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("添加媒体", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = { showUpload = false }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, null, Modifier.size(20.dp))
                            }
                        }
                        OutlinedTextField(value = photoCaption, onValueChange = { photoCaption = it },
                            label = { Text("描述（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            shape = RoundedCornerShape(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { photoPicker.launch("image/*") },
                                modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.PhotoLibrary, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("照片", fontSize = 14.sp)
                            }
                            OutlinedButton(onClick = { videoPicker.launch("video/*") },
                                modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.Videocam, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("视频", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // Content
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFFEC407A))
                        Spacer(Modifier.height(16.dp))
                        Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (sortedPhotos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.size(80.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Collections, null, Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("还没有照片或视频", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("点击右下角 + 添加宝宝的美好瞬间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(3), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalItemSpacing = 4.dp
                ) {
                    items(sortedPhotos, key = { it.id }) { photo ->
                        val isSelected = photo.id in selectedIds
                        val isUploading = photo.id in uploadingIds
                        val uploadError = uploadErrors[photo.id]
                        val isVideo = photo.tags.contains("视频")
                        val idx = sortedPhotos.indexOf(photo)

                        Box(modifier = Modifier.fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        selectedIds = if (isSelected) selectedIds - photo.id else selectedIds + photo.id
                                        if (selectedIds.isEmpty()) selectionMode = false
                                    } else {
                                        selectedIndex = idx; viewerVisible = true
                                    }
                                },
                                onLongClick = {
                                    if (!selectionMode) { selectionMode = true; selectedIds = setOf(photo.id) }
                                }
                            )
                        ) {
                            Card(modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent
                                )
                            ) {
                                Box {
                                    val displayUrl = photo.thumbnailPath ?: photo.url
                                    if (isVideo) {
                                        VideoThumbnail(filePath = displayUrl,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp))
                                    } else {
                                        AuthenticatedAsyncImage(model = displayUrl,
                                            contentDescription = photo.caption,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp),
                                            contentScale = ContentScale.Crop)
                                    }

                                    // Upload progress overlay on thumbnail
                                    if (isUploading) {
                                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                                            contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(28.dp),
                                                    color = Color.White, strokeWidth = 2.5.dp)
                                                Spacer(Modifier.height(6.dp))
                                                Text("上传中", color = Color.White, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                    // Upload error overlay
                                    if (uploadError != null) {
                                        Box(modifier = Modifier.fillMaxSize().background(Color(0xAAFF5252)),
                                            contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Default.Error, null, Modifier.size(24.dp), tint = Color.White)
                                                Spacer(Modifier.height(4.dp))
                                                Text("失败", color = Color.White, fontSize = 10.sp)
                                            }
                                        }
                                    }

                                    // Selection overlay
                                    if (selectionMode) {
                                        Box(modifier = Modifier.fillMaxSize().background(if (isSelected) Color(0x44000000) else Color.Transparent),
                                            contentAlignment = Alignment.TopEnd) {
                                            Surface(modifier = Modifier.padding(6.dp).size(24.dp), shape = CircleShape,
                                                color = if (isSelected) Color(0xFFEC407A) else Color.White.copy(alpha = 0.7f),
                                                border = if (!isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color.White) else null) {
                                                if (isSelected) Icon(Icons.Default.Check, null, Modifier.size(16.dp).align(Alignment.Center), tint = Color.White)
                                            }
                                        }
                                    }
                                    // Video indicator
                                    if (isVideo && !selectionMode && !isUploading) {
                                        Surface(modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                                            shape = RoundedCornerShape(4.dp), color = Color.Black.copy(alpha = 0.65f)) {
                                            Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.PlayArrow, null, Modifier.size(12.dp), tint = Color.White)
                                                Text("视频", color = Color.White, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selection bar
            AnimatedVisibility(visible = selectionMode,
                enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showDeleteConfirm = true }, enabled = selectedIds.isNotEmpty()) {
                            Icon(Icons.Outlined.Delete, "删除", tint = Color(0xFFEF5350))
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${selectedIds.size} 项已选", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    // Full-screen viewer
    if (viewerVisible && sortedPhotos.isNotEmpty()) {
        val pagerState = rememberPagerState(initialPage = selectedIndex, pageCount = { sortedPhotos.size })
        Dialog(onDismissRequest = { viewerVisible = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val photo = sortedPhotos[page]
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (photo.tags.contains("视频")) {
                            VideoPlayerDialog(videoPath = photo.url, onDismiss = { viewerVisible = false })
                        } else {
                            AuthenticatedAsyncImage(model = photo.url, contentDescription = photo.caption,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                    }
                }
                // Top bar
                Surface(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Color.Black.copy(alpha = 0.4f)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).statusBarsPadding(),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewerVisible = false }) { Icon(Icons.Default.Close, "关闭", tint = Color.White) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(sortedPhotos[pagerState.currentPage].caption.ifEmpty { "照片" }, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(sortedPhotos[pagerState.currentPage].date, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                        Row {
                            IconButton(onClick = {
                                editPhotoId = sortedPhotos[pagerState.currentPage].id
                                editCaption = sortedPhotos[pagerState.currentPage].caption
                                editDate = sortedPhotos[pagerState.currentPage].date
                                editDialog = true
                            }) { Icon(Icons.Default.Edit, "编辑", tint = Color.White, modifier = Modifier.size(20.dp)) }
                            IconButton(onClick = { DataManager.deletePhoto(sortedPhotos[pagerState.currentPage].id); viewerVisible = false }) {
                                Icon(Icons.Default.Delete, "删除", tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                // Page indicator
                Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.5f)) {
                    Text("${pagerState.currentPage + 1} / ${sortedPhotos.size}", color = Color.White, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
            }
        }
    }

    // Edit dialog
    if (editDialog) {
        AlertDialog(onDismissRequest = { editDialog = false }, title = { Text("编辑") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = editCaption, onValueChange = { editCaption = it }, label = { Text("描述") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = editDate, onValueChange = { editDate = it }, label = { Text("日期") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            }
        }, confirmButton = { Button(onClick = { DataManager.updatePhoto(editPhotoId, editCaption, editDate); editDialog = false }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { editDialog = false }) { Text("取消") } })
    }

    // Delete confirm
    if (showDeleteConfirm) {
        AlertDialog(onDismissRequest = { showDeleteConfirm = false }, title = { Text("确认删除") },
            text = { Text("确定要删除选中的 ${selectedIds.size} 个媒体吗？此操作不可撤销。") },
            confirmButton = { Button(onClick = { DataManager.deletePhotos(selectedIds); selectedIds = emptySet(); selectionMode = false; showDeleteConfirm = false },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))) { Text("删除", color = Color.White) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } })
    }
}
