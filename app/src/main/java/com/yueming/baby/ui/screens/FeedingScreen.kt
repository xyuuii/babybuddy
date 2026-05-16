package com.yueming.baby.ui.screens

import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.yueming.baby.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.ExperimentalFoundationApi
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedingScreen(onDismiss: () -> Unit) {
    val appBarColor = MaterialTheme.colorScheme.background
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.applyDialogSystemBars(view, appBarColor)
        }
    }

    val babyInfo by DataManager.babyInfo.collectAsState()
    val feedingRecords by DataManager.feedingRecords.collectAsState()
    val scopedFeedingRecords = remember(feedingRecords, babyInfo.id) {
        feedingRecords.filter { belongsToBaby(it.babyId, babyInfo.id) }
    }
    var selectedType by remember { mutableStateOf("formula") }
    var volume by remember { mutableStateOf("") }
    var showVolumeInput by remember { mutableStateOf(false) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }

    val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
    val todayRecords = remember(scopedFeedingRecords) {
        scopedFeedingRecords.filter { it.timestamp >= todayStart }
    }
    val todayFormulaMl = remember(todayRecords) {
        todayRecords.filter { it.type == "formula" || it.type == "water" }.sumOf { it.volumeMl }
    }
    val todayBreastCount = remember(todayRecords) { todayRecords.count { it.type == "breast" } }
    val todaySupplementCount = remember(todayRecords) { todayRecords.count { it.type == "supplement" } }

    val historyRecords = remember(scopedFeedingRecords) {
        scopedFeedingRecords.filter { it.timestamp < todayStart }
            .groupBy {
                Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            }.toSortedMap(compareByDescending { it })
    }

    val feedingTypeInfo = mapOf(
        "breast" to FeedingTypeInfo("母乳", Icons.Outlined.FavoriteBorder, Color(0xFFF8C8D8), "分钟"),
        "formula" to FeedingTypeInfo("配方奶", Icons.Default.Fastfood, Color(0xFFA5D8DD), "ml"),
        "supplement" to FeedingTypeInfo("辅食", Icons.Default.Fastfood, Color(0xFFF6BA6D), ""),
        "water" to FeedingTypeInfo("喝水", Icons.Default.Fastfood, Color(0xFFBBDEFB), "ml"),
        "snack" to FeedingTypeInfo("零食", Icons.Default.Star, Color(0xFFFDE68A), "")
    )

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Status bar background fill — extends into transparent status bar area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(appBarColor)
        )
        // Top bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = appBarColor,
            shadowElevation = 0.dp
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "关闭")
                }
                Text("喂养日志", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(48.dp))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Today summary card
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("今日喂养", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            SummaryItem("${todayRecords.size} 次", "总次数")
                            SummaryItem("${todayBreastCount} 次", "母乳")
                            SummaryItem("${todayFormulaMl} ml", "奶量")
                            SummaryItem("${todaySupplementCount} 次", "辅食")
                        }
                    }
                }
            }

            // Quick add
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("快速记录", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(12.dp))

                        LazyRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(FEEDING_TYPES.size) { index ->
                                val (type, label) = FEEDING_TYPES[index]
                                val info = feedingTypeInfo[type]!!
                                val isSelected = selectedType == type
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedType = type
                                        showVolumeInput = type in listOf("formula", "water")
                                        volume = ""
                                    },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(info.icon, null, Modifier.size(14.dp), tint = info.color)
                                            Spacer(Modifier.width(4.dp))
                                            Text(label, fontSize = 12.sp, maxLines = 1)
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = info.color.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        if (showVolumeInput) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = volume,
                                    onValueChange = { v -> volume = v.filter { it.isDigit() } },
                                    placeholder = { Text("输入${feedingTypeInfo[selectedType]?.unit ?: ""}量") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = MaterialTheme.typography.bodyMedium
                                )
                                Button(
                                    onClick = {
                                        val vol = volume.toIntOrNull() ?: 0
                                        if (vol > 0 || selectedType !in listOf("formula", "water")) {
                                            DataManager.addFeedingRecord(FeedingRecord(
                                                id = "feed-${UUID.randomUUID().toString().take(8)}",
                                                timestamp = System.currentTimeMillis(),
                                                type = selectedType,
                                                volumeMl = vol
                                            ))
                                            volume = ""
                                        }
                                    },
                                    enabled = volume.toIntOrNull()?.let { it > 0 } ?: (selectedType !in listOf("formula", "water")),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A))
                                ) {
                                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("记录")
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    DataManager.addFeedingRecord(FeedingRecord(
                                        id = "feed-${UUID.randomUUID().toString().take(8)}",
                                        timestamp = System.currentTimeMillis(),
                                        type = selectedType,
                                        durationMin = if (selectedType == "breast") 10 else 0
                                    ))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A))
                            ) {
                                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("记录${feedingTypeInfo[selectedType]?.label ?: ""}")
                            }
                        }
                    }
                }
            }

            // Today's records
            if (todayRecords.isNotEmpty()) {
                item {
                    Text("今天", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp))
                }
                items(todayRecords, key = { it.id }) { record ->
                    FeedingRecordItem(
                        record = record,
                        info = feedingTypeInfo[record.type],
                        onDelete = { deleteConfirmId = record.id }
                    )
                }
            }

            // History grouped by date
            historyRecords.forEach { (date, records) ->
                item {
                    Text(
                        formatDate(date),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(records, key = { it.id }) { record ->
                    FeedingRecordItem(
                        record = record,
                        info = feedingTypeInfo[record.type],
                        onDelete = { deleteConfirmId = record.id }
                    )
                }
            }

            if (scopedFeedingRecords.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Fastfood, null, Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            Spacer(Modifier.height(12.dp))
                            Text("还没有喂养记录",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("点击上方按钮快速记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    // Delete confirm
    deleteConfirmId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("删除记录") },
            text = { Text("确定要删除这条喂养记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    DataManager.deleteFeedingRecord(id)
                    deleteConfirmId = null
                }) { Text("删除", color = Color(0xFFEF5350)) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmId = null }) { Text("取消") } }
        )
    }
}

private data class FeedingTypeInfo(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val unit: String
)

private fun Window.applyDialogSystemBars(view: android.view.View, statusBarColor: Color) {
    addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    WindowCompat.setDecorFitsSystemWindows(this, false)
    setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
    setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    @Suppress("DEPRECATION")
    this.statusBarColor = statusBarColor.toArgb()
    WindowCompat.getInsetsController(this, view).isAppearanceLightStatusBars =
        statusBarColor.luminance() > 0.5f
}

@Composable
private fun SummaryItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFEC407A))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedingRecordItem(
    record: FeedingRecord,
    info: FeedingTypeInfo?,
    onDelete: () -> Unit
) {
    val timeStr = remember(record.timestamp) {
        Instant.ofEpochMilli(record.timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(info?.icon ?: Icons.Outlined.Fastfood, null, Modifier.size(22.dp),
                tint = info?.color ?: MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(info?.label ?: record.type, fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodySmall)
                val detail = buildString {
                    if (record.volumeMl > 0) append("${record.volumeMl} ml")
                    if (record.durationMin > 0) append("${record.durationMin} 分钟")
                    if (record.notes.isNotBlank()) append(record.notes)
                }
                if (detail.isNotEmpty()) {
                    Text(detail, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(timeStr, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        }
    }
}

private fun formatDate(date: LocalDate): String {
    val today = LocalDate.now()
    return when {
        date == today -> "今天"
        date == today.minusDays(1) -> "昨天"
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("M月d日"))
        else -> date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
    }
}
