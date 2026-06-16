package com.linkease

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import kotlinx.datetime.*

enum class CalendarView(val label: String) {
    DAY("День"),
    THREE_DAY("3 дні"),
    MONTH("Місяць"),
}

private val appColorScheme = lightColorScheme(
    primary              = Color(0xFF1A237E),
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFE8EAF6),
    secondary            = Color(0xFF004D40),
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFF80CBC4),
    onSecondaryContainer = Color(0xFF00251A),
    tertiary             = Color(0xFFBF360C),
    background           = Color(0xFFFFFFFF),
    surface              = Color(0xFFFFFFFF),
    surfaceVariant       = Color(0xFFF1F3F4),
    outline              = Color(0xFFDADCE0),
    outlineVariant       = Color(0xFFE8EAED),
    error                = Color(0xFFC62828),
)

enum class Screen { CALENDAR, CLIENTS, LOCATIONS, AVAILABILITY, SETTINGS, REPORT }

private fun adjustStartForView(date: LocalDate, view: CalendarView): LocalDate = when (view) {
    CalendarView.MONTH -> LocalDate(date.year, date.month, 1)
    else -> date
}

private fun navigatePrev(date: LocalDate, view: CalendarView): LocalDate = when (view) {
    CalendarView.DAY       -> date.minus(1, DateTimeUnit.DAY)
    CalendarView.THREE_DAY -> date.minus(1, DateTimeUnit.DAY)
    CalendarView.MONTH     -> LocalDate(date.year, date.month, 1).minus(1, DateTimeUnit.MONTH).let { LocalDate(it.year, it.month, 1) }
}

private fun navigateNext(date: LocalDate, view: CalendarView): LocalDate = when (view) {
    CalendarView.DAY       -> date.plus(1, DateTimeUnit.DAY)
    CalendarView.THREE_DAY -> date.plus(1, DateTimeUnit.DAY)
    CalendarView.MONTH     -> LocalDate(date.year, date.month, 1).plus(1, DateTimeUnit.MONTH).let { LocalDate(it.year, it.month, 1) }
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
    onDataChanged: ((sessions: List<Session>, clients: List<Client>, locations: List<Location>, availability: List<AvailabilitySlot>) -> Unit)? = null,
    refreshVersion: Long = 0L,
    onExportBackup: (() -> Unit)? = null,
    onImportBackup: (() -> Unit)? = null,
    workHoursStartInitial: Int = 7,
    workHoursEndInitial: Int = 22,
    onWorkHoursChange: ((Int, Int) -> Unit)? = null,
    compactModeInitial: Boolean = true,
    onCompactModeChange: ((Boolean) -> Unit)? = null,
    gcalSyncInitial: Boolean = false,
    onGcalSyncChange: ((Boolean) -> Unit)? = null,
    onSessionSyncCreate: ((session: Session, clients: List<Client>, location: Location?) -> Unit)? = null,
    onSessionSyncUpdate: ((session: Session, clients: List<Client>, location: Location?) -> Unit)? = null,
    onSessionSyncDelete: ((sessionId: Long) -> Unit)? = null,
    onCopyToClipboard: ((String) -> Unit)? = null,
    onShareFreeTimeImage: ((title: String, lines: List<ScheduleImageLine>) -> Unit)? = null,
    onCopyFreeTimeImage: ((title: String, lines: List<ScheduleImageLine>) -> Unit)? = null,
    onShareAvailabilityImage: ((title: String, lines: List<ScheduleImageLine>) -> Unit)? = null,
    onCopyAvailabilityImage: ((title: String, lines: List<ScheduleImageLine>) -> Unit)? = null,
    onOpenUrl: ((String) -> Unit)? = null,
    onEraseAllData: ((sessionIds: List<Long>) -> Unit)? = null,
    notificationSoundName: String = "Стандартний",
    onPickNotificationSound: (() -> Unit)? = null,
    onSendTestNotification: (() -> Unit)? = null,
) {
    val tz = TimeZone.currentSystemDefault()
    val todayDate = Clock.System.now().toLocalDateTime(tz).date

    var screenHistory by remember { mutableStateOf(listOf(Screen.CALENDAR)) }
    val screen = screenHistory.last()
    fun navigateTo(s: Screen) { screenHistory = screenHistory + s }
    fun goBack() { if (screenHistory.size > 1) screenHistory = screenHistory.dropLast(1) }
    // Calendar / Розклад / Налаштування are peer tabs reached via the persistent bottom
    // nav — switching tabs replaces the whole stack (no "Назад" needed between them),
    // while sub-screens pushed from Settings (Clients/Locations/Report) still use
    // navigateTo/goBack above.
    fun switchRootScreen(s: Screen) { screenHistory = listOf(s) }

    BackHandler(enabled = screenHistory.size > 1) { goBack() }

    var selectedDay by remember { mutableStateOf(todayDate) }
    var startDate by remember { mutableStateOf(todayDate) }
    var currentView by remember { mutableStateOf(CalendarView.THREE_DAY) }
    var workHoursStart by remember { mutableStateOf(workHoursStartInitial) }
    var workHoursEnd by remember { mutableStateOf(workHoursEndInitial) }
    var gcalSync by remember { mutableStateOf(gcalSyncInitial) }

    var sessions by remember { mutableStateOf(sessionRepository.getAll()) }
    var clients by remember { mutableStateOf(clientRepository.getAll()) }
    var locations by remember { mutableStateOf(locationRepository.getAll()) }
    var availability by remember { mutableStateOf(availabilityRepository.getAll()) }

    fun syncData() {
        onDataChanged?.invoke(sessions, clients, locations, availability)
    }

    fun refreshSessions() {
        sessions = sessionRepository.getAll()
        onScheduleNotifications?.invoke(sessions, clients, locations)
        syncData()
    }

    fun syncSessionCreate(session: Session, clientIds: List<Long>, locationId: Long?) {
        if (!gcalSync) return
        val c = clients.filter { it.id in clientIds }
        val l = locations.find { it.id == locationId }
        onSessionSyncCreate?.invoke(session, c, l)
    }

    fun syncSessionUpdate(session: Session) {
        if (!gcalSync) return
        val c = clients.filter { it.id in session.clientIds }
        val l = locations.find { it.id == session.locationId }
        onSessionSyncUpdate?.invoke(session, c, l)
    }

    fun syncSessionDelete(sessionId: Long) {
        if (!gcalSync) return
        onSessionSyncDelete?.invoke(sessionId)
    }

    fun eraseAllData() {
        val sessionIds = sessions.map { it.id }
        sessionRepository.deleteAll()
        clientRepository.deleteAll()
        locationRepository.deleteAll()
        availabilityRepository.deleteAll()
        sessions = emptyList()
        clients = emptyList()
        locations = emptyList()
        availability = emptyList()
        onEraseAllData?.invoke(sessionIds)
        syncData()
    }

    var showAddSession by remember { mutableStateOf(false) }
    var addSessionDate by remember { mutableStateOf(selectedDay) }
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

    // Bottom-nav tab toggles: tapping a screen's own icon while already there returns
    // to Calendar, so Розклад/Налаштування need no separate "Назад" button.
    fun onSettingsTabClick() {
        switchRootScreen(if (screen == Screen.SETTINGS) Screen.CALENDAR else Screen.SETTINGS)
    }
    fun onAvailabilityTabClick() {
        switchRootScreen(if (screen == Screen.AVAILABILITY) Screen.CALENDAR else Screen.AVAILABILITY)
    }
    fun onFreeTimeTabClick() { switchRootScreen(Screen.CALENDAR) }

    fun quickCreateSession() {
        val target = selectedDay
        val desiredStart = if (target == todayDate) {
            val now = Clock.System.now().toLocalDateTime(tz).time
            val mins = now.hour * 60 + now.minute
            minutesToLocalTime(((mins + 29) / 30) * 30)
        } else LocalTime(9, 0)
        openNewSession(target, findNextAvailableStart(sessions, target, desiredStart, workHoursEnd = workHoursEnd))
    }

    fun switchView(newView: CalendarView) {
        currentView = newView
        startDate = when (newView) {
            CalendarView.MONTH -> LocalDate(selectedDay.year, selectedDay.month, 1)
            else -> selectedDay
        }
    }

    // Open create-session dialog when triggered externally (e.g. from widget or notification).
    LaunchedEffect(createSessionVersion) {
        if (createSessionVersion > 0L) openNewSession(todayDate, LocalTime(9, 0))
    }

    // Reload all data after external import.
    LaunchedEffect(refreshVersion) {
        if (refreshVersion > 0L) {
            sessions = sessionRepository.getAll()
            clients = clientRepository.getAll()
            locations = locationRepository.getAll()
            availability = availabilityRepository.getAll()
            syncData()
        }
    }

    MaterialTheme(colorScheme = appColorScheme) {
        when (screen) {
            Screen.CALENDAR -> CalendarScreen(
                startDate = startDate,
                currentView = currentView,
                sessions = sessions,
                clients = clients,
                locations = locations,
                availability = availability,
                hoursStart = workHoursStart,
                hoursEnd = workHoursEnd,
                compactModeInitial = compactModeInitial,
                onCompactModeChange = onCompactModeChange,
                onCopyToClipboard = onCopyToClipboard,
                onShareFreeTimeImage = onShareFreeTimeImage,
                onCopyFreeTimeImage = onCopyFreeTimeImage,
                onDayClickInThreeDay = { date ->
                    selectedDay = date
                    startDate = date
                    currentView = CalendarView.DAY
                },
                onPrev = {
                    startDate = navigatePrev(startDate, currentView)
                    if (currentView != CalendarView.MONTH) selectedDay = startDate
                },
                onNext = {
                    startDate = navigateNext(startDate, currentView)
                    if (currentView != CalendarView.MONTH) selectedDay = startDate
                },
                onGoToToday = {
                    selectedDay = todayDate
                    startDate = adjustStartForView(todayDate, currentView)
                },
                onDateChange = { date ->
                    startDate = date
                    selectedDay = date
                },
                onViewChange = { switchView(it) },
                selectedDay = selectedDay,
                onAddSession = { date, time -> openNewSession(date, time) },
                onAddSessionFromSlot = { date, start, end -> openNewSession(date, start, end) },
                onEditSession = { editingSession = it; addSessionEndTime = null; showAddSession = true },
                onDeleteSession = { id ->
                    syncSessionDelete(id)
                    sessionRepository.delete(id)
                    refreshSessions()
                },
                onCopySession = { copyingSession = it },
                onAvailabilityClick = { onAvailabilityTabClick() },
                onSettingsClick = { onSettingsTabClick() },
                onShareFreeTime = { _, text -> onShare(text) },
                onDayClickInMonth = { date ->
                    selectedDay = date
                    startDate = date
                    currentView = CalendarView.DAY
                },
                onPinToStatusBar = onPinToStatusBar,
                onExportDayToCalendar = onExportDayDirect,
            )

            Screen.CLIENTS -> ClientsScreen(
                clients = clients,
                sessions = sessions,
                locations = locations,
                onSettingsClick = { goBack() },
                onSave = { n, p, e, c, rate ->
                    clientRepository.save(n, p, e, c, rate)
                    clients = clientRepository.getAll()
                    onScheduleNotifications?.invoke(sessions, clients, locations)
                    syncData()
                },
                onUpdate = { clientRepository.update(it); clients = clientRepository.getAll(); syncData() },
                onDelete = { clientRepository.delete(it); clients = clientRepository.getAll(); syncData() }
            )

            Screen.LOCATIONS -> LocationsScreen(
                locations = locations,
                onSettingsClick = { goBack() },
                onSave = { n, a, c, m ->
                    locationRepository.save(n, a, c, m)
                    locations = locationRepository.getAll()
                    onScheduleNotifications?.invoke(sessions, clients, locations)
                    syncData()
                },
                onUpdate = { locationRepository.update(it); locations = locationRepository.getAll(); syncData() },
                onDelete = { locationRepository.delete(it); locations = locationRepository.getAll(); syncData() },
                onShare = onShare,
                onCopyToClipboard = onCopyToClipboard,
                onOpenMap = onOpenUrl,
            )

            Screen.AVAILABILITY -> AvailabilityScreen(
                availability = availability,
                locations = locations,
                sessions = sessions,
                clients = clients,
                hoursStart = workHoursStart,
                hoursEnd = workHoursEnd,
                onSettingsClick = { onSettingsTabClick() },
                onAvailabilityNavClick = { onAvailabilityTabClick() },
                onCreateClick = { quickCreateSession() },
                onFreeTimeClick = { onFreeTimeTabClick() },
                onSave = { date, start, end, locId ->
                    availabilityRepository.save(date, start, end, locId)
                    availability = availabilityRepository.getAll()
                    syncData()
                },
                onUpdate = { availabilityRepository.update(it); availability = availabilityRepository.getAll(); syncData() },
                onDelete = { availabilityRepository.delete(it); availability = availabilityRepository.getAll(); syncData() },
                onShareSchedule = { text -> onShare(text) },
                onCopySchedule = onCopyToClipboard,
                onShareAvailabilityImage = onShareAvailabilityImage,
                onCopyAvailabilityImage = onCopyAvailabilityImage,
                onSessionClick = { editingSession = it; addSessionEndTime = null; showAddSession = true },
                onAddSession = { date -> openNewSession(date, LocalTime(9, 0)) },
            )

            Screen.SETTINGS -> SettingsScreen(
                workHoursStart = workHoursStart,
                workHoursEnd = workHoursEnd,
                onWorkHoursChange = { s, e ->
                    workHoursStart = s; workHoursEnd = e
                    onWorkHoursChange?.invoke(s, e)
                },
                gcalSync = gcalSync,
                onGcalSyncChange = { v ->
                    gcalSync = v
                    onGcalSyncChange?.invoke(v)
                },
                onSyncAllFutureSessions = if (gcalSync) {
                    {
                        val futureSessions = sessions.filter { it.date >= todayDate }
                        futureSessions.forEach { s -> syncSessionUpdate(s) }
                    }
                } else null,
                onSettingsClick = { onSettingsTabClick() },
                onAvailabilityClick = { onAvailabilityTabClick() },
                onCreateClick = { quickCreateSession() },
                onFreeTimeClick = { onFreeTimeTabClick() },
                onClientsClick = { navigateTo(Screen.CLIENTS) },
                onLocationsClick = { navigateTo(Screen.LOCATIONS) },
                onReportClick = { navigateTo(Screen.REPORT) },
                onExportBackup = onExportBackup,
                onImportBackup = onImportBackup,
                onEraseAllData = { eraseAllData() },
                notificationSoundName = notificationSoundName,
                onPickNotificationSound = onPickNotificationSound,
                onSendTestNotification = onSendTestNotification,
            )

            Screen.REPORT -> ReportScreen(
                sessions = sessions,
                clients = clients,
                onBack = { goBack() },
            )
        }

        fun createNewClient(name: String): Client {
            val c = clientRepository.save(name.trim(), "", "", "#2196F3")
            clients = clientRepository.getAll()
            syncData()
            return c
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
                availability = availability,
                workHoursStart = workHoursStart,
                workHoursEnd = workHoursEnd,
                onCreateClient = { name -> createNewClient(name) },
                onDismiss = { showAddSession = false; editingSession = null; addSessionEndTime = null },
                onConfirm = { date, start, end, clientIds, locationId, notes ->
                    val existing = editingSession
                    if (existing == null) {
                        val newSession = sessionRepository.save(date, start, end, clientIds, locationId, notes)
                        syncSessionCreate(newSession, clientIds, locationId)
                    } else {
                        val updated = existing.copy(date = date, startTime = start, endTime = end,
                            clientIds = clientIds, locationId = locationId, notes = notes)
                        sessionRepository.update(updated)
                        syncSessionUpdate(updated)
                    }
                    refreshSessions()
                    showAddSession = false; editingSession = null; addSessionEndTime = null
                },
                onConfirmSeries = { dates, start, end, clientIds, locationId, notes ->
                    dates.forEach { date ->
                        val newSession = sessionRepository.save(date, start, end, clientIds, locationId, notes)
                        syncSessionCreate(newSession, clientIds, locationId)
                    }
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
                availability = availability,
                workHoursStart = workHoursStart,
                workHoursEnd = workHoursEnd,
                onCreateClient = { name -> createNewClient(name) },
                onDismiss = { copyingSession = null },
                onConfirm = { date, start, end, clientIds, locationId, notes ->
                    val newSession = sessionRepository.save(date, start, end, clientIds, locationId, notes)
                    syncSessionCreate(newSession, clientIds, locationId)
                    refreshSessions()
                    copyingSession = null
                },
                onConfirmSeries = { dates, start, end, clientIds, locationId, notes ->
                    dates.forEach { date ->
                        val newSession = sessionRepository.save(date, start, end, clientIds, locationId, notes)
                        syncSessionCreate(newSession, clientIds, locationId)
                    }
                    refreshSessions()
                    copyingSession = null
                }
            )
        }
    }
}
