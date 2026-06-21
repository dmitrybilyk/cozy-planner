package com.reminderwidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

object GeofenceManager {
    private const val TAG = "GeofenceManager"
    const val RADIUS_METERS = 100f

    private fun pendingIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 0,
            Intent(ctx, GeofencingReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

    fun registerForEvent(ctx: Context, event: EventStore.AppEvent, lat: Double, lng: Double) {
        val geofenceId = "e${event.id}"
        val geofence = Geofence.Builder()
            .setRequestId(geofenceId)
            .setCircularRegion(lat, lng, RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()
        LocationServices.getGeofencingClient(ctx)
            .addGeofences(request, pendingIntent(ctx))
            .addOnSuccessListener { Log.d(TAG, "Geofence registered: $geofenceId") }
            .addOnFailureListener { Log.w(TAG, "Geofence register failed ($geofenceId): $it") }
    }

    fun unregisterForEvent(ctx: Context, eventId: Long) {
        val geofenceId = "e$eventId"
        LocationServices.getGeofencingClient(ctx)
            .removeGeofences(listOf(geofenceId))
            .addOnSuccessListener { Log.d(TAG, "Geofence removed: $geofenceId") }
            .addOnFailureListener { Log.w(TAG, "Geofence remove failed ($geofenceId): $it") }
    }

    fun unregisterForLocation(ctx: Context, locationName: String) {
        val ids = EventStore.load(ctx)
            .filter { it.locationName == locationName }
            .map { "e${it.id}" }
        if (ids.isEmpty()) return
        LocationServices.getGeofencingClient(ctx)
            .removeGeofences(ids)
            .addOnSuccessListener { Log.d(TAG, "Geofences removed for location '$locationName': $ids") }
            .addOnFailureListener { Log.w(TAG, "Geofence remove failed for location '$locationName': $it") }
    }

    fun reregisterAll(ctx: Context) {
        EventStore.load(ctx)
            .filter { it.locationName != null && !it.completed }
            .forEach { event ->
                val loc = LocationsStore.get(ctx, event.locationName!!) ?: return@forEach
                registerForEvent(ctx, event, loc.lat, loc.lng)
            }
    }
}
