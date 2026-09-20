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
            customStatus = 0, // STATUS_TENTATIVE
            syncAccessLevel = false,
            customAccessLevel = 2 // ACCESS_PRIVATE
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
        assertFalse(restored.syncAccessLevel)
        assertEquals(2, restored.customAccessLevel)
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

    @Test
    fun testFilterCriteriaSerializationRoundtrip() {
        val pair = SyncPair(
            id = "filter-pair-1",
            fromCalendarId = 10L,
            fromCalendarName = "Source",
            toCalendarId = 20L,
            toCalendarName = "Target",
            filterEnabled = true,
            filterAllowBusy = false,
            filterAllowFree = true,
            filterAllowTentative = true,
            filterAllowEmpty = false,
            filterRsvpAccepted = false,
            filterRsvpTentative = true,
            filterRsvpDeclined = true,
            filterIncludeAllDay = false,
            filterEnableTimeFilter = true,
            filterFromHour = 10,
            filterFromMinute = 30,
            filterToHour = 16,
            filterToMinute = 45,
            filterActiveDays = setOf(1, 3, 5),
            filterTitleContains = "sync, meeting",
            filterTitleContainsMatchAll = true,
            filterTitleDoesNotContain = "cancel, skip",
            filterTitleDoesNotContainMatchAll = false,
            filterDescriptionContains = "internal",
            filterDescriptionContainsMatchAll = false,
            filterDescriptionDoesNotContain = "confidential",
            filterDescriptionDoesNotContainMatchAll = true
        )

        val jsonString = SyncPair.listToJsonString(listOf(pair))
        val restored = SyncPair.listFromJsonString(jsonString).first()

        assertTrue(restored.filterEnabled)
        assertFalse(restored.filterAllowBusy)
        assertTrue(restored.filterAllowFree)
        assertTrue(restored.filterAllowTentative)
        assertFalse(restored.filterAllowEmpty)
        assertFalse(restored.filterRsvpAccepted)
        assertTrue(restored.filterRsvpTentative)
        assertTrue(restored.filterRsvpDeclined)
        assertFalse(restored.filterIncludeAllDay)
        assertTrue(restored.filterEnableTimeFilter)
        assertEquals(10, restored.filterFromHour)
        assertEquals(30, restored.filterFromMinute)
        assertEquals(16, restored.filterToHour)
        assertEquals(45, restored.filterToMinute)
        assertEquals(setOf(1, 3, 5), restored.filterActiveDays)
        assertEquals("sync, meeting", restored.filterTitleContains)
        assertTrue(restored.filterTitleContainsMatchAll)
        assertEquals("cancel, skip", restored.filterTitleDoesNotContain)
        assertFalse(restored.filterTitleDoesNotContainMatchAll)
        assertEquals("internal", restored.filterDescriptionContains)
        assertFalse(restored.filterDescriptionContainsMatchAll)
        assertEquals("confidential", restored.filterDescriptionDoesNotContain)
        assertTrue(restored.filterDescriptionDoesNotContainMatchAll)
    }

    @Test
    fun testEventFilterMatchingLogic() {
        fun makeEvent(
            title: String = "Sprint Planning",
            description: String = "Weekly team sync",
            dtStart: Long = 1716195600000L, // Monday
            allDay: Int = 0,
            availability: Int? = 0,
            selfAttendeeStatus: Int? = 1
        ) = SyncEvent(
            id = 1L,
            title = title,
            description = description,
            location = "Room 101",
            dtStart = dtStart,
            dtEnd = dtStart + 3600000L,
            duration = null,
            allDay = allDay,
            timeZone = "UTC",
            rrule = null,
            availability = availability,
            selfAttendeeStatus = selfAttendeeStatus
        )

        val basePair = SyncPair(
            id = "filter-test",
            fromCalendarId = 1L,
            fromCalendarName = "Source",
            toCalendarId = 2L,
            toCalendarName = "Target",
            filterEnabled = true
        )

        // Availability filter
        val busyEvent = makeEvent(availability = 0)
        val freeEvent = makeEvent(availability = 1)
        val nullAvailEvent = makeEvent(availability = null)

        val disallowBusyPair = basePair.copy(filterAllowBusy = false, filterAllowFree = true)
        assertFalse(com.stripedlens.calcloner.engine.CalendarEventWriter.matchesFilters(busyEvent, disallowBusyPair))
        assertTrue(com.stripedlens.calcloner.engine.CalendarEventWriter.matchesFilters(freeEvent, disallowBusyPair))

        val disallowEmptyPair = basePair.copy(filterAllowEmpty = false)
        assertFalse(com.stripedlens.calcloner.engine.CalendarEventWriter.matchesFilters(nullAvailEvent, disallowEmptyPair))

        // RSVP filter
        val declinedEvent = makeEvent(selfAttendeeStatus = 2)
        val acceptedEvent = makeEvent(selfAttendeeStatus = 1)
        val disallowDeclinedPair = basePair.copy(filterRsvpDeclined = false, filterRsvpAccepted = true)
        assertFalse(com.stripedlens.calcloner.engine.CalendarEventWriter.matchesFilters(declinedEvent, disallowDeclinedPair))
        assertTrue(com.stripedlens.calcloner.engine.CalendarEventWriter.matchesFilters(acceptedEvent, disallowDeclinedPair))

        // All Day filter
        val allDayEvent = makeEvent(allDay = 1)
        val disallowAllDayPair = basePair.copy(filterIncludeAllDay = false)
        assertFalse(com.stripedlens.calcloner.engine.CalendarEventWriter.matchesFilters(allDayEvent, disallowAllDayPair))
        val allowAllDayPair = basePair.copy(filterIncludeAllDay = true)
        assertTrue(com.stripedlens.calcloner.engine.CalendarEventWriter.matchesFilters(allDayEvent, allowAllDayPair))

        // Text filter - title contains match all vs any
        val titlePairMatchAll = basePair.copy(
            filterTitleContains = "sprint, planning",
            filterTitleContainsMatchAll = true
        )
        assertTrue(com.stripedlens.calcloner.engine.CalendarEventWriter.matchesFilters(makeEvent(title = "Sprint Planning"), titlePairMatchAll))
        assertFalse(com.stripedlens.calcloner.engine.CalendarEventWriter.matchesFilters(makeEvent(title = "Sprint Retro"), titlePairMatchAll))

        val titlePairMatchAny = basePair.copy(
            filterTitleContains = "planning, retro",
            filterTitleContainsMatchAll = false
        )
        assertTrue(com.stripedlens.calcloner.engine.CalendarEventWriter.matchesFilters(makeEvent(title = "Sprint Retro"), titlePairMatchAny))
        assertFalse(com.stripedlens.calcloner.engine.CalendarEventWriter.matchesFilters(makeEvent(title = "Team Standup"), titlePairMatchAny))

        // Text filter - title does not contain
        val titleExclusionPair = basePair.copy(
            filterTitleDoesNotContain = "canceled, skip"
        )
        assertTrue(com.stripedlens.calcloner.engine.CalendarEventWriter.matchesFilters(makeEvent(title = "Sprint Planning"), titleExclusionPair))
        assertFalse(com.stripedlens.calcloner.engine.CalendarEventWriter.matchesFilters(makeEvent(title = "Canceled: Sprint Planning"), titleExclusionPair))
    }

    @Test
    fun testOneTimeCopyTrackingTagCompatibility() {
        val tagRegex = Regex("""\[CalClone(?:r)?-ID:\s*(?:([a-zA-Z0-9_-]+):)?(\d+)\]""")

        val oneTimeTagMatch = tagRegex.find("Project roadmap [CalCloner-ID: onetime:12345] details")
        assertNotNull(oneTimeTagMatch)
        assertEquals("onetime", oneTimeTagMatch!!.groupValues[1])
        assertEquals("12345", oneTimeTagMatch.groupValues[2])

        val customAppUri = "calcloner://event/onetime/12345"
        assertTrue(customAppUri.startsWith("calcloner://event/"))
        val remainder = customAppUri.removePrefix("calcloner://event/")
        val matchedPairId = remainder.substringBefore("/")
        val sourceId = remainder.substringAfter("/").toLongOrNull()
        assertEquals("onetime", matchedPairId)
        assertEquals(12345L, sourceId)
    }
}
