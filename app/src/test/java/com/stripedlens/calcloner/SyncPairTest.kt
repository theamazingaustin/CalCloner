package com.stripedlens.calcloner

import com.stripedlens.calcloner.ui.components.checkHasCycle
import org.junit.Assert.*
import org.junit.Test

class SyncPairTest {

    @Test
    fun testSyncPairJsonSerializationRoundtrip() {
        val original = listOf(
            SyncPair(
                id = "pair-1",
                fromCalendarId = 101L,
                fromCalendarName = "Personal",
                toCalendarId = 202L,
                toCalendarName = "Work Mirror",
                daysPast = 14,
                daysFuture = 60,
                isEnabled = true,
                lastSyncTime = 1700000000000L,
                lastSyncStatus = "Sync complete: 5 added."
            ),
            SyncPair(
                id = "pair-2",
                fromCalendarId = 303L,
                fromCalendarName = "Side Project",
                toCalendarId = 202L,
                toCalendarName = "Work Mirror",
                daysPast = 30,
                daysFuture = 30,
                isEnabled = false,
                lastSyncTime = null,
                lastSyncStatus = null
            )
        )

        val jsonString = SyncPair.listToJsonString(original)
        assertNotNull(jsonString)
        assertTrue(jsonString.contains("pair-1"))
        assertTrue(jsonString.contains("pair-2"))

        val deserialized = SyncPair.listFromJsonString(jsonString)
        assertEquals(2, deserialized.size)

        assertEquals("pair-1", deserialized[0].id)
        assertEquals(101L, deserialized[0].fromCalendarId)
        assertEquals("Personal", deserialized[0].fromCalendarName)
        assertEquals(202L, deserialized[0].toCalendarId)
        assertEquals("Work Mirror", deserialized[0].toCalendarName)
        assertEquals(14, deserialized[0].daysPast)
        assertEquals(60, deserialized[0].daysFuture)
        assertTrue(deserialized[0].isEnabled)
        assertEquals(1700000000000L, deserialized[0].lastSyncTime)
        assertEquals("Sync complete: 5 added.", deserialized[0].lastSyncStatus)

        assertEquals("pair-2", deserialized[1].id)
        assertEquals(303L, deserialized[1].fromCalendarId)
        assertEquals(202L, deserialized[1].toCalendarId)
        assertEquals(30, deserialized[1].daysPast)
        assertEquals(30, deserialized[1].daysFuture)
        assertFalse(deserialized[1].isEnabled)
        assertNull(deserialized[1].lastSyncTime)
        assertNull(deserialized[1].lastSyncStatus)
    }

    @Test
    fun testDirectSelfSyncBlocked() {
        val pairs = emptyList<SyncPair>()
        // A -> A is blocked
        assertTrue(checkHasCycle(pairs, proposedFromId = 1L, proposedToId = 1L))
    }

    @Test
    fun testTwoNodeCycleDetected() {
        val existing = listOf(
            SyncPair(
                id = "p1",
                fromCalendarId = 1L,
                fromCalendarName = "A",
                toCalendarId = 2L,
                toCalendarName = "B"
            )
        )

        // Adding B -> A creates a cycle: 1 -> 2 -> 1
        assertTrue(checkHasCycle(existing, proposedFromId = 2L, proposedToId = 1L))

        // Adding A -> C does NOT create a cycle
        assertFalse(checkHasCycle(existing, proposedFromId = 1L, proposedToId = 3L))

        // Adding C -> D does NOT create a cycle
        assertFalse(checkHasCycle(existing, proposedFromId = 3L, proposedToId = 4L))
    }

    @Test
    fun testThreeNodeCycleDetected() {
        val existing = listOf(
            SyncPair(
                id = "p1",
                fromCalendarId = 1L,
                fromCalendarName = "A",
                toCalendarId = 2L,
                toCalendarName = "B"
            ),
            SyncPair(
                id = "p2",
                fromCalendarId = 2L,
                fromCalendarName = "B",
                toCalendarId = 3L,
                toCalendarName = "C"
            )
        )

        // Adding C -> A creates a 3-node cycle: A -> B -> C -> A
        assertTrue(checkHasCycle(existing, proposedFromId = 3L, proposedToId = 1L))

        // Adding C -> D does not create a cycle
        assertFalse(checkHasCycle(existing, proposedFromId = 3L, proposedToId = 4L))

        // Adding D -> A does not create a cycle (D -> A -> B -> C is a DAG without loop)
        assertFalse(checkHasCycle(existing, proposedFromId = 4L, proposedToId = 1L))
    }

    @Test
    fun testMultiSourceToSingleTargetAllowed() {
        // Personal (1) -> Work (3)
        // SideHustle (2) -> Work (3)
        // Valid DAG (In-degree of Work is 2, no cycles)
        val existing = listOf(
            SyncPair(
                id = "p1",
                fromCalendarId = 1L,
                fromCalendarName = "Personal",
                toCalendarId = 3L,
                toCalendarName = "Work"
            )
        )

        assertFalse(checkHasCycle(existing, proposedFromId = 2L, proposedToId = 3L))
    }

    @Test
    fun testTrackingTagRegexCompatibility() {
        val tagRegex = Regex("""\[CalClone(?:r)?-ID:\s*(?:([a-zA-Z0-9_-]+):)?(\d+)\]""")

        // Modern namespaced tag
        val modernMatch = tagRegex.find("Meeting notes [CalCloner-ID: pair_abc123:42] see you there")
        assertNotNull(modernMatch)
        assertEquals("pair_abc123", modernMatch!!.groupValues[1])
        assertEquals("42", modernMatch.groupValues[2])

        // Legacy CalCloner tag
        val legacyClonerMatch = tagRegex.find("Event details [CalCloner-ID: 99] more text")
        assertNotNull(legacyClonerMatch)
        assertEquals("", legacyClonerMatch!!.groupValues[1])
        assertEquals("99", legacyClonerMatch.groupValues[2])

        // Legacy CalClone tag
        val legacyCloneMatch = tagRegex.find("Event details [CalClone-ID: 105]")
        assertNotNull(legacyCloneMatch)
        assertEquals("", legacyCloneMatch!!.groupValues[1])
        assertEquals("105", legacyCloneMatch.groupValues[2])
    }
}
