package com.yueming.baby.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yueming.baby.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen() {
    val babyInfo by DataManager.babyInfo.collectAsState()
    val timeline by DataManager.timeline.collectAsState()
    val allCategories = DataManager.allCategories
    var activeCategory by remember { mutableStateOf("all") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<TimelineRecord?>(null) }

    val filtered = remember(timeline, activeCategory) {
        if (activeCategory == "all") timeline
        else timeline.filter { it.category == activeCategory }
    }

    val sorted = remember(filtered) {
        filtered.sortedByDescending { it.date }
    }

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

            // Filter chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = activeCategory == "all",
                    onClick = { activeCategory = "all" },
                    label = { Text("全部", fontSize = 12.sp) }
                )
                allCategories.forEach { cat ->
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
                        Text("该分类下暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("点击下方按钮添加第一条记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                        items(records) { record ->
                            val catColor = Color(getCategoryConfig(record.category)?.color ?: 0xFFe5e7eb)
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                Row(Modifier.padding(12.dp)) {
                                    Box(
                                        Modifier.width(4.dp).fillMaxHeight()
                                            .clip(RoundedCornerShape(2.dp)).background(catColor)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(record.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f))
                                            Text(record.date.takeLast(5),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (record.description.isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(record.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (record.tags.isNotEmpty()) {
                                            Spacer(Modifier.height(6.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                record.tags.take(5).forEach { tag ->
                                                    Box(
                                                        Modifier.clip(RoundedCornerShape(8.dp))
                                                            .background(catColor.copy(alpha = 0.15f))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(tag, fontSize = 10.sp,
                                                            color = catColor)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = {
                editingRecord = null
                showAddDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFFEC407A)
        ) {
            Icon(Icons.Default.Add, contentDescription = "添加记录", tint = Color.White)
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
                        DataManager.updateRecord(record.id) { record }
                    } else {
                        DataManager.addRecord(record)
                    }
                    showAddDialog = false
                    editingRecord = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecordDialog(
    initialData: TimelineRecord?,
    onDismiss: () -> Unit,
    onSave: (TimelineRecord) -> Unit
) {
    val context = LocalContext.current
    val now = LocalDate.now()
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    var date by remember { mutableStateOf(initialData?.date ?: now.format(fmt)) }
    var title by remember { mutableStateOf(initialData?.title ?: "") }
    var description by remember { mutableStateOf(initialData?.description ?: "") }
    var category by remember { mutableStateOf(initialData?.category ?: "milestone") }
    var tags by remember { mutableStateOf(initialData?.tags ?: emptyList()) }

    val allCategories = DataManager.allCategories
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialData != null) "编辑记录" else "添加新记录",
                fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date picker
                OutlinedButton(
                    onClick = {
                        val d = LocalDate.parse(date)
                        DatePickerDialog(
                            context, { _, y, m, day ->
                                date = "%04d-%02d-%02d".format(y, m + 1, day)
                            }, d.year, d.monthValue - 1, d.dayOfMonth
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(date)
                }

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题（如：第一次翻身）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("记录下这个珍贵时刻...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                // Category
                Text("分类", style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    allCategories.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { cat ->
                                val catColor = Color(cat.color)
                                FilterChip(
                                    selected = category == cat.id,
                                    onClick = { category = cat.id },
                                    label = { Text(cat.label, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = catColor.copy(alpha = 0.3f),
                                        selectedLabelColor = catColor
                                    )
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                // Tags
                if (tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { tags = tags.filter { it != tag } },
                                label = { Text(tag, fontSize = 11.sp) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                }
                if (defaultTags.isNotEmpty()) {
                    val suggested = defaultTags.filter { it !in tags }.take(5)
                    if (suggested.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            suggested.forEach { tag ->
                                AssistChip(
                                    onClick = { tags = tags + tag },
                                    label = { Text("+$tag", fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(TimelineRecord(
                            id = initialData?.id ?: "record-${UUID.randomUUID().toString().take(8)}",
                            date = date,
                            title = title.trim(),
                            description = description.trim(),
                            category = category,
                            tags = tags
                        ))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A))
            ) {
                Text(if (initialData != null) "保存修改" else "添加记录",
                    color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
