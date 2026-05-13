package com.yueming.baby.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yueming.baby.BabySwitcher
import com.yueming.baby.data.*
import java.time.LocalDate
import java.util.UUID

@Composable
fun DashboardScreen() {
    val babies by DataManager.babies.collectAsState()
    val babyInfo by DataManager.babyInfo.collectAsState()
    val timeline by DataManager.timeline.collectAsState()
    val photos by DataManager.photos.collectAsState()

    val ageMonths = DataManager.getAgeInMonths(babyInfo.birthDate)
    val ageDays = DataManager.getAgeInDays(babyInfo.birthDate)
    val recentRecords = DataManager.getRecentRecords(3)
    val milestoneCount = DataManager.getMilestoneCount()

    val tip = TIPS.find { t -> ageMonths >= t.months.first && ageMonths <= t.months.second } ?: TIPS.last()

    val context = LocalContext.current

    val upcomingMilestones = remember(ageMonths) { getMilestonesForAge(ageMonths) }

    val growthData = listOf(
        "出生" to 50f to 0xFFf8c8d8,
        "3月" to 62f to 0xFFf6ba6d,
        "6月" to 68f to 0xFFa5d8dd,
        "9月" to 73f to 0xFFc4b5fd,
        "现在" to 78f to 0xFF86efac
    )

    var showAddBaby by remember { mutableStateOf(false) }
    var showGrowthEntry by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val localPath = DataManager.copyPhotoToInternalStorage(uri)
            DataManager.updateBabyInfo(babyInfo.copy(avatar = localPath ?: uri.toString()))
        }
    }

    // Animated numbers
    val animatedAgeMonths by animateIntAsState(targetValue = ageMonths, animationSpec = tween(800))
    val animatedDays by animateIntAsState(targetValue = ageDays, animationSpec = tween(1000))
    val animatedMilestone by animateIntAsState(targetValue = milestoneCount, animationSpec = tween(800))
    val animatedTimeline by animateIntAsState(targetValue = timeline.size, animationSpec = tween(800))
    val animatedPhotos by animateIntAsState(targetValue = photos.size, animationSpec = tween(800))

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
                        AsyncImage(model = babyInfo.avatar, contentDescription = null,
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
                    icon = { Icon(Icons.Default.TrendingUp, null, Modifier.size(18.dp), tint = Color(0xFFAB47BC)) },
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
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
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
                        Icon(Icons.Default.TrendingUp, null, Modifier.size(18.dp), tint = Color(0xFF4CAF50))
                        Spacer(Modifier.width(8.dp))
                        Text("身长趋势 (cm)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(14.dp))
                    val maxHeightPx = 120f
                    Row(
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        growthData.forEach { (pair, color) ->
                            val label = pair.first
                            val value = pair.second
                            val height = (value / 100f) * maxHeightPx
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
                item {
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

        // Recent photos
        val recentPhotos = photos.sortedByDescending { it.date }.take(4)
        if (recentPhotos.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp), tint = Color(0xFF64B5F6))
                    Spacer(Modifier.width(8.dp))
                    Text("最新照片", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recentPhotos.forEach { photo ->
                        Card(
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            AsyncImage(
                                model = photo.url, contentDescription = photo.caption,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        // Milk brand quick reference
        item {
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
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            Triple("爱他美卓萃", "1-3段", "德/荷"),
                            Triple("皇家美素佳儿", "1-3段", "荷兰"),
                            Triple("惠氏启赋", "1-4段", "爱尔兰"),
                            Triple("佳贝艾特", "1-3段(羊奶)", "荷兰")
                        ).forEach { (name, stages, origin) ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text("$stages / $origin", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
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
                DataManager.addRecord(record)
                showGrowthEntry = false
            }
        )
    }
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
                OutlinedButton(onClick = {
                    val d = java.time.LocalDate.parse(birthDate)
                    android.app.DatePickerDialog(context, { _, y, m, day ->
                        birthDate = "%04d-%02d-%02d".format(y, m + 1, day)
                    }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
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
                onClick = {
                    val d = LocalDate.parse(date)
                    DatePickerDialog(context, { _, y, m, day ->
                        date = "%04d-%02d-%02d".format(y, m + 1, day)
                    }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CalendarToday, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("日期: $date", fontSize = 13.sp)
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
                            )
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
}
