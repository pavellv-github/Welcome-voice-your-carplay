package ru.pavellysov.carplaywelcome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && preferences(context).getBoolean(KEY_CONFIGURED, false)) {
            preferences(context).edit().putBoolean(KEY_BLUETOOTH_GREETING_PLAYED, false).apply()
            WelcomeService.start(context, WelcomeService.ACTION_BOOT)
        }
    }
}
