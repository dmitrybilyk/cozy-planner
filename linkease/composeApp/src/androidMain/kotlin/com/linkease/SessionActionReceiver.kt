package com.linkease

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class SessionActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra("session_action") ?: return
        val notifId = intent.getIntExtra("notif_id", 0)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notifId)

        when (action) {
            "confirm" -> {
                val sessionId = intent.getStringExtra("session_id") ?: return
                val clientFirebaseId = intent.getStringExtra("client_firebase_id") ?: return
                FirebaseHelper.confirmClientSession(clientFirebaseId, sessionId) { ok ->
                    Toast.makeText(context, if (ok) "✅ Заняття підтверджено" else "❌ Помилка", Toast.LENGTH_SHORT).show()
                }
            }
            "reject" -> {
                val sessionId = intent.getStringExtra("session_id") ?: return
                val clientFirebaseId = intent.getStringExtra("client_firebase_id") ?: return
                FirebaseHelper.rejectClientSession(clientFirebaseId, sessionId) { ok ->
                    Toast.makeText(context, if (ok) "❌ Заняття відхилено" else "❌ Помилка", Toast.LENGTH_SHORT).show()
                }
            }
            "confirm_booking" -> {
                val bookingRequestId = intent.getStringExtra("booking_request_id") ?: return
                FirebaseHelper.updateBookingRequestStatus(bookingRequestId, "confirmed") { _ -> }
                Toast.makeText(context, "✅ Запит підтверджено", Toast.LENGTH_SHORT).show()
            }
            "decline_booking" -> {
                val bookingRequestId = intent.getStringExtra("booking_request_id") ?: return
                FirebaseHelper.updateBookingRequestStatus(bookingRequestId, "declined") { _ -> }
                Toast.makeText(context, "✕ Запит відхилено", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
