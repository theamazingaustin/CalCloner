package com.stripedlens.calcloner

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Event-driven JobService implementing The 4 Engineering Safeguards for Zero-Drain Reactive Sync:
 * 1. Fast Source Filter: Checks triggeredContentUris and drops non-source calendar edits in < 2ms.
 * 2. Echo Suppression: Ignores writes caused by CalCloner within the echo window or bearing our package.
 * 3. Adaptive Debounce: 5s quiet period / 30s ceiling configured in JobInfo.Builder.
 * 4. Micro-Diff Fallback & Doze Friendliness: Fast fingerprint check for generic table triggers.
 */
class CalendarSyncJobService : JobService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        @Volatile
        private var lastKnownFingerprint: String = ""

        fun reschedule(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val componentName = ComponentName(context, CalendarSyncJobService::class.java)
            val builder = JobInfo.Builder(CalendarSyncScheduler.REACTIVE_JOB_ID, componentName)
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        CalendarContract.Events.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                    )
                )
                .setTriggerContentUpdateDelay(5_000L) // 5s debounce
                .setTriggerContentMaxDelay(30_000L)   // 30s max batching
            jobScheduler.schedule(builder.build())
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        serviceScope.launch {
            try {
                handleJob(params)
            } finally {
                // Android JobScheduler Content URI triggers are one-shot; reschedule for next trigger
                reschedule(applicationContext)
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        serviceScope.cancel()
        return false
    }

    private suspend fun handleJob(params: JobParameters?) {
        val repo = SettingsRepository(applicationContext)
        val pairs = repo.syncPairsFlow.first().filter { it.isEnabled }
        if (pairs.isEmpty()) return

        val sourceCalendarIds = pairs.map { it.fromCalendarId }.toSet()
        val targetCalendarIds = pairs.map { it.toCalendarId }.toSet()

        // SAFEGUARD 2: In-memory echo suppression check
        val timeSinceLastSelfWrite = System.currentTimeMillis() - CalendarSyncEngine.lastSelfWriteTimestamp
        val isRecentSelfWrite = timeSinceLastSelfWrite in 0..10_000L

        // SAFEGUARD 1: Fast URI Inspection (< 2ms)
        val triggeredUris = params?.triggeredContentUris
        if (!triggeredUris.isNullOrEmpty()) {
            val changedEventIds = triggeredUris.mapNotNull { uri ->
                uri.lastPathSegment?.toLongOrNull()
            }.distinct()

            if (changedEventIds.isNotEmpty()) {
                val placeholders = changedEventIds.joinToString(",") { "?" }
                val args = changedEventIds.map { it.toString() }.toTypedArray()
                val affectedSourceCalendarIds = mutableSetOf<Long>()
                var hasTargetWritesOnly = true

                try {
                    val cursor = contentResolver.query(
                        CalendarContract.Events.CONTENT_URI,
                        arrayOf(
                            CalendarContract.Events._ID,
                            CalendarContract.Events.CALENDAR_ID,
                            CalendarContract.Events.CUSTOM_APP_PACKAGE
                        ),
                        "${CalendarContract.Events._ID} IN ($placeholders)",
                        args,
                        null
                    )
                    cursor?.use {
                        val calCol = it.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
                        val pkgCol = it.getColumnIndex(CalendarContract.Events.CUSTOM_APP_PACKAGE)
                        while (it.moveToNext()) {
                            val calId = it.getLong(calCol)
                            val customPkg = if (pkgCol != -1) it.getString(pkgCol) else null

                            // If this event was written by CalCloner, it's a clone echo
                            if (customPkg == packageName) continue

                            if (calId in sourceCalendarIds) {
                                affectedSourceCalendarIds.add(calId)
                                hasTargetWritesOnly = false
                            } else if (calId in targetCalendarIds) {
                                // Event on target calendar not originating from source
                                continue
                            }
                        }
                    }
                } catch (_: Exception) {}

                // If only target calendars or self-writes were modified, exit immediately
                if (affectedSourceCalendarIds.isEmpty()) {
                    return
                }

                // If echo window is active and we only saw self-writes, exit
                if (isRecentSelfWrite && hasTargetWritesOnly) {
                    return
                }

                // Sync ONLY the pairs whose source calendar actually changed
                val affectedPairs = pairs.filter { it.fromCalendarId in affectedSourceCalendarIds }
                if (affectedPairs.isNotEmpty()) {
                    CalendarSyncEngine.syncAllPairs(applicationContext, affectedPairs)
                }
                return
            }
        }

        // SAFEGUARD 4: Micro-Diff Fallback for generic table-level triggers
        val currentFingerprint = CalendarSyncEngine.getSourceCalendarsFingerprint(applicationContext, sourceCalendarIds)
        if (currentFingerprint.isNotEmpty() && currentFingerprint == lastKnownFingerprint) {
            // Source calendars did not change; exit without full scan
            return
        }
        lastKnownFingerprint = currentFingerprint

        if (isRecentSelfWrite) {
            // Recent self-write and generic trigger -> suppress echo
            return
        }

        // Run sync on enabled pairs
        CalendarSyncEngine.syncAllPairs(applicationContext, pairs)
    }
}
