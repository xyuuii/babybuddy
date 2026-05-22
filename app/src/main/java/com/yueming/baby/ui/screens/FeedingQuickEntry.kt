package com.yueming.baby.ui.screens

import com.yueming.baby.data.FeedingRecord

internal fun feedingQuickEntryUnit(type: String): String = when (type) {
    "breast" -> "分钟"
    "formula", "water" -> "ml"
    else -> ""
}

internal fun feedingQuickEntryPresets(type: String): List<Int> = when (type) {
    "breast" -> listOf(5, 10, 15, 20)
    "formula" -> listOf(60, 90, 120, 150)
    "water" -> listOf(20, 30, 50, 100)
    else -> emptyList()
}

internal fun feedingQuickEntryDefaultValue(type: String): String = when (type) {
    "breast" -> "10"
    "formula" -> "120"
    "water" -> "30"
    else -> ""
}

internal fun feedingQuickEntryNeedsNumber(type: String): Boolean =
    type == "breast" || type == "formula" || type == "water"

internal fun sanitizeFeedingQuickEntryNumber(value: String): String =
    value.filter { it.isDigit() }.take(4)

internal fun canSaveFeedingQuickEntry(type: String, numberValue: String): Boolean =
    !feedingQuickEntryNeedsNumber(type) || (numberValue.toIntOrNull() ?: 0) > 0

internal fun createQuickFeedingRecord(
    type: String,
    numberValue: String,
    notes: String,
    timestamp: Long = System.currentTimeMillis(),
    id: String
): FeedingRecord {
    val numericValue = numberValue.toIntOrNull() ?: 0
    return FeedingRecord(
        id = id,
        timestamp = timestamp,
        type = type,
        volumeMl = if (type == "formula" || type == "water") numericValue else 0,
        durationMin = if (type == "breast") numericValue else 0,
        notes = notes.trim()
    )
}

internal fun feedingRecordDetail(record: FeedingRecord): String {
    val parts = buildList {
        if (record.volumeMl > 0) add("${record.volumeMl} ml")
        if (record.durationMin > 0) add("${record.durationMin} 分钟")
        if (record.notes.isNotBlank()) add(record.notes.trim())
    }
    return parts.joinToString(" · ")
}
