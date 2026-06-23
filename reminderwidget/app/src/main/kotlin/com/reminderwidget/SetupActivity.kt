package com.reminderwidget

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SetupActivity : Activity() {

    private val RC_NOTIFICATIONS = 401
    private lateinit var rows: List<PermRow>

    private inner class PermRow(
        val title: String,
        val desc: String,
        val isGranted: () -> Boolean,
        val request: () -> Unit,
    ) {
        lateinit var statusIcon: TextView
        lateinit var actionBtn: Button
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rows = buildRows()
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        rows.forEach { refreshRow(it) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        rows.forEach { refreshRow(it) }
    }

    private fun buildRows(): List<PermRow> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(PermRow(
                title = "Сповіщення",
                desc  = "Показ нагадувань і будильників",
                isGranted = {
                    ContextCompat.checkSelfPermission(this@SetupActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                },
                request = {
                    ActivityCompat.requestPermissions(this@SetupActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), RC_NOTIFICATIONS)
                }
            ))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(PermRow(
                title = "Точні будильники",
                desc  = "Спрацьовування в потрібний час, навіть у режимі сну",
                isGranted = {
                    (getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms()
                },
                request = {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                }
            ))
        }

        add(PermRow(
            title = "Робота у фоні",
            desc  = "Щоб телефон не вбивав додаток і не затримував нагадування",
            isGranted = {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(packageName)
            },
            request = {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        ))

        add(PermRow(
            title = "Автозапуск",
            desc  = "Для відновлення нагадувань після перезавантаження телефону",
            isGranted = { false },
            request   = { showAutoStartDialog() }
        ))

        add(PermRow(
            title = "Мікрофон-віджет",
            desc  = "Додайте віджет на екран для швидкого голосового введення нагадувань",
            isGranted = {
                AppWidgetManager.getInstance(this@SetupActivity)
                    .getAppWidgetIds(ComponentName(this@SetupActivity, ReminderWidget::class.java))
                    .isNotEmpty()
            },
            request = { pinWidget() }
        ))
    }

    private fun pinWidget() {
        val mgr = AppWidgetManager.getInstance(this)
        val existing = mgr.getAppWidgetIds(ComponentName(this, ReminderWidget::class.java))
        if (existing.isNotEmpty()) {
            Toast.makeText(this, "✅ Віджет вже є на екрані", Toast.LENGTH_SHORT).show()
            rows.forEach { refreshRow(it) }
            return
        }
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, ReminderWidget::class.java), null, null)
        } else {
            Toast.makeText(this, "Додайте вручну через «Віджети» на головному екрані", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildUi(): View {
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val bgColor     = if (isDark) Color.parseColor("#1C1C1E") else Color.parseColor("#F2F2F7")
        val cardColor   = if (isDark) Color.parseColor("#2C2C2E") else Color.WHITE
        val textColor   = if (isDark) Color.WHITE else Color.BLACK
        val subColor    = if (isDark) Color.parseColor("#AEAEB2") else Color.parseColor("#6C6C70")
        val accentColor = 0xFFFF5722.toInt()

        val root = ScrollView(this).apply { setBackgroundColor(bgColor) }
        val col  = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        root.addView(col)

        col.addView(TextView(this).apply {
            text = "Налаштування"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            setPadding(dp(4), 0, 0, dp(4))
        })
        col.addView(TextView(this).apply {
            text = "Надайте необхідні дозволи, щоб нагадування працювали надійно"
            textSize = 14f
            setTextColor(subColor)
            setPadding(dp(4), 0, 0, dp(20))
        })

        for (row in rows) {
            val cardView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(cardColor)
                    cornerRadius = dp(14).toFloat()
                }
                setPadding(dp(16), dp(14), dp(16), dp(14))
                val lp = LinearLayout.LayoutParams(-1, -2)
                lp.bottomMargin = dp(12)
                layoutParams = lp
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val titleView = TextView(this).apply {
                text = row.title
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }

            row.statusIcon = TextView(this).apply {
                textSize = 20f
                setPadding(dp(8), 0, 0, 0)
            }

            topRow.addView(titleView)
            topRow.addView(row.statusIcon)

            val descView = TextView(this).apply {
                text = row.desc
                textSize = 13f
                setTextColor(subColor)
                setPadding(0, dp(4), 0, dp(12))
            }

            row.actionBtn = Button(this).apply {
                textSize = 13f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(accentColor)
                    cornerRadius = dp(8).toFloat()
                }
                val lp = LinearLayout.LayoutParams(-2, dp(38))
                lp.gravity = android.view.Gravity.END
                layoutParams = lp
                setOnClickListener { row.request() }
            }

            cardView.addView(topRow)
            cardView.addView(descView)
            cardView.addView(row.actionBtn)
            col.addView(cardView)
        }

        col.addView(Button(this).apply {
            text = "Готово"
            textSize = 16f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = dp(12).toFloat()
            }
            val lp = LinearLayout.LayoutParams(-1, dp(52))
            lp.topMargin = dp(8)
            layoutParams = lp
            setOnClickListener { finish() }
        })

        return root
    }

    private fun refreshRow(row: PermRow) {
        if (row.title == "Автозапуск") {
            row.statusIcon.text = "⚙️"
            row.actionBtn.setText("Відкрити налаштування")
            row.actionBtn.isEnabled = true
            row.actionBtn.alpha = 1f
            return
        }
        val granted = row.isGranted()
        row.statusIcon.text = if (granted) "✅" else "⚠️"
        row.actionBtn.setText(when {
            row.title == "Мікрофон-віджет" && granted -> "Додано ✓"
            row.title == "Мікрофон-віджет"            -> "Додати на екран"
            granted                                   -> "Надано"
            else                                      -> "Дозволити"
        })
        row.actionBtn.isEnabled = !granted
        row.actionBtn.alpha = if (granted) 0.4f else 1f
    }

    private fun showAutoStartDialog() {
        AlertDialog.Builder(this)
            .setTitle("Автозапуск")
            .setMessage(
                "Щоб нагадування відновлювались після перезавантаження, потрібно вручну увімкнути автозапуск:\n\n" +
                "Xiaomi / MIUI:\nНалаштування → Додатки → Remindly → Автозапуск → Увімкнути\n\n" +
                "Blackview:\nНалаштування → Батарея → Автозапуск → Remindly → Увімкнути"
            )
            .setPositiveButton("Відкрити налаштування") { _, _ ->
                try {
                    startActivity(Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                        setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                    })
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    } catch (_: Exception) {}
                }
            }
            .setNegativeButton("Зрозуміло", null)
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
