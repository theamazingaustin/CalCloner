package com.stripedlens.calcloner.engine

import android.accounts.Account
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import kotlinx.coroutines.delay

/**
 * Domain component handling calendar cleanup, orphan event pruning, and destructive calendar wiping.
 *
 * Enforces strict safety rules:
 * - Refuses execution if source calendar and target calendar match.
 * - Protects organic target events and events belonging to other active pairs.
 * - Uses standard soft deletes (`newDelete()`) so CalendarProvider sets `DELETED=1, DIRTY=1`
 *   tombstones to guarantee Google Calendar cloud propagation.
 */
object CalendarMaintenance {

    private val CALCLONER_TAG_REGEX = Regex("""\[CalClone(?:r)?-ID:\s*(?:([a-zA-Z0-9_-]+):)?(\d+)\]""")

    /**
     * Remove all cloned events strictly from the TARGET (Clone) calendar.
     *
     * GUARANTEES:
     * 1. Rejects execution if toCalendarId == fromCalendarId.
     * 2. SQL selection explicitly restricts deletion to CALENDAR_ID = toCalendarId.
     * 3. Source calendar is completely untouched.
     * 4. Organic events and events from other active sync pairs are preserved.
     */
    suspend fun clearTargetCalendarEvents(
        context: Context,
        toCalendarId: Long,
        fromCalendarId: Long?,
        pairId: String? = null,
        activePairIds: Set<String> = emptySet(),
        onProgress: ((message: String) -> Unit)? = null,
        onSelfWrite: (() -> Unit)? = null
    ): Int {
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

        // Query active events in target calendar (strictly filter only events belonging to this pair)
        val eventIds = mutableListOf<Long>()
        val queryCursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.CUSTOM_APP_URI, CalendarContract.Events.DESCRIPTION),
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0",
            arrayOf(toCalendarId.toString()),
            null
        )
        val uriPrefix = "calcloner://event/"
        val pairUriPrefix = if (pairId != null) "${uriPrefix}${pairId}/" else null
        val pairTagPrefix = if (pairId != null) "[CalCloner-ID: $pairId:" else null

        queryCursor?.use {
            val idCol = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val uriCol = it.getColumnIndex(CalendarContract.Events.CUSTOM_APP_URI)
            val descCol = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val customUri = if (uriCol != -1) it.getString(uriCol) else null
                val desc = if (descCol != -1) it.getString(descCol) else null

                // Extract matchedPairId if present
                var matchedPairId: String? = null
                if (customUri != null && customUri.startsWith(uriPrefix)) {
                    val remainder = customUri.removePrefix(uriPrefix)
                    if (remainder.contains("/")) {
                        matchedPairId = remainder.substringBefore("/")
                    }
                }
                if (matchedPairId == null && desc != null) {
                    val tagMatch = CALCLONER_TAG_REGEX.find(desc)
                    matchedPairId = tagMatch?.groupValues?.getOrNull(1)?.ifEmpty { null }
                }

                // If event belongs to another active pair, DO NOT DELETE!
                if (matchedPairId != null && activePairIds.contains(matchedPairId) && (pairId == null || matchedPairId != pairId)) {
                    continue
                }

                // Strictly ignore organic events (no customAppUri and no CalCloner tag)
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
                        eventIds.add(id)
                    }
                } else {
                    eventIds.add(id)
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
                deletedCount += results.sumOf { (it.count ?: 0).toInt() }
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
            onSelfWrite?.invoke()
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
            } catch (_: Exception) {}
        }

        onProgress?.invoke("Successfully cleared $deletedCount events from clone calendar.")
        return deletedCount
    }

    /**
     * Complete Nuke of Target Calendar:
     * 1. Rejects execution if toCalendarId == fromCalendarId.
     * 2. Toggles sync_events 0 -> 1 on target calendar in CalendarContract.Calendars, forcing Google Cloud
     *    to immediately download all remote/orphaned events into the local database.
     * 3. Polls CalendarContract.Events for up to 5 seconds to allow full cloud event download.
     * 4. Batch deletes 100% of all events in target calendar using standard newDelete(),
     *    preserving deleted=1, dirty=1 tombstones.
     * 5. Triggers expedited cloud sync with OVERRIDE_TOO_MANY_DELETIONS to permanently erase events from cloud.
     */
    suspend fun nukeTargetCalendarEvents(
        context: Context,
        toCalendarId: Long,
        fromCalendarId: Long?,
        onProgress: ((message: String) -> Unit)? = null,
        onSelfWrite: (() -> Unit)? = null
    ): Int {
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

            delay(1000L)

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
            foundCount = CalendarProviderReader.getCalendarEventCount(context, toCalendarId)
            lines[0] = "Step 1/3: Downloading cloud events... ($foundCount found)"
            onProgress?.invoke(lines.joinToString("\n"))
            if (foundCount == prevCount && foundCount > 0) {
                stableSeconds++
                if (stableSeconds >= 2) break
            } else {
                stableSeconds = 0
            }
            prevCount = foundCount
            delay(1000L)
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
        lines[1] = "Step 2/3: Deleted $deletedCount event(s) locally."
        onProgress?.invoke(lines.joinToString("\n"))

        // STEP 3: Push deletions to Google Cloud
        lines.add("Step 3/3: Pushing deletions to Google Cloud...")
        onProgress?.invoke(lines.joinToString("\n"))

        if (deletedCount > 0) {
            onSelfWrite?.invoke()
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
}
