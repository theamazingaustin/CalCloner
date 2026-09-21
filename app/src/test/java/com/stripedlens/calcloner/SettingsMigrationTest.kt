package com.stripedlens.calcloner

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMigrationTest {

    @Test
    fun testLegacyV1JsonDeserialization() {
        // Earliest v1 format without attendees, filters, or granular overrides
        val legacyV1Json = """
            {
                "id": "pair_legacy_v1",
                "fromCalendarId": 10,
                "fromCalendarName": "Personal",
                "toCalendarId": 20,
                "toCalendarName": "Work Cloned",
                "daysPast": 14,
                "daysFuture": 60,
                "isEnabled": true
            }
        """.trimIndent()

        val pair = SyncPair.fromJson(JSONObject(legacyV1Json))

        assertEquals("pair_legacy_v1", pair.id)
        assertEquals(10L, pair.fromCalendarId)
        assertEquals(20L, pair.toCalendarId)
        assertEquals(14, pair.daysPast)
        assertEquals(60, pair.daysFuture)
        assertTrue(pair.isEnabled)

        // Verify safe defaults are applied for unpopulated v2/v2.3 fields
        assertTrue(pair.syncTitle)
        assertNull(pair.customTitle)
        assertTrue(pair.syncDescription)
        assertNull(pair.customDescription)
        assertTrue(pair.syncReminders)
        assertFalse(pair.syncAttendees)
        assertEquals("END", pair.attendeesPlacement)
        assertFalse(pair.filterEnabled)
        assertTrue(pair.filterIncludeAllDay)
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7), pair.filterActiveDays)
    }

    @Test
    fun testLegacyV2JsonDeserializationWithoutFilters() {
        // v2 format with granular field overrides but before advanced filters were introduced
        val legacyV2Json = """
            {
                "id": "pair_v2",
                "fromCalendarId": 100,
                "fromCalendarName": "Family",
                "toCalendarId": 200,
                "toCalendarName": "Aggregated",
                "daysPast": 30,
                "daysFuture": 90,
                "isEnabled": true,
                "syncTitle": false,
                "customTitle": "Busy",
                "titlePrefix": "[SYNC] ",
                "syncReminders": false,
                "syncAttendees": true,
                "attendeesPlacement": "START"
            }
        """.trimIndent()

        val pair = SyncPair.fromJson(JSONObject(legacyV2Json))

        assertEquals("pair_v2", pair.id)
        assertFalse(pair.syncTitle)
        assertEquals("Busy", pair.customTitle)
        assertEquals("[SYNC] ", pair.titlePrefix)
        assertFalse(pair.syncReminders)
        assertTrue(pair.syncAttendees)
        assertEquals("START", pair.attendeesPlacement)

        // Filters should safely default to disabled with standard defaults
        assertFalse(pair.filterEnabled)
        assertTrue(pair.filterAllowBusy)
        assertTrue(pair.filterAllowFree)
        assertFalse(pair.filterAllowTentative)
        assertEquals(9, pair.filterFromHour)
        assertEquals(17, pair.filterToHour)
    }

    @Test
    fun testCorruptJsonHandling() {
        // Invalid json string should return empty list without throwing
        val corruptList = SyncPair.listFromJsonString("NOT_VALID_JSON{:::}")
        assertTrue(corruptList.isEmpty())

        // Empty string
        val emptyList = SyncPair.listFromJsonString("")
        assertTrue(emptyList.isEmpty())

        // Null string
        val nullList = SyncPair.listFromJsonString(null)
        assertTrue(nullList.isEmpty())
    }

    @Test
    fun testFullModernV23JsonRoundtrip() {
        val modernPair = SyncPair(
            id = "pair_v2_3",
            nickname = "VIP Meetings",
            fromCalendarId = 5L,
            fromCalendarName = "Source",
            toCalendarId = 6L,
            toCalendarName = "Target",
            daysPast = 45,
            daysFuture = 120,
            isEnabled = true,
            syncTitle = true,
            titlePrefix = "[VIP] ",
            titleSuffix = " - Cloned",
            syncDescription = false,
            customDescription = "Confidential",
            syncLocation = true,
            syncReminders = true,
            syncAvailability = false,
            customAvailability = 1, // FREE
            syncStatus = false,
            customStatus = 1, // CONFIRMED
            syncAccessLevel = false,
            customAccessLevel = 2, // PRIVATE
            syncAttendees = true,
            attendeesPlacement = "START",
            filterEnabled = true,
            filterAllowBusy = true,
            filterAllowFree = false,
            filterAllowTentative = true,
            filterAllowEmpty = false,
            filterRsvpAccepted = true,
            filterRsvpTentative = false,
            filterRsvpDeclined = false,
            filterIncludeAllDay = false,
            filterEnableTimeFilter = true,
            filterFromHour = 8,
            filterFromMinute = 30,
            filterToHour = 18,
            filterToMinute = 30,
            filterActiveDays = setOf(2, 3, 4, 5, 6),
            filterTitleContains = "Executive",
            filterTitleContainsMatchAll = true,
            filterTitleDoesNotContain = "Cancelled",
            filterTitleDoesNotContainMatchAll = true,
            filterDescriptionContains = "Urgent",
            filterDescriptionContainsMatchAll = false,
            filterDescriptionDoesNotContain = "Draft",
            filterDescriptionDoesNotContainMatchAll = false
        )

        val jsonString = SyncPair.listToJsonString(listOf(modernPair))
        val restoredList = SyncPair.listFromJsonString(jsonString)

        assertEquals(1, restoredList.size)
        val restored = restoredList[0]

        assertEquals(modernPair.id, restored.id)
        assertEquals(modernPair.nickname, restored.nickname)
        assertEquals(modernPair.fromCalendarId, restored.fromCalendarId)
        assertEquals(modernPair.toCalendarId, restored.toCalendarId)
        assertEquals(modernPair.titlePrefix, restored.titlePrefix)
        assertEquals(modernPair.titleSuffix, restored.titleSuffix)
        assertEquals(modernPair.customDescription, restored.customDescription)
        assertEquals(modernPair.customAvailability, restored.customAvailability)
        assertEquals(modernPair.customStatus, restored.customStatus)
        assertEquals(modernPair.customAccessLevel, restored.customAccessLevel)
        assertEquals(modernPair.syncAttendees, restored.syncAttendees)
        assertEquals(modernPair.attendeesPlacement, restored.attendeesPlacement)
        assertEquals(modernPair.filterEnabled, restored.filterEnabled)
        assertEquals(modernPair.filterAllowBusy, restored.filterAllowBusy)
        assertEquals(modernPair.filterAllowFree, restored.filterAllowFree)
        assertEquals(modernPair.filterEnableTimeFilter, restored.filterEnableTimeFilter)
        assertEquals(modernPair.filterFromHour, restored.filterFromHour)
        assertEquals(modernPair.filterFromMinute, restored.filterFromMinute)
        assertEquals(modernPair.filterToHour, restored.filterToHour)
        assertEquals(modernPair.filterToMinute, restored.filterToMinute)
        assertEquals(modernPair.filterActiveDays, restored.filterActiveDays)
        assertEquals(modernPair.filterTitleContains, restored.filterTitleContains)
        assertEquals(modernPair.filterTitleDoesNotContain, restored.filterTitleDoesNotContain)
        assertEquals(modernPair.filterDescriptionContains, restored.filterDescriptionContains)
        assertEquals(modernPair.filterDescriptionDoesNotContain, restored.filterDescriptionDoesNotContain)
    }
}
