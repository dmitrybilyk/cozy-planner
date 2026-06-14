package com.linkease

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** Finds first available start time on [date] at or after [desired], skipping occupied sessions. */
fun findNextAvailableStart(
    sessions: List<Session>,
    date: LocalDate,
    desired: LocalTime,
    excludeId: Long = -1L,
): LocalTime {
    val occupied = sessions
        .filter { it.date == date && it.id != excludeId }
        .sortedBy { it.startTime.toMinutes() }
    var candidate = desired.toMinutes()
    var changed = true
    while (changed) {
        changed = false
        for (s in occupied) {
            if (s.startTime.toMinutes() <= candidate && s.endTime.toMinutes() > candidate) {
                candidate = s.endTime.toMinutes()
                changed = true
                break
            }
        }
    }
    return minutesToLocalTime(candidate.coerceAtMost(22 * 60))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSessionDialog(
    // null = create new, non-null = edit existing
    initial: Session? = null,
    defaultDate: LocalDate,
    defaultStartTime: LocalTime = LocalTime(10, 0),
    defaultEndTime: LocalTime? = null,          // used for free-slot chips
    defaultClientIds: List<Long> = emptyList(), // used for copy
    defaultLocationId: Long? = null,            // used for copy
    defaultNotes: String = "",                  // used for copy
    clients: List<Client>,
    locations: List<Location>,
    existingSessions: List<Session>,
    onDismiss: () -> Unit,
    onConfirm: (date: LocalDate, start: LocalTime, end: LocalTime, clientIds: List<Long>, locationId: Long?, notes: String) -> Unit,
) {
    val isCopy = initial == null && defaultClientIds.isNotEmpty()

    var date by remember { mutableStateOf(initial?.date ?: defaultDate) }

    val adjustedStart = remember(date) {
        if (initial != null) initial.startTime
        else findNextAvailableStart(existingSessions, date, defaultStartTime, -1L)
    }
    var startTime by remember { mutableStateOf(adjustedStart) }
    var endTime by remember {
        mutableStateOf(
            initial?.endTime
                ?: defaultEndTime
                ?: minutesToLocalTime((adjustedStart.toMinutes() + 60).coerceAtMost(23 * 60))
        )
    }

    // When user changes date, re-find first free slot — but NOT on first composition (that would
    // override the deliberately clicked time).
    var prevDate by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(date) {
        if (initial == null && prevDate != null && prevDate != date) {
            val newStart = findNextAvailableStart(existingSessions, date, LocalTime(9, 0), -1L)
            startTime = newStart
            endTime = minutesToLocalTime((newStart.toMinutes() + 60).coerceAtMost(23 * 60))
        }
        prevDate = date
    }

    var selectedClientIds by remember {
        mutableStateOf(initial?.clientIds?.toSet() ?: defaultClientIds.toSet())
    }
    var selectedLocationId by remember { mutableStateOf(initial?.locationId ?: defaultLocationId) }
    var notes by remember { mutableStateOf(initial?.notes ?: defaultNotes) }
    var overlapError by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    fun validate(): Boolean {
        if (endTime.toMinutes() <= startTime.toMinutes()) return false
        val conflicts = existingSessions.filter { s ->
            s.id != (initial?.id ?: -1L) && s.date == date &&
            s.startTime.toMinutes() < endTime.toMinutes() &&
            s.endTime.toMinutes() > startTime.toMinutes()
        }
        overlapError = conflicts.isNotEmpty()
        return !overlapError
    }

    val title = when {
        isCopy -> "Копія заняття"
        initial == null -> "Нове заняття"
        else -> "Редагувати заняття"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TimeChip(label = "Початок", time = startTime, onClick = { showStartPicker = true })
                    Text("–")
                    TimeChip(label = "Кінець", time = endTime, onClick = { showEndPicker = true })
                }
                if (overlapError) {
                    Text("⚠ Перетинається з іншим заняттям", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                if (endTime.toMinutes() <= startTime.toMinutes()) {
                    Text("Кінець має бути після початку", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                if (clients.isNotEmpty()) {
                    Text("Клієнти", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color.Gray)
                    clients.forEach { client ->
                        val selected = client.id in selectedClientIds
                        val color = hexToColor(client.colorHex)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.5.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .background(if (selected) color.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable { selectedClientIds = if (selected) selectedClientIds - client.id else selectedClientIds + client.id }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                            Text(client.name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            if (selected) Text("✓", color = color, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (locations.isNotEmpty()) {
                    Text("Зал / Локація", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color.Gray)
                    locations.forEach { loc ->
                        val selected = loc.id == selectedLocationId
                        val color = hexToColor(loc.colorHex)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.5.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .background(if (selected) color.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable { selectedLocationId = if (selected) null else loc.id }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                            Column(Modifier.weight(1f)) {
                                Text(loc.name, fontSize = 14.sp)
                                if (loc.address.isNotBlank()) Text(loc.address, fontSize = 11.sp, color = Color.Gray)
                            }
                            if (selected) Text("✓", color = color, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Нотатки") },
                    modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (validate()) onConfirm(date, startTime, endTime, selectedClientIds.toList(), selectedLocationId, notes) }) {
                Text("Зберегти")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )

    if (showStartPicker) {
        TimePickerDialog(initial = startTime, onDismiss = { showStartPicker = false }, onConfirm = { t ->
            startTime = t
            if (endTime.toMinutes() <= t.toMinutes()) endTime = minutesToLocalTime((t.toMinutes() + 60).coerceAtMost(23 * 60))
            showStartPicker = false
        })
    }
    if (showEndPicker) {
        TimePickerDialog(initial = endTime, onDismiss = { showEndPicker = false }, onConfirm = { endTime = it; showEndPicker = false })
    }
}

@Composable
private fun TimeChip(label: String, time: LocalTime, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.width(100.dp)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
            Text(time.toStorageString(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Оберіть час") },
        text = { Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) { TimePicker(state = state) } },
        confirmButton = { Button(onClick = { onConfirm(LocalTime(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}
