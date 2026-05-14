package com.yueming.baby.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yueming.baby.data.*
import com.yueming.baby.ui.components.AuthenticatedAsyncImage
import com.yueming.baby.ui.components.VideoThumbnail
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

sealed class UploadState {
    data object Uploading : UploadState()
    data class Success(val label: String) : UploadState()
    data class Failed(val error: String) : UploadState()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen() {
    val babyInfo by DataManager.babyInfo.collectAsState()
    val photos by DataManager.photos.collectAsState()
    val isLoading by DataManager.isLoading.collectAsState()
    var showUpload by remember { mutableStateOf(false) }
    var photoCaption by remember { mutableStateOf("") }
    var lightboxPhoto by remember { mutableStateOf<PhotoEntry?>(null) }
    var selectedVideoPath by remember { mutableStateOf<String?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var uploadState by remember { mutableStateOf<UploadState?>(null) }
    var editPhoto by remember { mutableStateOf<PhotoEntry?>(null) }
    var editCaption by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (babyInfo.id.isEmpty()) {
            Toast.makeText(context, "请先在首页添加宝宝信息", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        if (uri != null) {
            uploadState = UploadState.Uploading
            val localPath = DataManager.copyPhotoToInternalStorage(uri)
            DataManager.addPhoto(PhotoEntry(
                id = "photo-${UUID.randomUUID().toString().take(8)}",
                url = localPath ?: uri.toString(),
                caption = photoCaption.ifBlank { "照片" },
                date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                tags = emptyList()
            ))
            photoCaption = ""
            showUpload = false
            uploadState = UploadState.Success("照片上传成功")
            scope.launch { delay(3000); uploadState = null }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (babyInfo.id.isEmpty()) {
            Toast.makeText(context, "请先在首页添加宝宝信息", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        if (uri != null) {
            uploadState = UploadState.Uploading
            val localPath = DataManager.copyVideoToInternalStorage(uri) ?: uri.toString()
            DataManager.addPhoto(PhotoEntry(
                id = "photo-${UUID.randomUUID().toString().take(8)}",
                url = localPath,
                caption = photoCaption.ifBlank { "视频" },
                date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                tags = listOf("视频")
            ))
            photoCaption = ""
            showUpload = false
            uploadState = UploadState.Success("视频上传成功")
            scope.launch { delay(3000); uploadState = null }
        }
    }

    val groupedPhotos = remember(photos) {
        val sorted = photos.sortedByDescending { it.date }
        val groups = linkedMapOf<String, List<PhotoEntry>>()
        for (photo in sorted) {
            try {
                val date = LocalDate.parse(photo.date)
                val label = "${date.year}年${date.monthValue}月"
                groups.getOrPut(label) { mutableListOf() }.let {
                    groups[label] = (it as MutableList) + photo
                }
            } catch (_: Exception) {
                groups.getOrPut("未知日期") { mutableListOf() }.let {
                    groups["未知日期"] = (it as MutableList) + photo
                }
            }
        }
        groups.toList()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("${babyInfo.nickname}的照片墙",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${photos.size} 个媒体", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!showUpload && !selectionMode) {
                FloatingActionButton(
                    onClick = { showUpload = !showUpload },
                    containerColor = Color(0xFFEC407A),
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(if (showUpload) Icons.Default.Close else Icons.Default.Add,
                        "添加", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            if (selectionMode) {
                TextButton(onClick = {
                    selectionMode = false
                    selectedIds = emptySet()
                }) { Text("取消") }
            }
        }

        // Upload progress
        AnimatedVisibility(visible = uploadState != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val state = uploadState
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = when (state) {
                    is UploadState.Uploading -> Color(0xFFFFF3E0)
                    is UploadState.Success -> Color(0xFFE8F5E9)
                    is UploadState.Failed -> Color(0xFFFFEBEE)
                    else -> Color.Transparent
                })
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state is UploadState.Uploading) {
                        LinearProgressIndicator(modifier = Modifier.weight(1f),
                            color = Color(0xFFFF9800))
                        Spacer(Modifier.width(8.dp))
                        Text("上传中...", fontSize = 13.sp)
                    } else if (state is UploadState.Success) {
                        Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(state.label, fontSize = 13.sp, color = Color(0xFF388E3C))
                    } else if (state is UploadState.Failed) {
                        Icon(Icons.Default.Close, null, tint = Color(0xFFF44336), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(state.error, fontSize = 13.sp, color = Color(0xFFD32F2F))
                    }
                }
            }
        }

        // Upload panel
        AnimatedVisibility(visible = showUpload,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("添加照片/视频", fontWeight = FontWeight.Medium)
                        IconButton(onClick = { showUpload = false }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                    OutlinedTextField(
                        value = photoCaption, onValueChange = { photoCaption = it },
                        label = { Text("描述（可选）") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { photoPicker.launch("image/*") },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("照片", fontSize = 14.sp)
                        }
                        OutlinedButton(
                            onClick = { videoPicker.launch("video/*") },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Videocam, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("视频", fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Content
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFEC407A))
                    Spacer(Modifier.height(16.dp))
                    Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhotoLibrary, null, Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    Spacer(Modifier.height(12.dp))
                    Text("还没有照片", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("点击右下角 + 开始添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                groupedPhotos.forEach { (monthLabel, monthPhotos) ->
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(monthLabel, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("  ${monthPhotos.size}张",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                    item {
                        val chunked = monthPhotos.chunked(3)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            chunked.forEach { row ->
                                Row(Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    row.forEach { photo ->
                                        val isSelected = photo.id in selectedIds
                                        Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                                            Card(
                                                modifier = Modifier.fillMaxSize()
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (selectionMode) {
                                                                selectedIds = if (isSelected)
                                                                    selectedIds - photo.id
                                                                else selectedIds + photo.id
                                                            } else {
                                                                if (photo.tags.contains("视频"))
                                                                    selectedVideoPath = photo.url
                                                                else
                                                                    lightboxPhoto = photo
                                                            }
                                                        },
                                                        onLongClick = {
                                                            selectionMode = true
                                                            selectedIds = selectedIds + photo.id
                                                        }
                                                    ),
                                                shape = RoundedCornerShape(12.dp),
                                                elevation = CardDefaults.cardElevation(
                                                    defaultElevation = if (isSelected) 4.dp else 1.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (photo.tags.contains("视频")) {
                                                        VideoThumbnail(filePath = photo.url,
                                                            modifier = Modifier.fillMaxSize())
                                                    } else {
                                                        AuthenticatedAsyncImage(
                                                            model = photo.url,
                                                            contentDescription = photo.caption,
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop)
                                                    }
                                                }
                                            }
                                            if (selectionMode) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = {},
                                                    modifier = Modifier.align(Alignment.TopEnd)
                                                        .padding(4.dp).size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        // Selection action bar
        AnimatedVisibility(visible = selectionMode,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 8.dp
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("已选 ${selectedIds.size} 项", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = selectedIds.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(18.dp),
                            tint = if (selectedIds.isNotEmpty()) Color(0xFFF44336)
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text("删除", color = if (selectedIds.isNotEmpty()) Color(0xFFF44336)
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // Lightbox with edit
    lightboxPhoto?.let { photo ->
        Dialog(
            onDismissRequest = { lightboxPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().clickable { lightboxPhoto = null },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Card(shape = RoundedCornerShape(20.dp)) {
                        AuthenticatedAsyncImage(model = photo.url,
                            contentDescription = photo.caption,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(photo.caption, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(photo.date, color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.width(16.dp))
                        IconButton(onClick = {
                            editPhoto = photo
                            editCaption = photo.caption
                            editDate = photo.date
                        }) {
                            Icon(Icons.Default.Edit, "编辑", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = {
                            DataManager.deletePhoto(photo.id)
                            lightboxPhoto = null
                        }) {
                            Icon(Icons.Default.Delete, "删除", tint = Color(0xFFFF5252), modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }

    // Edit dialog
    editPhoto?.let { photo ->
        AlertDialog(
            onDismissRequest = { editPhoto = null },
            title = { Text("编辑照片") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editCaption, onValueChange = { editCaption = it },
                        label = { Text("描述") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = editDate, onValueChange = { editDate = it },
                        label = { Text("日期") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    DataManager.updatePhoto(photo.id, editCaption, editDate)
                    editPhoto = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editPhoto = null }) { Text("取消") }
            }
        )
    }

    // Delete confirm dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除选中的 ${selectedIds.size} 个媒体吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        DataManager.deletePhotos(selectedIds)
                        selectedIds = emptySet()
                        selectionMode = false
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) { Text("删除", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    // Video player
    selectedVideoPath?.let { path ->
        com.yueming.baby.ui.components.VideoPlayerDialog(
            videoPath = path,
            onDismiss = { selectedVideoPath = null }
        )
    }
}
