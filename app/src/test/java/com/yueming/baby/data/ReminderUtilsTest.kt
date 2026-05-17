package com.yueming.baby.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderUtilsTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun upcomingRemindersSortsAndFiltersCompletedOrOtherBabies() {
        val now = LocalDateTime.of(2026, 5, 17, 12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val reminders = listOf(
            reminder(id = "later", babyId = "baby-a", dueAt = now + 3_600_000L),
            reminder(id = "done", babyId = "baby-a", dueAt = now + 1_000L, completedAt = now),
            reminder(id = "other-baby", babyId = "baby-b", dueAt = now + 2_000L),
            reminder(id = "soon", babyId = "baby-a", dueAt = now + 60_000L)
        )

        val result = upcomingReminders(
            reminders = reminders,
            activeBabyId = "baby-a",
            nowMillis = now,
            limit = 3
        )

        assertEquals(listOf("soon", "later"), result.map { it.id })
        assertFalse(result.any { it.isCompleted })
    }

    @Test
    fun defaultTwoWeekReminderUsesLocalNineAm() {
        val now = LocalDateTime.of(2026, 5, 17, 22, 45)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val dueAt = defaultReminderDueAt(nowMillis = now, zoneId = zone)
        val localDue = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(dueAt), zone)

        assertEquals(LocalDateTime.of(2026, 5, 31, 9, 0), localDue)
    }

    @Test
    fun dueTextUsesReadableRelativeDay() {
        val now = LocalDateTime.of(2026, 5, 17, 12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val tomorrow = LocalDateTime.of(2026, 5, 18, 9, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        assertEquals("明天 09:30", reminderDueText(tomorrow, nowMillis = now, zoneId = zone))
    }

    private fun reminder(
        id: String,
        babyId: String,
        dueAt: Long,
        completedAt: Long? = null
    ) = Reminder(
        id = id,
        babyId = babyId,
        title = "复查血常规",
        dueAt = dueAt,
        completedAt = completedAt
    )
}
