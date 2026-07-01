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
    const val ACTION_ADD_CALENDAR_NOTIF = "com.reminderwidget.ADD_CALENDAR_NOTIF"
    const val EXTRA_EVENT_ID        = "event_id"
    const val EXTRA_SILENT          = "silent"
    const val EXTRA_FULLSCREEN      = "fullscreen"
    const val EXTRA_SNOOZE_MINUTES  = "snooze_minutes"
    const val KEY_SNOOZE1_MIN       = "snooze1_min"
    const val KEY_SNOOZE2_MIN       = "snooze2_min"
    const val KEY_SNOOZE1_IS_DAYS   = "snooze1_is_days"
    const val KEY_SNOOZE2_IS_DAYS   = "snooze2_is_days"

    private fun repeatPrefs(context: Context) =
        context.getSharedPreferences("repeat_state", Context.MODE_PRIVATE)

    fun isRepeating(context: Context, eventId: Long) =
        repeatPrefs(context).getBoolean("r_$eventId", false)

    fun setRepeating(context: Context, eventId: Long, value: Boolean) =
        repeatPrefs(context).edit().putBoolean("r_$eventId", value).apply()

    private fun pinnedPrefs(context: Context) =
        context.getSharedPreferences("pinned_state", Context.MODE_PRIVATE)

    fun isPinned(context: Context, eventId: Long) =
        pinnedPrefs(context).getBoolean("p_$eventId", false)

    fun setPinned(context: Context, eventId: Long, value: Boolean) =
        pinnedPrefs(context).edit().putBoolean("p_$eventId", value).apply()

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
            // setAlarmClock has highest OEM priority — shown in status bar, not killed by battery optimizers
            val showIntent = PendingIntent.getActivity(
                context, notifId(eventId) + 21,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMs, showIntent), pi)
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
        locationTriggered: Boolean = false,
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

        val snoozePrefs   = context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)
        val snooze1Value  = snoozePrefs.getInt(KEY_SNOOZE1_MIN, 1).coerceAtLeast(1)
        val snooze2Value  = snoozePrefs.getInt(KEY_SNOOZE2_MIN, 30).coerceAtLeast(1)
        val snooze1IsDays = snoozePrefs.getBoolean(KEY_SNOOZE1_IS_DAYS, false)
        val snooze2IsDays = snoozePrefs.getBoolean(KEY_SNOOZE2_IS_DAYS, false)
        val snooze1Min    = if (snooze1IsDays) snooze1Value * 24 * 60 else snooze1Value
        val snooze2Min    = if (snooze2IsDays) snooze2Value * 24 * 60 else snooze2Value
        val donePi         = broadcastPi(context, nid,      ACTION_DONE,          event.id)
        val repeatPi       = broadcastPi(context, nid + 7,  ACTION_REPEAT_TOGGLE, event.id)
        val pinPi          = broadcastPi(context, nid + 8,  ACTION_PIN,           event.id)
        val postponePi     = broadcastPi(context, nid + 9,  ACTION_POSTPONE_DAY,  event.id)
        val snoozePinPi    = snoozeMinPi(context, nid + 10, event.id, 1)
        val snoozePi1      = snoozeMinPi(context, nid + 4,  event.id, snooze1Min)
        val snoozePi2      = snoozeMinPi(context, nid + 5,  event.id, snooze2Min)
        val dismissPi      = broadcastPi(context, nid + 1,  ACTION_DISMISSED,       event.id)
        val dismissNotifPi = broadcastPi(context, nid + 13, ACTION_DISMISS_NOTIF,   event.id)
        val gcalNotifPi    = broadcastPi(context, nid + 15, ACTION_ADD_CALENDAR_NOTIF, event.id)

        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val titleColor = if (isDark) Color.WHITE else Color.BLACK
        val btnText = Color.WHITE

        val repeating = isRepeating(context, event.id)
        if (pinned) setPinned(context, event.id, true)
        val effectivePinned = pinned || isPinned(context, event.id)

        val openAppPi = PendingIntent.getActivity(
            context, nid + 14,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val locationPi = PendingIntent.getActivity(
            context, nid + 16,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_EVENT_ID, event.id)
                putExtra("show_location_picker", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun fmtMin(m: Int) = if (m >= 60) "${m / 60}г${if (m % 60 != 0) "${m % 60}'" else ""}" else "$m'"
        fun fmtSnooze(value: Int, isDays: Boolean) = if (isDays) "${value}д" else fmtMin(value)

        fun makeViews(showBody: Boolean): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.notification_alarm)
            v.setTextViewText(R.id.notif_title, event.title)
            v.setTextColor(R.id.notif_title, titleColor)
            v.setTextViewText(R.id.notif_body, body)
            v.setViewVisibility(R.id.notif_body, if (showBody) View.VISIBLE else View.GONE)

            if (!event.hasTime && !locationTriggered) {
                // Pinned no-time / location events: [📍 location, 📌 pin, 📅 gcal, ✅ done]
                val exported = event.calendarEventId != -1L
                val hasLoc   = event.locationName != null
                v.setViewVisibility(R.id.row_buttons, View.GONE)
                v.setViewVisibility(R.id.pinned_row, View.VISIBLE)
                v.setViewVisibility(R.id.btn_snooze_pin, View.VISIBLE)
                v.setTextViewText(R.id.btn_snooze_pin, "📍")
                v.setInt(R.id.btn_snooze_pin, "setBackgroundColor", if (hasLoc) 0xFF0D2040.toInt() else Color.TRANSPARENT)
                v.setOnClickPendingIntent(R.id.btn_snooze_pin, locationPi)
                v.setViewVisibility(R.id.btn_postpone_day, View.VISIBLE)
                v.setTextViewText(R.id.btn_postpone_day, "📌")
                v.setInt(R.id.btn_postpone_day, "setBackgroundColor", if (effectivePinned) 0xFFFFAB40.toInt() else Color.TRANSPARENT)
                v.setOnClickPendingIntent(R.id.btn_postpone_day, pinPi)
                v.setViewVisibility(R.id.btn_add_calendar, View.VISIBLE)
                v.setImageViewResource(R.id.btn_add_calendar, R.drawable.ic_export_gc)
                v.setInt(R.id.btn_add_calendar, "setColorFilter", if (exported) 0xFF66BB6A.toInt() else 0xFFFFFFFF.toInt())
                v.setInt(R.id.btn_add_calendar, "setBackgroundColor", if (exported) 0xFF1B2B0E.toInt() else Color.TRANSPARENT)
                v.setOnClickPendingIntent(R.id.btn_add_calendar, gcalNotifPi)
                v.setOnClickPendingIntent(R.id.btn_done_pinned, donePi)
            } else {
                // Has-time events: always show row_buttons
                v.setViewVisibility(R.id.pinned_row, View.GONE)
                v.setViewVisibility(R.id.row_buttons, View.VISIBLE)
                v.setOnClickPendingIntent(R.id.btn_done, donePi)
                v.setOnClickPendingIntent(R.id.btn_repeat, repeatPi)
                listOf(R.id.btn_snooze_a, R.id.btn_snooze_b, R.id.btn_done).forEach { id ->
                    v.setInt(id, "setBackgroundColor", Color.TRANSPARENT)
                    v.setTextColor(id, btnText)
                }
                // Repeat button: orange when active, transparent otherwise; shows "🔁1'"
                v.setInt(R.id.btn_repeat, "setBackgroundColor", if (repeating) 0xFFFF5722.toInt() else Color.TRANSPARENT)
                v.setTextColor(R.id.btn_repeat, btnText)
                v.setTextViewText(R.id.btn_repeat, "🔁1'")
                v.setTextViewText(R.id.btn_snooze_a, fmtSnooze(snooze1Value, snooze1IsDays))
                v.setTextViewText(R.id.btn_snooze_b, fmtSnooze(snooze2Value, snooze2IsDays))
                v.setOnClickPendingIntent(R.id.btn_snooze_a, snoozePi1)
                v.setOnClickPendingIntent(R.id.btn_snooze_b, snoozePi2)
                // Pin button: hidden when repeating (repeat = pinned); orange when already pinned
                if (repeating) {
                    v.setViewVisibility(R.id.btn_pin, View.GONE)
                } else {
                    v.setViewVisibility(R.id.btn_pin, View.VISIBLE)
                    v.setInt(R.id.btn_pin, "setBackgroundColor", if (effectivePinned) 0xFFFFAB40.toInt() else Color.TRANSPARENT)
                    v.setTextColor(R.id.btn_pin, btnText)
                    v.setOnClickPendingIntent(R.id.btn_pin, pinPi)
                }
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
