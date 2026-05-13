package com.yueming.baby.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yueming.baby.data.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val babyInfo by DataManager.babyInfo.collectAsState()
    val babies by DataManager.babies.collectAsState()
    val timeline by DataManager.timeline.collectAsState()
    val photos by DataManager.photos.collectAsState()
    val aiConfig by DataManager.aiConfig.collectAsState()
    val customCategories by DataManager.customCategories.collectAsState()
    val themeMode by DataManager.themeMode.collectAsState()
    val webDavConfig by DataManager.webDavConfig.collectAsState()
    val isBackingUp by DataManager.isBackingUp.collectAsState()
    val allCategories = DataManager.allCategories

    var showBabySheet by remember { mutableStateOf(false) }
    var showAISheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showAddBabySheet by remember { mutableStateOf(false) }
    var showWebDavSheet by remember { mutableStateOf(false) }
    var showRestoreSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // ---- Baby Info Group ----
        item {
            SettingsGroupCard(
                icon = Icons.Default.Person,
                iconTint = Color(0xFFEC407A),
                title = "宝宝信息",
                subtitle = "${babyInfo.nickname} · ${DataManager.getAgeInMonths(babyInfo.birthDate)}个月",
                onClick = { showBabySheet = true }
            )
        }

        // ---- AI Config Group ----
        item {
            SettingsGroupCard(
                icon = Icons.Default.SmartToy,
                iconTint = Color(0xFF7C4DFF),
                title = "AI 助手配置",
                subtitle = if (aiConfig.apiKey.isNotEmpty()) "已配置 · ${aiConfig.model}" else "点击配置",
                onClick = { showAISheet = true }
            )
        }

        // ---- Backup & Sync Group ----
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cloud, null, Modifier.size(20.dp), tint = Color(0xFF66BB6A))
                        Spacer(Modifier.width(10.dp))
                        Text("备份与同步", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (webDavConfig != null) "已连接" else "未配置",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (webDavConfig != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (webDavConfig != null) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Dns, null, Modifier.size(16.dp),
                                    tint = if (webDavConfig != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text("WebDAV",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { showWebDavSheet = true }) {
                                    Text(if (webDavConfig != null) "修改" else "配置", fontSize = 12.sp)
                                }
                            }
                            if (webDavConfig != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(webDavConfig!!.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                DataManager.uploadBackup(
                                    onProgress = { msg ->
                                        scope.launch {
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onComplete = { result ->
                                        result.onSuccess {
                                            Toast.makeText(context, "备份成功！", Toast.LENGTH_SHORT).show()
                                        }.onFailure {
                                            Toast.makeText(context, "备份失败: ${it.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = webDavConfig != null && !isBackingUp,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66BB6A)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (isBackingUp) {
                                CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Upload, null, Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("立即备份", fontSize = 13.sp, color = Color.White)
                        }
                        OutlinedButton(
                            onClick = { showRestoreSheet = true },
                            modifier = Modifier.weight(1f),
                            enabled = webDavConfig != null,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Download, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("从备份恢复", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // ---- Theme Group ----
        item {
            SettingsGroupCard(
                icon = Icons.Default.Palette,
                iconTint = Color(0xFFAB47BC),
                title = "主题",
                subtitle = when (themeMode) {
                    ThemeMode.LIGHT -> "亮色"
                    ThemeMode.DARK -> "暗色"
                    ThemeMode.SYSTEM -> "跟随系统"
                },
                onClick = { showThemeSheet = true }
            )
        }

        // ---- Category Management Group ----
        item {
            SettingsGroupCard(
                icon = Icons.Default.Category,
                iconTint = Color(0xFFF6BA6D),
                title = "分类管理",
                subtitle = "${allCategories.size} 个分类",
                onClick = { showCategorySheet = true }
            )
        }

        // ---- Clear Data ----
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    DataManager.resetAllData {
                                        Toast.makeText(context, "数据已清除", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, null, Modifier.size(20.dp), tint = Color(0xFFEF5350))
                            Spacer(Modifier.width(10.dp))
                            Text("清除所有数据",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFEF5350),
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // ---- About ----
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("关于", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("悦萌 YueMing v2.0", style = MaterialTheme.typography.bodySmall)
                    Text("宝宝成长记录应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Jetpack Compose + Material3 + Room",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    // ---- Modal Bottom Sheets ----

    // Baby Info Sheet
    if (showBabySheet) {
        BabyInfoSheet(
            babyInfo = babyInfo,
            babies = babies,
            onDismiss = { showBabySheet = false },
            onSave = { info ->
                DataManager.updateBabyInfo(info)
                showBabySheet = false
            },
            onAddBaby = { showAddBabySheet = true },
            onDelete = { babyId ->
                DataManager.deleteBaby(babyId) {
                    showBabySheet = false
                }
            }
        )
    }

    // Add Baby Sheet
    if (showAddBabySheet) {
        AddBabySheet(
            onDismiss = { showAddBabySheet = false },
            onAdd = { info ->
                DataManager.addBaby(info)
                showAddBabySheet = false
            }
        )
    }

    // AI Config Sheet
    if (showAISheet) {
        val profiles by DataManager.aiProfiles.collectAsState()
        AIConfigSheet(
            profiles = profiles,
            onDismiss = { showAISheet = false },
            onSaveProfile = { profile ->
                DataManager.updateAIProfile(profile)
            },
            onAddProfile = { profile ->
                DataManager.addAIProfile(profile)
            },
            onDeleteProfile = { id ->
                DataManager.deleteAIProfile(id)
            },
            onSetActive = { id ->
                DataManager.setActiveAIProfile(id)
            },
            onTestConnection = { profile, callback ->
                DataManager.testAIProfileConnection(profile, callback)
            }
        )
    }

    // Theme Sheet
    if (showThemeSheet) {
        ThemeSheet(
            currentMode = themeMode,
            onDismiss = { showThemeSheet = false },
            onSelect = { mode ->
                DataManager.setThemeMode(mode)
                showThemeSheet = false
            }
        )
    }

    // Category Sheet
    if (showCategorySheet) {
        CategorySheet(
            categories = allCategories,
            customCategories = customCategories,
            onDismiss = { showCategorySheet = false },
            onAdd = { cat -> DataManager.addCategory(cat) },
            onRemove = { id -> DataManager.removeCategory(id) }
        )
    }

    // WebDAV Config Sheet
    if (showWebDavSheet) {
        WebDavConfigSheet(
            currentConfig = webDavConfig,
            onDismiss = { showWebDavSheet = false },
            onSave = { config -> DataManager.saveWebDavConfig(config) },
            onClear = { DataManager.clearWebDavConfig() }
        )
    }

    // Restore Sheet
    if (showRestoreSheet) {
        RestoreSheet(
            onDismiss = { showRestoreSheet = false },
            onRestore = { filename ->
                DataManager.downloadBackup(filename,
                    onProgress = { msg ->
                        scope.launch {
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onComplete = { result ->
                        result.onSuccess { bytes ->
                            DataManager.restoreFromBackup(bytes) { success ->
                                Toast.makeText(context, if (success) "恢复成功！" else "恢复失败", Toast.LENGTH_SHORT).show()
                                showRestoreSheet = false
                            }
                        }.onFailure {
                            Toast.makeText(context, "下载失败: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        )
    }
}

// ---- Helper Components ----

@Composable
private fun SettingsGroupCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(14.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(22.dp), tint = iconTint)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

// ---- Bottom Sheets ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BabyInfoSheet(
    babyInfo: BabyInfo,
    babies: List<BabyInfo>,
    onDismiss: () -> Unit,
    onSave: (BabyInfo) -> Unit,
    onAddBaby: () -> Unit,
    onDelete: (String) -> Unit
) {
    var form by remember { mutableStateOf(babyInfo) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val localPath = DataManager.copyPhotoToInternalStorage(uri)
            form = form.copy(avatar = localPath ?: uri.toString())
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("宝宝信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Avatar
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF8C8D8))
                        .clickable { avatarPicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (form.avatar != null) {
                        AsyncImage(model = form.avatar, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Person, null, Modifier.size(36.dp), tint = Color.White)
                    }
                }
            }

            OutlinedTextField(
                value = form.name, onValueChange = { form = form.copy(name = it) },
                label = { Text("宝宝名字") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = form.nickname, onValueChange = { form = form.copy(nickname = it) },
                label = { Text("昵称") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("出生日期: ${form.birthDate}")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("girl" to "女宝宝", "boy" to "男宝宝").forEach { (g, label) ->
                    FilterChip(
                        selected = form.gender == g,
                        onClick = { form = form.copy(gender = g) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF8C8D8)
                        )
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (form.name.isNotBlank() && form.nickname.isNotBlank()) onSave(form)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A)),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("保存", color = Color.White) }
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onAddBaby()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("添加宝宝") }
            }
            if (babies.size > 1) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("删除宝宝", color = Color(0xFFEF5350))
                }
            }
        }
    }

    if (showDatePicker) {
        SettingsDatePicker(
            initialDate = LocalDate.parse(form.birthDate),
            onDateSelected = { selectedDate ->
                form = form.copy(birthDate = "%04d-%02d-%02d".format(selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth))
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除「${babyInfo.nickname}」吗？该宝宝的所有记录和照片也将被删除。此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(babyInfo.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) { Text("删除", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBabySheet(
    onDismiss: () -> Unit,
    onAdd: (BabyInfo) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("2025-01-01") }
    var gender by remember { mutableStateOf("girl") }
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("添加宝宝", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("宝宝名字") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = nickname, onValueChange = { nickname = it },
                label = { Text("昵称") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            ) { Text("出生日期: $birthDate") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("girl" to "女宝宝", "boy" to "男宝宝").forEach { (g, label) ->
                    FilterChip(selected = gender == g, onClick = { gender = g },
                        label = { Text(label) }, modifier = Modifier.weight(1f))
                }
            }
            Button(
                onClick = {
                    if (name.isNotBlank() && nickname.isNotBlank()) {
                        onAdd(BabyInfo(
                            name = name.trim(), nickname = nickname.trim(),
                            birthDate = birthDate, gender = gender
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A)),
                shape = RoundedCornerShape(16.dp)
            ) { Text("添加", color = Color.White) }
        }
    }

    if (showDatePicker) {
        SettingsDatePicker(
            initialDate = LocalDate.parse(birthDate),
            onDateSelected = { selectedDate ->
                birthDate = "%04d-%02d-%02d".format(selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth)
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIConfigSheet(
    profiles: List<AIProfile>,
    onDismiss: () -> Unit,
    onSaveProfile: (AIProfile) -> Unit,
    onAddProfile: (AIProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSetActive: (String) -> Unit,
    onTestConnection: (AIProfile, (Result<Boolean>) -> Unit) -> Unit
) {
    var editingProfile by remember { mutableStateOf<AIProfile?>(null) }
    var showAddForm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        if (editingProfile != null) {
            // Profile edit mode
            AIProfileEditForm(
                profile = editingProfile!!,
                isNew = showAddForm,
                onSave = {
                    if (showAddForm) onAddProfile(it) else onSaveProfile(it)
                    editingProfile = null
                    showAddForm = false
                },
                onCancel = { editingProfile = null; showAddForm = false },
                onTestConnection = onTestConnection
            )
        } else {
            // Profile list mode
            Column(
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("AI 配置文件管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                if (profiles.isEmpty()) {
                    Text("尚未配置 AI 配置文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp))
                }

                profiles.forEach { profile ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (profile.isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(profile.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold)
                                        if (profile.isActive) {
                                            Spacer(Modifier.width(6.dp))
                                            Box(
                                                Modifier.clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.primary)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("活跃", fontSize = 10.sp, color = Color.White)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text("${profile.model} · ${profile.apiBaseUrl}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (!profile.isActive) {
                                    OutlinedButton(
                                        onClick = { onSetActive(profile.id) },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) { Text("启用", fontSize = 11.sp) }
                                }
                                OutlinedButton(
                                    onClick = { editingProfile = profile },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("编辑", fontSize = 11.sp) }
                                OutlinedButton(
                                    onClick = { showDeleteConfirm = profile.id },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                                ) { Text("删除", fontSize = 11.sp) }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        editingProfile = AIProfile(
                            apiBaseUrl = "https://api.deepseek.com",
                            model = "deepseek-chat",
                            isActive = profiles.isEmpty()
                        )
                        showAddForm = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加配置", color = Color.White)
                }
            }
        }
    }

    // Delete confirmation
    showDeleteConfirm?.let { profileId ->
        val profile = profiles.find { it.id == profileId }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认删除", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除配置「${profile?.name ?: ""}」吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProfile(profileId)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) { Text("删除", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIProfileEditForm(
    profile: AIProfile,
    isNew: Boolean,
    onSave: (AIProfile) -> Unit,
    onCancel: () -> Unit,
    onTestConnection: (AIProfile, (Result<Boolean>) -> Unit) -> Unit
) {
    var form by remember { mutableStateOf(profile) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isNew) "新建配置" else "编辑配置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onCancel) { Text("返回") }
        }

        OutlinedTextField(
            value = form.name, onValueChange = { form = form.copy(name = it) },
            label = { Text("配置名称") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = form.apiBaseUrl, onValueChange = { form = form.copy(apiBaseUrl = it) },
            label = { Text("API Base URL") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = form.apiKey, onValueChange = { form = form.copy(apiKey = it) },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Text("模型选择", fontWeight = FontWeight.Medium)
        AI_MODELS.forEach { model ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { form = form.copy(model = model.id, apiBaseUrl = model.apiBase) }
                    .background(if (form.model == model.id) Color(0xFFEC407A).copy(alpha = 0.1f) else Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(model.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Text(model.provider, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (form.model == model.id) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = Color(0xFFEC407A))
                }
            }
        }

        OutlinedTextField(
            value = form.systemPrompt, onValueChange = { form = form.copy(systemPrompt = it) },
            label = { Text("系统提示词") },
            modifier = Modifier.fillMaxWidth(), minLines = 2,
            shape = RoundedCornerShape(12.dp)
        )

        Column {
            Text("温度: %.1f".format(form.temperature),
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text("0=精确严谨，1=平衡，2=创意发散。育儿建议推荐0.7-1.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            Slider(
                value = form.temperature, onValueChange = { form = form.copy(temperature = it) },
                valueRange = 0f..2f,
                steps = 19,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column {
            Text("Top-P: %.2f".format(form.topP),
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text("核采样概率，通常与温度二选一调整",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            Slider(
                value = form.topP, onValueChange = { form = form.copy(topP = it) },
                valueRange = 0f..1f,
                steps = 19,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("最大 Token 数: ${form.maxTokens}",
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            OutlinedButton(
                onClick = {
                    val options = listOf(512, 1024, 2048, 4096, 8192)
                    val idx = options.indexOf(form.maxTokens)
                    val nextIdx = (idx + 1) % options.size
                    form = form.copy(maxTokens = options[nextIdx])
                },
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                shape = RoundedCornerShape(8.dp)
            ) { Text("切换", fontSize = 11.sp) }
        }
        Text("单次回复的最大字数。2048≈1500汉字，4096≈3000汉字。值越大回复越长，但响应更慢。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))

        testResult?.let {
            Text(it, style = MaterialTheme.typography.labelSmall,
                color = if (it.contains("成功") || it.contains("通过")) Color(0xFF4CAF50) else Color(0xFFEF5350))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (form.apiBaseUrl.isNotBlank()) {
                        isTesting = true
                        testResult = null
                        onTestConnection(form) { result ->
                            isTesting = false
                            testResult = result.fold(
                                onSuccess = { "连接成功！" },
                                onFailure = { "连接失败: ${it.message}" }
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isTesting && form.apiBaseUrl.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isTesting) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                else { Icon(Icons.Default.NetworkCheck, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                Text("测试连接", fontSize = 13.sp)
            }
            Button(
                onClick = {
                    if (form.name.isNotBlank() && form.apiBaseUrl.isNotBlank()) {
                        onSave(form)
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                shape = RoundedCornerShape(12.dp)
            ) { Text("保存", color = Color.White) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSheet(
    currentMode: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("选择主题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            listOf(
                Triple(ThemeMode.LIGHT, "亮色", Icons.Default.LightMode),
                Triple(ThemeMode.DARK, "暗色", Icons.Default.DarkMode),
                Triple(ThemeMode.SYSTEM, "跟随系统", Icons.Default.BrightnessAuto)
            ).forEach { (mode, label, icon) ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(mode) }
                        .background(if (currentMode == mode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, Modifier.size(22.dp),
                        tint = if (currentMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    if (currentMode == mode) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(
    categories: List<CategoryConfig>,
    customCategories: List<CategoryConfig>,
    onDismiss: () -> Unit,
    onAdd: (CategoryConfig) -> Unit,
    onRemove: (String) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("") }
    var newColor by remember { mutableStateOf(0xFFf8c8d8) }

    val presetColors = listOf(0xFFf8c8d8, 0xFFf6ba6d, 0xFFa5d8dd, 0xFFc4b5fd, 0xFF86efac, 0xFFfde68a)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("分类管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            categories.forEach { cat ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(Color(cat.color)))
                        Spacer(Modifier.width(10.dp))
                        Text(cat.label, style = MaterialTheme.typography.bodySmall)
                    }
                    if (cat.isDefault) {
                        Icon(Icons.Default.Lock, null, Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    } else {
                        IconButton(onClick = { onRemove(cat.id) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp), tint = Color(0xFFEF5350))
                        }
                    }
                }
            }
            if (showAdd) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newLabel, onValueChange = { newLabel = it },
                    label = { Text("分类名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    presetColors.forEach { c ->
                        Box(
                            Modifier.size(30.dp).clip(RoundedCornerShape(15.dp))
                                .background(Color(c))
                                .clickable { newColor = c }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (newLabel.isNotBlank()) {
                                onAdd(CategoryConfig(
                                    id = "custom-${System.currentTimeMillis()}",
                                    label = newLabel.trim(), icon = "Tag",
                                    color = newColor, isDefault = false
                                ))
                                newLabel = ""; showAdd = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A))
                    ) { Text("添加", color = Color.White) }
                    OutlinedButton(onClick = { showAdd = false }, modifier = Modifier.weight(1f)) { Text("取消") }
                }
            } else {
                OutlinedButton(
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Icon(Icons.Default.Add, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("添加自定义分类") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebDavConfigSheet(
    currentConfig: WebDavManager.WebDavConfig?,
    onDismiss: () -> Unit,
    onSave: (WebDavManager.WebDavConfig) -> Unit,
    onClear: () -> Unit
) {
    var url by remember { mutableStateOf(currentConfig?.url ?: "") }
    var username by remember { mutableStateOf(currentConfig?.username ?: "") }
    var password by remember { mutableStateOf(currentConfig?.password ?: "") }
    var backupPath by remember { mutableStateOf(currentConfig?.backupPath ?: "/sata1-15529232180/yueming-backups/") }
    var showPassword by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var dirContents by remember { mutableStateOf<List<String>>(emptyList()) }
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("WebDAV 配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("服务器地址") },
                placeholder = { Text("https://dav.example.com") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text("用户名") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                }
            )
            OutlinedTextField(
                value = backupPath, onValueChange = { backupPath = it },
                label = { Text("备份路径") },
                placeholder = { Text("/path/to/backups/") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            testResult?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (it.contains("连接成功")) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                    } else {
                        Icon(Icons.Default.Cancel, null, Modifier.size(16.dp), tint = Color(0xFFEF5350))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = if (it.contains("连接成功")) Color(0xFF4CAF50) else Color(0xFFEF5350))
                }
                if (dirContents.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("备份目录内容 (${dirContents.size} 个文件):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    dirContents.take(5).forEach { file ->
                        Text("  • $file", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (url.isNotBlank()) {
                            isTesting = true
                            testResult = null
                            dirContents = emptyList()
                            val config = WebDavManager.WebDavConfig(url.trim(), username, password, backupPath.trim())
                            DataManager.testWebDavConnection(config) { result ->
                                isTesting = false
                                result.onSuccess { testRes ->
                                    testResult = if (testRes.success) "连接成功" else testRes.message
                                    dirContents = testRes.directoryContents
                                }.onFailure {
                                    testResult = "连接失败: ${it.message}"
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isTesting,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isTesting) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Default.NetworkCheck, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                    Text("测试连接", fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        if (url.isNotBlank()) {
                            onSave(WebDavManager.WebDavConfig(url.trim(), username, password, backupPath.trim()))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66BB6A)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("保存", color = Color.White) }
            }
            if (currentConfig != null) {
                TextButton(onClick = { onClear(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("清除 WebDAV 配置", color = Color(0xFFEF5350), fontSize = 13.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestoreSheet(
    onDismiss: () -> Unit,
    onRestore: (String) -> Unit
) {
    var backupFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        DataManager.refreshBackupList { result ->
            result.onSuccess { backupFiles = it }
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("从备份恢复", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("选择要恢复的备份文件:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (backupFiles.isEmpty()) {
                Text("没有找到备份文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp))
            } else {
                backupFiles.forEach { file ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onRestore(file) }
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Archive, null, Modifier.size(20.dp), tint = Color(0xFF66BB6A))
                        Spacer(Modifier.width(12.dp))
                        Text(file, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.Download, null, Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDatePicker(
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
