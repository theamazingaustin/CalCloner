package com.stripedlens.calcloner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val repo = SettingsRepository(context)
                    val interval = repo.syncIntervalFlow.first()
                    val pairs = repo.syncPairsFlow.first().filter { it.isEnabled }
                    val syncOnLow = repo.syncOnLowBatteryFlow.first()
                    if (pairs.isNotEmpty()) {
                        if (interval == 0) {
                            CalendarSyncScheduler.scheduleReactiveSync(context, syncOnLow)
                        } else {
                            CalendarSyncScheduler.scheduleSync(context, interval, syncOnLow)
                        }
                    } else {
                        CalendarSyncScheduler.cancelReactiveSync(context)
                        CalendarSyncScheduler.cancelSync(context)
                    }
                } catch (_: Exception) {
                    // Ignore boot setup failures
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
