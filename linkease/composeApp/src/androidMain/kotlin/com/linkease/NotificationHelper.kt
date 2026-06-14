package com.linkease

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.datetime.*

object NotificationHelper {
    const val CHANNEL_ID = "linkease_sessions"
    private const val REMIND_MINUTES_BEFORE = 10L

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Заняття",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Нагадування перед початком заняття"
                enableVibration(true)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
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
