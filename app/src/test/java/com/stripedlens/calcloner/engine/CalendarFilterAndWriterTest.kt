package com.stripedlens.calcloner.engine

import com.stripedlens.calcloner.SyncAttendee
import com.stripedlens.calcloner.SyncEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CalendarFilterAndWriterTest {

    private val utcTz = TimeZone.getTimeZone("UTC")

    private fun createTimestamp(
        year: Int = 2024,
        month: Int = Calendar.JUNE,
        day: Int = 10, // Monday
        hour: Int = 10,
        minute: Int = 0
    ): Long {
        val cal = Calendar.getInstance(utcTz).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun makeEvent(
        title: String = "Sprint Planning",
        description: String = "Weekly team sync",
        dtStart: Long = createTimestamp(),
        allDay: Int = 0,
        availability: Int? = 0, // BUSY
        status: Int? = 1, // CONFIRMED
        selfAttendeeStatus: Int? = 1, // ACCEPTED
        attendees: List<SyncAttendee> = emptyList()
    ) = SyncEvent(
        id = 1L,
        title = title,
        description = description,
        location = "Boardroom",
        dtStart = dtStart,
        dtEnd = dtStart + 3600000L,
        duration = null,
        allDay = allDay,
        timeZone = "UTC",
        rrule = null,
        availability = availability,
        status = status,
        selfAttendeeStatus = selfAttendeeStatus,
        attendees = attendees
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Time-of-Day Filter Window Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testTimeOfDayFilterWindow() {
        // Window from 09:00 to 17:00
        val eventAt10am = makeEvent(dtStart = createTimestamp(hour = 10, minute = 0))
        val eventAt8am = makeEvent(dtStart = createTimestamp(hour = 8, minute = 30))
        val eventAt6pm = makeEvent(dtStart = createTimestamp(hour = 18, minute = 0))
        val eventAt9amExact = makeEvent(dtStart = createTimestamp(hour = 9, minute = 0))
        val eventAt5pmExact = makeEvent(dtStart = createTimestamp(hour = 17, minute = 0))

        assertTrue(
            "Event at 10:00 AM should pass 9-17 window",
            CalendarEventWriter.matchesFilters(
                event = eventAt10am,
                filterEnabled = true,
                filterEnableTimeFilter = true,
                filterFromHour = 9,
                filterFromMinute = 0,
                filterToHour = 17,
                filterToMinute = 0,
                deviceTimeZone = utcTz
            )
        )

        assertFalse(
            "Event at 8:30 AM should be rejected by 9-17 window",
            CalendarEventWriter.matchesFilters(
                event = eventAt8am,
                filterEnabled = true,
                filterEnableTimeFilter = true,
                filterFromHour = 9,
                filterFromMinute = 0,
                filterToHour = 17,
                filterToMinute = 0,
                deviceTimeZone = utcTz
            )
        )

        assertFalse(
            "Event at 6:00 PM should be rejected by 9-17 window",
            CalendarEventWriter.matchesFilters(
                event = eventAt6pm,
                filterEnabled = true,
                filterEnableTimeFilter = true,
                filterFromHour = 9,
                filterFromMinute = 0,
                filterToHour = 17,
                filterToMinute = 0,
                deviceTimeZone = utcTz
            )
        )

        assertTrue(
            "Event at exact start boundary (9:00 AM) should pass",
            CalendarEventWriter.matchesFilters(
                event = eventAt9amExact,
                filterEnabled = true,
                filterEnableTimeFilter = true,
                filterFromHour = 9,
                filterFromMinute = 0,
                filterToHour = 17,
                filterToMinute = 0,
                deviceTimeZone = utcTz
            )
        )

        assertTrue(
            "Event at exact end boundary (5:00 PM) should pass",
            CalendarEventWriter.matchesFilters(
                event = eventAt5pmExact,
                filterEnabled = true,
                filterEnableTimeFilter = true,
                filterFromHour = 9,
                filterFromMinute = 0,
                filterToHour = 17,
                filterToMinute = 0,
                deviceTimeZone = utcTz
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Active Days of the Week Filter Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testActiveDaysOfWeekFilter() {
        // Monday = June 10, 2024 (Calendar.MONDAY = 2)
        // Wednesday = June 12, 2024 (Calendar.WEDNESDAY = 4)
        // Saturday = June 15, 2024 (Calendar.SATURDAY = 7)
        // Sunday = June 16, 2024 (Calendar.SUNDAY = 1)
        val mondayEvent = makeEvent(dtStart = createTimestamp(day = 10))
        val wednesdayEvent = makeEvent(dtStart = createTimestamp(day = 12))
        val saturdayEvent = makeEvent(dtStart = createTimestamp(day = 15))
        val sundayEvent = makeEvent(dtStart = createTimestamp(day = 16))

        // ISO Business days: Monday=1 to Friday=5
        val weekdaysOnly = setOf(1, 2, 3, 4, 5)

        assertTrue(
            "Monday event should pass weekday filter",
            CalendarEventWriter.matchesFilters(
                event = mondayEvent,
                filterEnabled = true,
                filterActiveDays = weekdaysOnly,
                deviceTimeZone = utcTz
            )
        )

        assertTrue(
            "Wednesday event should pass weekday filter",
            CalendarEventWriter.matchesFilters(
                event = wednesdayEvent,
                filterEnabled = true,
                filterActiveDays = weekdaysOnly,
                deviceTimeZone = utcTz
            )
        )

        assertFalse(
            "Saturday event should be excluded by weekday filter",
            CalendarEventWriter.matchesFilters(
                event = saturdayEvent,
                filterEnabled = true,
                filterActiveDays = weekdaysOnly,
                deviceTimeZone = utcTz
            )
        )

        assertFalse(
            "Sunday event should be excluded by weekday filter",
            CalendarEventWriter.matchesFilters(
                event = sundayEvent,
                filterEnabled = true,
                filterActiveDays = weekdaysOnly,
                deviceTimeZone = utcTz
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Description Keyword Filter Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testDescriptionKeywordFiltering() {
        val budgetMeeting = makeEvent(description = "Discussing quarterly BUDGET and headcount allocations.")
        val generalSync = makeEvent(description = "General 1-on-1 catchup.")

        // Contains "budget" (case-insensitive)
        assertTrue(
            CalendarEventWriter.matchesFilters(
                event = budgetMeeting,
                filterEnabled = true,
                filterDescriptionContains = "budget",
                deviceTimeZone = utcTz
            )
        )
        assertFalse(
            CalendarEventWriter.matchesFilters(
                event = generalSync,
                filterEnabled = true,
                filterDescriptionContains = "budget",
                deviceTimeZone = utcTz
            )
        )

        // Excludes "headcount"
        assertFalse(
            CalendarEventWriter.matchesFilters(
                event = budgetMeeting,
                filterEnabled = true,
                filterDescriptionDoesNotContain = "headcount",
                deviceTimeZone = utcTz
            )
        )
        assertTrue(
            CalendarEventWriter.matchesFilters(
                event = generalSync,
                filterEnabled = true,
                filterDescriptionDoesNotContain = "headcount",
                deviceTimeZone = utcTz
            )
        )

        // Multiple keywords (matchAll vs matchAny)
        val multiKeywordEvent = makeEvent(description = "Confidential strategy meeting regarding acquisitions.")
        assertTrue(
            CalendarEventWriter.matchesFilters(
                event = multiKeywordEvent,
                filterEnabled = true,
                filterDescriptionContains = "confidential, strategy",
                filterDescriptionContainsMatchAll = true,
                deviceTimeZone = utcTz
            )
        )
        assertFalse(
            CalendarEventWriter.matchesFilters(
                event = multiKeywordEvent,
                filterEnabled = true,
                filterDescriptionContains = "confidential, budget",
                filterDescriptionContainsMatchAll = true,
                deviceTimeZone = utcTz
            )
        )
        assertTrue(
            CalendarEventWriter.matchesFilters(
                event = multiKeywordEvent,
                filterEnabled = true,
                filterDescriptionContains = "confidential, budget",
                filterDescriptionContainsMatchAll = false,
                deviceTimeZone = utcTz
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // parseDurationDays Helper Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testParseDurationDays() {
        assertEquals(1L, CalendarEventWriter.parseDurationDays("P1D"))
        assertEquals(7L, CalendarEventWriter.parseDurationDays("P7D"))
        assertEquals(14L, CalendarEventWriter.parseDurationDays("P2W"))
        assertEquals(30L, CalendarEventWriter.parseDurationDays("P30D"))
        assertNull(CalendarEventWriter.parseDurationDays(null))
        assertNull(CalendarEventWriter.parseDurationDays(""))
        assertNull(CalendarEventWriter.parseDurationDays("PT1H")) // sub-day duration
        assertNull(CalendarEventWriter.parseDurationDays("INVALID"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // formatAttendeesBlock Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testFormatAttendeesBlock() {
        val attendees = listOf(
            SyncAttendee("Alice Smith", "alice@example.com", 1), // ACCEPTED
            SyncAttendee(null, "bob@example.com", 2),           // DECLINED
            SyncAttendee("Charlie", "charlie@example.com", 4)   // TENTATIVE
        )

        val block = CalendarEventWriter.formatAttendeesBlock(attendees)
        assertTrue(block.startsWith("Attendees:\n"))
        assertTrue(block.contains("• Alice Smith (Accepted)"))
        assertTrue(block.contains("• bob (Declined)"))
        assertTrue(block.contains("• Charlie (Tentative)"))
    }
}
