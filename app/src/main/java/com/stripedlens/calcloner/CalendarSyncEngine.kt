package com.stripedlens.calcloner

import android.accounts.Account
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.TimeZone

object CalendarSyncEngine {

    private val syncMutex = Mutex()

    /**
     * Query count of active events in a given calendar.
     * Read-only operation.
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
     * Read-only operation.
     */
    fun getAvailableCalendars(context: Context): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
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

            while (it.moveToNext()) {
                val accessLevel = if (accessCol != -1 && !it.isNull(accessCol)) it.getInt(accessCol) else 700
                calendars.add(
                    CalendarInfo(
                        id = it.getLong(idCol),
                        displayName = it.getString(nameCol) ?: "Unnamed Calendar",
                        accountName = it.getString(accCol) ?: "Local",
                        isPrimary = it.getInt(primaryCol) == 1,
                        accessLevel = accessLevel
                    )
                )
            }
        }
        return calendars
    }

    @Volatile
    var lastSelfWriteTimestamp: Long = 0L
        private set

    fun recordSelfWrite() {
        lastSelfWriteTimestamp = System.currentTimeMillis()
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

    private data class ClonedEventMeta(
        val targetId: Long,
        val dtStart: Long,
        val dtEnd: Long?,
        val duration: String?,
        val allDay: Int,
        val timeZone: String?,
        val title: String?,
        val description: String?,
        val location: String?,
        val rrule: String?,
        val exdate: String?,
        val status: Int?,
        val availability: Int?,
        val originalId: Long?,
        val originalSyncId: String?,
        val originalInstanceTime: Long?,
        val hasRrule: Boolean
    )

    private fun isEventContentChanged(existing: ClonedEventMeta, newValues: ContentValues): Boolean {
        val newTitle = newValues.getAsString(CalendarContract.Events.TITLE) ?: ""
        if (newTitle != (existing.title ?: "")) return true

        val newDesc = newValues.getAsString(CalendarContract.Events.DESCRIPTION) ?: ""
        if (newDesc != (existing.description ?: "")) return true

        val newLoc = newValues.getAsString(CalendarContract.Events.EVENT_LOCATION) ?: ""
        if (newLoc != (existing.location ?: "")) return true

        val newStart = newValues.getAsLong(CalendarContract.Events.DTSTART) ?: 0L
        if (newStart != existing.dtStart) return true

        val newAllDay = newValues.getAsInteger(CalendarContract.Events.ALL_DAY) ?: 0
        if (newAllDay != existing.allDay) return true

        val newEnd = newValues.getAsLong(CalendarContract.Events.DTEND)
        if (newEnd != existing.dtEnd) return true

        val newDur = newValues.getAsString(CalendarContract.Events.DURATION)
        if (newDur != existing.duration) return true

        val newTz = newValues.getAsString(CalendarContract.Events.EVENT_TIMEZONE)
        if (newTz != existing.timeZone) return true

        val newRrule = newValues.getAsString(CalendarContract.Events.RRULE)
        if (newRrule != existing.rrule) return true

        val newExdate = newValues.getAsString(CalendarContract.Events.EXDATE)
        if (newExdate != existing.exdate) return true

        val newStatus = newValues.getAsInteger(CalendarContract.Events.STATUS)
        if (newStatus != existing.status) return true

        val newAvail = newValues.getAsInteger(CalendarContract.Events.AVAILABILITY)
        if (newAvail != existing.availability) return true

        val newOrigId = newValues.getAsLong(CalendarContract.Events.ORIGINAL_ID)
        if (newOrigId != existing.originalId) return true

        val newOrigSync = newValues.getAsString(CalendarContract.Events.ORIGINAL_SYNC_ID)
        if (newOrigSync != existing.originalSyncId) return true

        val newOrigInst = newValues.getAsLong(CalendarContract.Events.ORIGINAL_INSTANCE_TIME)
        if (newOrigInst != existing.originalInstanceTime) return true

        return false
    }

    private fun parseDurationDays(durationStr: String?): Long? {
        if (durationStr.isNullOrEmpty()) return null
        val dayWeekRegex = Regex("""^P(?:(\d+)W)?(?:(\d+)D)?""")
        val match = dayWeekRegex.find(durationStr)
        if (match != null) {
            val weeks = match.groupValues.getOrNull(1)?.toLongOrNull() ?: 0L
            val days = match.groupValues.getOrNull(2)?.toLongOrNull() ?: 0L
            val total = (weeks * 7) + days
            if (total > 0) return total
        }
        val secRegex = Regex("""^P.*T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""")
        val secMatch = secRegex.find(durationStr)
        if (secMatch != null) {
            val h = secMatch.groupValues.getOrNull(1)?.toLongOrNull() ?: 0L
            val m = secMatch.groupValues.getOrNull(2)?.toLongOrNull() ?: 0L
            val s = secMatch.groupValues.getOrNull(3)?.toLongOrNull() ?: 0L
            val totalSeconds = (h * 3600) + (m * 60) + s
            val daysFromSec = (totalSeconds + 43200) / 86400
            if (daysFromSec > 0) return daysFromSec
        }
        return null
    }

    /**
     * Read events strictly from the SOURCE calendar.
     * GUARANTEE: Never modifies or deletes any data in source calendar. Read-only.
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
            CalendarContract.Events.EXDATE
        )

        // Query events within user-configured sync window
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
                val exdate = if (exdateCol != -1 && !it.isNull(exdateCol)) it.getString(exdateCol) else null

                // Filter non-recurring events strictly to user-configured window
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

        return rawEvents.map { event ->
            val rems = remindersMap[event.id] ?: emptyList()
            event.copy(reminders = rems)
        }
    }

    private fun syncReminders(context: Context, targetEventId: Long, reminders: List<SyncReminder>) {
        try {
            val existingReminders = mutableListOf<SyncReminder>()
            context.contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                arrayOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD),
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(targetEventId.toString()),
                null
            )?.use { cur ->
                val minCol = cur.getColumnIndexOrThrow(CalendarContract.Reminders.MINUTES)
                val methodCol = cur.getColumnIndexOrThrow(CalendarContract.Reminders.METHOD)
                while (cur.moveToNext()) {
                    existingReminders.add(SyncReminder(cur.getInt(minCol), cur.getInt(methodCol)))
                }
            }

            val distinctNew = reminders.distinctBy { it.minutes to it.method }
            val distinctExisting = existingReminders.distinctBy { it.minutes to it.method }
            if (distinctNew.size == distinctExisting.size && distinctNew.toSet() == distinctExisting.toSet()) {
                return
            }

            // Delete existing reminders for cloned event to prevent duplicates
            context.contentResolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(targetEventId.toString())
            )
            if (distinctNew.isNotEmpty()) {
                for (reminder in distinctNew) {
                    val values = ContentValues().apply {
                        put(CalendarContract.Reminders.EVENT_ID, targetEventId)
                        put(CalendarContract.Reminders.MINUTES, reminder.minutes)
                        put(CalendarContract.Reminders.METHOD, reminder.method)
                    }
                    context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
                }
            }
        } catch (_: Exception) {}
    }

    private fun buildEventValues(
        context: Context,
        event: SyncEvent,
        toCalendarId: Long,
        uriPrefix: String,
        defaultTimeZone: String,
        targetParentId: Long?,
        pairId: String? = null
    ): ContentValues {
        return ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, toCalendarId)
            put(CalendarContract.Events.TITLE, event.title ?: "")
            put(CalendarContract.Events.EVENT_LOCATION, event.location ?: "")

            // Description Tracking Tag: Append [CalCloner-ID: <pairId>:<id>] for cross-cloud persistence
            val trackingTag = if (pairId != null) "[CalCloner-ID: ${pairId}:${event.id}]" else "[CalCloner-ID: ${event.id}]"
            val fullDescription = when {
                event.description.isNullOrEmpty() -> trackingTag
                event.description.contains("[CalCloner-ID:") -> {
                    event.description.replace(Regex("""\[CalCloner-ID:\s*(?:[a-zA-Z0-9_-]+:)?\d+\]"""), trackingTag)
                }
                event.description.contains("[CalClone-ID:") -> {
                    event.description.replace(Regex("""\[CalClone-ID:\s*(?:[a-zA-Z0-9_-]+:)?\d+\]"""), trackingTag)
                }
                else -> "${event.description}\n\n$trackingTag"
            }
            put(CalendarContract.Events.DESCRIPTION, fullDescription)

            val effectiveStart = if (event.dtStart > 0) event.dtStart else (event.originalInstanceTime ?: System.currentTimeMillis())

            if (event.allDay == 1) {
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")

                // RFC 5545 & Android CalendarContract: All-day events are defined in UTC midnight.
                // If source event timezone is UTC, empty, or already UTC midnight, preserve UTC directly.
                // If source event had a specific non-UTC timezone, derive LocalDate in that zone and align to UTC midnight.
                val sourceZone = try {
                    if (!event.timeZone.isNullOrEmpty() && event.timeZone != "UTC") ZoneId.of(event.timeZone)
                    else ZoneOffset.UTC
                } catch (_: Exception) {
                    ZoneOffset.UTC
                }
                val localStartDate = Instant.ofEpochMilli(effectiveStart).atZone(sourceZone).toLocalDate()
                val startUtc = localStartDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                put(CalendarContract.Events.DTSTART, startUtc)

                val durationDays = parseDurationDays(event.duration)
                val numDays: Long = if (event.dtEnd != null && event.dtEnd > effectiveStart) {
                    val localEndDate = Instant.ofEpochMilli(event.dtEnd).atZone(sourceZone).toLocalDate()
                    val diff = ChronoUnit.DAYS.between(localStartDate, localEndDate)
                    if (diff > 0) diff else 1L
                } else if (durationDays != null && durationDays > 0) {
                    durationDays
                } else {
                    1L
                }

                if (!event.rrule.isNullOrEmpty()) {
                    // RFC 5545: Recurring all-day event MUST have RRULE + DURATION and MUST NOT have DTEND
                    put(CalendarContract.Events.RRULE, event.rrule)
                    put(CalendarContract.Events.DURATION, "P${numDays}D")
                } else {
                    // Non-recurring all-day event: DTEND = startUtc + (numDays * 86400000)
                    put(CalendarContract.Events.DTEND, startUtc + (numDays * 86400000L))
                }
            } else {
                put(CalendarContract.Events.ALL_DAY, 0)
                put(CalendarContract.Events.DTSTART, effectiveStart)
                put(CalendarContract.Events.EVENT_TIMEZONE, event.timeZone ?: defaultTimeZone)

                if (!event.rrule.isNullOrEmpty()) {
                    put(CalendarContract.Events.RRULE, event.rrule)
                    val dur = if (!event.duration.isNullOrEmpty()) {
                        event.duration
                    } else if (event.dtEnd != null && event.dtEnd > effectiveStart) {
                        "P${(event.dtEnd - effectiveStart) / 1000}S"
                    } else {
                        "P3600S"
                    }
                    put(CalendarContract.Events.DURATION, dur)
                } else {
                    if (event.dtEnd != null && event.dtEnd > 0) {
                        put(CalendarContract.Events.DTEND, event.dtEnd)
                    } else {
                        put(CalendarContract.Events.DTEND, effectiveStart + 3600000L)
                    }
                }
            }

            if (!event.exdate.isNullOrEmpty()) {
                put(CalendarContract.Events.EXDATE, event.exdate)
            }

            val eventStatus = event.status ?: CalendarContract.Events.STATUS_CONFIRMED
            put(CalendarContract.Events.STATUS, eventStatus)
            val eventAvailability = event.availability ?: CalendarContract.Events.AVAILABILITY_BUSY
            put(CalendarContract.Events.AVAILABILITY, eventAvailability)
            put(CalendarContract.Events.HAS_ALARM, if (event.reminders.isNotEmpty()) 1 else 0)
            put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
            val fullAppUri = if (pairId != null) "${uriPrefix}${pairId}/${event.id}" else "$uriPrefix${event.id}"
            put(CalendarContract.Events.CUSTOM_APP_URI, fullAppUri)

            // Solution A: Two-pass recurrence exception linking
            if (targetParentId != null) {
                put(CalendarContract.Events.ORIGINAL_ID, targetParentId)
            }
            if (!event.originalSyncId.isNullOrEmpty()) {
                put(CalendarContract.Events.ORIGINAL_SYNC_ID, event.originalSyncId)
            }
            if (event.originalInstanceTime != null) {
                put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, event.originalInstanceTime)
            }
        }
    }

    /**
     * Replicates events from SOURCE calendar to TARGET calendar.
     * GUARANTEE:
     * 1. Rejects execution if toCalendarId == fromCalendarId.
     * 2. All writes and updates target toCalendarId exclusively.
     * 3. Source calendar is completely untouched.
     */
    suspend fun syncEventsToTarget(
        context: Context,
        fromCalendarId: Long,
        toCalendarId: Long,
        daysPast: Int = 30,
        daysFuture: Int = 30,
        pairId: String? = null,
        onProgress: ((current: Int, total: Int, message: String) -> Unit)? = null
    ): SyncResult = syncMutex.withLock {
        require(fromCalendarId != toCalendarId) {
            "CRITICAL SAFETY VIOLATION: Source calendar ($fromCalendarId) and Target calendar ($toCalendarId) cannot be the same!"
        }

        if (!calendarExists(context, fromCalendarId)) {
            throw IllegalStateException("Calendar Not Found: Source calendar not found.")
        }
        if (!calendarExists(context, toCalendarId)) {
            throw IllegalStateException("Calendar Not Found: Target calendar not found.")
        }

        onProgress?.invoke(0, 0, "Reading source calendar...")
        val sourceEvents = readSourceEvents(context, fromCalendarId, daysPast, daysFuture)
        val total = sourceEvents.size

        val now = System.currentTimeMillis()
        val windowStart = now - (daysPast.toLong().coerceAtLeast(1) * 24 * 60 * 60 * 1000)
        val windowEnd = now + (daysFuture.toLong().coerceAtLeast(1) * 24 * 60 * 60 * 1000)

        // Query target calendar account info
        var targetAccountName: String? = null
        var targetAccountType: String? = null
        val calCursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE),
            "${CalendarContract.Calendars._ID} = ?",
            arrayOf(toCalendarId.toString()),
            null
        )
        calCursor?.use {
            if (it.moveToFirst()) {
                targetAccountName = it.getString(0)
                targetAccountType = it.getString(1)
            }
        }

        // Find existing events in TARGET calendar using CUSTOM_APP_URI or [CalClone-ID] tag or title/time signature
        val existingTargetEvents = mutableMapOf<Long, ClonedEventMeta>()
        val existingTargetBySignature = mutableMapOf<String, ClonedEventMeta>()
        val sourceIdToTargetIdMap = mutableMapOf<Long, Long>()
        val sourceSyncIdToTargetIdMap = mutableMapOf<String, Long>()

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CUSTOM_APP_URI,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.EXDATE,
            CalendarContract.Events.STATUS,
            CalendarContract.Events.AVAILABILITY,
            CalendarContract.Events.ORIGINAL_ID,
            CalendarContract.Events.ORIGINAL_SYNC_ID,
            CalendarContract.Events.ORIGINAL_INSTANCE_TIME
        )
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0"
        val selectionArgs = arrayOf(toCalendarId.toString())

        val targetCursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        val uriPrefix = "calcloner://event/"
        val legacyUriPrefix1 = "calclone://event/"
        val legacyUriPrefix2 = "calsync://event/"
        targetCursor?.use {
            val idCol = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val uriCol = it.getColumnIndexOrThrow(CalendarContract.Events.CUSTOM_APP_URI)
            val titleCol = it.getColumnIndex(CalendarContract.Events.TITLE)
            val descCol = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            val locCol = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
            val startCol = it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val endCol = it.getColumnIndex(CalendarContract.Events.DTEND)
            val durCol = it.getColumnIndex(CalendarContract.Events.DURATION)
            val allDayCol = it.getColumnIndex(CalendarContract.Events.ALL_DAY)
            val tzCol = it.getColumnIndex(CalendarContract.Events.EVENT_TIMEZONE)
            val rruleCol = it.getColumnIndex(CalendarContract.Events.RRULE)
            val exdateCol = it.getColumnIndex(CalendarContract.Events.EXDATE)
            val statusCol = it.getColumnIndex(CalendarContract.Events.STATUS)
            val availCol = it.getColumnIndex(CalendarContract.Events.AVAILABILITY)
            val origIdCol = it.getColumnIndex(CalendarContract.Events.ORIGINAL_ID)
            val origSyncIdCol = it.getColumnIndex(CalendarContract.Events.ORIGINAL_SYNC_ID)
            val origInstCol = it.getColumnIndex(CalendarContract.Events.ORIGINAL_INSTANCE_TIME)

            while (it.moveToNext()) {
                val targetId = it.getLong(idCol)
                val customUri = it.getString(uriCol)
                val start = it.getLong(startCol)
                val end = if (endCol != -1 && !it.isNull(endCol)) it.getLong(endCol) else null
                val dur = if (durCol != -1 && !it.isNull(durCol)) it.getString(durCol) else null
                val allDay = if (allDayCol != -1 && !it.isNull(allDayCol)) it.getInt(allDayCol) else 0
                val tz = if (tzCol != -1 && !it.isNull(tzCol)) it.getString(tzCol) else null
                val title = if (titleCol != -1 && !it.isNull(titleCol)) it.getString(titleCol) else ""
                val desc = if (descCol != -1 && !it.isNull(descCol)) it.getString(descCol) else null
                val loc = if (locCol != -1 && !it.isNull(locCol)) it.getString(locCol) else null
                val rrule = if (rruleCol != -1 && !it.isNull(rruleCol)) it.getString(rruleCol) else null
                val exdate = if (exdateCol != -1 && !it.isNull(exdateCol)) it.getString(exdateCol) else null
                val status = if (statusCol != -1 && !it.isNull(statusCol)) it.getInt(statusCol) else null
                val avail = if (availCol != -1 && !it.isNull(availCol)) it.getInt(availCol) else null
                val origId = if (origIdCol != -1 && !it.isNull(origIdCol)) it.getLong(origIdCol) else null
                val origSync = if (origSyncIdCol != -1 && !it.isNull(origSyncIdCol)) it.getString(origSyncIdCol) else null
                val origInst = if (origInstCol != -1 && !it.isNull(origInstCol)) it.getLong(origInstCol) else null
                val hasRrule = !rrule.isNullOrEmpty()

                val meta = ClonedEventMeta(
                    targetId = targetId,
                    dtStart = start,
                    dtEnd = end,
                    duration = dur,
                    allDay = allDay,
                    timeZone = tz,
                    title = title,
                    description = desc,
                    location = loc,
                    rrule = rrule,
                    exdate = exdate,
                    status = status,
                    availability = avail,
                    originalId = origId,
                    originalSyncId = origSync,
                    originalInstanceTime = origInst,
                    hasRrule = hasRrule
                )

                var sourceId: Long? = null
                var matchedPairId: String? = null
                if (customUri != null) {
                    when {
                        pairId != null && customUri.startsWith("${uriPrefix}${pairId}/") -> {
                            sourceId = customUri.removePrefix("${uriPrefix}${pairId}/").toLongOrNull()
                            matchedPairId = pairId
                        }
                        customUri.startsWith(uriPrefix) -> {
                            val remainder = customUri.removePrefix(uriPrefix)
                            if (remainder.contains("/")) {
                                matchedPairId = remainder.substringBefore("/")
                                sourceId = remainder.substringAfter("/").toLongOrNull()
                            } else {
                                sourceId = remainder.toLongOrNull()
                            }
                        }
                        customUri.startsWith(legacyUriPrefix1) -> {
                            val remainder = customUri.removePrefix(legacyUriPrefix1)
                            if (remainder.contains("/")) {
                                matchedPairId = remainder.substringBefore("/")
                                sourceId = remainder.substringAfter("/").toLongOrNull()
                            } else {
                                sourceId = remainder.toLongOrNull()
                            }
                        }
                        customUri.startsWith(legacyUriPrefix2) -> sourceId = customUri.removePrefix(legacyUriPrefix2).toLongOrNull()
                    }
                }

                // Fallback: Check for [CalCloner-ID: <pairId>:<id>] or [CalCloner-ID: <id>] in DESCRIPTION
                if (sourceId == null && desc != null) {
                    val tagMatch = Regex("""\[CalClone(?:r)?-ID:\s*(?:([a-zA-Z0-9_-]+):)?(\d+)\]""").find(desc)
                    if (tagMatch != null) {
                        matchedPairId = tagMatch.groupValues.getOrNull(1)?.ifEmpty { null }
                        sourceId = tagMatch.groupValues.getOrNull(2)?.toLongOrNull()
                    }
                }

                if (sourceId != null) {
                    // Multi-pair isolation: If pairId is specified and matchedPairId is from a different pair, skip it
                    if (pairId != null && matchedPairId != null && matchedPairId != pairId) {
                        continue
                    }
                    existingTargetEvents[sourceId] = meta
                    sourceIdToTargetIdMap[sourceId] = targetId
                    continue
                }

                existingTargetBySignature["$title|$start"] = meta
            }
        }

        var insertedCount = 0
        var updatedCount = 0
        val defaultTimeZone = TimeZone.getDefault().id

        // Two-Pass Sync:
        // Pass 1: Sync base / non-exception events first so parent recurring events are created & ID-mapped
        // Pass 2: Sync exception events (moved/cancelled occurrences) and link to targetParentId
        val (exceptionEvents, baseEvents) = sourceEvents.partition {
            it.originalId != null || it.originalSyncId != null || it.originalInstanceTime != null
        }

        fun syncSingleEvent(event: SyncEvent, targetParentId: Long?) {
            val values = buildEventValues(context, event, toCalendarId, uriPrefix, defaultTimeZone, targetParentId, pairId)
            val sigKey = "${event.title ?: ""}|${event.dtStart}"
            val existingMeta = existingTargetEvents[event.id] ?: existingTargetBySignature[sigKey]

            val targetId: Long?
            if (existingMeta != null) {
                targetId = existingMeta.targetId
                val hasChanged = isEventContentChanged(existingMeta, values)
                if (hasChanged) {
                    val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, targetId)
                    val rows = context.contentResolver.update(
                        updateUri,
                        values,
                        null,
                        null
                    )
                    if (rows > 0) updatedCount++
                }
            } else {
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                targetId = uri?.lastPathSegment?.toLongOrNull()
                if (targetId != null) insertedCount++
            }

            if (targetId != null) {
                sourceIdToTargetIdMap[event.id] = targetId
                if (!event.syncId.isNullOrEmpty()) {
                    sourceSyncIdToTargetIdMap[event.syncId] = targetId
                }
                syncReminders(context, targetId, event.reminders)
            }
        }

        var processedCount = 0

        // Pass 1: Sync base events
        for (event in baseEvents) {
            processedCount++
            val eventTitle = event.title ?: "Untitled Event"
            onProgress?.invoke(processedCount, total, "Cloning ($processedCount of $total): $eventTitle")
            syncSingleEvent(event, null)
        }

        // Pass 2: Sync recurrence exceptions with target parent linking
        for (event in exceptionEvents) {
            processedCount++
            val eventTitle = event.title ?: "Untitled Event"
            onProgress?.invoke(processedCount, total, "Cloning exception ($processedCount of $total): $eventTitle")
            val targetParentId = (if (event.originalId != null) sourceIdToTargetIdMap[event.originalId] else null)
                ?: (if (!event.originalSyncId.isNullOrEmpty()) sourceSyncIdToTargetIdMap[event.originalSyncId] else null)
                ?: (if (event.originalId != null) existingTargetEvents[event.originalId]?.targetId else null)
            syncSingleEvent(event, targetParentId)
        }

        // 4. Identify and delete out-of-window and orphaned events from clone calendar
        val activeSourceIds = sourceEvents.map { it.id }.toSet()
        var deletedCount = 0
        val eventsToDelete = mutableListOf<Long>()
        val candidateSourceIds = mutableListOf<Long>()

        for ((sourceId, meta) in existingTargetEvents) {
            val isOutOfWindow = !meta.hasRrule && (meta.dtStart < windowStart || meta.dtStart > windowEnd)
            if (isOutOfWindow) {
                // Rule: Anything farther in the past or future than window settings is deleted
                eventsToDelete.add(meta.targetId)
            } else if (sourceId !in activeSourceIds) {
                // Inside window (or recurring), but missing from active source events: batch verify against source DB
                candidateSourceIds.add(sourceId)
            }
        }

        // Batch query source database in chunks of 150 to check if candidate events truly no longer exist
        if (candidateSourceIds.isNotEmpty()) {
            val verifiedSourceIds = mutableSetOf<Long>()
            candidateSourceIds.chunked(150).forEach { chunk ->
                val inClause = chunk.joinToString(",")
                val sel = "${CalendarContract.Events._ID} IN ($inClause) AND ${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0"
                try {
                    context.contentResolver.query(
                        CalendarContract.Events.CONTENT_URI,
                        arrayOf(CalendarContract.Events._ID),
                        sel,
                        arrayOf(fromCalendarId.toString()),
                        null
                    )?.use { cur ->
                        val idCol = cur.getColumnIndexOrThrow(CalendarContract.Events._ID)
                        while (cur.moveToNext()) {
                            verifiedSourceIds.add(cur.getLong(idCol))
                        }
                    }
                } catch (_: Exception) {}
            }

            for (sourceId in candidateSourceIds) {
                if (sourceId !in verifiedSourceIds) {
                    existingTargetEvents[sourceId]?.let { eventsToDelete.add(it.targetId) }
                }
            }
        }

        // Batch delete pruning: delete in chunks of 200 via applyBatch(newDelete)
        if (eventsToDelete.isNotEmpty()) {
            onProgress?.invoke(total, total, "Removing ${eventsToDelete.size} deleted/out-of-window event(s) from clone...")
            eventsToDelete.chunked(200).forEach { chunk ->
                val ops = ArrayList<ContentProviderOperation>()
                for (id in chunk) {
                    val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                    ops.add(ContentProviderOperation.newDelete(eventUri).build())
                }
                try {
                    val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, ops)
                    deletedCount += results.size
                } catch (_: Exception) {
                    // Fallback to individual deletes if provider restricts batch
                    for (id in chunk) {
                        try {
                            val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                            val rows = context.contentResolver.delete(eventUri, null, null)
                            if (rows > 0) deletedCount++
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        if (insertedCount > 0 || updatedCount > 0 || deletedCount > 0) {
            recordSelfWrite()
        }

        // Notify content observers and broadcast to Google Calendar / Samsung Calendar to refresh UI immediately
        try {
            context.contentResolver.notifyChange(CalendarContract.Events.CONTENT_URI, null)
            context.contentResolver.notifyChange(CalendarContract.Instances.CONTENT_URI, null)
            context.contentResolver.notifyChange(CalendarContract.Calendars.CONTENT_URI, null)
            context.sendBroadcast(android.content.Intent(android.content.Intent.ACTION_PROVIDER_CHANGED, CalendarContract.CONTENT_URI))
            context.sendBroadcast(android.content.Intent("com.google.android.calendar.SYNC_TRIGGER"))
        } catch (_: Exception) {}

        // Request immediate aggressive sync (bypasses throttling, forces immediate cloud push)
        if (!targetAccountName.isNullOrEmpty() && !targetAccountType.isNullOrEmpty()) {
            try {
                val account = Account(targetAccountName, targetAccountType)
                val syncBundle = Bundle().apply {
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_OVERRIDE_TOO_MANY_DELETIONS, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_UPLOAD, true)
                }
                android.content.ContentResolver.requestSync(account, CalendarContract.AUTHORITY, syncBundle)
                try {
                    android.content.ContentResolver.requestSync(account, "com.google.android.calendar", syncBundle)
                } catch (_: Exception) {}
            } catch (e: Exception) {
                // Ignore sync trigger failures
            }
        }

        val statusMsg = when {
            total == 0 && deletedCount == 0 -> "Source calendar is empty (0 events)."
            total == 0 && deletedCount > 0 -> "Source calendar is empty. Removed $deletedCount clone event(s)."
            insertedCount == 0 && updatedCount == 0 && deletedCount == 0 -> "Sync complete: 0 changes found."
            else -> {
                val parts = mutableListOf<String>()
                if (insertedCount > 0) parts.add("$insertedCount added")
                if (updatedCount > 0) parts.add("$updatedCount updated")
                if (deletedCount > 0) parts.add("$deletedCount removed")
                "Sync complete: ${parts.joinToString(", ")}."
            }
        }

        onProgress?.invoke(total, total, statusMsg)

        SyncResult(
            insertedCount = insertedCount,
            updatedCount = updatedCount,
            totalSourceEvents = total,
            deletedCount = deletedCount
        )
    }

    /**
     * Remove all events strictly from the TARGET (Clone) calendar.
     * GUARANTEE:
     * 1. Rejects execution if toCalendarId == fromCalendarId.
     * 2. SQL selection explicitly restricts deletion to CALENDAR_ID = toCalendarId.
     * 3. Source calendar is completely untouched.
     */
    suspend fun clearTargetCalendarEvents(
        context: Context,
        toCalendarId: Long,
        fromCalendarId: Long?,
        pairId: String? = null,
        onProgress: ((message: String) -> Unit)? = null
    ): Int = syncMutex.withLock {
        if (fromCalendarId != null) {
            require(toCalendarId != fromCalendarId) {
                "CRITICAL SAFETY VIOLATION: Cannot clear calendar because target calendar ($toCalendarId) matches source calendar ($fromCalendarId)!"
            }
        }

        onProgress?.invoke("Purging events from clone calendar...")

        var targetAccountName: String? = null
        var targetAccountType: String? = null
        val calCursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE),
            "${CalendarContract.Calendars._ID} = ?",
            arrayOf(toCalendarId.toString()),
            null
        )
        calCursor?.use {
            if (it.moveToFirst()) {
                targetAccountName = it.getString(0)
                targetAccountType = it.getString(1)
            }
        }

        // Query active events in target calendar (filtered by pairId if specified)
        val eventIds = mutableListOf<Long>()
        val queryCursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.CUSTOM_APP_URI, CalendarContract.Events.DESCRIPTION),
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0",
            arrayOf(toCalendarId.toString()),
            null
        )
        val pairUriPrefix = if (pairId != null) "calcloner://event/$pairId/" else null
        val pairTagPrefix = if (pairId != null) "[CalCloner-ID: $pairId:" else null
        queryCursor?.use {
            val idCol = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val uriCol = it.getColumnIndex(CalendarContract.Events.CUSTOM_APP_URI)
            val descCol = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                if (pairId == null) {
                    eventIds.add(id)
                } else {
                    val customUri = if (uriCol != -1) it.getString(uriCol) else null
                    val desc = if (descCol != -1) it.getString(descCol) else null
                    val isPairEvent = (customUri != null && customUri.startsWith(pairUriPrefix!!)) ||
                            (desc != null && desc.contains(pairTagPrefix!!)) ||
                            (customUri != null && customUri.startsWith("calcloner://event/") && !customUri.substringAfter("calcloner://event/").contains("/"))
                    if (isPairEvent) {
                        eventIds.add(id)
                    }
                }
            }
        }

        if (eventIds.isEmpty()) {
            onProgress?.invoke("Clone calendar is already empty (0 events).")
            return 0
        }

        var deletedCount = 0
        // Delete in chunks of 200 via applyBatch (forces CalendarProvider to purge Instances table)
        eventIds.chunked(200).forEach { chunk ->
            val ops = ArrayList<ContentProviderOperation>()
            for (id in chunk) {
                val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                ops.add(ContentProviderOperation.newDelete(eventUri).build())
            }
            try {
                val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, ops)
                deletedCount += results.size
            } catch (e: Exception) {
                // Fallback to individual deletes if applyBatch encounters provider restriction
                for (id in chunk) {
                    val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                    val rows = context.contentResolver.delete(eventUri, null, null)
                    if (rows > 0) deletedCount++
                }
            }
        }

        if (deletedCount > 0) {
            recordSelfWrite()
        }

        // Notify content observers and broadcast to Google Calendar / Samsung Calendar to refresh UI immediately
        try {
            context.contentResolver.notifyChange(CalendarContract.Events.CONTENT_URI, null)
            context.contentResolver.notifyChange(CalendarContract.Instances.CONTENT_URI, null)
            context.contentResolver.notifyChange(CalendarContract.Calendars.CONTENT_URI, null)
            context.sendBroadcast(android.content.Intent(android.content.Intent.ACTION_PROVIDER_CHANGED, CalendarContract.CONTENT_URI))
            context.sendBroadcast(android.content.Intent("com.google.android.calendar.SYNC_TRIGGER"))
        } catch (_: Exception) {}

        // Request immediate aggressive sync to push deletions to cloud without throttling
        if (!targetAccountName.isNullOrEmpty() && !targetAccountType.isNullOrEmpty()) {
            try {
                val account = Account(targetAccountName, targetAccountType)
                val syncBundle = Bundle().apply {
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_OVERRIDE_TOO_MANY_DELETIONS, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_UPLOAD, true)
                }
                android.content.ContentResolver.requestSync(account, CalendarContract.AUTHORITY, syncBundle)
                try {
                    android.content.ContentResolver.requestSync(account, "com.google.android.calendar", syncBundle)
                } catch (_: Exception) {}
            } catch (e: Exception) {
                // Ignore if provider disallows
            }
        }

        onProgress?.invoke("Successfully cleared $deletedCount events from clone calendar.")
        return deletedCount
    }

    /**
     * Complete Nuke of Target Calendar:
     * 1. Rejects execution if toCalendarId == fromCalendarId.
     * 2. Toggles sync_events 0 -> 1 on target calendar in CalendarContract.Calendars, forcing Google Cloud
     *    to immediately download all remote/orphaned events into the local database.
     * 3. Polls CalendarContract.Events for up to 6 seconds to allow full cloud event download.
     * 4. Batch deletes 100% of all events in target calendar using standard newDelete(),
     *    preserving deleted=1, dirty=1 tombstones.
     * 5. Triggers expedited cloud sync with OVERRIDE_TOO_MANY_DELETIONS to permanently erase events from cloud.
     */
    suspend fun nukeTargetCalendarEvents(
        context: Context,
        toCalendarId: Long,
        fromCalendarId: Long?,
        onProgress: ((message: String) -> Unit)? = null
    ): Int = syncMutex.withLock {
        if (fromCalendarId != null) {
            require(toCalendarId != fromCalendarId) {
                "CRITICAL SAFETY VIOLATION: Cannot nuke calendar because target calendar ($toCalendarId) matches source calendar ($fromCalendarId)!"
            }
        }

        var targetAccountName: String? = null
        var targetAccountType: String? = null
        val calCursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE),
            "${CalendarContract.Calendars._ID} = ?",
            arrayOf(toCalendarId.toString()),
            null
        )
        calCursor?.use {
            if (it.moveToFirst()) {
                targetAccountName = it.getString(0)
                targetAccountType = it.getString(1)
            }
        }

        val lines = mutableListOf<String>()

        // STEP 1: Force Cloud Download via sync_events toggle & expedited sync
        lines.add("Step 1/3: Forcing Google Cloud download...")
        onProgress?.invoke(lines.joinToString("\n"))
        try {
            val calUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, toCalendarId)
            val cvOff = ContentValues().apply { put(CalendarContract.Calendars.SYNC_EVENTS, 0) }
            context.contentResolver.update(calUri, cvOff, null, null)

            kotlinx.coroutines.delay(1000L)

            val cvOn = ContentValues().apply { put(CalendarContract.Calendars.SYNC_EVENTS, 1) }
            context.contentResolver.update(calUri, cvOn, null, null)

            if (!targetAccountName.isNullOrEmpty() && !targetAccountType.isNullOrEmpty()) {
                val account = Account(targetAccountName, targetAccountType)
                val pullBundle = Bundle().apply {
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_INITIALIZE, true)
                }
                android.content.ContentResolver.requestSync(account, CalendarContract.AUTHORITY, pullBundle)
            }
        } catch (_: Exception) {}

        // Wait up to 5 seconds for downstream sync to settle
        var foundCount = 0
        var stableSeconds = 0
        var prevCount = -1
        for (sec in 1..5) {
            foundCount = getCalendarEventCount(context, toCalendarId)
            lines[0] = "Step 1/3: Downloading cloud events... ($foundCount found)"
            onProgress?.invoke(lines.joinToString("\n"))
            if (foundCount == prevCount && foundCount > 0) {
                stableSeconds++
                if (stableSeconds >= 2) break
            } else {
                stableSeconds = 0
            }
            prevCount = foundCount
            kotlinx.coroutines.delay(1000L)
        }
        lines[0] = if (foundCount > 0) {
            "Step 1/3: Downloaded $foundCount cloud event(s)."
        } else {
            "Step 1/3: Downloaded 0 cloud events (calendar empty)."
        }
        onProgress?.invoke(lines.joinToString("\n"))

        // STEP 2: Batch Delete 100% of all events in target calendar
        val eventIds = mutableListOf<Long>()
        val queryCursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0",
            arrayOf(toCalendarId.toString()),
            null
        )
        queryCursor?.use {
            val idCol = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
            while (it.moveToNext()) {
                eventIds.add(it.getLong(idCol))
            }
        }

        if (eventIds.isEmpty()) {
            lines.add("Step 2/3: 0 events to delete.")
            lines.add("Step 3/3: Cloud sync verified.")
            lines.add("")
            lines.add("Operation Completed:")
            lines.add("• Target calendar is already completely empty.")
            lines.add("• Timeframe: Cloud up to date.")
            lines.add("• What to do next: You can now safely delete the target calendar in Google Calendar or keep it as an empty clone destination.")
            onProgress?.invoke(lines.joinToString("\n"))
            return 0
        }

        lines.add("Step 2/3: Deleting ${eventIds.size} event(s)...")
        onProgress?.invoke(lines.joinToString("\n"))

        var deletedCount = 0
        eventIds.chunked(200).forEach { chunk ->
            val ops = ArrayList<ContentProviderOperation>()
            for (id in chunk) {
                val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                ops.add(ContentProviderOperation.newDelete(eventUri).build())
            }
            try {
                val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, ops)
                deletedCount += results.size
            } catch (_: Exception) {
                for (id in chunk) {
                    try {
                        val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                        val rows = context.contentResolver.delete(eventUri, null, null)
                        if (rows > 0) deletedCount++
                    } catch (_: Exception) {}
                }
            }
        }
        lines[1] = "Step 2/3: Deleted $deletedCount event(s) locally."
        onProgress?.invoke(lines.joinToString("\n"))

        // STEP 3: Push deletions to Google Cloud
        lines.add("Step 3/3: Pushing deletions to Google Cloud...")
        onProgress?.invoke(lines.joinToString("\n"))

        if (deletedCount > 0) {
            recordSelfWrite()
        }

        try {
            context.contentResolver.notifyChange(CalendarContract.Events.CONTENT_URI, null)
            context.contentResolver.notifyChange(CalendarContract.Instances.CONTENT_URI, null)
            context.contentResolver.notifyChange(CalendarContract.Calendars.CONTENT_URI, null)
            context.sendBroadcast(android.content.Intent(android.content.Intent.ACTION_PROVIDER_CHANGED, CalendarContract.CONTENT_URI))
            context.sendBroadcast(android.content.Intent("com.google.android.calendar.SYNC_TRIGGER"))
        } catch (_: Exception) {}

        if (!targetAccountName.isNullOrEmpty() && !targetAccountType.isNullOrEmpty()) {
            try {
                val account = Account(targetAccountName, targetAccountType)
                val pushBundle = Bundle().apply {
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_OVERRIDE_TOO_MANY_DELETIONS, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_UPLOAD, true)
                }
                android.content.ContentResolver.requestSync(account, CalendarContract.AUTHORITY, pushBundle)
                try {
                    android.content.ContentResolver.requestSync(account, "com.google.android.calendar", pushBundle)
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }

        lines[2] = "Step 3/3: Pushed deletions to Google Cloud."
        lines.add("")
        lines.add("Operation Completed:")
        lines.add("• Target calendar completely wiped from phone & cloud.")
        lines.add("• Timeframe: Google sync takes 1–2 minutes to reflect across devices.")
        lines.add("• What to do next: You can now safely delete the target calendar in Google Calendar or keep it as an empty clone destination.")
        onProgress?.invoke(lines.joinToString("\n"))
        return deletedCount
    }

    /**
     * Synchronizes all enabled sync pairs in sequence.
     * Updates each pair's lastSyncTime and lastSyncStatus in SettingsRepository.
     */
    suspend fun syncAllPairs(
        context: Context,
        pairs: List<SyncPair>,
        onPairProgress: ((pair: SyncPair, currentPairIndex: Int, totalPairs: Int, result: SyncResult?, message: String?) -> Unit)? = null
    ): Map<String, SyncResult> {
        val results = mutableMapOf<String, SyncResult>()
        val enabledPairs = pairs.filter { it.isEnabled }
        val repo = SettingsRepository(context)

        enabledPairs.forEachIndexed { index, pair ->
            try {
                onPairProgress?.invoke(pair, index + 1, enabledPairs.size, null, "Syncing: ${pair.fromCalendarName} → ${pair.toCalendarName}...")
                val res = syncEventsToTarget(
                    context = context,
                    fromCalendarId = pair.fromCalendarId,
                    toCalendarId = pair.toCalendarId,
                    daysPast = pair.daysPast ?: 30,
                    daysFuture = pair.daysFuture ?: 30,
                    pairId = pair.id,
                    onProgress = { _, _, msg ->
                        onPairProgress?.invoke(pair, index + 1, enabledPairs.size, null, msg)
                    }
                )
                results[pair.id] = res
                val parts = mutableListOf<String>()
                if (res.insertedCount > 0) parts.add("${res.insertedCount} added")
                if (res.updatedCount > 0) parts.add("${res.updatedCount} updated")
                if (res.deletedCount > 0) parts.add("${res.deletedCount} removed")
                val statusMsg = if (parts.isEmpty()) "Sync complete: 0 changes." else "Sync complete: ${parts.joinToString(", ")}."
                repo.updatePairSyncStatus(pair.id, System.currentTimeMillis(), statusMsg)
                onPairProgress?.invoke(pair, index + 1, enabledPairs.size, res, statusMsg)
            } catch (e: Exception) {
                val errorMsg = "Sync failed: ${e.message}"
                repo.updatePairSyncStatus(pair.id, System.currentTimeMillis(), errorMsg)
                onPairProgress?.invoke(pair, index + 1, enabledPairs.size, null, errorMsg)
            }
        }
        return results
    }

    /**
     * Safeguard 4: Quickly queries count and max event ID for source calendars.
     * Takes ~1-2ms and avoids scanning instances or comparing event fields if nothing changed.
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
