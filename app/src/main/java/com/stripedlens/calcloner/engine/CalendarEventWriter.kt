package com.stripedlens.calcloner.engine

import android.accounts.Account
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import com.stripedlens.calcloner.SyncAttendee
import com.stripedlens.calcloner.SyncEvent
import com.stripedlens.calcloner.SyncReminder
import com.stripedlens.calcloner.SyncResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.TimeZone

/**
 * Domain component responsible for writing, updating, and diffing calendar events
 * from a source calendar to a designated target calendar.
 *
 * Enforces:
 * - One-way replication guarantee (target writes only, source untouched).
 * - RFC 5545 timestamp normalization for all-day events.
 * - Two-pass recurrence exception linking (parents first, exception instances second).
 * - Intelligent Content diffing before issuing update operations.
 * - Orphan pruning of events no longer present in source.
 */
object CalendarEventWriter {

    private val CALCLONER_TAG_REGEX = com.stripedlens.calcloner.AppConstants.Engine.CALCLONER_TAG_REGEX
    private val CALCLONER_TAG_REPLACE_REGEX = com.stripedlens.calcloner.AppConstants.Engine.CALCLONER_TAG_REPLACE_REGEX
    private val LEGACY_CALCLONE_TAG_REPLACE_REGEX = com.stripedlens.calcloner.AppConstants.Engine.LEGACY_CALCLONE_TAG_REPLACE_REGEX

    internal data class ClonedEventMeta(
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
        val hasRrule: Boolean,
        val customAppUri: String? = null
    )

    internal fun isEventContentChanged(existing: ClonedEventMeta, newValues: ContentValues): Boolean {
        val newUri = newValues.getAsString(CalendarContract.Events.CUSTOM_APP_URI)
        if (!newUri.isNullOrEmpty() && newUri != existing.customAppUri) return true

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

    internal fun parseDurationDays(durationStr: String?): Long? {
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

    internal fun syncReminders(context: Context, targetEventId: Long, reminders: List<SyncReminder>) {
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

    internal fun formatAttendeesBlock(attendees: List<SyncAttendee>): String {
        if (attendees.isEmpty()) return ""
        val lines = attendees.mapNotNull { att ->
            val displayName = when {
                !att.name.isNullOrBlank() -> att.name.trim()
                !att.email.isNullOrBlank() -> att.email.substringBefore('@').trim()
                else -> null
            } ?: return@mapNotNull null

            val statusText = when (att.status) {
                CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED -> "Accepted"
                CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED -> "Declined"
                CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE -> "Tentative"
                CalendarContract.Attendees.ATTENDEE_STATUS_INVITED -> "Invited"
                else -> null
            }
            if (statusText != null) "• $displayName ($statusText)" else "• $displayName"
        }
        if (lines.isEmpty()) return ""
        return "Attendees:\n" + lines.joinToString("\n")
    }

    internal fun buildEventValues(
        context: Context,
        event: SyncEvent,
        toCalendarId: Long,
        uriPrefix: String,
        defaultTimeZone: String,
        targetParentId: Long?,
        pairId: String? = null,
        syncTitle: Boolean = true,
        customTitle: String? = null,
        titlePrefix: String? = null,
        titleSuffix: String? = null,
        syncDescription: Boolean = true,
        customDescription: String? = null,
        descriptionPrefix: String? = null,
        descriptionSuffix: String? = null,
        syncLocation: Boolean = true,
        customLocation: String? = null,
        syncAvailability: Boolean = true,
        customAvailability: Int? = null,
        syncStatus: Boolean = true,
        customStatus: Int? = null,
        syncAttendees: Boolean = false,
        attendeesPlacement: String = "END"
    ): ContentValues {
        return ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, toCalendarId)
            val titleToUse = if (syncTitle) {
                val base = event.title ?: ""
                val pre = titlePrefix ?: ""
                val suf = titleSuffix ?: ""
                "$pre$base$suf"
            } else {
                val sanitized = customTitle?.trim()?.replace(Regex("[\\p{Cntrl}&&[^\r\n\t]]"), "")
                if (sanitized.isNullOrEmpty()) "Busy" else sanitized.take(100)
            }
            put(CalendarContract.Events.TITLE, titleToUse)
            val locationToUse = if (syncLocation) {
                event.location ?: ""
            } else {
                customLocation?.trim() ?: ""
            }
            put(CalendarContract.Events.EVENT_LOCATION, locationToUse)

            // Description Tracking Tag: Append [CalCloner-ID: <pairId>:<id>] for cross-cloud persistence
            val trackingTag = if (pairId != null) "[CalCloner-ID: ${pairId}:${event.id}]" else "[CalCloner-ID: ${event.id}]"
            val attBlock = if (syncAttendees && event.attendees.isNotEmpty()) {
                formatAttendeesBlock(event.attendees)
            } else ""

            val fullDescription = if (syncDescription) {
                val cleanBaseDesc = (event.description ?: "")
                    .replace(CALCLONER_TAG_REPLACE_REGEX, "")
                    .replace(LEGACY_CALCLONE_TAG_REPLACE_REGEX, "")
                    .trim()
                val descWithAttendees = when {
                    attBlock.isEmpty() -> cleanBaseDesc
                    cleanBaseDesc.isEmpty() -> attBlock
                    attendeesPlacement == "START" -> "$attBlock\n\n$cleanBaseDesc"
                    else -> "$cleanBaseDesc\n\n$attBlock"
                }
                val pre = if (!descriptionPrefix.isNullOrEmpty()) "${descriptionPrefix}\n" else ""
                val suf = if (!descriptionSuffix.isNullOrEmpty()) "\n${descriptionSuffix}" else ""
                val content = if (descWithAttendees.isNotEmpty()) {
                    "$pre$descWithAttendees$suf"
                } else if (pre.isNotEmpty() || suf.isNotEmpty()) {
                    "$pre$suf".trim()
                } else {
                    ""
                }
                if (content.isEmpty()) trackingTag else "$content\n\n$trackingTag"
            } else {
                val cleanFixed = (customDescription?.trim() ?: "")
                    .replace(CALCLONER_TAG_REPLACE_REGEX, "")
                    .replace(LEGACY_CALCLONE_TAG_REPLACE_REGEX, "")
                    .trim()
                val descWithAttendees = when {
                    attBlock.isEmpty() -> cleanFixed
                    cleanFixed.isEmpty() -> attBlock
                    attendeesPlacement == "START" -> "$attBlock\n\n$cleanFixed"
                    else -> "$cleanFixed\n\n$attBlock"
                }
                if (descWithAttendees.isEmpty()) trackingTag else "$descWithAttendees\n\n$trackingTag"
            }
            put(CalendarContract.Events.DESCRIPTION, fullDescription)

            val effectiveStart = if (event.dtStart > 0) event.dtStart else (event.originalInstanceTime ?: System.currentTimeMillis())

            if (event.allDay == 1) {
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")

                // RFC 5545 & Android CalendarContract: All-day events are defined in UTC midnight.
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

            val eventStatus = if (syncStatus) {
                event.status ?: CalendarContract.Events.STATUS_CONFIRMED
            } else {
                customStatus ?: CalendarContract.Events.STATUS_CONFIRMED
            }
            put(CalendarContract.Events.STATUS, eventStatus)
            if (syncAvailability) {
                val eventAvailability = event.availability ?: CalendarContract.Events.AVAILABILITY_BUSY
                put(CalendarContract.Events.AVAILABILITY, eventAvailability)
            } else {
                if (customAvailability != null) {
                    put(CalendarContract.Events.AVAILABILITY, customAvailability)
                } else {
                    putNull(CalendarContract.Events.AVAILABILITY)
                }
            }
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
     * Executes the replication algorithm from source to target calendar.
     */
    fun syncEventsToTarget(
        context: Context,
        fromCalendarId: Long,
        toCalendarId: Long,
        daysPast: Int = 30,
        daysFuture: Int = 30,
        pairId: String? = null,
        syncTitle: Boolean = true,
        customTitle: String? = null,
        titlePrefix: String? = null,
        titleSuffix: String? = null,
        syncDescription: Boolean = true,
        customDescription: String? = null,
        descriptionPrefix: String? = null,
        descriptionSuffix: String? = null,
        syncLocation: Boolean = true,
        customLocation: String? = null,
        syncReminders: Boolean = true,
        syncAvailability: Boolean = true,
        customAvailability: Int? = null,
        syncStatus: Boolean = true,
        customStatus: Int? = null,
        syncAttendees: Boolean = false,
        attendeesPlacement: String = "END",
        activePairIds: Set<String> = emptySet(),
        onProgress: ((current: Int, total: Int, message: String) -> Unit)? = null,
        onSelfWrite: (() -> Unit)? = null
    ): SyncResult {
        val startTime = System.currentTimeMillis()
        require(fromCalendarId != toCalendarId) {
            "CRITICAL SAFETY VIOLATION: Source calendar ($fromCalendarId) and Target calendar ($toCalendarId) cannot be the same!"
        }

        if (!CalendarProviderReader.calendarExists(context, fromCalendarId)) {
            throw IllegalStateException("Calendar Not Found: Source calendar not found.")
        }
        if (!CalendarProviderReader.calendarExists(context, toCalendarId)) {
            throw IllegalStateException("Calendar Not Found: Target calendar not found.")
        }

        onProgress?.invoke(0, 0, "Reading source calendar...")
        val sourceEvents = CalendarProviderReader.readSourceEvents(context, fromCalendarId, daysPast, daysFuture)
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
        val redundantDuplicateIdsToDelete = mutableListOf<Long>()
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
                    hasRrule = hasRrule,
                    customAppUri = customUri
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
                    val tagMatch = CALCLONER_TAG_REGEX.find(desc)
                    if (tagMatch != null) {
                        matchedPairId = tagMatch.groupValues.getOrNull(1)?.ifEmpty { null }
                        sourceId = tagMatch.groupValues.getOrNull(2)?.toLongOrNull()
                    }
                }

                // Multi-Pair Isolation: If matchedPairId belongs to another ACTIVE sync pair, strictly protect & skip it!
                if (matchedPairId != null && activePairIds.contains(matchedPairId) && (pairId == null || matchedPairId != pairId)) {
                    continue
                }

                val sourceEventIds = sourceEvents.map { it.id }.toSet()

                if (sourceId != null) {
                    if (sourceEventIds.contains(sourceId)) {
                        if (existingTargetEvents.containsKey(sourceId)) {
                            redundantDuplicateIdsToDelete.add(targetId)
                        } else {
                            existingTargetEvents[sourceId] = meta
                            sourceIdToTargetIdMap[sourceId] = targetId
                        }
                        continue
                    } else if (matchedPairId == pairId) {
                        existingTargetEvents[sourceId] = meta
                        sourceIdToTargetIdMap[sourceId] = targetId
                        continue
                    }
                }

                // Signature matching: ONLY for events marked with CalCloner tags
                val isCalClonerEvent = (customUri != null && customUri.startsWith(uriPrefix)) ||
                        (desc != null && (desc.contains("[CalCloner-ID:") || desc.contains("[CalClone-ID:")))
                if (isCalClonerEvent) {
                    val sigKey = "$title|$start"
                    if (existingTargetBySignature.containsKey(sigKey)) {
                        redundantDuplicateIdsToDelete.add(targetId)
                    } else {
                        existingTargetBySignature[sigKey] = meta
                    }
                }
            }
        }

        // Clean up redundant duplicate clones
        if (redundantDuplicateIdsToDelete.isNotEmpty()) {
            val deleteOps = ArrayList<ContentProviderOperation>()
            for (dupId in redundantDuplicateIdsToDelete) {
                val delUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, dupId)
                deleteOps.add(ContentProviderOperation.newDelete(delUri).build())
            }
            try {
                context.contentResolver.applyBatch(CalendarContract.AUTHORITY, deleteOps)
            } catch (_: Exception) {
                for (dupId in redundantDuplicateIdsToDelete) {
                    try {
                        val delUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, dupId)
                        context.contentResolver.delete(delUri, null, null)
                    } catch (_: Exception) {}
                }
            }
        }

        var insertedCount = 0
        var updatedCount = 0
        val defaultTimeZone = TimeZone.getDefault().id

        // Two-Pass Sync
        val (exceptionEvents, baseEvents) = sourceEvents.partition {
            it.originalId != null || it.originalSyncId != null || it.originalInstanceTime != null
        }

        fun syncSingleEvent(event: SyncEvent, targetParentId: Long?) {
            val values = buildEventValues(
                context = context,
                event = event,
                toCalendarId = toCalendarId,
                uriPrefix = uriPrefix,
                defaultTimeZone = defaultTimeZone,
                targetParentId = targetParentId,
                pairId = pairId,
                syncTitle = syncTitle,
                customTitle = customTitle,
                titlePrefix = titlePrefix,
                titleSuffix = titleSuffix,
                syncDescription = syncDescription,
                customDescription = customDescription,
                descriptionPrefix = descriptionPrefix,
                descriptionSuffix = descriptionSuffix,
                syncLocation = syncLocation,
                customLocation = customLocation,
                syncAvailability = syncAvailability,
                customAvailability = customAvailability,
                syncStatus = syncStatus,
                customStatus = customStatus,
                syncAttendees = syncAttendees,
                attendeesPlacement = attendeesPlacement
            )
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
                if (syncReminders) {
                    syncReminders(context, targetId, event.reminders)
                } else {
                    syncReminders(context, targetId, emptyList())
                }
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

        // Pruning out-of-window and deleted events
        val activeSourceIds = sourceEvents.map { it.id }.toSet()
        var deletedCount = 0
        val eventsToDelete = mutableListOf<Long>()
        val candidateSourceIds = mutableListOf<Long>()

        for ((sourceId, meta) in existingTargetEvents) {
            val isOutOfWindow = !meta.hasRrule && (meta.dtStart < windowStart || meta.dtStart > windowEnd)
            if (isOutOfWindow) {
                eventsToDelete.add(meta.targetId)
            } else if (sourceId !in activeSourceIds) {
                candidateSourceIds.add(sourceId)
            }
        }

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
                    deletedCount += results.sumOf { (it.count ?: 0).toInt() }
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
        }

        if (insertedCount > 0 || updatedCount > 0 || deletedCount > 0) {
            onSelfWrite?.invoke()
        }

        // Notify content observers
        try {
            context.contentResolver.notifyChange(CalendarContract.Events.CONTENT_URI, null)
            context.contentResolver.notifyChange(CalendarContract.Instances.CONTENT_URI, null)
            context.contentResolver.notifyChange(CalendarContract.Calendars.CONTENT_URI, null)
            context.sendBroadcast(android.content.Intent(android.content.Intent.ACTION_PROVIDER_CHANGED, CalendarContract.CONTENT_URI))
            context.sendBroadcast(android.content.Intent("com.google.android.calendar.SYNC_TRIGGER"))
        } catch (_: Exception) {}

        // Request standard expedited sync
        if (!targetAccountName.isNullOrEmpty() && !targetAccountType.isNullOrEmpty()) {
            try {
                val account = Account(targetAccountName, targetAccountType)
                val syncBundle = Bundle().apply {
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                    putBoolean(android.content.ContentResolver.SYNC_EXTRAS_UPLOAD, true)
                }
                android.content.ContentResolver.requestSync(account, CalendarContract.AUTHORITY, syncBundle)
                try {
                    android.content.ContentResolver.requestSync(account, "com.google.android.calendar", syncBundle)
                } catch (_: Exception) {}
            } catch (_: Exception) {}
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

        val durationMs = System.currentTimeMillis() - startTime
        return SyncResult(
            insertedCount = insertedCount,
            updatedCount = updatedCount,
            totalSourceEvents = total,
            deletedCount = deletedCount,
            durationMs = durationMs
        )
    }
}
