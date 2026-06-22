package com.reminderwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotifActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == PersistentNotif.ACTION_HIDE) {
            context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(PersistentNotif.KEY_ENABLED, false).apply()
            PersistentNotif.cancel(context)
        }
    }
}
