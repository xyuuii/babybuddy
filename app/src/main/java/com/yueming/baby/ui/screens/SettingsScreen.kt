package com.yueming.baby.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yueming.baby.data.*
import com.yueming.baby.data.cloud.CloudStorageConfig
import com.yueming.baby.data.cloud.CloudManager
import com.yueming.baby.data.cloud.StorageProtocol
import com.yueming.baby.ui.components.AuthenticatedAsyncImage
import com.yueming.baby.ui.components.BabyContentCard
import com.yueming.baby.ui.components.BabyDateWheelDialog
import com.yueming.baby.ui.components.BabyGlassAlertDialog
import com.yueming.baby.ui.components.BabyGlassModalSheet
import com.yueming.baby.ui.components.BabyDangerButton
import com.yueming.baby.ui.components.BabyGlassButton
import com.yueming.baby.ui.components.BabyGlassIconButton
import com.yueming.baby.ui.components.BabyGlassChip
import com.yueming.baby.ui.components.BabyGlassRole
import com.yueming.baby.ui.components.BabyGlassSurface
import com.yueming.baby.ui.components.BabyGlassTextField
import com.yueming.baby.ui.components.BabyGlassTitle
import com.yueming.baby.ui.components.BabyPrimaryButton
import com.yueming.baby.ui.components.BabySecondaryButton
import com.yueming.baby.ui.components.BabySettingsRow
import com.yueming.baby.ui.components.LocalBabyBottomBarClearance
import com.yueming.baby.ui.components.LocalBabyStatusBarClearance
import com.yueming.baby.ui.components.babyPageBackground
import com.yueming.baby.ui.motion.BabyMotion
import com.yueming.baby.ui.motion.MotionAnimatedContent
import com.yueming.baby.ui.motion.motionCardPress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val cloudStorageConfig by DataManager.cloudStorageConfig.collectAsState()
    val syncStatus by DataManager.syncStatus.collectAsState()
    val allCategories = DataManager.allCategories
    val effectiveSyncStatus = remember(syncStatus, cloudStorageConfig.host) {
        val hasCloudConfig = cloudStorageConfig.host.isNotBlank()
        syncStatus.copy(
            isConfigured = syncStatus.isConfigured || hasCloudConfig,
            lastMessage = if (hasCloudConfig && syncStatus.lastUpdatedAt == 0L) {
                "等待同步状态刷新"
            } else {
                syncStatus.lastMessage
            }
        )
    }

    var showBabySheet by remember { mutableStateOf(false) }
    var showAISheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showAddBabySheet by remember { mutableStateOf(false) }
    var showWebDavSheet by remember { mutableStateOf(false) }
    var showCloudStorageSheet by remember { mutableStateOf(false) }
    var showClearDataConfirm by remember { mutableStateOf(false) }
    var showSyncCenter by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bottomBarClearance = LocalBabyBottomBarClearance.current
    val statusBarClearance = LocalBabyStatusBarClearance.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().babyPageBackground(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = statusBarClearance + 12.dp,
            end = 20.dp,
            bottom = bottomBarClearance
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            MiuixSettingsHeader(
                babyInfo = babyInfo,
                photosCount = photos.size,
                timelineCount = timeline.size,
                syncStatus = effectiveSyncStatus
            )
        }

        // ---- Baby Info Group ----
        item {
            SettingsGroupCard(
                icon = Icons.Default.Person,
                iconTint = Color(0xFFEC407A),
                title = "宝宝信息",
                subtitle = "${babyInfo.nickname} · ${DataManager.getAgeInMonths(babyInfo.birthDate)}个月",
                onClick = { showBabySheet = true },
                modifier = Modifier.animateItem()
            )
        }

        // ---- AI Config Group ----
        item {
            SettingsGroupCard(
                icon = Icons.Default.SmartToy,
                iconTint = Color(0xFF7C4DFF),
                title = "AI 助手配置",
                subtitle = if (aiConfig.apiKey.isNotEmpty()) "已配置 · ${aiConfig.model}" else "点击配置",
                onClick = { showAISheet = true },
                modifier = Modifier.animateItem()
            )
        }

        // ---- Cloud Storage Config Group ----
        item {
            SettingsGroupCard(
                icon = Icons.Default.CloudQueue,
                iconTint = Color(0xFF42A5F5),
                title = "存储配置",
                subtitle = if (cloudStorageConfig.host.isBlank()) {
                    "点击配置 NAS / WebDAV"
                } else {
                    "${cloudStorageConfig.protocol.name} · ${cloudStorageConfig.host}:${cloudStorageConfig.port}"
                },
                onClick = { showCloudStorageSheet = true },
                modifier = Modifier.animateItem()
            )
        }

        item {
            SyncStatusCard(
                status = effectiveSyncStatus,
                onRetry = {
                    DataManager.retrySyncNow()
                    Toast.makeText(context, "正在重试同步", Toast.LENGTH_SHORT).show()
                },
                onDetails = { showSyncCenter = true },
                modifier = Modifier.animateItem()
            )
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
                onClick = { showThemeSheet = true },
                modifier = Modifier.animateItem()
            )
        }

        // ---- Category Management Group ----
        item {
            SettingsGroupCard(
                icon = Icons.Default.Category,
                iconTint = Color(0xFFF6BA6D),
                title = "分类管理",
                subtitle = "${allCategories.size} 个分类",
                onClick = { showCategorySheet = true },
                modifier = Modifier.animateItem()
            )
        }

        // ---- Clear Data ----
        item {
            Card(
                modifier = Modifier.fillMaxWidth().animateItem(),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .clickable { showClearDataConfirm = true }
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
                modifier = Modifier.fillMaxWidth().animateItem(),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("关于", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("babybuddy v1.0", style = MaterialTheme.typography.bodySmall)
                    Text("宝宝成长记录应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Jetpack Compose + Material3 + Room",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Font: optional MiSans local font",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/xyuuii/babybuddy")
                                    )
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Code, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("GitHub", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "github.com/xyuuii/babybuddy",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    if (showClearDataConfirm) {
        BabyGlassAlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF5350)) },
            title = { Text("确认清除所有数据？") },
            text = {
                Text("这会清空本机宝宝资料、时间线、媒体索引和配置，并会把远端数据文件同步为空。此操作不可撤销，请先确认 NAS 上已有备份。")
            },
            confirmButton = {
                BabyDangerButton(
                    text = "确认清除",
                    onClick = {
                        showClearDataConfirm = false
                        DataManager.resetAllData {
                            Toast.makeText(context, "数据已清除", Toast.LENGTH_SHORT).show()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                )
            },
            dismissButton = {
                BabySecondaryButton(text = "取消", onClick = { showClearDataConfirm = false })
            }
        )
    }

    if (showSyncCenter) {
        SyncCenterDialog(
            status = effectiveSyncStatus,
            onDismiss = { showSyncCenter = false },
            onRetry = {
                DataManager.retrySyncNow()
                Toast.makeText(context, "正在重试同步", Toast.LENGTH_SHORT).show()
            }
        )
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

    // Cloud Storage Config Sheet
    if (showCloudStorageSheet) {
        CloudStorageConfigSheet(
            currentConfig = cloudStorageConfig,
            onDismiss = { showCloudStorageSheet = false },
            onSave = { config -> DataManager.saveCloudStorageConfig(config) }
        )
    }

}

// ---- Helper Components ----

@Composable
private fun MiuixSettingsHeader(
    babyInfo: BabyInfo,
    photosCount: Int,
    timelineCount: Int,
    syncStatus: DataManager.SyncStatus
) {
    val statusColor = when {
        syncStatus.isSyncing -> Color(0xFF42A5F5)
        !syncStatus.isConfigured -> Color(0xFFFFA726)
        syncStatus.hasUnsyncedChanges || syncStatus.pendingMediaCount > 0 -> Color(0xFFFFA726)
        else -> Color(0xFF4CAF50)
    }
    val statusText = when {
        syncStatus.isSyncing -> "正在同步"
        !syncStatus.isConfigured -> "NAS 未配置"
        syncStatus.hasUnsyncedChanges || syncStatus.pendingMediaCount > 0 -> "待同步"
        else -> "NAS 正常"
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BabyGlassTitle(
            title = "设置",
            subtitle = statusText
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(34.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f))
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        babyInfo.nickname.take(1).ifBlank { "宝" },
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        babyInfo.nickname.ifBlank { "宝宝" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$photosCount 个媒体 · $timelineCount 条记录",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(statusColor.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cardCorner by animateDpAsState(
        targetValue = if (pressed) 34.dp else 30.dp,
        animationSpec = BabyMotion.cardShapeSpring(),
        label = "settingsGroupCardCorner"
    )
    val iconCorner by animateDpAsState(
        targetValue = if (pressed) 22.dp else 18.dp,
        animationSpec = BabyMotion.cardShapeSpring(),
        label = "settingsGroupIconCorner"
    )
    val containerColor by animateColorAsState(
        targetValue = if (pressed) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
        },
        animationSpec = tween(durationMillis = 180, easing = BabyMotion.fadeThroughEase),
        label = "settingsGroupContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = if (pressed) {
            iconTint.copy(alpha = 0.24f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)
        },
        animationSpec = tween(durationMillis = 180, easing = BabyMotion.fadeThroughEase),
        label = "settingsGroupBorder"
    )

    BabySettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        modifier = modifier,
        accent = iconTint
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.70f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.ChevronRight,
                null,
                Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
            )
        }
    }
}

@Composable
private fun SyncStatusCard(
    status: DataManager.SyncStatus,
    onRetry: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when {
        status.isSyncing -> Color(0xFF42A5F5)
        !status.isConfigured -> Color(0xFFFFA726)
        status.hasUnsyncedChanges || status.pendingMediaCount > 0 -> Color(0xFFFFA726)
        else -> Color(0xFF66BB6A)
    }
    val title = when {
        status.isSyncing -> "正在同步"
        !status.isConfigured -> "NAS 未配置"
        status.hasUnsyncedChanges || status.pendingMediaCount > 0 -> "等待同步"
        else -> "同步正常"
    }
    val detail = buildString {
        append(status.lastMessage)
        if (status.pendingMediaCount > 0) append(" · 待上传 ${status.pendingMediaCount} 个媒体")
        if (status.hasUnsyncedChanges) append(" · 有本地改动")
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cardCorner by animateDpAsState(
        targetValue = if (pressed) 36.dp else 32.dp,
        animationSpec = BabyMotion.cardShapeSpring(),
        label = "syncStatusCardCorner"
    )
    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surface.copy(alpha = if (pressed) 0.96f else 0.985f),
        animationSpec = tween(durationMillis = 180, easing = BabyMotion.fadeThroughEase),
        label = "syncStatusContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (pressed) 0.34f else 0.22f),
        animationSpec = tween(durationMillis = 180, easing = BabyMotion.fadeThroughEase),
        label = "syncStatusBorder"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .motionCardPress(interactionSource = interactionSource, pressedScale = 0.982f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDetails
            ),
        shape = RoundedCornerShape(cardCorner),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(0.7.dp, borderColor)
    ) {
        Column(
            Modifier.padding(18.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f)),
                    contentAlignment = Alignment.Center
                ) {
                    MotionAnimatedContent(
                        targetState = status.isSyncing,
                        label = "syncStatusIcon"
                    ) { syncing ->
                        if (syncing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = accent)
                        } else {
                            Icon(
                                if (status.hasUnsyncedChanges || status.pendingMediaCount > 0 || !status.isConfigured) Icons.Default.SyncProblem else Icons.Default.CloudDone,
                                null,
                                Modifier.size(23.dp),
                                tint = accent
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(3.dp))
                    Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onRetry, enabled = !status.isSyncing && status.isConfigured) {
                    Text("重试", fontWeight = FontWeight.Bold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SyncChip("远端", if (status.isConfigured) "已连接" else "未配置", accent)
                SyncChip("队列", "${status.pendingMediaCount}", accent)
                SyncChip("本地", if (status.hasUnsyncedChanges) "有改动" else "干净", accent)
            }
        }
    }
}

@Composable
private fun SyncChip(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(5.dp))
        Text(value, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SyncCenterDialog(
    status: DataManager.SyncStatus,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    val updatedAt = remember(status.lastUpdatedAt) {
        if (status.lastUpdatedAt == 0L) {
            "尚未同步"
        } else {
            runCatching {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                Instant.ofEpochMilli(status.lastUpdatedAt)
                    .atZone(ZoneId.systemDefault())
                    .format(formatter)
            }.getOrDefault("未知")
        }
    }

    BabyGlassAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Sync, contentDescription = null) },
        title = { Text("同步中心") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SyncCenterRow("NAS 配置", if (status.isConfigured) "已配置" else "未配置")
                SyncCenterRow("同步状态", if (status.isSyncing) "正在同步" else status.lastMessage)
                SyncCenterRow("本地改动", if (status.hasUnsyncedChanges) "有待同步改动" else "无")
                SyncCenterRow("待上传媒体", "${status.pendingMediaCount} 个")
                SyncCenterRow("最后更新", updatedAt)
                if (status.hasUnsyncedChanges || status.pendingMediaCount > 0) {
                    Text(
                        "提示：断网或 NAS 不可达时会先保留本地快照，网络恢复后可在这里手动重试。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            BabyPrimaryButton(
                text = "立即重试",
                onClick = onRetry,
                enabled = status.isConfigured && !status.isSyncing,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            )
        },
        dismissButton = {
            BabySecondaryButton(text = "关闭", onClick = onDismiss)
        }
    )
}

@Composable
private fun SyncCenterRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

// ---- Bottom Sheets ----

@Composable
private fun MiuixSheetCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    BabyGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        role = BabyGlassRole.RegularChrome
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

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
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val localPath = withContext(Dispatchers.IO) {
                    DataManager.copyPhotoToInternalStorage(uri)
                }
                form = form.copy(avatar = localPath ?: uri.toString())
            }
        }
    }

    BabyGlassModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp)
                .padding(bottom = 56.dp),
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
                        AuthenticatedAsyncImage(model = form.avatar, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Person, null, Modifier.size(36.dp), tint = Color.White)
                    }
                }
            }

            BabyGlassTextField(
                value = form.name, onValueChange = { form = form.copy(name = it) },
                label = "宝宝名字", modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            BabyGlassTextField(
                value = form.nickname, onValueChange = { form = form.copy(nickname = it) },
                label = "昵称", modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            BabySecondaryButton(
                text = "出生日期: ${form.birthDate}",
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("girl" to "女宝宝", "boy" to "男宝宝").forEach { (g, label) ->
                    BabyGlassChip(
                        label = label,
                        selected = form.gender == g,
                        onClick = { form = form.copy(gender = g) },
                        modifier = Modifier.weight(1f),
                        accent = Color(0xFFEC407A)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BabyPrimaryButton(
                    text = "保存",
                    onClick = {
                        if (form.name.isNotBlank() && form.nickname.isNotBlank()) onSave(form)
                    },
                    modifier = Modifier.weight(1f)
                )
                BabySecondaryButton(
                    text = "添加宝宝",
                    onClick = {
                        onDismiss()
                        onAddBaby()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            if (babies.size > 1) {
                BabySecondaryButton(
                    text = "删除信息",
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Default.Delete
                )
            }
        }
    }

    if (showDatePicker) {
        val safeDate = if (form.birthDate.isNotBlank()) {
            try { LocalDate.parse(form.birthDate) } catch (_: Exception) { LocalDate.now() }
        } else LocalDate.now()
        SettingsDatePicker(
            initialDate = safeDate,
            onDateSelected = { selectedDate ->
                form = form.copy(birthDate = "%04d-%02d-%02d".format(selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth))
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showDeleteConfirm) {
        BabyGlassAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除「${babyInfo.nickname}」吗？该宝宝的所有记录和照片也将被删除。此操作不可撤销。") },
            confirmButton = {
                BabyDangerButton(
                    text = "删除",
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(babyInfo.id)
                    },
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                )
            },
            dismissButton = { BabySecondaryButton(text = "取消", onClick = { showDeleteConfirm = false }) }
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f

    BabyGlassModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp)
                .padding(bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("添加宝宝", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            BabyGlassTextField(
                value = name, onValueChange = { name = it },
                label = "宝宝名字", modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            BabyGlassTextField(
                value = nickname, onValueChange = { nickname = it },
                label = "昵称", modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            BabySecondaryButton(
                text = "出生日期: $birthDate",
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("girl" to "女宝宝", "boy" to "男宝宝").forEach { (g, label) ->
                    BabyGlassChip(
                        label = label,
                        selected = gender == g,
                        onClick = { gender = g },
                        modifier = Modifier.weight(1f),
                        accent = Color(0xFFEC407A)
                    )
                }
            }
            BabyPrimaryButton(
                text = "添加",
                onClick = {
                    if (name.isNotBlank() && nickname.isNotBlank()) {
                        onAdd(BabyInfo(
                            name = name.trim(), nickname = nickname.trim(),
                            birthDate = birthDate, gender = gender
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
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
    var selectedProfileId by remember { mutableStateOf<String?>(profiles.firstOrNull()?.id) }
    var showAddForm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var isEditing by remember { mutableStateOf(false) }

    val selectedProfile = profiles.find { it.id == selectedProfileId }
    val displayProfiles = profiles
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f

    BabyGlassModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp)
                .padding(bottom = 40.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI 配置文件管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            // --- Top Area: Profile List (LazyRow) ---
            if (displayProfiles.isEmpty() && !showAddForm) {
                BabyGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    role = BabyGlassRole.RegularChrome
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("尚未配置 AI 配置文件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        BabyPrimaryButton(
                            text = "添加配置",
                            onClick = {
                                showAddForm = true
                                isEditing = true
                                selectedProfileId = null
                            },
                            leadingIcon = Icons.Default.Add
                        )
                    }
                }
            } else if (!isEditing) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayProfiles.size) { index ->
                        val profile = displayProfiles[index]
                        val isSelected = profile.id == selectedProfileId
                        BabyGlassChip(
                            label = if (profile.isActive) "${profile.name} · 活跃" else profile.name,
                            selected = isSelected,
                            onClick = {
                                selectedProfileId = profile.id
                                showAddForm = false
                            },
                            icon = if (profile.isActive) Icons.Default.CheckCircle else null,
                            accent = MaterialTheme.colorScheme.primary
                        )
                    }
                    item {
                        BabyGlassChip(
                            label = "+ 添加",
                            selected = showAddForm,
                            onClick = {
                                selectedProfileId = null
                                showAddForm = true
                                isEditing = true
                            },
                            accent = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

    // --- Bottom Area ---
            if (showAddForm || isEditing) {
                val editProfile = if (showAddForm) {
                    AIProfile(
                        apiBaseUrl = "https://api.deepseek.com",
                        model = "deepseek-v4-pro",
                        isActive = profiles.isEmpty()
                    )
                } else selectedProfile!!

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AIProfileEditFormRedesigned(
                        profile = editProfile,
                        isNew = showAddForm,
                        onSave = {
                            if (showAddForm) onAddProfile(it) else onSaveProfile(it)
                            showAddForm = false
                            isEditing = false
                            selectedProfileId = it.id
                        },
                        onCancel = { showAddForm = false; isEditing = false },
                        onTestConnection = onTestConnection
                    )
                }
            } else if (selectedProfile != null) {
                // Show selected profile info with action buttons
                BabyGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    role = BabyGlassRole.RegularChrome
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(selectedProfile.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold)
                                    if (selectedProfile.isActive) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            Modifier.clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("活跃", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text("${selectedProfile.model} · ${selectedProfile.apiBaseUrl}",
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
                            if (!selectedProfile.isActive) {
                                BabySecondaryButton(
                                    text = "启用",
                                    onClick = { onSetActive(selectedProfile.id) },
                                    modifier = Modifier.height(42.dp)
                                )
                            }
                            BabySecondaryButton(
                                text = "编辑",
                                onClick = { isEditing = true },
                                modifier = Modifier.height(42.dp)
                            )
                            BabyDangerButton(
                                text = "删除",
                                onClick = { showDeleteConfirm = selectedProfile.id },
                                modifier = Modifier.height(42.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation
    showDeleteConfirm?.let { profileId ->
        val profile = profiles.find { it.id == profileId }
        BabyGlassAlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认删除", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除配置「${profile?.name ?: ""}」吗？") },
            confirmButton = {
                BabyDangerButton(
                    text = "删除",
                    onClick = {
                        onDeleteProfile(profileId)
                        showDeleteConfirm = null
                    }
                )
            },
            dismissButton = { BabySecondaryButton(text = "取消", onClick = { showDeleteConfirm = null }) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIProfileEditFormRedesigned(
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isNew) "新建配置" else "编辑配置",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            BabySecondaryButton(
                text = "返回",
                onClick = onCancel
            )
        }

        // Card Group 1: Basic Info (Name / URL / Key)
        BabyContentCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("基本信息", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                BabyGlassTextField(
                    value = form.name, onValueChange = { form = form.copy(name = it) },
                    label = "配置名称",
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                BabyGlassTextField(
                    value = form.apiBaseUrl, onValueChange = { form = form.copy(apiBaseUrl = it) },
                    label = "API Base URL",
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                BabyGlassTextField(
                    value = form.apiKey, onValueChange = { form = form.copy(apiKey = it) },
                    label = "API Key",
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
        }

        // Card Group 2: Model Params (Model / Temperature / Tokens)
        BabyContentCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("模型参数", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)

                Text("模型选择", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                AI_MODELS.forEach { model ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { form = form.copy(model = model.id, apiBaseUrl = model.apiBase) }
                            .background(
                                if (form.model == model.id) {
                                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.76f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(model.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text(model.provider, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (form.model == model.id) {
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Column {
                    Text("温度: %.1f".format(form.temperature),
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Slider(
                        value = form.temperature, onValueChange = { form = form.copy(temperature = it) },
                        valueRange = 0f..2f, steps = 19,
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
                    BabySecondaryButton(
                        text = "切换",
                        onClick = {
                            val options = listOf(512, 1024, 2048, 4096, 8192)
                            val idx = options.indexOf(form.maxTokens)
                            val nextIdx = (idx + 1) % options.size
                            form = form.copy(maxTokens = options[nextIdx])
                        },
                        modifier = Modifier.height(36.dp)
                    )
                }
            }
        }

        // Card Group 3: Advanced (System Prompt)
        BabyContentCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("高级设置", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                BabyGlassTextField(
                    value = form.systemPrompt, onValueChange = { form = form.copy(systemPrompt = it) },
                    label = "系统提示词",
                    modifier = Modifier.fillMaxWidth(), minLines = 2
                )
            }
        }

        testResult?.let {
            Text(it, style = MaterialTheme.typography.labelSmall,
                color = if (it.contains("成功") || it.contains("通过")) Color(0xFF4CAF50) else Color(0xFFEF5350))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BabySecondaryButton(
                text = if (isTesting) "测试中" else "测试连接",
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
                leadingIcon = Icons.Default.NetworkCheck
            )
            BabyPrimaryButton(
                text = "保存",
                onClick = {
                    if (form.name.isNotBlank() && form.apiBaseUrl.isNotBlank()) {
                        onSave(form)
                    }
                },
                modifier = Modifier.weight(1f)
            )
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f

    BabyGlassModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp)
                .padding(bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("选择主题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            listOf(
                Triple(ThemeMode.LIGHT, "亮色", Icons.Default.LightMode),
                Triple(ThemeMode.DARK, "暗色", Icons.Default.Star),
                Triple(ThemeMode.SYSTEM, "跟随系统", Icons.Default.BrightnessAuto)
            ).forEach { (mode, label, icon) ->
                val selected = currentMode == mode
                BabySettingsRow(
                    icon = icon,
                    title = label,
                    subtitle = if (selected) "当前使用" else "点击切换",
                    onClick = { onSelect(mode) },
                    accent = MaterialTheme.colorScheme.primary,
                    trailing = {
                        if (selected) {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
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
    var expandedCategoryId by remember { mutableStateOf<String?>(null) }
    var showAddSub by remember { mutableStateOf<String?>(null) }
    var newSubName by remember { mutableStateOf("") }
    var editingSub by remember { mutableStateOf<Pair<String, CategoryConfig.SubCategory>?>(null) }
    var editSubName by remember { mutableStateOf("") }

    val presetColors = listOf(0xFFf8c8d8, 0xFFf6ba6d, 0xFFa5d8dd, 0xFFc4b5fd, 0xFF86efac, 0xFFfde68a)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f

    BabyGlassModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp)
                .padding(bottom = 56.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("分类管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            categories.forEach { cat ->
                val isExpanded = expandedCategoryId == cat.id
                BabyContentCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .clickable { expandedCategoryId = if (isExpanded) null else cat.id }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(Color(cat.color)))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        cat.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (cat.children.isNotEmpty()) {
                                        Text(
                                            "${cat.children.size} 个子分类",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.widthIn(min = 56.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (cat.isDefault) {
                                    Icon(Icons.Default.Lock, null, Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                } else {
                                    BabyGlassIconButton(
                                        icon = Icons.Default.Close,
                                        onClick = { onRemove(cat.id) },
                                        modifier = Modifier.size(36.dp),
                                        accent = MaterialTheme.colorScheme.error
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null, Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HorizontalDivider()

                                cat.children.forEach { sub ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.58f))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.SubdirectoryArrowRight, null, Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                sub.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        BabySecondaryButton(
                                            text = "编辑",
                                            onClick = {
                                                editingSub = cat.id to sub
                                                editSubName = sub.name
                                            },
                                            modifier = Modifier.height(40.dp),
                                            leadingIcon = Icons.Default.Edit
                                        )
                                    }
                                }

                                if (cat.children.isEmpty()) {
                                    Text(
                                        "暂无子分类",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.46f))
                                            .padding(horizontal = 12.dp, vertical = 12.dp)
                                    )
                                }

                                if (showAddSub == cat.id) {
                                    Column(
                                        Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        BabyGlassTextField(
                                            value = newSubName, onValueChange = { newSubName = it },
                                            placeholder = "子分类名称",
                                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                                        )
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            BabyPrimaryButton(
                                                text = "添加",
                                                onClick = {
                                                    if (newSubName.isNotBlank()) {
                                                        DataManager.addSubCategory(cat.id, newSubName.trim())
                                                        newSubName = ""
                                                        showAddSub = null
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            BabySecondaryButton(
                                                text = "取消",
                                                onClick = { showAddSub = null; newSubName = "" },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                } else {
                                    BabySecondaryButton(
                                        text = "添加子分类",
                                        onClick = { showAddSub = cat.id },
                                        modifier = Modifier.fillMaxWidth(),
                                        leadingIcon = Icons.Default.Add
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showAdd) {
                BabyContentCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BabyGlassTextField(
                            value = newLabel, onValueChange = { newLabel = it },
                            label = "分类名称", modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            presetColors.forEach { c ->
                                Box(
                                    Modifier
                                        .size(if (newColor == c) 34.dp else 30.dp)
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(Color(c))
                                        .clickable { newColor = c },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (newColor == c) {
                                        Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BabyPrimaryButton(
                                text = "添加",
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
                                modifier = Modifier.weight(1f)
                            )
                            BabySecondaryButton(text = "取消", onClick = { showAdd = false }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                BabySecondaryButton(
                    text = "添加自定义分类",
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Default.Add
                )
            }
        }
    }

    // Edit sub-category dialog
    editingSub?.let { (catId, sub) ->
        BabyGlassAlertDialog(
            onDismissRequest = { editingSub = null },
            title = { Text("编辑子分类", fontWeight = FontWeight.Bold) },
            text = {
                BabyGlassTextField(
                    value = editSubName, onValueChange = { editSubName = it },
                    label = "名称", singleLine = true
                )
            },
            confirmButton = {
                BabyPrimaryButton(
                    text = "保存",
                    onClick = {
                        if (editSubName.isNotBlank()) {
                            DataManager.updateCategory(catId) {
                                val updatedChildren = children.map {
                                    if (it.id == sub.id) it.copy(name = editSubName.trim()) else it
                                }
                                copy(children = updatedChildren)
                            }
                            editingSub = null
                        }
                    }
                )
            },
            dismissButton = { BabySecondaryButton(text = "取消", onClick = { editingSub = null }) }
        )
    }
}@OptIn(ExperimentalMaterial3Api::class)
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
    var dataPath by remember { mutableStateOf(currentConfig?.dataPath ?: "/babybuddy/data") }
    var showPassword by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var dirContents by remember { mutableStateOf<List<String>>(emptyList()) }
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f

    BabyGlassModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp)
                .padding(bottom = 56.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("WebDAV 配置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            MiuixSheetCard(title = "连接信息") {
                BabyGlassTextField(
                    value = url, onValueChange = { url = it },
                    label = "服务器地址",
                    placeholder = "https://dav.example.com",
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                BabyGlassTextField(
                    value = username, onValueChange = { username = it },
                    label = "用户名",
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                BabyGlassTextField(
                    value = password, onValueChange = { password = it },
                    label = "密码",
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    }
                )
                BabyGlassTextField(
                    value = dataPath, onValueChange = { dataPath = it },
                    label = "数据路径",
                    placeholder = "/babybuddy/data",
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }

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
                BabySecondaryButton(
                    text = if (isTesting) "测试中" else "测试连接",
                    onClick = {
                        if (url.isNotBlank()) {
                            isTesting = true
                            testResult = null
                            dirContents = emptyList()
                            val config = WebDavManager.WebDavConfig(url.trim(), username, password, dataPath.trim())
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
                    leadingIcon = Icons.Default.NetworkCheck
                )
                BabyPrimaryButton(
                    text = "保存",
                    onClick = {
                        if (url.isNotBlank()) {
                            onSave(WebDavManager.WebDavConfig(url.trim(), username, password, dataPath.trim()))
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            if (currentConfig != null) {
                BabyDangerButton(
                    text = "清除 WebDAV 配置",
                    onClick = { onClear(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudStorageConfigSheet(
    currentConfig: CloudStorageConfig,
    onDismiss: () -> Unit,
    onSave: (CloudStorageConfig) -> Unit
) {
    var protocol by remember { mutableStateOf(currentConfig.protocol) }
    var host by remember { mutableStateOf(currentConfig.host) }
    var port by remember { mutableStateOf(currentConfig.port.toString()) }
    var username by remember { mutableStateOf(currentConfig.username) }
    var password by remember { mutableStateOf(currentConfig.password) }
    var webdavPath by remember { mutableStateOf(currentConfig.webdavPath) }
    var smbShare by remember { mutableStateOf(currentConfig.smbShare) }
    var smbDomain by remember { mutableStateOf(currentConfig.smbDomain) }
    var ftpPath by remember { mutableStateOf(currentConfig.ftpPath) }
    var showPassword by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f

    BabyGlassModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp)
                .padding(bottom = 56.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CloudQueue, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("存储配置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "NAS / WebDAV 会作为照片和视频的远端备份",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            MiuixSheetCard(title = "存储协议") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StorageProtocol.entries.forEach { proto ->
                        BabyGlassChip(
                            label = proto.name,
                            selected = protocol == proto,
                            onClick = { protocol = proto },
                            modifier = Modifier.weight(1f),
                            accent = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            MiuixSheetCard(title = "连接信息") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BabyGlassTextField(
                        value = host, onValueChange = { host = it },
                        label = "主机",
                        modifier = Modifier.weight(2f), singleLine = true
                    )
                    BabyGlassTextField(
                        value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = "端口",
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                }
                BabyGlassTextField(
                    value = username, onValueChange = { username = it },
                    label = "用户名",
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                BabyGlassTextField(
                    value = password, onValueChange = { password = it },
                    label = "密码",
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    }
                )
            }

            when (protocol) {
                StorageProtocol.WEBDAV -> {
                    MiuixSheetCard(title = "WebDAV 路径") {
                        BabyGlassTextField(
                            value = webdavPath, onValueChange = { webdavPath = it },
                            label = "存储路径",
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                    }
                }
                StorageProtocol.SMB -> {
                    MiuixSheetCard(title = "SMB 共享") {
                        BabyGlassTextField(
                            value = smbShare, onValueChange = { smbShare = it },
                            label = "共享名",
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        BabyGlassTextField(
                            value = smbDomain, onValueChange = { smbDomain = it },
                            label = "域名（可选）",
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                    }
                }
                StorageProtocol.FTP -> {
                    MiuixSheetCard(title = "FTP 路径") {
                        BabyGlassTextField(
                            value = ftpPath, onValueChange = { ftpPath = it },
                            label = "远程路径",
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                    }
                }
            }

            testResult?.let {
                val success = it.contains("成功") || it.contains("通过")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background((if (success) Color(0xFF4CAF50) else Color(0xFFEF5350)).copy(alpha = 0.10f))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (success) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        null, Modifier.size(18.dp),
                        tint = if (success) Color(0xFF4CAF50) else Color(0xFFEF5350)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (success) Color(0xFF4CAF50) else Color(0xFFEF5350),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BabySecondaryButton(
                    text = if (isTesting) "测试中" else "测试连接",
                    onClick = {
                        if (host.isNotBlank()) {
                            isTesting = true
                            testResult = null
                            val config = CloudStorageConfig(
                                protocol = protocol, host = host.trim(),
                                port = port.toIntOrNull() ?: 5005, username = username,
                                password = password, webdavPath = webdavPath.trim(),
                                smbShare = smbShare.trim(), smbDomain = smbDomain.trim(),
                                ftpPath = ftpPath.trim()
                            )
                            scope.launch {
                                val result = CloudManager.testConnection(config)
                                isTesting = false
                                testResult = result.fold(
                                    onSuccess = { ok ->
                                        if (ok) "连接成功，读写验证通过" else "连接失败：服务器未通过验证"
                                    },
                                    onFailure = { "连接失败: ${it.message}" }
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isTesting,
                    leadingIcon = Icons.Default.NetworkCheck
                )
                BabyPrimaryButton(
                    text = "保存",
                    onClick = {
                        if (host.isNotBlank()) {
                            onSave(CloudStorageConfig(
                                protocol = protocol, host = host.trim(),
                                port = port.toIntOrNull() ?: 5005, username = username,
                                password = password, webdavPath = webdavPath.trim(),
                                smbShare = smbShare.trim(), smbDomain = smbDomain.trim(),
                                ftpPath = ftpPath.trim()
                            ))
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
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
    BabyDateWheelDialog(
        initialDate = initialDate,
        onDateSelected = onDateSelected,
        onDismiss = onDismiss
    )
}
