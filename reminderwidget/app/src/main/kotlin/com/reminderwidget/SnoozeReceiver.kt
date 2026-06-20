package com.reminderwidget

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(NotificationHelper.EXTRA_EVENT_ID, -1L)
        if (eventId == -1L) return
        val nid = NotificationHelper.notifId(eventId)
        val nm  = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (intent.action) {
            NotificationHelper.ACTION_DONE -> {
                nm.cancel(nid)
                NotificationHelper.cancelRepeat(context, eventId)
                NotificationHelper.setRepeating(context, eventId, false)
                EventStore.markCompleted(context, eventId)
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
            }
            NotificationHelper.ACTION_SNOOZE1 -> {
                nm.cancel(nid); scheduleSnooze(context, eventId, 1L)
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
            }
            NotificationHelper.ACTION_SNOOZE5 -> {
                nm.cancel(nid); scheduleSnooze(context, eventId, 5L)
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
            }
            NotificationHelper.ACTION_SNOOZE -> {
                nm.cancel(nid); scheduleSnooze(context, eventId, 10L)
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
            }
            NotificationHelper.ACTION_SNOOZE15 -> {
                nm.cancel(nid); scheduleSnooze(context, eventId, 15L)
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
            }
            NotificationHelper.ACTION_REPOST -> {
                val event = EventStore.load(context).find { it.id == eventId } ?: return
                val silent     = intent.getBooleanExtra(NotificationHelper.EXTRA_SILENT,     false)
                val fullscreen = intent.getBooleanExtra(NotificationHelper.EXTRA_FULLSCREEN, true)
                NotificationHelper.post(context, event, silent = silent, fullscreen = fullscreen)
                EventsWidget.update(context)
            }
            NotificationHelper.ACTION_DISMISSED -> {
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
            }
            NotificationHelper.ACTION_REPEAT_TOGGLE -> {
                val event = EventStore.load(context).find { it.id == eventId } ?: return
                val wasOn = NotificationHelper.isRepeating(context, eventId)
                NotificationHelper.setRepeating(context, eventId, !wasOn)
                if (!wasOn) {
                    NotificationHelper.scheduleRepeat(context, eventId)
                } else {
                    NotificationHelper.cancelRepeat(context, eventId)
                }
                val existing = nm.activeNotifications.find { it.id == nid }
                val ongoing  = (existing?.notification?.flags ?: 0) and android.app.Notification.FLAG_ONGOING_EVENT != 0
                NotificationHelper.post(context, event, ongoing = ongoing, silent = true, fullscreen = false)
            }
            NotificationHelper.ACTION_REPEAT_FIRE -> {
                val event = EventStore.load(context).find { it.id == eventId }
                if (event == null || event.completed || !NotificationHelper.isRepeating(context, eventId)) {
                    NotificationHelper.setRepeating(context, eventId, false)
                    return
                }
                val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                android.media.RingtoneManager.getRingtone(context, soundUri)?.play()
                NotificationHelper.scheduleRepeat(context, eventId)
                val existing = nm.activeNotifications.find { it.id == nid }
                val ongoing  = (existing?.notification?.flags ?: 0) and android.app.Notification.FLAG_ONGOING_EVENT != 0
                NotificationHelper.post(context, event, ongoing = ongoing, silent = true, fullscreen = false)
            }
        }
    }

    companion object {
        fun scheduleSnooze(context: Context, eventId: Long, delayMinutes: Long) {
            NotificationHelper.cancelRepeat(context, eventId)
            NotificationHelper.setRepeating(context, eventId, false)
            val newTime = System.currentTimeMillis() + delayMinutes * 60_000L
            EventStore.updateStartMs(context, eventId, newTime)
            // sound=yes, heads-up=yes, full-screen dialog=no
            NotificationHelper.scheduleAt(context, eventId, newTime, silent = false, fullscreen = false)
        }
    }
}
