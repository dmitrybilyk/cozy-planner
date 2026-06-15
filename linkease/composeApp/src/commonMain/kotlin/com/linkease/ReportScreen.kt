package com.linkease

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.*

private data class ClientReport(
    val client: Client,
    val sessionCount: Int,
    val totalMinutes: Int,
    val revenue: Double,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    sessions: List<Session>,
    clients: List<Client>,
    onBack: () -> Unit,
) {
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    var reportMonth by remember { mutableStateOf(LocalDate(today.year, today.month, 1)) }

    val monthSessions = remember(sessions, reportMonth) {
        sessions.filter { it.date.year == reportMonth.year && it.date.month == reportMonth.month }
    }

    val clientReports = remember(monthSessions, clients) {
        clients.mapNotNull { client ->
            val cs = monthSessions.filter { client.id in it.clientIds }
            if (cs.isEmpty()) return@mapNotNull null
            val mins = cs.sumOf { it.endTime.toMinutes() - it.startTime.toMinutes() }
            val revenue = if (client.hourlyRate > 0) mins / 60.0 * client.hourlyRate else 0.0
            ClientReport(client, cs.size, mins, revenue)
        }.sortedByDescending { it.sessionCount }
    }

    val totalSessions = clientReports.sumOf { it.sessionCount }
    val totalMinutes = clientReports.sumOf { it.totalMinutes }
    val totalRevenue = clientReports.sumOf { it.revenue }
    val hasRevenue = clients.any { it.hourlyRate > 0 }

    val monthLabel = "${MONTHS_UK_FULL[reportMonth.month.ordinal]} ${reportMonth.year}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Звіт", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("◀ Назад") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Month navigation
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = {
                        reportMonth = LocalDate(reportMonth.year, reportMonth.month, 1)
                            .minus(1, DateTimeUnit.MONTH).let { LocalDate(it.year, it.month, 1) }
                    }) { Text("◀", fontSize = 18.sp) }
                    Text(monthLabel, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    IconButton(onClick = {
                        reportMonth = LocalDate(reportMonth.year, reportMonth.month, 1)
                            .plus(1, DateTimeUnit.MONTH).let { LocalDate(it.year, it.month, 1) }
                    }) { Text("▶", fontSize = 18.sp) }
                }
            }

            // Summary card
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        SummaryCell("Сесій", totalSessions.toString())
                        SummaryCell("Годин", formatDuration(totalMinutes))
                        if (hasRevenue && totalRevenue > 0)
                            SummaryCell("Дохід", "${formatRevenue(totalRevenue)} ₴")
                    }
                }
            }

            if (clientReports.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        Text("Сесій у цьому місяці немає", color = Color.Gray)
                    }
                }
            } else {
                // Header row
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text("Клієнт", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                        Text("Сесій", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                        Text("Час", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(72.dp), textAlign = TextAlign.End)
                        if (hasRevenue) Text("Дохід", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(80.dp), textAlign = TextAlign.End)
                    }
                    HorizontalDivider()
                }

                items(clientReports) { r ->
                    val color = hexToColor(r.client.colorHex)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(26.dp).clip(CircleShape),
                                contentAlignment = Alignment.Center) {
                                Surface(shape = CircleShape, color = color, modifier = Modifier.fillMaxSize()) {}
                                Text(r.client.name.first().uppercaseChar().toString(),
                                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Text(r.client.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Text("${r.sessionCount}", fontSize = 14.sp, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                        Text(formatDuration(r.totalMinutes), fontSize = 13.sp, modifier = Modifier.width(72.dp), textAlign = TextAlign.End, color = Color.Gray)
                        if (hasRevenue) {
                            Text(
                                if (r.revenue > 0) "${formatRevenue(r.revenue)} ₴" else "—",
                                fontSize = 13.sp,
                                modifier = Modifier.width(80.dp),
                                textAlign = TextAlign.End,
                                color = if (r.revenue > 0) MaterialTheme.colorScheme.primary else Color.Gray,
                                fontWeight = if (r.revenue > 0) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

private fun formatRevenue(amount: Double): String = kotlin.math.round(amount).toString()
