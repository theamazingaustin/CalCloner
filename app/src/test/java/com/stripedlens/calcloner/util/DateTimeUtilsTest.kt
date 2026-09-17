package com.stripedlens.calcloner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateTimeUtilsTest {

    @Test
    fun testFormatInterval() {
        assertEquals("Never (Manual Only)", DateTimeUtils.formatInterval(0))
        assertEquals("Never (Manual Only)", DateTimeUtils.formatInterval(-1))
        assertEquals("15 minutes", DateTimeUtils.formatInterval(15))
        assertEquals("45 minutes", DateTimeUtils.formatInterval(45))
        assertEquals("1 hour", DateTimeUtils.formatInterval(60))
        assertEquals("2 hours", DateTimeUtils.formatInterval(120))
        assertEquals("1 day(s)", DateTimeUtils.formatInterval(1440))
        assertEquals("2 day(s)", DateTimeUtils.formatInterval(2880))
    }

    @Test
    fun testFormatDays() {
        assertEquals("7 days (1 week)", DateTimeUtils.formatDays(7))
        assertEquals("14 days (2 weeks)", DateTimeUtils.formatDays(14))
        assertEquals("30 days (1 month)", DateTimeUtils.formatDays(30))
        assertEquals("60 days (2 months)", DateTimeUtils.formatDays(60))
        assertEquals("90 days (3 months)", DateTimeUtils.formatDays(90))
        assertEquals("180 days (6 months)", DateTimeUtils.formatDays(180))
        assertEquals("365 days (1 year)", DateTimeUtils.formatDays(365))
        assertEquals("730 days (2 years)", DateTimeUtils.formatDays(730))
        assertEquals("45 days", DateTimeUtils.formatDays(45))
    }

    @Test
    fun testFormatLastSync() {
        assertEquals("Never", DateTimeUtils.formatLastSync(null))
        assertEquals("Never", DateTimeUtils.formatLastSync(0L))
        assertEquals("Never", DateTimeUtils.formatLastSync(-100L))

        val now = System.currentTimeMillis()
        val result = DateTimeUtils.formatLastSync(now)
        assertTrue(result.startsWith("Today at "))
    }

    @Test
    fun testFormatSyncSummaryZeroChanges() {
        val summary = DateTimeUtils.formatSyncSummary(0, 0, 0)
        assertEquals("Sync complete: 0 changes.", summary)

        val summaryPairs = DateTimeUtils.formatSyncSummary(0, 0, 0, pairCount = 3)
        assertEquals("Sync complete: 0 changes across 3 pair(s).", summaryPairs)
    }

    @Test
    fun testFormatSyncSummaryWithChanges() {
        val summary1 = DateTimeUtils.formatSyncSummary(
            inserted = 5,
            updated = 2,
            deleted = 1
        )
        assertEquals("Sync complete: 5 added, 2 updated, 1 removed.", summary1)

        val summary2 = DateTimeUtils.formatSyncSummary(
            inserted = 3,
            updated = 0,
            deleted = 0,
            pairCount = 2
        )
        assertEquals("Sync complete (2 pairs): 3 added.", summary2)

        val summary3 = DateTimeUtils.formatSyncSummary(
            inserted = 0,
            updated = 4,
            deleted = 0,
            prefix = "Manual sync"
        )
        assertEquals("Manual sync: 4 updated.", summary3)
    }
}
