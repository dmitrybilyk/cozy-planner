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
                val event = EventStore.load(context).find { it.id == eventId }
                val next  = event?.let { advanceRecurring(it) }
                if (next != null) {
                    EventStore.update(context, next)
                    val notifOn = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                        .getBoolean(MainActivity.KEY_NOTIFICATIONS_ENABLED, true)
                    if (notifOn) NotificationHelper.scheduleAt(context, eventId, next.startMs)
                } else {
                    EventStore.markCompleted(context, eventId)
                }
                // remove from group collection so all members' alarms get cancelled
                if (event?.isGroup == true) {
                    val groupId = GroupStore.getGroupId(context)
                    if (groupId != null) FirebaseSync.deleteGroupEvent(groupId, eventId)
                }
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
                PersistentNotif.update(context)
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
                val mins = intent.getIntExtra(NotificationHelper.EXTRA_SNOOZE_MINUTES, 5).toLong()
                nm.cancel(nid); scheduleSnooze(context, eventId, mins)
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
                PersistentNotif.update(context)
            }
            NotificationHelper.ACTION_SNOOZE10 -> {
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
                val notifEnabled = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(MainActivity.KEY_NOTIFICATIONS_ENABLED, true)
                if (!notifEnabled) { EventsWidget.update(context); return }
                val event = EventStore.load(context).find { it.id == eventId } ?: return
                val silent     = intent.getBooleanExtra(NotificationHelper.EXTRA_SILENT,     false)
                val fullscreen = intent.getBooleanExtra(NotificationHelper.EXTRA_FULLSCREEN, false)
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
            NotificationHelper.ACTION_PIN -> {
                val event = EventStore.load(context).find { it.id == eventId } ?: return
                NotificationHelper.cancelRepeat(context, eventId)
                NotificationHelper.setRepeating(context, eventId, false)
                NotificationHelper.post(context, event, ongoing = true, silent = true, fullscreen = false, pinned = true)
            }
            NotificationHelper.ACTION_POSTPONE_DAY -> {
                nm.cancel(nid)
                val event = EventStore.load(context).find { it.id == eventId } ?: return
                val newTime = event.startMs + 24 * 60 * 60_000L
                EventStore.updateStartMs(context, eventId, newTime)
                val notifOn = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(MainActivity.KEY_NOTIFICATIONS_ENABLED, true)
                if (notifOn) NotificationHelper.scheduleAt(context, eventId, newTime)
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
                PersistentNotif.update(context)
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
            NotificationHelper.scheduleAt(context, eventId, newTime, silent = false, fullscreen = false)
        }

        /** Returns the event advanced to its next RRULE occurrence, or null if series is exhausted. */
        fun advanceRecurring(event: EventStore.AppEvent): EventStore.AppEvent? {
            val rrule = event.rrule ?: return null
            val countMatch = Regex("""COUNT=(\d+)""").find(rrule)
            val count = countMatch?.groupValues?.get(1)?.toIntOrNull()
            if (count != null && count <= 1) return null  // last occurrence just completed
            val intervalMs = when {
                rrule.contains("FREQ=HOURLY")  -> 3_600_000L
                rrule.contains("FREQ=DAILY")   -> 86_400_000L
                rrule.contains("FREQ=WEEKLY")  -> 7L * 86_400_000L
                else -> return null
            }
            val nextRrule = if (count != null)
                rrule.replace("COUNT=$count", "COUNT=${count - 1}")
            else rrule
            return event.copy(startMs = event.startMs + intervalMs, rrule = nextRrule, completed = false)
        }
    }
}
