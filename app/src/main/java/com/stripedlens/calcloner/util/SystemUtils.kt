package com.stripedlens.calcloner.util

import android.content.Context
import android.content.Intent
import android.provider.Settings

object SystemUtils {

    fun openNotificationShade(context: Context) {
        try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val method = statusBarManager.getMethod("expandNotificationsPanel")
            method.invoke(statusBarService)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SYNC_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    fun isLowPowerOrBattery(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (powerManager?.isPowerSaveMode == true) return true

            val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val plugged = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            if (plugged > 0) return false // Currently plugged in / charging

            val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100

            val isLow = batteryIntent?.getBooleanExtra(android.os.BatteryManager.EXTRA_BATTERY_LOW, false) ?: false
            isLow || pct <= 15
        } catch (_: Exception) {
            false
        }
    }
}
