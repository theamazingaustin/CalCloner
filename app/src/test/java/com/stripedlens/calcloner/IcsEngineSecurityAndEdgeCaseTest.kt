package com.stripedlens.calcloner

import com.stripedlens.calcloner.domain.ics.IcsEngine
import com.stripedlens.calcloner.engine.CalendarIcsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class IcsEngineSecurityAndEdgeCaseTest {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. OWASP CSV Formula Injection (DDE) Defense
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testCsvFormulaInjectionDefense() {
        val dangerousEvents = listOf(
            SyncEvent(
                id = 1L,
                title = "=cmd|'/C calc'!A0",
                description = "@SUM(A1:A100)",
                location = "+447123456789",
                dtStart = 1716202800000L,
                dtEnd = 1716206400000L,
                duration = null,
                allDay = 0,
                timeZone = "UTC",
                rrule = null
            ),
            SyncEvent(
                id = 2L,
                title = "-1000 Discount",
                description = "\tTabIndentedText",
                location = "Safe Location",
                dtStart = 1716202800000L,
                dtEnd = 1716206400000L,
                duration = null,
                allDay = 0,
                timeZone = "UTC",
                rrule = null
            )
        )

        val csv = IcsEngine.serializeCsv(dangerousEvents)
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)

        // Line 1: Verify title '=cmd...' is neutralized with leading single quote
        assertTrue("Equal sign formula must be prepended with quote", lines[1].contains("''=cmd|'/C calc'!A0") || lines[1].contains("'=cmd|'/C calc'!A0"))
        // Verify description '@SUM...' is neutralized
        assertTrue("@ formula must be prepended with quote", lines[1].contains("'@SUM(A1:A100)"))
        // Verify phone '+44...' is neutralized
        assertTrue("+ formula trigger must be prepended with quote", lines[1].contains("'+447123456789"))

        // Line 2: Verify '-1000' and '\t' are neutralized
        assertTrue("- formula trigger must be prepended with quote", lines[2].contains("'-1000 Discount"))
        assertTrue("Tab formula trigger must be prepended with quote", lines[2].contains("'\tTabIndentedText"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. RFC 5545 Line Unfolding
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testRfc5545LineUnfolding() {
        // Real-world iCalendar lines folded at 75 octets with CRLF followed by single space
        val foldedIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//EN
            BEGIN:VEVENT
            UID:test-unfold-1
            SUMMARY:Annual General Meeting with All Board Members and St
             akeholders Across Europe
            DESCRIPTION:This is a very long description that was folded by the s
             erver into multiple lines using RFC 5545 standard continuation sp
             aces.
            DTSTART:20240520T100000Z
            DTEND:20240520T110000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = IcsEngine.parse(ByteArrayInputStream(foldedIcs.toByteArray(Charsets.UTF_8)))
        assertEquals(1, parsed.size)
        val event = parsed[0]

        assertEquals(
            "Annual General Meeting with All Board Members and Stakeholders Across Europe",
            event.title
        )
        assertEquals(
            "This is a very long description that was folded by the server into multiple lines using RFC 5545 standard continuation spaces.",
            event.description
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. CRLF Header Injection Defense
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testCrlfHeaderInjectionDefense() {
        val maliciousEvent = SyncEvent(
            id = 10L,
            title = "Innocent Title\r\nATTACK:INJECTED_HEADER\r\nBEGIN:VEVENT\r\nUID:rogue",
            description = "Innocent Desc\r\nMETHOD:CANCEL",
            location = "Room 101",
            dtStart = 1716202800000L,
            dtEnd = 1716206400000L,
            duration = null,
            allDay = 0,
            timeZone = "UTC",
            rrule = null
        )

        val serialized = IcsEngine.serialize(listOf(maliciousEvent), "TestCal", timeZoneId = "UTC")

        // Rogue header must NOT exist as a top-level line
        val lines = serialized.lines().map { it.trim() }
        assertFalse("Raw injected header must not appear as an unescaped line", lines.contains("ATTACK:INJECTED_HEADER"))
        assertFalse("Raw injected METHOD must not appear as an unescaped line", lines.contains("METHOD:CANCEL"))

        // Must be safely escaped with backslash-n
        assertTrue(serialized.contains("Innocent Title\\nATTACK:INJECTED_HEADER\\nBEGIN:VEVENT\\nUID:rogue"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Malformed and Adversarial Payloads
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testMalformedIcsDoesNotCrash() {
        // Incomplete / truncated payload (no END:VCALENDAR or missing closing tags)
        val truncatedIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            SUMMARY:Truncated Event
            DTSTART:20240520T100000Z
        """.trimIndent()

        val parsed = IcsEngine.parse(ByteArrayInputStream(truncatedIcs.toByteArray(Charsets.UTF_8)))
        assertNotNull(parsed)

        // Null bytes in payload
        val nullByteIcs = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nSUMMARY:Null\u0000Byte\r\nDTSTART:20240520T100000Z\r\nEND:VEVENT\r\nEND:VCALENDAR"
        val parsedNull = IcsEngine.parse(ByteArrayInputStream(nullByteIcs.toByteArray(Charsets.UTF_8)))
        assertEquals(1, parsedNull.size)
        assertTrue(parsedNull[0].title!!.contains("Null"))

        // Embedded HTML / JavaScript / SQL strings are inert
        val xssIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            SUMMARY:<script>alert('XSS')</script>
            DESCRIPTION:'; DROP TABLE events; --
            DTSTART:20240520T100000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val parsedXss = IcsEngine.parse(ByteArrayInputStream(xssIcs.toByteArray(Charsets.UTF_8)))
        assertEquals(1, parsedXss.size)
        assertEquals("<script>alert('XSS')</script>", parsedXss[0].title)
        assertEquals("'; DROP TABLE events; --", parsedXss[0].description)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Path Traversal & Filename Sanitization Security
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testFilenameSanitizationPathTraversalSecurity() {
        // Path traversal attempts
        val sanitizedPasswd = CalendarIcsManager.sanitizeFilename("../../../../etc/passwd")
        assertFalse(sanitizedPasswd.contains(".."))
        assertFalse(sanitizedPasswd.contains("/"))
        assertTrue(sanitizedPasswd.contains("etc_passwd"))

        val sanitizedSecret = CalendarIcsManager.sanitizeFilename("..\\..\\secret_data")
        assertFalse(sanitizedSecret.contains(".."))
        assertFalse(sanitizedSecret.contains("\\"))
        assertTrue(sanitizedSecret.contains("secret_data"))

        // Control characters
        assertEquals("My_Calendar_File", CalendarIcsManager.sanitizeFilename("My\u0000Calendar\u001FFile"))

        // Empty, blank, or dot-only strings fallback to safe default
        assertEquals("Calendar", CalendarIcsManager.sanitizeFilename(""))
        assertEquals("Calendar", CalendarIcsManager.sanitizeFilename("    "))
        assertEquals("Calendar", CalendarIcsManager.sanitizeFilename("..."))

        // Characters forbidden by Windows & POSIX filesystems
        val forbidden = CalendarIcsManager.sanitizeFilename("My:Cool*Calendar?\"<Name>|")
        assertFalse(forbidden.contains(":"))
        assertFalse(forbidden.contains("*"))
        assertFalse(forbidden.contains("?"))
        assertFalse(forbidden.contains("\""))
        assertFalse(forbidden.contains("<"))
        assertFalse(forbidden.contains(">"))
        assertFalse(forbidden.contains("|"))
    }
}
