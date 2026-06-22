package com.reminderwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object PersistentNotif {
    private const val CHANNEL_ID = "persistent_status"
    const val NOTIF_ID           = 9001
    const val KEY_ENABLED        = "persistent_notif"
    const val ACTION_HIDE        = "com.reminderwidget.HIDE_NOTIF"

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
            .filter { !it.completed && it.hasTime && it.startMs > now }
            .minByOrNull { it.startMs }

        val micPi = PendingIntent.getActivity(
            ctx, NOTIF_ID + 1,
            Intent(ctx, VoiceActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val listPi = PendingIntent.getActivity(
            ctx, NOTIF_ID + 3,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hidePi = PendingIntent.getBroadcast(
            ctx, NOTIF_ID + 2,
            Intent(ctx, NotifActionReceiver::class.java).apply { action = ACTION_HIDE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isDark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val titleColor = if (isDark) Color.WHITE else Color.BLACK

        val rv = RemoteViews(ctx.packageName, R.layout.notification_status)
        val titleText = if (next != null) next.title else "Немає нагадувань"
        rv.setTextViewText(R.id.status_title, titleText)
        rv.setTextColor(R.id.status_title, titleColor)
        rv.setInt(R.id.status_btn_mic,   "setBackgroundColor", Color.TRANSPARENT)
        rv.setInt(R.id.status_btn_list,  "setBackgroundColor", Color.TRANSPARENT)
        rv.setInt(R.id.status_btn_close, "setBackgroundColor", Color.TRANSPARENT)
        rv.setOnClickPendingIntent(R.id.status_btn_mic,   micPi)
        rv.setOnClickPendingIntent(R.id.status_btn_list,  listPi)
        rv.setOnClickPendingIntent(R.id.status_btn_close, hidePi)

        if (next != null) {
            val msUntil = next.startMs - now
            if (msUntil <= 24 * 3_600_000L) {
                // within 24h — show live countdown
                val chronoBase = SystemClock.elapsedRealtime() + msUntil
                rv.setChronometer(R.id.status_chrono, chronoBase, null, true)
                rv.setChronometerCountDown(R.id.status_chrono, true)
                rv.setViewVisibility(R.id.status_chrono, View.VISIBLE)
            } else {
                // further out — show readable date/time
                val eCal  = Calendar.getInstance().apply { timeInMillis = next.startMs }
                val today = Calendar.getInstance()
                val tmrw  = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                val dayLabel = when {
                    eCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                    eCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> "сьогодні"
                    eCal.get(Calendar.DAY_OF_YEAR) == tmrw.get(Calendar.DAY_OF_YEAR) &&
                    eCal.get(Calendar.YEAR) == tmrw.get(Calendar.YEAR) -> "завтра"
                    else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(next.startMs))
                }
                val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                rv.setChronometer(R.id.status_chrono, 0L, "$dayLabel · ${timeFmt.format(Date(next.startMs))}", false)
                rv.setChronometerCountDown(R.id.status_chrono, false)
                rv.setViewVisibility(R.id.status_chrono, View.VISIBLE)
            }
        } else {
            rv.setViewVisibility(R.id.status_chrono, View.GONE)
        }

        val builder = Notification.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(Icon.createWithResource(ctx, R.drawable.ic_notification))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(listPi)
            .setColor(0xFFFF5722.toInt())
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(rv)
            .setCustomBigContentView(rv)
            .setShowWhen(false)

        nm.notify(NOTIF_ID, builder.build())
    }

    fun cancel(ctx: Context) {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
    }
}
