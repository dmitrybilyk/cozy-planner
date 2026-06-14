package com.linkease

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.linkease.db.LinkDatabaseHelper
import com.linkease.db.AndroidSessionRepository
import com.linkease.db.AndroidClientRepository
import com.linkease.db.AndroidLocationRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        val db = LinkDatabaseHelper(context)
        val sessions  = AndroidSessionRepository(db).getAll()
        val clients   = AndroidClientRepository(db).getAll()
        val locations = AndroidLocationRepository(db).getAll()
        NotificationHelper.rescheduleAll(context, sessions, clients, locations)
    }
}
