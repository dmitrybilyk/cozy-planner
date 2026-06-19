package com.linkease

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

class ChatReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trainerId  = intent.getStringExtra("trainer_id")  ?: return
        val clientId   = intent.getStringExtra("client_id")   ?: return
        val senderId   = intent.getStringExtra("sender_id")   ?: return
        val notifId    = intent.getIntExtra("notif_id", 0)

        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.CHAT_REPLY_KEY)
            ?.toString()?.trim()
            ?: intent.getStringExtra("quick_reply")
            ?: return

        if (replyText.isBlank()) return

        FirebaseHelper.sendChatMessage(trainerId, clientId, senderId, replyText) { _, _ -> }

        // Dismiss the notification
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notifId)
    }
}
