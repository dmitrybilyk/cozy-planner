package com.reminderwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

object PersistentNotif {
    private const val CHANNEL_ID = "persistent_status"
    const val NOTIF_ID           = 9001
    const val KEY_ENABLED        = "persistent_notif"

    fun isEnabled(ctx: Context) =
        ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Статус-бар (мікрофон)", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    fun update(ctx: Context) {
        if (!isEnabled(ctx)) { cancel(ctx); return }
        ensureChannel(ctx)
        val nm  = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val now = System.currentTimeMillis()
        val next = EventStore.load(ctx)
            .filter { !it.completed && it.startMs > now }
            .minByOrNull { it.startMs }

        val voicePi = PendingIntent.getActivity(
            ctx, NOTIF_ID,
            Intent(ctx, VoiceActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(Icon.createWithResource(ctx, R.drawable.ic_notification))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(voicePi)
            .setColor(0xFFFF5722.toInt())

        if (next != null) {
            builder.setContentTitle(next.title)
            builder.setContentText("🎙 Натисніть для нового нагадування")
            builder.setWhen(next.startMs)
            builder.setShowWhen(true)
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)
        } else {
            builder.setContentTitle("🎙 Нагадування")
            builder.setContentText("Натисніть для нового нагадування")
            builder.setShowWhen(false)
        }

        nm.notify(NOTIF_ID, builder.build())
    }

    fun cancel(ctx: Context) {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
    }
}
