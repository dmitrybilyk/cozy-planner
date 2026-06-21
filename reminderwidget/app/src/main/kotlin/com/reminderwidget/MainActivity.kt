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
    private var todoContainer:   LinearLayout? = null
    private var todoTitleView:   TextView?     = null
    private val RC_TODO_VOICE = 2001
    private var todoListener:    com.google.firebase.firestore.ListenerRegistration? = null
    private var eventsListener:  com.google.firebase.firestore.ListenerRegistration? = null

    private val listChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            loadEventsList()
            if (currentTab == 1) loadFavoritesList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        NotificationHelper.ensureChannel(this)
        NotificationHelper.ensureSilentChannel(this)
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED) {
            startFirebaseSync()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.GET_ACCOUNTS), REQ_ACCOUNTS)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(listChangedReceiver) } catch (_: Exception) {}
        todoListener?.remove();   todoListener   = null
        eventsListener?.remove(); eventsListener = null
    }

    private fun startFirebaseSync() {
        FirebaseSync.init(this)
        if (!FirebaseSync.isReady()) return
        todoListener?.remove()
        todoListener = FirebaseSync.listenTodo(this) { title, items ->
            TodoStore.mergeFromRemote(this, title, items)
            todoTitleView?.text = title
            if (currentTab == 2) loadTodoList()
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
                if (currentTab == 0) loadEventsList()
                if (currentTab == 1) loadFavoritesList()
                EventsWidget.update(this)
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_TODO_VOICE && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.trim()
            if (!text.isNullOrBlank()) {
                TodoStore.add(this, text)
                loadTodoList()
            }
        }
    }

    private fun refreshDynamicState() {
        loadEventsList()
        if (currentTab == 1) loadFavoritesList()
        if (currentTab == 2) loadTodoList()
        updateSoundBtn()
        updateExportSwitch()
        updatePinBtn()
        updateUsageBadge()
        refreshMicWidget()
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
            buildFavoritesPage(),
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
        val navData = listOf("📋" to "Події", "❤️" to "Обрані", "✅" to "Список", "⚙️" to "Налаш.")
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(56))
            setBackgroundColor(0xFF111111.toInt())
        }
        navBtns = Array(4) { i ->
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
            0 -> loadEventsList()
            1 -> loadFavoritesList()
            2 -> loadTodoList()
            3 -> updateSoundBtn()
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
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(24))
        }
        eventsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(eventsContainer)
        scroll.addView(inner)
        page.addView(scroll)
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
                            if (currentTab == 1) loadFavoritesList()
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

        val active    = events.filter { !it.completed }.sortedBy { it.startMs }
        val completed = events.filter {  it.completed }.sortedByDescending { it.startMs }

        var lastDay = ""
        active.forEach { event ->
            val dayLabel = dayLabel(event.startMs)
            if (dayLabel != lastDay) { c.addView(buildDayHeader(dayLabel)); lastDay = dayLabel }
            val activeNotif = nm.activeNotifications.find { it.id == NotificationHelper.notifId(event.id) }
            val isActive    = activeNotif != null
            val isOngoing   = (activeNotif?.notification?.flags ?: 0) and Notification.FLAG_ONGOING_EVENT != 0
            c.addView(buildEventRow(event, isActive, isOngoing, event.calendarEventId != -1L, premium, timeFmt, false))
        }

        if (completed.isNotEmpty()) {
            c.addView(buildDayHeader("✅ Виконані"))
            completed.forEach { event ->
                c.addView(buildEventRow(event, false, false, event.calendarEventId != -1L, premium, fullFmt, true))
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
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .also { it.bottomMargin = dp(6) }
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (event.completed) 0xFF161616.toInt() else 0xFF1C1C1E.toInt())
                if (isOngoing) setStroke(dp(1), 0x55FF5722)
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
            text = event.title; textSize = 14f
            if (event.completed) {
                setTextColor(0xFF666666.toInt())
                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
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
                    if (prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)) {
                        NotificationHelper.post(this, event, ongoing = true, silent = true, pinned = true)
                        Toast.makeText(this, "📌 Закріплено в статус-барі", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Сповіщення вимкнено в налаштуваннях", Toast.LENGTH_SHORT).show()
                    }
                    EventsWidget.update(this)
                }
                loadEventsList(); if (currentTab == 1) loadFavoritesList()
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
            loadEventsList(); if (currentTab == 1) loadFavoritesList()
        })

        // 🗑 delete — always
        row.addView(iconBtn("🗑", btnSz, 0xFF242424.toInt()) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NotificationHelper.notifId(event.id))
            NotificationHelper.cancelAlarm(this, event.id)
            NotificationHelper.cancelRepeat(this, event.id)
            NotificationHelper.setRepeating(this, event.id, false)
            EventStore.remove(this, event.id)
            EventsWidget.update(this)
            loadEventsList(); if (currentTab == 1) loadFavoritesList()
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
                loadEventsList(); if (currentTab == 1) loadFavoritesList()
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
            loadEventsList(); if (currentTab == 1) loadFavoritesList()
        } else {
            Toast.makeText(this, "Не вдалося додати до Календаря", Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TAB 2 — TODO
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildTodoPage(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D0D0D.toInt())
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        // ── Header: date + share + delete-all ────────────────────────────────
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

        // ── Scrollable list ───────────────────────────────────────────────────
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        todoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(80))
        }
        scroll.addView(todoContainer)
        page.addView(scroll)

        // ── Mic FAB ───────────────────────────────────────────────────────────
        val fabWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(80))
            setBackgroundColor(0xFF0D0D0D.toInt())
        }
        val fab = TextView(this).apply {
            text = "🎤"; textSize = 30f; gravity = android.view.Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFF5722.toInt())
            }
            val s = dp(58)
            layoutParams = FrameLayout.LayoutParams(s, s).also {
                it.gravity = android.view.Gravity.CENTER
            }
            setOnClickListener { startTodoVoice() }
        }
        fabWrap.addView(fab)
        page.addView(fabWrap)

        return page
    }

    private fun loadTodoList() {
        val container = todoContainer ?: return
        container.removeAllViews()
        val items = TodoStore.load(this)
        if (items.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Натисніть 🎤 щоб додати пункт"
                textSize = 14f; setTextColor(0xFF555555.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, dp(32), 0, 0)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            })
            return
        }
        items.forEachIndexed { idx, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
                    it.bottomMargin = dp(10)
                }
                setBackgroundColor(0xFF1A1A1A.toInt())
                background = GradientDrawable().apply {
                    setColor(0xFF1A1A1A.toInt()); cornerRadius = dp(10).toFloat()
                }
                setPadding(dp(14), dp(12), dp(8), dp(12))
            }
            val numView = TextView(this).apply {
                text = "${idx + 1}."; textSize = 15f
                setTextColor(0xFFFF5722.toInt()); setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(dp(32), WRAP_CONTENT)
            }
            val textView = TextView(this).apply {
                text = item.text; textSize = 15f; setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            }
            val delBtn = TextView(this).apply {
                text = "✕"; textSize = 16f; setTextColor(0xFF666666.toInt())
                setPadding(dp(12), dp(4), dp(4), dp(4))
                setOnClickListener {
                    TodoStore.remove(this@MainActivity, item.id)
                    loadTodoList()
                }
            }
            row.addView(numView)
            row.addView(textView)
            row.addView(delBtn)
            container.addView(row)
        }
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
            .setPositiveButton("Видалити") { _, _ -> TodoStore.clear(this); loadTodoList() }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun startTodoVoice() {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Що додати до списку?")
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try { startActivityForResult(intent, RC_TODO_VOICE) }
        catch (_: Exception) { android.widget.Toast.makeText(this, "Голосовий ввід недоступний", android.widget.Toast.LENGTH_SHORT).show() }
    }

    private fun shareTodoBitmap() {
        val items = TodoStore.load(this)
        val title = TodoStore.loadTitle(this)
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
        val lineH    = (32 * density).toInt()
        val titleH   = (52 * density).toInt()
        val footerH  = (36 * density).toInt()
        val h        = titleH + padV + items.size * lineH + padV * 2 + footerH + padV

        val bmp    = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)

        // Background gradient
        val bgPaint = android.graphics.Paint()
        val shader  = android.graphics.LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF1A1A2E.toInt(), 0xFF16213E.toInt(), 0xFF0F3460.toInt()),
            floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.CLAMP)
        bgPaint.shader = shader
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Accent bar at top
        val accentPaint = android.graphics.Paint().apply { color = 0xFFFF5722.toInt(); isAntiAlias = true }
        canvas.drawRect(0f, 0f, w.toFloat(), (4 * density), accentPaint)

        // Title
        val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 20 * density
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText(title, padH.toFloat(), (4 * density) + padV + titlePaint.textSize, titlePaint)

        // Divider
        val divPaint = android.graphics.Paint().apply { color = 0x33FFFFFF; strokeWidth = density }
        canvas.drawLine(padH.toFloat(), (titleH - 2 * density), (w - padH).toFloat(), (titleH - 2 * density), divPaint)

        // Items
        val numPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF5722.toInt(); textSize = 15 * density
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE0E0E0.toInt(); textSize = 15 * density
        }
        items.forEachIndexed { i, item ->
            val y = (titleH + padV + (i + 1) * lineH - 4 * density)
            canvas.drawText("${i + 1}.", padH.toFloat(), y, numPaint)
            canvas.drawText(item.text, (padH + 32 * density), y, textPaint)
        }

        // Footer
        val footPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF555555.toInt(); textSize = 11 * density
        }
        val footY = h - padV.toFloat()
        canvas.drawText("Нагадування", padH.toFloat(), footY, footPaint)

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

        page.addView(section("Тривалість нагадування"))
        page.addView(radioGroup(DURATION_OPTIONS, KEY_DURATION_MS, 7 * 24 * 60 * 60_000L, startId = 200))

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

        val notifRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; layoutParams = lp(btm = 16); gravity = Gravity.CENTER_VERTICAL
        }
        notifRow.addView(TextView(this).apply {
            text = "Показувати сповіщення в застосунку"; textSize = 13f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        val notifSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, checked).apply()
            }
        }
        notifRow.addView(notifSwitch)
        page.addView(notifRow)

        page.addView(section("Кнопки «Відкласти»"))
        page.addView(snoozeInputRow())

        page.addView(section("Час дня (для «зранку/вдень/ввечері»)"))
        page.addView(timeOfDaySection())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            page.addView(Button(this).apply {
                text = "⏰  Дозволити точні будильники"; textSize = 12f; setTextColor(Color.WHITE)
                background = roundedBg(0xFF1E2030.toInt(), 8f); layoutParams = lp(h = 46, btm = 4)
                setOnClickListener { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
            })
            page.addView(small("Потрібно для точного спрацьовування «через N хвилин»", btm = 20))
        }

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
            startFirebaseSync()
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
