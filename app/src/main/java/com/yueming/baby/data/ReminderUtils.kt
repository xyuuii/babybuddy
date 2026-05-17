package com.yueming.baby.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private val reminderTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val reminderDateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

fun upcomingReminders(
    reminders: List<Reminder>,
    activeBabyId: String,
    nowMillis: Long = System.currentTimeMillis(),
    limit: Int = 3
): List<Reminder> {
    return reminders
        .asSequence()
        .filter { belongsToBaby(it.babyId, activeBabyId) }
        .filter { !it.isCompleted }
        .sortedWith(compareBy<Reminder> { it.dueAt }.thenBy { it.createdAt })
        .take(limit)
        .toList()
}

fun defaultReminderDueAt(
    nowMillis: Long = System.currentTimeMillis(),
    daysFromNow: Long = 14,
    hour: Int = 9,
    minute: Int = 0,
    zoneId: ZoneId = ZoneId.systemDefault()
): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    return now
        .plusDays(daysFromNow)
        .atTime(LocalTime.of(hour, minute))
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()
}

fun reminderDueText(
    dueAt: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    val due = LocalDateTime.ofInstant(Instant.ofEpochMilli(dueAt), zoneId)
    val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), due.toLocalDate())
    val time = due.format(reminderTimeFormatter)
    return when {
        days < 0 -> "已逾期 ${abs(days)}天 $time"
        days == 0L -> "今天 $time"
        days == 1L -> "明天 $time"
        days in 2L..6L -> "${days}天后 $time"
        else -> due.format(reminderDateTimeFormatter)
    }
}

fun reminderCategoryLabel(category: String): String {
    return REMINDER_CATEGORIES.firstOrNull { it.id == category }?.label
        ?: REMINDER_CATEGORIES.last().label
}

fun reminderCategoryColor(category: String): Long {
    return REMINDER_CATEGORIES.firstOrNull { it.id == category }?.color
        ?: REMINDER_CATEGORIES.last().color
}
