package com.stripedlens.calcloner

import com.stripedlens.calcloner.domain.routing.CycleDetector
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
        assertTrue(CycleDetector.hasCycle(pairs, proposedFromId = 1L, proposedToId = 1L))
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
        assertTrue(CycleDetector.hasCycle(existing, proposedFromId = 2L, proposedToId = 1L))

        // Adding A -> C does NOT create a cycle
        assertFalse(CycleDetector.hasCycle(existing, proposedFromId = 1L, proposedToId = 3L))

        // Adding C -> D does NOT create a cycle
        assertFalse(CycleDetector.hasCycle(existing, proposedFromId = 3L, proposedToId = 4L))
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
        assertTrue(CycleDetector.hasCycle(existing, proposedFromId = 3L, proposedToId = 1L))

        // Adding C -> D does not create a cycle
        assertFalse(CycleDetector.hasCycle(existing, proposedFromId = 3L, proposedToId = 4L))

        // Adding D -> A does not create a cycle (D -> A -> B -> C is a DAG without loop)
        assertFalse(CycleDetector.hasCycle(existing, proposedFromId = 4L, proposedToId = 1L))
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

        assertFalse(CycleDetector.hasCycle(existing, proposedFromId = 2L, proposedToId = 3L))
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

    @Test
    fun testGranularFieldCustomizationSerialization() {
        val pair = SyncPair(
            id = "pair-custom",
            fromCalendarId = 1L,
            fromCalendarName = "Personal",
            toCalendarId = 2L,
            toCalendarName = "Work",
            syncTitle = true,
            titlePrefix = "[Home] ",
            titleSuffix = " (Private)",
            syncDescription = false,
            customDescription = "Managed calendar entry",
            descriptionPrefix = "Details:\n",
            descriptionSuffix = "\nStrictly confidential",
            syncLocation = false,
            customLocation = "Remote / Home Office",
            syncReminders = true,
            syncAvailability = false,
            customAvailability = 1, // AVAILABILITY_BUSY
            syncStatus = false,
            customStatus = 0 // STATUS_TENTATIVE
        )

        val json = pair.toJson().toString()
        val restored = SyncPair.fromJson(org.json.JSONObject(json))

        assertEquals("pair-custom", restored.id)
        assertTrue(restored.syncTitle)
        assertEquals("[Home] ", restored.titlePrefix)
        assertEquals(" (Private)", restored.titleSuffix)
        assertFalse(restored.syncDescription)
        assertEquals("Managed calendar entry", restored.customDescription)
        assertEquals("Details:\n", restored.descriptionPrefix)
        assertEquals("\nStrictly confidential", restored.descriptionSuffix)
        assertFalse(restored.syncLocation)
        assertEquals("Remote / Home Office", restored.customLocation)
        assertTrue(restored.syncReminders)
        assertFalse(restored.syncAvailability)
        assertEquals(1, restored.customAvailability)
        assertFalse(restored.syncStatus)
        assertEquals(0, restored.customStatus)
    }

    @Test
    fun testFieldValidationError() {
        // Valid default configuration
        val validPair = SyncPair(
            id = "p1",
            fromCalendarId = 1L,
            fromCalendarName = "A",
            toCalendarId = 2L,
            toCalendarName = "B",
            syncTitle = true
        )
        assertNull(validPair.fieldValidationError)
        assertTrue(validPair.isConfigValidForSync)
        assertFalse(validPair.hasSyncError)

        // Invalid: Title mirroring turned off without a fallback custom title
        val invalidPairEmptyTitle = validPair.copy(
            syncTitle = false,
            customTitle = ""
        )
        assertNotNull(invalidPairEmptyTitle.fieldValidationError)
        assertFalse(invalidPairEmptyTitle.isConfigValidForSync)
        assertTrue(invalidPairEmptyTitle.hasSyncError)

        val invalidPairBlankTitle = validPair.copy(
            syncTitle = false,
            customTitle = "   "
        )
        assertNotNull(invalidPairBlankTitle.fieldValidationError)
        assertFalse(invalidPairBlankTitle.isConfigValidForSync)
        assertTrue(invalidPairBlankTitle.hasSyncError)

        val invalidPairNullTitle = validPair.copy(
            syncTitle = false,
            customTitle = null
        )
        assertNotNull(invalidPairNullTitle.fieldValidationError)
        assertFalse(invalidPairNullTitle.isConfigValidForSync)
        assertTrue(invalidPairNullTitle.hasSyncError)

        // Fixed: Title mirroring turned off but custom title provided
        val fixedPair = validPair.copy(
            syncTitle = false,
            customTitle = "Busy"
        )
        assertNull(fixedPair.fieldValidationError)
        assertTrue(fixedPair.isConfigValidForSync)
        assertFalse(fixedPair.hasSyncError)
    }

    @Test
    fun testSyncAttendeesSerializationRoundtrip() {
        val pairWithAttendees = SyncPair(
            id = "pair-att",
            fromCalendarId = 1L,
            fromCalendarName = "Source",
            toCalendarId = 2L,
            toCalendarName = "Target",
            syncAttendees = true,
            attendeesPlacement = "START"
        )

        val json = pairWithAttendees.toJson().toString()
        val restored = SyncPair.fromJson(org.json.JSONObject(json))

        assertTrue(restored.syncAttendees)
        assertEquals("START", restored.attendeesPlacement)

        // Test default values
        val defaultPair = SyncPair.fromJson(org.json.JSONObject("{}").apply {
            put("fromCalendarId", 1L)
            put("toCalendarId", 2L)
        })
        assertFalse(defaultPair.syncAttendees)
        assertEquals("END", defaultPair.attendeesPlacement)
    }

    @Test
    fun testFormatAttendeesBlock() {
        val attendees = listOf(
            com.stripedlens.calcloner.SyncAttendee(
                name = "Alice Smith",
                email = "alice@example.com",
                status = 1 // ATTENDEE_STATUS_ACCEPTED
            ),
            com.stripedlens.calcloner.SyncAttendee(
                name = null,
                email = "bob@example.com",
                status = 4 // ATTENDEE_STATUS_TENTATIVE
            ),
            com.stripedlens.calcloner.SyncAttendee(
                name = "Charlie Brown",
                email = null,
                status = 2 // ATTENDEE_STATUS_DECLINED
            )
        )

        val formatted = com.stripedlens.calcloner.engine.CalendarEventWriter.formatAttendeesBlock(attendees)
        assertTrue(formatted.startsWith("Attendees:\n"))
        assertTrue(formatted.contains("• Alice Smith (Accepted)"))
        assertTrue(formatted.contains("• bob (Tentative)"))
        assertTrue(formatted.contains("• Charlie Brown (Declined)"))
    }
}
