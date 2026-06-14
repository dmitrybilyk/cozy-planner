package com.linkease

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.*

val DAYS_UK = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд")
val MONTHS_UK_SHORT = listOf(
    "Січ", "Лют", "Бер", "Кві", "Тра", "Чер",
    "Лип", "Сер", "Вер", "Жов", "Лис", "Гру"
)
private val MONTHS_UK_FULL = listOf(
    "Січень", "Лютий", "Березень", "Квітень", "Травень", "Червень",
    "Липень", "Серпень", "Вересень", "Жовтень", "Листопад", "Грудень"
)

val HOUR_HEIGHT = 64.dp
private val TIME_COL_NORMAL = 44.dp
private val TIME_COL_WEEK   = 28.dp

private val WEEKEND_BG      = Color(0xFFFFF3E0)
private val WEEKEND_HEADER  = Color(0xFFBF360C)
private val PAST_OVERLAY    = Color(0x1A000000)
private val FREE_SLOT_DEFAULT = Color(0xFF2E7D32)
private val DISABLED_ZONE   = Color(0x22000000)

fun hexToColor(hex: String): Color = try {
    val h = hex.removePrefix("#")
    Color(h.substring(0,2).toInt(16), h.substring(2,4).toInt(16), h.substring(4,6).toInt(16))
} catch (_: Exception) { Color(0xFF3949AB) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    startDate: LocalDate,
    currentView: CalendarView,
    sessions: List<Session>,
    clients: List<Client>,
    locations: List<Location>,
    availability: List<AvailabilitySlot>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onGoToToday: () -> Unit,
    onViewChange: (CalendarView) -> Unit,
    onAddSession: (date: LocalDate, startTime: LocalTime) -> Unit,
    onAddSessionFromSlot: (date: LocalDate, startTime: LocalTime, endTime: LocalTime) -> Unit,
    onEditSession: (Session) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onCopySession: (Session) -> Unit,
    onAvailabilityClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onShareFreeTime: (date: LocalDate, text: String) -> Unit,
    onDayClickInMonth: (LocalDate) -> Unit,
    onPinToStatusBar: (() -> Unit)? = null,
    onExportDayToCalendar: ((date: LocalDate, sessions: List<Session>, clients: List<Client>, locations: List<Location>) -> Unit)? = null,
) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val clientsById = clients.associateBy { it.id }
    val locationsById = locations.associateBy { it.id }

    val numDays = when (currentView) {
        CalendarView.DAY       -> 1
        CalendarView.THREE_DAY -> 3
        CalendarView.WEEK      -> 7
        CalendarView.MONTH     -> 0
    }
    val days = if (numDays > 0) (0 until numDays).map { startDate.plus(it, DateTimeUnit.DAY) } else emptyList()

    val includesTODAY = when (currentView) {
        CalendarView.MONTH -> startDate.year == today.year && startDate.month == today.month
        else -> days.any { it == today }
    }

    val scrollState = rememberScrollState()
    var sessionMenu by remember { mutableStateOf<Session?>(null) }
    var swipeDelta by remember { mutableFloatStateOf(0f) }
    var showFreeChips by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Free chips: next 14 days from today (not just visible days)
    val freeChips = remember(today, sessions, availability) {
        (0 until 14).map { today.plus(it, DateTimeUnit.DAY) }.flatMap { day ->
            val daySessions = sessions.filter { it.date == day }
            calculateFreeSlots(daySessions, availability, day.dayOfWeek.isoDayNumber)
                .map { Pair(day, it) }
        }
    }

    val timeColWidth = if (currentView == CalendarView.WEEK) TIME_COL_WEEK else TIME_COL_NORMAL

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                CalendarTopBar(
                    currentView = currentView,
                    startDate = startDate,
                    today = today,
                    onPrev = onPrev, onNext = onNext, onGoToToday = onGoToToday,
                    onSettingsClick = onSettingsClick,
                    includesTODAY = includesTODAY,
                    onExportDay = if (onExportDayToCalendar != null && currentView == CalendarView.DAY) {
                        { onExportDayToCalendar(startDate, sessions.filter { it.date == startDate }, clients, locations) }
                    } else null,
                )
                ViewSelector(currentView = currentView, onViewChange = onViewChange)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        bottomBar = {
            Column {
                if (currentView != CalendarView.MONTH) {
                    AnimatedVisibility(
                        visible = showFreeChips,
                        enter = expandVertically(expandFrom = Alignment.Bottom),
                        exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
                    ) {
                        FreeTimeChipsPanel(
                            chips = freeChips,
                            locationsById = locationsById,
                            onChipClick = { date, start, end ->
                                showFreeChips = false
                                onAddSessionFromSlot(date, start, end)
                            },
                            onShareDay = { date, text -> onShareFreeTime(date, text) }
                        )
                    }
                }
                BottomBar(
                    showFreeChipsActive = showFreeChips && currentView != CalendarView.MONTH,
                    showFreeTimeItem = currentView != CalendarView.MONTH,
                    onCreateClick = {
                        val firstDay = if (days.isNotEmpty()) days.first() else today
                        val suggestedTime = findNextAvailableStart(sessions, firstDay, LocalTime(9, 0))
                        onAddSession(firstDay, suggestedTime)
                    },
                    onFreeTimeClick = { showFreeChips = !showFreeChips },
                    onAvailabilityClick = onAvailabilityClick,
                    onPinToStatusBar = onPinToStatusBar,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(onPrev, onNext) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (swipeDelta > 80f) onPrev() else if (swipeDelta < -80f) onNext()
                            swipeDelta = 0f
                        },
                        onDragCancel = { swipeDelta = 0f },
                        onHorizontalDrag = { _, delta -> swipeDelta += delta }
                    )
                }
        ) {
            when (currentView) {
                CalendarView.MONTH -> {
                    MonthView(
                        startDate = startDate,
                        today = today,
                        sessions = sessions,
                        locationsById = locationsById,
                        onDayClick = onDayClickInMonth,
                    )
                }
                else -> {
                    DayHeaderRow(days = days, today = today, sessions = sessions, availability = availability, locationsById = locationsById, onShareFreeTime = onShareFreeTime)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                        HourLabels(timeColWidth = timeColWidth)
                        days.forEach { day ->
                            val daySessions = sessions.filter { it.date == day }
                            val freeSlots = calculateFreeSlots(daySessions, availability, day.dayOfWeek.isoDayNumber)
                            DayColumn(
                                date = day, today = today,
                                sessions = daySessions,
                                freeSlots = freeSlots,
                                availability = availability,
                                locationsById = locationsById, clientsById = clientsById,
                                modifier = Modifier.weight(1f),
                                isWeekView = currentView == CalendarView.WEEK,
                                onEmptyClick = { time -> onAddSession(day, time) },
                                onSlotClick = { start, end -> onAddSessionFromSlot(day, start, end) },
                                onOutOfScope = {
                                    scope.launch { snackbarHostState.showSnackbar("Поза межами вашого графіку") }
                                },
                                onSessionClick = { sessionMenu = it },
                            )
                        }
                    }
                }
            }
        }
    }

    sessionMenu?.let { session ->
        SessionContextMenu(
            session = session,
            clients = session.clientIds.mapNotNull { clientsById[it] },
            location = session.locationId?.let { locationsById[it] },
            onDismiss = { sessionMenu = null },
            onEdit = { onEditSession(session); sessionMenu = null },
            onDelete = { onDeleteSession(session.id); sessionMenu = null },
            onCopy = { onCopySession(session); sessionMenu = null },
        )
    }
}

// ─────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTopBar(
    currentView: CalendarView,
    startDate: LocalDate,
    today: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onGoToToday: () -> Unit,
    onSettingsClick: () -> Unit,
    includesTODAY: Boolean,
    onExportDay: (() -> Unit)? = null,
) {
    val label = when (currentView) {
        CalendarView.MONTH -> "${MONTHS_UK_FULL[startDate.month.ordinal]} ${startDate.year}"
        CalendarView.DAY   -> "${DAYS_UK[startDate.dayOfWeek.ordinal]}, ${startDate.dayOfMonth} ${MONTHS_UK_SHORT[startDate.month.ordinal]} ${startDate.year}"
        else -> {
            val first = startDate
            val last = startDate.plus(if (currentView == CalendarView.WEEK) 6 else 2, DateTimeUnit.DAY)
            buildString {
                append(first.dayOfMonth)
                if (first.month != last.month) append(" ${MONTHS_UK_SHORT[first.month.ordinal]}")
                append(" – ${last.dayOfMonth} ${MONTHS_UK_SHORT[last.month.ordinal]} ${last.year}")
            }
        }
    }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onSettingsClick) { Text("⚙", fontSize = 20.sp) }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Text("◀", fontSize = 16.sp) }
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                IconButton(onClick = onNext) { Text("▶", fontSize = 16.sp) }
            }
        },
        actions = {
            if (onExportDay != null) {
                IconButton(onClick = onExportDay) { Text("📤", fontSize = 18.sp) }
            }
            if (!includesTODAY) {
                TextButton(onClick = onGoToToday) {
                    Text("Сьогодні", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

// ─────────────────────────────────────────────
// View selector
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewSelector(currentView: CalendarView, onViewChange: (CalendarView) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        CalendarView.entries.forEachIndexed { i, view ->
            SegmentedButton(
                selected = currentView == view,
                onClick = { onViewChange(view) },
                shape = SegmentedButtonDefaults.itemShape(i, CalendarView.entries.size),
                label = { Text(view.label, fontSize = 11.sp) }
            )
        }
    }
}

// ─────────────────────────────────────────────
// Bottom bar
// ─────────────────────────────────────────────

@Composable
private fun BottomBar(
    showFreeChipsActive: Boolean,
    showFreeTimeItem: Boolean,
    onCreateClick: () -> Unit,
    onFreeTimeClick: () -> Unit,
    onAvailabilityClick: () -> Unit,
    onPinToStatusBar: (() -> Unit)? = null,
) {
    NavigationBar(
        containerColor = Color(0xFF1A1A2E),
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = false,
            onClick = onCreateClick,
            icon = { Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White) },
            label = { Text("Створити", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f)) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.White.copy(alpha = 0.15f))
        )
        if (showFreeTimeItem) {
            NavigationBarItem(
                selected = showFreeChipsActive,
                onClick = onFreeTimeClick,
                icon = { Text(if (showFreeChipsActive) "🟢" else "⏰", fontSize = 18.sp) },
                label = { Text("Вільний час", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f)) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.White.copy(alpha = 0.15f))
            )
        }
        NavigationBarItem(
            selected = false,
            onClick = onAvailabilityClick,
            icon = { Text("📅", fontSize = 18.sp) },
            label = { Text("Графік", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f)) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.White.copy(alpha = 0.15f))
        )
        if (onPinToStatusBar != null) {
            NavigationBarItem(
                selected = false,
                onClick = onPinToStatusBar,
                icon = { Text("📌", fontSize = 18.sp) },
                label = { Text("Панель", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f)) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.White.copy(alpha = 0.15f))
            )
        }
    }
}

// ─────────────────────────────────────────────
// Month view
// ─────────────────────────────────────────────

@Composable
private fun MonthView(
    startDate: LocalDate,
    today: LocalDate,
    sessions: List<Session>,
    locationsById: Map<Long, Location>,
    onDayClick: (LocalDate) -> Unit,
) {
    val firstOfMonth = LocalDate(startDate.year, startDate.month, 1)
    val lastOfMonth = firstOfMonth.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    val daysInMonth = lastOfMonth.dayOfMonth
    val startDow = firstOfMonth.dayOfWeek.isoDayNumber  // 1=Mon

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
        // Weekday header
        Row(modifier = Modifier.fillMaxWidth()) {
            DAYS_UK.forEach { d ->
                Text(d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
            }
        }
        Spacer(Modifier.height(4.dp))

        var dayCounter = 1 - (startDow - 1)
        val totalCells = startDow - 1 + daysInMonth
        val rows = (totalCells + 6) / 7
        repeat(rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) {
                    if (dayCounter < 1 || dayCounter > daysInMonth) {
                        Box(modifier = Modifier.weight(1f).height(56.dp))
                    } else {
                        val date = LocalDate(firstOfMonth.year, firstOfMonth.month, dayCounter)
                        val daySessions = sessions.filter { it.date == date }
                        MonthDayCell(
                            date = date, today = today,
                            sessions = daySessions, locationsById = locationsById,
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    dayCounter++
                }
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    date: LocalDate,
    today: LocalDate,
    sessions: List<Session>,
    locationsById: Map<Long, Location>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isToday = date == today
    val isPast  = date < today
    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    Box(
        modifier = modifier
            .height(56.dp)
            .background(if (isWeekend) WEEKEND_BG else Color.Transparent)
            .clickable { onClick() }
            .padding(2.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape)
                    .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${date.dayOfMonth}", fontSize = 13.sp,
                    color = when {
                        isToday   -> Color.White
                        isPast    -> Color(0xFFBDBDBD)
                        isWeekend -> WEEKEND_HEADER
                        else      -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            if (sessions.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    sessions.take(3).forEach { s ->
                        val dotColor = s.locationId?.let { locationsById[it] }
                            ?.let { hexToColor(it.colorHex) } ?: MaterialTheme.colorScheme.primary
                        Box(
                            modifier = Modifier.size(5.dp).clip(CircleShape)
                                .background(dotColor.copy(alpha = if (isPast) 0.4f else 1f))
                        )
                        Spacer(Modifier.width(2.dp))
                    }
                    if (sessions.size > 3) Text("+${sessions.size - 3}", fontSize = 8.sp, color = Color.Gray)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Free-time chips panel
// ─────────────────────────────────────────────

@Composable
private fun FreeTimeChipsPanel(
    chips: List<Pair<LocalDate, FreeSlot>>,
    locationsById: Map<Long, Location>,
    onChipClick: (LocalDate, LocalTime, LocalTime) -> Unit,
    onShareDay: (LocalDate, String) -> Unit,
) {
    // Deduplicate days that have chips, in order
    val chipDays = chips.map { it.first }.distinct()
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 4.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (chips.isEmpty()) {
                Text("Немає вільного часу на найближчі 2 тижні",
                    fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
            } else {
                Text("Вільний час (2 тижні):", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chips) { (date, freeSlot) ->
                        val dayShort = DAYS_UK[date.dayOfWeek.ordinal]
                        val dayNum   = date.dayOfMonth
                        val chipColor = freeSlot.locationId?.let { locationsById[it] }
                            ?.let { hexToColor(it.colorHex) } ?: FREE_SLOT_DEFAULT
                        SuggestionChip(
                            onClick = { onChipClick(date, freeSlot.startTime, freeSlot.endTime) },
                            label = {
                                Text("$dayShort $dayNum  ${freeSlot.startTime.toStorageString()}–${freeSlot.endTime.toStorageString()}",
                                    fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = chipColor.copy(alpha = 0.15f)),
                            border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = chipColor.copy(alpha = 0.4f))
                        )
                    }
                }
                // Share buttons per day (up to 7 shown to avoid overflow)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    items(chipDays.take(7)) { day ->
                        val dayChips = chips.filter { it.first == day }
                        val text = buildFreeTimeText(day, dayChips.map { it.second }, locationsById)
                        TextButton(
                            onClick = { onShareDay(day, text) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("📋 ${DAYS_UK[day.dayOfWeek.ordinal]} ${day.dayOfMonth}", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Day header row
// ─────────────────────────────────────────────

@Composable
private fun DayHeaderRow(
    days: List<LocalDate>,
    today: LocalDate,
    sessions: List<Session>,
    availability: List<AvailabilitySlot>,
    locationsById: Map<Long, Location>,
    onShareFreeTime: (LocalDate, String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Spacer(Modifier.width(if (days.size > 3) TIME_COL_WEEK else TIME_COL_NORMAL))
        days.forEach { day ->
            val isToday   = day == today
            val isPast    = day < today
            val isWeekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
            val daySessions = sessions.filter { it.date == day }
            val freeSlots = calculateFreeSlots(daySessions, availability, day.dayOfWeek.isoDayNumber)
            Column(
                modifier = Modifier.weight(1f).background(if (isWeekend) WEEKEND_BG else Color.Transparent).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(DAYS_UK[day.dayOfWeek.ordinal], fontSize = 10.sp, color = when {
                    isToday  -> MaterialTheme.colorScheme.primary
                    isWeekend -> WEEKEND_HEADER
                    isPast   -> Color(0xFFBDBDBD)
                    else     -> Color(0xFF757575)
                }, fontWeight = FontWeight.Medium)
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                        .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${day.dayOfMonth}", fontSize = 14.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isToday  -> Color.White
                            isPast   -> Color(0xFFBDBDBD)
                            isWeekend -> WEEKEND_HEADER
                            else     -> MaterialTheme.colorScheme.onSurface
                        })
                }
                if (freeSlots.isNotEmpty()) {
                    Text("📋", fontSize = 10.sp,
                        modifier = Modifier.clickable {
                            onShareFreeTime(day, buildFreeTimeText(day, freeSlots, locationsById))
                        }.padding(1.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Hour labels
// ─────────────────────────────────────────────

@Composable
private fun HourLabels(timeColWidth: androidx.compose.ui.unit.Dp) {
    Column(modifier = Modifier.width(timeColWidth)) {
        Spacer(Modifier.height(HOUR_HEIGHT / 2))
        for (h in CALENDAR_HOURS_START..CALENDAR_HOURS_END) {
            Box(modifier = Modifier.height(HOUR_HEIGHT), contentAlignment = Alignment.TopEnd) {
                Text(
                    if (timeColWidth <= TIME_COL_WEEK) "$h" else "${h.toString().padStart(2,'0')}:00",
                    fontSize = 9.sp, color = Color(0xFF9E9E9E),
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Day column (time grid)
// ─────────────────────────────────────────────

@Composable
private fun DayColumn(
    date: LocalDate,
    today: LocalDate,
    sessions: List<Session>,
    freeSlots: List<FreeSlot>,
    availability: List<AvailabilitySlot>,
    locationsById: Map<Long, Location>,
    clientsById: Map<Long, Client>,
    modifier: Modifier = Modifier,
    isWeekView: Boolean = false,
    onEmptyClick: (LocalTime) -> Unit,
    onSlotClick: (LocalTime, LocalTime) -> Unit,
    onOutOfScope: () -> Unit,
    onSessionClick: (Session) -> Unit,
) {
    val isPast    = date < today
    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    val totalHours = CALENDAR_HOURS_END - CALENDAR_HOURS_START + 1
    val density = LocalDensity.current

    val dayAvail = remember(date, availability) {
        availability.filter { it.dayOfWeek == date.dayOfWeek.isoDayNumber }
    }

    // Out-of-availability disabled ranges (only when explicit availability is set for this day)
    val disabledRanges = remember(dayAvail) {
        if (dayAvail.isEmpty()) emptyList()
        else {
            val sorted = dayAvail.sortedBy { it.startTime.toMinutes() }
            val ranges = mutableListOf<Pair<Int, Int>>()
            var cursor = CALENDAR_HOURS_START * 60
            sorted.forEach { slot ->
                if (cursor < slot.startTime.toMinutes()) ranges.add(cursor to slot.startTime.toMinutes())
                cursor = maxOf(cursor, slot.endTime.toMinutes())
            }
            val endBound = (CALENDAR_HOURS_END + 1) * 60
            if (cursor < endBound) ranges.add(cursor to endBound)
            ranges
        }
    }

    Box(
        modifier = modifier
            .height(HOUR_HEIGHT * totalHours)
            .background(if (isWeekend) WEEKEND_BG else Color.Transparent)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .pointerInput(date, dayAvail, freeSlots, sessions) {
                detectTapGestures { offset ->
                    val hourHeightPx = with(density) { HOUR_HEIGHT.toPx() }
                    val minuteFromStart = ((offset.y / hourHeightPx) * 60).toInt()
                    val rounded = (minuteFromStart / 15) * 15
                    val totalMin = (CALENDAR_HOURS_START * 60 + rounded)
                        .coerceIn(CALENDAR_HOURS_START * 60, CALENDAR_HOURS_END * 60)
                    val clickedTime = minutesToLocalTime(totalMin)

                    // Occupied check — don't open create if there's already a session here
                    if (sessions.any { s ->
                        clickedTime.toMinutes() >= s.startTime.toMinutes() &&
                        clickedTime.toMinutes() < s.endTime.toMinutes()
                    }) return@detectTapGestures

                    // Availability check
                    if (dayAvail.isNotEmpty() && dayAvail.none { slot ->
                        clickedTime.toMinutes() >= slot.startTime.toMinutes() &&
                        clickedTime.toMinutes() < slot.endTime.toMinutes()
                    }) {
                        onOutOfScope(); return@detectTapGestures
                    }

                    // Free slot → use clicked time as start, clamp end to slot boundary
                    val matchingSlot = freeSlots.firstOrNull { slot ->
                        clickedTime.toMinutes() >= slot.startTime.toMinutes() &&
                        clickedTime.toMinutes() < slot.endTime.toMinutes()
                    }
                    if (matchingSlot != null) {
                        val smartEnd = minutesToLocalTime(
                            (clickedTime.toMinutes() + 60).coerceAtMost(matchingSlot.endTime.toMinutes())
                        )
                        onSlotClick(clickedTime, smartEnd)
                    } else onEmptyClick(clickedTime)
                }
            }
    ) {
        // Hour dividers
        for (i in 0..totalHours) {
            HorizontalDivider(modifier = Modifier.offset(y = HOUR_HEIGHT * i), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        for (i in 0 until totalHours) {
            HorizontalDivider(modifier = Modifier.offset(y = HOUR_HEIGHT * i + HOUR_HEIGHT / 2).padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), thickness = 0.5.dp)
        }

        // Disabled zones (explicit availability set)
        disabledRanges.forEach { (startMin, endMin) ->
            val s = (startMin - CALENDAR_HOURS_START * 60).coerceAtLeast(0)
            val e = (endMin - CALENDAR_HOURS_START * 60).coerceAtMost(totalHours * 60)
            if (e > s) Box(modifier = Modifier.fillMaxWidth().offset(y = HOUR_HEIGHT * s.toFloat() / 60f)
                .height(HOUR_HEIGHT * (e - s).toFloat() / 60f).background(DISABLED_ZONE))
        }

        // Free-time blocks (colored by linked location)
        freeSlots.forEach { free ->
            val startMin = (free.startTime.toMinutes() - CALENDAR_HOURS_START * 60).coerceAtLeast(0)
            val endMin   = (free.endTime.toMinutes()   - CALENDAR_HOURS_START * 60).coerceAtMost(totalHours * 60)
            if (endMin <= 0 || startMin >= totalHours * 60) return@forEach
            val freeColor = free.locationId?.let { locationsById[it] }?.let { hexToColor(it.colorHex) } ?: FREE_SLOT_DEFAULT
            Box(modifier = Modifier.fillMaxWidth()
                .offset(y = HOUR_HEIGHT * startMin.toFloat() / 60f)
                .height(HOUR_HEIGHT * (endMin - startMin).toFloat() / 60f)
                .padding(horizontal = 2.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(freeColor.copy(alpha = 0.13f))
                .border(0.5.dp, freeColor.copy(alpha = 0.4f), RoundedCornerShape(3.dp)))
        }

        // Sessions
        val columns = assignColumns(sessions)
        sessions.forEach { session ->
            val (col, totalCols) = columns[session.id] ?: (0 to 1)
            val startMin = (session.startTime.toMinutes() - CALENDAR_HOURS_START * 60).coerceAtLeast(0)
            val endMin   = (session.endTime.toMinutes()   - CALENDAR_HOURS_START * 60).coerceAtMost(totalHours * 60)
            if (endMin <= 0 || startMin >= totalHours * 60) return@forEach
            val topFrac    = startMin.toFloat() / 60f
            val heightFrac = (endMin - startMin).toFloat() / 60f
            val widthFrac  = 1f / totalCols
            val location   = session.locationId?.let { locationsById[it] }
            val baseColor  = hexToColor(location?.colorHex ?: "#3949AB")
            val blockColor = if (isPast) baseColor.copy(alpha = 0.4f) else baseColor
            val names = session.clientIds.mapNotNull { clientsById[it]?.name }.joinToString(", ")

            Box(modifier = Modifier
                .fillMaxWidth(widthFrac)
                .absoluteOffset(x = HOUR_HEIGHT * widthFrac * col, y = HOUR_HEIGHT * topFrac)
                .height(HOUR_HEIGHT * heightFrac)
                .padding(1.5.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(blockColor)
                .border(1.dp, blockColor.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                .clickable { onSessionClick(session) }
                .padding(horizontal = if (isWeekView) 2.dp else 5.dp, vertical = 3.dp)
            ) {
                Column {
                    Text("${session.startTime.toStorageString()}–${session.endTime.toStorageString()}",
                        fontSize = if (isWeekView) 8.sp else 10.sp, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
                    if (!isWeekView && names.isNotBlank())
                        Text(names, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    else if (isWeekView && names.isNotBlank())
                        Text(names, fontSize = 9.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (isPast) Box(modifier = Modifier.fillMaxSize().background(PAST_OVERLAY))
    }
}

private fun assignColumns(sessions: List<Session>): Map<Long, Pair<Int, Int>> {
    val sorted = sessions.sortedBy { it.startTime.toMinutes() }
    val cols = mutableMapOf<Long, Int>()
    val colEnds = mutableListOf<Int>()
    sorted.forEach { s ->
        val start = s.startTime.toMinutes()
        val col = colEnds.indexOfFirst { it <= start }.takeIf { it >= 0 } ?: run { colEnds.add(0); colEnds.lastIndex }
        cols[s.id] = col
        colEnds[col] = s.endTime.toMinutes()
    }
    val maxCol = (cols.values.maxOrNull() ?: 0) + 1
    return cols.mapValues { (_, col) -> col to maxCol }
}

// ─────────────────────────────────────────────
// Session context menu
// ─────────────────────────────────────────────

@Composable
private fun SessionContextMenu(
    session: Session,
    clients: List<Client>,
    location: Location?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                location?.let { Box(Modifier.size(12.dp).clip(CircleShape).background(hexToColor(it.colorHex))) }
                Text("${session.startTime.toStorageString()} – ${session.endTime.toStorageString()}", fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Show at most 4 clients to keep dialog compact
                clients.take(4).forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(hexToColor(c.colorHex)))
                        Text(c.name, fontSize = 14.sp)
                    }
                }
                if (clients.size > 4) Text("...ще ${clients.size - 4}", fontSize = 12.sp, color = Color.Gray)
                location?.let { Text(it.name, fontSize = 13.sp, color = Color.Gray) }
                if (session.notes.isNotBlank()) Text(session.notes, fontSize = 13.sp, color = Color.Gray)
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("Редагувати") } },
        dismissButton = {
            Row {
                TextButton(onClick = onCopy) { Text("Копіювати") }
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Видалити") }
            }
        }
    )
}

// ─────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────

fun buildFreeTimeText(date: LocalDate, freeSlots: List<FreeSlot>, locationsById: Map<Long, Location>): String {
    val dow = DAYS_UK[date.dayOfWeek.ordinal]
    val dateStr = "${date.dayOfMonth} ${MONTHS_UK_SHORT[date.month.ordinal]} ${date.year}"
    return buildString {
        appendLine("🕐 Вільний час — $dow, $dateStr:")
        appendLine()
        freeSlots.forEach { free ->
            val loc = free.locationId?.let { locationsById[it] }
            val locPart = if (loc != null) "  (${loc.name})" else ""
            appendLine("• ${free.startTime.toStorageString()} – ${free.endTime.toStorageString()}$locPart")
        }
        appendLine()
        append("Для запису — напишіть у особисті 📅")
    }.trim()
}
