package com.reminderwidget

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.*

object NotificationHelper {
    const val CHANNEL_ID        = "reminders"
    const val CHANNEL_ID_SILENT = "reminders_silent"
    // legacy per-duration actions kept for in-flight alarms
    const val ACTION_SNOOZE1        = "com.reminderwidget.SNOOZE1"
    const val ACTION_SNOOZE5        = "com.reminderwidget.SNOOZE5"
    const val ACTION_SNOOZE         = "com.reminderwidget.SNOOZE"
    const val ACTION_SNOOZE10       = "com.reminderwidget.SNOOZE10"
    const val ACTION_SNOOZE15       = "com.reminderwidget.SNOOZE15"
    const val ACTION_DONE           = "com.reminderwidget.DONE"
    const val ACTION_REPOST         = "com.reminderwidget.REPOST"
    const val ACTION_DISMISSED      = "com.reminderwidget.DISMISSED"
    const val ACTION_LIST_CHANGED   = "com.reminderwidget.NOTIF_LIST_CHANGED"
    const val ACTION_REPEAT_TOGGLE  = "com.reminderwidget.REPEAT_TOGGLE"
    const val ACTION_REPEAT_FIRE    = "com.reminderwidget.REPEAT_FIRE"
    const val ACTION_PIN            = "com.reminderwidget.PIN"
    const val ACTION_POSTPONE_DAY   = "com.reminderwidget.POSTPONE_DAY"
    const val ACTION_SHARE_GROUP    = "com.reminderwidget.SHARE_GROUP"
    const val ACTION_DISMISS_NOTIF  = "com.reminderwidget.DISMISS_NOTIF"
    const val EXTRA_EVENT_ID        = "event_id"
    const val EXTRA_SILENT          = "silent"
    const val EXTRA_FULLSCREEN      = "fullscreen"
    const val EXTRA_SNOOZE_MINUTES  = "snooze_minutes"
    const val KEY_SNOOZE1_MIN       = "snooze1_min"
    const val KEY_SNOOZE2_MIN       = "snooze2_min"

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

    fun scheduleAt(context: Context, eventId: Long, triggerMs: Long, silent: Boolean = false, fullscreen: Boolean = false) {
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

    fun post(
        context: Context,
        event: EventStore.AppEvent,
        ongoing: Boolean = false,
        silent: Boolean = false,
        fullscreen: Boolean = false,
        pinned: Boolean = false,
    ) {
        ensureChannel(context)
        ensureSilentChannel(context)
        val nm        = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val nid       = notifId(event.id)
        val channelId = if (silent) CHANNEL_ID_SILENT else CHANNEL_ID
        val fmt  = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        val body = if (event.rrule != null) "🔁 повторюється · ${fmt.format(Date(event.startMs))}"
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

        val snoozePrefs  = context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)
        val snooze1Min   = snoozePrefs.getInt(KEY_SNOOZE1_MIN, 1).coerceAtLeast(1)
        val snooze2Min   = snoozePrefs.getInt(KEY_SNOOZE2_MIN, 30).coerceAtLeast(1)
        val donePi         = broadcastPi(context, nid,      ACTION_DONE,          event.id)
        val repeatPi       = broadcastPi(context, nid + 7,  ACTION_REPEAT_TOGGLE, event.id)
        val pinPi          = broadcastPi(context, nid + 8,  ACTION_PIN,           event.id)
        val postponePi     = broadcastPi(context, nid + 9,  ACTION_POSTPONE_DAY,  event.id)
        val snoozePinPi    = snoozeMinPi(context, nid + 10, event.id, 1)
        val snoozePi1      = snoozeMinPi(context, nid + 4,  event.id, snooze1Min)
        val snoozePi2      = snoozeMinPi(context, nid + 5,  event.id, snooze2Min)
        val dismissPi      = broadcastPi(context, nid + 1,  ACTION_DISMISSED,     event.id)
        val dismissNotifPi = broadcastPi(context, nid + 13, ACTION_DISMISS_NOTIF, event.id)

        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val titleColor = if (isDark) Color.WHITE else Color.BLACK
        val btnBg = if (isDark) 0x55AAAAAA.toInt() else 0xFF37474F.toInt()
        val btnText = Color.WHITE

        val repeating = isRepeating(context, event.id)
        val repeatBg  = if (repeating) 0xFFFF5722.toInt() else btnBg

        fun fmtMin(m: Int) = if (m >= 60) "${m / 60}г${if (m % 60 != 0) "${m % 60}'" else ""}" else "$m'"

        fun makeViews(showBody: Boolean): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.notification_alarm)
            v.setTextViewText(R.id.notif_title, event.title)
            v.setTextColor(R.id.notif_title, titleColor)
            v.setTextViewText(R.id.notif_body, body)
            v.setViewVisibility(R.id.notif_body, if (showBody) View.VISIBLE else View.GONE)
            if (pinned) {
                v.setViewVisibility(R.id.row_buttons, View.GONE)
                v.setViewVisibility(R.id.pinned_row, View.VISIBLE)
                v.setInt(R.id.btn_done_pinned, "setBackgroundResource", R.drawable.btn_done_pinned_bg)
                v.setTextColor(R.id.btn_done_pinned, Color.WHITE)
                v.setOnClickPendingIntent(R.id.btn_done_pinned, donePi)
                v.setInt(R.id.btn_postpone_day, "setBackgroundResource", R.drawable.btn_postpone_bg)
                v.setTextColor(R.id.btn_postpone_day, Color.WHITE)
                v.setOnClickPendingIntent(R.id.btn_postpone_day, postponePi)
                v.setInt(R.id.btn_snooze_pin, "setBackgroundColor", if (repeating) 0xFFFF5722.toInt() else btnBg)
                v.setTextColor(R.id.btn_snooze_pin, btnText)
                v.setOnClickPendingIntent(R.id.btn_snooze_pin, repeatPi)
                if (!event.hasTime) {
                    v.setViewVisibility(R.id.btn_add_calendar, View.VISIBLE)
                    v.setTextViewText(R.id.btn_add_calendar, "🔕")
                    v.setInt(R.id.btn_add_calendar, "setBackgroundColor", btnBg)
                    v.setOnClickPendingIntent(R.id.btn_add_calendar, dismissNotifPi)
                } else {
                    v.setViewVisibility(R.id.btn_add_calendar, View.GONE)
                }
            } else {
                v.setViewVisibility(R.id.row_buttons, View.VISIBLE)
                v.setViewVisibility(R.id.pinned_row, View.GONE)
                v.setOnClickPendingIntent(R.id.btn_done, donePi)
                v.setOnClickPendingIntent(R.id.btn_repeat, repeatPi)
                v.setOnClickPendingIntent(R.id.btn_pin, pinPi)
                listOf(R.id.btn_snooze_a, R.id.btn_snooze_b, R.id.btn_done, R.id.btn_pin).forEach { id ->
                    v.setInt(id, "setBackgroundColor", btnBg)
                    v.setTextColor(id, btnText)
                }
                v.setInt(R.id.btn_repeat, "setBackgroundColor", repeatBg)
                v.setTextColor(R.id.btn_repeat, btnText)
                v.setTextViewText(R.id.btn_snooze_a, fmtMin(snooze1Min))
                v.setTextViewText(R.id.btn_snooze_b, fmtMin(snooze2Min))
                v.setOnClickPendingIntent(R.id.btn_snooze_a, snoozePi1)
                v.setOnClickPendingIntent(R.id.btn_snooze_b, snoozePi2)
            }
            return v
        }

        val builder = Notification.Builder(context, channelId)
            .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_notification))
            .setContentTitle(event.title)
            .setContentText(body)
            .setColor(0xFFFF5722.toInt())
            .setColorized(true)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(makeViews(false))
            .setCustomBigContentView(makeViews(true))
            .setContentIntent(alertPi)
            .setDeleteIntent(dismissPi)
            .setAutoCancel(false)
            .setOngoing(ongoing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        if (!silent) {
            builder.setCategory(Notification.CATEGORY_ALARM)
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

    private fun snoozeMinPi(context: Context, reqCode: Int, eventId: Long, minutes: Int) =
        PendingIntent.getBroadcast(
            context, reqCode,
            Intent(context, SnoozeReceiver::class.java).also {
                it.action = ACTION_SNOOZE
                it.putExtra(EXTRA_EVENT_ID, eventId)
                it.putExtra(EXTRA_SNOOZE_MINUTES, minutes)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun notifId(eventId: Long): Int = (eventId and 0x7FFF_FFFFL).toInt()

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null && existing.importance < NotificationManager.IMPORTANCE_DEFAULT) {
            nm.deleteNotificationChannel(CHANNEL_ID)
        }
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
        val existing = nm.getNotificationChannel(CHANNEL_ID_SILENT)
        if (existing != null && existing.importance == NotificationManager.IMPORTANCE_NONE) {
            nm.deleteNotificationChannel(CHANNEL_ID_SILENT)
        }
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
