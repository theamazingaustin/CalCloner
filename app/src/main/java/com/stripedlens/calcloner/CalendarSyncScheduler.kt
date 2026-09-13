package com.stripedlens.calcloner

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object CalendarSyncScheduler {
    private const val WORK_NAME = "PeriodicCalendarSync"

    fun scheduleSync(context: Context, intervalMinutes: Int) {
        if (intervalMinutes <= 0) {
            cancelSync(context)
            return
        }

        // WorkManager enforces minimum 15 minute interval
        val safeMinutes = intervalMinutes.coerceAtLeast(15)

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

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
