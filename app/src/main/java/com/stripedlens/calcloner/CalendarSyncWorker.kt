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
        val syncOnLow = repo.syncOnLowBatteryFlow.first()
        if (!syncOnLow && com.stripedlens.calcloner.util.SystemUtils.isLowPowerOrBattery(appContext)) {
            return Result.retry()
        }

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
                showSyncFailureNotification(
                    context = appContext,
                    pairId = pair.id,
                    pairName = pair.displayName,
                    message = "$missingDesc not found on device. Tap to adjust pair."
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

            // Check if any specific pair failed
            val failedPairs = validPairs.filter { it.id !in results }
            if (failedPairs.isNotEmpty()) {
                val firstFailed = failedPairs.first()
                val freshPairs = repo.syncPairsFlow.first()
                val failedStatus = freshPairs.find { it.id == firstFailed.id }?.lastSyncStatus ?: "Sync failed"
                showSyncFailureNotification(
                    context = appContext,
                    pairId = firstFailed.id,
                    pairName = firstFailed.displayName,
                    message = "$failedStatus. Tap to adjust pair."
                )
            }

            val statusMsg = com.stripedlens.calcloner.util.DateTimeUtils.formatSyncSummary(
                inserted = totalInserted,
                updated = totalUpdated,
                deleted = totalDeleted,
                pairCount = validPairs.size,
                prefix = "Auto-sync"
            )

            repo.saveLastSync(System.currentTimeMillis(), statusMsg)
            if (failedPairs.isNotEmpty()) Result.retry() else Result.success()
        } catch (e: SecurityException) {
            repo.saveLastSync(System.currentTimeMillis(), "Permission denied")
            showSyncFailureNotification(
                context = appContext,
                pairId = validPairs.firstOrNull()?.id,
                pairName = validPairs.firstOrNull()?.displayName ?: "Sync Engine",
                message = "Calendar permissions revoked. Tap to adjust permissions."
            )
            Result.failure()
        } catch (e: Exception) {
            val errorMsg = e.message?.take(80) ?: "Unexpected error"
            repo.saveLastSync(System.currentTimeMillis(), "Failed: $errorMsg")
            showSyncFailureNotification(
                context = appContext,
                pairId = validPairs.firstOrNull()?.id,
                pairName = validPairs.firstOrNull()?.displayName ?: "Sync Engine",
                message = "Sync failed: $errorMsg. Tap to adjust pair."
            )
            Result.retry()
        }
    }

    private fun showSyncFailureNotification(
        context: Context,
        pairId: String?,
        pairName: String,
        message: String
    ) {
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
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!pairId.isNullOrBlank()) {
                putExtra(MainActivity.EXTRA_OPEN_PAIR_ID, pairId)
            }
        }
        val notifId = pairId?.hashCode() ?: 1001
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_calcloner)
            .setContentTitle("Sync Issue: $pairName")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {}
    }
}
