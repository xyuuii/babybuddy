package com.yueming.baby.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.yueming.baby.data.*
import com.yueming.baby.ui.components.AppEditorDialog
import com.yueming.baby.ui.components.AuthenticatedAsyncImage
import com.yueming.baby.ui.components.VideoPlayer
import com.yueming.baby.ui.components.VideoThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.UUID

// ── Helpers ──────────────────────────────────────────────────────────

private val primaryPink = Color(0xFFEC407A)

private fun catIcon(catId: String): ImageVector = when (catId) {
    "milestone" -> Icons.Default.Star
    "health"    -> Icons.Default.FavoriteBorder
    "feeding"   -> Icons.Default.Fastfood
    "sleep"     -> Icons.Default.Star
    "play"      -> Icons.Default.PlayArrow
    "growth"    -> Icons.Default.TrendingUp
    else        -> Icons.Default.Description
}

private fun appendRecognizedText(current: String, recognized: String): String {
    val cleaned = recognized.trim()
    if (cleaned.isBlank()) return current
    return if (current.isBlank()) cleaned else current.trimEnd() + "\n" + cleaned
}

private fun speechErrorText(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_AUDIO -> "麦克风暂时不可用"
    SpeechRecognizer.ERROR_CLIENT -> "语音输入已取消"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "需要麦克风权限"
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络不稳定，稍后再试"
    SpeechRecognizer.ERROR_NO_MATCH -> "没有听清楚，再说一次试试"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音服务正忙，稍后再试"
    SpeechRecognizer.ERROR_SERVER -> "语音服务暂时不可用"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听到声音"
    else -> "语音输入失败，请重试"
}

@Composable
private fun TimelineHeroCard(
    nickname: String,
    totalCount: Int,
    activeCategoryLabel: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(primaryPink.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoStories, null, Modifier.size(26.dp), tint = primaryPink)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "共 $totalCount 条记录 · 当前查看 $activeCategoryLabel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineFilterChip(
    label: String,
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(
            0.5.dp,
            if (selected) accent.copy(alpha = 0.34f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(14.dp), tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TimelineMonthHeader(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.width(10.dp))
        Surface(
            shape = RoundedCornerShape(50),
            color = primaryPink.copy(alpha = 0.12f)
        ) {
            Text(
                "$count 条",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = primaryPink
            )
        }
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 0.6.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)
        )
    }
}

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

    val scopedTimeline = remember(timeline, babyInfo.id) {
        timeline.filter { belongsToBaby(it.babyId, babyInfo.id) }
    }

    val filtered = remember(scopedTimeline, activeCategory) {
        if (activeCategory == "all") scopedTimeline
        else scopedTimeline.filter { it.category == activeCategory }
    }
    val activeCategoryLabel = remember(activeCategory, allCategories) {
        if (activeCategory == "all") {
            "全部记录"
        } else {
            allCategories.find { it.id == activeCategory }?.label ?: "分类记录"
        }
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
            groups[groups.lastIndex] = groups.last().let { it.first to (it.second + r) }
        }
        groups
    }

    // FAB pulse animation
    val infiniteTransition = rememberInfiniteTransition()
    val fabScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val fabGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 0.dp)) {
            Text(
                "时间线",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                item {
                    TimelineFilterChip(
                        label = "全部",
                        icon = Icons.Default.Description,
                        accent = primaryPink,
                        selected = activeCategory == "all",
                        onClick = { activeCategory = "all" }
                    )
                }
                items(allCategories, key = { it.id }) { cat ->
                    TimelineFilterChip(
                        label = cat.label,
                        icon = catIcon(cat.id),
                        accent = Color(cat.color),
                        selected = activeCategory == cat.id,
                        onClick = { activeCategory = cat.id }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            // Header
            Row(
                Modifier.fillMaxWidth().height(0.dp).padding(end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "${babyInfo.nickname}的成长时光",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${scopedTimeline.size} 条记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(0.dp))

            // Scrollable filter chips — polished styling
            LazyRow(
                modifier = Modifier.height(0.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                item {
                    val sel = activeCategory == "all"
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (sel) primaryPink else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clickable { activeCategory = "all" }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Description, null, Modifier.size(14.dp),
                                tint = if (sel) Color.White else MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "全部",
                                fontSize = 12.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                items(allCategories, key = { it.id }) { cat ->
                    val sel = activeCategory == cat.id
                    val catColor = Color(cat.color)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (sel) catColor else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clickable { activeCategory = cat.id }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(catIcon(cat.id), null, Modifier.size(13.dp),
                                tint = if (sel) Color.White else Color(cat.color))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                cat.label,
                                fontSize = 12.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(0.dp))

            if (grouped.isEmpty()) {
                // ── Warmer empty state ────────────────────────────
                Box(
                    Modifier.fillMaxSize().padding(end = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Decorative circle behind icon
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(
                                    primaryPink.copy(alpha = 0.08f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                Modifier.size(48.dp),
                                tint = primaryPink.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "还没有记录哦~",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "点击下方按钮\n记录宝宝的每一个珍贵时刻",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    grouped.forEach { (label, records) ->
                        // ── Month group header (prominent badge style) ──
                        item(key = "header-$label") {
                            val displayLabel = records.firstOrNull()?.let { record ->
                                val date = LocalDate.parse(record.date)
                                "${date.year}年 ${date.monthValue}月"
                            } ?: label
                            TimelineMonthHeader(
                                label = displayLabel,
                                count = records.size
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().height(0.dp).padding(top = 6.dp, bottom = 8.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = primaryPink.copy(alpha = 0.1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CalendarToday, null, Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryPink
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        // Record count badge
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = primaryPink.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                "${records.size}条",
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = primaryPink
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                HorizontalDivider(
                                    Modifier.weight(2f),
                                    thickness = 0.5.dp,
                                    color = primaryPink.copy(alpha = 0.2f)
                                )
                            }
                        }

                        // ── Records with timeline connector ────────
                        itemsIndexed(records, key = { _, it -> it.id }) { index, record ->
                            val catColor = Color(
                                getCategoryConfig(
                                    record.category.takeWhile { it != '|' }.ifEmpty { record.category }
                                )?.color ?: 0xFFe5e7eb
                            )
                            val isFirst = index == 0
                            val isLast = index == records.lastIndex
                            val totalInGroup = records.size

                            Row(modifier = Modifier.fillMaxWidth()) {
                                // ── Left timeline column: dot + line ──
                                Column(
                                    modifier = Modifier.width(36.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Line above dot (only for non-first items)
                                    if (!isFirst) {
                                        Box(
                                            modifier = Modifier
                                                .width(2.5.dp)
                                                .height(12.dp)
                                                .background(
                                                    catColor.copy(alpha = 0.35f)
                                                )
                                        )
                                    } else {
                                        Spacer(Modifier.height(12.dp))
                                    }

                                    // Colored dot
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        catColor.copy(alpha = 0.8f),
                                                        catColor.copy(alpha = 0.3f)
                                                    )
                                                ),
                                                shape = CircleShape
                                            )
                                            .border(1.5.dp, catColor.copy(alpha = 0.5f), CircleShape)
                                    )

                                    // Line below dot (only for non-last items)
                                    if (!isLast) {
                                        Box(
                                            modifier = Modifier
                                                .width(2.5.dp)
                                                .weight(1f)
                                                .background(
                                                    catColor.copy(alpha = 0.35f)
                                                )
                                        )
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }

                                Spacer(Modifier.width(6.dp))

                                // ── Card with left accent border ───
                                Box(modifier = Modifier.weight(1f).padding(bottom = 12.dp)) {
                                    // Left accent stripe
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(5.dp)
                                            .align(Alignment.CenterStart)
                                            .background(
                                                catColor.copy(alpha = 0.6f),
                                                RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                                            )
                                    )
                                    Card(
                                        modifier = Modifier
                                            .animateItem()
                                            .fillMaxWidth()
                                            .padding(start = 5.dp),
                                        shape = RoundedCornerShape(26.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                        border = BorderStroke(0.5.dp, catColor.copy(alpha = 0.18f)),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                                        )
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            // Top row: category badge + date + menu
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = catColor.copy(alpha = 0.12f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(
                                                            horizontal = 8.dp,
                                                            vertical = 3.dp
                                                        ),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            catIcon(
                                                                record.category.takeWhile { it != '|' }
                                                                    .ifEmpty { record.category }
                                                            ),
                                                            null,
                                                            Modifier.size(12.dp),
                                                            tint = catColor
                                                        )
                                                        Spacer(Modifier.width(3.dp))
                                                        Text(
                                                            getCategoryConfig(
                                                                record.category.takeWhile { it != '|' }
                                                                    .ifEmpty { record.category }
                                                            )?.label ?: record.category,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Medium,
                                                            color = catColor
                                                        )
                                                    }
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        record.date,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            .copy(alpha = 0.6f)
                                                    )
                                                    Spacer(Modifier.width(2.dp))
                                                    var menuExpanded by remember { mutableStateOf(false) }
                                                    Box {
                                                        IconButton(
                                                            onClick = { menuExpanded = true },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.MoreVert,
                                                                contentDescription = null,
                                                                Modifier.size(16.dp),
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        DropdownMenu(
                                                            expanded = menuExpanded,
                                                            onDismissRequest = { menuExpanded = false }
                                                        ) {
                                                            DropdownMenuItem(
                                                                text = { Text("编辑") },
                                                                onClick = {
                                                                    menuExpanded = false
                                                                    editingRecord = record
                                                                    showAddDialog = true
                                                                },
                                                                leadingIcon = {
                                                                    Icon(
                                                                        Icons.Default.Edit,
                                                                        contentDescription = null,
                                                                        Modifier.size(16.dp)
                                                                    )
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = {
                                                                    Text(
                                                                        "删除",
                                                                        color = Color(0xFFEF5350)
                                                                    )
                                                                },
                                                                onClick = {
                                                                    menuExpanded = false
                                                                    DataManager.deleteRecord(record.id)
                                                                },
                                                                leadingIcon = {
                                                                    Icon(
                                                                        Icons.Default.Delete,
                                                                        contentDescription = null,
                                                                        Modifier.size(16.dp),
                                                                        tint = Color(0xFFEF5350)
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.height(8.dp))

                                            // Title
                                            Text(
                                                record.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )

                                            // Description
                                            if (record.description.isNotEmpty()) {
                                                Spacer(Modifier.height(5.dp))
                                                Text(
                                                    record.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            // ── Better photo grid (2x2 for 3-4 photos) ──
                                            if (record.photos.isNotEmpty()) {
                                                Spacer(Modifier.height(10.dp))
                                                val photos = record.photos
                                                if (photos.size == 1) {
                                                    // Single photo — wider
                                                    Surface(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(180.dp),
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = MaterialTheme.colorScheme.surfaceContainer
                                                    ) {
                                                        AuthenticatedAsyncImage(
                                                            model = photos[0],
                                                            contentDescription = null,
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    }
                                                } else {
                                                    // 2-column grid
                                                    val displayCount = minOf(photos.size, 4)
                                                    val rows = (displayCount + 1) / 2
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        for (r in 0 until rows) {
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(
                                                                    4.dp
                                                                )
                                                            ) {
                                                                val idx1 = r * 2
                                                                Surface(
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .aspectRatio(1f),
                                                                    shape = RoundedCornerShape(10.dp),
                                                                    color = MaterialTheme.colorScheme.surfaceContainer
                                                                ) {
                                                                    AuthenticatedAsyncImage(
                                                                        model = photos[idx1],
                                                                        contentDescription = null,
                                                                        modifier = Modifier.fillMaxSize(),
                                                                        contentScale = ContentScale.Crop
                                                                    )
                                                                }
                                                                if (idx1 + 1 < displayCount) {
                                                                    val idx2 = idx1 + 1
                                                                    val showOverflow =
                                                                        r == rows - 1 && idx2 == displayCount - 1 && photos.size > 4
                                                                    Surface(
                                                                        modifier = Modifier
                                                                            .weight(1f)
                                                                            .aspectRatio(1f),
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        color = MaterialTheme.colorScheme.surfaceContainer
                                                                    ) {
                                                                        Box {
                                                                            AuthenticatedAsyncImage(
                                                                                model = photos[idx2],
                                                                                contentDescription = null,
                                                                                modifier = Modifier.fillMaxSize(),
                                                                                contentScale = ContentScale.Crop
                                                                            )
                                                                            if (showOverflow) {
                                                                                Box(
                                                                                    modifier = Modifier
                                                                                        .fillMaxSize()
                                                                                        .background(
                                                                                            Color.Black.copy(
                                                                                                alpha = 0.55f
                                                                                            )
                                                                                        ),
                                                                                    contentAlignment = Alignment.Center
                                                                                ) {
                                                                                    Text(
                                                                                        "+${photos.size - 4}",
                                                                                        fontSize = 20.sp,
                                                                                        fontWeight = FontWeight.Bold,
                                                                                        color = Color.White
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                } else if (photos.size > 4 && r == rows - 1) {
                                                                    // Overflow badge in last cell
                                                                    Surface(
                                                                        modifier = Modifier
                                                                            .weight(1f)
                                                                            .aspectRatio(1f),
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        color = primaryPink.copy(alpha = 0.12f)
                                                                    ) {
                                                                        Box(
                                                                            contentAlignment = Alignment.Center
                                                                        ) {
                                                                            Text(
                                                                                "+${photos.size - 3}",
                                                                                fontSize = 18.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                color = primaryPink
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // ── Video previews ─────
                                            if (record.videos.isNotEmpty()) {
                                                Spacer(Modifier.height(10.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    record.videos.take(4).forEach { path ->
                                                        Surface(
                                                            modifier = Modifier
                                                                .size(64.dp)
                                                                .clickable {
                                                                    playVideoPath = path
                                                                },
                                                            shape = RoundedCornerShape(10.dp),
                                                            color = MaterialTheme.colorScheme.surfaceContainer
                                                        ) {
                                                            Box {
                                                                VideoThumbnail(
                                                                    filePath = path,
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                                // Play icon overlay
                                                                Surface(
                                                                    modifier = Modifier
                                                                        .align(Alignment.Center)
                                                                        .size(26.dp),
                                                                    shape = CircleShape,
                                                                    color = Color.Black.copy(alpha = 0.5f)
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.PlayArrow,
                                                                        contentDescription = null,
                                                                        Modifier.size(15.dp)
                                                                            .align(Alignment.Center),
                                                                        tint = Color.White
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // ── Tags ───────────────
                                            if (record.tags.isNotEmpty()) {
                                                Spacer(Modifier.height(8.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    record.tags.take(5).forEach { tag ->
                                                        Surface(
                                                            shape = RoundedCornerShape(10.dp),
                                                            color = catColor.copy(alpha = 0.08f)
                                                        ) {
                                                            Text(
                                                                " $tag ",
                                                                color = catColor,
                                                                fontSize = 11.sp,
                                                                modifier = Modifier.padding(
                                                                    horizontal = 7.dp,
                                                                    vertical = 2.dp
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom spacer for FAB clearance
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }

        // ── FAB with pulse animation ──────────────────────────────────
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Box {
                // Soft halo behind FAB
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(52.dp)
                        .scale(1f + (fabScale - 1f) * 0.35f)
                        .background(primaryPink.copy(alpha = fabGlow * 0.08f), CircleShape)
                )
                ExtendedFloatingActionButton(
                    onClick = {
                        if (babyInfo.id.isEmpty() || babyInfo.name.isEmpty()) {
                            Toast.makeText(
                                context,
                                "请先在首页添加宝宝信息",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@ExtendedFloatingActionButton
                        }
                        editingRecord = null
                        showAddDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(22.dp),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 1.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加记录")
                    Spacer(Modifier.width(6.dp))
                    Text("添加记录", fontWeight = FontWeight.SemiBold)
                }
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

// ──────────────────────────────────────────────────────────────────────
// Add / Edit Record Dialog — polished version
// ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddRecordDialog(
    initialData: TimelineRecord?,
    onDismiss: () -> Unit,
    onSave: (TimelineRecord) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cal = Calendar.getInstance()

    var date by remember {
        mutableStateOf(
            initialData?.date ?: "%04d-%02d-%02d".format(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
        )
    }
    var timeStr by remember {
        mutableStateOf(
            "%02d:%02d".format(
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE)
            )
        )
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

    var showVoiceSheet by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var voiceDraft by remember { mutableStateOf("") }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var voiceLevel by remember { mutableStateOf(0.18f) }

    val speechRecognizer = remember(context) {
        runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
    }

    fun startListening() {
        if (speechRecognizer == null) {
            showVoiceSheet = true
            isListening = false
            voiceError = "当前设备没有可用的语音识别服务"
            return
        }

        showVoiceSheet = true
        isListening = true
        voiceDraft = ""
        voiceError = null
        voiceLevel = 0.18f

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        runCatching {
            speechRecognizer.cancel()
            speechRecognizer.startListening(intent)
        }.onFailure {
            isListening = false
            voiceError = "语音服务启动失败，请重试"
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }

    fun closeVoiceSheet() {
        speechRecognizer?.cancel()
        showVoiceSheet = false
        isListening = false
        voiceLevel = 0.18f
    }

    fun commitVoiceDraft() {
        if (voiceDraft.isNotBlank()) {
            description = appendRecognizedText(description, voiceDraft)
            closeVoiceSheet()
            voiceDraft = ""
            voiceError = null
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            showVoiceSheet = true
            isListening = false
            voiceError = "允许麦克风权限后才能语音输入"
        }
    }

    fun startSpeechInput() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            showVoiceSheet = true
            voiceDraft = ""
            voiceError = "需要麦克风权限"
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                voiceError = null
            }

            override fun onBeginningOfSpeech() {
                isListening = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                voiceLevel = ((rmsdB + 2f) / 12f).coerceIn(0.12f, 1f)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                isListening = false
                voiceLevel = 0.18f
            }

            override fun onError(error: Int) {
                isListening = false
                voiceLevel = 0.18f
                if (showVoiceSheet) {
                    voiceError = speechErrorText(error)
                }
            }

            override fun onResults(results: Bundle?) {
                val recognized = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()

                isListening = false
                voiceLevel = 0.18f
                if (recognized.isNotBlank()) {
                    voiceDraft = recognized
                    voiceError = null
                } else {
                    voiceError = "没有识别到内容"
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()

                if (partial.isNotBlank()) {
                    voiceDraft = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        speechRecognizer?.setRecognitionListener(listener)
        onDispose {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
    }

    AppEditorDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) { requestClose ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // ── Header row ─────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (initialData != null) "编辑记录" else "新纪录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = requestClose) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            // ── Title section ──────────────────────────────────
            SectionHeader("标题", Icons.Default.Edit)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("如：第一次翻身") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // ── Description section ────────────────────────────
            SectionHeader("描述", Icons.Default.Description)
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text("记录下这个珍贵时刻...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(onClick = { startSpeechInput() }) {
                        Icon(Icons.Default.Mic, contentDescription = "语音输入")
                    }
                }
            )

            // ── Date & Time section ────────────────────────────
            SectionHeader("日期时间", Icons.Default.CalendarToday)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(date, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = {
                        val parts = timeStr.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 12
                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                timeStr = "%02d:%02d".format(hour, minute)
                            },
                            h, m, true
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(timeStr, fontSize = 13.sp)
                }
            }

            // ── Category section ───────────────────────────────
            SectionHeader("分类", Icons.Default.Dashboard)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                allCategories.forEach { cat ->
                    val catColor = Color(cat.color)
                    val sel = category == cat.id
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (sel) catColor else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clickable { category = cat.id }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(catIcon(cat.id), null, Modifier.size(13.dp),
                                tint = if (sel) Color.White else Color(cat.color))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                cat.label,
                                fontSize = 12.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Sub-category selector
            if (hasSubCategories) {
                Spacer(Modifier.height(-8.dp))
                Text(
                    "子分类",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
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

            // ── Photos section ─────────────────────────────────
            SectionHeader(
                "照片 (${selectedPhotos.size}/4)",
                Icons.Default.PhotoLibrary
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(selectedPhotos.size) { index ->
                    val uri = selectedPhotos[index]
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        AuthenticatedAsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = {
                                selectedPhotos = selectedPhotos.filter { it != uri }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(22.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.55f),
                                    RoundedCornerShape(11.dp)
                                )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                Modifier.size(13.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
                if (selectedPhotos.size < 4) {
                    item {
                        OutlinedButton(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier.size(76.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(0.dp),
                            border = BorderStroke(
                                1.dp,
                                primaryPink.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    Modifier.size(22.dp),
                                    tint = primaryPink
                                )
                                Text(
                                    "添加",
                                    fontSize = 9.sp,
                                    color = primaryPink.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // ── Videos section ─────────────────────────────────
            SectionHeader(
                "视频 (${selectedVideos.size}/3)",
                Icons.Default.Videocam
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(selectedVideos.size) { index ->
                    val path = selectedVideos[index]
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        VideoThumbnail(
                            filePath = path,
                            modifier = Modifier.fillMaxSize(),
                            onClick = { videoPreviewPath = path }
                        )
                        // Play overlay
                        Surface(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(28.dp),
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.45f)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                Modifier.size(16.dp).align(Alignment.Center),
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = {
                                selectedVideos = selectedVideos.filter { it != path }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(22.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.55f),
                                    RoundedCornerShape(11.dp)
                                )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                Modifier.size(13.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
                if (selectedVideos.size < 3) {
                    item {
                        OutlinedButton(
                            onClick = { videoPicker.launch("video/*") },
                            modifier = Modifier.size(76.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(0.dp),
                            border = BorderStroke(
                                1.dp,
                                primaryPink.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Videocam,
                                    contentDescription = null,
                                    Modifier.size(22.dp),
                                    tint = primaryPink
                                )
                                Text(
                                    "添加",
                                    fontSize = 9.sp,
                                    color = primaryPink.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // ── Tags section ───────────────────────────────────
            SectionHeader("标签", Icons.Default.Label)
            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = { tags = tags.filter { it != tag } },
                            label = { Text(tag, fontSize = 12.sp) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    Modifier.size(14.dp)
                                )
                            },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = primaryPink.copy(alpha = 0.12f),
                                selectedLabelColor = primaryPink
                            ),
                            border = InputChipDefaults.inputChipBorder(
                                borderColor = primaryPink.copy(alpha = 0.25f),
                                selectedBorderColor = primaryPink.copy(alpha = 0.4f),
                                enabled = true,
                                selected = true
                            )
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
                                label = { Text("+ $tag", fontSize = 11.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // ── Save button ────────────────────────────────────
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val catLabel =
                            if (subCategory.isNotEmpty() && hasSubCategories) {
                                val sub =
                                    selectedCatConfig?.children?.find { it.id == subCategory }
                                if (sub != null) "$category|${sub.name}" else category
                            } else category
                        onSave(
                            TimelineRecord(
                                id = initialData?.id
                                    ?: "record-${UUID.randomUUID().toString().take(8)}",
                                date = date,
                                title = title.trim(),
                                description = description.trim(),
                                category = catLabel,
                                tags = tags,
                                photos = selectedPhotos,
                                videos = selectedVideos
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryPink),
                shape = RoundedCornerShape(16.dp),
                enabled = title.isNotBlank()
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (initialData != null) "保存修改" else "保存记录",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            }

            AnimatedVisibility(
                visible = showVoiceSheet,
                enter = fadeIn(animationSpec = tween(140)),
                exit = fadeOut(animationSpec = tween(120)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f))
                        .clickable { closeVoiceSheet() }
                )
            }

            AnimatedVisibility(
                visible = showVoiceSheet,
                enter = fadeIn(animationSpec = tween(160)) +
                    slideInVertically(
                        animationSpec = tween(260),
                        initialOffsetY = { it / 2 }
                    ),
                exit = fadeOut(animationSpec = tween(120)) +
                    slideOutVertically(
                        animationSpec = tween(180),
                        targetOffsetY = { it / 2 }
                    ),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                VoiceInputSheet(
                    isListening = isListening,
                    voiceLevel = voiceLevel,
                    transcript = voiceDraft,
                    error = voiceError,
                    onStop = { stopListening() },
                    onRetry = { startListening() },
                    onDismiss = { closeVoiceSheet() },
                    onCommit = { commitVoiceDraft() }
                )
            }
        }
    }

    // ── Date picker ────────────────────────────────────────────────
    if (showDatePicker) {
        YueMingTimelineDatePicker(
            initialDate = LocalDate.parse(date),
            onDateSelected = { selectedDate ->
                date = "%04d-%02d-%02d".format(
                    selectedDate.year,
                    selectedDate.monthValue,
                    selectedDate.dayOfMonth
                )
            },
            onDismiss = { showDatePicker = false }
        )
    }

    // ── Video preview ──────────────────────────────────────────────
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

// ──────────────────────────────────────────────────────────────────────
// Reusable section header with icon
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun VoiceInputSheet(
    isListening: Boolean,
    voiceLevel: Float,
    transcript: String,
    error: String?,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onCommit: () -> Unit
) {
    val pulseScale by animateFloatAsState(
        targetValue = if (isListening) 1f + voiceLevel * 0.16f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "voicePulseScale"
    )
    val displayText = when {
        transcript.isNotBlank() -> transcript
        error != null -> error
        isListening -> "我在听，慢慢说"
        else -> "准备开始语音输入"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        primaryPink,
                                        primaryPink.copy(alpha = 0.35f),
                                        primaryPink.copy(alpha = 0.10f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(25.dp),
                            tint = Color.White
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (isListening) "正在听你说" else "语音输入",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(30.dp)
                        ) {
                            listOf(0.55f, 0.88f, 1f, 0.72f, 0.95f).forEach { factor ->
                                Box(
                                    modifier = Modifier
                                        .width(5.dp)
                                        .height((8f + 24f * voiceLevel * factor).dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            primaryPink.copy(
                                                alpha = if (isListening) 0.72f else 0.28f
                                            )
                                        )
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = if (error != null && transcript.isBlank()) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                Text(
                    displayText,
                    modifier = Modifier.padding(14.dp),
                    minLines = 2,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null && transcript.isBlank()) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "取消")
                }

                OutlinedButton(
                    onClick = if (isListening) onStop else onRetry,
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        if (isListening) Icons.Default.Stop else Icons.Default.Refresh,
                        contentDescription = if (isListening) "停止" else "重试",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Button(
                    onClick = onCommit,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    enabled = transcript.isNotBlank() && !isListening,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryPink),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "加入描述",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 0.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = primaryPink
        )
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ──────────────────────────────────────────────────────────────────────
// Date picker (unchanged logic)
// ──────────────────────────────────────────────────────────────────────

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
                        Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    )
                }
                onDismiss()
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
