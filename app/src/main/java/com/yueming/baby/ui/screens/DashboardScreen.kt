package com.yueming.baby.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
import java.time.Instant
import java.time.ZoneId
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
import com.yueming.baby.BabySwitcher
import com.yueming.baby.data.*
import com.yueming.baby.ui.components.AuthenticatedAsyncImage
import com.yueming.baby.ui.components.VideoPlayer
import com.yueming.baby.ui.components.VideoThumbnail
import java.time.LocalDate
import java.util.UUID

private const val DASHBOARD_RECENT_MEDIA_LIMIT = 24

@Composable
fun DashboardScreen() {
    val babies by DataManager.babies.collectAsState()
    val babyInfo by DataManager.babyInfo.collectAsState()
    val timeline by DataManager.timeline.collectAsState()
    val photos by DataManager.photos.collectAsState()
    val isLoading by DataManager.isLoading.collectAsState()

    val ageMonths = remember(babyInfo.birthDate) { DataManager.getAgeInMonths(babyInfo.birthDate) }
    val ageDays = remember(babyInfo.birthDate) { DataManager.getAgeInDays(babyInfo.birthDate) }
    val recentRecords = remember(timeline) { DataManager.getRecentRecords(3) }
    val milestoneCount = remember(timeline) { DataManager.getMilestoneCount() }
    val animatedAgeMonths by animateIntAsState(targetValue = ageMonths, animationSpec = tween(800))
    val animatedDays by animateIntAsState(targetValue = ageDays, animationSpec = tween(1000))
    val animatedMilestone by animateIntAsState(targetValue = milestoneCount, animationSpec = tween(800))
    val animatedTimeline by animateIntAsState(targetValue = timeline.size, animationSpec = tween(800))
    val animatedPhotos by animateIntAsState(targetValue = photos.size, animationSpec = tween(800))
    val recentMedia = remember(photos) {
        photos.sortedByDescending { it.date }.take(DASHBOARD_RECENT_MEDIA_LIMIT)
    }

    val tip = TIPS.find { t -> ageMonths >= t.months.first && ageMonths <= t.months.second } ?: TIPS.last()

    val context = LocalContext.current

    val upcomingMilestones = remember(ageMonths) { getMilestonesForAge(ageMonths) }

    val growthData = remember(timeline) {
        timeline
            .filter { it.category == "growth" }
            .sortedBy { it.date }
            .mapNotNull { record ->
                // Parse height from description like "身长78cm，体重10.2kg"
                val heightRegex = Regex("身长\\s*(\\d+\\.?\\d*)\\s*cm")
                val match = heightRegex.find(record.description)
                if (match != null && match.groupValues.size >= 2) {
                    val height = match.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
                    // Extract a short label from the date
                    val dateParts = record.date.split("-")
                    val label = if (dateParts.size >= 2) "${dateParts[1].toIntOrNull() ?: ""}月" else record.date
                    Triple(label, height, 0xFF86efac.toLong())
                } else {
                    // Also try title
                    val titleMatch = heightRegex.find(record.title)
                    if (titleMatch != null && titleMatch.groupValues.size >= 2) {
                        val height = titleMatch.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
                        val dateParts = record.date.split("-")
                        val label = if (dateParts.size >= 2) "${dateParts[1].toIntOrNull() ?: ""}月" else record.date
                        Triple(label, height, 0xFF86efac.toLong())
                    } else null
                }
            }
    }

    var showAddBaby by remember { mutableStateOf(false) }
    var showGrowthEntry by remember { mutableStateOf(false) }
    var showMilestoneDetail by remember { mutableStateOf(false) }
    var milestoneEntryData by remember { mutableStateOf<Milestone?>(null) }
    var dashboardPreviewPhoto by remember { mutableStateOf<PhotoEntry?>(null) }
    var dashboardPreviewVideoPath by remember { mutableStateOf<String?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val localPath = DataManager.copyPhotoToInternalStorage(uri)
            DataManager.updateBabyInfo(babyInfo.copy(avatar = localPath ?: uri.toString()))
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFFEC407A))
                Spacer(Modifier.height(16.dp))
                Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else if (babies.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ChildCare, null, Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                Spacer(Modifier.height(16.dp))
                Text("还没有添加宝宝",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text("记录宝宝的成长点滴，从添加第一个宝宝开始",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { showAddBaby = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A)),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("添加宝宝", color = Color.White) }
            }
        }
    } else {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header with BabySwitcher
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${DataManager.getGreeting()}，${babyInfo.nickname}的家长",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null,
                            modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${babyInfo.nickname}今天 ${animatedDays} 天大",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFF8C8D8))
                        .clickable { avatarPicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (babyInfo.avatar != null) {
                        AuthenticatedAsyncImage(model = babyInfo.avatar, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            BabySwitcher(
                babies = babies,
                activeBaby = babyInfo,
                onSelect = { DataManager.switchBaby(it.id) },
                onAddBaby = { showAddBaby = true }
            )
        }

        // 2x2 Stats cards with elevation
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedStatCard(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp), tint = Color(0xFFEC407A)) },
                    value = "$animatedAgeMonths",
                    label = "月龄"
                )
                AnimatedStatCard(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Default.Star, null, Modifier.size(18.dp), tint = Color(0xFFF6BA6D)) },
                    value = "$animatedMilestone",
                    label = "里程碑"
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedStatCard(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp), tint = Color(0xFF64B5F6)) },
                    value = "$animatedPhotos",
                    label = "照片"
                )
                AnimatedStatCard(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, null, Modifier.size(18.dp), tint = Color(0xFFAB47BC)) },
                    value = "$animatedTimeline",
                    label = "记录"
                )
            }
        }

        // Upcoming milestones
        if (upcomingMilestones.isNotEmpty()) {
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.clickable { showMilestoneDetail = true }
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Flag, null, Modifier.size(18.dp), tint = Color(0xFFFF9800))
                            Spacer(Modifier.width(8.dp))
                            Text("即将到来的里程碑", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))
                        upcomingMilestones.take(3).forEach { m ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(getMilestoneIcon(m.category), fontSize = 20.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(m.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text(m.description, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Text("${m.ageMonths}月龄", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Growth chart
        item {
            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.clickable { showGrowthEntry = true }
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, null, Modifier.size(18.dp), tint = Color(0xFF4CAF50))
                        Spacer(Modifier.width(8.dp))
                        Text("身长趋势 (cm)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(14.dp))
                    if (growthData.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                Spacer(Modifier.height(8.dp))
                                Text("点击录入第一组身长数据",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        val maxHeightPx = 120f
                        val maxHeight = growthData.maxOf { it.second }
                        Row(
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            growthData.forEach { (label, value, color) ->
                                val height = (value / maxOf(maxHeight, 100f)) * maxHeightPx
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${value.toInt()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier.width(36.dp).height(height.dp)
                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                            .animateContentSize()
                                            .background(Color(color))
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(label, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent records
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, null, Modifier.size(18.dp), tint = Color(0xFFFFC107))
                Spacer(Modifier.width(8.dp))
                Text("最近记录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
        if (recentRecords.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EditNote, null, Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Spacer(Modifier.height(8.dp))
                        Text("还没有成长记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("点击时间线标签开始记录吧", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }
        } else {
            recentRecords.forEach { record ->
                item(key = record.id) {
                    val catColor = Color(getCategoryConfig(record.category)?.color ?: 0xFFe5e7eb)
                    ElevatedCard(
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Row(Modifier.padding(14.dp)) {
                            Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(catColor))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(record.title, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(2.dp))
                                Text(record.date, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (record.description.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(record.description, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (record.tags.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        record.tags.take(3).forEach { tag ->
                                            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                                .background(catColor.copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                                                Text(tag, style = MaterialTheme.typography.labelSmall, color = catColor, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent photos and videos
        if (recentMedia.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp), tint = Color(0xFF64B5F6))
                    Spacer(Modifier.width(8.dp))
                    Text("最新照片", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "左右滑动查看更多",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                RecentMediaCarousel(
                    media = recentMedia,
                    onPhotoClick = { dashboardPreviewPhoto = it },
                    onVideoClick = { dashboardPreviewVideoPath = it.url }
                )
            }
        }

        // Milk brand quick reference
        item {
            MilkBrandSection(
                ageMonths = ageMonths
            )
        }

        // Parenting tip
        item {
            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Row(Modifier.padding(18.dp)) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF8C8D8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Favorite, null, Modifier.size(16.dp), tint = Color(0xFFEC407A))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("育儿小贴士", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("${babyInfo.nickname}现在 ${ageMonths} 个月。${tip.text}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
    }

    // Add Baby Dialog
    if (showAddBaby) {
        AddBabyDialog(
            onDismiss = { showAddBaby = false },
            onAdd = { info ->
                DataManager.addBaby(info)
                showAddBaby = false
            }
        )
    }

    // Growth Entry Sheet
    if (showGrowthEntry) {
        GrowthEntrySheet(
            onDismiss = { showGrowthEntry = false },
            onSave = { record ->
                if (babyInfo.id.isEmpty() || babyInfo.name.isEmpty()) {
                    Toast.makeText(context, "请先在首页添加宝宝信息", Toast.LENGTH_SHORT).show()
                    return@GrowthEntrySheet
                }
                DataManager.addRecord(record)
                showGrowthEntry = false
            }
        )
    }

    // Milestone Detail Dialog
    if (showMilestoneDetail) {
        MilestoneDetailDialog(
            ageMonths = ageMonths,
            onDismiss = { showMilestoneDetail = false },
            onEntry = { milestone ->
                milestoneEntryData = milestone
                showMilestoneDetail = false
            }
        )
    }

    // Milestone Entry Sheet
    milestoneEntryData?.let { milestone ->
        MilestoneEntrySheet(
            milestone = milestone,
            onDismiss = { milestoneEntryData = null },
            onSave = { record ->
                if (babyInfo.id.isEmpty() || babyInfo.name.isEmpty()) {
                    Toast.makeText(context, "请先在首页添加宝宝信息", Toast.LENGTH_SHORT).show()
                    return@MilestoneEntrySheet
                }
                DataManager.addRecord(record)
                milestoneEntryData = null
            }
        )
    }

    dashboardPreviewPhoto?.let { photo ->
        DashboardMediaLightbox(
            photo = photo,
            onDismiss = { dashboardPreviewPhoto = null }
        )
    }

    dashboardPreviewVideoPath?.let { path ->
        Dialog(
            onDismissRequest = { dashboardPreviewVideoPath = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            VideoPlayer(
                filePath = path,
                onClose = { dashboardPreviewVideoPath = null }
            )
        }
    }
}

@Composable
private fun RecentMediaCarousel(
    media: List<PhotoEntry>,
    onPhotoClick: (PhotoEntry) -> Unit,
    onVideoClick: (PhotoEntry) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(132.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(
            items = media,
            key = { it.id },
            contentType = { if (it.isVideoMedia()) "video" else "photo" }
        ) { item ->
            RecentMediaCard(
                photo = item,
                onClick = {
                    if (item.isVideoMedia()) onVideoClick(item) else onPhotoClick(item)
                }
            )
        }
    }
}

@Composable
private fun RecentMediaCard(
    photo: PhotoEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(132.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (photo.isVideoMedia()) {
                VideoThumbnail(
                    filePath = photo.url,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AuthenticatedAsyncImage(
                    model = photo.url,
                    contentDescription = photo.caption,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun DashboardMediaLightbox(
    photo: PhotoEntry,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.clickable { },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    AuthenticatedAsyncImage(
                        model = photo.url,
                        contentDescription = photo.caption,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(photo.caption, color = Color.White, fontWeight = FontWeight.Medium)
                Text(
                    photo.date,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            ) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
            }
        }
    }
}

private fun PhotoEntry.isVideoMedia(): Boolean {
    if (tags.any { it.contains("\u89c6\u9891") || it.equals("video", ignoreCase = true) }) {
        return true
    }
    val cleanUrl = url.substringBefore('?').substringBefore('#').lowercase()
    return listOf(".mp4", ".webm", ".mov", ".m4v", ".3gp", ".mkv").any { cleanUrl.endsWith(it) }
}

@Composable
private fun AnimatedStatCard(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    value: String,
    label: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                icon()
                Text(value, style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBabyDialog(
    onDismiss: () -> Unit,
    onAdd: (BabyInfo) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("2025-01-01") }
    var gender by remember { mutableStateOf("girl") }
    var showAddDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加宝宝", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("宝宝名字") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = nickname, onValueChange = { nickname = it },
                    label = { Text("昵称") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedButton(onClick = { showAddDatePicker = true },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("出生日期: $birthDate")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("girl" to "女宝宝", "boy" to "男宝宝").forEach { (g, label) ->
                        FilterChip(selected = gender == g, onClick = { gender = g },
                            label = { Text(label) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank() && nickname.isNotBlank()) {
                    onAdd(BabyInfo(name = name.trim(), nickname = nickname.trim(), birthDate = birthDate, gender = gender))
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A))) {
                Text("添加", color = Color.White)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showAddDatePicker) {
        YueMingDatePicker(
            initialDate = LocalDate.parse(birthDate),
            onDateSelected = { selectedDate ->
                birthDate = "%04d-%02d-%02d".format(selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth)
            },
            onDismiss = { showAddDatePicker = false }
        )
    }
}

private data class Tip(val months: Pair<Int, Int>, val text: String)

private val TIPS = listOf(
    Tip(0 to 3, "关注抬头训练和视觉刺激，黑白卡是不错的选择。"),
    Tip(4 to 5, "宝宝可能开始添加辅食，从单一食材米粉开始尝试。"),
    Tip(6 to 8, "宝宝开始学坐和爬行，做好家居安全防护。"),
    Tip(9 to 11, "语言爆发期将至，多和宝宝对话、读绘本。"),
    Tip(12 to 99, "进入幼儿期，语言和社交能力快速发展，多户外活动。")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrowthEntrySheet(
    onDismiss: () -> Unit,
    onSave: (TimelineRecord) -> Unit
) {
    val context = LocalContext.current
    val cal = java.util.Calendar.getInstance()
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var date by remember {
        mutableStateOf("%04d-%02d-%02d".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ))
    }
    var selectedPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedVideos by remember { mutableStateOf<List<String>>(emptyList()) }
    var videoPreviewPath by remember { mutableStateOf<String?>(null) }
    var showGrowthDatePicker by remember { mutableStateOf(false) }

    val growthPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && selectedPhotos.size < 4) {
            val localPath = DataManager.copyPhotoToInternalStorage(uri)
            selectedPhotos = selectedPhotos + (localPath ?: uri.toString())
        }
    }

    val growthVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null && selectedVideos.size < 3) {
            val localPath = DataManager.copyVideoToInternalStorage(uri)
            selectedVideos = selectedVideos + (localPath ?: uri.toString())
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("记录身长体重", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = height,
                onValueChange = { height = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("身高 (cm)") },
                placeholder = { Text("如：78.5") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("体重 (kg，可选)") },
                placeholder = { Text("如：10.2") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedButton(
                onClick = { showGrowthDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CalendarToday, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("日期: $date", fontSize = 13.sp)
            }

            // Photo selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedPhotos.forEach { uri ->
                    Box(
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        AuthenticatedAsyncImage(model = uri, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        IconButton(
                            onClick = { selectedPhotos = selectedPhotos.filter { it != uri } },
                            modifier = Modifier.size(20.dp).align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        ) {
                            Icon(Icons.Default.Close, null, Modifier.size(12.dp), tint = Color.White)
                        }
                    }
                }
                if (selectedPhotos.size < 4) {
                    OutlinedButton(
                        onClick = { growthPhotoPicker.launch("image/*") },
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(24.dp), tint = Color(0xFFEC407A))
                    }
                }
            }

            // Video selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedVideos.forEach { path ->
                    Box(
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        VideoThumbnail(
                            filePath = path,
                            modifier = Modifier.fillMaxSize(),
                            onClick = { videoPreviewPath = path }
                        )
                        IconButton(
                            onClick = { selectedVideos = selectedVideos.filter { it != path } },
                            modifier = Modifier.size(20.dp).align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        ) {
                            Icon(Icons.Default.Close, null, Modifier.size(12.dp), tint = Color.White)
                        }
                    }
                }
                if (selectedVideos.size < 3) {
                    OutlinedButton(
                        onClick = { growthVideoPicker.launch("video/*") },
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Videocam, null, Modifier.size(24.dp), tint = Color(0xFFEC407A))
                    }
                }
            }

            Button(
                onClick = {
                    val h = height.trim()
                    if (h.isNotEmpty()) {
                        val descParts = mutableListOf("身长${h}cm")
                        val w = weight.trim()
                        if (w.isNotEmpty()) descParts.add("体重${w}kg")
                        val desc = descParts.joinToString("，")

                        onSave(TimelineRecord(
                            id = "growth-${UUID.randomUUID().toString().take(8)}",
                            date = date,
                            title = "身长${h}cm" + if (w.isNotEmpty()) " 体重${w}kg" else "",
                            description = desc,
                            category = "growth",
                            tags = listOfNotNull(
                                "身长",
                                if (w.isNotEmpty()) "体重" else null,
                                "生长发育"
                            ),
                            photos = selectedPhotos,
                            videos = selectedVideos
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A)),
                shape = RoundedCornerShape(16.dp),
                enabled = height.trim().isNotEmpty()
            ) { Text("保存记录", color = Color.White) }
        }
    }

    if (showGrowthDatePicker) {
        YueMingDatePicker(
            initialDate = LocalDate.parse(date),
            onDateSelected = { selectedDate ->
                date = "%04d-%02d-%02d".format(selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth)
            },
            onDismiss = { showGrowthDatePicker = false }
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

@Composable
private fun MilestoneDetailDialog(
    ageMonths: Int,
    onDismiss: () -> Unit,
    onEntry: (Milestone) -> Unit
) {
    val ageGroups = listOf(
        "0-6个月" to (0..6),
        "6-12个月" to (7..12),
        "12-24个月" to (13..24),
        "24-36个月" to (25..36)
    )

    val groupedMilestones = remember {
        ageGroups.map { (label, range) ->
            label to ALL_MILESTONES.filter { it.ageMonths in range }
        }
    }

    var selectedGroup by remember { mutableStateOf(ageGroups.first().first) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("成长里程碑", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Age group tabs
                ScrollableTabRow(
                    selectedTabIndex = ageGroups.indexOfFirst { it.first == selectedGroup }.coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    ageGroups.forEach { (label, _) ->
                        Tab(
                            selected = selectedGroup == label,
                            onClick = { selectedGroup = label },
                            text = {
                                Text(label, fontSize = 12.sp,
                                    color = if (selectedGroup == label) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                val currentMilestones = groupedMilestones.find { it.first == selectedGroup }?.second ?: emptyList()
                if (currentMilestones.isEmpty()) {
                    Text("该阶段暂无里程碑数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp))
                }
                currentMilestones.forEach { milestone ->
                    val isCurrentAge = milestone.ageMonths <= ageMonths + 1 && milestone.ageMonths >= ageMonths - 1
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onEntry(milestone) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentAge) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.surfaceContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(getMilestoneIcon(milestone.category), fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(milestone.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(milestone.description, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${milestone.ageMonths}月",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCurrentAge) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (isCurrentAge) {
                                    Text("当前",
                                        fontSize = 10.sp,
                                        color = Color(0xFFFF9800),
                                        fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        modifier = Modifier.fillMaxWidth(0.95f)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MilestoneEntrySheet(
    milestone: Milestone,
    onDismiss: () -> Unit,
    onSave: (TimelineRecord) -> Unit
) {
    val cal = java.util.Calendar.getInstance()
    var title by remember { mutableStateOf(milestone.title) }
    var description by remember { mutableStateOf(milestone.description) }
    var date by remember {
        mutableStateOf("%04d-%02d-%02d".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ))
    }
    var selectedPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedVideos by remember { mutableStateOf<List<String>>(emptyList()) }
    var videoPreviewPath by remember { mutableStateOf<String?>(null) }
    var showMilestoneDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val photoPicker = rememberLauncherForActivityResult(
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
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(getMilestoneIcon(milestone.category), fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text("记录里程碑", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("标题") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("描述") },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedButton(
                onClick = { showMilestoneDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CalendarToday, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("日期: $date", fontSize = 13.sp)
            }

            // Photo selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedPhotos.forEach { uri ->
                    Box(
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        AuthenticatedAsyncImage(model = uri, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        IconButton(
                            onClick = { selectedPhotos = selectedPhotos.filter { it != uri } },
                            modifier = Modifier.size(20.dp).align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        ) {
                            Icon(Icons.Default.Close, null, Modifier.size(12.dp), tint = Color.White)
                        }
                    }
                }
                if (selectedPhotos.size < 4) {
                    OutlinedButton(
                        onClick = { photoPicker.launch("image/*") },
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(24.dp), tint = Color(0xFFEC407A))
                    }
                }
            }

            // Video selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedVideos.forEach { path ->
                    Box(
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        VideoThumbnail(
                            filePath = path,
                            modifier = Modifier.fillMaxSize(),
                            onClick = { videoPreviewPath = path }
                        )
                        IconButton(
                            onClick = { selectedVideos = selectedVideos.filter { it != path } },
                            modifier = Modifier.size(20.dp).align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        ) {
                            Icon(Icons.Default.Close, null, Modifier.size(12.dp), tint = Color.White)
                        }
                    }
                }
                if (selectedVideos.size < 3) {
                    OutlinedButton(
                        onClick = { videoPicker.launch("video/*") },
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Videocam, null, Modifier.size(24.dp), tint = Color(0xFFEC407A))
                    }
                }
            }

            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(TimelineRecord(
                            id = "milestone-${UUID.randomUUID().toString().take(8)}",
                            date = date,
                            title = title.trim(),
                            description = description.trim(),
                            category = milestone.category,
                            tags = listOf("里程碑", milestone.title),
                            photos = selectedPhotos,
                            videos = selectedVideos
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A)),
                shape = RoundedCornerShape(16.dp)
            ) { Text("保存里程碑", color = Color.White) }
        }
    }

    if (showMilestoneDatePicker) {
        YueMingDatePicker(
            initialDate = LocalDate.parse(date),
            onDateSelected = { selectedDate ->
                date = "%04d-%02d-%02d".format(selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth)
            },
            onDismiss = { showMilestoneDatePicker = false }
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

@Composable
private fun MilkBrandSection(ageMonths: Int) {
    var expanded by remember { mutableStateOf(false) }
    var selectedBrand by remember { mutableStateOf<MilkBrand?>(null) }
    var favorites by remember { mutableStateOf<Set<String>>(emptySet()) }

    val displayBrands = if (expanded) MILK_BRANDS else MILK_BRANDS.take(4)

    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, Modifier.size(18.dp), tint = Color(0xFFEC407A))
                Spacer(Modifier.width(8.dp))
                Text("奶粉品牌速查", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))

            // Favorite brands displayed first
            val favBrands = displayBrands.filter { favorites.contains(it.id) }
            val nonFavBrands = displayBrands.filter { !favorites.contains(it.id) }
            val sortedBrands = favBrands + nonFavBrands

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sortedBrands.forEach { brand ->
                    val isFav = favorites.contains(brand.id)
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isFav) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.surface)
                            .clickable { selectedBrand = brand }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            if (isFav) {
                                Icon(Icons.Default.Favorite, null, Modifier.size(14.dp), tint = Color(0xFFFF9800))
                                Spacer(Modifier.width(6.dp))
                            }
                            Column {
                                Text(brand.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text(
                                    "${brand.type.let { if (it == "goat") "羊奶" else "牛奶" }} · ${brand.origin}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                favorites = if (isFav) favorites - brand.id else favorites + brand.id
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (isFav) "取消收藏" else "收藏",
                                modifier = Modifier.size(16.dp),
                                tint = if (isFav) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }

            // Expand/Collapse button
            if (MILK_BRANDS.size > 4) {
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (expanded) "收起 (${MILK_BRANDS.size}个品牌)" else "展开全部 ${MILK_BRANDS.size} 个品牌",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Brand Detail Sheet
    selectedBrand?.let { brand ->
        MilkBrandDetailSheet(
            brand = brand,
            isFavorite = favorites.contains(brand.id),
            onToggleFavorite = {
                favorites = if (favorites.contains(brand.id)) favorites - brand.id else favorites + brand.id
            },
            onDismiss = { selectedBrand = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MilkBrandDetailSheet(
    brand: MilkBrand,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit
) {
    val milkType = when {
        brand.type == "goat" -> "羊奶粉"
        brand.type == "hydrolyzed" -> "水解蛋白"
        else -> "牛奶粉"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(brand.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(brand.brand, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Basic info
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row { Text("类型: ", fontWeight = FontWeight.Medium, fontSize = 13.sp); Text(milkType, fontSize = 13.sp) }
                    Row { Text("产地: ", fontWeight = FontWeight.Medium, fontSize = 13.sp); Text(brand.origin, fontSize = 13.sp) }
                    Row {
                        Text("段数: ", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text(brand.stages.joinToString("、"), fontSize = 13.sp)
                    }
                }
            }

            // Features
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("产品特点", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(brand.notes, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Brewing/feeding suggestions
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("冲调建议", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("• 水温40-45°C，先水后粉", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• 按奶粉罐标注比例冲泡，每平勺约4.3g", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• 冲泡后轻摇瓶身避免气泡，不要剧烈摇晃", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Transition tips
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("转奶建议", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("• 新旧奶粉按比例逐步过渡：Day1-2: 1/3新+2/3旧", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• Day3-4: 各半；Day5-6: 2/3新+1/3旧", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• 观察3-5天无不适后再完全转换", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YueMingDatePicker(
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
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
