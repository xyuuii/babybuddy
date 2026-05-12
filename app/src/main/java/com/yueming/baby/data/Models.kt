package com.yueming.baby.data

data class BabyInfo(
    val name: String = "小月亮",
    val nickname: String = "月月",
    val birthDate: String = "2025-01-15",
    val gender: String = "girl"
)

data class TimelineRecord(
    val id: String,
    val date: String,
    val title: String,
    val description: String,
    val category: String,
    val tags: List<String>
)
