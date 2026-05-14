package com.yueming.baby.ui.screens

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.yueming.baby.ui.components.VideoPlayer
import com.yueming.baby.ui.components.VideoThumbnail
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimelineScreen() {
    val babyInfo by DataManager.babyInfo.collectAsState()
    val timeline by DataManager.timeline.collectAsState()
    val allCategories = DataManager.allCategories
    var activeCategory by remember { mutableStateOf("all") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<TimelineRecord?>(null) }
    var playVideoPath by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val filtered = remember(timeline, activeCategory) {
        if (activeCategory == "all") timeline
        else timeline.filter { it.category == activeCategory }
    }

    val sorted = remember(filtered) { filtered.sortedByDescending { it.date } }

    val grouped = remember(sorted) {
        val groups = mutableListOf<Pair<String, List<TimelineRecord>>>()
        var currentLabel = ""
        for (r in sorted) {
            val date = LocalDate.parse(r.date)
            val label = "${date.year}年${date.monthValue}月"
            if (label != currentLabel) {
                groups.add(label to mutableListOf())
                currentLabel = label
            }
            groups.last().let { groups[groups.lastIndex] = it.first to (it.second + r) }
        }
        groups
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("${babyInfo.nickname}的成长时间线",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${timeline.size} 条记录", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Scrollable filter chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                item {
                    FilterChip(
                        selected = activeCategory == "all",
                        onClick = { activeCategory = "all" },
                        label = { Text("全部", fontSize = 12.sp) }
                    )
                }
                items(allCategories, key = { it.id }) { cat ->
                    val catColor = Color(cat.color)
                    FilterChip(
                        selected = activeCategory == cat.id,
                        onClick = { activeCategory = cat.id },
                        label = { Text(cat.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = catColor.copy(alpha = 0.3f),
                            selectedLabelColor = catColor
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (grouped.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EditNote, null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Spacer(Modifier.height(12.dp))
                        Text("该分类下暂无记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("点击右下角按钮添加第一条记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    grouped.forEach { (label, records) ->
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                HorizontalDivider(Modifier.weight(1f))
                            }
                        }
                        items(records, key = { it.id }) { record ->
                            val catColor = Color(getCategoryConfig(record.category)?.color ?: 0xFFe5e7eb)
                            ElevatedCard(
                                modifier = Modifier.animateItem(),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    // Top row: category chip + date + menu
                                    Row(Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = catColor.copy(alpha = 0.12f)
                                        ) {
                                            Text(" ${record.category} ",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = catColor,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(record.date,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                            Spacer(Modifier.width(4.dp))
                                            var menuExpanded by remember { mutableStateOf(false) }
                                            Box {
                                                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(24.dp)) {
                                                    Icon(Icons.Default.MoreVert, null, Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                                    DropdownMenuItem(text = { Text("编辑") }, onClick = {
                                                        menuExpanded = false; editingRecord = record; showAddDialog = true
                                                    }, leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)) })
                                                    DropdownMenuItem(text = { Text("删除", color = Color(0xFFEF5350)) }, onClick = {
                                                        menuExpanded = false; DataManager.deleteRecord(record.id)
                                                    }, leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = Color(0xFFEF5350)) })
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    // Title
                                    Text(record.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold)

                                    // Description
                                    if (record.description.isNotEmpty()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(record.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }

                                    // Photos preview
                                    if (record.photos.isNotEmpty()) {
                                        Spacer(Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            record.photos.take(4).forEach { url ->
                                                Surface(
                                                    modifier = Modifier.size(56.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surfaceContainer
                                                ) {
                                                    AuthenticatedAsyncImage(model = url, contentDescription = null,
                                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                }
                                            }
                                            if (record.photos.size > 4) {
                                                Surface(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surfaceContainer) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text("+${record.photos.size - 4}", fontSize = 13.sp,
                                                            color = MaterialTheme.colorScheme.primary)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Videos preview
                                    if (record.videos.isNotEmpty()) {
                                        Spacer(Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            record.videos.take(4).forEach { path ->
                                                Surface(modifier = Modifier.size(56.dp).clickable { playVideoPath = path },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surfaceContainer) {
                                                    Box {
                                                        VideoThumbnail(filePath = path, modifier = Modifier.fillMaxSize())
                                                        // Play icon overlay
                                                        Surface(modifier = Modifier.align(Alignment.Center).size(24.dp),
                                                            shape = CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
                                                            Icon(Icons.Default.PlayArrow, null, Modifier.size(14.dp).align(Alignment.Center), tint = Color.White)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Tags
                                    if (record.tags.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            record.tags.take(5).forEach { tag ->
                                                Surface(shape = RoundedCornerShape(10.dp), color = catColor.copy(alpha = 0.08f)) {
                                                    Text(" $tag ", color = catColor, fontSize = 11.sp,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }

        // FAB with scroll show/hide animation
        var fabVisible by remember { mutableStateOf(true) }
        AnimatedVisibility(
            visible = fabVisible,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    if (babyInfo.id.isEmpty() || babyInfo.name.isEmpty()) {
                        Toast.makeText(context, "请先在首页添加宝宝信息", Toast.LENGTH_SHORT).show()
                        return@ExtendedFloatingActionButton
                    }
                    editingRecord = null
                    showAddDialog = true
                },
                containerColor = Color(0xFFEC407A),
                contentColor = Color.White,
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加记录")
                Spacer(Modifier.width(6.dp))
                Text("添加记录")
            }
        }

        // Add/Edit Dialog
        if (showAddDialog) {
            AddRecordDialog(
                initialData = editingRecord,
                onDismiss = {
                    showAddDialog = false
                    editingRecord = null
                },
                onSave = { record ->
                    if (editingRecord != null) {
                        DataManager.deleteRecord(editingRecord!!.id)
                        DataManager.addRecord(record)
                    } else {
                        DataManager.addRecord(record)
                    }
                    showAddDialog = false
                    editingRecord = null
                }
            )
        }

        // Video Player Dialog
        playVideoPath?.let { path ->
            Dialog(
                onDismissRequest = { playVideoPath = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                VideoPlayer(
                    filePath = path,
                    onClose = { playVideoPath = null }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddRecordDialog(
    initialData: TimelineRecord?,
    onDismiss: () -> Unit,
    onSave: (TimelineRecord) -> Unit
) {
    val context = LocalContext.current
    val cal = Calendar.getInstance()

    var date by remember {
        mutableStateOf(initialData?.date ?: "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        ))
    }
    var timeStr by remember {
        mutableStateOf("%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)))
    }
    var title by remember { mutableStateOf(initialData?.title ?: "") }
    var description by remember { mutableStateOf(initialData?.description ?: "") }
    var category by remember { mutableStateOf(initialData?.category ?: "milestone") }
    var subCategory by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(initialData?.tags ?: emptyList()) }
    var selectedPhotos by remember { mutableStateOf(initialData?.photos ?: emptyList()) }
    var selectedVideos by remember { mutableStateOf(initialData?.videos ?: emptyList()) }
    var videoPreviewPath by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val allCategories = DataManager.allCategories
    val selectedCatConfig = allCategories.find { it.id == category }
    val hasSubCategories = (selectedCatConfig?.children?.isNotEmpty() == true)

    // Reset subCategory when category changes
    LaunchedEffect(category) { subCategory = "" }

    val defaultTags = remember(category) {
        when (category) {
            "milestone" -> listOf("翻身", "学坐", "学爬", "学走", "说话")
            "health" -> listOf("体检", "疫苗", "生病", "发育")
            "feeding" -> listOf("母乳", "配方奶", "辅食", "手指食物")
            "sleep" -> listOf("整夜觉", "午睡", "夜醒")
            "play" -> listOf("玩具", "户外", "游戏", "阅读")
            "growth" -> listOf("身高", "体重", "头围", "出牙")
            else -> emptyList()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && selectedPhotos.size < 4) {
            val localPath = DataManager.copyPhotoToInternalStorage(uri)
            selectedPhotos = selectedPhotos + (localPath ?: uri.toString())
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null && selectedVideos.size < 3) {
            val localPath = DataManager.copyVideoToInternalStorage(uri)
            selectedVideos = selectedVideos + (localPath ?: uri.toString())
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (initialData != null) "编辑记录" else "新纪录",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null)
                }
            }

            // Title
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("标题") },
                placeholder = { Text("如：第一次翻身") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Description
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("描述") },
                placeholder = { Text("记录下这个珍贵时刻...") },
                modifier = Modifier.fillMaxWidth(), minLines = 3,
                shape = RoundedCornerShape(12.dp)
            )

            // Date & Time row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(date, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = {
                        val parts = timeStr.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 12
                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        TimePickerDialog(context, { _, hour, minute ->
                            timeStr = "%02d:%02d".format(hour, minute)
                        }, h, m, true).show()
                    },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(timeStr, fontSize = 13.sp)
                }
            }

            // Category
            Column {
                Text("分类", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    allCategories.forEach { cat ->
                        val catColor = Color(cat.color)
                        FilterChip(
                            selected = category == cat.id,
                            onClick = { category = cat.id },
                            label = { Text(cat.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = catColor.copy(alpha = 0.3f),
                                selectedLabelColor = catColor
                            )
                        )
                    }
                }

                // Sub-category selector
                if (hasSubCategories) {
                    Spacer(Modifier.height(6.dp))
                    Text("子分类", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        selectedCatConfig?.children?.forEach { sub ->
                            FilterChip(
                                selected = subCategory == sub.id,
                                onClick = { subCategory = sub.id },
                                label = { Text(sub.name, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // Photos section
            Text("照片 (${selectedPhotos.size}/4)",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedPhotos.size) { index ->
                    val uri = selectedPhotos[index]
                    Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp))) {
                        AuthenticatedAsyncImage(model = uri, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        IconButton(
                            onClick = { selectedPhotos = selectedPhotos.filter { it != uri } },
                            modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        ) {
                            Icon(Icons.Default.Close, null, Modifier.size(12.dp), tint = Color.White)
                        }
                    }
                }
                if (selectedPhotos.size < 4) {
                    item {
                        OutlinedButton(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier.size(72.dp),
                            shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(24.dp), tint = Color(0xFFEC407A))
                        }
                    }
                }
            }

            // Videos section
            Text("视频 (${selectedVideos.size}/3)",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedVideos.size) { index ->
                    val path = selectedVideos[index]
                    Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp))) {
                        VideoThumbnail(
                            filePath = path,
                            modifier = Modifier.fillMaxSize(),
                            onClick = { videoPreviewPath = path }
                        )
                        IconButton(
                            onClick = { selectedVideos = selectedVideos.filter { it != path } },
                            modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        ) {
                            Icon(Icons.Default.Close, null, Modifier.size(12.dp), tint = Color.White)
                        }
                    }
                }
                if (selectedVideos.size < 3) {
                    item {
                        OutlinedButton(
                            onClick = { videoPicker.launch("video/*") },
                            modifier = Modifier.size(72.dp),
                            shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Videocam, null, Modifier.size(24.dp), tint = Color(0xFFEC407A))
                        }
                    }
                }
            }

            // Tags
            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = true, onClick = { tags = tags.filter { it != tag } },
                            label = { Text(tag, fontSize = 11.sp) },
                            trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) }
                        )
                    }
                }
            }
            if (defaultTags.isNotEmpty()) {
                val suggested = defaultTags.filter { it !in tags }.take(5)
                if (suggested.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        suggested.forEach { tag ->
                            AssistChip(
                                onClick = { tags = tags + tag },
                                label = { Text("+$tag", fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }

            // Save button
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val catLabel = if (subCategory.isNotEmpty() && hasSubCategories) {
                            val sub = selectedCatConfig?.children?.find { it.id == subCategory }
                            if (sub != null) "$category|${sub.name}" else category
                        } else category
                        onSave(TimelineRecord(
                            id = initialData?.id ?: "record-${UUID.randomUUID().toString().take(8)}",
                            date = date, title = title.trim(), description = description.trim(),
                            category = catLabel, tags = tags, photos = selectedPhotos, videos = selectedVideos
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A)),
                shape = RoundedCornerShape(16.dp),
                enabled = title.isNotBlank()
            ) {
                Text(if (initialData != null) "保存修改" else "保存记录", color = Color.White,
                    fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (showDatePicker) {
        YueMingTimelineDatePicker(
            initialDate = LocalDate.parse(date),
            onDateSelected = { selectedDate ->
                date = "%04d-%02d-%02d".format(selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth)
            },
            onDismiss = { showDatePicker = false }
        )
    }

    videoPreviewPath?.let { path ->
        Dialog(
            onDismissRequest = { videoPreviewPath = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            VideoPlayer(
                filePath = path,
                onClose = { videoPreviewPath = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YueMingTimelineDatePicker(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toEpochDay() * 86400000L
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    onDateSelected(
                        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    )
                }
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) {
        DatePicker(state = datePickerState)
    }
}
