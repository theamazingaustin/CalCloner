package com.stripedlens.calcloner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first

class CalendarSyncWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = SettingsRepository(appContext)
        val fromId = repo.fromCalendarIdFlow.first()
        val toId = repo.toCalendarIdFlow.first()

        if (fromId == null || toId == null || fromId == toId) {
            return Result.failure()
        }

        // Validate that both source and target calendars exist on the device
        val fromExists = CalendarSyncEngine.calendarExists(appContext, fromId)
        val toExists = CalendarSyncEngine.calendarExists(appContext, toId)

        if (!fromExists || !toExists) {
            val missingDesc = when {
                !fromExists && !toExists -> "Source and target calendars were"
                !fromExists -> "Source calendar was"
                else -> "Target calendar was"
            }

            // Set sync frequency to Never and cancel background work
            repo.saveSyncInterval(0)
            CalendarSyncScheduler.cancelSync(appContext)

            val statusMsg = "Calendar Not Found: $missingDesc not found. Scheduled sync set to Never."
            repo.saveLastSync(System.currentTimeMillis(), statusMsg)

            showCalendarNotFoundNotification(
                context = appContext,
                message = "$missingDesc not found on device. Background sync has been paused and set to Never."
            )

            return Result.failure()
        }

        val daysPast = repo.syncDaysPastFlow.first()
        val daysFuture = repo.syncDaysFutureFlow.first()

        return try {
            val syncResult = CalendarSyncEngine.syncEventsToTarget(
                context = appContext,
                fromCalendarId = fromId,
                toCalendarId = toId,
                daysPast = daysPast,
                daysFuture = daysFuture
            )
            val statusMsg = when {
                syncResult.totalSourceEvents == 0 && syncResult.deletedCount == 0 -> "Auto-sync: source calendar is empty."
                syncResult.totalSourceEvents == 0 && syncResult.deletedCount > 0 -> "Auto-sync: removed ${syncResult.deletedCount} clone event(s)."
                syncResult.insertedCount == 0 && syncResult.updatedCount == 0 && syncResult.deletedCount == 0 -> "Auto-sync complete: 0 changes found."
                else -> {
                    val parts = mutableListOf<String>()
                    if (syncResult.insertedCount > 0) parts.add("${syncResult.insertedCount} added")
                    if (syncResult.updatedCount > 0) parts.add("${syncResult.updatedCount} updated")
                    if (syncResult.deletedCount > 0) parts.add("${syncResult.deletedCount} removed")
                    "Auto-synced: ${parts.joinToString(", ")}."
                }
            }

            repo.saveLastSync(System.currentTimeMillis(), statusMsg)
            Result.success()
        } catch (e: SecurityException) {
            repo.saveLastSync(System.currentTimeMillis(), "Permission denied")
            Result.failure()
        } catch (e: Exception) {
            repo.saveLastSync(System.currentTimeMillis(), "Failed: ${e.message}")
            Result.retry()
        }
    }

    private fun showCalendarNotFoundNotification(context: Context, message: String) {
        val channelId = "calcloner_alerts"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "CalCloner Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for CalCloner sync warnings and errors"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_calcloner)
            .setContentTitle("Calendar Not Found")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(1001, notification)
        } catch (_: SecurityException) {}
    }
}
