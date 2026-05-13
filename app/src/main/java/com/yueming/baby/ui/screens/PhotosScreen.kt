package com.yueming.baby.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.yueming.baby.data.*
import com.yueming.baby.ui.components.VideoPlayer
import com.yueming.baby.ui.components.VideoThumbnail
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen() {
    val babyInfo by DataManager.babyInfo.collectAsState()
    val photos by DataManager.photos.collectAsState()
    var showUpload by remember { mutableStateOf(false) }
    var photoCaption by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var showUrlInput by remember { mutableStateOf(false) }
    var lightboxPhoto by remember { mutableStateOf<PhotoEntry?>(null) }
    var selectedVideoPath by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
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
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val localPath = DataManager.copyVideoToInternalStorage(uri)
            selectedVideoPath = localPath ?: uri.toString()
            DataManager.addPhoto(PhotoEntry(
                id = "photo-${UUID.randomUUID().toString().take(8)}",
                url = selectedVideoPath!!,
                caption = photoCaption.ifBlank { "视频" },
                date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                tags = listOf("视频")
            ))
            photoCaption = ""
            showUpload = false
            selectedVideoPath = null
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
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("${babyInfo.nickname}的照片墙",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${photos.size} 张照片", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!showUpload) {
                Button(
                    onClick = { showUpload = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("上传照片", color = Color.White)
                }
            }
        }

        if (showUpload) {
            Spacer(Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("添加照片", fontWeight = FontWeight.Medium)
                        IconButton(onClick = { showUpload = false }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                    OutlinedTextField(
                        value = photoCaption, onValueChange = { photoCaption = it },
                        label = { Text("照片描述（可选）") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { photoPicker.launch("image/*") },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("添加照片", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { videoPicker.launch("video/*") },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Videocam, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("添加视频", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { showUrlInput = !showUrlInput },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (showUrlInput) "收起" else "图片链接", fontSize = 12.sp)
                        }
                    }
                    if (showUrlInput) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = photoUrl, onValueChange = { photoUrl = it },
                                label = { Text("图片URL") }, modifier = Modifier.weight(1f),
                                singleLine = true, shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = {
                                    val url = photoUrl.trim()
                                    if (url.isNotEmpty()) {
                                        DataManager.addPhoto(PhotoEntry(
                                            id = "photo-${UUID.randomUUID().toString().take(8)}",
                                            url = url, caption = photoCaption.ifBlank { "照片" },
                                            date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                                            tags = emptyList()
                                        ))
                                        photoUrl = ""; photoCaption = ""; showUrlInput = false; showUpload = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A)),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("添加", color = Color.White) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhotoLibrary, null, Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                    Spacer(Modifier.height(12.dp))
                    Text("还没有照片",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("上传宝宝的第一张照片吧",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                groupedPhotos.forEach { (monthLabel, monthPhotos) ->
                    stickyHeader {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
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
                    }
                    item {
                        val chunked = monthPhotos.chunked(3)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            chunked.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    row.forEach { photo ->
                                        Card(
                                            modifier = Modifier.weight(1f).aspectRatio(1f)
                                                .clickable {
                                                    if (photo.tags.contains("视频")) {
                                                        selectedVideoPath = photo.url
                                                    } else {
                                                        lightboxPhoto = photo
                                                    }
                                                },
                                            shape = RoundedCornerShape(16.dp),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                if (photo.tags.contains("视频")) {
                                                    VideoThumbnail(
                                                        filePath = photo.url,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    AsyncImage(model = photo.url, contentDescription = photo.caption,
                                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                }
                                            }
                                        }
                                    }
                                    repeat(3 - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // Lightbox
    lightboxPhoto?.let { photo ->
        Dialog(
            onDismissRequest = { lightboxPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().clickable { lightboxPhoto = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Card(shape = RoundedCornerShape(20.dp)) {
                        AsyncImage(
                            model = photo.url, contentDescription = photo.caption,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(photo.caption, color = Color.White, fontWeight = FontWeight.Medium)
                    Text(photo.date, color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    // Video fullscreen player
    selectedVideoPath?.let { path ->
        Dialog(
            onDismissRequest = { selectedVideoPath = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            VideoPlayer(
                filePath = path,
                onClose = { selectedVideoPath = null }
            )
        }
    }
}
