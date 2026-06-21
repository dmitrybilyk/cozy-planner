package com.reminderwidget

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*

class LocationManagerActivity : Activity() {

    companion object {
        private const val RC_PICK = 1
    }

    private lateinit var listContainer: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun roundedBg(color: Int, r: Float) = GradientDrawable().apply { cornerRadius = dp(r.toInt()).toFloat(); setColor(color) }

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D0D0D.toInt())
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF111111.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        header.addView(TextView(this).apply {
            text = "📍 Менеджер локацій"; textSize = 16f; setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = "✕"; textSize = 20f; setTextColor(0xFF888888.toInt())
            setPadding(dp(8), dp(4), dp(4), dp(4))
            setOnClickListener { finish() }
        })
        root.addView(header)

        // Add button
        root.addView(TextView(this).apply {
            text = "+ Додати нову локацію"; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0xFF1B5E20.toInt()) }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
                it.setMargins(dp(16), dp(12), dp(16), dp(4))
            }
            setOnClickListener { startActivityForResult(Intent(this@LocationManagerActivity, LocationPickerActivity::class.java), RC_PICK) }
        })

        // List
        val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f) }
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }
        scroll.addView(listContainer)
        root.addView(scroll)

        setContentView(root)
        rebuildList()
    }

    private fun rebuildList() {
        listContainer.removeAllViews()
        val locations = LocationsStore.load(this)
        if (locations.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "Немає збережених локацій.\nДодайте першу ↑"; textSize = 14f
                setTextColor(0xFF555555.toInt()); gravity = Gravity.CENTER
                setPadding(0, dp(32), 0, 0)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            })
            return
        }
        locations.forEach { loc ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply { setColor(0xFF1A1A1A.toInt()); cornerRadius = dp(10).toFloat() }
                setPadding(dp(14), dp(12), dp(8), dp(12))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.bottomMargin = dp(6) }
            }
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            }
            col.addView(TextView(this).apply {
                text = loc.name; textSize = 15f; setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            })
            col.addView(TextView(this).apply {
                text = "%.4f, %.4f".format(loc.lat, loc.lng); textSize = 11f; setTextColor(0xFF666666.toInt())
            })
            row.addView(col)
            row.addView(TextView(this).apply {
                text = "🗑"; textSize = 18f; setPadding(dp(10), dp(4), dp(4), dp(4))
                setOnClickListener {
                    android.app.AlertDialog.Builder(this@LocationManagerActivity, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle("Видалити «${loc.name}»?")
                        .setPositiveButton("Видалити") { _, _ ->
                            GeofenceManager.unregisterForLocation(this@LocationManagerActivity, loc.name)
                            EventStore.clearLocation(this@LocationManagerActivity, loc.name)
                            LocationsStore.remove(this@LocationManagerActivity, loc.name)
                            rebuildList()
                            setResult(RESULT_OK)
                        }
                        .setNegativeButton("Скасувати", null).show()
                }
            })
            listContainer.addView(row)
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        if (rc == RC_PICK && res == RESULT_OK && data != null) {
            val name = data.getStringExtra(LocationPickerActivity.EXTRA_NAME) ?: return
            val lat  = data.getDoubleExtra(LocationPickerActivity.EXTRA_LAT,  0.0)
            val lng  = data.getDoubleExtra(LocationPickerActivity.EXTRA_LNG,  0.0)
            LocationsStore.add(this, LocationsStore.AppLocation(name, lat, lng))
            rebuildList()
            setResult(RESULT_OK)
        }
    }
}
