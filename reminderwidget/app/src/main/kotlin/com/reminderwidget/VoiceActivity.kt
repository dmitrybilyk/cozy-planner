package com.reminderwidget

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceActivity : Activity() {

    companion object {
        private const val RC_VOICE    = 1001
        private const val RC_CAL_PERM = 1002

        private const val KEY_COUNT_DATE = "count_date"
        private const val KEY_COUNT      = "day_count"
        const val FREE_LIMIT = 3

        fun isUnlocked(context: Context): Boolean = true

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

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
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
            if (!text.isNullOrBlank()) { handleText(text); return }
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
            createEventAsync(text, calExport && hasCalendarPerms())
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_CAL_PERM) {
            val text = pendingText ?: run { finish(); return }
            pendingText = null
            createEventAsync(text, grantResults.all { it == PackageManager.PERMISSION_GRANTED })
        }
    }

    private fun createEventAsync(text: String, exportToCalendar: Boolean) {
        val nowMs = System.currentTimeMillis()
        val p     = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val tp    = NlpParser.TimePrefs(
            morningHour  = p.getInt(MainActivity.KEY_MORNING_HOUR, 8),
            morningMin   = p.getInt(MainActivity.KEY_MORNING_MIN,  0),
            dayHour      = p.getInt(MainActivity.KEY_DAY_HOUR,    13),
            dayMin       = p.getInt(MainActivity.KEY_DAY_MIN,      0),
            eveningHour  = p.getInt(MainActivity.KEY_EVENING_HOUR, 19),
            eveningMin   = p.getInt(MainActivity.KEY_EVENING_MIN,  0),
        )
        val multiResults = tryMultiDate(text, nowMs, tp)
        if (multiResults != null) {
            multiResults.forEachIndexed { i, parsed -> applyResult(text, parsed, exportToCalendar, idOffset = i.toLong()) }
        } else {
            val parsed = NlpParser.parse(text, nowMs, tp)
            if (parsed.count > 1 && parsed.intervalMs != null) {
                for (i in 0 until parsed.count) {
                    val shifted = parsed.copy(startMs = parsed.startMs + i * parsed.intervalMs, count = 1, intervalMs = null)
                    applyResult(text, shifted, exportToCalendar, idOffset = i.toLong())
                }
            } else {
                applyResult(text, parsed, exportToCalendar)
            }
        }
        finish()
    }

    /** Returns multiple parsed results if the phrase contains distinct date anchors separated by і/та/and, else null. */
    private fun tryMultiDate(text: String, nowMs: Long, tp: NlpParser.TimePrefs): List<NlpParser.Result>? {
        val sepRx = Regex("""\s+(?:і|та|and|або)\s+""", RegexOption.IGNORE_CASE)
        val parts = text.split(sepRx).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val results = parts.map { NlpParser.parse(it, nowMs, tp) }
        val distinctStarts = results.map { it.startMs }.distinct()
        if (distinctStarts.size < 2) return null
        // all distinct times must differ by at least 6 hours (filter out same-day clock noise)
        val sorted = distinctStarts.sorted()
        if (sorted.zipWithNext().any { (a, b) -> b - a < 6 * 3_600_000L }) return null
        // pick the longest title as the common title for all events
        val bestTitle = results.maxByOrNull { it.title.split(Regex("\\s+")).size }?.title ?: return null
        return results.map { it.copy(title = bestTitle) }
    }

    private fun applyResult(originalText: String, parsed: NlpParser.Result, exportToCalendar: Boolean, idOffset: Long = 0L) {
        val prefs         = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val durationMs    = prefs.getLong(MainActivity.KEY_DURATION_MS, 7 * 24 * 60 * 60_000L)
        val color         = prefs.getInt(MainActivity.KEY_EVENT_COLOR, 0)
        val effectiveDur  = parsed.durationOverrideMs ?: durationMs
        val localId       = System.currentTimeMillis() + idOffset

        val event = EventStore.AppEvent(
            id         = localId,
            title      = parsed.title,
            startMs    = parsed.startMs,
            durationMs = effectiveDur,
            rrule      = parsed.rrule,
        )
        EventStore.add(this, event)
        incrementCount(this)

        val notificationsEnabled = prefs.getBoolean(MainActivity.KEY_NOTIFICATIONS_ENABLED, true)

        if (!parsed.hasTime) {
            if (notificationsEnabled)
                NotificationHelper.post(this, event, ongoing = true, silent = true, fullscreen = false, pinned = true)
            if (exportToCalendar) {
                val calId = CalendarHelper.createReminder(this, parsed.title, parsed.startMs, effectiveDur, color, parsed.rrule)
                if (calId != -1L) EventStore.updateCalendarId(this, localId, calId)
            }
            EventsWidget.update(this)
            Toast.makeText(this, "📌 «${parsed.title}»", Toast.LENGTH_SHORT).show()
            return
        }

        val now       = System.currentTimeMillis()
        val immediate = event.startMs <= now + 5_000L
        if (notificationsEnabled) {
            if (immediate) NotificationHelper.post(this, event)
            else           NotificationHelper.scheduleAt(this, event.id, event.startMs)
        }

        if (exportToCalendar) {
            val calId = CalendarHelper.createReminder(this, parsed.title, parsed.startMs, effectiveDur, color, parsed.rrule)
            if (calId != -1L) EventStore.updateCalendarId(this, localId, calId)
        }

        EventsWidget.update(this)

        val icon = if (parsed.rrule != null) "🔁" else if (immediate) "✅" else "⏰"
        val suffix = when {
            immediate || parsed.rrule != null -> ""
            else -> {
                val eCal   = java.util.Calendar.getInstance().apply { timeInMillis = event.startMs }
                val today  = java.util.Calendar.getInstance()
                val tmrw   = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
                val sameYr = eCal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)
                val dayStr = when {
                    sameYr && eCal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR) -> "сьогодні"
                    sameYr && eCal.get(java.util.Calendar.DAY_OF_YEAR) == tmrw.get(java.util.Calendar.DAY_OF_YEAR)  -> "завтра"
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
        return prefs.getBoolean(MainActivity.KEY_CALENDAR_EXPORT, false)
    }

    private fun hasCalendarPerms(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)  == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
}
