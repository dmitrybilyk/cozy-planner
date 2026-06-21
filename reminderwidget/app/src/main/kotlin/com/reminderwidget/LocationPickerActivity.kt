package com.reminderwidget

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class LocationPickerActivity : Activity() {

    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_LAT  = "lat"
        const val EXTRA_LNG  = "lng"
        const val EDIT_NAME  = "edit_name"  // pre-fill name when editing
    }

    private var selectedLat = 50.4501
    private var selectedLng = 30.5234
    private lateinit var webView: WebView
    private lateinit var nameEdit: EditText
    private lateinit var coordsLabel: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D0D0D.toInt())
        }

        // top bar: title + name input
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF111111.toInt())
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        topBar.addView(TextView(this).apply {
            text = "Оберіть точку на карті"; textSize = 11f; setTextColor(0xFF888888.toInt())
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.bottomMargin = dp(6) }
        })
        nameEdit = EditText(this).apply {
            hint = "Назва місця"; textSize = 15f; setTextColor(Color.WHITE); setHintTextColor(0xFF555555.toInt())
            setText(intent.getStringExtra(EDIT_NAME) ?: "")
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0xFF252525.toInt()); setStroke(dp(1), 0xFF333333.toInt()) }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.bottomMargin = dp(6) }
        }
        topBar.addView(nameEdit)
        coordsLabel = TextView(this).apply {
            text = "Перемістіть карту до потрібного місця"; textSize = 11f; setTextColor(0xFF888888.toInt())
        }
        topBar.addView(coordsLabel)
        root.addView(topBar)

        // "Use current location" button
        root.addView(TextView(this).apply {
            text = "🎯  Використати поточне місце"; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(0xFF1565C0.toInt()) }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setOnClickListener { useCurrentLocation() }
        })

        // map
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            webViewClient = WebViewClient()
            addJavascriptInterface(JsBridge(), "Android")
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        root.addView(webView)
        webView.loadUrl("file:///android_asset/location_picker.html")

        // bottom buttons
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF111111.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        bottomRow.addView(Button(this).apply {
            text = "Скасувати"; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0xFF333333.toInt()) }
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).also { it.marginEnd = dp(6) }
            setOnClickListener { finish() }
        })
        bottomRow.addView(Button(this).apply {
            text = "Зберегти"; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0xFF1B5E20.toInt()) }
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).also { it.marginStart = dp(6) }
            setOnClickListener { save() }
        })
        root.addView(bottomRow)
        setContentView(root)
    }

    inner class JsBridge {
        @JavascriptInterface
        fun onLocationSelected(lat: Double, lng: Double) {
            selectedLat = lat; selectedLng = lng
            runOnUiThread {
                coordsLabel.text = "%.5f, %.5f".format(lat, lng)
            }
        }
    }

    private fun save() {
        val name = nameEdit.text.toString().trim()
        if (name.isBlank()) { Toast.makeText(this, "Введіть назву місця", Toast.LENGTH_SHORT).show(); return }
        setResult(RESULT_OK, Intent().apply {
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_LAT,  selectedLat)
            putExtra(EXTRA_LNG,  selectedLng)
        })
        finish()
    }

    private fun useCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }
        val client = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
        try {
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    selectedLat = loc.latitude; selectedLng = loc.longitude
                    runOnUiThread {
                        coordsLabel.text = "%.5f, %.5f".format(loc.latitude, loc.longitude)
                        webView.evaluateJavascript(
                            "map.setView([${loc.latitude},${loc.longitude}],17);", null
                        )
                    }
                } else Toast.makeText(this, "GPS не знайдено. Спробуйте ще раз.", Toast.LENGTH_SHORT).show()
            }
        } catch (_: SecurityException) {}
    }

    override fun onRequestPermissionsResult(rc: Int, perms: Array<String>, results: IntArray) {
        if (rc == 1 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) useCurrentLocation()
    }
}
