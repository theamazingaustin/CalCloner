package com.stripedlens.calcloner

import android.content.Context
import com.stripedlens.calcloner.engine.CalendarEventWriter
import com.stripedlens.calcloner.engine.CalendarMaintenance
import com.stripedlens.calcloner.engine.CalendarProviderReader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * High-level facade for the CalCloner sync engine.
 *
 * Responsibilities:
 * - Thread synchronization: Ensures atomic, serialized execution across concurrent callers via [syncMutex].
 * - Self-write detection: Tracks [lastSelfWriteTimestamp] to prevent infinite loops from reactive content observers.
 * - API stability: Preserves 100% backward compatibility for all background workers, receivers, and UI layers.
 * - Clean delegation: Delegates reader operations to [CalendarProviderReader], writes to [CalendarEventWriter],
 *   and maintenance/wipes to [CalendarMaintenance].
 */
object CalendarSyncEngine {

    private val syncMutex = Mutex()

    @Volatile
    var lastSelfWriteTimestamp: Long = 0L
        private set

    fun recordSelfWrite() {
        lastSelfWriteTimestamp = System.currentTimeMillis()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Read Queries (Delegated to CalendarProviderReader)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Query count of active events in a given calendar.
     */
    fun getCalendarEventCount(context: Context, calendarId: Long): Int {
        return CalendarProviderReader.getCalendarEventCount(context, calendarId)
    }

    /**
     * Query all user-accessible calendars on the device.
     */
    fun getAvailableCalendars(context: Context): List<CalendarInfo> {
        return CalendarProviderReader.getAvailableCalendars(context)
    }

    /**
     * Checks if a given calendar ID exists and is accessible.
     */
    fun calendarExists(context: Context, calendarId: Long): Boolean {
        return CalendarProviderReader.calendarExists(context, calendarId)
    }

    /**
     * Reads all active events within the configured sync window from the source calendar.
     */
    fun readSourceEvents(
        context: Context,
        fromCalendarId: Long,
        daysPast: Int = 30,
        daysFuture: Int = 30
    ): List<SyncEvent> {
        return CalendarProviderReader.readSourceEvents(context, fromCalendarId, daysPast, daysFuture)
    }

    /**
     * Accurately count only cloned events belonging to this pair.
     */
    fun getTargetClonedEventCount(
        context: Context,
        toCalendarId: Long,
        pairId: String?,
        activePairIds: Set<String> = emptySet()
    ): Int {
        return CalendarProviderReader.getTargetClonedEventCount(context, toCalendarId, pairId, activePairIds)
    }

    /**
     * Quickly queries count and max event ID for source calendars for ~1ms change detection.
     */
    fun getSourceCalendarsFingerprint(context: Context, sourceCalendarIds: Set<Long>): String {
        return CalendarProviderReader.getSourceCalendarsFingerprint(context, sourceCalendarIds)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Write & Sync Operations (Delegated to CalendarEventWriter)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Replicates events from SOURCE calendar to TARGET calendar.
     * Guaranteed: Source calendar is completely untouched; all writes affect target exclusively.
     */
    suspend fun syncEventsToTarget(
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
        onProgress: ((current: Int, total: Int, message: String) -> Unit)? = null
    ): SyncResult = syncMutex.withLock {
        CalendarEventWriter.syncEventsToTarget(
            context = context,
            fromCalendarId = fromCalendarId,
            toCalendarId = toCalendarId,
            daysPast = daysPast,
            daysFuture = daysFuture,
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
            syncReminders = syncReminders,
            syncAvailability = syncAvailability,
            customAvailability = customAvailability,
            syncStatus = syncStatus,
            customStatus = customStatus,
            syncAttendees = syncAttendees,
            attendeesPlacement = attendeesPlacement,
            activePairIds = activePairIds,
            onProgress = onProgress,
            onSelfWrite = { recordSelfWrite() }
        )
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
        val activePairIds = pairs.map { it.id }.toSet()

        enabledPairs.forEachIndexed { index, pair ->
            if (!pair.isConfigValidForSync) {
                val validationMsg = "Sync blocked: ${pair.fieldValidationError}"
                repo.updatePairSyncStatus(pair.id, System.currentTimeMillis(), validationMsg)
                onPairProgress?.invoke(pair, index + 1, enabledPairs.size, null, validationMsg)
                return@forEachIndexed
            }
            val startTime = System.currentTimeMillis()
            try {
                onPairProgress?.invoke(pair, index + 1, enabledPairs.size, null, "Syncing: ${pair.displayName}...")
                val res = syncEventsToTarget(
                    context = context,
                    fromCalendarId = pair.fromCalendarId,
                    toCalendarId = pair.toCalendarId,
                    daysPast = pair.daysPast,
                    daysFuture = pair.daysFuture,
                    pairId = pair.id,
                    syncTitle = pair.syncTitle,
                    customTitle = pair.customTitle,
                    titlePrefix = pair.titlePrefix,
                    titleSuffix = pair.titleSuffix,
                    syncDescription = pair.syncDescription,
                    customDescription = pair.customDescription,
                    descriptionPrefix = pair.descriptionPrefix,
                    descriptionSuffix = pair.descriptionSuffix,
                    syncLocation = pair.syncLocation,
                    customLocation = pair.customLocation,
                    syncReminders = pair.syncReminders,
                    syncAvailability = pair.syncAvailability,
                    customAvailability = pair.customAvailability,
                    syncStatus = pair.syncStatus,
                    customStatus = pair.customStatus,
                    syncAttendees = pair.syncAttendees,
                    attendeesPlacement = pair.attendeesPlacement,
                    activePairIds = activePairIds,
                    onProgress = { _, _, msg ->
                        onPairProgress?.invoke(pair, index + 1, enabledPairs.size, null, msg)
                    }
                )
                val durationMs = System.currentTimeMillis() - startTime
                results[pair.id] = res
                val statusMsg = com.stripedlens.calcloner.util.DateTimeUtils.formatSyncSummary(
                    inserted = res.insertedCount,
                    updated = res.updatedCount,
                    deleted = res.deletedCount,
                    prefix = "Sync complete"
                )
                repo.updatePairSyncStatus(
                    pairId = pair.id,
                    timestamp = System.currentTimeMillis(),
                    status = statusMsg,
                    insertedCount = res.insertedCount,
                    updatedCount = res.updatedCount,
                    deletedCount = res.deletedCount,
                    durationMs = durationMs
                )
                onPairProgress?.invoke(pair, index + 1, enabledPairs.size, res, statusMsg)
            } catch (e: Exception) {
                val errorMsg = "Sync failed: ${e.message}"
                repo.updatePairSyncStatus(pair.id, System.currentTimeMillis(), errorMsg)
                onPairProgress?.invoke(pair, index + 1, enabledPairs.size, null, errorMsg)
            }
        }
        return results
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Maintenance & Wipe Operations (Delegated to CalendarMaintenance)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Remove all events strictly from the TARGET (Clone) calendar.
     */
    suspend fun deleteClonedCalendarEvents(
        context: Context,
        toCalendarId: Long,
        fromCalendarId: Long?,
        pairId: String? = null,
        activePairIds: Set<String> = emptySet(),
        onProgress: ((message: String) -> Unit)? = null
    ): Int = syncMutex.withLock {
        CalendarMaintenance.deleteClonedCalendarEvents(
            context = context,
            toCalendarId = toCalendarId,
            fromCalendarId = fromCalendarId,
            pairId = pairId,
            activePairIds = activePairIds,
            onProgress = onProgress,
            onSelfWrite = { recordSelfWrite() }
        )
    }

    /**
     * Standard deletion of all events in target calendar using tombstones.
     */
    suspend fun clearAllCalendarEvents(
        context: Context,
        toCalendarId: Long,
        fromCalendarId: Long? = null,
        onProgress: ((message: String) -> Unit)? = null
    ): Int = syncMutex.withLock {
        CalendarMaintenance.clearAllCalendarEvents(
            context = context,
            toCalendarId = toCalendarId,
            fromCalendarId = fromCalendarId,
            onProgress = onProgress,
            onSelfWrite = { recordSelfWrite() }
        )
    }

    /**
     * Complete Wipe of Target Calendar with 3-step cloud download and batch wipe.
     */
    suspend fun deleteAllCalendarEvents(
        context: Context,
        toCalendarId: Long,
        fromCalendarId: Long?,
        onProgress: ((message: String) -> Unit)? = null
    ): Int = syncMutex.withLock {
        CalendarMaintenance.deleteAllCalendarEvents(
            context = context,
            toCalendarId = toCalendarId,
            fromCalendarId = fromCalendarId,
            onProgress = onProgress,
            onSelfWrite = { recordSelfWrite() }
        )
    }

    /**
     * Purges old synchronized tombstones from local calendar database.
     */
    suspend fun purgeSyncedTombstones(
        context: Context,
        calendarId: Long? = null,
        onProgress: ((message: String) -> Unit)? = null
    ): Int = syncMutex.withLock {
        CalendarMaintenance.purgeSyncedTombstones(
            context = context,
            calendarId = calendarId,
            onProgress = onProgress
        )
    }
}
