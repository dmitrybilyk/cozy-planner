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
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import kotlinx.datetime.*

object NotificationHelper {
    const val CHANNEL_ID = "linkease_sessions"
    const val BIRTHDAY_CHANNEL_ID = "linkease_birthdays"
    const val BOOKINGS_CHANNEL_ID = "linkease_bookings"
    const val CLIENT_SESSIONS_CHANNEL_ID = "linkease_client_sessions"
    const val CLIENT_AVAILABILITY_CHANNEL_ID = "linkease_client_avail"
    const val CHAT_CHANNEL_ID = "linkease_chat"
    const val CHAT_REPLY_KEY = "chat_reply_text"
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

    fun createBookingsChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(BOOKINGS_CHANNEL_ID, "Запити на бронювання", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Нові запити від клієнтів"
                enableVibration(true)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    fun createClientSessionsChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CLIENT_SESSIONS_CHANNEL_ID, "Заняття від тренера", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Нові та оновлені заняття від тренера"
                enableVibration(true)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    fun showBookingRequestNotification(context: Context, request: BookingRequest) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dateStr = "${request.date.dayOfMonth}.${request.date.monthNumber.toString().padStart(2,'0')}"
        val notifId = 5000 + request.id.hashCode()
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun actionIntent(action: String) = PendingIntent.getBroadcast(
            context, notifId + action.hashCode(),
            Intent(context, SessionActionReceiver::class.java).apply {
                putExtra("session_action", action)
                putExtra("booking_request_id", request.id)
                putExtra("notif_id", notifId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, BOOKINGS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("📅 Новий запит на бронювання")
            .setContentText("$dateStr ${request.startTime.toStorageString()}–${request.endTime.toStorageString()}")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_media_play, "✓ Підтвердити", actionIntent("confirm_booking"))
            .addAction(android.R.drawable.ic_delete, "✕ Відхилити", actionIntent("decline_booking"))
            .build()
        nm.notify(notifId, n)
    }

    fun showBookingConfirmedNotification(context: Context, request: BookingRequest) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dateStr = "${request.date.dayOfMonth}.${request.date.monthNumber.toString().padStart(2,'0')}"
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, request.id.hashCode() + 9000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CLIENT_SESSIONS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("✅ Сесію підтверджено")
            .setContentText("$dateStr ${request.startTime.toStorageString()}–${request.endTime.toStorageString()}")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()
        nm.notify(9000 + request.id.hashCode(), n)
    }

    fun showClientSessionNotification(context: Context, session: ClientSession, clientFirebaseId: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dateStr = "${session.date.dayOfMonth}.${session.date.monthNumber.toString().padStart(2,'0')}"
        val notifId = 6000 + session.id.hashCode()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun actionIntent(action: String) = PendingIntent.getBroadcast(
            context,
            notifId + action.hashCode(),
            Intent(context, SessionActionReceiver::class.java).apply {
                putExtra("session_action", action)
                putExtra("session_id", session.id)
                putExtra("client_firebase_id", clientFirebaseId)
                putExtra("notif_id", notifId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(context, CLIENT_SESSIONS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("📋 Нова сесія")
            .setContentText("$dateStr ${session.startTime.toStorageString()}–${session.endTime.toStorageString()}")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_media_play, "✓ Підтвердити", actionIntent("confirm"))
            .addAction(android.R.drawable.ic_delete, "✕ Відхилити", actionIntent("reject"))
            .build()
        nm.notify(notifId, n)
    }

    fun showNewConnectionNotification(context: Context, firebaseId: String, email: String?) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "open_clients_screen")
        }
        val pending = PendingIntent.getActivity(context, firebaseId.hashCode() + 8000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = if (!email.isNullOrBlank()) "🔔 Підключився: $email" else "🔔 Новий клієнт підключився"
        val n = NotificationCompat.Builder(context, BOOKINGS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText("Натисніть, щоб прив'язати в менеджері клієнтів")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()
        nm.notify(8000 + firebaseId.hashCode(), n)
    }

    fun showClientSeriesNotification(context: Context, sessions: List<ClientSession>, clientFirebaseId: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = 6500
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val count = sessions.size
        val dateRange = if (sessions.size > 1) {
            val sorted = sessions.sortedBy { it.date }
            val first = sorted.first()
            val last = sorted.last()
            "${first.date.dayOfMonth}.${first.date.monthNumber.toString().padStart(2,'0')} – ${last.date.dayOfMonth}.${last.date.monthNumber.toString().padStart(2,'0')}"
        } else {
            val s = sessions.first()
            "${s.date.dayOfMonth}.${s.date.monthNumber.toString().padStart(2,'0')} ${s.startTime.toStorageString()}–${s.endTime.toStorageString()}"
        }
        val n = NotificationCompat.Builder(context, CLIENT_SESSIONS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("📋 $count нових сесій")
            .setContentText(dateRange)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPending)
            .build()
        nm.notify(notifId, n)
    }

    fun createClientAvailabilityChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CLIENT_AVAILABILITY_CHANNEL_ID, "Доступність клієнтів", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Клієнт встановив свою доступність"
                enableVibration(true)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    fun createChatChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHAT_CHANNEL_ID, "Повідомлення", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Нові повідомлення від тренера або клієнта"
                enableVibration(true)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    fun showClientAvailabilityNotification(context: Context, clientId: String, clientName: String?) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "open_clients_screen")
            putExtra("client_firebase_id", clientId)
        }
        val pending = PendingIntent.getActivity(context, clientId.hashCode() + 7000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CLIENT_AVAILABILITY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("📆 ${clientName ?: "Клієнт"} встановив доступність")
            .setContentText("Перегляньте та призначте сесію")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .build()
        nm.notify(7000 + clientId.hashCode(), n)
    }

    fun showChatNotification(
        context: Context,
        senderId: String,
        text: String,
        trainerId: String = "",
        clientId: String = "",
        myId: String = "",
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = 8000 + senderId.hashCode()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "open_chat")
            putExtra("chat_sender_id", senderId)
        }
        val openPending = PendingIntent.getActivity(context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Inline reply RemoteInput
        val remoteInput = RemoteInput.Builder(CHAT_REPLY_KEY)
            .setLabel("Відповісти…")
            .build()

        fun replyPending(extraReply: String? = null): PendingIntent {
            val replyIntent = Intent(context, ChatReplyReceiver::class.java).apply {
                putExtra("trainer_id", trainerId)
                putExtra("client_id", clientId)
                putExtra("sender_id", myId)
                putExtra("notif_id", notifId)
                extraReply?.let { putExtra("quick_reply", it) }
            }
            val rc = notifId + (extraReply?.hashCode() ?: 1)
            return PendingIntent.getBroadcast(context, rc, replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        }

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "Відповісти", replyPending()
        ).addRemoteInput(remoteInput).build()

        val okAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_play, "OK", replyPending("OK")
        ).build()

        val n = NotificationCompat.Builder(context, CHAT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("💬 Нове повідомлення")
            .setContentText(text.take(100))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPending)
            .addAction(replyAction)
            .addAction(okAction)
            .build()
        nm.notify(notifId, n)
    }

    fun createBirthdayChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BIRTHDAY_CHANNEL_ID,
                "Дні народження",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Нагадування про дні народження клієнтів"
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
