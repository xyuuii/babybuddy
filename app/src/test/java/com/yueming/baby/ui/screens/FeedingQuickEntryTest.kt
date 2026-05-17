package com.yueming.baby.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedingQuickEntryTest {
    @Test
    fun breastEntryUsesEnteredMinutesInsteadOfFixedDefault() {
        val record = createQuickFeedingRecord(
            type = "breast",
            numberValue = "17",
            notes = "",
            timestamp = 123L,
            id = "feed-test"
        )

        assertEquals(17, record.durationMin)
        assertEquals(0, record.volumeMl)
        assertEquals("17 分钟", feedingRecordDetail(record))
    }

    @Test
    fun formulaEntryUsesMilliliters() {
        val record = createQuickFeedingRecord(
            type = "formula",
            numberValue = "120",
            notes = "",
            timestamp = 123L,
            id = "feed-test"
        )

        assertEquals(120, record.volumeMl)
        assertEquals(0, record.durationMin)
        assertEquals("120 ml", feedingRecordDetail(record))
    }

    @Test
    fun supplementalFoodCanSaveWithoutNumberAndKeepsTrimmedNotes() {
        val record = createQuickFeedingRecord(
            type = "supplement",
            numberValue = "",
            notes = "  米粉两勺  ",
            timestamp = 123L,
            id = "feed-test"
        )

        assertTrue(canSaveFeedingQuickEntry("supplement", ""))
        assertEquals(0, record.volumeMl)
        assertEquals(0, record.durationMin)
        assertEquals("米粉两勺", feedingRecordDetail(record))
    }

    @Test
    fun numericTypesRequirePositiveNumbers() {
        assertFalse(canSaveFeedingQuickEntry("breast", ""))
        assertFalse(canSaveFeedingQuickEntry("formula", "0"))
        assertTrue(canSaveFeedingQuickEntry("water", "30"))
    }

    @Test
    fun numberInputKeepsOnlyDigitsAndLimitsLength() {
        assertEquals("1234", sanitizeFeedingQuickEntryNumber("12ml34分钟56"))
    }
}
