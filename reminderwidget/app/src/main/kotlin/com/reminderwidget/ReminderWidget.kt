package com.reminderwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class ReminderWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val bgColor = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getInt(MainActivity.KEY_WIDGET_BG, 0xDD000000.toInt())

            val intent = Intent(context, VoiceActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pi = PendingIntent.getActivity(
                context, widgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_reminder)
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
