package com.linkease

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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

private val DAYS_AVAIL_FULL = listOf("Понеділок","Вівторок","Середа","Четвер","П'ятниця","Субота","Неділя")
private val DAYS_AVAIL_SHORT = listOf("Пн","Вт","Ср","Чт","Пт","Сб","Нд")
private val MONTHS_AVAIL_FULL = listOf(
    "Січень","Лютий","Березень","Квітень","Травень","Червень",
    "Липень","Серпень","Вересень","Жовтень","Листопад","Грудень"
)
private val MONTHS_AVAIL_SHORT = listOf(
    "Січ","Лют","Бер","Кві","Тра","Чер","Лип","Сер","Вер","Жов","Лис","Гру"
)

private val AVAIL_DEFAULT_BG = Color(0x14008000)   // subtle green: "free by default"
private val AVAIL_DISABLED   = Color(0x18000000)   // gray: outside configured window
private val AVAIL_TIME_COL   = 48.dp

// Virtual page range for the continuous agenda, anchored at "today" — same trick as
// CalendarScreen's day/month pagers, just driven by a vertical LazyColumn instead.
private const val AGENDA_PAGE_COUNT = 20001
private const val AGENDA_CENTER = AGENDA_PAGE_COUNT / 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilityScreen(
    availability: List<AvailabilitySlot>,
    locations: List<Location>,
    sessions: List<Session> = emptyList(),
    clients: List<Client> = emptyList(),
    hoursStart: Int = CALENDAR_HOURS_START,
    hoursEnd: Int = CALENDAR_HOURS_END,
    scheduleModeInitial: Boolean = true,
    onSettingsClick: () -> Unit,
    onAvailabilityNavClick: () -> Unit,
    onCreateClick: () -> Unit,
    onFreeTimeClick: () -> Unit,
    onSave: (date: LocalDate, start: LocalTime, end: LocalTime, locationId: Long?) -> Unit,
    onUpdate: (AvailabilitySlot) -> Unit,
    onDelete: (Long) -> Unit,
    onShareSchedule: (String) -> Unit,
    onCopySchedule: ((String) -> Unit)? = null,
    onShareAvailabilityImage: ((title: String, lines: List<ScheduleImageLine>) -> Unit)? = null,
    onCopyAvailabilityImage: ((title: String, lines: List<ScheduleImageLine>) -> Unit)? = null,
    onSessionClick: ((Session) -> Unit)? = null,
    onAddSession: ((LocalDate) -> Unit)? = null,
) {
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    // Default to a Google-Calendar-style continuous agenda of the real schedule; the
    // availability slot editor (time grid / month) is reached via a button and toggles this off.
    var scheduleMode by remember { mutableStateOf(scheduleModeInitial) }
    var currentView by remember { mutableStateOf(CalendarView.THREE_DAY) }
    var startDate by remember { mutableStateOf(today) }
    val clientsById = remember(clients) { clients.associateBy { it.id } }
    val locationsById = remember(locations) { locations.associateBy { it.id } }

    // Agenda filters — client / location, only applied in schedule mode.
    var filterClientIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var filterLocationIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val filteredSessions = remember(sessions, filterClientIds, filterLocationIds) {
        if (filterClientIds.isEmpty() && filterLocationIds.isEmpty()) sessions
        else sessions.filter { s ->
            (filterClientIds.isEmpty() || s.clientIds.any { it in filterClientIds }) &&
            (filterLocationIds.isEmpty() || s.locationId in filterLocationIds)
        }
    }
    val sessionsByDate = remember(filteredSessions) { filteredSessions.groupBy { it.date } }

    // Agenda scroll state — hoisted so the top bar's "Сьогодні" action can jump it.
    val agendaListState = rememberLazyListState(initialFirstVisibleItemIndex = AGENDA_CENTER)
    val agendaScope = rememberCoroutineScope()

    fun setScheduleMode(enabled: Boolean) {
        scheduleMode = enabled
        if (enabled && currentView == CalendarView.MONTH) {
            currentView = CalendarView.THREE_DAY
            startDate = today
        }
    }

    // Edit/add state
    var editingSlot by remember { mutableStateOf<AvailabilitySlot?>(null) }
    var addingForDate by remember { mutableStateOf<LocalDate?>(null) }
    var addingDefaultStart by remember { mutableStateOf(LocalTime(hoursStart, 0)) }
    var dayDialogDate by remember { mutableStateOf<LocalDate?>(null) } // month-view day tap

    val days: List<LocalDate> = remember(currentView, startDate) {
        when (currentView) {
            CalendarView.DAY -> listOf(startDate)
            CalendarView.THREE_DAY -> (0..2).map { startDate.plus(it, DateTimeUnit.DAY) }
            CalendarView.MONTH -> {
                val first = LocalDate(startDate.year, startDate.month, 1)
                val last = first.plus(1, DateTimeUnit.MONTH).let { LocalDate(it.year, it.month, 1).minus(1, DateTimeUnit.DAY) }
                (1..last.dayOfMonth).map { LocalDate(first.year, first.month, it) }
            }
        }
    }

    // Editor-mode arrow nav — same ±1-day-even-in-THREE_DAY convention as CalendarScreen,
    // so the top-bar arrows always step in sync with the swipe-pager granularity below.
    fun navPrev() {
        startDate = when (currentView) {
            CalendarView.DAY, CalendarView.THREE_DAY -> startDate.minus(1, DateTimeUnit.DAY)
            CalendarView.MONTH -> LocalDate(startDate.year, startDate.month, 1).minus(1, DateTimeUnit.MONTH).let { LocalDate(it.year, it.month, 1) }
        }
    }
    fun navNext() {
        startDate = when (currentView) {
            CalendarView.DAY, CalendarView.THREE_DAY -> startDate.plus(1, DateTimeUnit.DAY)
            CalendarView.MONTH -> LocalDate(startDate.year, startDate.month, 1).plus(1, DateTimeUnit.MONTH).let { LocalDate(it.year, it.month, 1) }
        }
    }
    fun switchView(v: CalendarView) {
        currentView = v
        startDate = when (v) {
            CalendarView.MONTH -> LocalDate(today.year, today.month, 1)
            else -> today
        }
    }

    val navLabel = when (currentView) {
        CalendarView.DAY -> "${DAYS_AVAIL_SHORT[startDate.dayOfWeek.ordinal]}, ${startDate.dayOfMonth} ${MONTHS_AVAIL_SHORT[startDate.month.ordinal]} ${startDate.year}"
        CalendarView.THREE_DAY -> {
            val last = startDate.plus(2, DateTimeUnit.DAY)
            buildString {
                append(startDate.dayOfMonth)
                if (startDate.month != last.month) append(" ${MONTHS_AVAIL_SHORT[startDate.month.ordinal]}")
                append("–${last.dayOfMonth} ${MONTHS_AVAIL_SHORT[last.month.ordinal]} ${last.year}")
            }
        }
        CalendarView.MONTH -> "${MONTHS_AVAIL_FULL[startDate.month.ordinal]} ${startDate.year}"
    }

    // Share period = current view's days (only days that have slots for image; all days for text)
    val shareTitle = buildShareTitle(days, currentView, startDate)
    val shareLines = remember(days, availability, sessions, locations, hoursStart, hoursEnd) {
        buildAvailabilityImageLines(availability, sessions, locations, days, hoursStart, hoursEnd)
    }
    val shareText = remember(days, availability, sessions, locations, hoursStart, hoursEnd) {
        buildPeriodText(availability, sessions, locations, days, hoursStart, hoursEnd)
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                if (scheduleMode) {
                    // Continuous agenda — no prev/next window to page through, just a
                    // jump back to "today" and filters for the list below.
                    CenterAlignedTopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onSettingsClick) {
                                Icon(Icons.Default.Settings, contentDescription = "Налаштування", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        title = {
                            Text("Розклад", fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary)
                        },
                        actions = {
                            TextButton(onClick = { agendaScope.launch { agendaListState.animateScrollToItem(AGENDA_CENTER) } }) {
                                Text("Сьогодні", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                    AgendaFilterRow(
                        clients = clients,
                        locations = locations,
                        filterClientIds = filterClientIds,
                        onFilterClientIdsChange = { filterClientIds = it },
                        filterLocationIds = filterLocationIds,
                        onFilterLocationIdsChange = { filterLocationIds = it },
                    )
                } else {
                    CenterAlignedTopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onSettingsClick) {
                                Icon(Icons.Default.Settings, contentDescription = "Налаштування", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Налаштування доступності", fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = ::navPrev, modifier = Modifier.size(32.dp)) { Text("◀", fontSize = 14.sp) }
                                    Text(navLabel, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                    IconButton(onClick = ::navNext, modifier = Modifier.size(32.dp)) { Text("▶", fontSize = 14.sp) }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                    val views = CalendarView.entries.toList()
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        views.forEachIndexed { i, v ->
                            SegmentedButton(
                                selected = currentView == v, onClick = { switchView(v) },
                                shape = SegmentedButtonDefaults.itemShape(i, views.size),
                                label = { Text(v.label, fontSize = 13.sp) }
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Column {
                    if (!scheduleMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { setScheduleMode(true) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text("← Розклад", fontSize = 12.sp) }
                            Spacer(Modifier.weight(1f))
                            if (onCopySchedule != null) {
                                TextButton(
                                    onClick = {
                                        if (onCopyAvailabilityImage != null && shareLines.isNotEmpty())
                                            onCopyAvailabilityImage(shareTitle, shareLines)
                                        else onCopySchedule(shareText)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) { Text("📋 Копіювати", fontSize = 12.sp) }
                            }
                            TextButton(
                                onClick = {
                                    if (onShareAvailabilityImage != null && shareLines.isNotEmpty())
                                        onShareAvailabilityImage(shareTitle, shareLines)
                                    else onShareSchedule(shareText)
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text("📤 Поширити", fontSize = 12.sp) }
                        }
                    }
                    MainBottomNav(
                        currentScreen = Screen.AVAILABILITY,
                        onHomeClick = onFreeTimeClick,
                        onAvailabilityClick = onAvailabilityNavClick,
                        onCreateClick = onCreateClick,
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (scheduleMode) {
                // Continuous Google-Calendar-style agenda, infinitely scrollable in both
                // directions and centered on "today" at first composition.
                LazyColumn(state = agendaListState, modifier = Modifier.fillMaxSize()) {
                    items(count = AGENDA_PAGE_COUNT) { index ->
                        val date = today.plus(index - AGENDA_CENTER, DateTimeUnit.DAY)
                        AgendaDaySection(
                            date = date,
                            today = today,
                            sessions = (sessionsByDate[date] ?: emptyList()).sortedBy { it.startTime.toMinutes() },
                            clientsById = clientsById,
                            locationsById = locationsById,
                            onSessionClick = { session -> onSessionClick?.invoke(session) },
                            onAddSession = { onAddSession?.invoke(date) },
                        )
                    }
                }
            } else when (currentView) {
                CalendarView.MONTH -> {
                    val monthPager = rememberMonthPagerController(startDate) { startDate = it }
                    HorizontalPager(state = monthPager.pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        val pageMonth = monthPager.pageToMonth(page)
                        AvailabilityMonthView(
                            startDate = pageMonth,
                            today = today,
                            availability = availability,
                            locations = locations,
                            onDayClick = { date -> dayDialogDate = date }
                        )
                    }
                }
                else -> {
                    // Smooth animated swipe — same shared pager controller CalendarScreen
                    // uses, so Day/3-day feels identical between the two screens.
                    val dayPager = rememberDayPagerController(startDate) { startDate = it }
                    val pagerCurrentDate = dayPager.pageToDate(dayPager.pagerState.currentPage)
                    val numDays = if (currentView == CalendarView.THREE_DAY) 3 else 1
                    val headerDays = remember(pagerCurrentDate, numDays) {
                        (0 until numDays).map { pagerCurrentDate.plus(it, DateTimeUnit.DAY) }
                    }
                    AvailabilityTimeGrid(
                        pagerState = dayPager.pagerState,
                        pageToDate = dayPager.pageToDate,
                        numDays = numDays,
                        headerDays = headerDays,
                        today = today,
                        availability = availability,
                        locations = locations,
                        hoursStart = hoursStart,
                        hoursEnd = hoursEnd,
                        onSlotClick = { slot -> editingSlot = slot },
                        onEmptyTap = { date, time ->
                            addingForDate = date
                            addingDefaultStart = time
                        },
                        onDayClick = if (currentView == CalendarView.THREE_DAY) { date ->
                            startDate = date
                            currentView = CalendarView.DAY
                        } else null,
                    )
                }
            }
        }
    }

    // Month view: day tap → list dialog for that date
    dayDialogDate?.let { date ->
        val daySlots = availability.filter { it.date == date }.sortedBy { it.startTime.toMinutes() }
        val dow = date.dayOfWeek.isoDayNumber
        val dateLabel = "${DAYS_AVAIL_SHORT[dow - 1]}, ${date.dayOfMonth} ${MONTHS_AVAIL_SHORT[date.month.ordinal]} ${date.year}"
        DayAvailabilityDialog(
            dateLabel = dateLabel,
            slots = daySlots,
            locations = locations,
            onDismiss = { dayDialogDate = null },
            onAddSlot = {
                addingForDate = date
                addingDefaultStart = daySlots.lastOrNull()?.endTime ?: LocalTime(hoursStart, 0)
                dayDialogDate = null
            },
            onEditSlot = { slot -> editingSlot = slot; dayDialogDate = null },
            onDeleteSlot = { id -> onDelete(id) }
        )
    }

    // Add new slot
    addingForDate?.let { date ->
        val existingSlots = availability.filter { it.date == date }
        val nextSlotStart = existingSlots
            .filter { it.startTime.toMinutes() > addingDefaultStart.toMinutes() }
            .minByOrNull { it.startTime.toMinutes() }?.startTime
        // Default to the full working-hours range — not a narrow +60min window — so a
        // new slot doesn't depend on tap position or existing slots to cover the whole day.
        val defaultEnd = minutesToLocalTime(nextSlotStart?.toMinutes() ?: (hoursEnd * 60))
        val dow = date.dayOfWeek.isoDayNumber
        AddEditAvailabilityDialog(
            dayLabel = "${DAYS_AVAIL_SHORT[dow - 1]}, ${date.dayOfMonth} ${MONTHS_AVAIL_SHORT[date.month.ordinal]}",
            defaultStartTime = addingDefaultStart,
            defaultEndTime = defaultEnd,
            existingSlotsOnDay = existingSlots,
            locations = locations,
            hoursStart = hoursStart,
            hoursEnd = hoursEnd,
            onDismiss = { addingForDate = null },
            onConfirm = { start, end, locationId ->
                onSave(date, start, end, locationId)
                addingForDate = null
            }
        )
    }

    // Edit existing slot
    editingSlot?.let { slot ->
        val dow = slot.date.dayOfWeek.isoDayNumber
        val existingSlots = availability.filter { it.date == slot.date }
        AddEditAvailabilityDialog(
            dayLabel = "${DAYS_AVAIL_SHORT[dow - 1]}, ${slot.date.dayOfMonth} ${MONTHS_AVAIL_SHORT[slot.date.month.ordinal]}",
            initial = slot,
            existingSlotsOnDay = existingSlots,
            locations = locations,
            hoursStart = hoursStart,
            hoursEnd = hoursEnd,
            onDelete = { onDelete(slot.id); editingSlot = null },
            onDismiss = { editingSlot = null },
            onConfirm = { start, end, locationId ->
                onUpdate(slot.copy(startTime = start, endTime = end, locationId = locationId))
                editingSlot = null
            }
        )
    }
}

// ─────────────────────────────────────────────
// Continuous agenda (schedule mode)
// ─────────────────────────────────────────────

@Composable
private fun AgendaDaySection(
    date: LocalDate,
    today: LocalDate,
    sessions: List<Session>,
    clientsById: Map<Long, Client>,
    locationsById: Map<Long, Location>,
    onSessionClick: (Session) -> Unit,
    onAddSession: () -> Unit,
) {
    val isToday = date == today
    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    Column {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f) else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(DAYS_AVAIL_SHORT[date.dayOfWeek.ordinal],
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
                    color = when {
                        isToday   -> MaterialTheme.colorScheme.primary
                        isWeekend -> Color(0xFFBF360C)
                        else      -> Color(0xFF757575)
                    })
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape)
                        .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${date.dayOfMonth}",
                        fontSize = 15.sp,
                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                        color = when {
                            isToday   -> Color.White
                            isWeekend -> Color(0xFFBF360C)
                            else      -> MaterialTheme.colorScheme.onSurface
                        })
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onAddSession)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) { Text("+ Додати", fontSize = 12.sp, color = Color.Gray.copy(alpha = 0.6f)) }
                } else {
                    sessions.forEach { session ->
                        CompactSessionCard(session, clientsById, locationsById, onClick = { onSessionClick(session) })
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgendaFilterRow(
    clients: List<Client>,
    locations: List<Location>,
    filterClientIds: Set<Long>,
    onFilterClientIdsChange: (Set<Long>) -> Unit,
    filterLocationIds: Set<Long>,
    onFilterLocationIdsChange: (Set<Long>) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Clients row ────────────────────────────────────────────────────
        if (clients.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                clients.forEach { client ->
                    val clientColor = hexToColor(client.colorHex)
                    FilterChip(
                        selected = client.id in filterClientIds,
                        onClick = {
                            onFilterClientIdsChange(
                                if (client.id in filterClientIds) filterClientIds - client.id
                                else filterClientIds + client.id
                            )
                        },
                        leadingIcon = {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(clientColor))
                        },
                        label = { Text(client.name, fontSize = 12.sp, maxLines = 1) },
                    )
                }
                if (filterClientIds.isNotEmpty()) {
                    Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color(0x33000000)))
                    InputChip(
                        selected = false,
                        onClick = { onFilterClientIdsChange(emptySet()) },
                        label = { Text("✕", fontSize = 12.sp) },
                    )
                }
            }
        }

        // ── Locations row ──────────────────────────────────────────────────
        if (locations.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                locations.forEach { loc ->
                    FilterChip(
                        selected = loc.id in filterLocationIds,
                        onClick = {
                            onFilterLocationIdsChange(
                                if (loc.id in filterLocationIds) filterLocationIds - loc.id
                                else filterLocationIds + loc.id
                            )
                        },
                        leadingIcon = { Text("📍", fontSize = 11.sp) },
                        label = { Text(loc.name, fontSize = 12.sp, maxLines = 1) },
                    )
                }
                if (filterLocationIds.isNotEmpty()) {
                    Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color(0x33000000)))
                    InputChip(
                        selected = false,
                        onClick = { onFilterLocationIdsChange(emptySet()) },
                        label = { Text("✕", fontSize = 12.sp) },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Time-grid view (Day / Three-day)
// ─────────────────────────────────────────────

@Composable
private fun AvailabilityTimeGrid(
    pagerState: PagerState,
    pageToDate: (Int) -> LocalDate,
    numDays: Int,
    headerDays: List<LocalDate>,
    today: LocalDate,
    availability: List<AvailabilitySlot>,
    locations: List<Location>,
    hoursStart: Int,
    hoursEnd: Int,
    onSlotClick: (AvailabilitySlot) -> Unit,
    onEmptyTap: (date: LocalDate, time: LocalTime) -> Unit,
    onDayClick: ((LocalDate) -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    val totalHours = hoursEnd - hoursStart + 1
    Column(modifier = Modifier.fillMaxSize()) {
        AvailDayHeaderRow(days = headerDays, today = today, onDayClick = onDayClick)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        Row(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
            AvailHourLabels(hoursStart = hoursStart, hoursEnd = hoursEnd)
            BoxWithConstraints(modifier = Modifier.weight(1f).height(HOUR_HEIGHT * totalHours)) {
                val dayWidth = maxWidth / numDays
                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fixed(dayWidth),
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val day = pageToDate(page)
                    val daySlots = availability.filter { it.date == day }.sortedBy { it.startTime.toMinutes() }
                    AvailabilityDayColumn(
                        date = day,
                        today = today,
                        slots = daySlots,
                        locations = locations,
                        hoursStart = hoursStart,
                        hoursEnd = hoursEnd,
                        modifier = Modifier.fillMaxWidth(),
                        onSlotClick = onSlotClick,
                        onEmptyTap = { time -> onEmptyTap(day, time) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AvailDayHeaderRow(
    days: List<LocalDate>,
    today: LocalDate,
    onDayClick: ((LocalDate) -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Spacer(Modifier.width(AVAIL_TIME_COL))
        days.forEach { day ->
            val isToday   = day == today
            val isPast    = day < today
            val isWeekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
            Column(
                modifier = Modifier.weight(1f)
                    .background(if (isWeekend) Color(0xFFFAFAFA) else Color.Transparent)
                    .then(if (onDayClick != null) Modifier.clickable { onDayClick(day) } else Modifier)
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(DAYS_AVAIL_SHORT[day.dayOfWeek.ordinal],
                    fontSize = if (isToday) 11.sp else 10.sp,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
                    color = when {
                        isToday   -> MaterialTheme.colorScheme.primary
                        isWeekend -> Color(0xFFBF360C)
                        isPast    -> Color(0xFFBDBDBD)
                        else      -> Color(0xFF757575)
                    })
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(if (isToday) 34.dp else 28.dp)
                        .clip(CircleShape)
                        .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${day.dayOfMonth}",
                        fontSize = if (isToday) 16.sp else 14.sp,
                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                        color = when {
                            isToday   -> Color.White
                            isPast    -> Color(0xFFBDBDBD)
                            isWeekend -> Color(0xFFBF360C)
                            else      -> MaterialTheme.colorScheme.onSurface
                        })
                }
            }
        }
    }
}

@Composable
private fun AvailHourLabels(hoursStart: Int, hoursEnd: Int) {
    Column(modifier = Modifier.width(AVAIL_TIME_COL)) {
        Spacer(Modifier.height(HOUR_HEIGHT / 2))
        for (h in hoursStart..hoursEnd) {
            Box(modifier = Modifier.height(HOUR_HEIGHT), contentAlignment = Alignment.TopEnd) {
                Text("${h.toString().padStart(2, '0')}:00", fontSize = 10.sp, color = Color(0xFF70757A),
                    modifier = Modifier.padding(end = 6.dp))
            }
        }
    }
}

@Composable
private fun AvailabilityDayColumn(
    date: LocalDate,
    today: LocalDate,
    slots: List<AvailabilitySlot>,
    locations: List<Location>,
    hoursStart: Int,
    hoursEnd: Int,
    modifier: Modifier = Modifier,
    onSlotClick: (AvailabilitySlot) -> Unit,
    onEmptyTap: (LocalTime) -> Unit,
) {
    val locById = locations.associateBy { it.id }
    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    val totalHours = hoursEnd - hoursStart + 1
    val density = LocalDensity.current

    // Zones outside configured slots (shown as gray overlay)
    val disabledZones = remember(slots, hoursStart, hoursEnd) {
        if (slots.isEmpty()) emptyList()
        else {
            val sorted = slots.sortedBy { it.startTime.toMinutes() }
            val ranges = mutableListOf<Pair<Int, Int>>()
            var cursor = hoursStart * 60
            sorted.forEach { slot ->
                val sStart = slot.startTime.toMinutes().coerceAtLeast(hoursStart * 60)
                val sEnd = slot.endTime.toMinutes().coerceAtMost((hoursEnd + 1) * 60)
                if (cursor < sStart) ranges.add(cursor to sStart)
                cursor = maxOf(cursor, sEnd)
            }
            if (cursor < (hoursEnd + 1) * 60) ranges.add(cursor to (hoursEnd + 1) * 60)
            ranges
        }
    }

    Box(
        modifier = modifier
            .height(HOUR_HEIGHT * totalHours)
            .background(if (isWeekend) Color(0xFFFAFAFA) else Color.White)
            .pointerInput(slots, hoursStart, hoursEnd) {
                detectTapGestures { offset ->
                    val hourHeightPx = with(density) { HOUR_HEIGHT.toPx() }
                    val minuteFromStart = ((offset.y / hourHeightPx) * 60).toInt()
                    val rounded = (minuteFromStart / 30) * 30
                    val totalMin = (hoursStart * 60 + rounded).coerceIn(hoursStart * 60, hoursEnd * 60)
                    val clickedTime = minutesToLocalTime(totalMin)

                    val tappedSlot = slots.firstOrNull { slot ->
                        clickedTime.toMinutes() >= slot.startTime.toMinutes() &&
                        clickedTime.toMinutes() < slot.endTime.toMinutes()
                    }
                    if (tappedSlot != null) onSlotClick(tappedSlot)
                    else onEmptyTap(clickedTime)
                }
            }
    ) {
        // Hour grid lines
        for (i in 0..totalHours) {
            HorizontalDivider(modifier = Modifier.offset(y = HOUR_HEIGHT * i), color = Color(0xFFDADCE0), thickness = 0.5.dp)
        }
        for (i in 0 until totalHours) {
            HorizontalDivider(
                modifier = Modifier.offset(y = HOUR_HEIGHT * i + HOUR_HEIGHT / 2).padding(start = 8.dp),
                color = Color(0xFFE8EAED), thickness = 0.5.dp
            )
        }

        // Default availability indicator (no slots configured = working hours are free)
        if (slots.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .offset(y = 0.dp)
                    .height(HOUR_HEIGHT * totalHours)
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AVAIL_DEFAULT_BG)
            )
        }

        // Gray overlay for times outside configured availability
        disabledZones.forEach { (startMin, endMin) ->
            val s = (startMin - hoursStart * 60).coerceAtLeast(0)
            val e = (endMin   - hoursStart * 60).coerceAtMost(totalHours * 60)
            if (e > s) Box(
                modifier = Modifier.fillMaxWidth()
                    .offset(y = HOUR_HEIGHT * s.toFloat() / 60f)
                    .height(HOUR_HEIGHT * (e - s).toFloat() / 60f)
                    .background(AVAIL_DISABLED)
            )
        }

        // Availability slot blocks
        slots.forEach { slot ->
            val startMin = (slot.startTime.toMinutes() - hoursStart * 60).coerceAtLeast(0)
            val endMin   = (slot.endTime.toMinutes()   - hoursStart * 60).coerceAtMost(totalHours * 60)
            if (endMin <= startMin) return@forEach
            val blockColor = slot.locationId?.let { locById[it] }?.let { hexToColor(it.colorHex) } ?: Color(0xFF2E7D32)
            val loc = slot.locationId?.let { locById[it] }
            val blockH = HOUR_HEIGHT * (endMin - startMin).toFloat() / 60f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .absoluteOffset(y = HOUR_HEIGHT * startMin.toFloat() / 60f)
                    .height(blockH)
                    .padding(horizontal = 2.dp, vertical = 1.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(blockColor.copy(alpha = 0.18f))
                    .border(1.dp, blockColor.copy(alpha = 0.55f), RoundedCornerShape(5.dp))
            ) {
                Column(modifier = Modifier.padding(start = 6.dp, top = 3.dp, end = 4.dp)) {
                    Text(
                        "${slot.startTime.toStorageString()}–${slot.endTime.toStorageString()}",
                        fontSize = 11.sp, fontWeight = FontWeight.Medium, color = blockColor,
                        maxLines = 1
                    )
                    if (blockH > 38.dp && loc != null) {
                        Text(loc.name, fontSize = 10.sp, color = blockColor.copy(alpha = 0.75f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Month calendar view
// ─────────────────────────────────────────────

@Composable
private fun AvailabilityMonthView(
    startDate: LocalDate,
    today: LocalDate,
    availability: List<AvailabilitySlot>,
    locations: List<Location>,
    onDayClick: (LocalDate) -> Unit,
) {
    val firstOfMonth = LocalDate(startDate.year, startDate.month, 1)
    val daysInMonth = firstOfMonth.plus(1, DateTimeUnit.MONTH).let { LocalDate(it.year, it.month, 1).minus(1, DateTimeUnit.DAY).dayOfMonth }
    val startDow = firstOfMonth.dayOfWeek.isoDayNumber
    val rows = (startDow - 1 + daysInMonth + 6) / 7

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            DAYS_AVAIL_SHORT.forEach { d ->
                Text(d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp,
                    fontWeight = FontWeight.Medium, color = Color.Gray)
            }
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(4.dp))

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            var dayCounter = 1 - (startDow - 1)
            repeat(rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) {
                        if (dayCounter < 1 || dayCounter > daysInMonth) {
                            Box(modifier = Modifier.weight(1f).height(72.dp))
                        } else {
                            val date = LocalDate(firstOfMonth.year, firstOfMonth.month, dayCounter)
                            val dow = date.dayOfWeek.isoDayNumber
                            val daySlots = availability.filter { it.date == date }.sortedBy { it.startTime.toMinutes() }
                            AvailabilityDayCell(
                                date = date,
                                isToday = date == today,
                                isPast = date < today,
                                isWeekend = dow == 6 || dow == 7,
                                slots = daySlots,
                                locations = locations,
                                modifier = Modifier.weight(1f),
                                onClick = { onDayClick(date) }
                            )
                        }
                        dayCounter++
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Натисніть на день, щоб налаштувати доступність.\nДні без налаштувань — вільний час за робочими годинами.",
                fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 4.dp), lineHeight = 15.sp
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AvailabilityDayCell(
    date: LocalDate,
    isToday: Boolean,
    isPast: Boolean,
    isWeekend: Boolean,
    slots: List<AvailabilitySlot>,
    locations: List<Location>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val locById = locations.associateBy { it.id }
    Box(
        modifier = modifier
            .height(72.dp)
            .background(when {
                isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                isWeekend -> Color(0xFFFAFAFA)
                else -> Color.Transparent
            })
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape)
                    .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text("${date.dayOfMonth}", fontSize = 12.sp,
                    color = when {
                        isToday -> Color.White
                        isPast -> Color(0xFFBDBDBD)
                        isWeekend -> Color(0xFFBF360C)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
            }
            Spacer(Modifier.height(2.dp))
            slots.take(2).forEach { slot ->
                val loc = slot.locationId?.let { locById[it] }
                val color = loc?.let { hexToColor(it.colorHex) } ?: MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                        .background(color.copy(alpha = if (isPast) 0.2f else 0.18f))
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                ) {
                    Text("${slot.startTime.toStorageString()}–${slot.endTime.toStorageString()}",
                        fontSize = 7.sp, color = color.copy(alpha = if (isPast) 0.5f else 1f),
                        maxLines = 1, lineHeight = 8.sp)
                }
                Spacer(Modifier.height(1.dp))
            }
            if (slots.size > 2) Text("+${slots.size - 2}", fontSize = 8.sp, color = Color.Gray)
        }
    }
}

// ─────────────────────────────────────────────
// Day dialog (month-view tap)
// ─────────────────────────────────────────────

@Composable
private fun DayAvailabilityDialog(
    dateLabel: String,
    slots: List<AvailabilitySlot>,
    locations: List<Location>,
    onDismiss: () -> Unit,
    onAddSlot: () -> Unit,
    onEditSlot: (AvailabilitySlot) -> Unit,
    onDeleteSlot: (Long) -> Unit,
) {
    val locById = locations.associateBy { it.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 340.dp),
        title = { Text(dateLabel, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (slots.isEmpty()) {
                    Text("Не налаштовано — вільний час за робочими годинами", fontSize = 13.sp, color = Color.Gray)
                } else {
                    slots.forEach { slot ->
                        val loc = slot.locationId?.let { locById[it] }
                        val color = loc?.let { hexToColor(it.colorHex) } ?: MaterialTheme.colorScheme.primary
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(color.copy(alpha = 0.1f))
                                .clickable { onEditSlot(slot) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (loc != null) Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                            Text("${slot.startTime.toStorageString()} – ${slot.endTime.toStorageString()}",
                                fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            if (loc != null) Text(loc.name, fontSize = 11.sp, color = Color.Gray)
                            Text("✕", fontSize = 12.sp, color = Color.Gray,
                                modifier = Modifier.clickable { onDeleteSlot(slot.id) }.padding(4.dp))
                        }
                    }
                }
                TextButton(onClick = onAddSlot, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
                    Text("+ Додати проміжок", fontSize = 13.sp)
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Готово") } }
    )
}

// ─────────────────────────────────────────────
// Share helpers
// ─────────────────────────────────────────────

private fun buildShareTitle(days: List<LocalDate>, view: CalendarView, startDate: LocalDate): String {
    return when {
        view == CalendarView.MONTH -> "${MONTHS_AVAIL_FULL[startDate.month.ordinal]} ${startDate.year}"
        days.size == 1 -> "${DAYS_AVAIL_SHORT[days[0].dayOfWeek.ordinal]}, ${days[0].dayOfMonth} ${MONTHS_AVAIL_SHORT[days[0].month.ordinal]} ${days[0].year}"
        else -> {
            val first = days.first(); val last = days.last()
            "${first.dayOfMonth}–${last.dayOfMonth} ${MONTHS_AVAIL_SHORT[last.month.ordinal]} ${last.year}"
        }
    }
}

private fun buildAvailabilityImageLines(
    availability: List<AvailabilitySlot>,
    sessions: List<Session>,
    locations: List<Location>,
    days: List<LocalDate>,
    hoursStart: Int,
    hoursEnd: Int,
): List<ScheduleImageLine> {
    val locById = locations.associateBy { it.id }
    val result = mutableListOf<ScheduleImageLine>()
    days.forEach { day ->
        val daySessions = sessions.filter { it.date == day }
        val freeSlots = calculateFreeSlots(daySessions, availability, day, hoursStart, hoursEnd)
            .sortedBy { it.startTime.toMinutes() }
        if (freeSlots.isNotEmpty()) {
            val dow = DAYS_AVAIL_SHORT[day.dayOfWeek.isoDayNumber - 1]
            result.add(ScheduleImageLine("$dow, ${day.dayOfMonth} ${MONTHS_AVAIL_SHORT[day.month.ordinal]}", isHeader = true))
            freeSlots.forEach { slot ->
                val loc = slot.locationId?.let { locById[it] }
                val locStr = if (loc != null) "  ${loc.name}" else ""
                result.add(ScheduleImageLine(
                    text = "${slot.startTime.toStorageString()} – ${slot.endTime.toStorageString()}$locStr",
                    isHeader = false,
                    colorHex = loc?.colorHex,
                ))
            }
        }
    }
    return result
}

private fun buildPeriodText(
    availability: List<AvailabilitySlot>,
    sessions: List<Session>,
    locations: List<Location>,
    days: List<LocalDate>,
    hoursStart: Int,
    hoursEnd: Int,
): String {
    val locById = locations.associateBy { it.id }
    return buildString {
        appendLine("📅 Моя доступність:")
        appendLine()
        days.forEach { day ->
            val dow = day.dayOfWeek.isoDayNumber
            val label = "${DAYS_AVAIL_SHORT[dow - 1]}, ${day.dayOfMonth} ${MONTHS_AVAIL_SHORT[day.month.ordinal]}"
            val daySessions = sessions.filter { it.date == day }
            val freeSlots = calculateFreeSlots(daySessions, availability, day, hoursStart, hoursEnd)
                .sortedBy { it.startTime.toMinutes() }
            if (freeSlots.isEmpty()) {
                appendLine("$label: немає вільного часу")
            } else {
                freeSlots.forEach { slot ->
                    val loc = slot.locationId?.let { locById[it] }
                    val locPart = if (loc != null) " (${loc.name})" else ""
                    appendLine("$label: ${slot.startTime.toStorageString()} – ${slot.endTime.toStorageString()}$locPart")
                }
            }
        }
        appendLine()
        append("Для запису — напишіть у особисті 📅")
    }.trim()
}
