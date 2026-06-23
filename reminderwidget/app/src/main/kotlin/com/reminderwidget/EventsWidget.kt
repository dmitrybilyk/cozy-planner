package com.reminderwidget

import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Locale

class EventsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    companion object {
        private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dateFmt = SimpleDateFormat("dd.MM", Locale.getDefault())

        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, EventsWidget::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs   = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            val bgColor = prefs.getInt(MainActivity.KEY_WIDGET_BG, 0xDD000000.toInt())
            val snooze1Min = context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)
                .getInt(NotificationHelper.KEY_SNOOZE1_MIN, 5).coerceAtLeast(1)

            val now    = System.currentTimeMillis()
            val all    = EventStore.load(context).filter { !it.completed }
            val noTime   = all.filter { !it.hasTime }.sortedByDescending { it.id }
            val upcoming = all.filter { it.hasTime && it.startMs >= now }.sortedBy { it.startMs }
            val overdue  = all.filter { it.hasTime && it.startMs < now  }.sortedBy { it.startMs }
            // Show upcoming first, then overdue, then no-time — cap at 5 rows
            val active = (upcoming + overdue + noTime).take(5)

            val views = RemoteViews(context.packageName, R.layout.widget_events)
            views.setInt(R.id.widget_events_root, "setBackgroundColor", bgColor)

            val openAppPi = PendingIntent.getActivity(
                context, id + 200,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val micPi = PendingIntent.getActivity(
                context, id + 201,
                Intent(context, VoiceActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val totalActive = all.size
            views.setTextViewText(R.id.widget_count_label,
                if (totalActive == 0) "Remindly" else "Remindly  ·  $totalActive")
            views.setOnClickPendingIntent(R.id.widget_header, openAppPi)
            views.setOnClickPendingIntent(R.id.widget_mic_btn, micPi)

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (active.isEmpty()) {
                views.setViewVisibility(R.id.empty_text, View.VISIBLE)
                views.setViewVisibility(R.id.events_list, View.GONE)
            } else {
                views.setViewVisibility(R.id.empty_text, View.GONE)
                views.setViewVisibility(R.id.events_list, View.VISIBLE)
                views.removeAllViews(R.id.events_list)

                active.forEach { event ->
                    val row = RemoteViews(context.packageName, R.layout.widget_event_row)

                    // Time badge
                    val isPast  = event.hasTime && event.startMs < now
                    val isToday = event.hasTime && isSameDay(event.startMs, now)
                    val timeLabel = when {
                        !event.hasTime -> "📍"
                        isToday        -> timeFmt.format(event.startMs)
                        else           -> dateFmt.format(event.startMs)
                    }
                    val timeColor = when {
                        !event.hasTime -> 0xFF4FC3F7.toInt()   // light blue for location
                        isPast         -> 0xFFFF7043.toInt()   // orange-red for overdue
                        else           -> 0xFF90CAF9.toInt()   // light blue for upcoming
                    }
                    row.setTextViewText(R.id.event_time, timeLabel)
                    row.setTextColor(R.id.event_time, timeColor)

                    // Title — dim if no active notification
                    val hasNotif = nm.activeNotifications.any { it.id == NotificationHelper.notifId(event.id) }
                    row.setTextViewText(R.id.event_title, event.title)
                    row.setTextColor(R.id.event_title,
                        if (hasNotif) Color.WHITE else 0xAABBBBBB.toInt())

                    // Done button
                    val donePi = PendingIntent.getBroadcast(
                        context,
                        NotificationHelper.notifId(event.id) + 60,
                        Intent(context, SnoozeReceiver::class.java).apply {
                            action = NotificationHelper.ACTION_DONE
                            putExtra(NotificationHelper.EXTRA_EVENT_ID, event.id)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    row.setOnClickPendingIntent(R.id.btn_done, donePi)

                    // Snooze button — use snooze1 preference; no-time events: ring notification instead
                    if (event.hasTime) {
                        val snoozePi = PendingIntent.getBroadcast(
                            context,
                            NotificationHelper.notifId(event.id) + 61,
                            Intent(context, SnoozeReceiver::class.java).apply {
                                action = NotificationHelper.ACTION_SNOOZE
                                putExtra(NotificationHelper.EXTRA_EVENT_ID, event.id)
                                putExtra(NotificationHelper.EXTRA_SNOOZE_MINUTES, snooze1Min)
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        row.setOnClickPendingIntent(R.id.btn_snooze, snoozePi)
                        row.setTextViewText(R.id.btn_snooze, "⏰")
                    } else {
                        // No-time: ⏰ posts/shows the notification (quick reminder)
                        val repostPi = PendingIntent.getBroadcast(
                            context,
                            NotificationHelper.notifId(event.id) + 61,
                            Intent(context, SnoozeReceiver::class.java).apply {
                                action = NotificationHelper.ACTION_REPOST
                                putExtra(NotificationHelper.EXTRA_EVENT_ID, event.id)
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        row.setOnClickPendingIntent(R.id.btn_snooze, repostPi)
                        row.setTextViewText(R.id.btn_snooze, "🔔")
                    }

                    // Row tap opens app
                    row.setOnClickPendingIntent(R.id.event_title, openAppPi)

                    views.addView(R.id.events_list, row)
                }
            }

            manager.updateAppWidget(id, views)
        }

        private fun isSameDay(ms1: Long, ms2: Long): Boolean {
            val c1 = java.util.Calendar.getInstance().apply { timeInMillis = ms1 }
            val c2 = java.util.Calendar.getInstance().apply { timeInMillis = ms2 }
            return c1.get(java.util.Calendar.YEAR)        == c2.get(java.util.Calendar.YEAR) &&
                   c1.get(java.util.Calendar.DAY_OF_YEAR) == c2.get(java.util.Calendar.DAY_OF_YEAR)
        }
    }
}
