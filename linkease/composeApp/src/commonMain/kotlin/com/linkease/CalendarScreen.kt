package com.linkease

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
val MONTHS_UK_FULL = listOf(
    "Січень", "Лютий", "Березень", "Квітень", "Травень", "Червень",
    "Липень", "Серпень", "Вересень", "Жовтень", "Листопад", "Грудень"
)

val HOUR_HEIGHT = 64.dp
private val TIME_COL_WIDTH  = 48.dp

private val WEEKEND_BG        = Color(0xFFFAFAFA)
private val WEEKEND_HEADER    = Color(0xFFBF360C)
private val PAST_OVERLAY      = Color(0x14000000)
private val FREE_SLOT_DEFAULT = Color(0xFF2E7D32)
private val DISABLED_ZONE     = Color(0x18000000)
private val NOW_LINE_COLOR    = Color(0xFFD50000)

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
    hoursStart: Int = CALENDAR_HOURS_START,
    hoursEnd: Int = CALENDAR_HOURS_END,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onGoToToday: () -> Unit,
    onViewChange: (CalendarView) -> Unit,
    selectedDay: LocalDate? = null,
    onAddSession: (date: LocalDate, startTime: LocalTime) -> Unit,
    onAddSessionFromSlot: (date: LocalDate, startTime: LocalTime, endTime: LocalTime) -> Unit,
    onEditSession: (Session) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onCopySession: (Session) -> Unit,
    onAvailabilityClick: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onShareFreeTime: (date: LocalDate, text: String) -> Unit,
    onDayClickInMonth: (LocalDate) -> Unit,
    onDayClickInThreeDay: ((LocalDate) -> Unit)? = null,
    onPinToStatusBar: (() -> Unit)? = null,
    onExportDayToCalendar: ((date: LocalDate, sessions: List<Session>, clients: List<Client>, locations: List<Location>) -> Unit)? = null,
    compactModeInitial: Boolean = true,
    onCompactModeChange: ((Boolean) -> Unit)? = null,
    onCopyToClipboard: ((String) -> Unit)? = null,
    onShareFreeTimeImage: ((title: String, lines: List<ScheduleImageLine>) -> Unit)? = null,
    onCopyFreeTimeImage: ((title: String, lines: List<ScheduleImageLine>) -> Unit)? = null,
) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val clientsById = clients.associateBy { it.id }
    val locationsById = locations.associateBy { it.id }

    val numDays = when (currentView) {
        CalendarView.DAY       -> 1
        CalendarView.THREE_DAY -> 3
        CalendarView.MONTH     -> 0
    }
    val days = if (numDays > 0) (0 until numDays).map { startDate.plus(it, DateTimeUnit.DAY) } else emptyList()

    val includesTODAY = when (currentView) {
        CalendarView.MONTH -> startDate.year == today.year && startDate.month == today.month
        else               -> days.any { it == today }
    }

    val scrollState = rememberScrollState()
    var sessionMenu by remember { mutableStateOf<Session?>(null) }
    var swipeDelta by remember { mutableFloatStateOf(0f) }
    var showFreeChips by remember { mutableStateOf(false) }
    var compactMode by remember { mutableStateOf(compactModeInitial) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Free chips: current period (visible days), clamped to working hours
    val freeChips = remember(days, sessions, availability, hoursStart, hoursEnd) {
        days.flatMap { day ->
            val daySessions = sessions.filter { it.date == day }
            calculateFreeSlots(daySessions, availability, day, hoursStart, hoursEnd)
                .map { Pair(day, it) }
        }
    }

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
                )
                ViewSelector(
                    currentView = currentView,
                    onViewChange = onViewChange,
                    compactMode = compactMode,
                    onToggleCompact = if (currentView == CalendarView.DAY) {{
                        val newMode = !compactMode
                        compactMode = newMode
                        onCompactModeChange?.invoke(newMode)
                    }} else null,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
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
                            days = days,
                            locationsById = locationsById,
                            onChipClick = { date, start, end ->
                                showFreeChips = false
                                onAddSessionFromSlot(date, start, end)
                            },
                            onShare = { text -> onShareFreeTime(days.firstOrNull() ?: today, text) },
                            onCopy = onCopyToClipboard,
                            onShareImage = onShareFreeTimeImage,
                            onCopyImage = onCopyFreeTimeImage,
                        )
                    }
                }
                BottomBar(
                    showFreeChipsActive = showFreeChips && currentView != CalendarView.MONTH,
                    showFreeTimeItem = currentView != CalendarView.MONTH,
                    onSettingsClick = onSettingsClick,
                    onAvailabilityClick = onAvailabilityClick,
                    onFreeTimeClick = { showFreeChips = !showFreeChips },
                    onCreateClick = {
                        val targetDay = when {
                            currentView != CalendarView.MONTH -> startDate
                            selectedDay != null -> selectedDay
                            else -> today
                        }
                        val desiredStart = if (targetDay == today) {
                            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
                            val mins = now.hour * 60 + now.minute
                            minutesToLocalTime(((mins + 29) / 30) * 30)
                        } else LocalTime(9, 0)
                        onAddSession(targetDay, findNextAvailableStart(sessions, targetDay, desiredStart))
                    },
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
                CalendarView.DAY, CalendarView.THREE_DAY -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (compactMode && currentView == CalendarView.DAY) {
                            // Compact card list
                            CompactDayView(
                                days = days,
                                today = today,
                                sessions = sessions,
                                clientsById = clientsById,
                                locationsById = locationsById,
                                onSessionClick = { sessionMenu = it },
                                onAddSession = { day ->
                                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
                                    val mins = now.hour * 60 + now.minute
                                    val desired = if (day == today)
                                        minutesToLocalTime(((mins + 29) / 30) * 30)
                                    else LocalTime(9, 0)
                                    onAddSession(day, findNextAvailableStart(sessions, day, desired))
                                },
                            )
                        } else {
                            // Time-grid view
                            if (currentView == CalendarView.THREE_DAY) {
                                DayHeaderRow(days = days, today = today, onDayClick = onDayClickInThreeDay)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                            }
                            Row(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
                                HourLabels(hoursStart = hoursStart, hoursEnd = hoursEnd)
                                days.forEach { day ->
                                    val daySessions = sessions.filter { it.date == day }
                                    val freeSlots = calculateFreeSlots(daySessions, availability, day, hoursStart, hoursEnd)
                                    DayColumn(
                                        date = day, today = today,
                                        sessions = daySessions,
                                        freeSlots = freeSlots,
                                        availability = availability,
                                        locationsById = locationsById, clientsById = clientsById,
                                        hoursStart = hoursStart, hoursEnd = hoursEnd,
                                        modifier = Modifier.weight(1f),
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
) {
    val isExactToday = currentView == CalendarView.DAY && startDate == today
    val label = when (currentView) {
        CalendarView.MONTH -> "${MONTHS_UK_FULL[startDate.month.ordinal]} ${startDate.year}"
        CalendarView.DAY   -> "${DAYS_UK[startDate.dayOfWeek.ordinal]}, ${startDate.dayOfMonth} ${MONTHS_UK_SHORT[startDate.month.ordinal]} ${startDate.year}"
        CalendarView.THREE_DAY -> {
            val last = startDate.plus(2, DateTimeUnit.DAY)
            buildString {
                append(startDate.dayOfMonth)
                if (startDate.month != last.month) append(" ${MONTHS_UK_SHORT[startDate.month.ordinal]}")
                append(" – ${last.dayOfMonth} ${MONTHS_UK_SHORT[last.month.ordinal]} ${last.year}")
            }
        }
    }
    val titleColor by animateColorAsState(
        targetValue = if (isExactToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300), label = "titleColor"
    )

    CenterAlignedTopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Text("◀", fontSize = 16.sp) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label,
                        fontWeight = if (isExactToday) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = if (isExactToday) 14.sp else 13.sp,
                        color = titleColor)
                    if (isExactToday) {
                        Text("Сьогодні", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                            fontWeight = FontWeight.Medium)
                    }
                }
                IconButton(onClick = onNext) { Text("▶", fontSize = 16.sp) }
            }
        },
        actions = {
            if (!includesTODAY) {
                TextButton(onClick = onGoToToday, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${today.dayOfMonth}", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
                        }
                        Text("Сьогодні", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    }
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

// ─────────────────────────────────────────────
// View selector
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewSelector(
    currentView: CalendarView,
    onViewChange: (CalendarView) -> Unit,
    compactMode: Boolean = false,
    onToggleCompact: (() -> Unit)? = null,
) {
    val views = CalendarView.entries
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            views.forEachIndexed { i, view ->
                SegmentedButton(
                    selected = currentView == view,
                    onClick = { onViewChange(view) },
                    shape = SegmentedButtonDefaults.itemShape(i, views.size),
                    label = { Text(view.label, fontSize = 13.sp) }
                )
            }
        }
        if (onToggleCompact != null) {
            Spacer(Modifier.width(4.dp))
            IconToggleButton(
                checked = compactMode,
                onCheckedChange = { onToggleCompact() },
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.iconToggleButtonColors(
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.primary,
                )
            ) {
                Text(if (compactMode) "≡" else "☰", fontSize = 18.sp,
                    color = if (compactMode) MaterialTheme.colorScheme.primary else Color.Gray)
            }
        }
    }
}

// ─────────────────────────────────────────────
// Compact day view
// ─────────────────────────────────────────────

@Composable
private fun CompactDayView(
    days: List<LocalDate>,
    today: LocalDate,
    sessions: List<Session>,
    clientsById: Map<Long, Client>,
    locationsById: Map<Long, Location>,
    onSessionClick: (Session) -> Unit,
    onAddSession: (LocalDate) -> Unit,
) {
    if (days.size > 1) {
        // THREE_DAY: side-by-side columns sharing a single vertical scroll
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(modifier = Modifier.fillMaxWidth()) {
                days.forEach { day ->
                    val daySessions = sessions.filter { it.date == day }.sortedBy { it.startTime.toMinutes() }
                    val isToday = day == today
                    val isWeekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
                    val circleBgCompact by animateColorAsState(
                        targetValue = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                        animationSpec = tween(300), label = "todayCircleCompact"
                    )
                    Column(
                        modifier = Modifier.weight(1f)
                            .background(if (isWeekend) WEEKEND_BG else Color.Transparent)
                    ) {
                        // Day header
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(DAYS_UK[day.dayOfWeek.ordinal],
                                fontSize = if (isToday) 11.sp else 10.sp,
                                color = if (isToday) MaterialTheme.colorScheme.primary
                                        else if (isWeekend) WEEKEND_HEADER else Color(0xFF757575),
                                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(if (isToday) 34.dp else 28.dp)
                                    .clip(CircleShape)
                                    .background(circleBgCompact),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${day.dayOfMonth}",
                                    fontSize = if (isToday) 16.sp else 14.sp,
                                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (isToday) Color.White
                                            else if (isWeekend) WEEKEND_HEADER else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        Column(
                            modifier = Modifier.padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (daySessions.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onAddSession(day) }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text("+", fontSize = 20.sp, color = Color.Gray.copy(alpha = 0.4f)) }
                            } else {
                                daySessions.forEach { session ->
                                    CompactSessionCard(session, clientsById, locationsById, onClick = { onSessionClick(session) })
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // DAY: single scrollable column
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val day = days.firstOrNull() ?: return@Column
            val daySessions = sessions.filter { it.date == day }.sortedBy { it.startTime.toMinutes() }
            if (daySessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { onAddSession(day) }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) { Text("+ Додати сесію", fontSize = 13.sp, color = Color.Gray) }
            } else {
                daySessions.forEach { session ->
                    CompactSessionCard(session, clientsById, locationsById, onClick = { onSessionClick(session) })
                }
            }
        }
    }
}

@Composable
private fun CompactSessionCard(
    session: Session,
    clientsById: Map<Long, Client>,
    locationsById: Map<Long, Location>,
    onClick: () -> Unit,
) {
    val sessionClients = session.clientIds.mapNotNull { clientsById[it] }
    val location = session.locationId?.let { locationsById[it] }
    val blockColor = location?.let { hexToColor(it.colorHex) }
        ?: sessionClients.firstOrNull()?.let { hexToColor(it.colorHex) }
        ?: Color(0xFF1A237E)
    val durationMin = session.endTime.toMinutes() - session.startTime.toMinutes()
    val clientNames = sessionClients.joinToString(", ") { it.name }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(blockColor.copy(alpha = 0.08f))
            .border(1.dp, blockColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left accent bar
        Box(Modifier.width(3.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(blockColor))
        // Time column
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(48.dp)) {
            Text(session.startTime.toStorageString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = blockColor)
            Text(session.endTime.toStorageString(), fontSize = 11.sp, color = blockColor.copy(alpha = 0.65f))
        }
        // Content
        Column(modifier = Modifier.weight(1f)) {
            if (clientNames.isNotEmpty())
                Text(clientNames, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1C1C1E))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatDuration(durationMin), fontSize = 11.sp, color = Color.Gray)
                if (location != null) Text(location.name, fontSize = 11.sp, color = Color.Gray)
            }
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
    onFreeTimeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAvailabilityClick: () -> Unit,
    onCreateClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A2E))
            .navigationBarsPadding()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Settings – bright amber icon to stand out
        BottomNavItem(modifier = Modifier.weight(1f), onClick = onSettingsClick, label = "Налаштування") {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFFFCA28).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text("⚙", fontSize = 17.sp, color = Color(0xFFFFCA28))
            }
        }
        // Create
        BottomNavItem(modifier = Modifier.weight(1f), onClick = onCreateClick, label = "Створити") {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        // Free time (hidden in Month view)
        if (showFreeTimeItem) {
            BottomNavItem(
                modifier = Modifier.weight(1f),
                onClick = onFreeTimeClick,
                label = "Вільний час",
                selected = showFreeChipsActive,
            ) {
                Text(if (showFreeChipsActive) "🟢" else "⏰", fontSize = 20.sp)
            }
        }
        // Availability (rightmost)
        BottomNavItem(modifier = Modifier.weight(1f), onClick = onAvailabilityClick, label = "Майб. доступність") {
            Text("📅", fontSize = 20.sp)
        }
    }
}

@Composable
private fun BottomNavItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    label: String,
    selected: Boolean = false,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        icon()
        Text(
            label, fontSize = 10.sp, textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = if (selected) 1f else 0.7f),
            lineHeight = 12.sp
        )
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
    days: List<LocalDate>,
    locationsById: Map<Long, Location>,
    onChipClick: (LocalDate, LocalTime, LocalTime) -> Unit,
    onShare: (String) -> Unit,
    onCopy: ((String) -> Unit)?,
    onShareImage: ((title: String, lines: List<ScheduleImageLine>) -> Unit)? = null,
    onCopyImage: ((title: String, lines: List<ScheduleImageLine>) -> Unit)? = null,
) {
    val periodText = buildPeriodFreeTimeText(days, chips, locationsById)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 4.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Вільний час", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                if (chips.isNotEmpty()) {
                    val imgTitle = buildPeriodTitle(days)
                    val imgLines = buildImageLines(days, chips, locationsById)
                    TextButton(
                        onClick = {
                            if (onCopyImage != null) onCopyImage(imgTitle, imgLines)
                            else onCopy?.invoke(periodText)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) { Text("📋 Копіювати", fontSize = 12.sp) }
                    TextButton(
                        onClick = {
                            if (onShareImage != null) onShareImage(imgTitle, imgLines)
                            else onShare(periodText)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) { Text("📤 Поширити", fontSize = 12.sp) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            if (chips.isEmpty()) {
                Text(
                    "Немає вільного часу на поточний період",
                    fontSize = 13.sp, color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            } else {
                // Vertical scrollable list of free slots grouped by day
                Column(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    days.forEach { day ->
                        val daySlots = chips.filter { it.first == day }
                        if (daySlots.isNotEmpty()) {
                            Text(
                                "${DAYS_UK[day.dayOfWeek.ordinal]}, ${day.dayOfMonth} ${MONTHS_UK_SHORT[day.month.ordinal]}",
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            daySlots.forEach { (_, slot) ->
                                val slotColor = slot.locationId?.let { locationsById[it] }
                                    ?.let { hexToColor(it.colorHex) } ?: FREE_SLOT_DEFAULT
                                val durationMin = slot.endTime.toMinutes() - slot.startTime.toMinutes()
                                val durStr = formatDuration(durationMin)
                                val locName = slot.locationId?.let { locationsById[it]?.name }
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { onChipClick(day, slot.startTime, slot.endTime) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(slotColor))
                                    Text(
                                        "${slot.startTime.toStorageString()} – ${slot.endTime.toStorageString()}",
                                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(durStr, fontSize = 11.sp, color = Color.Gray)
                                    if (locName != null) Text(locName, fontSize = 11.sp, color = slotColor)
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildPeriodFreeTimeText(
    days: List<LocalDate>,
    chips: List<Pair<LocalDate, FreeSlot>>,
    locationsById: Map<Long, Location>,
): String = buildString {
    appendLine("Вільний час:")
    days.forEach { day ->
        val dayChips = chips.filter { it.first == day }
        if (dayChips.isNotEmpty()) {
            val label = "${DAYS_UK[day.dayOfWeek.ordinal]}, ${day.dayOfMonth} ${MONTHS_UK_SHORT[day.month.ordinal]}:"
            appendLine(label)
            dayChips.forEach { (_, slot) ->
                val locName = slot.locationId?.let { locationsById[it]?.name }
                val locStr = if (locName != null) " ($locName)" else ""
                appendLine("  • ${slot.startTime.toStorageString()}–${slot.endTime.toStorageString()}$locStr")
            }
        }
    }
}.trimEnd()

private fun buildPeriodTitle(days: List<LocalDate>): String {
    if (days.isEmpty()) return "Вільний час"
    val first = days.first()
    val last = days.last()
    return if (first == last) "${first.dayOfMonth} ${MONTHS_UK_SHORT[first.month.ordinal]} ${first.year}"
    else "${first.dayOfMonth}–${last.dayOfMonth} ${MONTHS_UK_SHORT[last.month.ordinal]} ${last.year}"
}

private fun buildImageLines(
    days: List<LocalDate>,
    chips: List<Pair<LocalDate, FreeSlot>>,
    locationsById: Map<Long, Location>,
): List<ScheduleImageLine> {
    val result = mutableListOf<ScheduleImageLine>()
    days.forEach { day ->
        val dayChips = chips.filter { it.first == day }
        if (dayChips.isNotEmpty()) {
            result.add(ScheduleImageLine("${DAYS_UK[day.dayOfWeek.ordinal]}, ${day.dayOfMonth} ${MONTHS_UK_SHORT[day.month.ordinal]}", isHeader = true))
            dayChips.forEach { (_, slot) ->
                val durationMin = slot.endTime.toMinutes() - slot.startTime.toMinutes()
                val dur = formatDuration(durationMin)
                val loc = slot.locationId?.let { locationsById[it] }
                val locStr = if (loc != null) "  ${loc.name}" else ""
                result.add(ScheduleImageLine(
                    text = "${slot.startTime.toStorageString()} – ${slot.endTime.toStorageString()}  ($dur)$locStr",
                    isHeader = false,
                    colorHex = loc?.colorHex,
                ))
            }
        }
    }
    return result
}

// ─────────────────────────────────────────────
// Day header row
// ─────────────────────────────────────────────

@Composable
private fun DayHeaderRow(
    days: List<LocalDate>,
    today: LocalDate,
    onDayClick: ((LocalDate) -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Spacer(Modifier.width(TIME_COL_WIDTH))
        days.forEach { day ->
            val isToday   = day == today
            val isPast    = day < today
            val isWeekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
            val circleBg by animateColorAsState(
                targetValue = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(300), label = "todayCircle"
            )
            val dayNumColor by animateColorAsState(
                targetValue = when {
                    isToday   -> Color.White
                    isPast    -> Color(0xFFBDBDBD)
                    isWeekend -> WEEKEND_HEADER
                    else      -> MaterialTheme.colorScheme.onSurface
                },
                animationSpec = tween(300), label = "dayNum"
            )
            Column(
                modifier = Modifier.weight(1f)
                    .background(if (isWeekend) WEEKEND_BG else Color.Transparent)
                    .then(if (onDayClick != null) Modifier.clickable { onDayClick(day) } else Modifier)
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(DAYS_UK[day.dayOfWeek.ordinal],
                    fontSize = if (isToday) 11.sp else 10.sp,
                    color = when {
                        isToday   -> MaterialTheme.colorScheme.primary
                        isWeekend -> WEEKEND_HEADER
                        isPast    -> Color(0xFFBDBDBD)
                        else      -> Color(0xFF757575)
                    },
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(if (isToday) 34.dp else 28.dp)
                        .clip(CircleShape)
                        .background(circleBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${day.dayOfMonth}",
                        fontSize = if (isToday) 16.sp else 14.sp,
                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                        color = dayNumColor)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Hour labels
// ─────────────────────────────────────────────

@Composable
private fun HourLabels(hoursStart: Int, hoursEnd: Int) {
    Column(modifier = Modifier.width(TIME_COL_WIDTH)) {
        Spacer(Modifier.height(HOUR_HEIGHT / 2))
        for (h in hoursStart..hoursEnd) {
            Box(modifier = Modifier.height(HOUR_HEIGHT), contentAlignment = Alignment.TopEnd) {
                Text(
                    "${h.toString().padStart(2, '0')}:00",
                    fontSize = 10.sp, color = Color(0xFF70757A),
                    modifier = Modifier.padding(end = 6.dp)
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
    hoursStart: Int,
    hoursEnd: Int,
    modifier: Modifier = Modifier,
    onEmptyClick: (LocalTime) -> Unit,
    onSlotClick: (LocalTime, LocalTime) -> Unit,
    onOutOfScope: () -> Unit,
    onSessionClick: (Session) -> Unit,
) {
    val isPast     = date < today
    val isWeekend  = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    val totalHours = hoursEnd - hoursStart + 1
    val density    = LocalDensity.current

    val tz = TimeZone.currentSystemDefault()
    val nowTime = remember { Clock.System.now().toLocalDateTime(tz).time }
    val nowMin  = nowTime.toMinutes()

    val dayAvail = remember(date, availability) {
        availability.filter { it.date == date }
    }

    val disabledRanges = remember(dayAvail, hoursStart, hoursEnd) {
        if (dayAvail.isEmpty()) emptyList()
        else {
            val sorted = dayAvail.sortedBy { it.startTime.toMinutes() }
            val ranges = mutableListOf<Pair<Int, Int>>()
            var cursor = hoursStart * 60
            sorted.forEach { slot ->
                if (cursor < slot.startTime.toMinutes()) ranges.add(cursor to slot.startTime.toMinutes())
                cursor = maxOf(cursor, slot.endTime.toMinutes())
            }
            if (cursor < (hoursEnd + 1) * 60) ranges.add(cursor to (hoursEnd + 1) * 60)
            ranges
        }
    }

    Box(
        modifier = modifier
            .height(HOUR_HEIGHT * totalHours)
            .background(if (isWeekend) WEEKEND_BG else Color.White)
            .pointerInput(date, dayAvail, freeSlots, sessions, hoursStart, hoursEnd) {
                detectTapGestures { offset ->
                    val hourHeightPx = with(density) { HOUR_HEIGHT.toPx() }
                    val minuteFromStart = ((offset.y / hourHeightPx) * 60).toInt()
                    val rounded  = (minuteFromStart / 15) * 15
                    val totalMin = (hoursStart * 60 + rounded).coerceIn(hoursStart * 60, hoursEnd * 60)
                    val clickedTime = minutesToLocalTime(totalMin)

                    if (sessions.any { s ->
                        clickedTime.toMinutes() >= s.startTime.toMinutes() &&
                        clickedTime.toMinutes() < s.endTime.toMinutes()
                    }) return@detectTapGestures

                    if (dayAvail.isNotEmpty() && dayAvail.none { slot ->
                        clickedTime.toMinutes() >= slot.startTime.toMinutes() &&
                        clickedTime.toMinutes() < slot.endTime.toMinutes()
                    }) { onOutOfScope(); return@detectTapGestures }

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
        // Hour grid lines
        for (i in 0..totalHours) {
            HorizontalDivider(
                modifier = Modifier.offset(y = HOUR_HEIGHT * i),
                color = Color(0xFFDADCE0), thickness = 0.5.dp
            )
        }
        for (i in 0 until totalHours) {
            HorizontalDivider(
                modifier = Modifier.offset(y = HOUR_HEIGHT * i + HOUR_HEIGHT / 2).padding(start = 8.dp),
                color = Color(0xFFE8EAED), thickness = 0.5.dp
            )
        }

        // Disabled zones
        disabledRanges.forEach { (startMin, endMin) ->
            val s = (startMin - hoursStart * 60).coerceAtLeast(0)
            val e = (endMin   - hoursStart * 60).coerceAtMost(totalHours * 60)
            if (e > s) Box(
                modifier = Modifier.fillMaxWidth()
                    .offset(y = HOUR_HEIGHT * s.toFloat() / 60f)
                    .height(HOUR_HEIGHT * (e - s).toFloat() / 60f)
                    .background(DISABLED_ZONE)
            )
        }

        // Free-time blocks
        freeSlots.forEach { free ->
            val startMin = (free.startTime.toMinutes() - hoursStart * 60).coerceAtLeast(0)
            val endMin   = (free.endTime.toMinutes()   - hoursStart * 60).coerceAtMost(totalHours * 60)
            if (endMin <= 0 || startMin >= totalHours * 60) return@forEach
            val freeColor = free.locationId?.let { locationsById[it] }?.let { hexToColor(it.colorHex) } ?: FREE_SLOT_DEFAULT
            Box(
                modifier = Modifier.fillMaxWidth()
                    .offset(y = HOUR_HEIGHT * startMin.toFloat() / 60f)
                    .height(HOUR_HEIGHT * (endMin - startMin).toFloat() / 60f)
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(freeColor.copy(alpha = 0.10f))
                    .border(1.dp, freeColor.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            )
        }

        // Sessions — full width, Google Calendar style
        sessions.forEach { session ->
            val startMin   = (session.startTime.toMinutes() - hoursStart * 60).coerceAtLeast(0)
            val endMin     = (session.endTime.toMinutes()   - hoursStart * 60).coerceAtMost(totalHours * 60)
            if (endMin <= 0 || startMin >= totalHours * 60) return@forEach
            val topFrac    = startMin.toFloat() / 60f
            val heightFrac = (endMin - startMin).toFloat() / 60f
            val location   = session.locationId?.let { locationsById[it] }
            val baseColor  = hexToColor(location?.colorHex ?: "#3949AB")
            val blockColor = if (isPast) baseColor.copy(alpha = 0.45f) else baseColor
            val names      = session.clientIds.mapNotNull { clientsById[it]?.name }.joinToString(", ")
            val timeLabel  = "${session.startTime.toStorageString()} – ${session.endTime.toStorageString()}"
            val blockHeight = HOUR_HEIGHT * heightFrac

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .absoluteOffset(y = HOUR_HEIGHT * topFrac)
                    .height(blockHeight)
                    .padding(horizontal = 2.dp, vertical = 1.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(blockColor.copy(alpha = 0.15f))
                    .border(1.dp, blockColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .clickable { onSessionClick(session) }
            ) {
                // Left accent bar
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(blockColor)
                )
                Column(
                    modifier = Modifier.padding(start = 8.dp, top = 3.dp, end = 4.dp, bottom = 3.dp)
                ) {
                    if (names.isNotBlank())
                        Text(
                            names,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = blockColor,
                            maxLines = if (blockHeight > 40.dp) 2 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    if (blockHeight > 30.dp)
                        Text(
                            timeLabel,
                            fontSize = 11.sp,
                            color = blockColor.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    if (blockHeight > 52.dp && location != null)
                        Text(
                            location.name,
                            fontSize = 11.sp,
                            color = blockColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                }
            }
        }

        // Current-time indicator
        if (date == today) {
            val nowOffset = (nowMin - hoursStart * 60).coerceIn(0, totalHours * 60)
            val yPos = HOUR_HEIGHT * nowOffset.toFloat() / 60f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .absoluteOffset(y = yPos - 1.dp)
                    .height(2.dp)
                    .background(NOW_LINE_COLOR)
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .absoluteOffset(x = (-3).dp, y = yPos - 5.dp)
                    .clip(CircleShape)
                    .background(NOW_LINE_COLOR)
            )
        }

        if (isPast) Box(modifier = Modifier.fillMaxSize().background(PAST_OVERLAY))
    }
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
