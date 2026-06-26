package com.linkease

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.datetime.LocalTime

@Composable
fun AddEditAvailabilityDialog(
    dayLabel: String,
    initial: AvailabilitySlot? = null,
    defaultStartTime: LocalTime = LocalTime(CALENDAR_HOURS_START, 0),
    defaultEndTime: LocalTime = LocalTime(CALENDAR_HOURS_END, 0),
    existingSlotsOnDay: List<AvailabilitySlot> = emptyList(),
    locations: List<Location>,
    hoursStart: Int = CALENDAR_HOURS_START,
    hoursEnd: Int = CALENDAR_HOURS_END,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (start: LocalTime, end: LocalTime, locationId: Long?) -> Unit,
) {
    val otherSlots = remember(existingSlotsOnDay, initial) {
        existingSlotsOnDay.filter { it.id != (initial?.id ?: -1L) }
    }

    var startTime by remember { mutableStateOf(initial?.startTime ?: defaultStartTime) }
    var endTime by remember { mutableStateOf(initial?.endTime ?: defaultEndTime) }
    var selectedLocationId by remember { mutableStateOf(initial?.locationId) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val allDayStart = LocalTime(hoursStart, 0)
    val allDayEnd = LocalTime(hoursEnd, 0)
    var allDay by remember { mutableStateOf(
        initial != null && initial.startTime == allDayStart && initial.endTime == allDayEnd
    ) }

    val startHiddenMinutes = remember(otherSlots) {
        otherSlots.flatMap { slot ->
            (slot.startTime.toMinutes() until slot.endTime.toMinutes()).toList()
        }.toSet()
    }

    val endHiddenMinutes = remember(startTime, otherSlots, hoursStart, hoursEnd) {
        val nextSlot = otherSlots
            .filter { it.startTime.toMinutes() > startTime.toMinutes() }
            .minByOrNull { it.startTime.toMinutes() }
        val ceil = nextSlot?.startTime?.toMinutes() ?: (hoursEnd * 60)
        val blocked = ((ceil + 1)..(hoursEnd * 60 + 59)).toSet()
        val beforeStart = (hoursStart * 60..startTime.toMinutes()).toSet()
        beforeStart + blocked
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dayLabel, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Весь день", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = allDay,
                        onCheckedChange = { checked ->
                            allDay = checked
                            if (checked) { startTime = allDayStart; endTime = allDayEnd }
                        }
                    )
                }
                if (!allDay) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimeChipAvail(label = "Початок", time = startTime, onClick = { showStartPicker = true })
                    Text("–", fontSize = 16.sp)
                    TimeChipAvail(label = "Кінець", time = endTime, onClick = { showEndPicker = true })
                }
                }

                if (locations.isNotEmpty()) {
                    Text("Зал (необов'язково)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    if (selectedLocationId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedLocationId = null }
                                .padding(10.dp)
                        ) {
                            Text("Без прив'язки до залу", fontSize = 13.sp,
                                color = if (selectedLocationId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        locations.forEach { loc ->
                            val selected = loc.id == selectedLocationId
                            val color = hexToColor(loc.colorHex)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .background(if (selected) color.copy(alpha = 0.1f) else Color.Transparent)
                                    .clickable { selectedLocationId = if (selected) null else loc.id }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                                Text(loc.name, fontSize = 13.sp)
                                if (loc.address.isNotBlank()) Text(loc.address, fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (endTime.toMinutes() > startTime.toMinutes()) onConfirm(startTime, endTime, selectedLocationId)
            }) { Text("Зберегти") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Видалити") }
                }
                TextButton(onClick = onDismiss) { Text("Скасувати") }
            }
        }
    )

    if (showStartPicker) {
        TimePickerDialog(
            initial = startTime,
            minHour = hoursStart,
            maxHour = hoursEnd,
            hiddenMinutes = startHiddenMinutes,
            onDismiss = { showStartPicker = false },
            onConfirm = { t ->
                startTime = t
                if (endTime.toMinutes() <= t.toMinutes()) {
                    endTime = minutesToLocalTime((t.toMinutes() + 60).coerceAtMost(hoursEnd * 60))
                }
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initial = endTime,
            minHour = hoursStart,
            maxHour = hoursEnd,
            hiddenMinutes = endHiddenMinutes,
            onDismiss = { showEndPicker = false },
            onConfirm = { endTime = it; showEndPicker = false }
        )
    }
}

@Composable
private fun TimeChipAvail(label: String, time: LocalTime, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.width(100.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
            Text(time.toStorageString(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}
