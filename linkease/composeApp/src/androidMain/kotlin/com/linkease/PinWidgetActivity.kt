package com.linkease

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity

class PinWidgetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val provider = if (intent.getStringExtra("widget") == "freetime")
            ComponentName(this, FreeTimeWidgetReceiver::class.java)
        else
            ComponentName(this, LinkEaseWidgetReceiver::class.java)
        val manager = AppWidgetManager.getInstance(this)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(provider, null, null)
        }
        finish()
    }
}
