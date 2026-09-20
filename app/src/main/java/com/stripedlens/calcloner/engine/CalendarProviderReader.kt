package com.stripedlens.calcloner.engine

import android.content.Context
import android.provider.CalendarContract
import com.stripedlens.calcloner.CalendarInfo
import com.stripedlens.calcloner.SyncAttendee
import com.stripedlens.calcloner.SyncEvent
import com.stripedlens.calcloner.SyncReminder

/**
 * Domain component dedicated to performing read-only queries against Android's CalendarProvider.
 *
 * Guarantees zero write mutations, providing safe event counting, calendar enumeration,
 * source event collection, and fast database fingerprinting.
 */
object CalendarProviderReader {

    private val CALCLONER_TAG_REGEX = com.stripedlens.calcloner.AppConstants.Engine.CALCLONER_TAG_REGEX

    /**
     * Query count of active events in a given calendar.
     */
    fun getCalendarEventCount(context: Context, calendarId: Long): Int {
        val projection = arrayOf(CalendarContract.Events._ID)
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0"
        val selectionArgs = arrayOf(calendarId.toString())
        return try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { it.count } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Query all user-accessible calendars on the device.
     */
    fun getAvailableCalendars(context: Context): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.CALENDAR_COLOR
        )

        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
        ) ?: return calendars

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val primaryCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)
            val accessCol = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            val colorCol = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)

            while (it.moveToNext()) {
                val accessLevel = if (accessCol != -1 && !it.isNull(accessCol)) it.getInt(accessCol) else 700
                val color = if (colorCol != -1 && !it.isNull(colorCol)) it.getInt(colorCol) else null
                calendars.add(
                    CalendarInfo(
                        id = it.getLong(idCol),
                        displayName = it.getString(nameCol) ?: "Unnamed Calendar",
                        accountName = it.getString(accCol) ?: "Local",
                        isPrimary = it.getInt(primaryCol) == 1,
                        accessLevel = accessLevel,
                        color = color
                    )
                )
            }
        }
        return calendars
    }

    /**
     * Checks if a given calendar ID exists and is accessible.
     */
    fun calendarExists(context: Context, calendarId: Long): Boolean {
        return try {
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                "${CalendarContract.Calendars._ID} = ?",
                arrayOf(calendarId.toString()),
                null
            )
            cursor?.use { it.moveToFirst() } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Reads all active events within the configured sync window from the source calendar.
     * Collects recurrence rules, recurring exception links, and event reminders.
     */
    fun readSourceEvents(
        context: Context,
        fromCalendarId: Long,
        daysPast: Int = 30,
        daysFuture: Int = 30
    ): List<SyncEvent> {
        val rawEvents = mutableListOf<SyncEvent>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.ORIGINAL_ID,
            CalendarContract.Events.ORIGINAL_SYNC_ID,
            CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
            CalendarContract.Events.STATUS,
            CalendarContract.Events._SYNC_ID,
            CalendarContract.Events.AVAILABILITY,
            CalendarContract.Events.ACCESS_LEVEL,
            CalendarContract.Events.EXDATE
        )

        val now = System.currentTimeMillis()
        val windowStart = now - (daysPast.toLong().coerceAtLeast(1) * 24 * 60 * 60 * 1000)
        val windowEnd = now + (daysFuture.toLong().coerceAtLeast(1) * 24 * 60 * 60 * 1000)

        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0 AND (" +
                "${CalendarContract.Events.DTSTART} >= ? OR " +
                "${CalendarContract.Events.RRULE} IS NOT NULL OR " +
                "${CalendarContract.Events.ORIGINAL_ID} IS NOT NULL OR " +
                "${CalendarContract.Events.ORIGINAL_SYNC_ID} IS NOT NULL" +
                ")"
        val selectionArgs = arrayOf(fromCalendarId.toString(), windowStart.toString())

        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        ) ?: return emptyList()

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val titleCol = it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val descCol = it.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
            val locCol = it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
            val startCol = it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val endCol = it.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            val durCol = it.getColumnIndexOrThrow(CalendarContract.Events.DURATION)
            val allDayCol = it.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
            val tzCol = it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_TIMEZONE)
            val rruleCol = it.getColumnIndexOrThrow(CalendarContract.Events.RRULE)
            val origIdCol = it.getColumnIndex(CalendarContract.Events.ORIGINAL_ID)
            val origSyncIdCol = it.getColumnIndex(CalendarContract.Events.ORIGINAL_SYNC_ID)
            val origInstCol = it.getColumnIndex(CalendarContract.Events.ORIGINAL_INSTANCE_TIME)
            val statusCol = it.getColumnIndex(CalendarContract.Events.STATUS)
            val syncIdCol = it.getColumnIndex(CalendarContract.Events._SYNC_ID)
            val availCol = it.getColumnIndex(CalendarContract.Events.AVAILABILITY)
            val accessCol = it.getColumnIndex(CalendarContract.Events.ACCESS_LEVEL)
            val exdateCol = it.getColumnIndex(CalendarContract.Events.EXDATE)

            while (it.moveToNext()) {
                val dtStart = it.getLong(startCol)
                val rrule = it.getString(rruleCol)
                val originalId = if (origIdCol != -1 && !it.isNull(origIdCol)) it.getLong(origIdCol) else null
                val originalSyncId = if (origSyncIdCol != -1 && !it.isNull(origSyncIdCol)) it.getString(origSyncIdCol) else null
                val originalInstanceTime = if (origInstCol != -1 && !it.isNull(origInstCol)) it.getLong(origInstCol) else null
                val status = if (statusCol != -1 && !it.isNull(statusCol)) it.getInt(statusCol) else null
                val syncId = if (syncIdCol != -1 && !it.isNull(syncIdCol)) it.getString(syncIdCol) else null
                val availability = if (availCol != -1 && !it.isNull(availCol)) it.getInt(availCol) else null
                val accessLevel = if (accessCol != -1 && !it.isNull(accessCol)) it.getInt(accessCol) else null
                val exdate = if (exdateCol != -1 && !it.isNull(exdateCol)) it.getString(exdateCol) else null

                val isRecurring = !rrule.isNullOrEmpty()
                val effectiveTime = if (dtStart > 0) dtStart else (originalInstanceTime ?: 0L)
                if (!isRecurring && (effectiveTime < windowStart || effectiveTime > windowEnd)) {
                    continue
                }

                rawEvents.add(
                    SyncEvent(
                        id = it.getLong(idCol),
                        title = it.getString(titleCol),
                        description = it.getString(descCol),
                        location = it.getString(locCol),
                        dtStart = dtStart,
                        dtEnd = if (it.isNull(endCol)) null else it.getLong(endCol),
                        duration = it.getString(durCol),
                        allDay = it.getInt(allDayCol),
                        timeZone = it.getString(tzCol),
                        rrule = rrule,
                        originalId = originalId,
                        originalSyncId = originalSyncId,
                        originalInstanceTime = originalInstanceTime,
                        status = status,
                        syncId = syncId,
                        availability = availability,
                        accessLevel = accessLevel,
                        exdate = exdate
                    )
                )
            }
        }

        if (rawEvents.isEmpty()) return emptyList()

        // Bulk-query reminders for all selected source events
        val remindersMap = mutableMapOf<Long, MutableList<SyncReminder>>()
        val eventIds = rawEvents.map { it.id }
        eventIds.chunked(150).forEach { chunk ->
            val inClause = chunk.joinToString(",")
            val remSelection = "${CalendarContract.Reminders.EVENT_ID} IN ($inClause)"
            val remProjection = arrayOf(
                CalendarContract.Reminders.EVENT_ID,
                CalendarContract.Reminders.MINUTES,
                CalendarContract.Reminders.METHOD
            )
            try {
                context.contentResolver.query(
                    CalendarContract.Reminders.CONTENT_URI,
                    remProjection,
                    remSelection,
                    null,
                    null
                )?.use { cur ->
                    val eventIdCol = cur.getColumnIndexOrThrow(CalendarContract.Reminders.EVENT_ID)
                    val minCol = cur.getColumnIndexOrThrow(CalendarContract.Reminders.MINUTES)
                    val methodCol = cur.getColumnIndexOrThrow(CalendarContract.Reminders.METHOD)
                    while (cur.moveToNext()) {
                        val eId = cur.getLong(eventIdCol)
                        val mins = cur.getInt(minCol)
                        val method = cur.getInt(methodCol)
                        remindersMap.getOrPut(eId) { mutableListOf() }.add(
                            SyncReminder(minutes = mins, method = method)
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        // Bulk-query attendees for all selected source events
        val attendeesMap = mutableMapOf<Long, MutableList<SyncAttendee>>()
        eventIds.chunked(150).forEach { chunk ->
            val inClause = chunk.joinToString(",")
            val attSelection = "${CalendarContract.Attendees.EVENT_ID} IN ($inClause)"
            val attProjection = arrayOf(
                CalendarContract.Attendees.EVENT_ID,
                CalendarContract.Attendees.ATTENDEE_NAME,
                CalendarContract.Attendees.ATTENDEE_EMAIL,
                CalendarContract.Attendees.ATTENDEE_STATUS
            )
            try {
                context.contentResolver.query(
                    CalendarContract.Attendees.CONTENT_URI,
                    attProjection,
                    attSelection,
                    null,
                    null
                )?.use { cur ->
                    val eventIdCol = cur.getColumnIndexOrThrow(CalendarContract.Attendees.EVENT_ID)
                    val nameCol = cur.getColumnIndexOrThrow(CalendarContract.Attendees.ATTENDEE_NAME)
                    val emailCol = cur.getColumnIndexOrThrow(CalendarContract.Attendees.ATTENDEE_EMAIL)
                    val statusCol = cur.getColumnIndexOrThrow(CalendarContract.Attendees.ATTENDEE_STATUS)
                    while (cur.moveToNext()) {
                        val eId = cur.getLong(eventIdCol)
                        val name = cur.getString(nameCol)
                        val email = cur.getString(emailCol)
                        val status = cur.getInt(statusCol)
                        attendeesMap.getOrPut(eId) { mutableListOf() }.add(
                            SyncAttendee(name = name, email = email, status = status)
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        return rawEvents.map { event ->
            val rems = remindersMap[event.id] ?: emptyList()
            val atts = attendeesMap[event.id] ?: emptyList()
            event.copy(reminders = rems, attendees = atts)
        }
    }

    /**
     * Counts cloned events on the target calendar belonging to a specific sync pair,
     * carefully distinguishing them from organic events and other active pairs.
     */
    fun getTargetClonedEventCount(
        context: Context,
        toCalendarId: Long,
        pairId: String?,
        activePairIds: Set<String> = emptySet()
    ): Int {
        val queryCursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.CUSTOM_APP_URI, CalendarContract.Events.DESCRIPTION),
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0",
            arrayOf(toCalendarId.toString()),
            null
        ) ?: return 0
        var count = 0
        val uriPrefix = "calcloner://event/"
        val pairUriPrefix = if (pairId != null) "${uriPrefix}${pairId}/" else null
        val pairTagPrefix = if (pairId != null) "[CalCloner-ID: $pairId:" else null

        queryCursor.use {
            val uriCol = it.getColumnIndex(CalendarContract.Events.CUSTOM_APP_URI)
            val descCol = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            while (it.moveToNext()) {
                val customUri = if (uriCol != -1) it.getString(uriCol) else null
                val desc = if (descCol != -1) it.getString(descCol) else null

                var matchedPairId: String? = null
                if (customUri != null && customUri.startsWith(uriPrefix)) {
                    val rem = customUri.removePrefix(uriPrefix)
                    if (rem.contains("/")) matchedPairId = rem.substringBefore("/")
                }
                if (matchedPairId == null && desc != null) {
                    val tagMatch = CALCLONER_TAG_REGEX.find(desc)
                    matchedPairId = tagMatch?.groupValues?.getOrNull(1)?.ifEmpty { null }
                }

                // If event belongs to another active pair, do not count as this pair's event
                if (matchedPairId != null && activePairIds.contains(matchedPairId) && (pairId == null || matchedPairId != pairId)) {
                    continue
                }

                // Ignore organic events
                val isClonedEvent = (customUri != null && customUri.startsWith(uriPrefix)) ||
                        (desc != null && (desc.contains("[CalCloner-ID:") || desc.contains("[CalClone-ID:")))
                if (!isClonedEvent) {
                    continue
                }

                if (pairId != null) {
                    val isThisPair = (pairUriPrefix != null && customUri != null && customUri.startsWith(pairUriPrefix)) ||
                            (pairTagPrefix != null && desc != null && desc.contains(pairTagPrefix)) ||
                            (matchedPairId == null || !activePairIds.contains(matchedPairId))
                    if (isThisPair) {
                        count++
                    }
                } else {
                    count++
                }
            }
        }
        return count
    }

    /**
     * Quickly queries count and max event ID across source calendars for high-efficiency fingerprinting.
     * Executes in ~1-2ms and prevents redundant database scans if the source has not changed.
     */
    fun getSourceCalendarsFingerprint(context: Context, sourceCalendarIds: Set<Long>): String {
        if (sourceCalendarIds.isEmpty()) return ""
        val placeholders = sourceCalendarIds.joinToString(",") { "?" }
        val args = sourceCalendarIds.map { it.toString() }.toTypedArray()
        return try {
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    "COUNT(*)",
                    "MAX(${CalendarContract.Events._ID})"
                ),
                "${CalendarContract.Events.CALENDAR_ID} IN ($placeholders) AND ${CalendarContract.Events.DELETED} = 0",
                args,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val count = it.getLong(0)
                    val maxId = if (!it.isNull(1)) it.getLong(1) else 0L
                    "$count:$maxId"
                } else ""
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
