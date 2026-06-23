package com.reminderwidget

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

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
                    NotificationHelper.scheduleAt(context, eventId, next.startMs)
                } else {
                    EventStore.markCompleted(context, eventId)
                }
                Toast.makeText(context, "✅ Виконано: «${event?.title ?: ""}»", Toast.LENGTH_SHORT).show()
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
                PersistentNotif.update(context)
            }
            NotificationHelper.ACTION_SNOOZE1 -> {
                nm.cancel(nid); scheduleSnooze(context, eventId, 1L)
                Toast.makeText(context, "⏰ Відкладено на 1'", Toast.LENGTH_SHORT).show()
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
            }
            NotificationHelper.ACTION_SNOOZE5 -> {
                nm.cancel(nid); scheduleSnooze(context, eventId, 5L)
                Toast.makeText(context, "⏰ Відкладено на 5'", Toast.LENGTH_SHORT).show()
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
            }
            NotificationHelper.ACTION_SNOOZE -> {
                val mins = intent.getIntExtra(NotificationHelper.EXTRA_SNOOZE_MINUTES, 5).toLong()
                nm.cancel(nid); scheduleSnooze(context, eventId, mins)
                val minStr = if (mins >= 60) "${mins / 60}г${if (mins % 60 != 0L) "${mins % 60}'" else ""}" else "$mins'"
                Toast.makeText(context, "⏰ Відкладено на $minStr", Toast.LENGTH_SHORT).show()
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
                PersistentNotif.update(context)
            }
            NotificationHelper.ACTION_SNOOZE10 -> {
                nm.cancel(nid); scheduleSnooze(context, eventId, 10L)
                Toast.makeText(context, "⏰ Відкладено на 10'", Toast.LENGTH_SHORT).show()
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
            }
            NotificationHelper.ACTION_SNOOZE15 -> {
                nm.cancel(nid); scheduleSnooze(context, eventId, 15L)
                Toast.makeText(context, "⏰ Відкладено на 15'", Toast.LENGTH_SHORT).show()
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
            }
            NotificationHelper.ACTION_DISMISS_NOTIF -> {
                nm.cancel(nid)
                Toast.makeText(context, "🔕 Прибрано зі статус-бару", Toast.LENGTH_SHORT).show()
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
            }
            NotificationHelper.ACTION_REPOST -> {
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
                    Toast.makeText(context, "🔁 Повтор увімкнено", Toast.LENGTH_SHORT).show()
                } else {
                    NotificationHelper.cancelRepeat(context, eventId)
                    Toast.makeText(context, "🔁 Повтор вимкнено", Toast.LENGTH_SHORT).show()
                }
                val existing = nm.activeNotifications.find { it.id == nid }
                val ongoing  = (existing?.notification?.flags ?: 0) and android.app.Notification.FLAG_ONGOING_EVENT != 0
                NotificationHelper.post(context, event, ongoing = ongoing, silent = true, fullscreen = false)
            }
            NotificationHelper.ACTION_PIN -> {
                val event = EventStore.load(context).find { it.id == eventId } ?: return
                if (NotificationHelper.isPinned(context, eventId)) {
                    NotificationHelper.setPinned(context, eventId, false)
                    nm.cancel(nid)
                    Toast.makeText(context, "🔕 Відкріплено зі статус-бару", Toast.LENGTH_SHORT).show()
                    context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                    EventsWidget.update(context)
                } else {
                    NotificationHelper.cancelRepeat(context, eventId)
                    NotificationHelper.setRepeating(context, eventId, false)
                    NotificationHelper.setPinned(context, eventId, true)
                    NotificationHelper.post(context, event, ongoing = true, silent = true, pinned = true)
                    Toast.makeText(context, "📌 Закріплено в статус-барі", Toast.LENGTH_SHORT).show()
                }
            }
            NotificationHelper.ACTION_POSTPONE_DAY -> {
                nm.cancel(nid)
                val event = EventStore.load(context).find { it.id == eventId } ?: return
                val newTime = event.startMs + 24 * 60 * 60_000L
                EventStore.updateStartMs(context, eventId, newTime)
                NotificationHelper.scheduleAt(context, eventId, newTime)
                Toast.makeText(context, "📅 Перенесено на завтра", Toast.LENGTH_SHORT).show()
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
                PersistentNotif.update(context)
            }
            NotificationHelper.ACTION_SHARE_GROUP -> {
                EventStore.markGroup(context, eventId, true)
                val updatedEvent = EventStore.load(context).find { it.id == eventId } ?: return
                val groupId = GroupStore.getGroupId(context)
                if (groupId != null) FirebaseSync.pushGroupEvent(groupId, updatedEvent)
                val existing = nm.activeNotifications.find { it.id == nid }
                val ongoing  = (existing?.notification?.flags ?: 0) and android.app.Notification.FLAG_ONGOING_EVENT != 0
                NotificationHelper.post(context, updatedEvent, ongoing = ongoing, silent = true, fullscreen = false, pinned = true)
                Toast.makeText(context, "👥 Поширено до групи", Toast.LENGTH_SHORT).show()
                context.sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                EventsWidget.update(context)
            }
            NotificationHelper.ACTION_ADD_CALENDAR_NOTIF -> {
                val event = EventStore.load(context).find { it.id == eventId } ?: return
                if (event.calendarEventId != -1L) {
                    Toast.makeText(context, "📅 Вже додано в Google Calendar", Toast.LENGTH_SHORT).show()
                    return
                }
                val hasPerms = android.content.pm.PackageManager.PERMISSION_GRANTED ==
                    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR)
                if (!hasPerms) {
                    Toast.makeText(context, "📅 Потрібен доступ до календаря — дозвольте в додатку", Toast.LENGTH_LONG).show()
                    context.startActivity(Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("request_calendar_perms", true)
                    })
                    return
                }
                val calId = try {
                    CalendarHelper.createReminder(
                        context, event.title,
                        if (event.startMs > 0) event.startMs else System.currentTimeMillis(),
                        event.durationMs, 0xFF4CAF50.toInt()
                    )
                } catch (_: Exception) { -1L }
                if (calId == -1L) {
                    Toast.makeText(context, "⚠️ Не вдалося додати до календаря", Toast.LENGTH_SHORT).show()
                    return
                }
                EventStore.updateCalendarId(context, eventId, calId)
                val updated = EventStore.load(context).find { it.id == eventId } ?: return
                val existing = nm.activeNotifications.find { it.id == nid }
                val ongoing = (existing?.notification?.flags ?: 0) and android.app.Notification.FLAG_ONGOING_EVENT != 0
                NotificationHelper.post(context, updated, ongoing = ongoing, silent = true, pinned = true)
                Toast.makeText(context, "📅 Додано в Google Calendar", Toast.LENGTH_SHORT).show()
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
