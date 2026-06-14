package com.linkease

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

private val DAYS_UK_FULL = listOf("Понеділок", "Вівторок", "Середа", "Четвер", "П'ятниця", "Субота", "Неділя")
private val DAYS_UK_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilityScreen(
    availability: List<AvailabilitySlot>,
    locations: List<Location>,
    onSettingsClick: () -> Unit,
    onSave: (dayOfWeek: Int, start: LocalTime, end: LocalTime, locationId: Long?) -> Unit,
    onUpdate: (AvailabilitySlot) -> Unit,
    onDelete: (Long) -> Unit,
    onShareSchedule: (String) -> Unit,
) {
    var editingSlot by remember { mutableStateOf<AvailabilitySlot?>(null) }
    var addingDow by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мій графік", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onSettingsClick) { Text("⚙", fontSize = 20.sp) } },
                actions = {
                    TextButton(onClick = {
                        onShareSchedule(buildScheduleText(availability, locations))
                    }) { Text("Поділитися") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(7) { i ->
                val dow = i + 1  // 1=Mon … 7=Sun
                val daySlots = availability.filter { it.dayOfWeek == dow }.sortedBy { it.startTime.toMinutes() }
                DayRow(
                    dow = dow,
                    label = DAYS_UK_FULL[i],
                    short = DAYS_UK_SHORT[i],
                    slots = daySlots,
                    locations = locations,
                    onAddSlot = { addingDow = dow },
                    onEditSlot = { editingSlot = it },
                    onDeleteSlot = { onDelete(it.id) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    // Add new slot
    addingDow?.let { dow ->
        AddEditAvailabilityDialog(
            dayLabel = DAYS_UK_FULL[dow - 1],
            locations = locations,
            onDismiss = { addingDow = null },
            onConfirm = { start, end, locationId ->
                onSave(dow, start, end, locationId)
                addingDow = null
            }
        )
    }

    // Edit existing slot
    editingSlot?.let { slot ->
        AddEditAvailabilityDialog(
            dayLabel = DAYS_UK_FULL[slot.dayOfWeek - 1],
            initial = slot,
            locations = locations,
            onDismiss = { editingSlot = null },
            onConfirm = { start, end, locationId ->
                onUpdate(slot.copy(startTime = start, endTime = end, locationId = locationId))
                editingSlot = null
            }
        )
    }
}

@Composable
private fun DayRow(
    dow: Int,
    label: String,
    short: String,
    slots: List<AvailabilitySlot>,
    locations: List<Location>,
    onAddSlot: () -> Unit,
    onEditSlot: (AvailabilitySlot) -> Unit,
    onDeleteSlot: (AvailabilitySlot) -> Unit,
) {
    val locById = locations.associateBy { it.id }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Day label
        Column(modifier = Modifier.width(88.dp)) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            if (slots.isEmpty()) Text("вихідний", fontSize = 12.sp, color = Color.Gray)
        }

        // Slots
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slots.forEach { slot ->
                val loc = slot.locationId?.let { locById[it] }
                val locColor = loc?.let { hexToColor(it.colorHex) } ?: MaterialTheme.colorScheme.primary
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(locColor.copy(alpha = 0.12f))
                        .clickable { onEditSlot(slot) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (loc != null) {
                        Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(locColor))
                    }
                    Text(
                        "${slot.startTime.toStorageString()} – ${slot.endTime.toStorageString()}",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                    if (loc != null) Text(loc.name, fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "✕",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.clickable { onDeleteSlot(slot) }.padding(4.dp)
                    )
                }
            }
            TextButton(
                onClick = onAddSlot,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("+ Додати", fontSize = 13.sp)
            }
        }
    }
}

private fun buildScheduleText(availability: List<AvailabilitySlot>, locations: List<Location>): String {
    val locById = locations.associateBy { it.id }
    return buildString {
        appendLine("📅 Мій графік роботи:")
        appendLine()
        (1..7).forEach { dow ->
            val slots = availability.filter { it.dayOfWeek == dow }.sortedBy { it.startTime.toMinutes() }
            val dayName = DAYS_UK_FULL[dow - 1]
            if (slots.isEmpty()) {
                appendLine("$dayName: вихідний")
            } else {
                slots.forEach { slot ->
                    val loc = slot.locationId?.let { locById[it] }
                    val locPart = if (loc != null) " (${loc.name})" else ""
                    appendLine("$dayName: ${slot.startTime.toStorageString()} – ${slot.endTime.toStorageString()}$locPart")
                }
            }
        }
    }.trim()
}
