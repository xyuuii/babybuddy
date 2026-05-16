package com.yueming.baby.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

val ALL_MILESTONES = listOf(
    Milestone(0, "health", "新生儿筛查", "足底血筛查、听力筛查"),
    Milestone(1, "health", "满月体检", "体重/身长/黄疸检查"),
    Milestone(2, "health", "二月体检", "疫苗接种：脊灰疫苗第一剂"),
    Milestone(3, "health", "三月体检", "生长发育评估+五联疫苗"),
    Milestone(4, "feeding", "辅食信号", "观察宝宝是否对食物感兴趣"),
    Milestone(4, "growth", "翻身期", "宝宝开始尝试翻身，做好看护"),
    Milestone(6, "health", "六月体检", "血常规+发育筛查"),
    Milestone(6, "feeding", "开始辅食", "从单一米粉开始，逐步添加"),
    Milestone(7, "growth", "学坐期", "宝宝开始尝试独坐"),
    Milestone(8, "growth", "学爬期", "做好安全防护，清除地面危险物"),
    Milestone(9, "health", "九月体检", "发育评估+疫苗接种"),
    Milestone(10, "growth", "扶站期", "宝宝可能扶着家具站立"),
    Milestone(12, "health", "一岁体检", "全面体检+疫苗接种"),
    Milestone(12, "growth", "学走期", "鼓励宝宝迈出第一步"),
    Milestone(12, "feeding", "引入纯牛奶", "一岁后可尝试纯牛奶"),
    Milestone(15, "growth", "语言爆发期", "词汇量快速增长，多对话"),
    Milestone(18, "health", "一岁半体检", "发育筛查+DTP加强"),
    Milestone(18, "growth", "自主进食", "鼓励使用勺子自己吃饭"),
    Milestone(24, "health", "两岁体检", "全面体检+发育评估"),
    Milestone(24, "growth", "如厕训练", "观察宝宝是否准备好开始如厕训练"),
    Milestone(30, "growth", "社交发展", "鼓励与同龄小朋友互动玩耍"),
    Milestone(36, "health", "三岁体检", "入托/入园前体检")
)

fun getMilestonesForAge(months: Int): List<Milestone> {
    return ALL_MILESTONES
        .filter { it.ageMonths <= months + 1 && it.ageMonths >= months - 1 }
        .map { it.copy(done = false) }
}

fun getMilestoneIcon(category: String): ImageVector = when (category) {
    "health" -> Icons.Default.FavoriteBorder
    "feeding" -> Icons.Default.Fastfood
    "growth" -> Icons.Default.TrendingUp
    else -> Icons.Default.Star
}
