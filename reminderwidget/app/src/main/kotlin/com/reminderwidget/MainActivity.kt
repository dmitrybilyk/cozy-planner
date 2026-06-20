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
import android.speech.RecognizerIntent
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
        private const val REQ_CAL_PERM   = 102
        private const val RC_AGENDA_VOICE = 2001

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
    private var agendaContainer:    LinearLayout? = null
    private var agendaDayLabel:     TextView?     = null
    private var selectedDay: Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    private var soundBtn:           Button?       = null
    private var exportSwitch:       Switch?       = null
    private var pinBtn:             Button?       = null
    private var usageBadge:         TextView?     = null
    private var calColorLabel:      View?         = null
    private var calColorRow:        View?         = null

    private val listChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            loadEventsList()
            if (currentTab == 1) loadFavoritesList()
            if (currentTab == 2) loadAgendaList()
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
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(listChangedReceiver) } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_AGENDA_VOICE && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.trim() ?: return
            if (text.isBlank()) return
            val parsed  = NlpParser.parse(text, System.currentTimeMillis())
            val startMs = if (!parsed.hasTime) {
                selectedDay.timeInMillis + 23 * 3600_000L + 59 * 60_000L
            } else parsed.startMs
            AgendaStore.add(this, AgendaStore.AgendaItem(
                id = System.currentTimeMillis(),
                title = parsed.title,
                startMs = startMs,
            ))
            loadAgendaList()
        }
    }

    private fun refreshDynamicState() {
        loadEventsList()
        if (currentTab == 1) loadFavoritesList()
        if (currentTab == 2) loadAgendaList()
        updateSoundBtn()
        updateExportSwitch()
        updatePinBtn()
        updateUsageBadge()
        updateCalColorVisibility()
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
            buildAgendaPage(),
            buildSettingsPage(),
            buildPremiumPage(),
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
        val navData = listOf("📋" to "Події", "❤️" to "Обрані", "📅" to "Agenda", "⚙️" to "Налаш.", "⭐" to "Преміум")
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(56))
            setBackgroundColor(0xFF111111.toInt())
        }
        navBtns = Array(5) { i ->
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
            2 -> loadAgendaList()
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
    // TAB 2 — AGENDA (independent store, separate from app events)
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildAgendaPage(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        // Day navigation header
        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setBackgroundColor(0xFF111111.toInt())
        }
        navRow.addView(iconBtn("◀", dp(40), 0xFF222222.toInt()) {
            selectedDay.add(Calendar.DAY_OF_YEAR, -1); updateAgendaDayLabel(); loadAgendaList()
        })
        agendaDayLabel = TextView(this).apply {
            textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        navRow.addView(agendaDayLabel!!)
        navRow.addView(iconBtn("▶", dp(40), 0xFF222222.toInt()) {
            selectedDay.add(Calendar.DAY_OF_YEAR, 1); updateAgendaDayLabel(); loadAgendaList()
        })
        updateAgendaDayLabel()
        page.addView(navRow)

        // Scrollable content
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }

        // Mic quick-add
        inner.addView(Button(this).apply {
            text = "🎙  Додати голосом"; textSize = 14f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat(); setColor(0xFFFF5722.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(50))
                .also { it.bottomMargin = dp(14) }
            setOnClickListener {
                @Suppress("DEPRECATION")
                startActivityForResult(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Що додати до агенди?")
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    },
                    RC_AGENDA_VOICE
                )
            }
        })

        agendaContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(agendaContainer!!)
        scroll.addView(inner)
        page.addView(scroll)

        // Bottom action bar
        val actionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(0xFF111111.toInt())
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        fun agendaBtn(label: String, bg: Int, onClick: () -> Unit) = Button(this).apply {
            text = label; textSize = 11f; setTextColor(Color.WHITE)
            background = roundedBg(bg, 8f)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).also { it.marginEnd = dp(5) }
            setOnClickListener { onClick() }
        }
        actionBar.addView(agendaBtn("📸 Картинка", 0xFF2A1A00.toInt()) {
            if (!VoiceActivity.isUnlocked(this)) {
                Toast.makeText(this, "Потрібен Преміум", Toast.LENGTH_SHORT).show()
            } else shareAgendaAsPicture()
        })
        actionBar.addView(agendaBtn("📅 Calendar", 0xFF0D2A0D.toInt()) {
            if (!VoiceActivity.isUnlocked(this)) {
                Toast.makeText(this, "Потрібен Преміум", Toast.LENGTH_SHORT).show()
            } else exportAgendaToCalendar()
        })
        actionBar.addView(agendaBtn("📝 Keep", 0xFF1A1A2A.toInt()) {
            if (!VoiceActivity.isUnlocked(this)) {
                Toast.makeText(this, "Потрібен Преміум", Toast.LENGTH_SHORT).show()
            } else exportAgendaToKeep()
        })
        page.addView(actionBar)
        return page
    }

    private fun updateAgendaDayLabel() {
        val fmt   = SimpleDateFormat("EEEE, dd.MM.yyyy", Locale("uk"))
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        agendaDayLabel?.text = when {
            selectedDay.get(Calendar.YEAR)        == today.get(Calendar.YEAR) &&
            selectedDay.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Сьогодні"
            else -> fmt.format(selectedDay.time).replaceFirstChar { it.uppercase() }
        }
    }

    private fun loadAgendaList() {
        val c = agendaContainer ?: return
        c.removeAllViews()
        val items = agendaItemsForDay()

        if (items.isEmpty()) {
            c.addView(buildEmptyState("Натисніть 🎙 щоб додати пункти агенди.\nПункти зберігаються окремо від нагадувань."))
            return
        }
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        items.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(if (item.completed) 0xFF161616.toInt() else 0xFF1C1C1E.toInt())
                }
                setPadding(dp(12), dp(10), dp(6), dp(10))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    .also { it.bottomMargin = dp(6) }
                if (!item.completed) setOnClickListener { showEditAgendaItemDialog(item) }
            }
            row.addView(TextView(this).apply {
                text = timeFmt.format(Date(item.startMs))
                textSize = 12f
                setTextColor(if (item.completed) 0xFF555555.toInt() else 0xFFFF5722.toInt())
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(dp(52), WRAP_CONTENT)
            })
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(1), dp(22)).also {
                    it.marginStart = dp(8); it.marginEnd = dp(10)
                }
                setBackgroundColor(0xFF333333.toInt())
            })
            row.addView(TextView(this).apply {
                text = item.title; textSize = 13f
                if (item.completed) {
                    setTextColor(0xFF555555.toInt())
                    paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    setTextColor(Color.WHITE)
                }
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            // Done / undo
            row.addView(iconBtn(if (item.completed) "↩" else "✅", dp(34), 0xFF1C1C1E.toInt()) {
                if (item.completed) {
                    AgendaStore.update(this, item.copy(completed = false))
                } else {
                    AgendaStore.markCompleted(this, item.id)
                }
                loadAgendaList()
            })
            // Delete
            row.addView(iconBtn("🗑", dp(34), 0xFF1C1C1E.toInt()) {
                AgendaStore.remove(this, item.id); loadAgendaList()
            })
            c.addView(row)
        }
    }

    private fun agendaItemsForDay(): List<AgendaStore.AgendaItem> {
        val dayStart = selectedDay.timeInMillis
        val dayEnd   = dayStart + 24 * 60 * 60_000L
        return AgendaStore.load(this)
            .filter { it.startMs >= dayStart && it.startMs < dayEnd }
            .sortedWith(compareBy({ it.completed }, { it.startMs }))
    }

    private fun showEditAgendaItemDialog(item: AgendaStore.AgendaItem) {
        var pickedMs = item.startMs
        val dateFmt  = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val timeFmt  = SimpleDateFormat("HH:mm",      Locale.getDefault())

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        val titleEdit = EditText(this).apply {
            setText(item.title); textSize = 15f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat(); setColor(0xFF252525.toInt())
                setStroke(dp(1), 0xFF333333.toInt())
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .also { it.bottomMargin = dp(12) }
        }
        layout.addView(titleEdit)

        val dateLabel = TextView(this).apply {
            text = "📅  ${dateFmt.format(Date(pickedMs))}"
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
            text = "🕐  ${timeFmt.format(Date(pickedMs))}"
            textSize = 13f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat(); setColor(0xFF252525.toInt())
                setStroke(dp(1), 0xFF333333.toInt())
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .also { it.bottomMargin = dp(16) }
        }
        dateLabel.setOnClickListener {
            val c = Calendar.getInstance().apply { timeInMillis = pickedMs }
            DatePickerDialog(this, { _, y, m, d ->
                val nc = Calendar.getInstance().apply { timeInMillis = pickedMs }
                nc.set(y, m, d); pickedMs = nc.timeInMillis
                dateLabel.text = "📅  ${dateFmt.format(Date(pickedMs))}"
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }
        timeLabel.setOnClickListener {
            val c = Calendar.getInstance().apply { timeInMillis = pickedMs }
            TimePickerDialog(this, { _, h, min ->
                val nc = Calendar.getInstance().apply { timeInMillis = pickedMs }
                nc.set(Calendar.HOUR_OF_DAY, h); nc.set(Calendar.MINUTE, min); nc.set(Calendar.SECOND, 0)
                pickedMs = nc.timeInMillis
                timeLabel.text = "🕐  ${timeFmt.format(Date(pickedMs))}"
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
        }
        layout.addView(dateLabel); layout.addView(timeLabel)

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Редагувати пункт")
            .setView(layout)
            .setPositiveButton("Зберегти") { _, _ ->
                val newTitle = titleEdit.text.toString().trim().takeIf { it.isNotEmpty() } ?: item.title
                AgendaStore.update(this, item.copy(title = newTitle, startMs = pickedMs))
                loadAgendaList()
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun shareAgendaAsPicture() {
        val items   = agendaItemsForDay().filter { !it.completed }
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEEE, dd.MM.yyyy", Locale("uk"))
        val dateStr = dateFmt.format(selectedDay.time).replaceFirstChar { it.uppercase() }

        val W = 1080; val pad = 60; val rowH = 96; val headerH = 220; val footerH = 80
        val H = headerH + rowH * items.size.coerceAtLeast(1) + pad + footerH
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv  = Canvas(bmp)
        cv.drawColor(Color.parseColor("#1C1C2E"))
        cv.drawRect(0f, 0f, W.toFloat(), 10f, Paint().apply { color = Color.parseColor("#FF5722") })
        cv.drawText("Нагадування", pad.toFloat(), (pad + 46).toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF5722"); textSize = 34f; typeface = Typeface.DEFAULT_BOLD })
        cv.drawText(dateStr, pad.toFloat(), (pad + 116).toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 52f; typeface = Typeface.DEFAULT_BOLD })
        cv.drawRect(pad.toFloat(), (pad + 134).toFloat(), (W - pad).toFloat(), (pad + 136).toFloat(),
            Paint().apply { color = Color.parseColor("#333344") })

        val timePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF5722"); textSize = 38f; typeface = Typeface.DEFAULT_BOLD }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 40f }
        val rowBg      = Paint().apply { color = Color.parseColor("#25253A") }
        var y = headerH
        if (items.isEmpty()) {
            cv.drawText("Немає пунктів агенди", pad.toFloat(), (y + 52).toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#555566"); textSize = 36f })
        } else {
            items.forEach { item ->
                cv.drawRoundRect(pad.toFloat(), y.toFloat(), (W - pad).toFloat(), (y + rowH - 8).toFloat(), 18f, 18f, rowBg)
                cv.drawText(timeFmt.format(Date(item.startMs)), (pad + 18).toFloat(), (y + 58).toFloat(), timePaint)
                cv.drawText(item.title, (pad + 150).toFloat(), (y + 58).toFloat(), titlePaint)
                y += rowH
            }
        }
        cv.drawText("Нагадування · нагадай собі важливе", pad.toFloat(), (H - 24).toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#444455"); textSize = 28f })

        try {
            val dir  = File(cacheDir, "images").also { it.mkdirs() }
            val file = File(dir, "agenda_${selectedDay.timeInMillis}.png")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Поділитись агендою"
            ))
        } catch (e: Exception) {
            Toast.makeText(this, "Помилка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportAgendaToCalendar() {
        if (!hasCalendarPerms()) { ensureCalendarPerms(); return }
        val items = agendaItemsForDay().filter { !it.completed }
        if (items.isEmpty()) {
            Toast.makeText(this, "Немає пунктів для експорту", Toast.LENGTH_SHORT).show(); return
        }
        var count = 0
        items.forEach { item ->
            val calId = CalendarHelper.createReminder(
                this, item.title, item.startMs, 3_600_000L,
                prefs.getInt(KEY_EVENT_COLOR, 0), null
            )
            if (calId != -1L) count++
        }
        Toast.makeText(this, "Додано $count подій до Google Календаря", Toast.LENGTH_SHORT).show()
    }

    private fun exportAgendaToKeep() {
        val items   = agendaItemsForDay()
        val dateFmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm",      Locale.getDefault())
        val text = buildString {
            appendLine("Agenda — ${dateFmt.format(selectedDay.time)}")
            appendLine()
            if (items.isEmpty()) appendLine("Немає пунктів")
            else items.forEach {
                val check = if (it.completed) "☑" else "☐"
                appendLine("$check  ${timeFmt.format(Date(it.startMs))}  ${it.title}")
            }
        }
        val keepIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
            `package` = "com.google.android.keep"
        }
        try { startActivity(keepIntent) } catch (_: Exception) {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) },
                "Поділитись агендою"
            ))
        }
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
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    EventStore.load(this@MainActivity).forEach { e ->
                        nm.cancel(NotificationHelper.notifId(e.id))
                        NotificationHelper.cancelAlarm(this@MainActivity, e.id)
                        NotificationHelper.cancelRepeat(this@MainActivity, e.id)
                        NotificationHelper.setRepeating(this@MainActivity, e.id, false)
                    }
                    EventStore.clear(this@MainActivity)
                    EventsWidget.update(this@MainActivity)
                    loadEventsList()
                    if (currentTab == 1) loadFavoritesList()
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

        // 🔔/📌 bell — premium only
        if (premium) {
            row.addView(iconBtn(if (isOngoing) "📌" else "🔔", btnSz,
                if (isOngoing) 0xFF2A1500.toInt() else 0xFF242424.toInt()) {
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
                if (pickedMs <= now + 5_000L) {
                    NotificationHelper.post(this, updated)
                } else {
                    NotificationHelper.scheduleAt(this, updated.id, pickedMs)
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
    // TAB 2 — SETTINGS
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
        page.addView(small("Відкриває системні налаштування каналу", btm = 16))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            page.addView(Button(this).apply {
                text = "⏰  Дозволити точні будильники"; textSize = 12f; setTextColor(Color.WHITE)
                background = roundedBg(0xFF1E2030.toInt(), 8f); layoutParams = lp(h = 46, btm = 4)
                setOnClickListener { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
            })
            page.addView(small("Потрібно для точного спрацьовування «через N хвилин»", btm = 20))
        }

        page.addView(section("Колір фону віджетів"))
        page.addView(colorRow(WIDGET_BG_COLORS, KEY_WIDGET_BG, 0xDD000000.toInt()) {
            refreshMicWidget(); EventsWidget.update(this)
        })

        // Calendar event color — only shown for premium users
        calColorLabel = section("Колір події в Календарі")
        page.addView(calColorLabel!!)
        calColorRow = colorRow(EVENT_COLORS, KEY_EVENT_COLOR, 0)
        page.addView(calColorRow!!)

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

    private fun updateCalColorVisibility() {
        val v = if (VoiceActivity.isUnlocked(this)) View.VISIBLE else View.GONE
        calColorLabel?.visibility = v
        calColorRow?.visibility   = v
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TAB 3 — PREMIUM
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildPremiumPage(): View {
        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(40), dp(24), dp(32))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF1C0E04.toInt(), 0xFF0D0D0D.toInt())
            )
        }
        hero.addView(TextView(this).apply {
            text = "⭐"; textSize = 52f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.bottomMargin = dp(10) }
        })
        hero.addView(TextView(this).apply {
            text = "ПРЕМІУМ"; textSize = 22f; setTextColor(0xFFFF5722.toInt())
            setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER; letterSpacing = 0.12f
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.bottomMargin = dp(20) }
        })
        listOf(
            "♾️  Необмежені нагадування (безкоштовно: 3/день)",
            "📅  Ручний та авто-експорт до Google Календаря",
            "📌  Постійні закріплені сповіщення в статус-барі",
            "🔄  Авто-синхронізація між пристроями",
        ).forEach { f ->
            hero.addView(TextView(this).apply {
                text = f; textSize = 13f; setTextColor(0xFFCCCCCC.toInt())
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.bottomMargin = dp(7) }
            })
        }
        page.addView(hero)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(20), dp(16), dp(32))
        }

        val unlockBtn = Button(this).apply { layoutParams = lp(h = 52, btm = 8) }
        fun applyUnlock() {
            val on = VoiceActivity.isUnlocked(this)
            unlockBtn.text = if (on) "✅ Преміум активовано — вимкнути" else "🔓 Активувати безкоштовно"
            unlockBtn.textSize = 14f
            unlockBtn.setTextColor(if (on) 0xFFAAAAAA.toInt() else Color.WHITE)
            unlockBtn.background = roundedBg(if (on) 0xFF1A2A1A.toInt() else 0xFFFF5722.toInt(), 12f)
        }
        applyUnlock()
        unlockBtn.setOnClickListener {
            prefs.edit().putBoolean("premium_unlocked", !VoiceActivity.isUnlocked(this)).apply()
            applyUnlock(); updateUsageBadge(); updateExportSwitch()
            updateCalColorVisibility(); loadEventsList()
            if (currentTab == 1) loadFavoritesList()
        }
        content.addView(unlockBtn)
        content.addView(small("Без оплати — можна вимкнути в будь-який момент", btm = 28))

        content.addView(section("Автоекспорт до Google Календаря"))
        val exportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; layoutParams = lp(btm = 4); gravity = Gravity.CENTER_VERTICAL
        }
        exportRow.addView(TextView(this).apply {
            text = "Авто-додавати при створенні"; textSize = 13f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        exportSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean(KEY_CALENDAR_EXPORT, false)
            isEnabled = VoiceActivity.isUnlocked(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                if (!VoiceActivity.isUnlocked(this@MainActivity)) {
                    isChecked = false
                    Toast.makeText(this@MainActivity, "Потрібен Преміум", Toast.LENGTH_SHORT).show()
                    return@setOnCheckedChangeListener
                }
                prefs.edit().putBoolean(KEY_CALENDAR_EXPORT, checked).apply()
                if (checked) ensureCalendarPerms()
            }
        }
        exportRow.addView(exportSwitch!!); content.addView(exportRow)
        content.addView(small("Кнопка 📅 в списку — ручний експорт (лише Преміум)", btm = 0))
        page.addView(content)
        scroll.addView(page)
        return scroll
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
    }

    private fun hasCalendarPerms() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)  == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateUsageBadge() {
        val unlocked = VoiceActivity.isUnlocked(this)
        val count    = VoiceActivity.todayCount(this)
        usageBadge?.text = if (unlocked) "★ Преміум" else "$count / ${VoiceActivity.FREE_LIMIT}"
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
