package com.yueming.baby.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yueming.baby.data.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val babyInfo by DataManager.babyInfo.collectAsState()
    val timeline by DataManager.timeline.collectAsState()
    val photos by DataManager.photos.collectAsState()
    val aiConfig by DataManager.aiConfig.collectAsState()
    val customCategories by DataManager.customCategories.collectAsState()
    val allCategories = DataManager.allCategories

    var babyForm by remember { mutableStateOf(babyInfo) }
    var savedBaby by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var newCatLabel by remember { mutableStateOf("") }
    var newCatColor by remember { mutableStateOf(PRESET_COLORS.first()) }
    var aiForm by remember { mutableStateOf(aiConfig) }
    var showModelSelector by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // Baby Info
        item {
            Card(shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, Modifier.size(18.dp), tint = Color(0xFFEC407A))
                        Spacer(Modifier.width(8.dp))
                        Text("宝宝信息", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))

                    // Avatar
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFF8C8D8)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (babyForm.avatar != null) {
                                AsyncImage(model = babyForm.avatar, contentDescription = null,
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Default.Person, null, Modifier.size(36.dp),
                                    tint = Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = babyForm.name, onValueChange = { babyForm = babyForm.copy(name = it) },
                        label = { Text("宝宝名字") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = babyForm.nickname, onValueChange = { babyForm = babyForm.copy(nickname = it) },
                        label = { Text("昵称") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val d = LocalDate.parse(babyForm.birthDate)
                            DatePickerDialog(context, { _, y, m, day ->
                                babyForm = babyForm.copy(birthDate = "%04d-%02d-%02d".format(y, m + 1, day))
                            }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("出生日期: ${babyForm.birthDate}")
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("girl" to "女宝宝", "boy" to "男宝宝").forEach { (g, label) ->
                            FilterChip(
                                selected = babyForm.gender == g,
                                onClick = { babyForm = babyForm.copy(gender = g) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (g == "girl") Color(0xFFF8C8D8)
                                    else Color(0xFFBBDEFB)
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (babyForm.name.isNotBlank() && babyForm.nickname.isNotBlank()) {
                                DataManager.updateBabyInfo(babyForm)
                                savedBaby = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = if (savedBaby) Color(0xFF4CAF50) else Color(0xFFEC407A))
                    ) {
                        Text(if (savedBaby) "已保存" else "保存宝宝信息", color = Color.White)
                    }
                }
            }
        }

        // AI Config
        item {
            Card(shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SmartToy, null, Modifier.size(18.dp), tint = Color(0xFF7C4DFF))
                        Spacer(Modifier.width(8.dp))
                        Text("AI 助手配置", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = aiForm.apiBaseUrl, onValueChange = { aiForm = aiForm.copy(apiBaseUrl = it) },
                        label = { Text("API Base URL") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = aiForm.apiKey, onValueChange = { aiForm = aiForm.copy(apiKey = it) },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    // Model selector
                    Text("模型选择", style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    AI_MODELS.take(6).forEach { model ->
                        Row(Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                aiForm = aiForm.copy(model = model.id)
                                showModelSelector = false
                            }
                            .background(if (aiForm.model == model.id)
                                Color(0xFFEC407A).copy(alpha = 0.1f)
                            else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(model.name, style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium)
                                Text(model.provider, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (aiForm.model == model.id) {
                                Icon(Icons.Default.Check, null, Modifier.size(16.dp),
                                    tint = Color(0xFFEC407A))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { DataManager.updateAIConfig(aiForm) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                    ) {
                        Text("保存配置", color = Color.White)
                    }
                }
            }
        }

        // Category Management
        item {
            Card(shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = Color(0xFFF6BA6D))
                        Spacer(Modifier.width(8.dp))
                        Text("分类管理", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    allCategories.forEach { cat ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp))
                                    .background(Color(cat.color)))
                                Spacer(Modifier.width(8.dp))
                                Text(cat.label, style = MaterialTheme.typography.bodySmall)
                            }
                            if (cat.isDefault) {
                                Icon(Icons.Default.Lock, null, Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            } else {
                                IconButton(
                                    onClick = { DataManager.removeCategory(cat.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, null, Modifier.size(14.dp),
                                        tint = Color(0xFFEF5350))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (showAddCategory) {
                        OutlinedTextField(
                            value = newCatLabel, onValueChange = { newCatLabel = it },
                            label = { Text("分类名称") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PRESET_COLORS.forEach { c ->
                                Box(
                                    Modifier.size(28.dp).clip(RoundedCornerShape(14.dp))
                                        .background(Color(c))
                                        .clickable { newCatColor = c }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (newCatLabel.isNotBlank()) {
                                        DataManager.addCategory(CategoryConfig(
                                            id = "custom-${System.currentTimeMillis()}",
                                            label = newCatLabel.trim(),
                                            icon = "Tag",
                                            color = newCatColor,
                                            isDefault = false
                                        ))
                                        newCatLabel = ""
                                        showAddCategory = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A))
                            ) {
                                Text("添加", color = Color.White)
                            }
                            OutlinedButton(
                                onClick = { showAddCategory = false },
                                modifier = Modifier.weight(1f)
                            ) { Text("取消") }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showAddCategory = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("添加自定义分类")
                        }
                    }
                }
            }
        }

        // Data Management
        item {
            Card(shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, null, Modifier.size(18.dp), tint = Color(0xFF66BB6A))
                        Spacer(Modifier.width(8.dp))
                        Text("数据管理", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                    ) {
                        Row(Modifier.padding(12.dp)) {
                            Icon(Icons.Default.Info, null, Modifier.size(14.dp),
                                tint = Color(0xFF1976D2))
                            Spacer(Modifier.width(8.dp))
                            Text("数据存储在本地。建议定期导出备份。",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1976D2))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("时间线记录", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${timeline.size} 条", fontWeight = FontWeight.Medium)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("照片", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${photos.size} 张", fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { /* Export JSON */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, null, Modifier.size(14.dp))
                            Text("导出JSON", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { /* Import JSON */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, null, Modifier.size(14.dp))
                            Text("导入JSON", fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { /* Reset data */ },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                        Text("清除所有数据")
                    }
                }
            }
        }

        // About
        item {
            Card(shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("关于", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("悦萌 YueMing v2.0", style = MaterialTheme.typography.bodySmall)
                    Text("宝宝成长记录应用", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Jetpack Compose + Material3", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

private val PRESET_COLORS = listOf(0xFFf8c8d8, 0xFFf6ba6d, 0xFFa5d8dd, 0xFFc4b5fd, 0xFF86efac, 0xFFfde68a)
