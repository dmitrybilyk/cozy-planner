package com.reminderwidget

import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class EventsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    companion object {
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, EventsWidget::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs   = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            val bgColor = prefs.getInt(MainActivity.KEY_WIDGET_BG, 0xDD000000.toInt())
            val events  = EventStore.load(context)
            val nm      = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val views = RemoteViews(context.packageName, R.layout.widget_events)
            views.setInt(R.id.widget_events_root, "setBackgroundColor", bgColor)

            val openPi = PendingIntent.getActivity(
                context, id + 200,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_events_header, openPi)

            if (events.isEmpty()) {
                views.setViewVisibility(R.id.empty_text, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.empty_text, View.GONE)
                events.take(6).forEach { event ->
                    val active = nm.activeNotifications.any {
                        it.id == NotificationHelper.notifId(event.id)
                    }
                    val row = RemoteViews(context.packageName, R.layout.widget_event_row)
                    row.setTextViewText(R.id.event_title, event.title)
                    row.setInt(R.id.event_dot, "setBackgroundResource",
                        if (active) R.drawable.dot_active else R.drawable.dot_inactive)

                    val bellPi = PendingIntent.getBroadcast(
                        context,
                        NotificationHelper.notifId(event.id) + 50,
                        Intent(context, SnoozeReceiver::class.java).apply {
                            action = NotificationHelper.ACTION_REPOST
                            putExtra(NotificationHelper.EXTRA_EVENT_ID, event.id)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    row.setOnClickPendingIntent(R.id.bell_btn, bellPi)
                    row.setOnClickPendingIntent(R.id.event_title, openPi)

                    views.addView(R.id.events_list, row)
                }
            }

            manager.updateAppWidget(id, views)
        }
    }
}
