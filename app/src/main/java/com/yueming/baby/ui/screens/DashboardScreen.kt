package com.yueming.baby.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yueming.baby.data.*

@Composable
fun DashboardScreen() {
    val babyInfo by DataManager.babyInfo.collectAsState()
    val timeline by DataManager.timeline.collectAsState()
    val photos by DataManager.photos.collectAsState()

    val ageMonths = DataManager.getAgeInMonths(babyInfo.birthDate)
    val ageDays = DataManager.getAgeInDays(babyInfo.birthDate)
    val recentRecords = DataManager.getRecentRecords(3)
    val recentPhotos = photos.sortedByDescending { it.date }.take(4)
    val milestoneCount = DataManager.getMilestoneCount()

    val tip = TIPS.find { t -> ageMonths >= t.months.first && ageMonths <= t.months.second } ?: TIPS.last()

    val upcomingMilestones = remember(ageMonths) { getMilestonesForAge(ageMonths) }

    val categoryStats = remember(timeline) {
        getAllCategories().map { cat ->
            cat to timeline.count { it.category == cat.id }
        }.filter { it.second > 0 }.sortedByDescending { it.second }
    }
    val maxCount = categoryStats.maxOfOrNull { it.second } ?: 1

    val growthData = listOf(
        "出生" to 50f to 0xFFf8c8d8,
        "3月" to 62f to 0xFFf6ba6d,
        "6月" to 68f to 0xFFa5d8dd,
        "9月" to 73f to 0xFFc4b5fd,
        "现在" to 78f to 0xFF86efac
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
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
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null,
                            modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${babyInfo.nickname}今天 ${ageDays} 天大",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF8C8D8)),
                    contentAlignment = Alignment.Center
                ) {
                    if (babyInfo.avatar != null) {
                        AsyncImage(model = babyInfo.avatar, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }

        // 2x2 Stats cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(modifier = Modifier.weight(1f), icon = {
                    Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp),
                        tint = Color(0xFFEC407A))
                }, value = "$ageMonths", label = "月龄")
                StatCard(modifier = Modifier.weight(1f), icon = {
                    Icon(Icons.Default.Star, null, Modifier.size(18.dp),
                        tint = Color(0xFFF6BA6D))
                }, value = "$milestoneCount", label = "里程碑")
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(modifier = Modifier.weight(1f), icon = {
                    Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp),
                        tint = Color(0xFF64B5F6))
                }, value = "${photos.size}", label = "照片")
                StatCard(modifier = Modifier.weight(1f), icon = {
                    Icon(Icons.Default.TrendingUp, null, Modifier.size(18.dp),
                        tint = Color(0xFFAB47BC))
                }, value = "${timeline.size}", label = "记录")
            }
        }

        // Upcoming milestones
        if (upcomingMilestones.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Flag, null, Modifier.size(16.dp),
                                tint = Color(0xFFFF9800))
                            Spacer(Modifier.width(6.dp))
                            Text("即将到来的里程碑", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        upcomingMilestones.take(3).forEach { m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(getMilestoneIcon(m.category), fontSize = 18.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(m.title, style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold)
                                    Text(m.description, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Text("${m.ageMonths}月龄",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Growth chart
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TrendingUp, null, Modifier.size(16.dp),
                            tint = Color(0xFF4CAF50))
                        Spacer(Modifier.width(6.dp))
                        Text("身长趋势 (cm)", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
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
                                Text("${value.toInt()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(height.dp)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, Modifier.size(16.dp),
                        tint = Color(0xFFFFC107))
                    Spacer(Modifier.width(6.dp))
                    Text("最近记录", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
        if (recentRecords.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有成长记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            recentRecords.forEach { record ->
                item {
                    val catColor = Color(getCategoryConfig(record.category)?.color ?: 0xFFe5e7eb)
                    Card(shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Row(Modifier.padding(12.dp)) {
                            Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(catColor))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(record.title, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(2.dp))
                                Text(record.date, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (record.description.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(record.description, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (record.tags.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        record.tags.take(3).forEach { tag ->
                                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFF8C8D8).copy(alpha = 0.4f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                Text(tag, style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFC2185B), fontSize = 10.sp)
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
        if (recentPhotos.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhotoLibrary, null, Modifier.size(16.dp),
                            tint = Color(0xFF64B5F6))
                        Spacer(Modifier.width(6.dp))
                        Text("最新照片", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    recentPhotos.forEach { photo ->
                        Card(
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            AsyncImage(
                                model = photo.url,
                                contentDescription = photo.caption,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        // Milk brand quick reference
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, null, Modifier.size(16.dp),
                            tint = Color(0xFFEC407A))
                        Spacer(Modifier.width(6.dp))
                        Text("奶粉品牌速查", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            Triple("爱他美卓萃", "1-3段", "德/荷"),
                            Triple("皇家美素佳儿", "1-3段", "荷兰"),
                            Triple("惠氏启赋", "1-4段", "爱尔兰"),
                            Triple("佳贝艾特", "1-3段(羊奶)", "荷兰")
                        ).forEach { (name, stages, origin) ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(name, style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium)
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
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF8C8D8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Favorite, null, Modifier.size(14.dp),
                            tint = Color(0xFFEC407A))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("育儿小贴士", style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${babyInfo.nickname}现在 ${ageMonths} 个月。${tip.text}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    value: String,
    label: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                icon()
                Text(value, style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
