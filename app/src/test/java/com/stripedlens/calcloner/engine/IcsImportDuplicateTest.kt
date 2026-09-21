package com.stripedlens.calcloner.engine

import com.stripedlens.calcloner.SyncEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying ICS event duplicate detection and skipping logic
 * during imports into target calendars.
 */
class IcsImportDuplicateTest {

    private fun computeDuplicateKey(title: String?, dtStart: Long, allDay: Int): String {
        return "${(title ?: "").trim()}_${dtStart}_$allDay"
    }

    @Test
    fun testIdenticalEventGeneratesMatchingDuplicateKey() {
        val event1 = SyncEvent(
            id = 1L,
            title = "Quarterly Business Review",
            dtStart = 1710000000000L,
            dtEnd = 1710003600000L,
            allDay = 0
        )
        val event2 = SyncEvent(
            id = 99L,
            title = "Quarterly Business Review",
            dtStart = 1710000000000L,
            dtEnd = 1710003600000L,
            allDay = 0
        )

        val key1 = computeDuplicateKey(event1.title, event1.dtStart, event1.allDay)
        val key2 = computeDuplicateKey(event2.title, event2.dtStart, event2.allDay)

        assertEquals(key1, key2)
    }

    @Test
    fun testTitleWhitespaceNormalizationPreventsDuplicates() {
        val keyOriginal = computeDuplicateKey("  Team Standup  ", 1710000000000L, 0)
        val keyClean = computeDuplicateKey("Team Standup", 1710000000000L, 0)

        assertEquals("Team Standup_1710000000000_0", keyOriginal)
        assertEquals(keyOriginal, keyClean)
    }

    @Test
    fun testAllDayVsTimedEventsDoNotCollide() {
        val timestamp = 1710000000000L
        val timedKey = computeDuplicateKey("All Hands", timestamp, allDay = 0)
        val allDayKey = computeDuplicateKey("All Hands", timestamp, allDay = 1)

        assertFalse("All-day and timed events must have distinct duplicate keys", timedKey == allDayKey)
        assertEquals("All Hands_${timestamp}_0", timedKey)
        assertEquals("All Hands_${timestamp}_1", allDayKey)
    }

    @Test
    fun testNullTitleTolerant() {
        val key1 = computeDuplicateKey(null, 1710000000000L, 0)
        val key2 = computeDuplicateKey("", 1710000000000L, 0)

        assertEquals("_1710000000000_0", key1)
        assertEquals(key1, key2)
    }

    @Test
    fun testBatchDuplicateSkippingSimulation() {
        // Simulate an existing set of target calendar events
        val existingKeys = mutableSetOf(
            computeDuplicateKey("Doctor Appointment", 1710000000000L, 0),
            computeDuplicateKey("Birthday", 1710086400000L, 1)
        )

        val incomingBatch = listOf(
            // Duplicate of existing
            SyncEvent(id = 1L, title = "Doctor Appointment", dtStart = 1710000000000L, allDay = 0),
            // New unique event
            SyncEvent(id = 2L, title = "Project Sync", dtStart = 1710010000000L, allDay = 0),
            // Duplicate of existing all-day
            SyncEvent(id = 3L, title = "  Birthday  ", dtStart = 1710086400000L, allDay = 1),
            // Another unique event
            SyncEvent(id = 4L, title = "Dentist", dtStart = 1710020000000L, allDay = 0),
            // Duplicate within the batch itself
            SyncEvent(id = 5L, title = "Project Sync", dtStart = 1710010000000L, allDay = 0)
        )

        var importedCount = 0
        var skippedCount = 0
        val seenInBatch = mutableSetOf<String>()

        for (event in incomingBatch) {
            val key = computeDuplicateKey(event.title, event.dtStart, event.allDay)
            if (existingKeys.contains(key) || seenInBatch.contains(key)) {
                skippedCount++
            } else {
                importedCount++
                seenInBatch.add(key)
            }
        }

        assertEquals(2, importedCount) // Project Sync, Dentist
        assertEquals(3, skippedCount) // Doctor Appointment, Birthday, 2nd Project Sync
    }
}
