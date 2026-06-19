package com.linkease

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.linkease.db.AndroidClientRepository
import com.linkease.db.AndroidLocationRepository
import com.linkease.db.AndroidSessionRepository
import com.linkease.db.LinkDatabaseHelper
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class NextSessionWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(140.dp, 90.dp), DpSize(250.dp, 90.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = LinkDatabaseHelper(context)
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(tz)
        val today = now.date
        val nowTime = now.time

        val sessions = AndroidSessionRepository(db).getAll()
        val clientsById = AndroidClientRepository(db).getAll().associateBy { it.id }
        val locsById = AndroidLocationRepository(db).getAll().associateBy { it.id }

        val next = sessions
            .filter { s -> s.date > today || (s.date == today && s.startTime > nowTime) }
            .minByOrNull { s -> s.date.toEpochDays().toLong() * 10000L + s.startTime.toMinutes().toLong() }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF0D1B2A))
                    .clickable(actionStartActivity(openIntent))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "⏰ Наступне заняття",
                    style = TextStyle(color = ColorProvider(Color(0x99FFFFFF.toInt())), fontSize = 10.sp)
                )
                Spacer(GlanceModifier.height(4.dp))
                if (next == null) {
                    Text(
                        "Занять не заплановано",
                        style = TextStyle(color = C_W, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                } else {
                    val dayDiff = next.date.toEpochDays() - today.toEpochDays()
                    val dayLabel = when {
                        dayDiff == 0 -> "Сьогодні"
                        dayDiff == 1 -> "Завтра"
                        else -> "${next.date.dayOfMonth} ${NS_MONTHS[next.date.monthNumber - 1]}"
                    }
                    val timeLabel = "${next.startTime.toStorageString()}–${next.endTime.toStorageString()}"
                    val names = next.clientIds.mapNotNull { clientsById[it]?.name }.joinToString(", ")
                    val locName = next.locationId?.let { locsById[it]?.name }

                    Text(
                        "$dayLabel  $timeLabel",
                        style = TextStyle(color = C_W, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                    if (names.isNotEmpty()) {
                        Spacer(GlanceModifier.height(2.dp))
                        Text(names, style = TextStyle(color = ColorProvider(Color(0xCCFFFFFF.toInt())), fontSize = 12.sp))
                    }
                    if (locName != null) {
                        Text(locName, style = TextStyle(color = ColorProvider(Color(0x99FFFFFF.toInt())), fontSize = 11.sp))
                    }
                }
            }
        }
    }
}

class NextSessionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NextSessionWidget()
}

private val NS_MONTHS = arrayOf("Січ", "Лют", "Бер", "Кві", "Тра", "Чер", "Лип", "Сер", "Вер", "Жов", "Лис", "Гру")
