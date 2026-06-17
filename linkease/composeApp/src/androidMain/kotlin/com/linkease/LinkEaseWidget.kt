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
import androidx.glance.LocalSize
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
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentWidth
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.linkease.db.AndroidAvailabilityRepository
import com.linkease.db.AndroidClientRepository
import com.linkease.db.AndroidLocationRepository
import com.linkease.db.AndroidSessionRepository
import com.linkease.db.LinkDatabaseHelper
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class LinkEaseWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 90.dp),   // compact: 2×2
            DpSize(220.dp, 110.dp),  // medium:  4×2
            DpSize(220.dp, 240.dp),  // tall:    4×4+
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { SessionWidgetContent(context) }
    }
}

@Composable
private fun SessionWidgetContent(context: Context) {
    val prefs     = currentState<Preferences>()
    val dayOffset = prefs[DAY_OFFSET_KEY] ?: 0
    val size      = LocalSize.current
    val isCompact = size.width < 160.dp
    val isTall    = size.height >= 200.dp

    val tz    = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val date  = today.plus(dayOffset, DateTimeUnit.DAY)

    val sharedPrefs    = context.getSharedPreferences("linkease_prefs", Context.MODE_PRIVATE)
    val workHoursStart = sharedPrefs.getInt("work_hours_start", CALENDAR_HOURS_START)
    val workHoursEnd   = sharedPrefs.getInt("work_hours_end",   CALENDAR_HOURS_END)

    val db          = LinkDatabaseHelper(context)
    val sessions    = AndroidSessionRepository(db).getAll()
        .filter { it.date == date }.sortedBy { it.startTime }
    val clientsById = AndroidClientRepository(db).getAll().associateBy { it.id }
    val locsById    = AndroidLocationRepository(db).getAll().associateBy { it.id }
    val freeSlots   = if (isTall) {
        val avail = AndroidAvailabilityRepository(db).getAll()
        calculateFreeSlots(sessions, avail, date, workHoursStart, workHoursEnd)
    } else emptyList()

    val dayName   = W_DAYS[date.dayOfWeek.ordinal]
    val monthName = W_MONTHS[date.monthNumber - 1]
    val dateLabel = if (isCompact) "${date.dayOfMonth} $monthName"
                    else "$dayName, ${date.dayOfMonth} $monthName"

    val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val createIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("action", "create_session")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val createClientIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("action", "create_client")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val freeTimeIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("action", "show_free_time")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val availabilityIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("action", "show_availability")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    val prevAction = actionRunCallback<ChangeDayAction>(
        actionParametersOf(DELTA_PARAM to -1, WIDGET_TYPE_PARAM to "sessions")
    )
    val nextAction = actionRunCallback<ChangeDayAction>(
        actionParametersOf(DELTA_PARAM to +1, WIDGET_TYPE_PARAM to "sessions")
    )

    Column(
        modifier = GlanceModifier.fillMaxSize().background(Color(0xFF1A237E)).padding(8.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .wrapContentWidth()
                    .clickable(prevAction)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) { Text("◀", style = TextStyle(color = C_W, fontSize = 14.sp)) }

            Spacer(GlanceModifier.defaultWeight())

            Text(
                "📅 $dateLabel",
                style = TextStyle(color = C_W, fontSize = if (isCompact) 11.sp else 12.sp, fontWeight = FontWeight.Bold)
            )

            Spacer(GlanceModifier.defaultWeight())

            Box(
                modifier = GlanceModifier
                    .wrapContentWidth()
                    .clickable(nextAction)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) { Text("▶", style = TextStyle(color = C_W, fontSize = 14.sp)) }
        }

        Spacer(GlanceModifier.height(4.dp))

        if (isTall) {
            // ── Sessions (capped at 4 rows) ───────────────────────────────
            if (sessions.isEmpty()) {
                Text("Немає занять", style = TextStyle(color = C_DIM, fontSize = 11.sp))
            } else {
                sessions.take(4).forEach { s ->
                    val hh    = s.startTime.hour.toString().padStart(2, '0')
                    val mm    = s.startTime.minute.toString().padStart(2, '0')
                    val names = s.clientIds.mapNotNull { clientsById[it]?.name }.joinToString(", ")
                    val loc   = s.locationId?.let { locsById[it]?.name }
                    Box(
                        modifier = GlanceModifier.fillMaxWidth()
                            .padding(vertical = 1.dp)
                            .clickable(actionStartActivity(openIntent))
                    ) {
                        Text(
                            buildString {
                                append("$hh:$mm")
                                if (names.isNotEmpty()) append("  $names")
                                if (!loc.isNullOrEmpty()) append(" · $loc")
                            },
                            style = TextStyle(color = C_W, fontSize = 11.sp),
                        )
                    }
                }
                if (sessions.size > 4) {
                    Text("+ ${sessions.size - 4} ще", style = TextStyle(color = C_DIM, fontSize = 10.sp))
                }
            }

            Spacer(GlanceModifier.height(6.dp))

            // ── Free time ─────────────────────────────────────────────────
            Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(Color(0x40FFFFFF))) {}
            Spacer(GlanceModifier.height(4.dp))
            Text("⏰ Вільний час", style = TextStyle(color = C_DIM, fontSize = 10.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(2.dp))
            if (freeSlots.isEmpty()) {
                Text("Немає вільного часу", style = TextStyle(color = C_DIM, fontSize = 11.sp))
            } else {
                freeSlots.take(3).forEach { slot ->
                    val sh = slot.startTime.hour.toString().padStart(2, '0')
                    val sm = slot.startTime.minute.toString().padStart(2, '0')
                    val eh = slot.endTime.hour.toString().padStart(2, '0')
                    val em = slot.endTime.minute.toString().padStart(2, '0')
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("$sh:$sm – $eh:$em", style = TextStyle(color = C_W, fontSize = 11.sp))
                        Spacer(GlanceModifier.defaultWeight())
                        Box(
                            modifier = GlanceModifier
                                .wrapContentWidth()
                                .background(Color(0xFF3949AB))
                                .cornerRadius(4.dp)
                                .clickable(actionStartActivity(createIntent))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("+", style = TextStyle(color = C_W, fontSize = 11.sp, fontWeight = FontWeight.Bold)) }
                    }
                }
            }

            Spacer(GlanceModifier.defaultWeight())

            // ── Action buttons ────────────────────────────────────────────
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .background(Color(0xFF3949AB))
                        .cornerRadius(6.dp)
                        .clickable(actionStartActivity(createIntent))
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) { Text("+ Заняття", style = TextStyle(color = C_W, fontSize = 11.sp, fontWeight = FontWeight.Bold)) }

                Spacer(GlanceModifier.width(4.dp))

                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .background(Color(0xFF006064))
                        .cornerRadius(6.dp)
                        .clickable(actionStartActivity(freeTimeIntent))
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) { Text("🕐 Вільний", style = TextStyle(color = C_W, fontSize = 10.sp, fontWeight = FontWeight.Bold)) }

                Spacer(GlanceModifier.width(4.dp))

                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .background(Color(0xFF4A148C))
                        .cornerRadius(6.dp)
                        .clickable(actionStartActivity(availabilityIntent))
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) { Text("📅 Доступ.", style = TextStyle(color = C_W, fontSize = 10.sp, fontWeight = FontWeight.Bold)) }
            }

        } else if (!isCompact) {
            // ── Medium: session list + action buttons ─────────────────────
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                if (sessions.isEmpty()) {
                    item {
                        Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Немає занять", style = TextStyle(color = C_DIM, fontSize = 12.sp))
                        }
                    }
                } else {
                    items(sessions.size, itemId = { sessions[it].id }) { i ->
                        val s     = sessions[i]
                        val hh    = s.startTime.hour.toString().padStart(2, '0')
                        val mm    = s.startTime.minute.toString().padStart(2, '0')
                        val names = s.clientIds.mapNotNull { clientsById[it]?.name }.joinToString(", ")
                        val loc   = s.locationId?.let { locsById[it]?.name }
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
                                .clickable(actionStartActivity(openIntent))
                        ) {
                            Text(
                                buildString {
                                    append("$hh:$mm")
                                    if (names.isNotEmpty()) append("  $names")
                                    if (!loc.isNullOrEmpty()) append(" · $loc")
                                },
                                style = TextStyle(color = C_W, fontSize = 11.sp),
                            )
                        }
                    }
                }
            }

            Spacer(GlanceModifier.height(4.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .background(Color(0xFF3949AB))
                        .cornerRadius(6.dp)
                        .clickable(actionStartActivity(createIntent))
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) { Text("+ Заняття", style = TextStyle(color = C_W, fontSize = 11.sp, fontWeight = FontWeight.Bold)) }

                Spacer(GlanceModifier.width(4.dp))

                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .background(Color(0xFF006064))
                        .cornerRadius(6.dp)
                        .clickable(actionStartActivity(freeTimeIntent))
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) { Text("🕐 Вільний", style = TextStyle(color = C_W, fontSize = 10.sp, fontWeight = FontWeight.Bold)) }

                Spacer(GlanceModifier.width(4.dp))

                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .background(Color(0xFF4A148C))
                        .cornerRadius(6.dp)
                        .clickable(actionStartActivity(availabilityIntent))
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) { Text("📅 Доступ.", style = TextStyle(color = C_W, fontSize = 10.sp, fontWeight = FontWeight.Bold)) }
            }

        } else {
            // ── Compact: session list only ────────────────────────────────
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                if (sessions.isEmpty()) {
                    item {
                        Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Немає занять", style = TextStyle(color = C_DIM, fontSize = 11.sp))
                        }
                    }
                } else {
                    items(sessions.size, itemId = { sessions[it].id }) { i ->
                        val s     = sessions[i]
                        val hh    = s.startTime.hour.toString().padStart(2, '0')
                        val mm    = s.startTime.minute.toString().padStart(2, '0')
                        val names = s.clientIds.mapNotNull { clientsById[it]?.name }.joinToString(", ")
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
                                .clickable(actionStartActivity(openIntent))
                        ) {
                            Text(
                                buildString {
                                    append("$hh:$mm")
                                    if (names.isNotEmpty()) append(" $names")
                                },
                                style = TextStyle(color = C_W, fontSize = 11.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val W_DAYS   = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд")
private val W_MONTHS = arrayOf("січ", "лют", "бер", "квіт", "трав", "черв", "лип", "серп", "вер", "жовт", "лист", "груд")
internal val C_W   = ColorProvider(Color.White)
internal val C_DIM = ColorProvider(Color(0xCCFFFFFF.toInt()))
