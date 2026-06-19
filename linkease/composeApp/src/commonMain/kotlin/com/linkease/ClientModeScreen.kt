package com.linkease

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.*

private val FREE_COLOR   = Color(0xFF2E7D32)
private val BOOKED_COLOR = Color(0xFF9E9E9E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientModeScreen(
    trainerData: TrainerData,
    myFirebaseId: String,
    clientEmail: String = "",
    clientSessions: List<ClientSession> = emptyList(),
    pendingBookingRequests: List<BookingRequest> = emptyList(),
    myAvailability: List<ClientAvailabilitySlot> = emptyList(),
    chatMessages: List<ChatMessage> = emptyList(),
    onDisconnect: () -> Unit,
    onBookSlot: (date: LocalDate, start: LocalTime, end: LocalTime, note: String?) -> Unit = { _, _, _, _ -> },
    onConfirmSession: (sessionId: String) -> Unit = {},
    onRejectSession: (sessionId: String) -> Unit = {},
    onCancelSession: (sessionId: String) -> Unit = {},
    onSaveMyAvailability: (List<ClientAvailabilitySlot>) -> Unit = {},
    onSendChat: (String) -> Unit = {},
    onCopyClientEmail: (() -> Unit)? = null,
    onShareClientEmail: (() -> Unit)? = null,
) {
    val tz    = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date

    var currentDate  by remember { mutableStateOf(today) }
    var selectedTab  by remember { mutableStateOf(0) }
    var bookingSlot  by remember { mutableStateOf<Triple<LocalDate, LocalTime, LocalTime>?>(null) }
    var showChat     by remember { mutableStateOf(false) }

    val days         = remember(currentDate) { (0..2).map { currentDate.plus(it, DateTimeUnit.DAY) } }
    val availability = trainerData.availability
    val sessionSlots = trainerData.sessionSlots
    val unconfirmed  = clientSessions.count { !it.clientConfirmed }
    val unreadChat   = chatMessages.count { it.senderId != myFirebaseId }.let { 0 } // simplified

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Розклад тренера", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showChat = true }) { Text("💬", fontSize = 20.sp) }
                    TextButton(onClick = onDisconnect) { Text("Відключитись") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("📅 Розклад") })
                Tab(
                    selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = {
                        if (unconfirmed > 0) Text("📋 Заняття ($unconfirmed)")
                        else Text("📋 Заняття")
                    }
                )
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    text = { Text("📆 Моя доступність") })
            }

            when (selectedTab) {
                0 -> ScheduleTabContent(
                    days = days,
                    today = today,
                    availability = availability,
                    sessionSlots = sessionSlots,
                    locations = trainerData.locations,
                    currentDate = currentDate,
                    onPrev = { currentDate = currentDate.minus(3, DateTimeUnit.DAY) },
                    onNext = { currentDate = currentDate.plus(3, DateTimeUnit.DAY) },
                    onGoToToday = { currentDate = today },
                    onBook = { date, start, end -> bookingSlot = Triple(date, start, end) }
                )
                1 -> MySessionsTabContent(
                    clientEmail = clientEmail,
                    clientSessions = clientSessions,
                    pendingBookingRequests = pendingBookingRequests,
                    today = today,
                    onConfirmSession = onConfirmSession,
                    onRejectSession = onRejectSession,
                    onCancelSession = onCancelSession,
                    onCopyClientEmail = onCopyClientEmail,
                    onShareClientEmail = onShareClientEmail,
                )
                2 -> ClientAvailabilityTabContent(
                    myFirebaseId = myFirebaseId,
                    slots = myAvailability,
                    today = today,
                    onSave = onSaveMyAvailability,
                )
            }
        }
    }

    bookingSlot?.let { (date, slotStart, slotEnd) ->
        BookSessionDialog(
            date = date,
            slotStart = slotStart,
            slotEnd = slotEnd,
            onDismiss = { bookingSlot = null },
            onConfirm = { start, end, note ->
                onBookSlot(date, start, end, note)
                bookingSlot = null
            }
        )
    }

    if (showChat) {
        ChatDialog(
            myId = myFirebaseId,
            partnerName = "Тренер",
            messages = chatMessages,
            onSend = onSendChat,
            onDismiss = { showChat = false }
        )
    }
}

@Composable
private fun ScheduleTabContent(
    days: List<LocalDate>,
    today: LocalDate,
    availability: List<AvailabilitySlot>,
    sessionSlots: List<BookedSlot>,
    locations: List<Location> = emptyList(),
    currentDate: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onGoToToday: () -> Unit,
    onBook: (LocalDate, LocalTime, LocalTime) -> Unit,
) {
    val locationsById = remember(locations) { locations.associateBy { it.id } }
    Column(modifier = Modifier.fillMaxSize()) {
        // Date navigation
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) { Text("◀", fontSize = 16.sp) }
            Spacer(Modifier.weight(1f))
            val first = days.first(); val last = days.last()
            Text(
                "${first.dayOfMonth}.${first.monthNumber.toString().padStart(2,'0')} – " +
                "${last.dayOfMonth}.${last.monthNumber.toString().padStart(2,'0')} ${last.year}",
                fontWeight = FontWeight.Medium, fontSize = 14.sp
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onNext) { Text("▶", fontSize = 16.sp) }
        }

        if (!days.any { it >= today }) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onGoToToday) { Text("Перейти до сьогодні", fontSize = 13.sp) }
            }
        }

        // Day columns
        Row(modifier = Modifier.fillMaxSize()) {
            days.forEach { day ->
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val isToday   = day == today
                    val isPast    = day < today
                    val isWeekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
                    val circleBg  = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            DAYS_UK[day.dayOfWeek.ordinal], fontSize = 11.sp,
                            color = when {
                                isToday   -> MaterialTheme.colorScheme.primary
                                isWeekend -> Color(0xFFBF360C)
                                else      -> Color(0xFF757575)
                            },
                            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Spacer(Modifier.height(2.dp))
                        Box(
                            modifier = Modifier.size(if (isToday) 34.dp else 28.dp)
                                .clip(CircleShape).background(circleBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${day.dayOfMonth}",
                                fontSize = if (isToday) 16.sp else 14.sp,
                                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if (isToday) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    val dayAvail  = availability.filter { it.date == day }
                    val dayBooked = sessionSlots.filter { it.date == day }
                    val nowMinutes = if (day == today) {
                        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
                        now.hour * 60 + now.minute
                    } else if (isPast) Int.MAX_VALUE else 0

                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        dayAvail.sortedBy { it.startTime.toMinutes() }.forEach { slot ->
                            val freeSlots = computeClientFreeSlots(slot, dayBooked)
                            val locationName = slot.locationId?.let { locationsById[it]?.name }
                            freeSlots.forEach { free ->
                                // skip slots that have already ended
                                if (free.second.toMinutes() <= nowMinutes) return@forEach
                                val clampedStart = if (free.first.toMinutes() < nowMinutes)
                                    minutesToLocalTime(nowMinutes) else free.first
                                FreeSlotChip(
                                    start = clampedStart, end = free.second, locationName = locationName,
                                    onBook = { onBook(day, clampedStart, free.second) }
                                )
                            }
                        }

                        dayBooked.sortedBy { it.startTime.toMinutes() }.forEach { booked ->
                            if (booked.endTime.toMinutes() > nowMinutes) {
                                BookedSlotChip(start = booked.startTime, end = booked.endTime)
                            }
                        }

                        if (dayAvail.isEmpty() && !isPast) {
                            Text(
                                "Немає слотів", fontSize = 11.sp, color = Color.Gray,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                if (day != days.last()) {
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun ClientAvailabilityTabContent(
    myFirebaseId: String,
    slots: List<ClientAvailabilitySlot>,
    today: LocalDate,
    onSave: (List<ClientAvailabilitySlot>) -> Unit,
) {
    var localSlots by remember(slots) { mutableStateOf(slots) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Мої вільні слоти", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { showAddDialog = true }) { Text("+ Додати") }
        }
        Text("Тренер побачить ваші вільні слоти і зможе призначити заняття",
            fontSize = 12.sp, color = Color.Gray)

        if (localSlots.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text("Немає вільних слотів", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            localSlots.sortedWith(compareBy({ it.date }, { it.startTime.toMinutes() })).forEach { slot ->
                val dateStr = "${slot.date.dayOfMonth}.${slot.date.monthNumber.toString().padStart(2,'0')}.${slot.date.year}"
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dateStr, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("${slot.startTime.toStorageString()} – ${slot.endTime.toStorageString()}", fontSize = 13.sp, color = Color.Gray)
                        }
                        TextButton(
                            onClick = {
                                localSlots = localSlots.filter { it.id != slot.id }
                                onSave(localSlots)
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("✕") }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddClientAvailabilityDialog(
            today = today,
            onDismiss = { showAddDialog = false },
            onConfirm = { date, start, end ->
                val newSlot = ClientAvailabilitySlot(
                    id = "${myFirebaseId}_${date}_${start.toStorageString()}_${end.toStorageString()}",
                    clientFirebaseId = myFirebaseId,
                    date = date, startTime = start, endTime = end,
                )
                localSlots = localSlots + newSlot
                onSave(localSlots)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddClientAvailabilityDialog(
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalTime, LocalTime) -> Unit,
) {
    val days = remember { (0..13).map { today.plus(it, DateTimeUnit.DAY) } }
    var selectedDay by remember { mutableStateOf(today) }
    var startTime by remember { mutableStateOf(LocalTime(9, 0)) }
    var endTime by remember { mutableStateOf(LocalTime(11, 0)) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Додати вільний слот") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Дата", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEach { day ->
                        val isSelected = day == selectedDay
                        val label = "${day.dayOfMonth}.${day.monthNumber.toString().padStart(2,'0')}"
                        if (isSelected) {
                            Button(onClick = {}, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text(label, fontSize = 12.sp) }
                        } else {
                            OutlinedButton(onClick = { selectedDay = day }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text(label, fontSize = 12.sp) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                        Text("Початок: ${startTime.toStorageString()}", fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                        Text("Кінець: ${endTime.toStorageString()}", fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (endTime.toMinutes() > startTime.toMinutes()) onConfirm(selectedDay, startTime, endTime) }) {
                Text("Зберегти")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )

    if (showStartPicker) {
        TimePickerDialog(
            initial = startTime, hiddenMinutes = emptySet(), minHour = 6, maxHour = 23,
            onDismiss = { showStartPicker = false },
            onConfirm = { startTime = it; showStartPicker = false }
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initial = endTime, hiddenMinutes = emptySet(),
            minHour = startTime.hour, maxHour = 23,
            onDismiss = { showEndPicker = false },
            onConfirm = { t ->
                endTime = if (t.toMinutes() <= startTime.toMinutes())
                    minutesToLocalTime(startTime.toMinutes() + 30) else t
                showEndPicker = false
            }
        )
    }
}

@Composable
private fun MySessionsTabContent(
    clientEmail: String,
    clientSessions: List<ClientSession>,
    pendingBookingRequests: List<BookingRequest>,
    today: LocalDate,
    onConfirmSession: (String) -> Unit,
    onRejectSession: (String) -> Unit,
    onCancelSession: (String) -> Unit = {},
    onCopyClientEmail: (() -> Unit)?,
    onShareClientEmail: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // My email card
        if (clientEmail.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Мій email", fontSize = 11.sp, color = Color.Gray)
                        Text(clientEmail, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    if (onCopyClientEmail != null) {
                        OutlinedButton(
                            onClick = onCopyClientEmail,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("Копіювати", fontSize = 11.sp) }
                    }
                    if (onShareClientEmail != null) {
                        OutlinedButton(
                            onClick = onShareClientEmail,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("📤", fontSize = 13.sp) }
                    }
                }
            }
        }

        // Booking requests
        if (pendingBookingRequests.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("Мої запити", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            pendingBookingRequests.sortedBy { it.date }.forEach { request ->
                ClientBookingRequestCard(request)
            }
        }

        // Sessions from trainer
        Spacer(Modifier.height(4.dp))
        Text("Розклад від тренера", style = MaterialTheme.typography.labelMedium, color = Color.Gray)

        val upcoming = clientSessions.filter { it.date >= today }.sortedBy { it.date }
        val past     = clientSessions.filter { it.date < today }.sortedByDescending { it.date }
        val ordered  = upcoming + past

        if (ordered.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text("Тренер ще не опублікував ваш розклад", fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
            }
        } else {
            ordered.forEach { session ->
                ClientSessionCard(session = session, today = today, onConfirm = onConfirmSession, onReject = onRejectSession, onCancel = onCancelSession)
            }
        }
    }
}

@Composable
private fun ClientSessionCard(
    session: ClientSession,
    today: LocalDate,
    onConfirm: (String) -> Unit,
    onReject: (String) -> Unit = {},
    onCancel: (String) -> Unit = {},
) {
    val isPast   = session.date < today
    val dateStr  = "${session.date.dayOfMonth}.${session.date.monthNumber.toString().padStart(2,'0')}.${session.date.year}"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                session.clientConfirmed -> Color(0xFFE8F5E9)
                isPast -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(dateStr, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                    color = if (isPast) Color.Gray else MaterialTheme.colorScheme.onSurface)
                Text(
                    "${session.startTime.toStorageString()} – ${session.endTime.toStorageString()}",
                    fontSize = 13.sp, color = Color.Gray
                )
                if (!session.notes.isNullOrBlank()) {
                    Text(session.notes, fontSize = 12.sp, color = Color.Gray)
                }
            }
            when {
                isPast -> Unit
                session.clientConfirmed -> OutlinedButton(
                    onClick = { onCancel(session.id) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Скасувати", fontSize = 12.sp) }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { onConfirm(session.id) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("✓", fontSize = 14.sp) }
                    OutlinedButton(
                        onClick = { onReject(session.id) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("✕", fontSize = 14.sp) }
                }
            }
        }
    }
}

@Composable
private fun ClientBookingRequestCard(request: BookingRequest) {
    val (statusColor, statusText) = when (request.status) {
        "confirmed" -> Color(0xFF2E7D32) to "✅ Підтверджено"
        "declined"  -> Color(0xFFD32F2F) to "❌ Відхилено"
        else        -> Color.Gray to "⏳ Очікує"
    }
    val dateStr = "${request.date.dayOfMonth}.${request.date.monthNumber.toString().padStart(2,'0')}.${request.date.year}"
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(dateStr, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    "${request.startTime.toStorageString()} – ${request.endTime.toStorageString()}",
                    fontSize = 13.sp, color = Color.Gray
                )
                if (!request.clientNote.isNullOrBlank()) {
                    Text(request.clientNote, fontSize = 12.sp, color = Color.Gray)
                }
            }
            Text(statusText, fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookSessionDialog(
    date: LocalDate,
    slotStart: LocalTime,
    slotEnd: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (start: LocalTime, end: LocalTime, note: String?) -> Unit,
) {
    val slotMinutes = slotEnd.toMinutes() - slotStart.toMinutes()
    // Start time options: every 30 min within slot, leaving room for at least 30 min session
    val startOptions = remember(slotStart, slotEnd) {
        val options = mutableListOf<LocalTime>()
        var m = slotStart.toMinutes()
        while (m + 30 <= slotEnd.toMinutes()) {
            options.add(minutesToLocalTime(m))
            m += 30
        }
        options
    }
    var selectedStart  by remember { mutableStateOf(slotStart) }
    val availAfterStart = slotEnd.toMinutes() - selectedStart.toMinutes()
    val durationOptions = listOf(30, 45, 60, 90, 120, 150, 180).filter { it <= availAfterStart }
    var selectedDuration by remember(selectedStart) { mutableStateOf(durationOptions.firstOrNull() ?: 30) }
    var noteText         by remember { mutableStateOf("") }
    val endTime          = minutesToLocalTime(selectedStart.toMinutes() + selectedDuration)
    val dateStr          = "${date.dayOfMonth}.${date.monthNumber.toString().padStart(2,'0')}.${date.year}"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Записатись на заняття") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "$dateStr · доступно ${slotStart.toStorageString()}–${slotEnd.toStorageString()}",
                    fontSize = 13.sp, color = Color.Gray
                )

                if (startOptions.size > 1) {
                    Text("Початок", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        startOptions.forEach { t ->
                            FilterChip(
                                selected = t == selectedStart,
                                onClick = { selectedStart = t },
                                label = { Text(t.toStorageString(), fontSize = 12.sp) }
                            )
                        }
                    }
                }

                if (durationOptions.isEmpty()) {
                    Text("Слот занадто малий для бронювання (мін. 30 хв)", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                } else {
                    Text("Тривалість", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        durationOptions.forEach { dur ->
                            FilterChip(
                                selected = dur == selectedDuration,
                                onClick = { selectedDuration = dur },
                                label = { Text(formatDuration(dur)) }
                            )
                        }
                    }
                    Text(
                        "${selectedStart.toStorageString()} – ${endTime.toStorageString()}",
                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Коментар (необов'язково)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedStart, endTime, noteText.ifBlank { null }) },
                enabled = durationOptions.isNotEmpty()
            ) { Text("Надіслати запит") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

private fun computeClientFreeSlots(
    slot: AvailabilitySlot,
    booked: List<BookedSlot>,
): List<Pair<LocalTime, LocalTime>> {
    val overlapping = booked.filter { b ->
        b.startTime.toMinutes() < slot.endTime.toMinutes() &&
        b.endTime.toMinutes() > slot.startTime.toMinutes()
    }.sortedBy { it.startTime.toMinutes() }
    if (overlapping.isEmpty()) return listOf(slot.startTime to slot.endTime)
    val result = mutableListOf<Pair<LocalTime, LocalTime>>()
    var cursor = slot.startTime.toMinutes()
    for (b in overlapping) {
        val bs = b.startTime.toMinutes()
        if (cursor < bs) result.add(minutesToLocalTime(cursor) to minutesToLocalTime(bs))
        cursor = maxOf(cursor, b.endTime.toMinutes())
    }
    if (cursor < slot.endTime.toMinutes()) result.add(minutesToLocalTime(cursor) to slot.endTime)
    return result
}

@Composable
private fun FreeSlotChip(
    start: LocalTime, end: LocalTime,
    locationName: String? = null,
    isPast: Boolean = false,
    onBook: (() -> Unit)? = null,
) {
    val dur = end.toMinutes() - start.toMinutes()
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(FREE_COLOR.copy(alpha = if (isPast) 0.05f else 0.10f))
            .border(1.dp, FREE_COLOR.copy(alpha = if (isPast) 0.2f else 0.4f), RoundedCornerShape(6.dp))
            .then(if (onBook != null) Modifier.clickable(onClick = onBook) else Modifier)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()) {
            Text(
                "${start.toStorageString()}–${end.toStorageString()}", fontSize = 11.sp,
                color = if (isPast) Color.Gray else FREE_COLOR, fontWeight = FontWeight.Medium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(dur), fontSize = 10.sp, color = Color.Gray)
                if (onBook != null) Text("+", fontSize = 13.sp, color = FREE_COLOR, fontWeight = FontWeight.Bold)
            }
        }
        if (!locationName.isNullOrBlank()) {
            Text("📍 $locationName", fontSize = 10.sp, color = FREE_COLOR.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun BookedSlotChip(start: LocalTime, end: LocalTime) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(BOOKED_COLOR.copy(alpha = 0.08f))
            .border(1.dp, BOOKED_COLOR.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${start.toStorageString()}–${end.toStorageString()}", fontSize = 11.sp, color = BOOKED_COLOR)
        Text("Зайнято", fontSize = 10.sp, color = BOOKED_COLOR)
    }
}

// ─── Connect screen ───────────────────────────────────────────────────────────

@Composable
fun ClientConnectScreen(
    savedTrainerId: String?,
    isLoading: Boolean,
    error: String?,
    onConnect: (String) -> Unit,
    onSwitchToUserMode: (() -> Unit)? = null,
) {
    var idInput by remember { mutableStateOf(savedTrainerId ?: "") }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Режим клієнта", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(
                "Введіть email тренера",
                fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center
            )
            OutlinedTextField(
                value = idInput,
                onValueChange = { idInput = it.trim() },
                label = { Text("Email тренера") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            Button(
                onClick = { if (idInput.isNotBlank()) onConnect(idInput) },
                modifier = Modifier.fillMaxWidth(),
                enabled = idInput.isNotBlank() && !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Підключитись")
            }
            if (onSwitchToUserMode != null) {
                TextButton(onClick = onSwitchToUserMode) {
                    Text("← Перейти до тренерського режиму", fontSize = 13.sp)
                }
            }
        }
    }
}
