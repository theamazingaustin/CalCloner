package com.stripedlens.calcloner

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object CalendarSyncScheduler {
    private const val WORK_NAME = "PeriodicCalendarSync"
    const val REACTIVE_JOB_ID = 2001

    fun scheduleReactiveSync(context: Context, syncOnLowBattery: Boolean = false) {
        CalendarSyncJobService.reschedule(context, syncOnLowBattery)
    }

    fun cancelReactiveSync(context: Context) {
        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? android.app.job.JobScheduler ?: return
        jobScheduler.cancel(REACTIVE_JOB_ID)
    }

    fun scheduleSync(context: Context, intervalMinutes: Int, syncOnLowBattery: Boolean = false) {
        if (intervalMinutes <= 0) {
            cancelSync(context)
            return
        }

        // WorkManager enforces minimum 15 minute interval
        val safeMinutes = intervalMinutes.coerceAtLeast(15)

        val constraints = Constraints.Builder().apply {
            if (!syncOnLowBattery) {
                setRequiresBatteryNotLow(true)
            }
        }.build()

        val syncRequest = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
            safeMinutes.toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }

    fun cancelSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
