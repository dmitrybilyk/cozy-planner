package com.linkease

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

val DAY_OFFSET_KEY   = intPreferencesKey("day_offset")
val DELTA_PARAM      = ActionParameters.Key<Int>("delta")
val WIDGET_TYPE_PARAM = ActionParameters.Key<String>("widget_type")

class ChangeDayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[DELTA_PARAM] ?: 0
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val current = prefs[DAY_OFFSET_KEY] ?: 0
            prefs.toMutablePreferences().also { it[DAY_OFFSET_KEY] = current + delta }
        }
        when (parameters[WIDGET_TYPE_PARAM]) {
            "freetime" -> FreeTimeWidget().update(context, glanceId)
            else       -> LinkEaseWidget().update(context, glanceId)
        }
    }
}
