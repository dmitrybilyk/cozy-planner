package com.linkease

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.wrapContentWidth
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.linkease.db.AndroidAvailabilityRepository
import com.linkease.db.AndroidSessionRepository
import com.linkease.db.LinkDatabaseHelper
import kotlinx.datetime.*

class FreeTimeWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(220.dp, 110.dp),
            DpSize(220.dp, 240.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { FreeTimeWidgetContent(context) }
    }
}

private val C_BG_FT  = ColorProvider(Color(0xFF1B5E20))   // forest green
private val C_BTN_FT = ColorProvider(Color(0xFF2E7D32))
private val W_DAYS_FT   = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд")
private val W_MONTHS_FT = arrayOf("січ", "лют", "бер", "квіт", "трав", "черв", "лип", "серп", "вер", "жовт", "лист", "груд")

@Composable
private fun FreeTimeWidgetContent(context: Context) {
    val prefs     = currentState<Preferences>()
    val dayOffset = prefs[DAY_OFFSET_KEY] ?: 0

    val tz    = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val date  = today.plus(dayOffset, DateTimeUnit.DAY)

    val db         = LinkDatabaseHelper(context)
    val sessions   = AndroidSessionRepository(db).getAll().filter { it.date == date }
    val avail      = AndroidAvailabilityRepository(db).getAll()
    val freeSlots  = calculateFreeSlots(sessions, avail, date)

    val dayName   = W_DAYS_FT[date.dayOfWeek.ordinal]
    val monthName = W_MONTHS_FT[date.monthNumber - 1]
    val dateLabel = "$dayName, ${date.dayOfMonth} $monthName"

    val prevAction = actionRunCallback<ChangeDayAction>(
        actionParametersOf(DELTA_PARAM to -1, WIDGET_TYPE_PARAM to "freetime")
    )
    val nextAction = actionRunCallback<ChangeDayAction>(
        actionParametersOf(DELTA_PARAM to +1, WIDGET_TYPE_PARAM to "freetime")
    )

    Column(
        modifier = GlanceModifier.fillMaxSize().background(Color(0xFF1B5E20)).padding(8.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier.wrapContentWidth().clickable(prevAction).padding(horizontal = 4.dp, vertical = 2.dp)
            ) { Text("◀", style = TextStyle(color = C_W, fontSize = 14.sp)) }

            Spacer(GlanceModifier.defaultWeight())

            Text(
                "⏰ $dateLabel",
                style = TextStyle(color = C_W, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )

            Spacer(GlanceModifier.defaultWeight())

            Box(
                modifier = GlanceModifier.wrapContentWidth().clickable(nextAction).padding(horizontal = 4.dp, vertical = 2.dp)
            ) { Text("▶", style = TextStyle(color = C_W, fontSize = 14.sp)) }
        }

        Spacer(GlanceModifier.height(4.dp))

        // ── Free slots ────────────────────────────────────────────────────
        if (freeSlots.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentAlignment = Alignment.Center
            ) {
                Text("Немає вільного часу", style = TextStyle(color = C_DIM, fontSize = 12.sp))
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(freeSlots.size) { i ->
                    val slot = freeSlots[i]
                    val startHH = slot.startTime.hour.toString().padStart(2, '0')
                    val startMM = slot.startTime.minute.toString().padStart(2, '0')
                    val endHH   = slot.endTime.hour.toString().padStart(2, '0')
                    val endMM   = slot.endTime.minute.toString().padStart(2, '0')

                    val createIntent = Intent(context, MainActivity::class.java).apply {
                        putExtra("action", "create_session")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }

                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$startHH:$startMM – $endHH:$endMM",
                            style = TextStyle(color = C_W, fontSize = 12.sp),
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        Box(
                            modifier = GlanceModifier
                                .wrapContentWidth()
                                .background(Color(0xFF2E7D32))
                                .cornerRadius(4.dp)
                                .clickable(actionStartActivity(createIntent))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("+", style = TextStyle(color = C_W, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}
