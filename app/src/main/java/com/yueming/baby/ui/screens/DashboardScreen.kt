package com.yueming.baby.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.yueming.baby.BabySwitcher
import com.yueming.baby.data.*
import com.yueming.baby.ui.components.AppEditorDialog
import com.yueming.baby.ui.components.AuthenticatedAsyncImage
import com.yueming.baby.ui.components.VideoPlayer
import com.yueming.baby.ui.components.VideoThumbnail
import com.yueming.baby.ui.motion.miuixPressable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DASHBOARD_RECENT_MEDIA_LIMIT = 24
private val REMINDER_EDITOR_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun DashboardScreen() {
    val babies by DataManager.babies.collectAsState()
    val babyInfo by DataManager.babyInfo.collectAsState()
    val timeline by DataManager.timeline.collectAsState()
    val photos by DataManager.photos.collectAsState()
    val feedingRecords by DataManager.feedingRecords.collectAsState()
    val vaccineStatuses by DataManager.vaccineStatuses.collectAsState()
    val reminders by DataManager.reminders.collectAsState()
    val isLoading by DataManager.isLoading.collectAsState()

    val activeTimeline = remember(timeline, babyInfo.id) {
        timeline.filter { belongsToBaby(it.babyId, babyInfo.id) }
    }
    val activePhotos = remember(photos, babyInfo.id) {
        photos.filter { belongsToBaby(it.babyId, babyInfo.id) }
    }
    val activeFeedingRecords = remember(feedingRecords, babyInfo.id) {
        feedingRecords.filter { belongsToBaby(it.babyId, babyInfo.id) }
    }
    val activeReminderCount = remember(reminders, babyInfo.id) {
        reminders.count { belongsToBaby(it.babyId, babyInfo.id) && !it.isCompleted }
    }
    val activeUpcomingReminders = remember(reminders, babyInfo.id) {
        upcomingReminders(reminders, babyInfo.id, limit = 3)
    }

    val ageMonths = remember(babyInfo.birthDate) { DataManager.getAgeInMonths(babyInfo.birthDate) }
    val ageDays = remember(babyInfo.birthDate) { DataManager.getAgeInDays(babyInfo.birthDate) }
    val recentRecords = remember(activeTimeline) { activeTimeline.sortedByDescending { it.date }.take(3) }
    val milestoneCount = remember(activeTimeline) { activeTimeline.count { it.category == "milestone" } }
    val animatedAgeMonths by animateIntAsState(targetValue = ageMonths, animationSpec = tween(800))
    val animatedDays by animateIntAsState(targetValue = ageDays, animationSpec = tween(1000))
    val animatedMilestone by animateIntAsState(targetValue = milestoneCount, animationSpec = tween(800))
    val animatedTimeline by animateIntAsState(targetValue = activeTimeline.size, animationSpec = tween(800))
    val animatedPhotos by animateIntAsState(targetValue = activePhotos.size, animationSpec = tween(800))
    val recentMedia = remember(activePhotos) {
        activePhotos.sortedByDescending { it.date }.take(DASHBOARD_RECENT_MEDIA_LIMIT)
    }

    val tip = TIPS.find { t -> ageMonths >= t.months.first && ageMonths <= t.months.second } ?: TIPS.last()
    val todayStart = remember {
        java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond() * 1000
    }
    val todayFeeding = remember(activeFeedingRecords) {
        activeFeedingRecords.filter { it.timestamp >= todayStart }
    }
    val todayFormulaMl = remember(todayFeeding) {
        todayFeeding.filter { it.type == "formula" || it.type == "water" }.sumOf { it.volumeMl }
    }
    val nextVaccine = remember(vaccineStatuses, ageMonths, babyInfo.id) {
        DataManager.getNextVaccine(babyInfo.birthDate)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val upcomingMilestones = remember(ageMonths) { getMilestonesForAge(ageMonths) }

    val growthData = remember(activeTimeline) {
        activeTimeline
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
    var showFeedingScreen by remember { mutableStateOf(false) }
    var showVaccineScreen by remember { mutableStateOf(false) }
    var showReminderEditor by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    var pendingReminderSave by remember { mutableStateOf<Pair<Reminder, Boolean>?>(null) }

    fun persistReminder(reminder: Reminder, addToCalendar: Boolean) {
        DataManager.upsertReminder(reminder)
        if (addToCalendar) {
            launchReminderCalendarInsert(context, reminder)
        }
        Toast.makeText(context, "提醒已保存", Toast.LENGTH_SHORT).show()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingReminderSave?.let { (reminder, addToCalendar) ->
            val finalReminder = if (granted) reminder else reminder.copy(notify = false)
            if (!granted) {
                Toast.makeText(context, "通知权限未开启，提醒会保存在首页待办", Toast.LENGTH_SHORT).show()
            }
            persistReminder(finalReminder, addToCalendar)
        }
        pendingReminderSave = null
    }

    fun saveReminder(reminder: Reminder, addToCalendar: Boolean) {
        val needsNotificationPermission = reminder.notify &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            pendingReminderSave = reminder to addToCalendar
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            persistReminder(reminder, addToCalendar)
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val localPath = withContext(Dispatchers.IO) {
                    DataManager.copyPhotoToInternalStorage(uri)
                }
                DataManager.updateBabyInfo(babyInfo.copy(avatar = localPath ?: uri.toString()))
            }
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
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "${DataManager.getGreeting()}，${babyInfo.nickname}的家长",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Medium
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(34.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    babyInfo.nickname.ifBlank { "宝宝" },
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "今天 $animatedDays 天大 · 已记录 $animatedTimeline 条成长瞬间",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                    .clickable { avatarPicker.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                if (babyInfo.avatar != null) {
                                    AuthenticatedAsyncImage(
                                        model = babyInfo.avatar,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        babyInfo.nickname.take(1).ifBlank { "宝" },
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 28.sp
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DashboardInfoChip("月龄", "$animatedAgeMonths 个月", Color(0xFFEC407A))
                            DashboardInfoChip("照片", "$animatedPhotos 张", Color(0xFF42A5F5))
                            DashboardInfoChip("喂养", "${todayFeeding.size} 次", Color(0xFFF6BA6D))
                        }
                    }
                }
                BabySwitcher(
                    babies = babies,
                    activeBaby = babyInfo,
                    onSelect = { DataManager.switchBaby(it.id) },
                    onAddBaby = { showAddBaby = true }
                )
            }
        }

        item {
            ReminderDashboardPanel(
                reminders = activeUpcomingReminders,
                openCount = activeReminderCount,
                onAdd = {
                    editingReminder = null
                    showReminderEditor = true
                },
                onEdit = { reminder ->
                    editingReminder = reminder
                    showReminderEditor = true
                },
                onComplete = { DataManager.completeReminder(it.id) },
                onDelete = { DataManager.deleteReminder(it.id) },
                modifier = Modifier.animateItem()
            )
        }

        item {
            Column(Modifier.animateItem()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnimatedStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.CalendarToday,
                        accent = Color(0xFFEC407A),
                        value = "$animatedAgeMonths",
                        label = "月龄"
                    )
                    AnimatedStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Star,
                        accent = Color(0xFFF6BA6D),
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
                        icon = Icons.Default.PhotoLibrary,
                        accent = Color(0xFF64B5F6),
                        value = "$animatedPhotos",
                        label = "照片"
                    )
                    AnimatedStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        accent = Color(0xFFAB47BC),
                        value = "$animatedTimeline",
                        label = "记录"
                    )
                }
            }
        }

        if (upcomingMilestones.isNotEmpty()) {
            item {
                MiuixDashboardPanel(
                    icon = Icons.Default.Flag,
                    accent = Color(0xFFFF9800),
                    title = "即将到来的里程碑",
                    modifier = Modifier.animateItem().clickable { showMilestoneDetail = true }
                ) {
                    upcomingMilestones.take(3).forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f))
                                .padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(getMilestoneIcon(m.category), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(m.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    m.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            DashboardPill("${m.ageMonths}月龄", Color(0xFFFF9800))
                        }
                    }
                }
            }
        }

        item {
            MiuixDashboardPanel(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                accent = Color(0xFF4CAF50),
                title = "身长趋势",
                modifier = Modifier.clickable { showGrowthEntry = true }
            ) {
                if (growthData.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.AutoMirrored.Filled.TrendingUp,
                                null,
                                Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "点击录入第一组身长数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                                Text("${value.toInt()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .height(height.dp)
                                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                        .animateContentSize()
                                        .background(Color(color))
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item {
            DashboardSectionTitle(
                icon = Icons.Default.Star,
                accent = Color(0xFFFFC107),
                title = "最近记录",
                subtitle = "把最近的成长片段集中放在首页"
            )
        }
        if (recentRecords.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                ) {
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
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(0.5.dp, catColor.copy(alpha = 0.2f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                        modifier = Modifier.animateItem()
                    ) {
                        Row(Modifier.padding(16.dp)) {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(catColor.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp), tint = catColor)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        record.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    DashboardPill(record.date, catColor)
                                }
                                if (record.description.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(record.description, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (record.tags.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        record.tags.take(3).forEach { tag ->
                                            Box(modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                                .background(catColor.copy(alpha = 0.12f))
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
                DashboardSectionTitle(
                    icon = Icons.Default.PhotoLibrary,
                    accent = Color(0xFF64B5F6),
                    title = "最新照片",
                    subtitle = "左右滑动查看更多"
                )
            }
            item {
                Box(Modifier.animateItem()) {
                    RecentMediaCarousel(
                        media = recentMedia,
                        onPhotoClick = { dashboardPreviewPhoto = it },
                        onVideoClick = { dashboardPreviewVideoPath = it.url }
                    )
                }
            }
        }

        item {
            MiuixActionCard(
                icon = Icons.Default.Fastfood,
                accent = Color(0xFFF6BA6D),
                title = "喂养日志",
                subtitle = if (todayFeeding.isNotEmpty()) {
                    "今日 ${todayFeeding.size} 次 · ${todayFormulaMl}ml"
                } else {
                    "点击记录今天的喂养"
                },
                onClick = { showFeedingScreen = true },
                modifier = Modifier.fillMaxWidth().animateItem()
            )
        }

        item {
            MiuixActionCard(
                icon = Icons.Default.MedicalServices,
                accent = Color(0xFF4CAF50),
                title = "疫苗接种",
                subtitle = if (nextVaccine != null) {
                    val (vax, monthsUntil) = nextVaccine
                    if (monthsUntil <= 0) "下一针: ${vax.name} (已到期)" else "下一针: ${vax.name} · ${monthsUntil}个月后"
                } else {
                    "查看接种计划"
                },
                onClick = { showVaccineScreen = true },
                modifier = Modifier.fillMaxWidth().animateItem()
            )
        }

        item {
            MiuixDashboardPanel(
                icon = Icons.Default.Favorite,
                accent = Color(0xFFEC407A),
                title = "育儿小贴士",
                modifier = Modifier.animateItem()
            ) {
                Text(
                    "${babyInfo.nickname}现在 ${ageMonths} 个月。${tip.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
    }

    if (showReminderEditor) {
        ReminderEditorDialog(
            babyId = babyInfo.id,
            reminder = editingReminder,
            onDismiss = {
                showReminderEditor = false
                editingReminder = null
            },
            onSave = { reminder, addToCalendar ->
                saveReminder(reminder, addToCalendar)
                showReminderEditor = false
                editingReminder = null
            }
        )
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

    // Feeding screen
    if (showFeedingScreen) {
        Dialog(
            onDismissRequest = { showFeedingScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, decorFitsSystemWindows = false)
        ) {
            FeedingScreen(onDismiss = { showFeedingScreen = false })
        }
    }

    // Vaccine screen
    if (showVaccineScreen) {
        Dialog(
            onDismissRequest = { showVaccineScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, decorFitsSystemWindows = false)
        ) {
            VaccineScreen(onDismiss = { showVaccineScreen = false })
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
    val displayUrl = photo.thumbnailPath ?: photo.url
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .size(132.dp)
            .miuixPressable(interactionSource, pressedScale = 0.96f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (photo.isVideoMedia()) {
                if (photo.thumbnailPath != null) {
                    AuthenticatedAsyncImage(
                        model = displayUrl,
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
                    model = displayUrl,
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
    if (mediaType.equals("video", ignoreCase = true)) {
        return true
    }
    if (tags.any { it.contains("\u89c6\u9891") || it.equals("video", ignoreCase = true) }) {
        return true
    }
    val cleanUrl = url.substringBefore('?').substringBefore('#').lowercase()
    return listOf(".mp4", ".webm", ".mov", ".m4v", ".3gp", ".mkv").any { cleanUrl.endsWith(it) }
}

@Composable
private fun ReminderDashboardPanel(
    reminders: List<Reminder>,
    openCount: Int,
    onAdd: () -> Unit,
    onEdit: (Reminder) -> Unit,
    onComplete: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFF26A69A)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(0.5.dp, accent.copy(alpha = 0.24f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Notifications, null, Modifier.size(19.dp), tint = accent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("待办提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (openCount > 0) "$openCount 条待处理" else "复查、疫苗和用药都可以放这里",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.12f))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加提醒", tint = accent)
                }
            }

            if (reminders.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
                        .clickable(onClick = onAdd)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.EventNote, null, Modifier.size(22.dp), tint = accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("添加一条复查提醒", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("例如：两周后复查血常规", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                reminders.forEach { reminder ->
                    ReminderDashboardRow(
                        reminder = reminder,
                        onEdit = { onEdit(reminder) },
                        onComplete = { onComplete(reminder) },
                        onDelete = { onDelete(reminder) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderDashboardRow(
    reminder: Reminder,
    onEdit: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = Color(reminderCategoryColor(reminder.category))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .clickable(onClick = onEdit)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onComplete,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.RadioButtonUnchecked, contentDescription = "完成提醒", tint = accent)
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(reminderCategoryIcon(reminder.category), null, Modifier.size(18.dp), tint = accent)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reminder.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                DashboardPill(reminderDueText(reminder.dueAt), accent)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                listOf(reminderCategoryLabel(reminder.category), reminder.notes).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "删除提醒", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderEditorDialog(
    babyId: String,
    reminder: Reminder?,
    onDismiss: () -> Unit,
    onSave: (Reminder, Boolean) -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val initialDueAt = remember(reminder?.id) { reminder?.dueAt ?: defaultReminderDueAt(now) }
    val initialDateTime = remember(initialDueAt) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(initialDueAt), ZoneId.systemDefault())
    }
    var title by remember(reminder?.id) { mutableStateOf(reminder?.title ?: "复查血常规") }
    var category by remember(reminder?.id) { mutableStateOf(reminder?.category ?: REMINDER_CATEGORY_CHECKUP) }
    var notes by remember(reminder?.id) { mutableStateOf(reminder?.notes ?: "") }
    var dueDate by remember(reminder?.id) { mutableStateOf(initialDateTime.toLocalDate()) }
    var timeText by remember(reminder?.id) { mutableStateOf(initialDateTime.toLocalTime().format(REMINDER_EDITOR_TIME_FORMATTER)) }
    var notify by remember(reminder?.id) { mutableStateOf(reminder?.notify ?: true) }
    var addToCalendar by remember(reminder?.id) { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val parsedTime = parseReminderTime(timeText)
    val canSave = title.trim().isNotEmpty() && parsedTime != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 620.dp),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.EventNote, null, Modifier.size(24.dp), tint = Color(0xFF26A69A))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (reminder == null) "添加提醒" else "编辑提醒",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("事项") },
                    placeholder = { Text("例如：复查血常规") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(REMINDER_CATEGORIES, key = { it.id }) { item ->
                        FilterChip(
                            selected = category == item.id,
                            onClick = { category = item.id },
                            label = { Text(item.label) },
                            leadingIcon = {
                                Icon(reminderCategoryIcon(item.id), null, Modifier.size(16.dp))
                            }
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        AssistChip(
                            onClick = {
                                dueDate = LocalDate.now()
                                timeText = "09:00"
                            },
                            label = { Text("今天") },
                            leadingIcon = { Icon(Icons.Default.Today, null, Modifier.size(16.dp)) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = {
                                dueDate = LocalDate.now().plusDays(1)
                                timeText = "09:00"
                            },
                            label = { Text("明天") },
                            leadingIcon = { Icon(Icons.Default.WbSunny, null, Modifier.size(16.dp)) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = {
                                dueDate = LocalDate.now().plusDays(14)
                                timeText = "09:00"
                            },
                            label = { Text("2周后") },
                            leadingIcon = { Icon(Icons.Default.EventRepeat, null, Modifier.size(16.dp)) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ReminderPickerField(
                        label = "日期",
                        value = "%02d/%02d".format(dueDate.monthValue, dueDate.dayOfMonth),
                        icon = Icons.Default.CalendarToday,
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f)
                    )
                    ReminderTimeField(
                        value = timeText,
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("备注") },
                    placeholder = { Text("地点、医生交代或注意事项") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                ReminderSwitchRow(
                    icon = Icons.Default.Notifications,
                    title = "到时间发通知",
                    checked = notify,
                    onCheckedChange = { notify = it }
                )
                ReminderSwitchRow(
                    icon = Icons.Default.CalendarMonth,
                    title = "同时添加到系统日历",
                    checked = addToCalendar,
                    onCheckedChange = { addToCalendar = it }
                )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            val time = parsedTime ?: return@Button
                            val dueAt = dueDate
                                .atTime(time)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                            onSave(
                                Reminder(
                                    id = reminder?.id ?: "reminder-${UUID.randomUUID().toString().take(8)}",
                                    babyId = reminder?.babyId?.takeIf { it.isNotBlank() } ?: babyId,
                                    title = title.trim(),
                                    dueAt = dueAt,
                                    category = category,
                                    notes = notes.trim(),
                                    notify = notify,
                                    calendarSynced = reminder?.calendarSynced == true || addToCalendar,
                                    completedAt = reminder?.completedAt,
                                    createdAt = reminder?.createdAt ?: System.currentTimeMillis()
                                ),
                                addToCalendar
                            )
                        },
                        enabled = canSave,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26A69A))
                    ) {
                        Text("保存", color = Color.White)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        YueMingDatePicker(
            initialDate = dueDate,
            onDateSelected = { dueDate = it },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showTimePicker) {
        YueMingTimePicker(
            initialTime = parsedTime ?: LocalTime.of(9, 0),
            onTimeSelected = { selectedTime ->
                timeText = selectedTime.format(REMINDER_EDITOR_TIME_FORMATTER)
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@Composable
private fun ReminderSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ReminderPickerField(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ReminderFieldFrame(
        modifier = modifier.clickable(onClick = onClick),
        isError = false
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReminderTimeField(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ReminderFieldFrame(
        modifier = modifier.clickable(onClick = onClick),
        isError = false
    ) {
        Icon(Icons.Default.AccessTime, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("时间", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReminderFieldFrame(
    modifier: Modifier = Modifier,
    isError: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    val borderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.62f)
    Row(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

private fun parseReminderTime(value: String): LocalTime? {
    val parts = value.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return LocalTime.of(hour, minute)
}

private fun reminderCategoryIcon(category: String): ImageVector {
    return when (category) {
        REMINDER_CATEGORY_CHECKUP -> Icons.Default.MedicalServices
        REMINDER_CATEGORY_VACCINE -> Icons.Default.Vaccines
        REMINDER_CATEGORY_MEDICINE -> Icons.Default.Medication
        REMINDER_CATEGORY_TEST -> Icons.Default.Science
        else -> Icons.AutoMirrored.Filled.EventNote
    }
}

private fun launchReminderCalendarInsert(context: android.content.Context, reminder: Reminder) {
    val description = buildString {
        append(reminderCategoryLabel(reminder.category))
        if (reminder.notes.isNotBlank()) {
            append("\n")
            append(reminder.notes)
        }
    }
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, reminder.title)
        putExtra(CalendarContract.Events.DESCRIPTION, description)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, reminder.dueAt)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, reminder.dueAt + 30 * 60 * 1000L)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "没有找到可用的日历应用", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun DashboardSectionTitle(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = accent)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DashboardInfoChip(label: String, value: String, accent: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DashboardPill(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiuixDashboardPanel(
    icon: ImageVector,
    accent: Color,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, Modifier.size(19.dp), tint = accent)
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun MiuixActionCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = modifier
            .miuixPressable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(30.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(20.dp), tint = accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f))
            }
        }
    }
}

@Composable
private fun AnimatedStatCard(
    modifier: Modifier,
    icon: ImageVector,
    accent: Color,
    value: String,
    label: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(0.5.dp, accent.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.08f))
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, Modifier.size(18.dp), tint = accent)
                }
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val scope = rememberCoroutineScope()
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
            scope.launch {
                val localPath = withContext(Dispatchers.IO) {
                    DataManager.copyPhotoToInternalStorage(uri)
                }
                selectedPhotos = selectedPhotos + (localPath ?: uri.toString())
            }
        }
    }

    val growthVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null && selectedVideos.size < 3) {
            scope.launch {
                val localPath = withContext(Dispatchers.IO) {
                    DataManager.copyVideoToInternalStorage(uri)
                }
                selectedVideos = selectedVideos + (localPath ?: uri.toString())
            }
        }
    }

    AppEditorDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background
    ) { requestClose ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("记录身长体重", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = requestClose) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

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
                            Icon(getMilestoneIcon(milestone.category), null, Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary)
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
    val scope = rememberCoroutineScope()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && selectedPhotos.size < 4) {
            scope.launch {
                val localPath = withContext(Dispatchers.IO) {
                    DataManager.copyPhotoToInternalStorage(uri)
                }
                selectedPhotos = selectedPhotos + (localPath ?: uri.toString())
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null && selectedVideos.size < 3) {
            scope.launch {
                val localPath = withContext(Dispatchers.IO) {
                    DataManager.copyVideoToInternalStorage(uri)
                }
                selectedVideos = selectedVideos + (localPath ?: uri.toString())
            }
        }
    }

    AppEditorDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background
    ) { requestClose ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        getMilestoneIcon(milestone.category),
                        null,
                        Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("记录里程碑", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = requestClose) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
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

@Composable
fun YueMingTimePicker(
    initialTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedHour by remember(initialTime) { mutableStateOf(initialTime.hour) }
    var selectedMinute by remember(initialTime) { mutableStateOf(initialTime.minute) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 380.dp),
            shape = RoundedCornerShape(30.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "选择时间",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "%02d:%02d".format(selectedHour, selectedMinute),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(236.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "小时",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MiuixWheelPicker(
                            values = (0..23).toList(),
                            selectedValue = selectedHour,
                            onValueChange = { selectedHour = it },
                            valueLabel = { "%02d".format(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text(
                        ":",
                        modifier = Modifier.padding(top = 28.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "分钟",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MiuixWheelPicker(
                            values = (0..59).toList(),
                            selectedValue = selectedMinute,
                            onValueChange = { selectedMinute = it },
                            valueLabel = { "%02d".format(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            onTimeSelected(LocalTime.of(selectedHour, selectedMinute))
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26A69A))
                    ) {
                        Text("确定", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixWheelPicker(
    values: List<Int>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    valueLabel: (Int) -> String,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 42.dp
) {
    val visibleItems = 5
    val centerPadding = visibleItems / 2
    val displayValues = remember(values) {
        List(centerPadding) { null } + values.map<Int, Int?> { it } + List(centerPadding) { null }
    }
    val initialIndex = remember(values, selectedValue) {
        values.indexOf(selectedValue).coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val scope = rememberCoroutineScope()
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    fun nearestValueIndex(): Int {
        if (values.isEmpty()) return 0
        val scrollRows = (listState.firstVisibleItemScrollOffset / itemHeightPx).roundToInt()
        return (listState.firstVisibleItemIndex + scrollRows).coerceIn(values.indices)
    }

    LaunchedEffect(values, listState, itemHeightPx) {
        snapshotFlow { nearestValueIndex() }
            .distinctUntilChanged()
            .collect { index ->
                values.getOrNull(index)?.let(latestOnValueChange)
            }
    }

    LaunchedEffect(values, listState, itemHeightPx) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (!isScrolling && listState.firstVisibleItemScrollOffset != 0) {
                    listState.animateScrollToItem(nearestValueIndex())
                }
            }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleItems)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.38f))
            .border(
                BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
                RoundedCornerShape(22.dp)
            )
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true
        ) {
            itemsIndexed(
                items = displayValues,
                key = { index, value -> value?.let { "value-$it" } ?: "pad-$index" }
            ) { index, value ->
                val valueIndex = index - centerPadding
                val distance = abs(valueIndex - values.indexOf(selectedValue))
                val isSelected = value == selectedValue
                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = when (distance) {
                            1 -> 0.72f
                            2 -> 0.42f
                            else -> 0.24f
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable(enabled = value != null) {
                            val targetIndex = valueIndex.coerceIn(values.indices)
                            values.getOrNull(targetIndex)?.let(latestOnValueChange)
                            scope.launch { listState.animateScrollToItem(targetIndex) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (value != null) {
                        Text(
                            text = valueLabel(value),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = if (isSelected) {
                                MaterialTheme.typography.headlineSmall
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = contentColor
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                .border(
                    BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                    RoundedCornerShape(16.dp)
                )
        )
    }
}
