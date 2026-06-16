package com.linkease

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlinx.datetime.*

object NotificationHelper {
    const val CHANNEL_ID = "linkease_sessions"
    private const val REMIND_MINUTES_BEFORE = 10L

    fun createChannel(context: Context, soundUri: Uri? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Заняття",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Нагадування перед початком заняття"
                enableVibration(true)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(soundUri ?: Settings.System.DEFAULT_NOTIFICATION_URI, audioAttributes)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    // Channels are immutable once created on Android O+ — changing the sound
    // requires deleting and recreating the channel under the same id.
    fun recreateChannelWithSound(context: Context, soundUri: Uri?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .deleteNotificationChannel(CHANNEL_ID)
        }
        createChannel(context, soundUri)
    }

    fun sendTestNotification(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("⏰ Тестове сповіщення")
            .setContentText("Так виглядатиме нагадування про заняття")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(-1, notification)
    }

    fun scheduleSession(context: Context, session: Session, clients: List<Client>, location: Location?) {
        val tz = TimeZone.currentSystemDefault()
        val startMs = session.date.atTime(session.startTime).toInstant(tz).toEpochMilliseconds()
        val triggerMs = startMs - REMIND_MINUTES_BEFORE * 60_000L
        val nowMs = Clock.System.now().toEpochMilliseconds()
        if (triggerMs <= nowMs) return

        val clientNames = clients.joinToString(", ") { it.name }.ifBlank { "Заняття" }
        val body = buildString {
            append("${session.startTime.toStorageString()}–${session.endTime.toStorageString()}")
            location?.let { append(" · ${it.name}") }
        }

        val intent = Intent(context, SessionAlarmReceiver::class.java).apply {
            putExtra("session_id", session.id)
            putExtra("title", clientNames)
            putExtra("body", body)
        }
        val pending = PendingIntent.getBroadcast(
            context, session.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pending)
        }
    }

    fun cancelSession(context: Context, sessionId: Long) {
        val intent = Intent(context, SessionAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, sessionId.toInt(), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pending?.let {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(it)
            it.cancel()
        }
    }

    fun rescheduleAll(context: Context, sessions: List<Session>, clients: List<Client>, locations: List<Location>) {
        val clientsById = clients.associateBy { it.id }
        val locationsById = locations.associateBy { it.id }
        sessions.forEach { session ->
            scheduleSession(
                context, session,
                session.clientIds.mapNotNull { clientsById[it] },
                session.locationId?.let { locationsById[it] }
            )
        }
    }
}
