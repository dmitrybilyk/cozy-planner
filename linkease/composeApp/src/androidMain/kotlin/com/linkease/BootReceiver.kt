package com.linkease

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.linkease.db.AndroidClientRepository
import com.linkease.db.AndroidLocationRepository
import com.linkease.db.AndroidSessionRepository
import com.linkease.db.LinkDatabaseHelper
import kotlinx.datetime.*

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        val prefs = context.getSharedPreferences("linkease_prefs", Context.MODE_PRIVATE)
        val notifEnabled = prefs.getBoolean("notifications_enabled", true)
        val minutesBefore = prefs.getInt("notify_minutes_before", 10).toLong()
        val db = LinkDatabaseHelper(context)
        if (notifEnabled) {
            NotificationHelper.rescheduleAll(
                context,
                AndroidSessionRepository(db).getAll(),
                AndroidClientRepository(db).getAll(),
                AndroidLocationRepository(db).getAll(),
                minutesBefore,
            )
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val birthdayPending = PendingIntent.getBroadcast(
            context, 9000,
            Intent(context, BirthdayReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(tz)
        val nextFireDate = if (now.time < LocalTime(9, 0)) now.date else now.date.plus(1, DateTimeUnit.DAY)
        val nextFire9am = LocalDateTime(nextFireDate, LocalTime(9, 0)).toInstant(tz).toEpochMilliseconds()
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, nextFire9am, AlarmManager.INTERVAL_DAY, birthdayPending)

        if (prefs.getBoolean("auto_backup_enabled", false) &&
            prefs.getString("backup_folder_uri", null) != null) {
            DriveBackupWorker.schedule(context)
        }
    }
}
