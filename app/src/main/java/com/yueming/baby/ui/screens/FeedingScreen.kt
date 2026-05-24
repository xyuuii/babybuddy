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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.yueming.baby.data.*
import com.yueming.baby.ui.components.BabyGlassAlertDialog
import com.yueming.baby.ui.components.BabyDangerButton
import com.yueming.baby.ui.components.BabyGlassChip
import com.yueming.baby.ui.components.BabyGlassIconButton
import com.yueming.baby.ui.components.BabyGlassRole
import com.yueming.baby.ui.components.BabyGlassSurface
import com.yueming.baby.ui.components.BabyGlassTextField
import com.yueming.baby.ui.components.BabyPrimaryButton
import com.yueming.baby.ui.components.BabySecondaryButton
import com.yueming.baby.ui.components.LocalBabyBottomBarClearance
import com.yueming.baby.ui.components.babyPageBackground
import com.yueming.baby.ui.motion.motionListItem
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
    var quickNumber by remember { mutableStateOf(feedingQuickEntryDefaultValue("formula")) }
    var quickNotes by remember { mutableStateOf("") }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }
    val bottomBarClearance = LocalBabyBottomBarClearance.current

    val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
    val todayRecords = remember(scopedFeedingRecords) {
        scopedFeedingRecords.filter { it.timestamp >= todayStart }
    }
    val todayFormulaMl = remember(todayRecords) {
        todayRecords.filter { it.type == "formula" || it.type == "water" }.sumOf { it.volumeMl }
    }
    val todayBreastCount = remember(todayRecords) { todayRecords.count { it.type == "breast" } }
    val todayBreastMinutes = remember(todayRecords) {
        todayRecords.filter { it.type == "breast" }.sumOf { it.durationMin }
    }
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

    Column(modifier = Modifier.fillMaxSize().babyPageBackground()) {
        // Status bar background fill — extends into transparent status bar area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(appBarColor)
        )
        // Top bar
        BabyGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            role = BabyGlassRole.NavigationChrome
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BabyGlassIconButton(
                    icon = Icons.Default.Close,
                    onClick = onDismiss,
                    contentDescription = "关闭"
                )
                Text("喂养日志", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(48.dp))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = bottomBarClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Today summary card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .motionListItem(index = 0),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("今日喂养", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            SummaryItem("${todayRecords.size} 次", "总次数")
                            SummaryItem("${todayBreastCount}次 ${todayBreastMinutes}分", "母乳")
                            SummaryItem("${todayFormulaMl} ml", "奶量")
                            SummaryItem("${todaySupplementCount} 次", "辅食")
                        }
                    }
                }
            }

            // Quick add
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .motionListItem(index = 1),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
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
                                BabyGlassChip(
                                    label = label,
                                    icon = info.icon,
                                    accent = info.color,
                                    selected = isSelected,
                                    onClick = {
                                        selectedType = type
                                        quickNumber = feedingQuickEntryDefaultValue(type)
                                        quickNotes = ""
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        val selectedInfo = feedingTypeInfo[selectedType]!!
                        val needsNumber = feedingQuickEntryNeedsNumber(selectedType)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(),
                            shape = RoundedCornerShape(22.dp),
                            color = selectedInfo.color.copy(alpha = 0.12f),
                            border = BorderStroke(0.5.dp, selectedInfo.color.copy(alpha = 0.28f))
                        ) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(selectedInfo.color.copy(alpha = 0.22f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                selectedInfo.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = selectedInfo.color
                                            )
                                        }
                                        Column {
                                            Text(
                                                selectedInfo.label,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                if (needsNumber) {
                                                    "设置${feedingQuickEntryUnit(selectedType)}后记录"
                                                } else {
                                                    "可直接记录，也可以补充备注"
                                                },
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (needsNumber) {
                                    val unit = feedingQuickEntryUnit(selectedType)
                                    BabyGlassTextField(
                                        value = quickNumber,
                                        onValueChange = { quickNumber = sanitizeFeedingQuickEntryNumber(it) },
                                        label = if (selectedType == "breast") "时长" else "数量",
                                        placeholder = "输入$unit",
                                        trailingIcon = { Text(unit, fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    )

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        feedingQuickEntryPresets(selectedType).forEach { preset ->
                                            BabyGlassChip(
                                                label = "$preset $unit",
                                                selected = quickNumber == preset.toString(),
                                                onClick = { quickNumber = preset.toString() },
                                                accent = selectedInfo.color
                                            )
                                        }
                                    }
                                } else {
                                    BabyGlassTextField(
                                        value = quickNotes,
                                        onValueChange = { quickNotes = it },
                                        placeholder = "备注，例如：米粉两勺、香蕉半根",
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                    )
                                }

                                BabyPrimaryButton(
                                    text = "记录${selectedInfo.label}",
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    onClick = {
                                        DataManager.addFeedingRecord(
                                            createQuickFeedingRecord(
                                                type = selectedType,
                                                numberValue = quickNumber,
                                                notes = quickNotes,
                                                id = "feed-${UUID.randomUUID().toString().take(8)}"
                                            )
                                        )
                                        quickNotes = ""
                                    },
                                    enabled = canSaveFeedingQuickEntry(selectedType, quickNumber),
                                    leadingIcon = Icons.Default.Add
                                )
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
                        onDelete = { deleteConfirmId = record.id },
                        modifier = Modifier.animateItem()
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
                        onDelete = { deleteConfirmId = record.id },
                        modifier = Modifier.animateItem()
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
        BabyGlassAlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("删除记录") },
            text = { Text("确定要删除这条喂养记录吗？") },
            confirmButton = {
                BabyDangerButton(
                    text = "删除",
                    onClick = {
                        DataManager.deleteFeedingRecord(id)
                        deleteConfirmId = null
                    }
                )
            },
            dismissButton = { BabySecondaryButton(text = "取消", onClick = { deleteConfirmId = null }) }
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
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeStr = remember(record.timestamp) {
        Instant.ofEpochMilli(record.timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
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
                val detail = feedingRecordDetail(record)
                if (detail.isNotEmpty()) {
                    Text(detail, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
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
