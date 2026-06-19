package com.linkease

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.linkease.db.AndroidClientRepository
import com.linkease.db.LinkDatabaseHelper
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class BirthdayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val todayMmDd = "${today.monthNumber.toString().padStart(2, '0')}-${today.dayOfMonth.toString().padStart(2, '0')}"

        val clients = AndroidClientRepository(LinkDatabaseHelper(context)).getAll()
        val birthdayClients = clients.filter { it.birthDate == todayMmDd }
        if (birthdayClients.isEmpty()) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        birthdayClients.forEachIndexed { idx, client ->
            val notification = NotificationCompat.Builder(context, NotificationHelper.BIRTHDAY_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🎂 День народження!")
                .setContentText("Сьогодні день народження у ${client.name}!")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            nm.notify(1000 + idx, notification)
        }
    }
}
