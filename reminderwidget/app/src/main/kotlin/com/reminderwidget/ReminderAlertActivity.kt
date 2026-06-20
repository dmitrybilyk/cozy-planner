package com.reminderwidget

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

class ReminderAlertActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val eventId = intent.getLongExtra(NotificationHelper.EXTRA_EVENT_ID, -1L)
        val title   = intent.getStringExtra("alert_title") ?: "Нагадування"
        val startMs = intent.getLongExtra("alert_start_ms", System.currentTimeMillis())

        setContentView(buildUi(eventId, title, startMs))

        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#1E1E2E")))
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun buildUi(eventId: Long, title: String, startMs: Long): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        root.addView(tv("🎙 Нагадування", 12f, Color.parseColor("#FF5722"), btm = 10))
        root.addView(tv(title, 20f, Color.WHITE, bold = true, btm = 6))

        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        root.addView(tv(fmt.format(Date(startMs)), 13f, Color.parseColor("#888888"), btm = 28))

        // Done — mark completed, dismiss notification
        root.addView(actionBtn("✅ Виконано", "#4CAF50", "#1B3A1B") {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(NotificationHelper.notifId(eventId))
            EventStore.markCompleted(this, eventId)
            sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
            EventsWidget.update(this)
            finish()
        })
        root.addView(spacer(8))

        // OK — silence the alarm but keep a quiet notification in the status bar
        root.addView(actionBtn("👍 OK — залишити сповіщення", "#37474F", "#1A2A2A") {
            val event = EventStore.load(this).find { it.id == eventId }
            if (event != null) {
                NotificationHelper.post(this, event, silent = true, fullscreen = false)
            }
            finish()
        })
        root.addView(spacer(16))

        root.addView(tv("Відкласти:", 11f, Color.parseColor("#888888"), btm = 8))

        val snoozeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val prefs = getSharedPreferences("widget_settings", MODE_PRIVATE)
        val min1 = prefs.getInt(NotificationHelper.KEY_SNOOZE1_MIN, 1)
        val min2 = prefs.getInt(NotificationHelper.KEY_SNOOZE2_MIN, 30)
        listOf(min1, min2).forEach { mins ->
            val label = if (mins >= 60) "${mins / 60}г${if (mins % 60 != 0) "${mins % 60}'" else ""}" else "$mins хв"
            snoozeRow.addView(snoozeBtn(label) {
                snooze(eventId, mins.toLong())
                finish()
            })
        }
        root.addView(snoozeRow)

        return root
    }

    private fun snooze(eventId: Long, minutes: Long) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NotificationHelper.notifId(eventId))
        val newTime = System.currentTimeMillis() + minutes * 60_000L
        EventStore.updateStartMs(this, eventId, newTime)
        NotificationHelper.scheduleAt(this, eventId, newTime, silent = false, fullscreen = false)
        sendBroadcast(Intent(NotificationHelper.ACTION_LIST_CHANGED))
        EventsWidget.update(this)
    }

    private fun actionBtn(text: String, bg: String, bgDark: String, onClick: () -> Unit) =
        Button(this).apply {
            this.text = text; textSize = 14f; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(bgDark))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
            )
            setOnClickListener { onClick() }
        }

    private fun snoozeBtn(label: String, onClick: () -> Unit) =
        Button(this).apply {
            text = label; textSize = 12f; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#263238"))
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).also {
                it.marginEnd = dp(6)
            }
            setOnClickListener { onClick() }
        }

    private fun tv(text: String, size: Float, color: Int, bold: Boolean = false, btm: Int = 0) =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(btm) }
        }

    private fun spacer(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(h))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}
