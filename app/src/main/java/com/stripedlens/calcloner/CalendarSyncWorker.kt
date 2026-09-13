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
        val allPairs = repo.syncPairsFlow.first()
        val enabledPairs = allPairs.filter { it.isEnabled }

        if (enabledPairs.isEmpty()) {
            return Result.success()
        }

        val validPairs = mutableListOf<SyncPair>()
        for (pair in enabledPairs) {
            val fromExists = CalendarSyncEngine.calendarExists(appContext, pair.fromCalendarId)
            val toExists = CalendarSyncEngine.calendarExists(appContext, pair.toCalendarId)

            if (!fromExists || !toExists) {
                val missingDesc = when {
                    !fromExists && !toExists -> "Source and target calendars for '${pair.fromCalendarName} → ${pair.toCalendarName}' were"
                    !fromExists -> "Source calendar '${pair.fromCalendarName}' was"
                    else -> "Target calendar '${pair.toCalendarName}' was"
                }
                repo.togglePairEnabled(pair.id, false)
                val statusMsg = "Calendar Not Found: $missingDesc not found. Pair disabled."
                repo.updatePairSyncStatus(pair.id, System.currentTimeMillis(), statusMsg)
                showCalendarNotFoundNotification(
                    context = appContext,
                    message = "$missingDesc not found on device. Sync pair has been disabled."
                )
            } else {
                validPairs.add(pair)
            }
        }

        if (validPairs.isEmpty()) {
            return Result.failure()
        }

        return try {
            val results = CalendarSyncEngine.syncAllPairs(appContext, validPairs)
            val totalInserted = results.values.sumOf { it.insertedCount }
            val totalUpdated = results.values.sumOf { it.updatedCount }
            val totalDeleted = results.values.sumOf { it.deletedCount }

            val statusMsg = when {
                totalInserted == 0 && totalUpdated == 0 && totalDeleted == 0 -> "Auto-sync complete: 0 changes across ${validPairs.size} pair(s)."
                else -> {
                    val parts = mutableListOf<String>()
                    if (totalInserted > 0) parts.add("$totalInserted added")
                    if (totalUpdated > 0) parts.add("$totalUpdated updated")
                    if (totalDeleted > 0) parts.add("$totalDeleted removed")
                    "Auto-synced (${validPairs.size} pairs): ${parts.joinToString(", ")}."
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
