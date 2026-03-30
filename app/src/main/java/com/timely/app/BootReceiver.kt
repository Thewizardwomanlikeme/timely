package com.timely.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restarts the TimerService after a device reboot, if it was previously running.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val settings = SettingsRepository(context)
            if (settings.isRunning) {
                val serviceIntent = Intent(context, TimerService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
