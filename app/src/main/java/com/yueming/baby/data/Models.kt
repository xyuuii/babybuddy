package com.yueming.baby.data

import androidx.compose.ui.graphics.Color

enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class BabyInfo(
    val id: String = "",
    val name: String = "",
    val nickname: String = "",
    val birthDate: String = "",
    val gender: String = "",
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
    val tags: List<String> = emptyList(),
    val thumbnailPath: String? = null,   // Local thumbnail - null means use url directly
    val mediaType: String? = null,
    val remotePath: String? = null,
    val remoteUrl: String? = null,
    val sha256: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

fun belongsToBaby(recordBabyId: String?, activeBabyId: String): Boolean {
    return activeBabyId.isBlank() || recordBabyId.isNullOrBlank() || recordBabyId == activeBabyId
}

data class CategoryConfig(
    val id: String,
    val label: String,
    val icon: String,
    val color: Long,
    val isDefault: Boolean,
    val children: List<SubCategory> = emptyList()
) {
    data class SubCategory(val id: String, val name: String)
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
    val model: String = "deepseek-v4-pro"
)

data class AIProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "默认配置",
    val apiBaseUrl: String = "https://api.deepseek.com",
    val apiKey: String = "",
    val model: String = "deepseek-v4-pro",
    val systemPrompt: String = "你是一个专业的育儿助手，基于宝宝数据提供科学、温暖的育儿建议。",
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 32768,
    val isActive: Boolean = false
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

data class FeedingRecord(
    val id: String,
    val babyId: String = "",
    val timestamp: Long,
    val type: String,           // "breast", "formula", "supplement", "water", "snack"
    val volumeMl: Int = 0,
    val durationMin: Int = 0,
    val notes: String = ""
)

val FEEDING_TYPES = listOf(
    "breast" to "母乳",
    "formula" to "配方奶",
    "supplement" to "辅食",
    "water" to "喝水",
    "snack" to "零食"
)

data class VaccineItem(
    val id: String,
    val name: String,
    val doseNumber: Int,
    val scheduledAgeMonths: Int,
    val diseasePrevented: String,
    val description: String,
    val isRequired: Boolean = true
)

data class VaccineStatus(
    val vaccineId: String,
    val babyId: String = "",
    val administeredDate: String? = null,
    val batchNumber: String = "",
    val notes: String = ""
) {
    val isDone: Boolean get() = administeredDate != null
}

// Chinese CDC National Immunization Program (国家免疫规划疫苗)
val CHINA_VACCINE_SCHEDULE = listOf(
    // Category 1 — Free, Required (一类疫苗)
    VaccineItem("bcg", "卡介苗", 1, 0, "结核病", "出生后尽快接种，左上臂皮内注射"),
    VaccineItem("hepb-1", "乙肝疫苗", 1, 0, "乙型肝炎", "出生后24小时内接种"),
    VaccineItem("hepb-2", "乙肝疫苗", 2, 1, "乙型肝炎", "满月时接种"),
    VaccineItem("ipv-1", "脊灰灭活疫苗", 1, 2, "脊髓灰质炎", "2月龄接种，注射"),
    VaccineItem("ipv-2", "脊灰灭活疫苗", 2, 3, "脊髓灰质炎", "3月龄接种，注射"),
    VaccineItem("dtap-1", "百白破疫苗", 1, 3, "百日咳/白喉/破伤风", "3月龄接种"),
    VaccineItem("bopv-3", "脊灰减毒活疫苗", 3, 4, "脊髓灰质炎", "4月龄接种，口服"),
    VaccineItem("dtap-2", "百白破疫苗", 2, 4, "百日咳/白喉/破伤风", "4月龄接种"),
    VaccineItem("dtap-3", "百白破疫苗", 3, 5, "百日咳/白喉/破伤风", "5月龄接种"),
    VaccineItem("hepb-3", "乙肝疫苗", 3, 6, "乙型肝炎", "6月龄接种"),
    VaccineItem("mena-1", "A群流脑疫苗", 1, 6, "A群流行性脑脊髓膜炎", "6月龄接种"),
    VaccineItem("mr-1", "麻腮风疫苗", 1, 8, "麻疹/腮腺炎/风疹", "8月龄接种"),
    VaccineItem("je-1", "乙脑减毒活疫苗", 1, 8, "流行性乙型脑炎", "8月龄接种"),
    VaccineItem("mena-2", "A群流脑疫苗", 2, 9, "A群流行性脑脊髓膜炎", "9月龄接种"),
    VaccineItem("dtap-4", "百白破疫苗", 4, 18, "百日咳/白喉/破伤风", "18月龄加强"),
    VaccineItem("mr-2", "麻腮风疫苗", 2, 18, "麻疹/腮腺炎/风疹", "18月龄接种"),
    VaccineItem("hepa-1", "甲肝减毒活疫苗", 1, 18, "甲型肝炎", "18月龄接种"),
    VaccineItem("je-2", "乙脑减毒活疫苗", 2, 24, "流行性乙型脑炎", "2岁加强"),
    VaccineItem("menac-1", "A+C群流脑疫苗", 1, 36, "A+C群流行性脑脊髓膜炎", "3岁接种"),
    VaccineItem("bopv-4", "脊灰减毒活疫苗", 4, 48, "脊髓灰质炎", "4岁加强"),
    VaccineItem("menac-2", "A+C群流脑疫苗", 2, 72, "A+C群流行性脑脊髓膜炎", "6岁接种"),
    VaccineItem("dt-6", "白破疫苗", 1, 72, "白喉/破伤风", "6岁加强"),
    // Category 2 — Paid, Optional (二类疫苗)
    VaccineItem("rv5", "五价轮状病毒疫苗", 1, 2, "轮状病毒肠炎", "口服3剂，首剂6-12周", false),
    VaccineItem("pcv13-1", "13价肺炎疫苗", 1, 2, "肺炎球菌疾病", "基础免疫3剂", false),
    VaccineItem("pcv13-2", "13价肺炎疫苗", 2, 4, "肺炎球菌疾病", "基础免疫第2剂", false),
    VaccineItem("pcv13-3", "13价肺炎疫苗", 3, 6, "肺炎球菌疾病", "基础免疫第3剂", false),
    VaccineItem("pcv13-4", "13价肺炎疫苗", 4, 12, "肺炎球菌疾病", "加强免疫", false),
    VaccineItem("hib", "Hib疫苗", 1, 3, "b型流感嗜血杆菌", "可替代为五联疫苗", false),
    VaccineItem("dapt-ipv-hib", "五联疫苗", 1, 2, "百日咳/白喉/破伤风/脊灰/Hib", "替代百白破+脊灰+Hib，共4剂", false),
    VaccineItem("ev71-1", "EV71手足口疫苗", 1, 6, "手足口病(EV71型)", "6月龄起接种，共2剂", false),
    VaccineItem("ev71-2", "EV71手足口疫苗", 2, 7, "手足口病(EV71型)", "与第1剂间隔1个月", false),
    VaccineItem("var-1", "水痘疫苗", 1, 12, "水痘", "12月龄接种第1剂", false),
    VaccineItem("var-2", "水痘疫苗", 2, 48, "水痘", "4岁加强", false),
    VaccineItem("flu", "流感疫苗", 1, 6, "流行性感冒", "每年接种，6月龄起", false),
    VaccineItem("hepa-2", "甲肝灭活疫苗", 2, 24, "甲型肝炎", "自费替代方案", false)
)
