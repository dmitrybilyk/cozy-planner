package com.reminderwidget

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceActivity : Activity() {

    companion object {
        private const val RC_VOICE        = 1001
        private const val RC_CAL_PERM     = 1002

        private const val KEY_UNLOCKED    = "premium_unlocked"
        private const val KEY_COUNT_DATE  = "count_date"
        private const val KEY_COUNT       = "day_count"
        const val FREE_LIMIT = 3

        fun isUnlocked(context: Context): Boolean =
            context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_UNLOCKED, false)

        fun todayCount(context: Context): Int {
            val prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            return if (prefs.getString(KEY_COUNT_DATE, "") == today) prefs.getInt(KEY_COUNT, 0) else 0
        }

        fun incrementCount(context: Context) {
            val prefs   = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            val today   = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val current = if (prefs.getString(KEY_COUNT_DATE, "") == today) prefs.getInt(KEY_COUNT, 0) else 0
            prefs.edit().putString(KEY_COUNT_DATE, today).putInt(KEY_COUNT, current + 1).apply()
        }
    }

    private var pendingText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isUnlocked(this) && todayCount(this) >= FREE_LIMIT) {
            Toast.makeText(this, "⛔ Ліміт $FREE_LIMIT нагадування на день. Відкрийте «Нагадування» для розблокування.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        startVoiceRecognition()
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Що нагадати?")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            startActivityForResult(intent, RC_VOICE)
        } catch (e: Exception) {
            Toast.makeText(this, "Голосовий ввід недоступний", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_VOICE && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.trim()
            if (!text.isNullOrBlank()) {
                handleText(text); return
            }
        }
        finish()
    }

    private fun handleText(text: String) {
        val calExport = calendarExportEnabled()
        if (calExport && !hasCalendarPerms()) {
            pendingText = text
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                RC_CAL_PERM)
        } else {
            createEvent(text, calExport && hasCalendarPerms())
            finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_CAL_PERM) {
            val text = pendingText ?: return run { finish() }
            pendingText = null
            createEvent(text, grantResults.all { it == PackageManager.PERMISSION_GRANTED })
            finish()
        }
    }

    private fun createEvent(text: String, exportToCalendar: Boolean) {
        val prefs      = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val durationMs = prefs.getLong(MainActivity.KEY_DURATION_MS, 7 * 24 * 60 * 60_000L)
        val color      = prefs.getInt(MainActivity.KEY_EVENT_COLOR, 0)
        val parsed     = NlpParser.parse(text, System.currentTimeMillis())

        val effectiveDuration = parsed.durationOverrideMs ?: durationMs
        val localId = System.currentTimeMillis()
        val event = EventStore.AppEvent(
            id         = localId,
            title      = parsed.title,
            startMs    = parsed.startMs,
            durationMs = effectiveDuration,
            rrule      = parsed.rrule,
        )
        EventStore.add(this, event)
        incrementCount(this)

        val now = System.currentTimeMillis()
        val immediate = event.startMs <= now + 5_000L   // within 5 s = fire now
        if (immediate) {
            NotificationHelper.post(this, event)
        } else {
            NotificationHelper.scheduleAt(this, event.id, event.startMs)
        }

        if (exportToCalendar) {
            val calId = CalendarHelper.createReminder(this, parsed.title, parsed.startMs, effectiveDuration, color, parsed.rrule)
            if (calId != -1L) EventStore.updateCalendarId(this, localId, calId)
        }

        EventsWidget.update(this)

        val icon = if (parsed.rrule != null) "🔁" else if (immediate) "✅" else "⏰"
        val suffix = when {
            immediate            -> ""
            parsed.rrule != null -> ""
            else -> {
                val eCal = java.util.Calendar.getInstance().apply { timeInMillis = event.startMs }
                val today = java.util.Calendar.getInstance()
                val tomorrow = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
                val sameYear = eCal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)
                val dayStr = when {
                    sameYear && eCal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)    -> "сьогодні"
                    sameYear && eCal.get(java.util.Calendar.DAY_OF_YEAR) == tomorrow.get(java.util.Calendar.DAY_OF_YEAR) -> "завтра"
                    else -> java.text.SimpleDateFormat("dd.MM", java.util.Locale.getDefault()).format(java.util.Date(event.startMs))
                }
                val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                " — $dayStr о ${timeFmt.format(java.util.Date(event.startMs))}"
            }
        }
        Toast.makeText(this, "$icon «${parsed.title}»$suffix", Toast.LENGTH_SHORT).show()
    }

    private fun calendarExportEnabled(): Boolean {
        val prefs = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(MainActivity.KEY_CALENDAR_EXPORT, false) && isUnlocked(this)
    }

    private fun hasCalendarPerms(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)  == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
}
