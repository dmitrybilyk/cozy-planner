package com.linkease

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.*

fun findNextAvailableStart(
    sessions: List<Session>,
    date: LocalDate,
    desired: LocalTime,
    excludeId: Long = -1L,
    workHoursEnd: Int = 22,
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
    return minutesToLocalTime(candidate.coerceAtMost(workHoursEnd * 60))
}

private fun generateSeriesDates(startDate: LocalDate, weekdays: Set<Int>, count: Int): List<LocalDate> {
    if (weekdays.isEmpty() || count <= 0) return emptyList()
    val result = mutableListOf<LocalDate>()
    var current = startDate
    var safety = count * 7 + 7
    while (result.size < count && safety-- > 0) {
        if (current.dayOfWeek.isoDayNumber in weekdays) result.add(current)
        current = current.plus(1, DateTimeUnit.DAY)
    }
    return result
}

private val sectionShape = RoundedCornerShape(12.dp)

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = sectionShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), content = content)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditSessionDialog(
    initial: Session? = null,
    defaultDate: LocalDate,
    defaultStartTime: LocalTime = LocalTime(10, 0),
    defaultEndTime: LocalTime? = null,
    defaultClientIds: List<Long> = emptyList(),
    defaultLocationId: Long? = null,
    defaultNotes: String = "",
    clients: List<Client>,
    locations: List<Location>,
    existingSessions: List<Session>,
    availability: List<AvailabilitySlot> = emptyList(),
    workHoursStart: Int = 7,
    workHoursEnd: Int = 22,
    onCreateClient: ((name: String) -> Client)? = null,
    onDismiss: () -> Unit,
    onConfirm: (date: LocalDate, start: LocalTime, end: LocalTime, clientIds: List<Long>, locationId: Long?, notes: String) -> Unit,
    onConfirmSeries: ((dates: List<LocalDate>, start: LocalTime, end: LocalTime, clientIds: List<Long>, locationId: Long?, notes: String) -> Unit)? = null,
) {
    val isCopy = initial == null && defaultClientIds.isNotEmpty()
    val isEditing = initial != null
    // Series tab only available for new sessions (not edit/copy)
    val showTabs = !isEditing && onConfirmSeries != null

    var selectedTab by remember { mutableStateOf(0) } // 0=single, 1=series

    // ── Single session state ───────────────────
    var date by remember { mutableStateOf(initial?.date ?: defaultDate) }
    val adjustedStart = remember(date) {
        if (initial != null) initial.startTime
        else findNextAvailableStart(existingSessions, date, defaultStartTime, -1L, workHoursEnd)
    }
    var startTime by remember { mutableStateOf(adjustedStart) }
    var endTime by remember {
        mutableStateOf(
            initial?.endTime
                ?: defaultEndTime
                ?: minutesToLocalTime((adjustedStart.toMinutes() + 60).coerceAtMost(workHoursEnd * 60))
        )
    }
    var prevDate by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(date) {
        if (initial == null && prevDate != null && prevDate != date) {
            val newStart = findNextAvailableStart(existingSessions, date, LocalTime(9, 0), -1L, workHoursEnd)
            startTime = newStart
            endTime = minutesToLocalTime((newStart.toMinutes() + 60).coerceAtMost(workHoursEnd * 60))
        }
        prevDate = date
    }

    // ── Series state ───────────────────────────
    var selectedDates by remember { mutableStateOf(setOf(defaultDate)) }
    var seriesCalendarStart by remember {
        mutableStateOf(LocalDate(defaultDate.year, defaultDate.month, 1))
    }

    // ── Shared state ───────────────────────────
    var selectedClientIds by remember {
        mutableStateOf(initial?.clientIds?.toSet() ?: defaultClientIds.toSet())
    }
    var selectedLocationId by remember { mutableStateOf(initial?.locationId ?: defaultLocationId) }
    var notes by remember { mutableStateOf(initial?.notes ?: defaultNotes) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var clientSearch by remember { mutableStateOf("") }
    var recentlyCreatedClientIds by remember { mutableStateOf(setOf<Long>()) }

    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { searchFocus.requestFocus() } catch (_: Exception) {}
    }

    val clientUsage = remember(clients, existingSessions) {
        clients.associate { c -> c.id to existingSessions.count { s -> c.id in s.clientIds } }
    }
    val sortedClients = remember(clients, clientUsage) {
        clients.sortedWith(compareByDescending<Client> { clientUsage[it.id] ?: 0 }.thenBy { it.name })
    }
    val locationUsage = remember(locations, existingSessions) {
        locations.associate { loc -> loc.id to existingSessions.count { s -> s.locationId == loc.id } }
    }
    val sortedLocations = remember(locations, locationUsage) {
        locations.sortedWith(compareByDescending<Location> { locationUsage[it.id] ?: 0 }.thenBy { it.name })
    }

    val displayedClients = remember(clientSearch, sortedClients, recentlyCreatedClientIds) {
        if (clientSearch.isBlank()) {
            val top5 = sortedClients.take(5)
            val recent = sortedClients.filter { it.id in recentlyCreatedClientIds }
            (top5 + recent).distinctBy { it.id }
        } else {
            sortedClients.filter { it.name.contains(clientSearch.trim(), ignoreCase = true) }
        }
    }

    val clientExactMatch = clients.any { it.name.equals(clientSearch.trim(), ignoreCase = true) }
    val canCreateClient = onCreateClient != null && clientSearch.isNotBlank() && !clientExactMatch

    // Reactive: sessions that conflict with the current time window (for single session)
    val overlapError = remember(date, startTime, endTime, existingSessions) {
        endTime.toMinutes() > startTime.toMinutes() && existingSessions.any { s ->
            s.id != (initial?.id ?: -1L) && s.date == date &&
            s.startTime.toMinutes() < endTime.toMinutes() &&
            s.endTime.toMinutes() > startTime.toMinutes()
        }
    }

    // Dates blocked for series due to existing sessions at the same time window
    val conflictDates = remember(startTime, endTime, existingSessions) {
        if (endTime.toMinutes() <= startTime.toMinutes()) emptySet()
        else existingSessions
            .filter { s ->
                s.id != (initial?.id ?: -1L) &&
                s.startTime.toMinutes() < endTime.toMinutes() &&
                s.endTime.toMinutes() > startTime.toMinutes()
            }
            .map { it.date }
            .toSet()
    }

    // Hidden minutes for time pickers: session-occupied + out-of-availability
    // Use step=1 so finer granularity (15/10 min) slots within blocked ranges are also hidden
    val startHiddenMinutes = remember(date, existingSessions, availability, workHoursStart, workHoursEnd) {
        val excludeId = initial?.id ?: -1L
        val sessionOccupied = existingSessions
            .filter { it.date == date && it.id != excludeId }
            .flatMap { s -> (s.startTime.toMinutes() until s.endTime.toMinutes()).toList() }
            .toSet()
        val dayAvail = availability.filter { it.date == date }
        val outOfAvail = if (dayAvail.isEmpty()) emptySet() else {
            val availMinutes = dayAvail.flatMap { slot ->
                (slot.startTime.toMinutes() until slot.endTime.toMinutes()).toList()
            }.toSet()
            ((workHoursStart * 60)..(workHoursEnd * 60))
                .filter { it !in availMinutes }
                .toSet()
        }
        sessionOccupied + outOfAvail
    }

    val endHiddenMinutes = remember(date, startTime, existingSessions, availability, workHoursStart, workHoursEnd) {
        val excludeId = initial?.id ?: -1L
        val otherSessions = existingSessions.filter { it.date == date && it.id != excludeId }
        val nextSession = otherSessions
            .filter { it.startTime.toMinutes() > startTime.toMinutes() }
            .minByOrNull { it.startTime.toMinutes() }
        val sessionCeil = nextSession?.startTime?.toMinutes() ?: (workHoursEnd * 60)
        val dayAvail = availability.filter { it.date == date }
        val containingSlot = dayAvail.firstOrNull {
            it.startTime.toMinutes() <= startTime.toMinutes() && it.endTime.toMinutes() > startTime.toMinutes()
        }
        val availCeil = containingSlot?.endTime?.toMinutes() ?: if (dayAvail.isEmpty()) (workHoursEnd * 60) else 0
        val ceil = if (dayAvail.isEmpty()) sessionCeil else minOf(sessionCeil, availCeil)
        val blocked = ((ceil + 1)..(workHoursEnd * 60 + 59)).toSet()
        val beforeStart = (workHoursStart * 60..startTime.toMinutes()).toSet()
        beforeStart + blocked
    }

    val singleValid = !overlapError && endTime.toMinutes() > startTime.toMinutes()
    val seriesValid = selectedDates.isNotEmpty() &&
                      selectedDates.none { it in conflictDates } &&
                      endTime.toMinutes() > startTime.toMinutes()

    val title = when {
        isCopy -> "Копія сесії"
        isEditing -> "Редагувати сесію"
        else -> "Нова сесія"
    }

    var showClientPicker      by remember { mutableStateOf(false) }
    var showLocationPicker    by remember { mutableStateOf(false) }
    var showNotesPicker       by remember { mutableStateOf(false) }
    var showDatePicker        by remember { mutableStateOf(false) }
    var showSeriesDatePicker  by remember { mutableStateOf(false) }

    val selectedClients = remember(selectedClientIds, clients) {
        clients.filter { it.id in selectedClientIds }
    }
    val selectedLocation = remember(selectedLocationId, locations) {
        locations.find { it.id == selectedLocationId }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 340.dp),
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // ── Tab selector ──────────────────────────────────────────
                if (showTabs) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            label = { Text("Одна сесія", fontSize = 13.sp) })
                        SegmentedButton(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            label = { Text("Серія", fontSize = 13.sp) })
                    }
                }

                // ── Date + time ───────────────────────────────────────────
                SectionCard {
                    if (selectedTab == 0 || !showTabs) {
                        // Date navigation row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = { date = date.minus(1, DateTimeUnit.DAY) }, modifier = Modifier.size(32.dp)) {
                                Text("◀", fontSize = 14.sp)
                            }
                            Text(
                                "${DAYS_UK[date.dayOfWeek.ordinal]}, ${date.dayOfMonth} ${MONTHS_UK_SHORT[date.month.ordinal]} ${date.year}",
                                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { showDatePicker = true }
                            )
                            IconButton(onClick = { date = date.plus(1, DateTimeUnit.DAY) }, modifier = Modifier.size(32.dp)) {
                                Text("▶", fontSize = 14.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    } else {
                        Text(
                            "Серія з ${defaultDate.dayOfMonth} ${MONTHS_UK_SHORT[defaultDate.month.ordinal]} ${defaultDate.year}",
                            fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TimeChip(label = "Початок", time = startTime, onClick = { showStartPicker = true })
                        Text("–", fontSize = 18.sp, color = Color.Gray)
                        TimeChip(label = "Кінець", time = endTime, onClick = { showEndPicker = true })
                    }
                    if (overlapError) Text("⚠ Перетинається з іншою сесією",
                        color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    if (endTime.toMinutes() <= startTime.toMinutes()) Text("Кінець має бути після початку",
                        color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }

                // ── Series dates row (tab 1 only) ─────────────────────────
                if (selectedTab == 1 && showTabs) {
                    val hasConflicts = selectedDates.any { it in conflictDates }
                    val datesLabel = when (selectedDates.size) {
                        0    -> "Оберіть дати"
                        1    -> "1 дата обрана"
                        else -> "${selectedDates.size} дат(и) обрано"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (hasConflicts) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                            .clickable { showSeriesDatePicker = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("📅", fontSize = 16.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(datesLabel, fontSize = 13.sp,
                                color = if (selectedDates.isEmpty()) Color.Gray else MaterialTheme.colorScheme.onSurface)
                            if (hasConflicts) {
                                val cnt = selectedDates.count { it in conflictDates }
                                Text("⚠ $cnt конфліктів", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text("›", fontSize = 16.sp, color = Color.LightGray)
                    }
                }

                // ── Selected summary ──────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Clients summary row
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable { showClientPicker = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("👤", fontSize = 16.sp)
                        if (selectedClients.isEmpty()) {
                            Text("Клієнти", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                        } else {
                            Text(selectedClients.joinToString(", ") { it.name },
                                fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        }
                        Text("›", fontSize = 16.sp, color = Color.LightGray)
                    }

                    // Location row (only if locations exist)
                    if (sortedLocations.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .clickable { showLocationPicker = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🏢", fontSize = 16.sp)
                            Text(selectedLocation?.name ?: "Локація",
                                fontSize = 13.sp, color = if (selectedLocation == null) Color.Gray else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f))
                            Text("›", fontSize = 16.sp, color = Color.LightGray)
                        }
                    }

                    // Notes row
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable { showNotesPicker = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("📝", fontSize = 16.sp)
                        Text(notes.ifBlank { "Нотатки" }, fontSize = 13.sp,
                            color = if (notes.isBlank()) Color.Gray else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f), maxLines = 1)
                        Text("›", fontSize = 16.sp, color = Color.LightGray)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = if (showTabs && selectedTab == 1) seriesValid else singleValid,
                onClick = {
                    if (selectedTab == 1 && showTabs) {
                        val dates = selectedDates.toList().sorted()
                        onConfirmSeries?.invoke(dates, startTime, endTime, selectedClientIds.toList(), selectedLocationId, notes)
                    } else {
                        onConfirm(date, startTime, endTime, selectedClientIds.toList(), selectedLocationId, notes)
                    }
                }
            ) { Text("Зберегти") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )

    // ── Sub-dialogs ───────────────────────────────────────────────────────

    if (showClientPicker) {
        AlertDialog(
            onDismissRequest = { showClientPicker = false },
            modifier = Modifier.widthIn(max = 340.dp),
            title = { Text("Клієнти", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = clientSearch, onValueChange = { clientSearch = it },
                        placeholder = { Text("Пошук…", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                        singleLine = true,
                        trailingIcon = { if (clientSearch.isNotEmpty()) IconButton(onClick = { clientSearch = "" }) { Text("✕", fontSize = 14.sp, color = Color.Gray) } }
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        displayedClients.forEach { client ->
                            val selected = client.id in selectedClientIds
                            SelectableChip(name = client.name, color = hexToColor(client.colorHex), selected = selected,
                                onClick = { selectedClientIds = if (selected) selectedClientIds - client.id else selectedClientIds + client.id })
                        }
                        if (canCreateClient) {
                            CreateChip(name = clientSearch.trim()) {
                                val newClient = onCreateClient!!(clientSearch.trim())
                                selectedClientIds = selectedClientIds + newClient.id
                                recentlyCreatedClientIds = recentlyCreatedClientIds + newClient.id
                                clientSearch = ""
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showClientPicker = false }) { Text("OK") } }
        )
    }

    if (showLocationPicker && sortedLocations.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showLocationPicker = false },
            modifier = Modifier.widthIn(max = 340.dp),
            title = { Text("Локація", fontWeight = FontWeight.SemiBold) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sortedLocations.forEach { loc ->
                        val selected = loc.id == selectedLocationId
                        SelectableChip(name = loc.name, color = hexToColor(loc.colorHex), selected = selected,
                            onClick = { selectedLocationId = if (selected) null else loc.id })
                    }
                }
            },
            confirmButton = { Button(onClick = { showLocationPicker = false }) { Text("OK") } }
        )
    }

    if (showNotesPicker) {
        AlertDialog(
            onDismissRequest = { showNotesPicker = false },
            modifier = Modifier.widthIn(max = 340.dp),
            title = { Text("Нотатки", fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    placeholder = { Text("Додаткова інформація…", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 4
                )
            },
            confirmButton = { Button(onClick = { showNotesPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { notes = ""; showNotesPicker = false }) { Text("Очистити") } }
        )
    }

    if (showDatePicker) {
        DatePickerMiniDialog(
            initial = date,
            onDismiss = { showDatePicker = false },
            onConfirm = { newDate -> date = newDate; showDatePicker = false }
        )
    }

    if (showSeriesDatePicker) {
        SeriesDatePickerDialog(
            selectedDates = selectedDates,
            conflictDates = conflictDates,
            initialMonth = seriesCalendarStart,
            onDismiss = { showSeriesDatePicker = false },
            onConfirm = { picked, month ->
                selectedDates = picked
                seriesCalendarStart = month
                showSeriesDatePicker = false
            }
        )
    }

    if (showStartPicker) {
        TimePickerDialog(
            initial = startTime,
            minHour = workHoursStart, maxHour = workHoursEnd,
            hiddenMinutes = startHiddenMinutes,
            onDismiss = { showStartPicker = false },
            onConfirm = { t ->
                startTime = t
                if (endTime.toMinutes() <= t.toMinutes()) endTime = minutesToLocalTime((t.toMinutes() + 60).coerceAtMost(workHoursEnd * 60))
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initial = endTime,
            minHour = workHoursStart, maxHour = workHoursEnd,
            hiddenMinutes = endHiddenMinutes,
            onDismiss = { showEndPicker = false },
            onConfirm = { endTime = it; showEndPicker = false }
        )
    }
}

// ─────────────────────────────────────────────
// Shared chip components
// ─────────────────────────────────────────────

@Composable
fun SelectableChip(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, if (selected) color else Color(0xFFDADCE0), RoundedCornerShape(20.dp))
            .background(if (selected) color.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            name, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) color else Color(0xFF3C3C3C)
        )
        if (selected) Text("✓", fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CreateChip(name: String, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, color.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("✦", fontSize = 11.sp, color = color)
        Text("Створити «$name»", fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CountStepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { if (value > min) onChange(value - 1) },
            modifier = Modifier.size(32.dp)
        ) { Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Text(
            "$value", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(40.dp), textAlign = TextAlign.Center
        )
        FilledTonalIconButton(
            onClick = { if (value < max) onChange(value + 1) },
            modifier = Modifier.size(32.dp)
        ) { Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun TimeChip(label: String, time: LocalTime, onClick: () -> Unit) {
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

@Composable
fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    minHour: Int = 0,
    maxHour: Int = 23,
    hiddenMinutes: Set<Int> = emptySet(),
) {
    var step by remember { mutableStateOf(30) }

    val slots = remember(minHour, maxHour, hiddenMinutes, step) {
        val result = mutableListOf<LocalTime>()
        var m = minHour * 60
        while (m <= maxHour * 60) {
            result.add(minutesToLocalTime(m))
            m += step
        }
        result.filter { it.toMinutes() !in hiddenMinutes }
    }

    val initialClosest = slots.minByOrNull { kotlin.math.abs(it.toMinutes() - initial.toMinutes()) } ?: initial
    var selected by remember { mutableStateOf(if (initial.toMinutes() !in hiddenMinutes) initial else initialClosest) }

    // Snap selected to closest available slot when step changes
    val snappedSelected = slots.minByOrNull { kotlin.math.abs(it.toMinutes() - selected.toMinutes()) } ?: selected
    if (snappedSelected != selected && slots.isNotEmpty()) selected = snappedSelected

    val selIndex = slots.indexOf(selected).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selIndex - 2).coerceAtLeast(0))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Оберіть час") },
        text = {
            Column {
                // Step selector
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Крок:", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(end = 6.dp))
                    listOf(30, 15, 10).forEach { s ->
                        val active = step == s
                        Box(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { step = s }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${s}хв", fontSize = 11.sp,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
                LazyColumn(state = listState, modifier = Modifier.heightIn(max = 260.dp)) {
                    itemsIndexed(slots) { _, time ->
                        val isSel = time == selected
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { selected = time }
                                .padding(vertical = 10.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                time.toStorageString(),
                                fontSize = 16.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSel) Text("✓", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(selected) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

@Composable
private fun DatePickerMiniDialog(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    var displayMonth by remember { mutableStateOf(LocalDate(initial.year, initial.month, 1)) }
    var picked by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 340.dp),
        title = { Text("Оберіть дату", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { displayMonth = displayMonth.minus(1, DateTimeUnit.MONTH) },
                        contentPadding = PaddingValues(4.dp)) { Text("◀", fontSize = 14.sp) }
                    Text("${MONTHS_UK_FULL[displayMonth.month.ordinal]} ${displayMonth.year}",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    TextButton(onClick = { displayMonth = displayMonth.plus(1, DateTimeUnit.MONTH) },
                        contentPadding = PaddingValues(4.dp)) { Text("▶", fontSize = 14.sp) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Пн","Вт","Ср","Чт","Пт","Сб","Нд").forEach { d ->
                        Text(d, fontSize = 10.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.size(32.dp).wrapContentHeight(), color = Color.Gray)
                    }
                }
                val firstDow = LocalDate(displayMonth.year, displayMonth.month, 1).dayOfWeek.isoDayNumber
                val daysInMonth = displayMonth.plus(1, DateTimeUnit.MONTH).let {
                    LocalDate(it.year, it.month, 1).minus(1, DateTimeUnit.DAY).dayOfMonth
                }
                val cells = mutableListOf<Int?>()
                repeat(firstDow - 1) { cells.add(null) }
                for (d in 1..daysInMonth) cells.add(d)
                while (cells.size % 7 != 0) cells.add(null)
                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        week.forEach { d ->
                            if (d == null) { Spacer(Modifier.size(32.dp)) } else {
                                val day = LocalDate(displayMonth.year, displayMonth.month, d)
                                val isSel = day == picked
                                Box(modifier = Modifier.size(32.dp).clip(CircleShape)
                                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .then(if (!isSel) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f), CircleShape) else Modifier)
                                    .clickable { picked = day },
                                    contentAlignment = Alignment.Center) {
                                    Text("$d", fontSize = 12.sp,
                                        color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(picked) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

@Composable
private fun SeriesDatePickerDialog(
    selectedDates: Set<LocalDate>,
    conflictDates: Set<LocalDate>,
    initialMonth: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (dates: Set<LocalDate>, displayMonth: LocalDate) -> Unit,
) {
    val seriesToday = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    var displayMonth by remember { mutableStateOf(LocalDate(initialMonth.year, initialMonth.month, 1)) }
    var currentSelected by remember { mutableStateOf(selectedDates) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 340.dp),
        title = { Text("Оберіть дати", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { displayMonth = displayMonth.minus(1, DateTimeUnit.MONTH) },
                        contentPadding = PaddingValues(4.dp)) { Text("◀", fontSize = 14.sp) }
                    Text("${MONTHS_UK_FULL[displayMonth.month.ordinal]} ${displayMonth.year}",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    TextButton(onClick = { displayMonth = displayMonth.plus(1, DateTimeUnit.MONTH) },
                        contentPadding = PaddingValues(4.dp)) { Text("▶", fontSize = 14.sp) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Пн","Вт","Ср","Чт","Пт","Сб","Нд").forEach { d ->
                        Text(d, fontSize = 10.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.size(32.dp).wrapContentHeight(), color = Color.Gray)
                    }
                }
                val firstDow = LocalDate(displayMonth.year, displayMonth.month, 1).dayOfWeek.isoDayNumber
                val nextFirst = displayMonth.plus(1, DateTimeUnit.MONTH)
                val daysInMonth = LocalDate(nextFirst.year, nextFirst.month, 1).minus(1, DateTimeUnit.DAY).dayOfMonth
                val cells = mutableListOf<Int?>()
                repeat(firstDow - 1) { cells.add(null) }
                for (d in 1..daysInMonth) cells.add(d)
                while (cells.size % 7 != 0) cells.add(null)
                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        week.forEach { d ->
                            if (d == null) { Spacer(Modifier.size(32.dp)) } else {
                                val day = LocalDate(displayMonth.year, displayMonth.month, d)
                                val isPast = day < seriesToday
                                val isSel = day in currentSelected
                                val isConflict = day in conflictDates
                                Box(
                                    modifier = Modifier.size(32.dp).clip(CircleShape)
                                        .background(when {
                                            isSel && !isConflict -> MaterialTheme.colorScheme.primary
                                            isConflict -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                            else -> Color.Transparent
                                        })
                                        .then(if (!isPast && !isConflict && !isSel)
                                            Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f), CircleShape)
                                        else Modifier)
                                        .clickable(enabled = !isPast && !isConflict) {
                                            currentSelected = if (isSel) currentSelected - day else currentSelected + day
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$d", fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSel && !isConflict -> Color.White
                                            isConflict -> MaterialTheme.colorScheme.error
                                            isPast -> Color.LightGray
                                            else -> MaterialTheme.colorScheme.onSurface
                                        })
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                val conflicts = currentSelected.count { it in conflictDates }
                when {
                    currentSelected.isEmpty() -> Text("Оберіть хоча б одну дату", fontSize = 12.sp, color = Color.Gray)
                    conflicts > 0 -> Text("⚠ $conflicts дат(и) мають конфлікт", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    else -> Text("Обрано: ${currentSelected.size} дат", fontSize = 12.sp, color = Color.Gray)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(currentSelected, displayMonth) },
                enabled = currentSelected.isNotEmpty() && currentSelected.none { it in conflictDates }
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}
