package com.reminderwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        GeofenceManager.reregisterAll(ctx)
        SyncService.start(ctx)
        PersistentNotif.update(ctx)
        rescheduleAll(ctx)
    }

    private fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        EventStore.load(ctx).filter { !it.completed }.forEach { event ->
            when {
                !event.hasTime -> {
                    // Re-pin no-time/location events to status bar
                    NotificationHelper.post(ctx, event, ongoing = true, silent = true, pinned = true)
                }
                event.startMs > now -> {
                    // Reschedule future timed alarms
                    NotificationHelper.scheduleAt(ctx, event.id, event.startMs)
                }
            }
        }
    }
}
