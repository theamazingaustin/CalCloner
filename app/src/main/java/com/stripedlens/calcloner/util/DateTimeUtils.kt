package com.stripedlens.calcloner.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateTimeUtils {

    fun formatInterval(minutes: Int): String {
        return when {
            minutes <= 0 -> "Never (Manual Only)"
            minutes < 60 -> "$minutes minutes"
            minutes == 60 -> "1 hour"
            minutes < 1440 -> "${minutes / 60} hours"
            else -> "${minutes / 1440} day(s)"
        }
    }

    fun formatLastSync(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0L) return "Never"
        val zoneId = ZoneId.systemDefault()
        val syncDateTime = Instant.ofEpochMilli(timestamp).atZone(zoneId)
        val syncDate = syncDateTime.toLocalDate()
        val today = LocalDate.now(zoneId)
        val yesterday = today.minusDays(1)
        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
        val timeStr = syncDateTime.format(timeFormatter)

        val datePart = when {
            syncDate.isEqual(today) -> "Today"
            syncDate.isEqual(yesterday) -> "Yesterday"
            else -> syncDateTime.format(DateTimeFormatter.ofPattern("MMM, d"))
        }
        return "$datePart at $timeStr"
    }

    fun formatDays(days: Int): String {
        return when {
            days == 7 -> "7 days (1 week)"
            days == 14 -> "14 days (2 weeks)"
            days == 30 -> "30 days (1 month)"
            days == 60 -> "60 days (2 months)"
            days == 90 -> "90 days (3 months)"
            days == 180 -> "180 days (6 months)"
            days == 365 -> "365 days (1 year)"
            days == 730 -> "730 days (2 years)"
            days % 365 == 0 -> "${days / 365} year(s)"
            days % 30 == 0 -> "${days / 30} month(s)"
            else -> "$days days"
        }
    }
}
