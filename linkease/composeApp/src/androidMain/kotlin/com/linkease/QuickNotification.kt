package com.linkease

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object QuickNotification {
    private const val CHANNEL_ID = "linkease_quick"
    private const val NOTIF_ID   = 9000
    private const val PREF_KEY   = "quick_notif_on"
    private const val PREFS_NAME = "linkease_prefs"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Linkease — швидкі дії", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Постійна нотифікація для швидкого створення занять"
                setShowBadge(false)
            }
            nm(context).createNotificationChannel(ch)
        }
    }

    fun show(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("action", "create_session")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, NOTIF_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Linkease")
            .setContentText("Торкніться, щоб створити заняття")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_input_add, "Створити заняття", pi)
            .build()
        nm(context).notify(NOTIF_ID, notif)
        prefs(context).edit().putBoolean(PREF_KEY, true).apply()
    }

    fun dismiss(context: Context) {
        nm(context).cancel(NOTIF_ID)
        prefs(context).edit().putBoolean(PREF_KEY, false).apply()
    }

    fun toggle(context: Context) {
        if (prefs(context).getBoolean(PREF_KEY, false)) dismiss(context) else show(context)
    }

    fun isShowing(context: Context) = prefs(context).getBoolean(PREF_KEY, false)

    private fun nm(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
