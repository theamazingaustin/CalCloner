package com.stripedlens.calcloner.domain.ics

import com.stripedlens.calcloner.SyncAttendee
import com.stripedlens.calcloner.SyncEvent
import com.stripedlens.calcloner.SyncReminder
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Supported file export formats for calendar data.
 */
enum class ExportFormat(val extension: String, val mimeType: String, val displayName: String) {
    ICS("ics", "text/calendar", "iCalendar (.ics)"),
    CSV("csv", "text/csv", "Spreadsheet (.csv)")
}

/**
 * Lightweight, zero-dependency RFC 5545 iCalendar serializer and parser,
 * along with standard CSV calendar export.
 */
object IcsEngine {

    private const val PRODID = "-//Striped Lens//CalCloner 2.3//EN"

    // ─────────────────────────────────────────────────────────────────────────
    // RFC 5545 iCalendar Serializer
    // ─────────────────────────────────────────────────────────────────────────

    fun serialize(
        events: List<SyncEvent>,
        calendarName: String,
        calendarColorHex: String? = null,
        timeZoneId: String = TimeZone.getDefault().id
    ): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:$PRODID\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        sb.append("METHOD:PUBLISH\r\n")
        sb.append("X-WR-CALNAME:${escapeIcsText(calendarName)}\r\n")
        sb.append("X-WR-TIMEZONE:$timeZoneId\r\n")
        if (!calendarColorHex.isNullOrBlank()) {
            val cleanHex = if (calendarColorHex.startsWith("#")) calendarColorHex else "#$calendarColorHex"
            sb.append("X-APPLE-CALENDAR-COLOR:$cleanHex\r\n")
        }

        val utcFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val nowUtc = utcFormat.format(Date())

        for (event in events) {
            sb.append("BEGIN:VEVENT\r\n")
            val uid = event.syncId?.takeIf { it.isNotBlank() } ?: "calcloner-${event.id}@stripedlens.com"
            sb.append("UID:$uid\r\n")
            sb.append("DTSTAMP:$nowUtc\r\n")

            val isAllDay = event.allDay == 1
            if (isAllDay) {
                val startStr = dateFormat.format(Date(event.dtStart))
                sb.append("DTSTART;VALUE=DATE:$startStr\r\n")
                if (event.dtEnd != null && event.dtEnd > event.dtStart) {
                    val endStr = dateFormat.format(Date(event.dtEnd))
                    sb.append("DTEND;VALUE=DATE:$endStr\r\n")
                }
            } else {
                val startStr = utcFormat.format(Date(event.dtStart))
                sb.append("DTSTART:$startStr\r\n")
                if (event.dtEnd != null && event.dtEnd > 0) {
                    val endStr = utcFormat.format(Date(event.dtEnd))
                    sb.append("DTEND:$endStr\r\n")
                } else if (!event.duration.isNullOrBlank()) {
                    sb.append("DURATION:${event.duration}\r\n")
                }
            }

            if (!event.title.isNullOrBlank()) {
                sb.append("SUMMARY:${escapeIcsText(event.title)}\r\n")
            }
            if (!event.description.isNullOrBlank()) {
                sb.append("DESCRIPTION:${escapeIcsText(event.description)}\r\n")
            }
            if (!event.location.isNullOrBlank()) {
                sb.append("LOCATION:${escapeIcsText(event.location)}\r\n")
            }
            if (!event.rrule.isNullOrBlank()) {
                val cleanRrule = if (event.rrule.startsWith("RRULE:", ignoreCase = true)) {
                    event.rrule.substringAfter(":")
                } else event.rrule
                sb.append("RRULE:$cleanRrule\r\n")
            }
            if (!event.exdate.isNullOrBlank()) {
                sb.append("EXDATE:${event.exdate}\r\n")
            }

            when (event.status) {
                1 -> sb.append("STATUS:CONFIRMED\r\n")
                2 -> sb.append("STATUS:CANCELLED\r\n")
                0 -> sb.append("STATUS:TENTATIVE\r\n")
            }

            when (event.availability) {
                1 -> sb.append("TRANSP:TRANSPARENT\r\n") // Free
                0 -> sb.append("TRANSP:OPAQUE\r\n")      // Busy
            }

            // Reminders / VALARM
            for (reminder in event.reminders) {
                sb.append("BEGIN:VALARM\r\n")
                sb.append("ACTION:DISPLAY\r\n")
                sb.append("DESCRIPTION:${escapeIcsText(event.title ?: "Reminder")}\r\n")
                sb.append("TRIGGER:-PT${reminder.minutes}M\r\n")
                sb.append("END:VALARM\r\n")
            }

            sb.append("END:VEVENT\r\n")
        }

        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    fun serializeToStream(
        events: List<SyncEvent>,
        calendarName: String,
        calendarColorHex: String? = null,
        outputStream: OutputStream
    ) {
        val icsString = serialize(events, calendarName, calendarColorHex)
        outputStream.bufferedWriter(Charsets.UTF_8).use {
            it.write(icsString)
            it.flush()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RFC 5545 iCalendar Parser
    // ─────────────────────────────────────────────────────────────────────────

    fun parse(inputStream: InputStream): List<SyncEvent> {
        val rawLines = mutableListOf<String>()
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    // Line unfolding (continuation line)
                    if (rawLines.isNotEmpty()) {
                        val last = rawLines.removeAt(rawLines.size - 1)
                        rawLines.add(last + line.substring(1))
                    }
                } else {
                    rawLines.add(line.trimEnd('\r', '\n'))
                }
            }
        }

        val events = mutableListOf<SyncEvent>()
        var inVEvent = false
        var currentTitle: String? = null
        var currentDesc: String? = null
        var currentLoc: String? = null
        var currentDtStart: Long = 0L
        var currentDtEnd: Long? = null
        var currentDuration: String? = null
        var currentAllDay: Int = 0
        var currentTz: String? = null
        var currentRrule: String? = null
        var currentExdate: String? = null
        var currentStatus: Int? = null
        var currentAvailability: Int? = null
        var currentUid: String? = null
        val currentReminders = mutableListOf<SyncReminder>()

        var inVAlarm = false
        var alarmTriggerMinutes: Int? = null

        val utcFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val localFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        fun parseIcsDate(dateStr: String): Pair<Long, Boolean> {
            val clean = dateStr.trim()
            return try {
                if (clean.endsWith("Z")) {
                    Pair(utcFormat.parse(clean)?.time ?: 0L, false)
                } else if (clean.contains("T")) {
                    Pair(localFormat.parse(clean)?.time ?: 0L, false)
                } else if (clean.length == 8) {
                    Pair(dateFormat.parse(clean)?.time ?: 0L, true) // All day date
                } else {
                    Pair(clean.toLongOrNull() ?: 0L, false)
                }
            } catch (_: Exception) {
                Pair(0L, false)
            }
        }

        for (line in rawLines) {
            val upper = line.uppercase()
            if (upper == "BEGIN:VEVENT") {
                inVEvent = true
                currentTitle = null
                currentDesc = null
                currentLoc = null
                currentDtStart = 0L
                currentDtEnd = null
                currentDuration = null
                currentAllDay = 0
                currentTz = null
                currentRrule = null
                currentExdate = null
                currentStatus = null
                currentAvailability = null
                currentUid = null
                currentReminders.clear()
                continue
            }

            if (upper == "END:VEVENT") {
                if (inVEvent && currentDtStart > 0L) {
                    val eventId = (events.size + 1).toLong()
                    events.add(
                        SyncEvent(
                            id = eventId,
                            title = currentTitle,
                            description = currentDesc,
                            location = currentLoc,
                            dtStart = currentDtStart,
                            dtEnd = currentDtEnd,
                            duration = currentDuration,
                            allDay = currentAllDay,
                            timeZone = currentTz ?: TimeZone.getDefault().id,
                            rrule = currentRrule,
                            exdate = currentExdate,
                            status = currentStatus,
                            availability = currentAvailability,
                            syncId = currentUid,
                            reminders = currentReminders.toList()
                        )
                    )
                }
                inVEvent = false
                continue
            }

            if (!inVEvent) continue

            if (upper == "BEGIN:VALARM") {
                inVAlarm = true
                alarmTriggerMinutes = null
                continue
            }
            if (upper == "END:VALARM") {
                inVAlarm = false
                alarmTriggerMinutes?.let { mins ->
                    currentReminders.add(SyncReminder(minutes = mins, method = 1))
                }
                continue
            }

            if (inVAlarm) {
                if (upper.startsWith("TRIGGER")) {
                    val value = line.substringAfter(":").trim()
                    // e.g. -PT15M or -P1D
                    val minutesMatch = Regex("""-?P(?:T(?:(\d+)H)?(?:(\d+)M)?)?""").find(value)
                    if (minutesMatch != null) {
                        val hours = minutesMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
                        val mins = minutesMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                        alarmTriggerMinutes = hours * 60 + mins
                    }
                }
                continue
            }

            val colonIdx = line.indexOf(':')
            if (colonIdx == -1) continue
            val propKeyPart = line.substring(0, colonIdx)
            val propValue = line.substring(colonIdx + 1)
            val propName = propKeyPart.substringBefore(';').trim().uppercase()

            when (propName) {
                "UID" -> currentUid = propValue.trim()
                "SUMMARY" -> currentTitle = unescapeIcsText(propValue)
                "DESCRIPTION" -> currentDesc = unescapeIcsText(propValue)
                "LOCATION" -> currentLoc = unescapeIcsText(propValue)
                "DTSTART" -> {
                    val (time, isDateOnly) = parseIcsDate(propValue)
                    currentDtStart = time
                    if (isDateOnly || propKeyPart.uppercase().contains("VALUE=DATE")) {
                        currentAllDay = 1
                    }
                }
                "DTEND" -> {
                    val (time, isDateOnly) = parseIcsDate(propValue)
                    currentDtEnd = time
                    if (isDateOnly || propKeyPart.uppercase().contains("VALUE=DATE")) {
                        currentAllDay = 1
                    }
                }
                "DURATION" -> currentDuration = propValue.trim()
                "RRULE" -> currentRrule = propValue.trim()
                "EXDATE" -> currentExdate = propValue.trim()
                "STATUS" -> {
                    currentStatus = when (propValue.trim().uppercase()) {
                        "CONFIRMED" -> 1
                        "CANCELLED" -> 2
                        "TENTATIVE" -> 0
                        else -> null
                    }
                }
                "TRANSP" -> {
                    currentAvailability = when (propValue.trim().uppercase()) {
                        "TRANSPARENT" -> 1 // Free
                        "OPAQUE" -> 0      // Busy
                        else -> null
                    }
                }
            }
        }

        return events
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CSV Calendar Serializer
    // ─────────────────────────────────────────────────────────────────────────

    fun serializeCsv(events: List<SyncEvent>): String {
        val sb = StringBuilder()
        // Standard Google / Outlook CSV Headers
        sb.append("Subject,Start Date,Start Time,End Date,End Time,All Day Event,Description,Location,Private\r\n")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

        for (event in events) {
            val isAllDay = event.allDay == 1
            val startDateStr = dateFormat.format(Date(event.dtStart))
            val startTimeStr = if (isAllDay) "" else timeFormat.format(Date(event.dtStart))

            val endDate = if (event.dtEnd != null && event.dtEnd > 0) Date(event.dtEnd) else Date(event.dtStart)
            val endDateStr = dateFormat.format(endDate)
            val endTimeStr = if (isAllDay) "" else timeFormat.format(endDate)

            val subject = escapeCsv(event.title ?: "")
            val allDayStr = if (isAllDay) "True" else "False"
            val desc = escapeCsv(event.description ?: "")
            val loc = escapeCsv(event.location ?: "")
            val isPrivate = if (event.accessLevel == 2) "True" else "False"

            sb.append("$subject,$startDateStr,$startTimeStr,$endDateStr,$endTimeStr,$allDayStr,$desc,$loc,$isPrivate\r\n")
        }

        return sb.toString()
    }

    fun serializeCsvToStream(events: List<SyncEvent>, outputStream: OutputStream) {
        val csvString = serializeCsv(events)
        outputStream.bufferedWriter(Charsets.UTF_8).use {
            it.write(csvString)
            it.flush()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers: Text Escaping / Unescaping
    // ─────────────────────────────────────────────────────────────────────────

    private fun escapeIcsText(text: String): String {
        return text.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")
    }

    private fun unescapeIcsText(text: String): String {
        return text.replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
    }

    private fun escapeCsv(text: String): String {
        val needsQuotes = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")
        val escaped = text.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
