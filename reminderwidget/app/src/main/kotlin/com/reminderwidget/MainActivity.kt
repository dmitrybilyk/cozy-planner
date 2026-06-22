package com.reminderwidget

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Notification
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    companion object {
        const val PREFS               = "widget_settings"
        const val KEY_DURATION_MS     = "duration_ms"
        const val KEY_EVENT_COLOR     = "event_color"
        const val KEY_WIDGET_BG       = "widget_bg"
        const val KEY_CALENDAR_EXPORT = "calendar_export"
        const val KEY_SNOOZE1_MIN     = NotificationHelper.KEY_SNOOZE1_MIN
        const val KEY_SNOOZE2_MIN     = NotificationHelper.KEY_SNOOZE2_MIN
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val KEY_MORNING_HOUR    = "morning_hour"
        const val KEY_MORNING_MIN     = "morning_min"
        const val KEY_DAY_HOUR        = "day_hour"
        const val KEY_DAY_MIN         = "day_min"
        const val KEY_EVENING_HOUR    = "evening_hour"
        const val KEY_EVENING_MIN     = "evening_min"
        private const val REQ_CAL_PERM    = 102
        private const val REQ_ACCOUNTS    = 103

        val DURATION_OPTIONS = listOf(
            "1 г"   to 60 * 60_000L,
            "1 д"   to 24 * 60 * 60_000L,
            "7 д"   to 7 * 24 * 60 * 60_000L,
            "100 д" to 100L * 24 * 60 * 60_000L,
        )
        val EVENT_COLORS = listOf(
            0                           to "Авто",
            Color.parseColor("#D50000") to "Tomato",
            Color.parseColor("#F4511E") to "Tangerine",
            Color.parseColor("#F6BF26") to "Banana",
            Color.parseColor("#33B679") to "Sage",
            Color.parseColor("#039BE5") to "Peacock",
            Color.parseColor("#7986CB") to "Lavender",
            Color.parseColor("#8E24AA") to "Grape",
        )
        val WIDGET_BG_COLORS = listOf(
            0xDD000000.toInt() to "Чорний",
            0xDD0D47A1.toInt() to "Синій",
            0xDD1B5E20.toInt() to "Зелений",
            0xDD8B0000.toInt() to "Червоний",
            0xDD4A148C.toInt() to "Фіолетовий",
            0x44000000         to "Напівпрозорий",
            0x00000000         to "Без фону",
        )
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var tabPages: Array<View>
    private lateinit var navBtns: Array<LinearLayout>
    private var currentTab = 0

    private var eventsContainer:    LinearLayout? = null
    private var favoritesContainer: LinearLayout? = null
    private var soundBtn:           Button?       = null
    private var exportSwitch:       Switch?       = null
    private var pinBtn:             Button?       = null
    private var usageBadge:         TextView?     = null

    // ── ToDo tab state ────────────────────────────────────────────────────────
    private var todoContainer:      LinearLayout? = null
    private var whatContainer:      LinearLayout? = null
    private var whereContainer:     LinearLayout? = null
    private var whatScroll:         ScrollView?   = null
    private var whereScroll:        ScrollView?   = null
    private var currentEventsSubTab = 0
    private var eventsSubTabBtns:   Array<TextView> = emptyArray()
    private var eventsAllScroll:    ScrollView? = null
    private var eventsFavScroll:    ScrollView? = null
    private var currentTodoTab      = 0
    private var todoSubTabBtns:     Array<TextView> = emptyArray()
    private var todoGroupPanel:     LinearLayout? = null
    private var todoTitleView:      TextView?     = null
    private var todoFab:            TextView?     = null
    private var lastDraggedId:      Long?         = null
    private var lastDraggedPlace:   String?       = null
    private val RC_TODO_VOICE       = 2001
    private val RC_TODO_EDIT_VOICE  = 2002
    private var pendingEditVoiceResult: String? = null
    private var pendingEditTitleField: android.widget.EditText? = null
    private var todoListener:         com.google.firebase.firestore.ListenerRegistration? = null
    private var eventsListener:       com.google.firebase.firestore.ListenerRegistration? = null
    private var groupEventsListener:  com.google.firebase.firestore.ListenerRegistration? = null
    private var groupItemsListener:   com.google.firebase.firestore.ListenerRegistration? = null
    private var groupPlacesListener:  com.google.firebase.firestore.ListenerRegistration? = null
    private var groupMembersListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var groupTodoItems:     List<TodoStore.Item> = emptyList()
    private var groupPlaces:        List<String> = emptyList()
    private var groupMemberNames:   List<String> = emptyList()
    private var groupStatusView:    TextView? = null
    private var currentTodoMode     = 0   // 0=private, 1=group
    private var modeSwitcherBtns:   Array<TextView> = emptyArray()
    private val RC_LOCATION_MANAGER = 302
    private var groupJoinContainer:      LinearLayout? = null
    private var groupConnectedContainer: LinearLayout? = null

    private val listChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (GroupStore.isInGroup(this@MainActivity)) {
                groupTodoItems = TodoStore.loadGroupItems(this@MainActivity)
                groupPlaces    = TodoStore.loadGroupPlaces(this@MainActivity)
            }
            loadEventsList()
            loadFavoritesList()
            if (currentTab == 1) loadTodoList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Notifications toggle was removed — always on
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, true).apply()
        NotificationHelper.ensureChannel(this)
        NotificationHelper.ensureSilentChannel(this)
        PersistentNotif.ensureChannel(this)
        GeofencingReceiver.ensureChannel(this)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refreshDynamicState()
        ContextCompat.registerReceiver(this, listChangedReceiver,
            IntentFilter(NotificationHelper.ACTION_LIST_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 300)
        // Group sync doesn't need userId — start it unconditionally
        EventStore.purgeOld(this)
        startGroupSync()
        SyncService.start(this)
        // Personal events/todo need userId — try GET_ACCOUNTS path
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED) {
            startPersonalSync()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.GET_ACCOUNTS), REQ_ACCOUNTS)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(listChangedReceiver) } catch (_: Exception) {}
        todoListener?.remove();         todoListener         = null
        eventsListener?.remove();       eventsListener       = null
        groupEventsListener?.remove();  groupEventsListener  = null
        groupItemsListener?.remove();   groupItemsListener   = null
        groupPlacesListener?.remove();  groupPlacesListener  = null
        groupMembersListener?.remove(); groupMembersListener = null
    }

    private fun startPersonalSync() {
        FirebaseSync.init(this)
        if (!FirebaseSync.isReady()) return
        todoListener?.remove()
        todoListener = FirebaseSync.listenTodo(this) { title, items, places ->
            TodoStore.mergeFromRemote(this, title, items, places)
            todoTitleView?.text = title
            if (currentTab == 1 && currentTodoMode == 0) {
                if (currentTodoTab == 0) loadWhatList() else loadWhereList()
            }
        }
        eventsListener?.remove()
        eventsListener = FirebaseSync.listenEvents(this) { added, removedIds ->
            val now = System.currentTimeMillis()
            added.forEach { event ->
                val alreadyLocal = EventStore.load(this).any { it.id == event.id }
                EventStore.addSilent(this, event)
                if (!alreadyLocal && !event.completed && event.startMs > now) {
                    val notifOn = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
                    if (notifOn) NotificationHelper.scheduleAt(this, event.id, event.startMs)
                }
            }
            removedIds.forEach { id ->
                EventStore.removeSilent(this, id)
                NotificationHelper.cancelAlarm(this, id)
            }
            if (added.isNotEmpty() || removedIds.isNotEmpty()) {
                if (currentTab == 0) { loadEventsList(); loadFavoritesList() }
                EventsWidget.update(this)
            }
        }
    }

    private fun startGroupSync() {
        val groupId = GroupStore.getGroupId(this) ?: return
        groupEventsListener?.remove()
        groupEventsListener = FirebaseSync.listenGroupEvents(groupId) { added, removedIds ->
            val now = System.currentTimeMillis()
            added.forEach { event ->
                val alreadyLocal = EventStore.load(this).any { it.id == event.id }
                EventStore.addSilent(this, event)
                if (!alreadyLocal && !event.completed && event.startMs > now) {
                    val notifOn = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
                    if (notifOn) NotificationHelper.scheduleAt(this, event.id, event.startMs)
                }
            }
            removedIds.forEach { id ->
                EventStore.removeSilent(this, id)
                NotificationHelper.cancelAlarm(this, id)
                NotificationHelper.cancelRepeat(this, id)
            }
            if (added.isNotEmpty() || removedIds.isNotEmpty()) {
                if (currentTab == 0) { loadEventsList(); loadFavoritesList() }
                EventsWidget.update(this)
                PersistentNotif.update(this)
            }
        }
        groupItemsListener?.remove()
        groupItemsListener = FirebaseSync.listenGroupItems(groupId) { added, removed ->
            val current = groupTodoItems.toMutableList()
            added.forEach { item -> current.removeAll { it.id == item.id }; current.add(item) }
            removed.forEach { id -> current.removeAll { it.id == id } }
            groupTodoItems = current.sortedBy { it.sortOrder }
            TodoStore.saveGroupItems(this, groupTodoItems)
            if (currentTab == 1 && currentTodoMode == 1) loadTodoList()
        }
        groupPlacesListener?.remove()
        groupPlacesListener = FirebaseSync.listenGroupPlaces(groupId) { places ->
            groupPlaces = places
            TodoStore.saveGroupPlaces(this, places)
            if (currentTab == 1 && currentTodoMode == 1 && currentTodoTab == 1) loadWhereList()
        }
        groupMembersListener?.remove()
        groupMembersListener = FirebaseSync.listenGroupMembers(groupId) { members ->
            groupMemberNames = members.map { it.second }
            updateGroupStatus()
        }
    }

    private fun stopGroupSync() {
        groupEventsListener?.remove();  groupEventsListener  = null
        groupItemsListener?.remove();   groupItemsListener   = null
        groupPlacesListener?.remove();  groupPlacesListener  = null
        groupMembersListener?.remove(); groupMembersListener = null
        groupTodoItems   = emptyList()
        groupPlaces      = emptyList()
        groupMemberNames = emptyList()
        TodoStore.saveGroupItems(this, emptyList())
        TodoStore.saveGroupPlaces(this, emptyList())
    }

    private fun updateGroupStatus() {
        val sv = groupStatusView ?: return
        val inGroup    = GroupStore.isInGroup(this)
        val groupEmail = GroupStore.getGroupEmail(this)
        val myName     = GroupStore.getMyName(this)
        if (inGroup && groupEmail != null) {
            val members = if (groupMemberNames.isNotEmpty()) "\nУчасники: ${groupMemberNames.joinToString()}" else ""
            sv.text = "✅ Група: $groupEmail (Ви: $myName)$members"
        } else {
            sv.text = ""
        }
        groupJoinContainer?.visibility      = if (inGroup) android.view.View.GONE else android.view.View.VISIBLE
        groupConnectedContainer?.visibility = if (inGroup) android.view.View.VISIBLE else android.view.View.GONE
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_LOCATION_MANAGER && resultCode == RESULT_OK) {
            loadEventsList(); loadFavoritesList()
            return
        }

        if (resultCode != RESULT_OK) return
        val raw = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.trim()
        if (raw.isNullOrBlank()) return

        if (requestCode == RC_TODO_VOICE) {
            val text = raw.replaceFirstChar { it.uppercase() }
            if (currentTodoTab == 1) addTodoPlace(text) else addTodoItem(text)
        } else if (requestCode == RC_TODO_EDIT_VOICE) {
            pendingEditTitleField?.setText(raw.replaceFirstChar { it.uppercase() })
            pendingEditTitleField = null
        }
    }

    private fun refreshDynamicState() {
        loadEventsList()
        loadFavoritesList()
        if (currentTab == 1) loadTodoList()
        updateSoundBtn()
        updateExportSwitch()
        updatePinBtn()
        updateUsageBadge()
        refreshMicWidget()
        PersistentNotif.update(this)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ROOT LAYOUT
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D0D0D.toInt())
        }
        root.addView(buildAppBar())

        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        tabPages = arrayOf(
            buildEventsPage(),
            buildTodoPage(),
            buildSettingsPage(),
        )
        tabPages.forEach { frame.addView(it) }
        root.addView(frame)
        root.addView(buildBottomNav())
        showTab(0)
        return root
    }

    private fun buildAppBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(56))
            setBackgroundColor(0xFF111111.toInt())
            setPadding(dp(16), 0, dp(16), 0)
        }
        bar.addView(TextView(this).apply {
            text = "🎙  Нагадування"; textSize = 17f; setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        usageBadge = TextView(this).apply {
            textSize = 11f; setTextColor(0xFFFF5722.toInt())
            setPadding(dp(9), dp(4), dp(9), dp(4))
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0x22FF5722) }
        }
        bar.addView(usageBadge!!)
        return bar
    }

    private fun buildBottomNav(): View {
        val navData = listOf("📋" to "Події", "✅" to "Список", "⚙️" to "Налаш.")
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(56))
            setBackgroundColor(0xFF111111.toInt())
        }
        navBtns = Array(3) { i ->
            val (icon, label) = navData[i]
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
                setOnClickListener { showTab(i) }
            }
            col.addView(TextView(this).apply {
                text = icon; textSize = 20f; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            })
            col.addView(TextView(this).apply {
                text = label; textSize = 9f; gravity = Gravity.CENTER
                setTextColor(0xFFAAAAAA.toInt())
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            })
            navBar.addView(col); col
        }
        return navBar
    }

    private fun showTab(idx: Int) {
        currentTab = idx
        tabPages.forEachIndexed { i, p -> p.visibility = if (i == idx) View.VISIBLE else View.GONE }
        navBtns.forEachIndexed { i, col ->
            val active = i == idx
            col.setBackgroundColor(if (active) 0xFF1A1A1A.toInt() else 0xFF111111.toInt())
            (col.getChildAt(0) as? TextView)?.setTextColor(if (active) 0xFFFF5722.toInt() else 0xFF666666.toInt())
            (col.getChildAt(1) as? TextView)?.setTextColor(if (active) 0xFFFF5722.toInt() else 0xFF555555.toInt())
        }
        when (idx) {
            0 -> { loadEventsList(); loadFavoritesList() }
            1 -> loadTodoList()
            2 -> updateSoundBtn()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TAB 0 — EVENTS
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildEventsPage(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        val subTabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF111111.toInt())
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        val allBtn = TextView(this).apply {
            text = "📋 Всі"; textSize = 13f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).also { it.marginEnd = dp(4) }
        }
        val favBtn = TextView(this).apply {
            text = "❤️ Обрані"; textSize = 13f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).also { it.marginStart = dp(4) }
        }
        eventsSubTabBtns = arrayOf(allBtn, favBtn)
        subTabBar.addView(allBtn); subTabBar.addView(favBtn)
        page.addView(subTabBar)

        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }

        eventsAllScroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        val allInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(24))
        }
        eventsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        allInner.addView(eventsContainer); eventsAllScroll!!.addView(allInner)

        eventsFavScroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT); visibility = View.GONE
        }
        val favInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(24))
        }
        favoritesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        favInner.addView(favoritesContainer); eventsFavScroll!!.addView(favInner)

        contentFrame.addView(eventsAllScroll); contentFrame.addView(eventsFavScroll)
        page.addView(contentFrame)

        fun applyEventsSubTab(idx: Int) {
            currentEventsSubTab = idx
            eventsAllScroll?.visibility  = if (idx == 0) View.VISIBLE else View.GONE
            eventsFavScroll?.visibility  = if (idx == 1) View.VISIBLE else View.GONE
            eventsSubTabBtns.forEachIndexed { i, btn ->
                btn.background = roundedBg(if (i == idx) 0xFFFF5722.toInt() else 0xFF252525.toInt(), 8f)
                btn.setTextColor(if (i == idx) Color.WHITE else 0xFF666666.toInt())
            }
            if (idx == 0) loadEventsList() else loadFavoritesList()
        }
        allBtn.setOnClickListener { applyEventsSubTab(0) }
        favBtn.setOnClickListener { applyEventsSubTab(1) }
        applyEventsSubTab(0)
        return page
    }

    private fun loadEventsList() {
        populateContainer(eventsContainer ?: return, EventStore.load(this), showDeleteAll = true)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TAB 1 — FAVORITES
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildFavoritesPage(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(24))
        }
        favoritesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(favoritesContainer)
        scroll.addView(inner)
        page.addView(scroll)
        return page
    }

    private fun loadFavoritesList() {
        populateContainer(
            favoritesContainer ?: return,
            EventStore.load(this).filter { it.favorite },
            showDeleteAll = false,
            emptyText = "Натисніть ❤️ на нагадуванні\nщоб додати до обраних"
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SHARED EVENT LIST RENDERING
    // ─────────────────────────────────────────────────────────────────────────

    private fun populateContainer(
        c: LinearLayout,
        events: List<EventStore.AppEvent>,
        showDeleteAll: Boolean,
        emptyText: String = "Поки немає нагадувань.\n\nНатисніть мікрофон на віджеті\nабо на іконці застосунку.",
    ) {
        c.removeAllViews()
        if (events.isEmpty()) { c.addView(buildEmptyState(emptyText)); return }

        // "Delete all" row
        if (showDeleteAll) {
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    .also { it.bottomMargin = dp(8) }
            }
            headerRow.addView(TextView(this).apply {
                text = "${events.size} нагадувань"; textSize = 11f; setTextColor(0xFF555555.toInt())
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            headerRow.addView(TextView(this).apply {
                text = "🗑 Видалити всі"; textSize = 11f; setTextColor(0xFF884444.toInt())
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("Видалити всі?")
                        .setMessage("Обране (❤️) буде збережено.")
                        .setPositiveButton("Видалити") { _, _ ->
                            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                            EventStore.load(this@MainActivity).filter { !it.favorite }.forEach { e ->
                                nm.cancel(NotificationHelper.notifId(e.id))
                                NotificationHelper.cancelAlarm(this@MainActivity, e.id)
                                NotificationHelper.cancelRepeat(this@MainActivity, e.id)
                                NotificationHelper.setRepeating(this@MainActivity, e.id, false)
                            }
                            EventStore.deleteNonFavorites(this@MainActivity)
                            EventsWidget.update(this@MainActivity)
                            loadEventsList()
                            loadFavoritesList()
                        }
                        .setNegativeButton("Скасувати", null)
                        .show()
                }
            })
            c.addView(headerRow)
        }

        val nm      = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val fullFmt = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault())
        val premium = VoiceActivity.isUnlocked(this)

        val now       = System.currentTimeMillis()
        val upcoming  = events.filter { !it.completed && it.startMs >= now }.sortedBy { it.startMs }
        val overdue   = events.filter { !it.completed && it.startMs < now  }.sortedByDescending { it.startMs }
        val completed = events.filter {  it.completed }.sortedByDescending { it.startMs }

        var lastDay = ""
        upcoming.forEach { event ->
            val dayLabel = dayLabel(event.startMs)
            if (dayLabel != lastDay) { c.addView(buildDayHeader(dayLabel)); lastDay = dayLabel }
            val activeNotif = nm.activeNotifications.find { it.id == NotificationHelper.notifId(event.id) }
            val isActive    = activeNotif != null
            val isOngoing   = (activeNotif?.notification?.flags ?: 0) and Notification.FLAG_ONGOING_EVENT != 0
            c.addView(buildEventRow(event, isActive, isOngoing, event.calendarEventId != -1L, premium, timeFmt, false, isPast = false))
        }

        if (overdue.isNotEmpty()) {
            c.addView(buildDayHeader("⚠️ Прострочені"))
            overdue.forEach { event ->
                val activeNotif = nm.activeNotifications.find { it.id == NotificationHelper.notifId(event.id) }
                val isActive    = activeNotif != null
                val isOngoing   = (activeNotif?.notification?.flags ?: 0) and Notification.FLAG_ONGOING_EVENT != 0
                c.addView(buildEventRow(event, isActive, isOngoing, event.calendarEventId != -1L, premium, fullFmt, true, isPast = true))
            }
        }

        if (completed.isNotEmpty()) {
            c.addView(buildDayHeader("✅ Виконані"))
            completed.forEach { event ->
                c.addView(buildEventRow(event, false, false, event.calendarEventId != -1L, premium, fullFmt, true, isPast = false))
            }
        }
    }

    private fun dayLabel(startMs: Long): String {
        val eCal   = Calendar.getInstance().apply { timeInMillis = startMs }
        val today  = Calendar.getInstance()
        val tmrw   = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val sameYr = eCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        return when {
            sameYr && eCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Сьогодні"
            sameYr && eCal.get(Calendar.DAY_OF_YEAR) == tmrw.get(Calendar.DAY_OF_YEAR)  -> "Завтра"
            else -> java.text.SimpleDateFormat("EEEE, dd.MM", java.util.Locale("uk"))
                        .format(java.util.Date(startMs))
                        .replaceFirstChar { it.uppercase() }
        }
    }

    private fun buildDayHeader(label: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .also { it.topMargin = dp(10); it.bottomMargin = dp(6) }
            addView(View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(1)).also { it.marginEnd = dp(8) }
                setBackgroundColor(0xFF333333.toInt())
            })
            addView(TextView(this@MainActivity).apply {
                text = label; textSize = 11f; setTextColor(0xFF888888.toInt())
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(1), 1f).also { it.marginStart = dp(8) }
                setBackgroundColor(0xFF333333.toInt())
            })
        }

    private fun buildEmptyState(msg: String): View =
        TextView(this).apply {
            text = msg; textSize = 13f; setTextColor(0xFF555555.toInt()); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .also { it.topMargin = dp(60); it.bottomMargin = dp(20) }
        }

    private fun buildEventRow(
        event: EventStore.AppEvent,
        isActive: Boolean,
        isOngoing: Boolean,
        exported: Boolean,
        premium: Boolean,
        timeFmt: java.text.SimpleDateFormat,
        showFullDateInRow: Boolean,
        isPast: Boolean = false,
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .also { it.bottomMargin = dp(6) }
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(when {
                    event.completed -> 0xFF161616.toInt()
                    isPast          -> 0xFF1A1710.toInt()
                    event.isGroup   -> 0xFF0E2214.toInt()
                    else            -> 0xFF1C1C1E.toInt()
                })
                when {
                    isPast                            -> setStroke(dp(1), 0x55FFA000.toInt())
                    event.isGroup && !event.completed -> setStroke(dp(2), 0xFF2E7D32.toInt())
                    isOngoing                         -> setStroke(dp(1), 0x55FF5722.toInt())
                }
            }
            setPadding(dp(12), dp(10), dp(8), dp(10))
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }

        // Status dot
        val dotColor = when {
            event.completed -> 0xFF26A69A.toInt()
            isOngoing       -> 0xFFFF5722.toInt()
            isPast          -> 0xFFFFA000.toInt()
            isActive        -> 0xFF4CAF50.toInt()
            else            -> 0xFF444444.toInt()
        }
        row.addView(View(this).apply {
            val s = dp(8)
            layoutParams = LinearLayout.LayoutParams(s, s).also { it.marginEnd = dp(10) }
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(dotColor) }
        })

        // Text column
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(this).apply {
            text = if (event.isGroup && !event.completed) "👥 ${event.title}" else event.title
            textSize = 14f
            if (event.completed) {
                setTextColor(0xFF666666.toInt())
                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                setTextColor(if (event.isGroup) 0xFF81C784.toInt() else Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.bottomMargin = dp(2) }
        })
        val timeStr = buildString {
            if (event.rrule != null) append("🔁 ")
            if (event.completed && !showFullDateInRow) append("✅ ")
            append(timeFmt.format(java.util.Date(event.startMs)))
            if (isOngoing) append("  📌")
        }
        textCol.addView(TextView(this).apply {
            text = timeStr; textSize = 11f; setTextColor(0xFF666666.toInt())
        })
        if (event.locationName != null) {
            textCol.addView(TextView(this).apply {
                text = "📍 Прив'язано до ${event.locationName}"
                textSize = 10f
                setTextColor(0xFF4FC3F7.toInt())
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            })
        }
        row.addView(textCol)

        val btnSz = dp(38)

        // Pin to status bar button
        if (premium) {
            row.addView(iconBtn("📌", btnSz,
                if (isOngoing) 0xFF2A1500.toInt() else 0xFF242424.toInt(),
                tint = if (isOngoing) 0xFFFF5722.toInt() else 0xFF555555.toInt()) {
                if (isActive || isOngoing) {
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                        .cancel(NotificationHelper.notifId(event.id))
                    sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                    EventsWidget.update(this)
                } else {
                    NotificationHelper.post(this, event, ongoing = true, silent = true, pinned = true)
                    Toast.makeText(this, "📌 Закріплено в статус-барі", Toast.LENGTH_SHORT).show()
                    EventsWidget.update(this)
                }
                loadEventsList(); loadFavoritesList()
            })
        }

        // 📅 calendar export — premium only
        if (premium) {
            row.addView(iconBtn("📅", btnSz, if (exported) 0xFF1B2B0E.toInt() else 0xFF242424.toInt(),
                tint = if (exported) 0xFF66BB6A.toInt() else 0xFFFFFFFF.toInt()) {
                exportEventToCalendar(event)
            })
        }

        // ❤️ favorite toggle — always
        row.addView(iconBtn(if (event.favorite) "❤️" else "🤍", btnSz,
            if (event.favorite) 0xFF2A1020.toInt() else 0xFF242424.toInt()) {
            EventStore.markFavorite(this, event.id, !event.favorite)
            loadEventsList(); loadFavoritesList()
        })

        // 👥 group toggle — only when in a group
        if (GroupStore.isInGroup(this)) {
            row.addView(iconBtn("👥", btnSz,
                if (event.isGroup) 0xFF0D2A0D.toInt() else 0xFF242424.toInt(),
                tint = if (event.isGroup) 0xFF66BB6A.toInt() else 0xFF333333.toInt()) {
                val nowGroup = !event.isGroup
                EventStore.markGroup(this, event.id, nowGroup)
                if (nowGroup) {
                    val groupId = GroupStore.getGroupId(this)!!
                    val updated = EventStore.load(this).find { it.id == event.id } ?: return@iconBtn
                    FirebaseSync.pushGroupEvent(groupId, updated)
                    Toast.makeText(this, "👥 Подія поширена до групи", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "👥 Групову мітку знято", Toast.LENGTH_SHORT).show()
                }
                loadEventsList(); loadFavoritesList()
            })
        }

        // 📍 location — only on non-completed events where no specific time was recognized
        if (!event.completed && !event.hasTime) {
            val hasLoc = event.locationName != null
            row.addView(iconBtn("📍", btnSz,
                if (hasLoc) 0xFF0D2040.toInt() else 0xFF242424.toInt(),
                tint = if (hasLoc) 0xFF1565C0.toInt() else 0xFF555555.toInt()) {
                showLocationPickerForEvent(event)
            })
        }

        // 🗑 delete — always
        row.addView(iconBtn("🗑", btnSz, 0xFF242424.toInt()) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NotificationHelper.notifId(event.id))
            NotificationHelper.cancelAlarm(this, event.id)
            NotificationHelper.cancelRepeat(this, event.id)
            NotificationHelper.setRepeating(this, event.id, false)
            EventStore.remove(this, event.id)
            EventsWidget.update(this)
            loadEventsList(); loadFavoritesList()
        })

        card.addView(row)

        if (!event.completed) {
            card.setOnClickListener { showEditEventDialog(event) }
        }

        return card
    }

    private fun showEditEventDialog(event: EventStore.AppEvent) {
        val cal = Calendar.getInstance().apply { timeInMillis = event.startMs }
        var pickedMs = event.startMs

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }

        val titleEdit = EditText(this).apply {
            setText(event.title); textSize = 15f; setTextColor(Color.WHITE)
            setHintTextColor(0xFF555555.toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat(); setColor(0xFF252525.toInt())
                setStroke(dp(1), 0xFF333333.toInt())
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .also { it.bottomMargin = dp(14) }
        }
        dialogLayout.addView(titleEdit)

        val dateFmt = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        val timeFmt = java.text.SimpleDateFormat("HH:mm",      java.util.Locale.getDefault())

        val dateLabel = TextView(this).apply {
            text = "📅  ${dateFmt.format(java.util.Date(pickedMs))}"
            textSize = 13f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat(); setColor(0xFF252525.toInt())
                setStroke(dp(1), 0xFF333333.toInt())
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .also { it.bottomMargin = dp(8) }
        }

        val timeLabel = TextView(this).apply {
            text = "🕐  ${timeFmt.format(java.util.Date(pickedMs))}"
            textSize = 13f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat(); setColor(0xFF252525.toInt())
                setStroke(dp(1), 0xFF333333.toInt())
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .also { it.bottomMargin = dp(16) }
        }

        fun pickTime() {
            val c = Calendar.getInstance().apply { timeInMillis = pickedMs }
            TimePickerDialog(this, { _, h, m ->
                val nc = Calendar.getInstance().apply { timeInMillis = pickedMs }
                nc.set(Calendar.HOUR_OF_DAY, h); nc.set(Calendar.MINUTE, m); nc.set(Calendar.SECOND, 0)
                pickedMs = nc.timeInMillis
                timeLabel.text = "🕐  ${timeFmt.format(java.util.Date(pickedMs))}"
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
        }

        fun pickDate() {
            val c = Calendar.getInstance().apply { timeInMillis = pickedMs }
            DatePickerDialog(this, { _, y, m, d ->
                val nc = Calendar.getInstance().apply { timeInMillis = pickedMs }
                nc.set(Calendar.YEAR, y); nc.set(Calendar.MONTH, m); nc.set(Calendar.DAY_OF_MONTH, d)
                pickedMs = nc.timeInMillis
                dateLabel.text = "📅  ${dateFmt.format(java.util.Date(pickedMs))}"
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }

        dateLabel.setOnClickListener { pickDate() }
        timeLabel.setOnClickListener { pickTime() }

        dialogLayout.addView(dateLabel)
        dialogLayout.addView(timeLabel)

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Редагувати нагадування")
            .setView(dialogLayout)
            .setPositiveButton("Зберегти") { _, _ ->
                val newTitle = titleEdit.text.toString().trim().takeIf { it.isNotEmpty() } ?: event.title
                val timeChanged  = pickedMs != event.startMs
                val titleChanged = newTitle != event.title
                if (!timeChanged && !titleChanged) return@setPositiveButton

                val updated = event.copy(title = newTitle, startMs = pickedMs)
                EventStore.update(this, updated)

                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(NotificationHelper.notifId(event.id))
                NotificationHelper.cancelAlarm(this, event.id)

                val now = System.currentTimeMillis()
                if (prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)) {
                    if (pickedMs <= now + 5_000L) {
                        NotificationHelper.post(this, updated)
                    } else {
                        NotificationHelper.scheduleAt(this, updated.id, pickedMs)
                    }
                }

                EventsWidget.update(this)
                sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
                loadEventsList(); loadFavoritesList()
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun iconBtn(
        text: String, size: Int, bgColor: Int,
        tint: Int = 0xFFFFFFFF.toInt(),
        onClick: () -> Unit
    ) = Button(this).apply {
        this.text = text; textSize = 15f; setTextColor(tint); setPadding(0, 0, 0, 0)
        setBackgroundColor(bgColor)
        layoutParams = LinearLayout.LayoutParams(size, size).also { it.marginStart = dp(3) }
        setOnClickListener { onClick() }
    }

    private fun exportEventToCalendar(event: EventStore.AppEvent) {
        if (!hasCalendarPerms()) { ensureCalendarPerms(); return }
        val calId = CalendarHelper.createReminder(this, event.title, event.startMs, event.durationMs,
            prefs.getInt(KEY_EVENT_COLOR, 0), event.rrule)
        if (calId != -1L) {
            EventStore.updateCalendarId(this, event.id, calId)
            Toast.makeText(this, "📅 Додано до Google Календаря", Toast.LENGTH_SHORT).show()
            loadEventsList(); loadFavoritesList()
        } else {
            Toast.makeText(this, "Не вдалося додати до Календаря", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLocationPickerForEvent(event: EventStore.AppEvent) {
        if (event.locationName != null) {
            android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("📍 ${event.locationName}")
                .setMessage("Нагадування спрацює при наближенні на ${GeofenceManager.RADIUS_METERS.toInt()}м.")
                .setPositiveButton("Видалити") { _, _ ->
                    EventStore.setLocation(this, event.id, null)
                    loadEventsList(); loadFavoritesList()
                }
                .setNegativeButton("Скасувати", null).show()
            return
        }
        val locations = LocationsStore.load(this)
        if (locations.isEmpty()) {
            android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Немає збережених локацій")
                .setMessage("Спочатку додайте локацію в менеджері локацій.")
                .setPositiveButton("Відкрити Менеджер") { _, _ ->
                    startActivityForResult(Intent(this, LocationManagerActivity::class.java), RC_LOCATION_MANAGER)
                }
                .setNegativeButton("Скасувати", null).show()
            return
        }
        val names = locations.map { it.name }.toTypedArray()
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("📍 Локація для «${event.title}»")
            .setItems(names) { _, which ->
                EventStore.setLocation(this, event.id, names[which])
                loadEventsList(); loadFavoritesList()
            }.show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TAB 2 — TODO  (Що / Де sub-tabs)
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildTodoPage(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D0D0D.toInt())
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        // ── Header ───────────────────────────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF111111.toInt())
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        todoTitleView = TextView(this).apply {
            text = TodoStore.loadTitle(this@MainActivity)
            textSize = 15f; setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            setOnClickListener { pickTodoDate() }
        }
        header.addView(todoTitleView!!)

        val shareBtn = TextView(this).apply {
            text = "📤"; textSize = 22f; setPadding(dp(10), dp(4), dp(2), dp(4))
            setOnClickListener { shareTodoBitmap() }
        }
        val deleteAllBtn = TextView(this).apply {
            text = "🗑"; textSize = 22f; setPadding(dp(8), dp(4), dp(2), dp(4))
            setOnClickListener { confirmDeleteAllTodo() }
        }
        header.addView(shareBtn)
        header.addView(deleteAllBtn)
        page.addView(header)

        // ── Mode switcher: Моє / Групове ──────────────────────────────────────
        val modeSwitcher = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF0A0A0A.toInt())
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        val privateBtn = TextView(this).apply {
            text = "🔒 Моє"; textSize = 12f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(28), 1f).also { it.marginEnd = dp(4) }
        }
        val groupModeBtn = TextView(this).apply {
            text = "👥 Групове"; textSize = 12f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(28), 1f).also { it.marginStart = dp(4) }
        }
        modeSwitcherBtns = arrayOf(privateBtn, groupModeBtn)
        modeSwitcher.addView(privateBtn); modeSwitcher.addView(groupModeBtn)
        page.addView(modeSwitcher)

        // ── Sub-tabs: Що / Де ─────────────────────────────────────────────────
        val subTabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF111111.toInt())
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        val whatBtn = TextView(this).apply {
            text = "📝 Що"; textSize = 13f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).also { it.marginEnd = dp(4) }
        }
        val whereBtn = TextView(this).apply {
            text = "📍 Де"; textSize = 13f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).also { it.marginStart = dp(4) }
        }
        todoSubTabBtns = arrayOf(whatBtn, whereBtn)
        subTabBar.addView(whatBtn); subTabBar.addView(whereBtn)
        page.addView(subTabBar)

        // ── Content area: two scroll views ────────────────────────────────────
        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }

        whatScroll = ScrollView(this).apply { layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT) }
        whatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(80))
        }
        whatScroll!!.addView(whatContainer)

        whereScroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT); visibility = View.GONE
        }
        whereContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(80))
        }
        whereScroll!!.addView(whereContainer)

        contentFrame.addView(whatScroll); contentFrame.addView(whereScroll)
        page.addView(contentFrame)

        // ── Group management panel (visible only in Групове mode) ─────────────
        todoGroupPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A1A0A.toInt())
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            visibility = View.GONE
        }
        groupStatusView = TextView(this).apply {
            textSize = 11f; setTextColor(0xFF66BB6A.toInt())
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        todoGroupPanel!!.addView(groupStatusView!!)

        val gEmailInput = EditText(this).apply {
            hint = "Email власника групи"; textSize = 12f; setTextColor(Color.WHITE); setHintTextColor(0xFF555555.toInt())
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0xFF1A2A1A.toInt()); setStroke(dp(1), 0xFF2A4A2A.toInt()) }
            setPadding(dp(10), dp(8), dp(10), dp(8))
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(6); it.bottomMargin = dp(4) }
        }
        val gNameInput = EditText(this).apply {
            hint = "Ваше ім'я в групі"; textSize = 12f; setTextColor(Color.WHITE); setHintTextColor(0xFF555555.toInt())
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0xFF1A2A1A.toInt()); setStroke(dp(1), 0xFF2A4A2A.toInt()) }
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.bottomMargin = dp(6) }
        }
        val gConnectBtn = Button(this).apply {
            text = "Підключитися"; textSize = 12f; setTextColor(Color.WHITE)
            background = roundedBg(0xFF1B5E20.toInt(), 8f)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(40))
            setOnClickListener {
                val email = gEmailInput.text.toString().trim()
                val name  = gNameInput.text.toString().trim()
                if (email.isBlank() || name.isBlank()) { Toast.makeText(this@MainActivity, "Введіть email і ім'я", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                val oldGroupId = GroupStore.getGroupId(this@MainActivity)
                if (oldGroupId != null) { FirebaseSync.leaveGroup(this@MainActivity, oldGroupId); stopGroupSync() }
                GroupStore.join(this@MainActivity, email, name)
                val gid = GroupStore.getGroupId(this@MainActivity)!!
                FirebaseSync.joinGroup(this@MainActivity, gid, name)
                SyncService.notifyGroupChanged(this@MainActivity)
                startGroupSync()
                updateGroupStatus()
                Toast.makeText(this@MainActivity, "✅ Підключено до групи $email", Toast.LENGTH_SHORT).show()
            }
        }
        groupJoinContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(gEmailInput); addView(gNameInput); addView(gConnectBtn)
        }
        todoGroupPanel!!.addView(groupJoinContainer!!)

        val gDisconnectBtn = Button(this).apply {
            text = "Відключитися від групи"; textSize = 12f; setTextColor(Color.WHITE)
            background = roundedBg(0xFF4A1010.toInt(), 8f)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(40)).also { it.topMargin = dp(6) }
            setOnClickListener {
                val gid = GroupStore.getGroupId(this@MainActivity)
                if (gid != null) {
                    FirebaseSync.leaveGroup(this@MainActivity, gid)
                    stopGroupSync()
                    GroupStore.leave(this@MainActivity)
                    groupTodoItems = emptyList()
                    SyncService.notifyGroupChanged(this@MainActivity)
                    if (currentTab == 1) loadWhatList()
                }
                updateGroupStatus()
                Toast.makeText(this@MainActivity, "Відключено від групи", Toast.LENGTH_SHORT).show()
            }
        }
        groupConnectedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; addView(gDisconnectBtn)
        }
        todoGroupPanel!!.addView(groupConnectedContainer!!)
        page.addView(todoGroupPanel!!)
        updateGroupStatus()

        // ── Bottom bar (mic FAB) ───────────────────────────────────────────────
        val bottomFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(72))
            setBackgroundColor(0xFF0D0D0D.toInt())
        }
        todoFab = TextView(this).apply {
            text = "🎤"; textSize = 30f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFFF5722.toInt()) }
            val s = dp(56)
            layoutParams = FrameLayout.LayoutParams(s, s).also { it.gravity = Gravity.CENTER }
            setOnClickListener { startTodoVoice() }
        }
        bottomFrame.addView(todoFab)
        page.addView(bottomFrame)

        fun applySubTab(idx: Int) {
            currentTodoTab = idx
            whatScroll?.visibility  = if (idx == 0) View.VISIBLE else View.GONE
            whereScroll?.visibility = if (idx == 1) View.VISIBLE else View.GONE
            todoFab?.visibility   = View.VISIBLE
            todoSubTabBtns.forEachIndexed { i, btn ->
                btn.background = roundedBg(if (i == idx) 0xFFFF5722.toInt() else 0xFF252525.toInt(), 8f)
                btn.setTextColor(if (i == idx) Color.WHITE else 0xFF666666.toInt())
            }
            if (idx == 0) loadWhatList() else loadWhereList()
        }

        fun applyMode(idx: Int) {
            currentTodoMode = idx
            modeSwitcherBtns.forEachIndexed { i, btn ->
                btn.background = roundedBg(if (i == idx) 0xFF1565C0.toInt() else 0xFF1C1C1E.toInt(), 6f)
                btn.setTextColor(if (i == idx) Color.WHITE else 0xFF666666.toInt())
            }
            todoGroupPanel?.visibility = if (idx == 1) View.VISIBLE else View.GONE
            loadTodoList()
        }
        privateBtn.setOnClickListener   { applyMode(0) }
        groupModeBtn.setOnClickListener { applyMode(1) }
        applyMode(currentTodoMode)

        whatBtn.setOnClickListener  { applySubTab(0) }
        whereBtn.setOnClickListener { applySubTab(1) }
        applySubTab(0)
        return page
    }

    private fun loadTodoList() { if (currentTodoTab == 0) loadWhatList() else loadWhereList() }

    // ── Mode-aware data helpers ────────────────────────────────────────────────

    private fun isGroupMode() = currentTodoMode == 1 && GroupStore.isInGroup(this)

    private fun todoItems()  = if (isGroupMode()) groupTodoItems  else TodoStore.load(this)
    private fun todoPlaces() = if (isGroupMode()) groupPlaces     else TodoStore.loadPlaces(this)

    private fun groupId() = GroupStore.getGroupId(this)

    private fun addTodoItem(text: String) {
        if (isGroupMode()) {
            val sortOrder = (groupTodoItems.maxOfOrNull { it.sortOrder } ?: 0L) + 1000L
            val item = TodoStore.Item(System.currentTimeMillis(), text.trim(), sortOrder = sortOrder)
            groupTodoItems = groupTodoItems + item
            FirebaseSync.pushGroupItem(groupId()!!, item)
        } else {
            TodoStore.add(this, text)
        }
        loadWhatList()
    }

    private fun removeTodoItem(id: Long) {
        if (isGroupMode()) {
            groupTodoItems = groupTodoItems.filter { it.id != id }
            FirebaseSync.deleteGroupItem(groupId()!!, id)
        } else {
            TodoStore.remove(this, id)
        }
        loadWhatList()
    }

    private fun toggleTodoItemDone(id: Long) {
        if (isGroupMode()) {
            val updated = groupTodoItems.find { it.id == id }?.let { it.copy(done = !it.done) } ?: return
            groupTodoItems = groupTodoItems.map { if (it.id == id) updated else it }
            FirebaseSync.pushGroupItem(groupId()!!, updated)
        } else {
            val item = TodoStore.load(this).find { it.id == id } ?: return
            TodoStore.markDone(this, id, !item.done)
        }
        loadWhatList()
    }

    private fun reorderTodoItems(items: List<TodoStore.Item>) {
        if (isGroupMode()) {
            val gid = groupId()!!
            val reordered = items.mapIndexed { i, item -> item.copy(sortOrder = (i + 1).toLong() * 1000L) }
            groupTodoItems = reordered
            reordered.forEach { FirebaseSync.pushGroupItem(gid, it) }
        } else {
            TodoStore.reorder(this, items)
        }
    }

    private fun assignTodoPlace(itemId: Long, placeName: String?) {
        if (isGroupMode()) {
            val updated = groupTodoItems.find { it.id == itemId }?.copy(placeName = placeName) ?: return
            groupTodoItems = groupTodoItems.map { if (it.id == itemId) updated else it }
            FirebaseSync.pushGroupItem(groupId()!!, updated)
        } else {
            TodoStore.assignPlace(this, itemId, placeName)
        }
        loadWhatList()
    }

    private fun addTodoPlace(name: String) {
        val p = name.trim().replaceFirstChar { it.uppercase() }
        if (p.isBlank()) return
        if (isGroupMode()) {
            if (!groupPlaces.contains(p)) {
                groupPlaces = groupPlaces + p
                FirebaseSync.pushGroupPlaces(groupId()!!, groupPlaces)
            }
        } else {
            TodoStore.addPlace(this, p)
        }
        loadWhereList()
    }

    private fun removeTodoPlace(name: String) {
        if (isGroupMode()) {
            val gid = groupId()!!
            groupPlaces = groupPlaces.filter { it != name }
            FirebaseSync.pushGroupPlaces(gid, groupPlaces)
            val affected = groupTodoItems.filter { it.placeName == name }.map { it.copy(placeName = null) }
            groupTodoItems = groupTodoItems.map { if (it.placeName == name) it.copy(placeName = null) else it }
            affected.forEach { FirebaseSync.pushGroupItem(gid, it) }
        } else {
            TodoStore.removePlace(this, name)
        }
        loadTodoList()
    }

    private fun renameTodoPlace(old: String, new: String) {
        if (isGroupMode()) {
            val gid = groupId()!!
            groupPlaces = groupPlaces.map { if (it == old) new else it }
            FirebaseSync.pushGroupPlaces(gid, groupPlaces)
            val affected = groupTodoItems.filter { it.placeName == old }.map { it.copy(placeName = new) }
            groupTodoItems = groupTodoItems.map { if (it.placeName == old) it.copy(placeName = new) else it }
            affected.forEach { FirebaseSync.pushGroupItem(gid, it) }
        } else {
            TodoStore.renamePlace(this, old, new)
        }
        loadTodoList()
    }

    private fun reorderTodoPlaces(places: List<String>) {
        if (isGroupMode()) {
            groupPlaces = places
            FirebaseSync.pushGroupPlaces(groupId()!!, places)
        } else {
            TodoStore.reorderPlaces(this, places)
        }
        loadWhereList()
    }

    // ── Що tab: items grouped by place ────────────────────────────────────────

    private fun loadWhatList() {
        val container = whatContainer ?: return
        container.removeAllViews()
        val items  = todoItems()
        val places = todoPlaces()
        if (items.isEmpty()) {
            val hint = if (isGroupMode()) "Натисніть 🎤 щоб додати спільний пункт" else "Натисніть 🎤 щоб додати пункт"
            container.addView(emptyView(hint)); return
        }

        val byPlace = items.groupBy { it.placeName }
        val unassigned = byPlace[null] ?: emptyList()
        var num = 1

        if (unassigned.isNotEmpty() || places.isEmpty()) {
            container.addView(buildGroupHeader("Загальне", null))
            unassigned.forEach { container.addView(buildWhatItemRow(it, num++, items)) }
        }
        places.forEach { place ->
            container.addView(buildGroupHeader(place, place))
            (byPlace[place] ?: emptyList()).forEach { container.addView(buildWhatItemRow(it, num++, items)) }
        }
        // orphaned items (place was deleted)
        items.filter { it.placeName != null && !places.contains(it.placeName) }
            .forEach { container.addView(buildWhatItemRow(it, num++, items)) }
    }


    private fun buildGroupHeader(label: String, placeName: String?): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(34)).also { it.topMargin = dp(4); it.bottomMargin = dp(3) }
            setBackgroundColor(0xFF141414.toInt())
            setPadding(dp(12), 0, dp(8), 0)
            setOnDragListener { v, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED -> { v.setBackgroundColor(0xFF252525.toInt()); true }
                    DragEvent.ACTION_DRAG_EXITED  -> { v.setBackgroundColor(0xFF141414.toInt()); true }
                    DragEvent.ACTION_DROP -> {
                        v.setBackgroundColor(0xFF141414.toInt())
                        lastDraggedId?.let { id -> assignTodoPlace(id, placeName); lastDraggedId = null }
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> { v.setBackgroundColor(0xFF141414.toInt()); true }
                    else -> false
                }
            }
            addView(TextView(this@MainActivity).apply {
                text = label; textSize = 11f; setTextColor(0xFFFF5722.toInt())
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
        }

    private fun buildWhatItemRow(item: TodoStore.Item, num: Int, allItems: List<TodoStore.Item>): LinearLayout {
        val bg = if (item.done) 0xFF111111.toInt() else 0xFF1A1A1A.toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.bottomMargin = dp(4) }
            background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(10).toFloat() }
            setPadding(dp(8), dp(10), dp(6), dp(10))
            setOnClickListener { if (!item.done) showEditTodoItemDialog(item) }
            setOnDragListener { v, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED -> { (v.background as? GradientDrawable)?.setColor(0xFF2A2A2A.toInt()); v.invalidate(); true }
                    DragEvent.ACTION_DRAG_EXITED  -> { (v.background as? GradientDrawable)?.setColor(bg); v.invalidate(); true }
                    DragEvent.ACTION_DROP -> {
                        (v.background as? GradientDrawable)?.setColor(bg); v.invalidate()
                        handleWhatDrop(item, allItems); true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        (v.background as? GradientDrawable)?.setColor(bg); v.invalidate()
                        v.alpha = 1f; true
                    }
                    else -> true
                }
            }
        }
        // done toggle
        row.addView(TextView(this).apply {
            text = if (item.done) "✓" else "□"
            textSize = 16f; setTextColor(if (item.done) 0xFF66BB6A.toInt() else 0xFF555555.toInt())
            setPadding(0, 0, dp(6), 0)
            setOnClickListener { toggleTodoItemDone(item.id) }
        })
        row.addView(TextView(this).apply {  // drag handle
            text = "⠿"; textSize = 26f; setTextColor(0xFF555555.toInt()); setPadding(dp(4), 0, dp(6), 0)
            isLongClickable = true
            setOnLongClickListener {
                lastDraggedId = item.id
                row.startDragAndDrop(ClipData.newPlainText("item", item.id.toString()), View.DragShadowBuilder(row), null, 0)
                row.alpha = 0.4f; true
            }
        })
        row.addView(TextView(this).apply {
            text = "$num."; textSize = 14f; setTextColor(0xFFFF5722.toInt()); setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(24), WRAP_CONTENT)
        })
        row.addView(TextView(this).apply {
            text = item.text; textSize = 14f
            if (item.done) {
                setTextColor(0xFF555555.toInt())
                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                setTextColor(Color.WHITE)
            }
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {  // assign to place
            text = "📍"; textSize = 14f; setTextColor(0xFF555555.toInt()); setPadding(dp(4), dp(2), dp(2), dp(2))
            setOnClickListener { showAssignPlaceDialog(item) }
        })
        row.addView(TextView(this).apply {  // delete
            text = "✕"; textSize = 14f; setTextColor(0xFF555555.toInt()); setPadding(dp(6), dp(2), dp(4), dp(2))
            setOnClickListener { removeTodoItem(item.id) }
        })
        return row
    }

    private fun handleWhatDrop(target: TodoStore.Item, allItems: List<TodoStore.Item>) {
        val id = lastDraggedId ?: return; lastDraggedId = null
        if (id == target.id) return
        val mut = allItems.toMutableList()
        val dragged = mut.find { it.id == id } ?: return
        val updated = if (dragged.placeName != target.placeName) dragged.copy(placeName = target.placeName) else dragged
        mut.removeAll { it.id == id }
        val idx = mut.indexOfFirst { it.id == target.id }.coerceAtLeast(0)
        mut.add(idx, updated)
        reorderTodoItems(mut); loadWhatList()
    }

    private fun showAssignPlaceDialog(item: TodoStore.Item) {
        val places = todoPlaces()
        val opts = arrayOf("Загальне (без місця)") + places.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Прив'язати до місця")
            .setItems(opts) { _, which ->
                assignTodoPlace(item.id, if (which == 0) null else places[which - 1])
            }.show()
    }

    // ── Де tab: manage places ─────────────────────────────────────────────────

    private fun loadWhereList() {
        val container = whereContainer ?: return
        container.removeAllViews()
        val places = todoPlaces()
        if (places.isEmpty()) container.addView(emptyView("Скажіть або натисніть 🎤 щоб додати місце"))
        places.forEachIndexed { idx, place -> container.addView(buildPlaceRow(place, idx)) }
    }

    private fun showAddPlaceDialog() {
        val input = EditText(this).apply {
            hint = "Назва місця"; textSize = 15f; setTextColor(Color.WHITE)
            setHintTextColor(0xFF555555.toInt())
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0xFF252525.toInt()); setStroke(dp(1), 0xFF333333.toInt()) }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Додати місце")
            .setView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(8)); addView(input) })
            .setPositiveButton("Додати") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) addTodoPlace(name)
            }
            .setNegativeButton("Скасувати", null).show()
    }

    private fun loadPlacesList(container: LinearLayout) {
        val places = TodoStore.loadPlaces(this)
        if (places.isEmpty()) { container.addView(emptyView("Натисніть 🎤 щоб додати місце")); return }
        container.addView(dividerHeader("🏷️ Місця"))
        places.forEachIndexed { idx, place -> container.addView(buildPlaceRow(place, idx)) }
    }

    private fun buildPlaceRow(place: String, idx: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
                it.bottomMargin = dp(6)
            }
            background = GradientDrawable().apply {
                setColor(0xFF1A1A1A.toInt()); cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(14), dp(12), dp(8), dp(12))
            isLongClickable = true
            setOnLongClickListener {
                val clip = ClipData.newPlainText("PLACE", place)
                val shadow = View.DragShadowBuilder(it)
                lastDraggedPlace = place
                it.startDragAndDrop(clip, shadow, null, 0)
                true
            }
            setOnDragListener { v, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        (v.background as? GradientDrawable)?.setColor(0xFF2A2A2A.toInt())
                        v.invalidate(); true
                    }
                    DragEvent.ACTION_DRAG_EXITED -> {
                        (v.background as? GradientDrawable)?.setColor(0xFF1A1A1A.toInt())
                        v.invalidate(); true
                    }
                    DragEvent.ACTION_DROP -> {
                        (v.background as? GradientDrawable)?.setColor(0xFF1A1A1A.toInt())
                        v.invalidate()
                        handlePlaceDrop(place)
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        (v.background as? GradientDrawable)?.setColor(0xFF1A1A1A.toInt())
                        v.invalidate(); true
                    }
                    else -> true
                }
            }
            setOnClickListener {
                showEditPlaceDialog(place)
            }
        }
        row.addView(TextView(this).apply {
            text = "${idx + 1}."; textSize = 15f
            setTextColor(0xFFFF5722.toInt()); setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(32), WRAP_CONTENT)
        })
        row.addView(TextView(this).apply {
            text = place; textSize = 15f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = "✕"; textSize = 16f; setTextColor(0xFFFF5252.toInt())
            setPadding(dp(10), dp(4), dp(4), dp(4))
            setOnClickListener { removeTodoPlace(place) }
        })
        return row
    }

    private fun handlePlaceDrop(targetPlace: String) {
        val dragged = lastDraggedPlace ?: return
        lastDraggedPlace = null
        if (dragged == targetPlace) return
        val places = todoPlaces().toMutableList()
        val fromIdx = places.indexOf(dragged)
        val toIdx = places.indexOf(targetPlace)
        if (fromIdx < 0 || toIdx < 0) return
        places.removeAt(fromIdx)
        places.add(toIdx, dragged)
        reorderTodoPlaces(places)
    }

    private fun showEditPlaceDialog(place: String) {
        val input = EditText(this).apply {
            setText(place); textSize = 15f; setTextColor(Color.WHITE)
            setHintTextColor(0xFF555555.toInt()); hint = "Назва"
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat(); setColor(0xFF252525.toInt())
                setStroke(dp(1), 0xFF333333.toInt())
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(8))
            addView(input)
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Редагувати місце")
            .setView(wrap)
            .setPositiveButton("Зберегти") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank() && newName != place) renameTodoPlace(place, newName)
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun loadItemsList(container: LinearLayout) {
        val items = TodoStore.load(this)
        if (items.isEmpty()) {
            container.addView(emptyView("Натисніть 🎤 щоб додати пункт"))
            return
        }
        container.addView(dividerHeader("📝 Список"))
        items.forEachIndexed { idx, item ->
            container.addView(buildTodoRow(item, idx, items))
        }
    }

    private fun dividerHeader(label: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
                it.bottomMargin = dp(4)
            }
        }
        row.addView(TextView(this).apply {
            text = label; textSize = 11f; setTextColor(0xFF888888.toInt())
            setTypeface(typeface, Typeface.BOLD)
        })
        row.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(1), 1f).also { it.marginStart = dp(8) }
            setBackgroundColor(0xFF222222.toInt())
        })
        return row
    }

    private fun buildTodoRow(
        item: TodoStore.Item,
        displayIdx: Int,
        allItems: List<TodoStore.Item>,
    ): LinearLayout {
        val normalBg = 0xFF1A1A1A.toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
                it.bottomMargin = dp(6)
            }
            background = GradientDrawable().apply {
                setColor(normalBg); cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(14), dp(12), dp(8), dp(12))
            setOnLongClickListener { v ->
                val clip = ClipData.newPlainText("TODO_ITEM", item.id.toString())
                val shadow = View.DragShadowBuilder(v)
                lastDraggedId = item.id
                v.startDragAndDrop(clip, shadow, null, 0)
                true
            }
            setOnClickListener { showEditTodoItemDialog(item) }
            setOnDragListener { v, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        (v.background as? GradientDrawable)?.setColor(0xFF2A2A2A.toInt())
                        v.invalidate(); true
                    }
                    DragEvent.ACTION_DRAG_EXITED -> {
                        (v.background as? GradientDrawable)?.setColor(normalBg)
                        v.invalidate(); true
                    }
                    DragEvent.ACTION_DROP -> {
                        (v.background as? GradientDrawable)?.setColor(normalBg)
                        v.invalidate()
                        handleDrop(item, allItems)
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        val gd = v.background as? GradientDrawable
                        if (gd != null) { gd.setColor(normalBg); v.invalidate() }
                        true
                    }
                    else -> true
                }
            }
        }

        // Number
        row.addView(TextView(this).apply {
            text = "${displayIdx + 1}."; textSize = 15f
            setTextColor(0xFFFF5722.toInt()); setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(32), WRAP_CONTENT)
        })

        // Item text
        row.addView(TextView(this).apply {
            text = item.text; textSize = 15f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })

        // Time (only shown when set)
        if (item.reminderMinutes != null) {
            row.addView(TextView(this).apply {
                text = "%02d:%02d".format(item.reminderMinutes / 60, item.reminderMinutes % 60)
                textSize = 12f; setTextColor(0xFF66BB6A.toInt())
                background = GradientDrawable().apply {
                    cornerRadius = dp(6).toFloat(); setColor(0xFF1A2E1A.toInt())
                }
                setPadding(dp(8), dp(3), dp(8), dp(3))
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also {
                    it.marginEnd = dp(4)
                }
                setOnClickListener { showTodoTimePicker(item) }
                setOnLongClickListener {
                    TodoStore.setReminderMinutes(this@MainActivity, item.id, null)
                    loadTodoList()
                    Toast.makeText(this@MainActivity, "Час видалено", Toast.LENGTH_SHORT).show()
                    true
                }
            })
        }

        // Number (continuous across all items)
        row.addView(TextView(this).apply {
            text = "${displayIdx + 1}."; textSize = 15f
            setTextColor(0xFFFF5722.toInt()); setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(32), WRAP_CONTENT)
        })

        // Item text
        row.addView(TextView(this).apply {
            text = item.text; textSize = 15f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })

        // Time
        if (item.reminderMinutes != null) {
            row.addView(TextView(this).apply {
                text = "%02d:%02d".format(item.reminderMinutes / 60, item.reminderMinutes % 60)
                textSize = 12f; setTextColor(0xFF66BB6A.toInt())
                background = GradientDrawable().apply {
                    cornerRadius = dp(6).toFloat(); setColor(0xFF1A2E1A.toInt())
                }
                setPadding(dp(8), dp(3), dp(8), dp(3))
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also {
                    it.marginEnd = dp(4)
                }
                setOnClickListener { showTodoTimePicker(item) }
                setOnLongClickListener {
                    TodoStore.setReminderMinutes(this@MainActivity, item.id, null)
                    loadTodoList()
                    Toast.makeText(this@MainActivity, "Час видалено", Toast.LENGTH_SHORT).show()
                    true
                }
            })
        }

        // Delete
        row.addView(TextView(this).apply {
            text = "✕"; textSize = 16f; setTextColor(0xFF666666.toInt())
            setPadding(dp(12), dp(4), dp(4), dp(4))
            setOnClickListener {
                TodoStore.remove(this@MainActivity, item.id)
                loadTodoList()
            }
        })
        return row
    }

    private fun handleDrop(targetItem: TodoStore.Item, allItems: List<TodoStore.Item>) {
        val draggedId = lastDraggedId ?: return
        lastDraggedId = null
        if (draggedId == targetItem.id) return
        val draggedItem = allItems.find { it.id == draggedId } ?: return
        val mutable = allItems.toMutableList()
        mutable.removeAll { it.id == draggedId }
        val insertIdx = mutable.indexOfFirst { it.id == targetItem.id }
        mutable.add(insertIdx, draggedItem)
        TodoStore.reorder(this, mutable)
        loadTodoList()
    }

    private fun showTodoTimePicker(item: TodoStore.Item) {
        val cal = Calendar.getInstance()
        item.reminderMinutes?.let { mins ->
            cal.set(Calendar.HOUR_OF_DAY, mins / 60)
            cal.set(Calendar.MINUTE, mins % 60)
        }
        TimePickerDialog(this, { _, h, m ->
            val totalMins = h * 60 + m
            TodoStore.setReminderMinutes(this, item.id, totalMins)
            loadTodoList()
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun pickTodoDate() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            val label = TodoStore.labelForCalendar(cal)
            TodoStore.saveTitle(this, label)
            todoTitleView?.text = label
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun confirmDeleteAllTodo() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Видалити всі пункти?")
            .setPositiveButton("Видалити") { _, _ ->
                if (isGroupMode()) {
                    val gid = groupId()!!
                    groupTodoItems.forEach { FirebaseSync.deleteGroupItem(gid, it.id) }
                    groupTodoItems = emptyList()
                    groupPlaces    = emptyList()
                    FirebaseSync.pushGroupPlaces(gid, emptyList())
                } else {
                    TodoStore.clear(this)
                }
                loadTodoList()
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun showEditTodoItemDialog(item: TodoStore.Item) {
        var editedMinutes = item.reminderMinutes

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.bottomMargin = dp(12) }
        }
        val titleEdit = EditText(this).apply {
            setText(item.text); textSize = 15f; setTextColor(Color.WHITE)
            setHintTextColor(0xFF555555.toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat(); setColor(0xFF252525.toInt())
                setStroke(dp(1), 0xFF333333.toInt())
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).also { it.marginEnd = dp(8) }
        }
        titleRow.addView(titleEdit)
        titleRow.addView(TextView(this).apply {
            text = "🎤"; textSize = 22f
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener {
                pendingEditTitleField = titleEdit
                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
                    putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Скажіть назву")
                    putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                try { startActivityForResult(intent, RC_TODO_EDIT_VOICE) }
                catch (_: Exception) { Toast.makeText(this@MainActivity, "Голосовий ввід недоступний", Toast.LENGTH_SHORT).show() }
            }
        })
        dialogLayout.addView(titleRow)

        val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val cal = Calendar.getInstance().apply {
            item.reminderMinutes?.let { set(Calendar.HOUR_OF_DAY, it / 60); set(Calendar.MINUTE, it % 60) }
        }
        val timeLabel = TextView(this).apply {
            text = if (item.reminderMinutes != null) "🕐  ${timeFmt.format(cal.time)}" else "🕐  Додати час"
            textSize = 13f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat(); setColor(0xFF252525.toInt())
                setStroke(dp(1), 0xFF333333.toInt())
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.bottomMargin = dp(16) }
            setOnClickListener {
                val c = Calendar.getInstance().apply {
                    editedMinutes?.let { set(Calendar.HOUR_OF_DAY, it / 60); set(Calendar.MINUTE, it % 60) }
                }
                TimePickerDialog(this@MainActivity, { _, h, m ->
                    editedMinutes = h * 60 + m
                    val nc = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }
                    text = "🕐  ${timeFmt.format(nc.time)}"
                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
            }
            setOnLongClickListener {
                editedMinutes = null
                text = "🕐  Додати час"
                true
            }
        }
        dialogLayout.addView(timeLabel)

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Редагувати")
            .setView(dialogLayout)
            .setPositiveButton("Зберегти") { _, _ ->
                val newText = titleEdit.text.toString().trim().takeIf { it.isNotEmpty() } ?: item.text
                if (newText != item.text || editedMinutes != item.reminderMinutes) {
                    if (isGroupMode()) {
                        val updated = groupTodoItems.find { it.id == item.id }
                            ?.copy(text = newText, reminderMinutes = editedMinutes) ?: return@setPositiveButton
                        groupTodoItems = groupTodoItems.map { if (it.id == item.id) updated else it }
                        FirebaseSync.pushGroupItem(groupId()!!, updated)
                    } else {
                        val items = TodoStore.load(this).map {
                            if (it.id == item.id) it.copy(text = newText, reminderMinutes = editedMinutes) else it
                        }
                        TodoStore.reorder(this, items)
                    }
                    loadTodoList()
                }
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun startTodoVoice() {
        val prompt = if (currentTodoTab == 1) "Скажіть назву місця" else "Що додати до списку?"
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try { startActivityForResult(intent, RC_TODO_VOICE) }
        catch (_: Exception) { android.widget.Toast.makeText(this, "Голосовий ввід недоступний", android.widget.Toast.LENGTH_SHORT).show() }
    }

    private fun emptyView(text: String) = TextView(this).apply {
        this.text = text; textSize = 14f; setTextColor(0xFF555555.toInt())
        gravity = android.view.Gravity.CENTER
        setPadding(0, dp(32), 0, 0)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    private fun shareTodoBitmap() {
        val items = todoItems()
        val title = if (isGroupMode())
            GroupStore.getGroupEmail(this) ?: TodoStore.loadTitle(this)
        else
            TodoStore.loadTitle(this)
        val bmp   = buildTodoBitmap(title, items)
        try {
            val dir  = File(cacheDir, "images").also { it.mkdirs() }
            val file = File(dir, "todo_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            val uri  = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            startActivity(android.content.Intent.createChooser(
                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/png"; putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Поділитися списком"
            ))
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Помилка: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildTodoBitmap(title: String, items: List<TodoStore.Item>): android.graphics.Bitmap {
        val density  = resources.displayMetrics.density
        val w        = (360 * density).toInt()
        val padH     = (24 * density).toInt()
        val padV     = (20 * density).toInt()
        val lineH    = (28 * density).toInt()
        val headerH  = (26 * density).toInt()
        val titleH   = (52 * density).toInt()
        val footerH  = (36 * density).toInt()

        // Build render list: [header label?, items...]
        val places  = todoPlaces()
        val byPlace = items.groupBy { it.placeName }
        data class Row(val label: String?, val item: TodoStore.Item?, val isGroupSection: Boolean = false)
        val rows = mutableListOf<Row>()

        val unassigned = byPlace[null] ?: emptyList()
        if (unassigned.isNotEmpty() || places.isEmpty()) {
            rows.add(Row("Загальне", null))
            unassigned.forEach { rows.add(Row(null, it)) }
        }
        places.forEach { place ->
            rows.add(Row(place, null))
            (byPlace[place] ?: emptyList()).forEach { rows.add(Row(null, it)) }
        }
        items.filter { it.placeName != null && !places.contains(it.placeName) }
            .forEach { rows.add(Row(null, it)) }

        val rowHeights = rows.map { if (it.item == null) headerH else lineH }
        val h = titleH + padV + rowHeights.sum() + padV * 2 + footerH + padV

        val bmp    = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)

        val bgPaint = android.graphics.Paint()
        val shader  = android.graphics.LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF1A1A2E.toInt(), 0xFF16213E.toInt(), 0xFF0F3460.toInt()),
            floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.CLAMP)
        bgPaint.shader = shader
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val accentPaint = android.graphics.Paint().apply { color = 0xFFFF5722.toInt() }
        canvas.drawRect(0f, 0f, w.toFloat(), 4 * density, accentPaint)

        val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 20 * density; typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText(title, padH.toFloat(), 4 * density + padV + titlePaint.textSize, titlePaint)

        val divPaint = android.graphics.Paint().apply { color = 0x33FFFFFF; strokeWidth = density }
        canvas.drawLine(padH.toFloat(), titleH - 2 * density, (w - padH).toFloat(), titleH - 2 * density, divPaint)

        val headerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11 * density; typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val numPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF5722.toInt(); textSize = 14 * density; typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE0E0E0.toInt(); textSize = 14 * density
        }

        var y = titleH + padV.toFloat()
        var itemNum = 1
        rows.forEach { row ->
            val rh = if (row.item == null) headerH else lineH
            val baseline = y + rh - 6 * density
            if (row.item == null) {
                headerPaint.color = if (row.isGroupSection) 0xFF66BB6A.toInt() else 0xFFFF5722.toInt()
                canvas.drawText(row.label ?: "", padH.toFloat(), baseline, headerPaint)
            } else {
                val nc = if (row.isGroupSection) 0xFF66BB6A.toInt() else 0xFFFF5722.toInt()
                numPaint.color = nc
                canvas.drawText("${itemNum++}.", padH.toFloat(), baseline, numPaint)
                canvas.drawText(row.item.text, padH + 32 * density, baseline, textPaint)
            }
            y += rh
        }

        val footPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF555555.toInt(); textSize = 11 * density
        }
        canvas.drawText("Нагадування", padH.toFloat(), h - padV.toFloat(), footPaint)

        return bmp
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TAB 3 — SETTINGS
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildSettingsPage(): View {
        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        page.addView(section("Мікрофон-віджет"))
        pinBtn = Button(this).apply {
            text = "+ Додати мікрофон-віджет на екран"
            textSize = 13f; setTextColor(Color.WHITE)
            background = roundedBg(0xFF2A2A2A.toInt(), 10f); layoutParams = lp(h = 46, btm = 4)
            setOnClickListener { pinMicWidget() }
        }
        page.addView(pinBtn!!)
        page.addView(small("Або: довге натискання на іконку → «Список нагадувань»", btm = 20))

        page.addView(section("Звук сповіщення"))
        soundBtn = Button(this).apply {
            textSize = 12f; setTextColor(Color.WHITE)
            background = roundedBg(0xFF1E2A30.toInt(), 8f); layoutParams = lp(h = 46, btm = 4)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.CHANNEL_ID)
                })
            }
        }
        page.addView(soundBtn!!)
        page.addView(small("Відкриває системні налаштування каналу", btm = 8))

        val persistRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; layoutParams = lp(btm = 4); gravity = Gravity.CENTER_VERTICAL
        }
        persistRow.addView(TextView(this).apply {
            text = "Показувати в статус-барі"; textSize = 13f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        val persistSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean(PersistentNotif.KEY_ENABLED, false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(PersistentNotif.KEY_ENABLED, checked).apply()
                PersistentNotif.update(this@MainActivity)
            }
        }
        persistRow.addView(persistSwitch)
        page.addView(persistRow)
        page.addView(small("Зворотній відлік до наступного нагадування + швидкий мікрофон із заблокованого екрану", btm = 16))

        page.addView(section("Кнопки «Відкласти»"))
        page.addView(snoozeInputRow())

        page.addView(section("Час дня (для «зранку/вдень/ввечері»)"))
        page.addView(timeOfDaySection())
        page.addView(section("Локації"))
        page.addView(small("Збережені місця для локаційних нагадувань подій (без конкретного часу).", btm = 8))
        page.addView(Button(this).apply {
            text = "📍  Менеджер локацій"; textSize = 12f; setTextColor(Color.WHITE)
            background = roundedBg(0xFF1A1A2E.toInt(), 8f); layoutParams = lp(h = 46, btm = 20)
            setOnClickListener {
                startActivityForResult(Intent(this@MainActivity, LocationManagerActivity::class.java), RC_LOCATION_MANAGER)
            }
        })

        page.addView(section("Google Календар"))
        page.addView(small("Налаштування експорту подій до Google Calendar", btm = 10))
        val exportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; layoutParams = lp(btm = 4); gravity = Gravity.CENTER_VERTICAL
        }
        exportRow.addView(TextView(this).apply {
            text = "Авто-додавати при створенні"; textSize = 13f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        exportSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean(KEY_CALENDAR_EXPORT, false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KEY_CALENDAR_EXPORT, checked).apply()
                if (checked) ensureCalendarPerms()
            }
        }
        exportRow.addView(exportSwitch!!)
        page.addView(exportRow)
        page.addView(small("Кнопка 📅 в списку — ручний експорт", btm = 10))
        page.addView(TextView(this).apply {
            text = "Колір події"; textSize = 13f; setTextColor(0xFFBBBBBB.toInt()); layoutParams = lp(btm = 6)
        })
        page.addView(colorRow(EVENT_COLORS, KEY_EVENT_COLOR, 0))

        page.addView(section("Приклади голосових команд"))
        page.addView(exampleBox(
            "«Через 5 хвилин відкрити двері»\n" +
            "«За годину нагадати зустріч»\n" +
            "«In 4 minutes open the door»\n" +
            "«On Wednesday close the deal»\n" +
            "«Every hour stand up»\n" +
            "«Tomorrow buy milk»"
        ))

        scroll.addView(page)
        return scroll
    }

    private fun exampleBox(text: String) = TextView(this).apply {
        this.text = text; textSize = 12f; setTextColor(0xFF777777.toInt())
        background = roundedBg(0xFF181818.toInt(), 8f)
        setPadding(dp(12), dp(10), dp(12), dp(10)); layoutParams = lp()
    }

    private fun updateSoundBtn() {
        soundBtn?.text = "🔔  Звук: ${NotificationHelper.getChannelSoundName(this)}"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CALENDAR PERMISSIONS
    // ─────────────────────────────────────────────────────────────────────────

    private fun ensureCalendarPerms() {
        if (!hasCalendarPerms()) ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), REQ_CAL_PERM)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAL_PERM && grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            prefs.edit().putBoolean(KEY_CALENDAR_EXPORT, false).apply(); updateExportSwitch()
        }
        if (requestCode == REQ_ACCOUNTS && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startPersonalSync()
        }
    }

    private fun hasCalendarPerms() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)  == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateUsageBadge() {
        usageBadge?.visibility = View.GONE
    }

    private fun updatePinBtn() {
        val ids = AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this, ReminderWidget::class.java))
        pinBtn?.visibility = if (ids.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateExportSwitch() {
        exportSwitch?.isEnabled = VoiceActivity.isUnlocked(this)
        exportSwitch?.isChecked = prefs.getBoolean(KEY_CALENDAR_EXPORT, false)
    }

    private fun pinMicWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, ReminderWidget::class.java), null, null)
            Toast.makeText(this, "✅ Мікрофон-віджет додано", Toast.LENGTH_SHORT).show()
            pinBtn?.visibility = View.GONE
        } else {
            Toast.makeText(this, "Додайте вручну через «Віджети»", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshMicWidget() {
        val mgr = AppWidgetManager.getInstance(this)
        mgr.getAppWidgetIds(ComponentName(this, ReminderWidget::class.java))
            .forEach { ReminderWidget.updateWidget(this, mgr, it) }
    }

    private fun snoozeInputRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = lp(btm = 20)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        fun labeledInput(key: String, default: Int): android.widget.EditText {
            val et = android.widget.EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText(prefs.getInt(key, default).toString())
                textSize = 14f; setTextColor(Color.WHITE)
                background = roundedBg(0xFF1E2A30.toInt(), 6f)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = LinearLayout.LayoutParams(dp(56), WRAP_CONTENT)
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                    override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) {
                        val v = s?.toString()?.toIntOrNull() ?: return
                        if (v >= 1) prefs.edit().putInt(key, v).apply()
                    }
                })
            }
            return et
        }
        fun minLabel(text: String) = TextView(this).apply {
            this.text = text; textSize = 13f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.marginEnd = dp(12) }
        }
        row.addView(minLabel("Кнопка 1:"))
        row.addView(labeledInput(KEY_SNOOZE1_MIN, 1))
        row.addView(TextView(this).apply {
            text = " хв"; textSize = 13f; setTextColor(0xFF888888.toInt())
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.marginEnd = dp(24) }
        })
        row.addView(minLabel("Кнопка 2:"))
        row.addView(labeledInput(KEY_SNOOZE2_MIN, 30))
        row.addView(TextView(this).apply {
            text = " хв"; textSize = 13f; setTextColor(0xFF888888.toInt())
        })
        return row
    }

    private fun timeOfDaySection(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutParams = lp(btm = 20)
        }
        fun timeRow(label: String, keyH: String, keyM: String, defH: Int, defM: Int) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; layoutParams = lp(btm = 6)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val btn = Button(this).apply {
                text = "%02d:%02d".format(prefs.getInt(keyH, defH), prefs.getInt(keyM, defM))
                textSize = 14f; setTextColor(Color.WHITE)
                background = roundedBg(0xFF1E2A30.toInt(), 6f)
                layoutParams = LinearLayout.LayoutParams(dp(80), dp(38))
                setOnClickListener {
                    TimePickerDialog(this@MainActivity, { _, h, m ->
                        prefs.edit().putInt(keyH, h).putInt(keyM, m).apply()
                        text = "%02d:%02d".format(h, m)
                    }, prefs.getInt(keyH, defH), prefs.getInt(keyM, defM), true).show()
                }
            }
            row.addView(TextView(this).apply {
                text = label; textSize = 13f; setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            row.addView(btn)
            col.addView(row)
        }
        timeRow("Зранку",  KEY_MORNING_HOUR, KEY_MORNING_MIN, 8,  0)
        timeRow("Вдень",   KEY_DAY_HOUR,     KEY_DAY_MIN,     13, 0)
        timeRow("Ввечері", KEY_EVENING_HOUR, KEY_EVENING_MIN, 19, 0)
        return col
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COMMON UI BUILDERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun section(text: String) = TextView(this).apply {
        this.text = text.uppercase(); textSize = 10f; setTextColor(0xFFBBBBBB.toInt())
        letterSpacing = 0.08f; layoutParams = lp(btm = 8)
    }

    private fun small(text: String, btm: Int = 0) = TextView(this).apply {
        this.text = text; textSize = 10f; setTextColor(0xFF555555.toInt()); layoutParams = lp(btm = btm)
    }

    private fun radioGroup(options: List<Pair<String, Long>>, key: String, default: Long, startId: Int): RadioGroup {
        val saved = prefs.getLong(key, default)
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; layoutParams = lp(btm = 20) }
        val idMap = options.mapIndexed { i, (label, ms) ->
            val id = startId + i
            group.addView(RadioButton(this).apply {
                text = label; this.id = id; setTextColor(Color.WHITE); textSize = 13f
                buttonTintList = android.content.res.ColorStateList.valueOf(0xFFFF5722.toInt())
                isChecked = ms == saved
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            id to ms
        }
        group.setOnCheckedChangeListener { _, id ->
            idMap.find { it.first == id }?.let { prefs.edit().putLong(key, it.second).apply(); refreshMicWidget() }
        }
        return group
    }

    private fun colorRow(palette: List<Pair<Int, String>>, key: String, default: Int,
                         onChange: (() -> Unit)? = null): LinearLayout {
        val saved = prefs.getInt(key, default)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; layoutParams = lp(btm = 20)
            setPadding(0, dp(4), 0, dp(4))
        }
        val gds = mutableListOf<GradientDrawable>()
        palette.forEach { (color, label) ->
            val display = if (color == 0 || color == 0x00000000) 0xFF333333.toInt() else color
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(display)
                if (color == saved) setStroke(dp(3), Color.WHITE)
            }
            gds.add(gd)
            val outer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.marginEnd = dp(10) }
            }
            outer.addView(View(this).apply {
                val s = dp(32); layoutParams = LinearLayout.LayoutParams(s, s)
                background = gd; contentDescription = label
                setOnClickListener {
                    prefs.edit().putInt(key, color).apply()
                    gds.forEach { it.setStroke(0, Color.TRANSPARENT) }
                    gd.setStroke(dp(3), Color.WHITE)
                    onChange?.invoke()
                }
            })
            if (color == 0x00000000) {
                outer.addView(TextView(this).apply {
                    text = "✕"; textSize = 8f; setTextColor(0xFF888888.toInt()); gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                })
            }
            row.addView(outer)
        }
        return row
    }

    private fun roundedBg(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun lp(h: Int = WRAP_CONTENT, btm: Int = 0) =
        LinearLayout.LayoutParams(MATCH_PARENT, if (h == WRAP_CONTENT) h else dp(h))
            .also { it.bottomMargin = dp(btm) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}
