package com.reminderwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofencingReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "location_reminders"

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Локаційні нагадування", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val triggeredIds = event.triggeringGeofences?.map { it.requestId } ?: return
        ensureChannel(ctx)

        val allEvents = EventStore.load(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        triggeredIds.forEach { geofenceId ->
            val eventId = geofenceId.removePrefix("e").toLongOrNull() ?: return@forEach
            val appEvent = allEvents.find { it.id == eventId } ?: return@forEach
            if (appEvent.completed) return@forEach
            NotificationHelper.post(ctx, appEvent, ongoing = false, silent = false, locationTriggered = true)
            GeofenceManager.unregisterForEvent(ctx, eventId)
        }
    }
}
