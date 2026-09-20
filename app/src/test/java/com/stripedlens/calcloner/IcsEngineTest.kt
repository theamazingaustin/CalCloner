package com.stripedlens.calcloner

import com.stripedlens.calcloner.domain.ics.ExportFormat
import com.stripedlens.calcloner.domain.ics.IcsEngine
import com.stripedlens.calcloner.engine.CalendarIcsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class IcsEngineTest {

    private val testStartUtc = 1716202800000L // 2024-05-20 11:00:00 UTC
    private val testEndUtc = 1716206400000L   // 2024-05-20 12:00:00 UTC

    @Test
    fun testIcsSerializationHeadersAndMetadata() {
        val event = SyncEvent(
            id = 101L,
            title = "Project Sync Meeting",
            description = "Discussing Q3 roadmaps and deliverables.",
            location = "Boardroom A / Google Meet",
            dtStart = testStartUtc,
            dtEnd = testEndUtc,
            duration = null,
            allDay = 0,
            timeZone = "UTC",
            rrule = null,
            reminders = listOf(SyncReminder(minutes = 15, method = 1))
        )

        val ics = IcsEngine.serialize(
            events = listOf(event),
            calendarName = "Work Calendar",
            calendarColorHex = "#00D47E",
            timeZoneId = "UTC"
        )

        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("VERSION:2.0"))
        assertTrue(ics.contains("PRODID:-//Striped Lens//CalCloner 2.3//EN"))
        assertTrue(ics.contains("X-WR-CALNAME:Work Calendar"))
        assertTrue(ics.contains("X-WR-TIMEZONE:UTC"))
        assertTrue(ics.contains("X-APPLE-CALENDAR-COLOR:#00D47E"))
        assertTrue(ics.contains("BEGIN:VEVENT"))
        assertTrue(ics.contains("SUMMARY:Project Sync Meeting"))
        assertTrue(ics.contains("LOCATION:Boardroom A / Google Meet"))
        assertTrue(ics.contains("DESCRIPTION:Discussing Q3 roadmaps and deliverables."))
        assertTrue(ics.contains("BEGIN:VALARM"))
        assertTrue(ics.contains("TRIGGER:-PT15M"))
        assertTrue(ics.contains("END:VALARM"))
        assertTrue(ics.contains("END:VEVENT"))
        assertTrue(ics.contains("END:VCALENDAR"))
    }

    @Test
    fun testIcsAllDayEventSerialization() {
        val allDayEvent = SyncEvent(
            id = 202L,
            title = "Company Holiday",
            description = "Office closed",
            location = null,
            dtStart = testStartUtc,
            dtEnd = testStartUtc + 86400000L,
            duration = null,
            allDay = 1,
            timeZone = "UTC",
            rrule = null
        )

        val ics = IcsEngine.serialize(listOf(allDayEvent), "Holidays", timeZoneId = "UTC")
        assertTrue(ics.contains("DTSTART;VALUE=DATE:"))
        assertTrue(ics.contains("DTEND;VALUE=DATE:"))
        assertFalse(ics.contains("DTSTART;TZID="))
    }

    @Test
    fun testIcsRecurrenceRuleSerialization() {
        val recurringEvent = SyncEvent(
            id = 303L,
            title = "Weekly Standup",
            description = "Monday team sync",
            location = "Room 404",
            dtStart = testStartUtc,
            dtEnd = testEndUtc,
            duration = null,
            allDay = 0,
            timeZone = "UTC",
            rrule = "FREQ=WEEKLY;BYDAY=MO"
        )

        val ics = IcsEngine.serialize(listOf(recurringEvent), "Team", timeZoneId = "UTC")
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;BYDAY=MO"))
    }

    @Test
    fun testIcsRoundtripParsing() {
        val originalEvent = SyncEvent(
            id = 404L,
            title = "Dentist Appointment",
            description = "Annual checkup & cleaning.\nRemember insurance card!",
            location = "Dental Suite 101, Main St",
            dtStart = 1716202800000L,
            dtEnd = 1716206400000L,
            duration = null,
            allDay = 0,
            timeZone = "UTC",
            rrule = null,
            reminders = listOf(SyncReminder(minutes = 30, method = 1))
        )

        val icsString = IcsEngine.serialize(listOf(originalEvent), "Personal", timeZoneId = "UTC")
        val parsedEvents = IcsEngine.parse(ByteArrayInputStream(icsString.toByteArray(Charsets.UTF_8)))

        assertEquals(1, parsedEvents.size)
        val parsed = parsedEvents[0]
        assertEquals("Dentist Appointment", parsed.title)
        assertEquals("Dental Suite 101, Main St", parsed.location)
        assertEquals("Annual checkup & cleaning.\nRemember insurance card!", parsed.description)
        assertEquals(originalEvent.dtStart, parsed.dtStart)
        assertEquals(originalEvent.dtEnd, parsed.dtEnd)
        assertEquals(0, parsed.allDay)
        assertEquals(1, parsed.reminders.size)
        assertEquals(30, parsed.reminders[0].minutes)
    }

    @Test
    fun testCsvSerialization() {
        val event = SyncEvent(
            id = 505L,
            title = "Client Presentation",
            description = "Demo new features, answer Q&A",
            location = "Downtown Office",
            dtStart = testStartUtc,
            dtEnd = testEndUtc,
            duration = null,
            allDay = 0,
            timeZone = "UTC",
            rrule = null
        )

        val csv = IcsEngine.serializeCsv(listOf(event))
        val lines = csv.trim().lines()
        assertTrue(lines.size >= 2)
        assertEquals("Subject,Start Date,Start Time,End Date,End Time,All Day Event,Description,Location,Private", lines[0])
        assertTrue(lines[1].contains("Client Presentation"))
        assertTrue(lines[1].contains("Downtown Office"))
        assertTrue(lines[1].contains("\"Demo new features, answer Q&A\""))
        assertTrue(lines[1].contains("False"))
    }

    @Test
    fun testFilenameSanitization() {
        val dirtyName = "My: Work / Personal * Calendar <2026>?"
        val safe = CalendarIcsManager.sanitizeFilename(dirtyName)
        assertEquals("My_ Work _ Personal _ Calendar _2026__", safe)
        assertFalse(safe.contains(":"))
        assertFalse(safe.contains("/"))
        assertFalse(safe.contains("*"))
        assertFalse(safe.contains("?"))
        assertFalse(safe.contains("<"))
        assertFalse(safe.contains(">"))
    }
}
