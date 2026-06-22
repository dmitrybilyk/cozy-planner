package com.reminderwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import com.google.firebase.firestore.ListenerRegistration

class SyncService : Service() {

    companion object {
        private const val CHANNEL_ID         = "sync_bg"
        const val NOTIF_ID                   = 9002
        private const val ACTION_GROUP_SYNC  = "com.reminderwidget.GROUP_SYNC"

        fun start(ctx: Context) {
            try { ctx.startForegroundService(Intent(ctx, SyncService::class.java)) } catch (_: Exception) {}
        }

        fun notifyGroupChanged(ctx: Context) {
            try {
                ctx.startForegroundService(
                    Intent(ctx, SyncService::class.java).apply { action = ACTION_GROUP_SYNC }
                )
            } catch (_: Exception) {}
        }
    }

    private var todoListener:        ListenerRegistration? = null
    private var eventsListener:      ListenerRegistration? = null
    private var groupEventsListener: ListenerRegistration? = null
    private var groupItemsListener:  ListenerRegistration? = null
    private var groupPlacesListener: ListenerRegistration? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotif(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, buildNotif())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Satisfy Android's foreground requirement for every startForegroundService() call,
        // then immediately remove the notification — we don't need it visible.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotif(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, buildNotif())
        }
        @Suppress("DEPRECATION")
        stopForeground(true)

        FirebaseSync.init(this)
        if (intent?.action == ACTION_GROUP_SYNC) {
            detachGroupListeners()
            attachGroupListeners()
        } else {
            if (groupEventsListener == null) attachGroupListeners()
            if (FirebaseSync.isReady() && todoListener == null) attachPersonalListeners()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        todoListener?.remove()
        eventsListener?.remove()
        groupEventsListener?.remove()
        groupItemsListener?.remove()
        groupPlacesListener?.remove()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun attachPersonalListeners() {
        val prefs = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)

        todoListener = FirebaseSync.listenTodo(this) { title, items, places ->
            TodoStore.mergeFromRemote(this, title, items, places)
            sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
        }

        eventsListener = FirebaseSync.listenEvents(this) { added, removedIds ->
            val now = System.currentTimeMillis()
            added.forEach { event ->
                val alreadyLocal = EventStore.load(this).any { it.id == event.id }
                EventStore.addSilent(this, event)
                if (!alreadyLocal && !event.completed && event.startMs > now) {
                    if (prefs.getBoolean(MainActivity.KEY_NOTIFICATIONS_ENABLED, true))
                        NotificationHelper.scheduleAt(this, event.id, event.startMs)
                }
            }
            removedIds.forEach { id ->
                EventStore.removeSilent(this, id)
                NotificationHelper.cancelAlarm(this, id)
            }
            if (added.isNotEmpty() || removedIds.isNotEmpty()) {
                EventsWidget.update(this)
                PersistentNotif.update(this)
                sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
            }
        }
    }

    private fun attachGroupListeners() {
        val groupId = GroupStore.getGroupId(this) ?: return
        val prefs   = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)

        groupEventsListener = FirebaseSync.listenGroupEvents(groupId) { added, removedIds ->
            val now = System.currentTimeMillis()
            added.forEach { event ->
                val alreadyLocal = EventStore.load(this).any { it.id == event.id }
                EventStore.addSilent(this, event)
                if (!alreadyLocal && !event.completed && event.startMs > now) {
                    if (prefs.getBoolean(MainActivity.KEY_NOTIFICATIONS_ENABLED, true))
                        NotificationHelper.scheduleAt(this, event.id, event.startMs)
                }
            }
            removedIds.forEach { id ->
                EventStore.removeSilent(this, id)
                NotificationHelper.cancelAlarm(this, id)
                NotificationHelper.cancelRepeat(this, id)
            }
            if (added.isNotEmpty() || removedIds.isNotEmpty()) {
                EventsWidget.update(this)
                PersistentNotif.update(this)
                sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
            }
        }

        groupItemsListener = FirebaseSync.listenGroupItems(groupId) { added, removedIds ->
            val current = TodoStore.loadGroupItems(this).toMutableList()
            removedIds.forEach { id -> current.removeAll { it.id == id } }
            added.forEach { item -> current.removeAll { it.id == item.id }; current.add(item) }
            TodoStore.saveGroupItems(this, current.sortedBy { it.sortOrder })
            sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
        }

        groupPlacesListener = FirebaseSync.listenGroupPlaces(groupId) { places ->
            TodoStore.saveGroupPlaces(this, places)
            sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
        }
    }

    private fun detachGroupListeners() {
        groupEventsListener?.remove(); groupEventsListener = null
        groupItemsListener?.remove();  groupItemsListener  = null
        groupPlacesListener?.remove(); groupPlacesListener = null
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Фонова синхронізація", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
        )
    }

    private fun buildNotif(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(Icon.createWithResource(this, R.drawable.ic_sync))
            .setColor(0xFF1565C0.toInt())
            .setContentTitle("Cozy Planner")
            .setContentText("Синхронізація активна")
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .build()
}
