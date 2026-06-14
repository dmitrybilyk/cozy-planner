package com.linkease

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import kotlinx.datetime.*

enum class AppTheme(val label: String, val primaryHex: String, val secondaryHex: String) {
    INDIGO("Індіго", "#1A237E", "#004D40"),
    FOREST("Ліс",   "#1B5E20", "#E65100"),
    VIOLET("Фіолет","#4A148C", "#1565C0"),
}

enum class CalendarView(val label: String) {
    DAY("День"),
    THREE_DAY("3 дні"),
    WEEK("Тиждень"),
    MONTH("Місяць"),
}

// Hardcoded Material 3 color schemes — no runtime computation so theme always applies instantly.
private val schemeIndigo = lightColorScheme(
    primary              = Color(0xFF1A237E),
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFE8EAF6),
    secondary            = Color(0xFF004D40),
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFF80CBC4),
    onSecondaryContainer = Color(0xFF00251A),
    tertiary             = Color(0xFFBF360C),
    background           = Color(0xFFF5F5F5),
    surface              = Color.White,
    surfaceVariant       = Color(0xFFEEEEEE),
    outline              = Color(0xFFBDBDBD),
    outlineVariant       = Color(0xFFE0E0E0),
    error                = Color(0xFFC62828),
)

private val schemeForest = lightColorScheme(
    primary              = Color(0xFF1B5E20),
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFE8F5E9),
    secondary            = Color(0xFFE65100),
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFFFCCBC),
    onSecondaryContainer = Color(0xFF3E2723),
    tertiary             = Color(0xFF827717),
    background           = Color(0xFFF5F5F5),
    surface              = Color.White,
    surfaceVariant       = Color(0xFFEEEEEE),
    outline              = Color(0xFFBDBDBD),
    outlineVariant       = Color(0xFFE0E0E0),
    error                = Color(0xFFC62828),
)

private val schemeViolet = lightColorScheme(
    primary              = Color(0xFF4A148C),
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFF3E5F5),
    secondary            = Color(0xFF1565C0),
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFBBDEFB),
    onSecondaryContainer = Color(0xFF0D47A1),
    tertiary             = Color(0xFF006064),
    background           = Color(0xFFF5F5F5),
    surface              = Color.White,
    surfaceVariant       = Color(0xFFEEEEEE),
    outline              = Color(0xFFBDBDBD),
    outlineVariant       = Color(0xFFE0E0E0),
    error                = Color(0xFFC62828),
)

val appThemeSchemes = mapOf(
    AppTheme.INDIGO to schemeIndigo,
    AppTheme.FOREST to schemeForest,
    AppTheme.VIOLET to schemeViolet,
)

enum class Screen { CALENDAR, CLIENTS, LOCATIONS, AVAILABILITY, SETTINGS }

private fun adjustStartForView(date: LocalDate, view: CalendarView): LocalDate = when (view) {
    CalendarView.WEEK  -> date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
    CalendarView.MONTH -> LocalDate(date.year, date.month, 1)
    else -> date
}

private fun navigatePrev(date: LocalDate, view: CalendarView): LocalDate = when (view) {
    CalendarView.DAY       -> date.minus(1, DateTimeUnit.DAY)
    CalendarView.THREE_DAY -> date.minus(3, DateTimeUnit.DAY)
    CalendarView.WEEK      -> date.minus(7, DateTimeUnit.DAY)
    CalendarView.MONTH     -> {
        val prev = date.minus(1, DateTimeUnit.MONTH)
        LocalDate(prev.year, prev.month, 1)
    }
}

private fun navigateNext(date: LocalDate, view: CalendarView): LocalDate = when (view) {
    CalendarView.DAY       -> date.plus(1, DateTimeUnit.DAY)
    CalendarView.THREE_DAY -> date.plus(3, DateTimeUnit.DAY)
    CalendarView.WEEK      -> date.plus(7, DateTimeUnit.DAY)
    CalendarView.MONTH     -> {
        val next = date.plus(1, DateTimeUnit.MONTH)
        LocalDate(next.year, next.month, 1)
    }
}

@Composable
fun App(
    sessionRepository: SessionRepository,
    clientRepository: ClientRepository,
    locationRepository: LocationRepository,
    availabilityRepository: AvailabilityRepository,
    onShare: (String) -> Unit = {},
    onExportToCalendar: ((Session, List<Client>, Location?) -> Unit)? = null,
    onScheduleNotifications: ((sessions: List<Session>, clients: List<Client>, locations: List<Location>) -> Unit)? = null,
    onPinToStatusBar: (() -> Unit)? = null,
    createSessionVersion: Long = 0L,
    onExportDayDirect: ((date: LocalDate, sessions: List<Session>, clients: List<Client>, locations: List<Location>) -> Unit)? = null,
) {
    val tz = TimeZone.currentSystemDefault()
    val todayDate = Clock.System.now().toLocalDateTime(tz).date

    var screen by remember { mutableStateOf(Screen.CALENDAR) }
    var startDate by remember { mutableStateOf(todayDate) }
    var currentView by remember { mutableStateOf(CalendarView.THREE_DAY) }
    var currentTheme by remember { mutableStateOf(AppTheme.INDIGO) }

    var sessions by remember { mutableStateOf(sessionRepository.getAll()) }
    var clients by remember { mutableStateOf(clientRepository.getAll()) }
    var locations by remember { mutableStateOf(locationRepository.getAll()) }
    var availability by remember { mutableStateOf(availabilityRepository.getAll()) }

    fun refreshSessions() {
        sessions = sessionRepository.getAll()
        onScheduleNotifications?.invoke(sessions, clients, locations)
    }

    var showAddSession by remember { mutableStateOf(false) }
    var addSessionDate by remember { mutableStateOf(todayDate) }
    var addSessionStartTime by remember { mutableStateOf(LocalTime(9, 0)) }
    var addSessionEndTime by remember { mutableStateOf<LocalTime?>(null) }
    var editingSession by remember { mutableStateOf<Session?>(null) }
    var copyingSession by remember { mutableStateOf<Session?>(null) }

    fun openNewSession(date: LocalDate, start: LocalTime, end: LocalTime? = null) {
        addSessionDate = date
        addSessionStartTime = start
        addSessionEndTime = end
        editingSession = null
        showAddSession = true
    }

    fun switchView(newView: CalendarView) {
        startDate = adjustStartForView(startDate, newView)
        currentView = newView
    }

    // Open create-session dialog when triggered externally (e.g. from widget or notification).
    LaunchedEffect(createSessionVersion) {
        if (createSessionVersion > 0L) openNewSession(todayDate, LocalTime(9, 0))
    }

    // Apply theme scheme — this is a direct lookup so it always triggers immediate recomposition.
    val colorScheme = when (currentTheme) {
        AppTheme.INDIGO -> schemeIndigo
        AppTheme.FOREST -> schemeForest
        AppTheme.VIOLET -> schemeViolet
    }

    MaterialTheme(colorScheme = colorScheme) {
        when (screen) {
            Screen.CALENDAR -> CalendarScreen(
                startDate = startDate,
                currentView = currentView,
                sessions = sessions,
                clients = clients,
                locations = locations,
                availability = availability,
                onPrev = { startDate = navigatePrev(startDate, currentView) },
                onNext = { startDate = navigateNext(startDate, currentView) },
                onGoToToday = { startDate = adjustStartForView(todayDate, currentView) },
                onViewChange = { switchView(it) },
                onAddSession = { date, time -> openNewSession(date, time) },
                onAddSessionFromSlot = { date, start, end -> openNewSession(date, start, end) },
                onEditSession = { editingSession = it; addSessionEndTime = null; showAddSession = true },
                onDeleteSession = { id ->
                    sessionRepository.delete(id); refreshSessions()
                },
                onCopySession = { copyingSession = it },
                onAvailabilityClick = { screen = Screen.AVAILABILITY },
                onSettingsClick = { screen = Screen.SETTINGS },
                onShareFreeTime = { _, text -> onShare(text) },
                onDayClickInMonth = { date -> startDate = date; currentView = CalendarView.DAY },
                onPinToStatusBar = onPinToStatusBar,
                onExportDayToCalendar = onExportDayDirect,
            )

            Screen.CLIENTS -> ClientsScreen(
                clients = clients,
                onSettingsClick = { screen = Screen.SETTINGS },
                onSave = { n, p, e, c ->
                    clientRepository.save(n, p, e, c)
                    clients = clientRepository.getAll()
                    onScheduleNotifications?.invoke(sessions, clients, locations)
                },
                onUpdate = { clientRepository.update(it); clients = clientRepository.getAll() },
                onDelete = { clientRepository.delete(it); clients = clientRepository.getAll() }
            )

            Screen.LOCATIONS -> LocationsScreen(
                locations = locations,
                onSettingsClick = { screen = Screen.SETTINGS },
                onSave = { n, a, c ->
                    locationRepository.save(n, a, c)
                    locations = locationRepository.getAll()
                    onScheduleNotifications?.invoke(sessions, clients, locations)
                },
                onUpdate = { locationRepository.update(it); locations = locationRepository.getAll() },
                onDelete = { locationRepository.delete(it); locations = locationRepository.getAll() }
            )

            Screen.AVAILABILITY -> AvailabilityScreen(
                availability = availability,
                locations = locations,
                onSettingsClick = { screen = Screen.SETTINGS },
                onSave = { dow, start, end, locId ->
                    availabilityRepository.save(dow, start, end, locId)
                    availability = availabilityRepository.getAll()
                },
                onUpdate = { availabilityRepository.update(it); availability = availabilityRepository.getAll() },
                onDelete = { availabilityRepository.delete(it); availability = availabilityRepository.getAll() },
                onShareSchedule = { text -> onShare(text) }
            )

            Screen.SETTINGS -> SettingsScreen(
                currentTheme = currentTheme,
                onThemeChange = { currentTheme = it },
                onBack = { screen = Screen.CALENDAR },
                onAvailabilityClick = { screen = Screen.AVAILABILITY },
                onClientsClick = { screen = Screen.CLIENTS },
                onLocationsClick = { screen = Screen.LOCATIONS },
            )
        }

        if (showAddSession) {
            AddEditSessionDialog(
                initial = editingSession,
                defaultDate = addSessionDate,
                defaultStartTime = addSessionStartTime,
                defaultEndTime = addSessionEndTime,
                clients = clients,
                locations = locations,
                existingSessions = sessions,
                onDismiss = { showAddSession = false; editingSession = null; addSessionEndTime = null },
                onConfirm = { date, start, end, clientIds, locationId, notes ->
                    val existing = editingSession
                    if (existing == null) sessionRepository.save(date, start, end, clientIds, locationId, notes)
                    else sessionRepository.update(existing.copy(date = date, startTime = start, endTime = end,
                        clientIds = clientIds, locationId = locationId, notes = notes))
                    refreshSessions()
                    showAddSession = false; editingSession = null; addSessionEndTime = null
                }
            )
        }

        copyingSession?.let { original ->
            AddEditSessionDialog(
                initial = null,
                defaultDate = original.date,
                defaultStartTime = original.startTime,
                defaultEndTime = original.endTime,
                defaultClientIds = original.clientIds,
                defaultLocationId = original.locationId,
                defaultNotes = original.notes,
                clients = clients,
                locations = locations,
                existingSessions = sessions,
                onDismiss = { copyingSession = null },
                onConfirm = { date, start, end, clientIds, locationId, notes ->
                    sessionRepository.save(date, start, end, clientIds, locationId, notes)
                    refreshSessions()
                    copyingSession = null
                }
            )
        }
    }
}
