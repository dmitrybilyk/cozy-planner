package com.linkease

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
    primary              = Color(0xFF4F46E5),   // modern indigo-600
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFEDE9FE),   // violet-100
    onPrimaryContainer   = Color(0xFF3730A3),
    secondary            = Color(0xFF0891B2),   // cyan-600
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary             = Color(0xFFDC2626),
    background           = Color(0xFFF8F8FC),   // very light lavender-white
    surface              = Color(0xFFFFFFFF),
    surfaceVariant       = Color(0xFFF1F0F9),   // soft lavender-gray
    outline              = Color(0xFFE2E0EF),
    outlineVariant       = Color(0xFFECEBF6),
    error                = Color(0xFFDC2626),
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
    autoBackupEnabled: Boolean = false,
    onAutoBackupToggle: ((Boolean) -> Unit)? = null,
    backupFolderName: String? = null,
    onPickBackupFolder: (() -> Unit)? = null,
    createSessionVersion: Long = 0L,
    showFreeTimeVersion: Long = 0L,
    createClientVersion: Long = 0L,
    showAvailabilityVersion: Long = 0L,
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
    telegramLinked: Boolean = false,
    onLinkTelegram: ((code: String) -> Unit)? = null,
    appMode: String = "user",
    onAppModeChange: ((String) -> Unit)? = null,
    myUserId: String? = null,
    onCopyMyId: (() -> Unit)? = null,
    onPublishToFirebase: (() -> Unit)? = null,
    trainerData: TrainerData? = null,
    isLoadingTrainer: Boolean = false,
    trainerLoadError: String? = null,
    onConnectToTrainer: ((String) -> Unit)? = null,
    onDisconnectFromTrainer: (() -> Unit)? = null,
    savedTrainerId: String? = null,
    clientSessions: List<ClientSession> = emptyList(),
    clientBookingRequests: List<BookingRequest> = emptyList(),
    onBookSlot: ((date: LocalDate, start: LocalTime, end: LocalTime, note: String?) -> Unit)? = null,
    onConfirmClientSession: ((sessionId: String) -> Unit)? = null,
    onRejectClientSession: ((sessionId: String) -> Unit)? = null,
    pendingBookingRequests: List<BookingRequest> = emptyList(),
    onConfirmBookingRequest: ((BookingRequest) -> Unit)? = null,
    onDeclineBookingRequest: ((BookingRequest) -> Unit)? = null,
    clients: List<Client> = emptyList(),
    clientAvailabilitySlots: List<ClientAvailabilitySlot> = emptyList(),
    onClientAvailabilityCreateSession: ((ClientAvailabilitySlot) -> Unit)? = null,
    myAvailability: List<ClientAvailabilitySlot> = emptyList(),
    onSaveMyAvailability: ((List<ClientAvailabilitySlot>) -> Unit)? = null,
    chatMessages: List<ChatMessage> = emptyList(),
    onSendChat: ((String) -> Unit)? = null,
    onOpenChatWithClient: ((clientFirebaseId: String, clientName: String) -> Unit)? = null,
    connectedClients: List<Triple<String, String, String?>> = emptyList(),
    onLinkClientFirebaseId: ((firebaseId: String, localClientId: Long) -> Unit)? = null,
    trainerEmail: String = "",
    onSaveTrainerEmail: ((String) -> Unit)? = null,
    onCopyTrainerEmail: (() -> Unit)? = null,
    onClearFirebaseData: (() -> Unit)? = null,
    clientEmail: String = "",
    onCopyClientEmail: (() -> Unit)? = null,
    onShareClientEmail: (() -> Unit)? = null,
    onboardingDone: Boolean = true,
    emailHint: String = "",
    onOnboardingSelectTrainer: ((String) -> Unit)? = null,
    onOnboardingSelectClient: ((String, String) -> Unit)? = null,
    pendingChatPartnerId: String? = null,
    onPendingChatConsumed: (() -> Unit)? = null,
    pendingClientAvailabilityId: String? = null,
    onPendingClientAvailabilityConsumed: (() -> Unit)? = null,
    onAskClientAvailability: ((clientFirebaseId: String, message: String) -> Unit)? = null,
) {
    val tz = TimeZone.currentSystemDefault()
    val todayDate = Clock.System.now().toLocalDateTime(tz).date

    var chatOpenClientId   by remember { mutableStateOf<String?>(null) }
    var chatOpenClientName by remember { mutableStateOf("") }

    var highlightClientFirebaseId by remember { mutableStateOf<String?>(null) }

    // Open chat dialog when triggered by notification tap
    LaunchedEffect(pendingChatPartnerId) {
        if (pendingChatPartnerId != null && appMode == "user") {
            chatOpenClientId = pendingChatPartnerId
            chatOpenClientName = clients.find { it.firebaseClientId == pendingChatPartnerId }?.name ?: ""
            onOpenChatWithClient?.invoke(pendingChatPartnerId, chatOpenClientName)
            onPendingChatConsumed?.invoke()
        }
    }

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

    // Navigate to Settings → client availability when tapped from notification
    LaunchedEffect(pendingClientAvailabilityId) {
        if (pendingClientAvailabilityId != null) {
            highlightClientFirebaseId = pendingClientAvailabilityId
            switchRootScreen(Screen.SETTINGS)
            onPendingClientAvailabilityConsumed?.invoke()
        }
    }

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

    var showAvailabilityPanel by remember { mutableStateOf(false) }

    var showAddSession by remember { mutableStateOf(false) }
    var addSessionDate by remember { mutableStateOf(selectedDay) }
    var addSessionStartTime by remember { mutableStateOf(LocalTime(9, 0)) }
    var addSessionEndTime by remember { mutableStateOf<LocalTime?>(null) }
    var addSessionDefaultClientIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var editingSession by remember { mutableStateOf<Session?>(null) }
    var copyingSession by remember { mutableStateOf<Session?>(null) }

    fun openNewSession(date: LocalDate, start: LocalTime, end: LocalTime? = null, clientIds: List<Long> = emptyList()) {
        addSessionDate = date
        addSessionStartTime = start
        addSessionEndTime = end
        addSessionDefaultClientIds = clientIds
        editingSession = null
        showAddSession = true
    }

    // Bottom-nav tab toggles: tapping a screen's own icon while already there returns
    // to Calendar, so Розклад/Налаштування need no separate "Назад" button.
    fun onSettingsTabClick() {
        switchRootScreen(if (screen == Screen.SETTINGS) Screen.CALENDAR else Screen.SETTINGS)
    }
    var availabilityScheduleModeInitial by remember { mutableStateOf(true) }
    fun onAvailabilityTabClick() {
        availabilityScheduleModeInitial = true
        switchRootScreen(if (screen == Screen.AVAILABILITY) Screen.CALENDAR else Screen.AVAILABILITY)
    }
    fun onAvailabilitySettingsTabClick() {
        availabilityScheduleModeInitial = false
        switchRootScreen(Screen.AVAILABILITY)
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

    LaunchedEffect(showFreeTimeVersion) {
        if (showFreeTimeVersion > 0L) switchRootScreen(Screen.CALENDAR)
    }

    LaunchedEffect(createClientVersion) {
        if (createClientVersion > 0L) {
            switchRootScreen(Screen.CALENDAR)
            navigateTo(Screen.CLIENTS)
        }
    }

    LaunchedEffect(showAvailabilityVersion) {
        if (showAvailabilityVersion > 0L) {
            switchRootScreen(Screen.CALENDAR)
            showAvailabilityPanel = true
        }
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
        if (!onboardingDone) {
            OnboardingScreen(
                emailHint = emailHint,
                isLoading = isLoadingTrainer,
                error = trainerLoadError,
                onSelectTrainer = { email -> onOnboardingSelectTrainer?.invoke(email) },
                onSelectClient = { myEmail, trainerEmail -> onOnboardingSelectClient?.invoke(myEmail, trainerEmail) },
            )
            return@MaterialTheme
        }

        if (appMode == "client") {
            if (trainerData != null) {
                ClientModeScreen(
                    trainerData = trainerData,
                    myFirebaseId = myUserId ?: "",
                    clientEmail = clientEmail,
                    clientSessions = clientSessions,
                    pendingBookingRequests = clientBookingRequests,
                    myAvailability = myAvailability,
                    chatMessages = chatMessages,
                    onDisconnect = { onDisconnectFromTrainer?.invoke() },
                    onBookSlot = { date, start, end, note -> onBookSlot?.invoke(date, start, end, note) },
                    onConfirmSession = { sid -> onConfirmClientSession?.invoke(sid) },
                    onRejectSession = { sid -> onRejectClientSession?.invoke(sid) },
                    onCancelSession = { sid -> onRejectClientSession?.invoke(sid) },
                    onSaveMyAvailability = { slots -> onSaveMyAvailability?.invoke(slots) },
                    onSendChat = { msg -> onSendChat?.invoke(msg) },
                    onCopyClientEmail = onCopyClientEmail,
                    onShareClientEmail = onShareClientEmail,
                )
            } else {
                ClientConnectScreen(
                    savedTrainerId = null,
                    isLoading = isLoadingTrainer,
                    error = trainerLoadError,
                    onConnect = { onConnectToTrainer?.invoke(it) },
                    onSwitchToUserMode = { onAppModeChange?.invoke("user") }
                )
            }
            return@MaterialTheme
        }

        when (screen) {
            Screen.CALENDAR -> CalendarScreen(
                showFreeTimeVersion = showFreeTimeVersion,
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
                pendingBookingRequests = pendingBookingRequests,
                onConfirmBookingRequest = { request ->
                    val localClientId = clients.find { it.firebaseClientId == request.clientFirebaseId }?.id
                    val newSession = sessionRepository.save(
                        request.date, request.startTime, request.endTime,
                        listOfNotNull(localClientId), null, ""
                    )
                    syncSessionCreate(newSession, listOfNotNull(localClientId), null)
                    refreshSessions()
                    onConfirmBookingRequest?.invoke(request)
                },
                onDeclineBookingRequest = { request ->
                    onDeclineBookingRequest?.invoke(request)
                },
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
                showAvailabilityPanel = showAvailabilityPanel,
                onAvailabilityPanelToggle = { showAvailabilityPanel = !showAvailabilityPanel },
                onSaveAvailability = { date, start, end, locId ->
                    availabilityRepository.save(date, start, end, locId)
                    availability = availabilityRepository.getAll()
                    syncData()
                },
                onUpdateAvailability = { availabilityRepository.update(it); availability = availabilityRepository.getAll(); syncData() },
                onDeleteAvailability = { availabilityRepository.delete(it); availability = availabilityRepository.getAll(); syncData() },
                onSettingsClick = { onSettingsTabClick() },
                onShareFreeTime = { _, text -> onShare(text) },
                onShareAvailability = { text -> onShare(text) },
                onDayClickInMonth = { date ->
                    selectedDay = date
                    startDate = date
                    currentView = CalendarView.DAY
                },
                onPinToStatusBar = onPinToStatusBar,
                onExportDayToCalendar = onExportDayDirect,
                onDuplicateWeek = {
                    val dow = startDate.dayOfWeek.isoDayNumber
                    val weekMon = startDate.minus(dow - 1, DateTimeUnit.DAY)
                    val weekSun = weekMon.plus(6, DateTimeUnit.DAY)
                    val weekSessions = sessions.filter { it.date in weekMon..weekSun }
                    weekSessions.forEach { s ->
                        val newSession = sessionRepository.save(
                            s.date.plus(7, DateTimeUnit.DAY),
                            s.startTime, s.endTime, s.clientIds, s.locationId, s.notes
                        )
                        syncSessionCreate(newSession, s.clientIds, s.locationId)
                    }
                    refreshSessions()
                },
            )

            Screen.CLIENTS -> ClientsScreen(
                openDialogVersion = createClientVersion,
                clients = clients,
                sessions = sessions,
                locations = locations,
                clientAvailabilitySlots = clientAvailabilitySlots,
                onAvailabilitySlotClick = { client, slot ->
                    openNewSession(slot.date, slot.startTime, slot.endTime, listOf(client.id))
                },
                onSettingsClick = { goBack() },
                onSave = { n, p, e, c, rate, pkgTotal, pkgUsed, birthDate, fbId ->
                    clientRepository.save(n, p, e, c, rate, pkgTotal, pkgUsed, birthDate, fbId)
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
                scheduleModeInitial = availabilityScheduleModeInitial,
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
                onSendTestNotification = onSendTestNotification,
                telegramLinked = telegramLinked,
                onLinkTelegram = onLinkTelegram,
                autoBackupEnabled = autoBackupEnabled,
                onAutoBackupToggle = onAutoBackupToggle,
                backupFolderName = backupFolderName,
                onPickBackupFolder = onPickBackupFolder,
                appMode = appMode,
                onAppModeChange = onAppModeChange,
                myUserId = myUserId,
                onCopyMyId = onCopyMyId,
                onPublishToFirebase = onPublishToFirebase,
                pendingBookingRequests = pendingBookingRequests,
                onConfirmBookingRequest = { request ->
                    val localClientId = clients.find { it.firebaseClientId == request.clientFirebaseId }?.id
                    val newSession = sessionRepository.save(
                        request.date, request.startTime, request.endTime,
                        listOfNotNull(localClientId), null, ""
                    )
                    syncSessionCreate(newSession, listOfNotNull(localClientId), null)
                    refreshSessions()
                    onConfirmBookingRequest?.invoke(request)
                },
                onDeclineBookingRequest = { request ->
                    onDeclineBookingRequest?.invoke(request)
                },
                clients = clients,
                clientAvailabilitySlots = clientAvailabilitySlots,
                onClientAvailabilityCreateSession = { slot ->
                    val localClientId = clients.find { it.firebaseClientId == slot.clientFirebaseId }?.id
                    openNewSession(slot.date, slot.startTime, slot.endTime)
                    if (localClientId != null) {
                        addSessionDate = slot.date
                        addSessionStartTime = slot.startTime
                        addSessionEndTime = slot.endTime
                        editingSession = null
                        showAddSession = true
                    }
                    onClientAvailabilityCreateSession?.invoke(slot)
                },
                onOpenChatWithClient = { clientFirebaseId, clientName ->
                    chatOpenClientId = clientFirebaseId
                    chatOpenClientName = clientName
                    onOpenChatWithClient?.invoke(clientFirebaseId, clientName)
                },
                connectedClients = connectedClients,
                onLinkClientFirebaseId = { firebaseId, localClientId ->
                    onLinkClientFirebaseId?.invoke(firebaseId, localClientId)
                    val linkedClient = clients.find { it.id == localClientId }
                    if (linkedClient != null) {
                        clients = clientRepository.getAll()
                        syncData()
                    }
                },
                trainerEmail = trainerEmail,
                onSaveTrainerEmail = onSaveTrainerEmail,
                onCopyTrainerEmail = onCopyTrainerEmail,
                onClearFirebaseData = onClearFirebaseData,
                highlightClientFirebaseId = highlightClientFirebaseId,
                onHighlightConsumed = { highlightClientFirebaseId = null },
                onAskClientAvailability = onAskClientAvailability,
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
                defaultClientIds = addSessionDefaultClientIds,
                clients = clients,
                locations = locations,
                existingSessions = sessions,
                availability = availability,
                workHoursStart = workHoursStart,
                workHoursEnd = workHoursEnd,
                onCreateClient = { name -> createNewClient(name) },
                onDismiss = { showAddSession = false; editingSession = null; addSessionEndTime = null; addSessionDefaultClientIds = emptyList() },
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
                    showAddSession = false; editingSession = null; addSessionEndTime = null; addSessionDefaultClientIds = emptyList()
                },
                onConfirmSeries = { dates, start, end, clientIds, locationId, notes ->
                    dates.forEach { date ->
                        val newSession = sessionRepository.save(date, start, end, clientIds, locationId, notes)
                        syncSessionCreate(newSession, clientIds, locationId)
                    }
                    refreshSessions()
                    showAddSession = false; editingSession = null; addSessionEndTime = null; addSessionDefaultClientIds = emptyList()
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

        // Trainer-side chat dialog
        chatOpenClientId?.let { partnerId ->
            ChatDialog(
                myId = myUserId ?: "",
                partnerName = chatOpenClientName,
                messages = chatMessages,
                onSend = { msg -> onSendChat?.invoke(msg) },
                onDismiss = { chatOpenClientId = null }
            )
        }

    }
}
