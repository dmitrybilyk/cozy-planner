package com.reminderwidget

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.*

object NotificationHelper {
    const val CHANNEL_ID        = "reminders"
    const val CHANNEL_ID_SILENT = "reminders_silent"
    const val ACTION_SNOOZE1        = "com.reminderwidget.SNOOZE1"
    const val ACTION_SNOOZE5        = "com.reminderwidget.SNOOZE5"
    const val ACTION_SNOOZE         = "com.reminderwidget.SNOOZE"    // legacy 10 min
    const val ACTION_SNOOZE15       = "com.reminderwidget.SNOOZE15"
    const val ACTION_DONE           = "com.reminderwidget.DONE"
    const val ACTION_REPOST         = "com.reminderwidget.REPOST"
    const val ACTION_DISMISSED      = "com.reminderwidget.DISMISSED"
    const val ACTION_LIST_CHANGED   = "com.reminderwidget.NOTIF_LIST_CHANGED"
    const val ACTION_REPEAT_TOGGLE  = "com.reminderwidget.REPEAT_TOGGLE"
    const val ACTION_REPEAT_FIRE    = "com.reminderwidget.REPEAT_FIRE"
    const val EXTRA_EVENT_ID        = "event_id"
    const val EXTRA_SILENT          = "silent"
    const val EXTRA_FULLSCREEN      = "fullscreen"

    private fun repeatPrefs(context: Context) =
        context.getSharedPreferences("repeat_state", Context.MODE_PRIVATE)

    fun isRepeating(context: Context, eventId: Long) =
        repeatPrefs(context).getBoolean("r_$eventId", false)

    fun setRepeating(context: Context, eventId: Long, value: Boolean) =
        repeatPrefs(context).edit().putBoolean("r_$eventId", value).apply()

    fun scheduleRepeat(context: Context, eventId: Long) {
        val pi = repeatFirePi(context, eventId)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerMs = System.currentTimeMillis() + 60_000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms())
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        else
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
    }

    fun cancelRepeat(context: Context, eventId: Long) {
        val pi = PendingIntent.getBroadcast(
            context, notifId(eventId) + 30,
            Intent(context, SnoozeReceiver::class.java).apply { action = ACTION_REPEAT_FIRE },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
        pi.cancel()
    }

    private fun repeatFirePi(context: Context, eventId: Long) = PendingIntent.getBroadcast(
        context, notifId(eventId) + 30,
        Intent(context, SnoozeReceiver::class.java).apply {
            action = ACTION_REPEAT_FIRE
            putExtra(EXTRA_EVENT_ID, eventId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun scheduleAt(context: Context, eventId: Long, triggerMs: Long, silent: Boolean = false, fullscreen: Boolean = true) {
        val pi = PendingIntent.getBroadcast(
            context, notifId(eventId) + 20,
            Intent(context, SnoozeReceiver::class.java).apply {
                action = ACTION_REPOST
                putExtra(EXTRA_EVENT_ID, eventId)
                putExtra(EXTRA_SILENT, silent)
                putExtra(EXTRA_FULLSCREEN, fullscreen)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
    }

    fun cancelAlarm(context: Context, eventId: Long) {
        val pi = PendingIntent.getBroadcast(
            context, notifId(eventId) + 20,
            Intent(context, SnoozeReceiver::class.java).apply { action = ACTION_REPOST },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
        pi.cancel()
    }

    /**
     * Post or update the notification for an event.
     * silent=true  → low-importance silent channel, no sound/vibration, no full-screen
     * fullscreen   → shows ReminderAlertActivity on top of lock screen (ignored when silent)
     * ongoing      → sticky (can't be swiped away)
     * pinned=true  → no snooze actions; only "Done" (used for the 🔔 status-bar pin)
     */
    fun post(
        context: Context,
        event: EventStore.AppEvent,
        ongoing: Boolean = false,
        silent: Boolean = false,
        fullscreen: Boolean = true,
        pinned: Boolean = false,
    ) {
        ensureChannel(context)
        ensureSilentChannel(context)
        val nm        = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val nid       = notifId(event.id)
        val channelId = if (silent) CHANNEL_ID_SILENT else CHANNEL_ID
        val fmt       = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        val body      = if (event.rrule != null) "🔁 повторюється · ${fmt.format(Date(event.startMs))}"
                        else fmt.format(Date(event.startMs))

        val alertIntent = Intent(context, ReminderAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_EVENT_ID, event.id)
            putExtra("alert_title", event.title)
            putExtra("alert_start_ms", event.startMs)
        }
        val alertPi = PendingIntent.getActivity(
            context, nid + 3, alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val donePi      = broadcastPi(context, nid,      ACTION_DONE,           event.id)
        val repeatPi    = broadcastPi(context, nid + 7,  ACTION_REPEAT_TOGGLE,  event.id)
        val snooze1Pi   = broadcastPi(context, nid + 6,  ACTION_SNOOZE1,        event.id)
        val snooze5Pi   = broadcastPi(context, nid + 4,  ACTION_SNOOZE5,        event.id)
        val snooze15Pi  = broadcastPi(context, nid + 5,  ACTION_SNOOZE15,       event.id)
        val dismissPi   = broadcastPi(context, nid + 1,  ACTION_DISMISSED,      event.id)

        val builder = Notification.Builder(context, channelId)
            .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_launcher_fg))
            .setContentTitle(event.title)
            .setContentText(body)
            .setColor(0xFFFF5722.toInt())
            .setColorized(true)
            .setStyle(Notification.BigTextStyle().setBigContentTitle(event.title).bigText(body))
            .setContentIntent(alertPi)
            .setDeleteIntent(dismissPi)
            .setAutoCancel(false)
            .setOngoing(ongoing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(
                Icon.createWithResource(context, android.R.drawable.ic_delete),
                "✅ Виконано", donePi).build())
            .addAction(Notification.Action.Builder(
                Icon.createWithResource(context, android.R.drawable.ic_popup_reminder),
                if (isRepeating(context, event.id)) "✅" else "🔁",
                repeatPi).build())

        if (!pinned) {
            builder
                .addAction(Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_popup_reminder),
                    "⏰ 1 хв", snooze1Pi).build())
                .addAction(Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_popup_reminder),
                    "⏰ 5 хв", snooze5Pi).build())
                .addAction(Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_popup_reminder),
                    "⏰ 15 хв", snooze15Pi).build())
        }

        if (!silent) {
            builder.setCategory(Notification.CATEGORY_ALARM)
            if (fullscreen) builder.setFullScreenIntent(alertPi, true)
        }

        nm.notify(nid, builder.build())
        context.sendBroadcast(Intent(ACTION_LIST_CHANGED))
    }

    private fun broadcastPi(context: Context, reqCode: Int, action: String, eventId: Long) =
        PendingIntent.getBroadcast(
            context, reqCode,
            Intent(context, SnoozeReceiver::class.java).also {
                it.action = action; it.putExtra(EXTRA_EVENT_ID, eventId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun notifId(eventId: Long): Int = (eventId and 0x7FFF_FFFFL).toInt()

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Нагадування", NotificationManager.IMPORTANCE_HIGH).apply {
                    val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    setSound(defaultSound, AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    fun ensureSilentChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID_SILENT) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID_SILENT, "Нагадування (тихо)", NotificationManager.IMPORTANCE_LOW).apply {
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    fun getChannelSoundName(context: Context): String {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sound = nm.getNotificationChannel(CHANNEL_ID)?.sound ?: return "За замовчуванням"
        return RingtoneManager.getRingtone(context, sound)?.getTitle(context) ?: "Власний звук"
    }
}
