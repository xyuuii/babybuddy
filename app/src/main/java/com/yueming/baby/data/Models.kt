package com.yueming.baby.data

import androidx.compose.ui.graphics.Color

enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class BabyInfo(
    val id: String = "",
    val name: String = "小月亮",
    val nickname: String = "月月",
    val birthDate: String = "2025-01-15",
    val gender: String = "girl",
    val avatar: String? = null
)

data class TimelineRecord(
    val id: String,
    val babyId: String = "",
    val date: String,
    val title: String,
    val description: String,
    val category: String,
    val tags: List<String> = emptyList(),
    val photos: List<String> = emptyList(),
    val videos: List<String> = emptyList()
)

data class PhotoEntry(
    val id: String,
    val babyId: String = "",
    val url: String,
    val caption: String,
    val date: String,
    val timelineRecordId: String? = null,
    val tags: List<String> = emptyList()
)

data class CategoryConfig(
    val id: String,
    val label: String,
    val icon: String,
    val color: Long,
    val isDefault: Boolean
) {
    fun colorValue(): Color = Color(color)
}

val DEFAULT_CATEGORIES = listOf(
    CategoryConfig("milestone", "成长里程碑", "Star", 0xFFf6ba6d, true),
    CategoryConfig("health", "健康记录", "Heart", 0xFFf8c8d8, true),
    CategoryConfig("feeding", "饮食喂养", "Utensils", 0xFFa5d8dd, true),
    CategoryConfig("sleep", "睡眠记录", "Moon", 0xFFc4b5fd, true),
    CategoryConfig("play", "游戏玩耍", "Gamepad2", 0xFFfde68a, true),
    CategoryConfig("growth", "生长发育", "Ruler", 0xFF86efac, true),
    CategoryConfig("other", "其他", "FileText", 0xFFe5e7eb, true)
)

fun getCategoryConfig(id: String, customs: List<CategoryConfig> = emptyList()): CategoryConfig? {
    val all = DEFAULT_CATEGORIES + customs.filter { !it.isDefault }
    return all.find { it.id == id }
}

fun getAllCategories(customs: List<CategoryConfig> = emptyList()): List<CategoryConfig> {
    return DEFAULT_CATEGORIES + customs.filter { !it.isDefault }
}

data class Milestone(
    val ageMonths: Int,
    val category: String,
    val title: String,
    val description: String,
    val done: Boolean = false
)

data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long
)

data class AIConfig(
    val apiBaseUrl: String = "https://api.deepseek.com",
    val apiKey: String = "",
    val model: String = "deepseek-chat"
)

data class AIModel(
    val id: String,
    val name: String,
    val provider: String,
    val apiBase: String
)

val AI_MODELS = listOf(
    AIModel("deepseek-v4-pro", "DeepSeek-V4 Pro", "DeepSeek", "https://api.deepseek.com"),
    AIModel("deepseek-v4-flash", "DeepSeek-V4 Flash", "DeepSeek", "https://api.deepseek.com"),
    AIModel("kimi-k2.6", "Kimi K2.6", "Moonshot", "https://api.moonshot.cn/v1"),
    AIModel("minimax-m2.7", "MiniMax M2.7", "MiniMax", "https://api.minimax.chat/v1"),
    AIModel("glm-5", "GLM-5", "ZhipuAI", "https://open.bigmodel.cn/api/paas/v4"),
    AIModel("qwen3.6", "Qwen3.6", "Alibaba", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    AIModel("qwen3-max", "Qwen3 Max", "Alibaba", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    AIModel("gpt-4o", "GPT-4o", "OpenAI", "https://api.openai.com"),
    AIModel("gpt-4o-mini", "GPT-4o Mini", "OpenAI", "https://api.openai.com"),
    AIModel("claude-sonnet-4-20250514", "Claude Sonnet 4", "Anthropic", "https://api.anthropic.com")
)

data class MilkBrand(
    val id: String,
    val name: String,
    val brand: String,
    val stages: List<String>,
    val origin: String,
    val type: String,
    val notes: String
)

val MILK_BRANDS = listOf(
    MilkBrand("aptamil", "爱他美卓萃", "Aptamil",
        listOf("1段 (0-6月)", "2段 (6-12月)", "3段 (12-36月)"),
        "德国/荷兰", "cow", "含GOS/FOS益生元组合，接近母乳配方。适合大多数宝宝。"),
    MilkBrand("royal-friso", "皇家美素佳儿", "Friso",
        listOf("1段 (0-6月)", "2段 (6-12月)", "3段 (12-36月)"),
        "荷兰", "cow", "含天然乳脂+乳铁蛋白，增强免疫力。奶源来自荷兰自家牧场。"),
    MilkBrand("wyeth-illuma", "惠氏启赋", "Wyeth",
        listOf("1段 (0-6月)", "2段 (6-12月)", "3段 (12-36月)", "4段 (3-7岁)"),
        "爱尔兰", "cow", "含OPO结构脂，模拟母乳脂肪结构，促进钙吸收。高端系列。"),
    MilkBrand("kabrita", "佳贝艾特", "Kabrita",
        listOf("1段 (0-6月)", "2段 (6-12月)", "3段 (12-36月)"),
        "荷兰", "goat", "100%纯羊乳蛋白，分子小易吸收。适合牛奶蛋白过敏宝宝。")
)
