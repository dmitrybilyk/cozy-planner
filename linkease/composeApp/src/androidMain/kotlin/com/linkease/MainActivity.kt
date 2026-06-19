package com.linkease

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ListenerRegistration
import com.linkease.db.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* silently degraded if denied */ }

    private val requestCalendarPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.WRITE_CALENDAR] != true) {
            Toast.makeText(this, "Для запису до Календаря потрібен дозвіл", Toast.LENGTH_SHORT).show()
        }
    }

    private var createSessionVersion by mutableStateOf(0L)
    private var showFreeTimeVersion by mutableStateOf(0L)
    private var createClientVersion by mutableStateOf(0L)
    private var showAvailabilityVersion by mutableStateOf(0L)
    private var refreshVersion by mutableStateOf(0L)
    private var pendingChatSenderId by mutableStateOf<String?>(null)
    private var pendingClientAvailabilityId by mutableStateOf<String?>(null)
    private var notificationSoundUriState by mutableStateOf<String?>(null)
    private var autoBackupEnabled by mutableStateOf(false)
    private var backupFolderName by mutableStateOf<String?>(null)
    private var appMode by mutableStateOf("user")
    private var myUserId by mutableStateOf<String?>(null)
    private var trainerData by mutableStateOf<TrainerData?>(null)
    private var isLoadingTrainer by mutableStateOf(false)
    private var trainerLoadError by mutableStateOf<String?>(null)
    private var savedTrainerId by mutableStateOf<String?>(null)
    private var clientSessions by mutableStateOf<List<ClientSession>>(emptyList())
    private var clientBookingRequests by mutableStateOf<List<BookingRequest>>(emptyList())
    private var pendingBookingRequests by mutableStateOf<List<BookingRequest>>(emptyList())
    private var clientAvailabilitySlots by mutableStateOf<List<ClientAvailabilitySlot>>(emptyList())
    private var myAvailabilitySlots by mutableStateOf<List<ClientAvailabilitySlot>>(emptyList())
    private var chatMessages by mutableStateOf<List<ChatMessage>>(emptyList())
    private var activeChatTrainerId by mutableStateOf<String?>(null)
    private var activeChatClientId by mutableStateOf<String?>(null)

    private lateinit var prefs: android.content.SharedPreferences

    private var trainerListener: ListenerRegistration? = null
    private var clientSessionsListener: ListenerRegistration? = null
    private var bookingRequestsListener: ListenerRegistration? = null
    private var clientBookingRequestsListener: ListenerRegistration? = null
    private var clientAvailabilityListener: ListenerRegistration? = null
    private var myAvailabilityListener: ListenerRegistration? = null
    private var chatListener: ListenerRegistration? = null
    private var connectionsListener: ListenerRegistration? = null

    private var connectedClients by mutableStateOf<List<Triple<String, String, String?>>>(emptyList())
    private var trainerEmail by mutableStateOf("")
    private var clientEmail by mutableStateOf("")
    private var onboardingDone by mutableStateOf(false)
    private var emailHint by mutableStateOf("")

    private val pickBackupFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) importFromUri(uri)
    }

    private val pickBackupFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            getSharedPreferences("linkease_prefs", MODE_PRIVATE).edit()
                .putString("backup_folder_uri", uri.toString()).apply()
            backupFolderName = resolveFolderName(uri)
            if (autoBackupEnabled) DriveBackupWorker.schedule(this)
        }
    }

    private val pickNotificationSound = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            val uriString = uri?.toString()
            getSharedPreferences("linkease_prefs", MODE_PRIVATE).edit()
                .putString("notification_sound_uri", uriString).apply()
            notificationSoundUriState = uriString
            NotificationHelper.recreateChannelWithSound(this, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#1A1A2E"))
        )

        prefs = getSharedPreferences("linkease_prefs", MODE_PRIVATE)
        notificationSoundUriState = prefs.getString("notification_sound_uri", null)
        NotificationHelper.createChannel(this, notificationSoundUriState?.let(Uri::parse))
        NotificationHelper.createBirthdayChannel(this)
        NotificationHelper.createBookingsChannel(this)
        NotificationHelper.createClientSessionsChannel(this)
        NotificationHelper.createClientAvailabilityChannel(this)
        NotificationHelper.createChatChannel(this)
        QuickNotification.createChannel(this)
        scheduleBirthdayAlarm()

        autoBackupEnabled = prefs.getBoolean("auto_backup_enabled", false)
        val folderUriStr = prefs.getString("backup_folder_uri", null)
        if (folderUriStr != null) backupFolderName = resolveFolderName(Uri.parse(folderUriStr))

        appMode = prefs.getString("app_mode", "user") ?: "user"
        savedTrainerId = prefs.getString("saved_trainer_id", null)
        trainerEmail = prefs.getString("trainer_email", "") ?: ""
        clientEmail = prefs.getString("client_email", "") ?: ""

        // Existing users (who had app_mode saved before onboarding was added) skip onboarding
        val savedMode = prefs.getString("app_mode", null)
        onboardingDone = prefs.getBoolean("onboarding_done", savedMode != null)

        // Try to read Google account email as a hint for onboarding
        emailHint = try {
            val accounts = android.accounts.AccountManager.get(this).getAccountsByType("com.google")
            accounts.firstOrNull()?.name ?: ""
        } catch (_: Exception) { "" }

        FirebaseHelper.signInAnonymously { uid ->
            myUserId = uid
            if (uid != null) {
                prefs.edit().putString("my_firebase_uid", uid).apply()
                if (onboardingDone) {
                    if (appMode == "user") {
                        startBookingRequestsListener(uid, prefs)
                    } else if (appMode == "client") {
                        val trainerId = savedTrainerId
                        if (trainerId != null) startClientListeners(uid, trainerId, prefs)
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            requestCalendarPermissions.launch(arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
            ))
        }

        when (intent.getStringExtra("action")) {
            "create_session"         -> createSessionVersion++
            "show_free_time"         -> showFreeTimeVersion++
            "create_client"          -> createClientVersion++
            "show_availability"      -> showAvailabilityVersion++
            "open_chat"              -> pendingChatSenderId = intent.getStringExtra("chat_sender_id")
            "open_client_availability" -> pendingClientAvailabilityId = intent.getStringExtra("client_firebase_id")
        }

        val dbHelper    = LinkDatabaseHelper(applicationContext)
        val sessionRepo = AndroidSessionRepository(dbHelper)
        val clientRepo  = AndroidClientRepository(dbHelper)
        val locationRepo = AndroidLocationRepository(dbHelper)
        val availRepo   = AndroidAvailabilityRepository(dbHelper)

        val workHoursStart  = prefs.getInt("work_hours_start", 7)
        val workHoursEnd    = prefs.getInt("work_hours_end", 22)
        val compactModeInit = prefs.getBoolean("compact_mode", true)
        val gcalSyncInit    = prefs.getBoolean("gcal_sync", false)

        // Write initial backup so share works even before the first data change.
        DataSyncHelper.exportAll(
            this,
            sessionRepo.getAll(),
            clientRepo.getAll(),
            locationRepo.getAll(),
            availRepo.getAll(),
        )

        handleDeepLink(intent)

        setContent {
            App(
                sessionRepository      = sessionRepo,
                clientRepository       = clientRepo,
                locationRepository     = locationRepo,
                availabilityRepository = availRepo,
                onShare = { text -> shareText(text) },
                onExportToCalendar = { session, clients, location ->
                    exportSessionViaIntent(session, clients, location)
                },
                onScheduleNotifications = { sessions, clients, locations ->
                    NotificationHelper.rescheduleAll(this, sessions, clients, locations)
                    lifecycleScope.launch { LinkEaseWidget().updateAll(this@MainActivity) }
                },
                onPinToStatusBar   = { QuickNotification.toggle(this) },
                autoBackupEnabled = autoBackupEnabled,
                onAutoBackupToggle = { enabled ->
                    autoBackupEnabled = enabled
                    prefs.edit().putBoolean("auto_backup_enabled", enabled).apply()
                    if (enabled) DriveBackupWorker.schedule(this) else DriveBackupWorker.cancel(this)
                },
                backupFolderName = backupFolderName,
                onPickBackupFolder = { pickBackupFolder.launch(null) },
                createSessionVersion = createSessionVersion,
                showFreeTimeVersion = showFreeTimeVersion,
                createClientVersion = createClientVersion,
                showAvailabilityVersion = showAvailabilityVersion,
                onExportDayDirect  = { date, sessions, clients, locations ->
                    exportDayToCalendar(date, sessions, clients, locations)
                },
                onDataChanged = { sessions, clients, locations, availability ->
                    DataSyncHelper.exportAll(this, sessions, clients, locations, availability)
                    val uid = myUserId
                    if (uid != null && appMode == "user") {
                        val workStart = prefs.getInt("work_hours_start", 7)
                        val workEnd = prefs.getInt("work_hours_end", 22)
                        FirebaseHelper.publishTrainerData(uid, availability, sessions, locations, clients, workStart, workEnd) { ok ->
                            if (!ok) Toast.makeText(this, "❌ Firebase: помилка синхронізації", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                refreshVersion = refreshVersion,
                onExportBackup = { DataSyncHelper.shareBackup(this) },
                onImportBackup = { pickBackupFile.launch(arrayOf("application/json", "*/*")) },
                workHoursStartInitial = workHoursStart,
                workHoursEndInitial   = workHoursEnd,
                onWorkHoursChange = { s, e ->
                    prefs.edit().putInt("work_hours_start", s).putInt("work_hours_end", e).apply()
                },
                compactModeInitial = compactModeInit,
                onCompactModeChange = { v ->
                    prefs.edit().putBoolean("compact_mode", v).apply()
                },
                gcalSyncInitial = gcalSyncInit,
                onGcalSyncChange = { v ->
                    prefs.edit().putBoolean("gcal_sync", v).apply()
                },
                onSessionSyncCreate = { session, clients, location ->
                    CalendarSyncHelper.insertEvent(this, session, clients, location)
                },
                onSessionSyncUpdate = { session, clients, location ->
                    CalendarSyncHelper.updateEvent(this, session, clients, location)
                },
                onSessionSyncDelete = { sessionId ->
                    CalendarSyncHelper.deleteEvent(this, sessionId)
                },
                onCopyToClipboard = { text ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("free_time", text))
                    Toast.makeText(this, "Скопійовано", Toast.LENGTH_SHORT).show()
                },
                onShareFreeTimeImage = { title, lines ->
                    FreeTimeImageHelper.share(this, title, lines)
                },
                onCopyFreeTimeImage = { title, lines ->
                    FreeTimeImageHelper.copyToClipboard(this, title, lines)
                },
                onShareAvailabilityImage = { title, lines ->
                    FreeTimeImageHelper.share(this, title, lines, heading = "Доступність")
                },
                onCopyAvailabilityImage = { title, lines ->
                    FreeTimeImageHelper.copyToClipboard(this, title, lines, heading = "Доступність")
                },
                onOpenUrl = { url ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                onEraseAllData = { sessionIds ->
                    sessionIds.forEach { NotificationHelper.cancelSession(this, it) }
                    lifecycleScope.launch { LinkEaseWidget().updateAll(this@MainActivity) }
                    Toast.makeText(this, "✅ Усі дані видалено", Toast.LENGTH_SHORT).show()
                },
                notificationSoundName = notificationSoundDisplayName(notificationSoundUriState),
                onPickNotificationSound = { launchSoundPicker() },
                onSendTestNotification = { NotificationHelper.sendTestNotification(this) },
                appMode = appMode,
                onAppModeChange = { mode ->
                    appMode = mode
                    prefs.edit().putString("app_mode", mode).apply()
                    if (mode == "user") {
                        trainerListener?.remove(); trainerListener = null
                        clientSessionsListener?.remove(); clientSessionsListener = null
                        clientBookingRequestsListener?.remove(); clientBookingRequestsListener = null
                        myAvailabilityListener?.remove(); myAvailabilityListener = null
                        chatListener?.remove(); chatListener = null
                        trainerData = null; trainerLoadError = null
                        clientSessions = emptyList(); clientBookingRequests = emptyList()
                        myAvailabilitySlots = emptyList(); chatMessages = emptyList()
                        val uid = myUserId
                        if (uid != null) startBookingRequestsListener(uid, prefs)
                    } else {
                        bookingRequestsListener?.remove(); bookingRequestsListener = null
                        clientAvailabilityListener?.remove(); clientAvailabilityListener = null
                        connectionsListener?.remove(); connectionsListener = null
                        pendingBookingRequests = emptyList(); clientAvailabilitySlots = emptyList()
                        connectedClients = emptyList()
                    }
                },
                myUserId = myUserId,
                onCopyMyId = {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("trainer_id", myUserId ?: ""))
                    Toast.makeText(this, "ID скопійовано", Toast.LENGTH_SHORT).show()
                },
                connectedClients = connectedClients,
                onLinkClientFirebaseId = { firebaseId, localClientId ->
                    val client = clientRepo.getAll().find { it.id == localClientId }
                    if (client != null) {
                        clientRepo.update(client.copy(firebaseClientId = firebaseId))
                        refreshVersion++
                        val uid = myUserId
                        if (uid != null) {
                            val wStart = prefs.getInt("work_hours_start", 7)
                            val wEnd = prefs.getInt("work_hours_end", 22)
                            FirebaseHelper.publishTrainerData(
                                uid, availRepo.getAll(), sessionRepo.getAll(),
                                locationRepo.getAll(), clientRepo.getAll(), wStart, wEnd
                            ) { ok ->
                                val msg = if (ok) "✅ Клієнта пов'язано, розклад надіслано" else "✅ Клієнта пов'язано"
                                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onPublishToFirebase = {
                    val uid = myUserId
                    if (uid != null) {
                        val workStart = prefs.getInt("work_hours_start", 7)
                        val workEnd = prefs.getInt("work_hours_end", 22)
                        FirebaseHelper.publishTrainerData(
                            uid, availRepo.getAll(), sessionRepo.getAll(), locationRepo.getAll(),
                            clientRepo.getAll(), workStart, workEnd
                        ) { ok ->
                            Toast.makeText(
                                this,
                                if (ok) "✅ Розклад опубліковано" else "❌ Помилка публікації",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(this, "Firebase ще не підключено", Toast.LENGTH_SHORT).show()
                    }
                },
                trainerData = trainerData,
                isLoadingTrainer = isLoadingTrainer,
                trainerLoadError = trainerLoadError,
                onConnectToTrainer = { email ->
                    isLoadingTrainer = true
                    trainerLoadError = null
                    FirebaseHelper.lookupTrainerByEmail(email) { resolvedId ->
                        if (resolvedId == null) {
                            isLoadingTrainer = false
                            trainerLoadError = "Тренера з таким email не знайдено"
                        } else {
                            savedTrainerId = resolvedId
                            prefs.edit().putString("saved_trainer_id", resolvedId).apply()
                            trainerListener?.remove()
                            clientSessionsListener?.remove()
                            clientBookingRequestsListener?.remove()
                            val uid = myUserId
                            if (uid != null) startClientListeners(uid, resolvedId, prefs)
                        }
                    }
                },
                onDisconnectFromTrainer = {
                    trainerListener?.remove(); trainerListener = null
                    clientSessionsListener?.remove(); clientSessionsListener = null
                    clientBookingRequestsListener?.remove(); clientBookingRequestsListener = null
                    myAvailabilityListener?.remove(); myAvailabilityListener = null
                    chatListener?.remove(); chatListener = null
                    trainerData = null; trainerLoadError = null
                    clientSessions = emptyList(); clientBookingRequests = emptyList()
                    myAvailabilitySlots = emptyList(); chatMessages = emptyList()
                },
                savedTrainerId = savedTrainerId,
                clientSessions = clientSessions,
                clientBookingRequests = clientBookingRequests,
                onBookSlot = { date, start, end, note ->
                    val uid = myUserId
                    val trainerId = savedTrainerId
                    if (uid != null && trainerId != null) {
                        FirebaseHelper.submitBookingRequest(trainerId, uid, date, start, end, note) { ok ->
                            Toast.makeText(
                                this,
                                if (ok) "✅ Запит надіслано тренеру" else "❌ Помилка надсилання запиту",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onConfirmClientSession = { sessionId ->
                    val uid = myUserId
                    if (uid != null) {
                        FirebaseHelper.confirmClientSession(uid, sessionId) { ok ->
                            if (ok) {
                                clientSessions = clientSessions.map {
                                    if (it.id == sessionId) it.copy(clientConfirmed = true) else it
                                }
                            }
                        }
                    }
                },
                onRejectClientSession = { sessionId ->
                    val uid = myUserId
                    if (uid != null) {
                        FirebaseHelper.rejectClientSession(uid, sessionId) { ok ->
                            if (ok) clientSessions = clientSessions.filter { it.id != sessionId }
                        }
                    }
                },
                clients = clientRepo.getAll(),
                clientAvailabilitySlots = clientAvailabilitySlots,
                onClientAvailabilityCreateSession = { _ -> /* session dialog opened in App */ },
                myAvailability = myAvailabilitySlots,
                onSaveMyAvailability = { slots ->
                    val uid = myUserId
                    val trainerId = savedTrainerId
                    if (uid != null && trainerId != null) {
                        FirebaseHelper.saveClientAvailability(uid, trainerId, slots) {}
                        myAvailabilitySlots = slots
                        val clientName = null
                        checkAndNotifyClientAvailability(uid, prefs)
                    }
                },
                chatMessages = chatMessages,
                onSendChat = { text ->
                    val uid = myUserId
                    val trainerId = if (appMode == "client") savedTrainerId else activeChatTrainerId
                    val clientId = if (appMode == "client") uid else activeChatClientId
                    if (uid != null && trainerId != null && clientId != null) {
                        FirebaseHelper.sendChatMessage(trainerId, clientId, uid, text) { ok, errMsg ->
                            if (!ok) {
                                val hint = if (errMsg?.contains("permission", ignoreCase = true) == true ||
                                               errMsg?.contains("PERMISSION_DENIED", ignoreCase = true) == true)
                                    "❌ Firestore: недостатньо прав. Оновіть правила у Firebase Console."
                                else "❌ Не вдалось надіслати: $errMsg"
                                Toast.makeText(this, hint, Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "❌ Чат не підключено (uid=$uid trainer=$trainerId client=$clientId)", Toast.LENGTH_LONG).show()
                    }
                },
                onOpenChatWithClient = { clientFirebaseId, clientName ->
                    val uid = myUserId
                    if (uid != null) {
                        activeChatTrainerId = uid
                        activeChatClientId = clientFirebaseId
                        chatListener?.remove()
                        chatListener = FirebaseHelper.listenChat(uid, clientFirebaseId) { messages ->
                            chatMessages = messages
                            checkAndNotifyChatMessages(messages, prefs, uid,
                                trainerId = uid, clientId = clientFirebaseId)
                        }
                    }
                },
                pendingBookingRequests = pendingBookingRequests,
                onConfirmBookingRequest = { request ->
                    FirebaseHelper.updateBookingRequestStatus(request.id, "confirmed") { _ -> }
                    pendingBookingRequests = pendingBookingRequests.filter { it.id != request.id }
                    val uid = myUserId
                    if (uid != null) {
                        val wStart = prefs.getInt("work_hours_start", 7)
                        val wEnd = prefs.getInt("work_hours_end", 22)
                        FirebaseHelper.publishTrainerData(uid, availRepo.getAll(), sessionRepo.getAll(),
                            locationRepo.getAll(), clientRepo.getAll(), wStart, wEnd) { _ -> }
                    }
                },
                onDeclineBookingRequest = { request ->
                    FirebaseHelper.updateBookingRequestStatus(request.id, "declined") { _ -> }
                    pendingBookingRequests = pendingBookingRequests.filter { it.id != request.id }
                },
                trainerEmail = trainerEmail,
                onSaveTrainerEmail = { email ->
                    trainerEmail = email
                    prefs.edit().putString("trainer_email", email).apply()
                    val uid = myUserId
                    if (uid != null) {
                        FirebaseHelper.publishTrainerEmail(uid, email) { ok ->
                            Toast.makeText(
                                this,
                                if (ok) "✅ Email збережено, клієнти можуть підключитись" else "❌ Помилка збереження email",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onCopyTrainerEmail = {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("trainer_email", trainerEmail))
                    Toast.makeText(this, "Email скопійовано", Toast.LENGTH_SHORT).show()
                },
                clientEmail = clientEmail,
                onCopyClientEmail = {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("client_email", clientEmail))
                    Toast.makeText(this, "Email скопійовано", Toast.LENGTH_SHORT).show()
                },
                onShareClientEmail = {
                    shareText("Мій email для Linkease: $clientEmail")
                },
                onClearFirebaseData = {
                    val uid = myUserId
                    if (uid != null) {
                        FirebaseHelper.clearTrainerData(uid, trainerEmail.ifBlank { null }) { ok ->
                            Toast.makeText(
                                this,
                                if (ok) "✅ Дані Firebase видалено" else "❌ Помилка видалення",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onboardingDone = onboardingDone,
                emailHint = emailHint,
                onOnboardingSelectTrainer = { email ->
                    trainerEmail = email
                    appMode = "user"
                    onboardingDone = true
                    prefs.edit()
                        .putString("trainer_email", email)
                        .putString("app_mode", "user")
                        .putBoolean("onboarding_done", true)
                        .apply()
                    val uid = myUserId
                    if (uid != null) {
                        FirebaseHelper.publishTrainerEmail(uid, email) {}
                        startBookingRequestsListener(uid, prefs)
                    }
                },
                onOnboardingSelectClient = { myEmail, trainerEmailInput ->
                    clientEmail = myEmail
                    prefs.edit().putString("client_email", myEmail).apply()
                    isLoadingTrainer = true
                    trainerLoadError = null
                    FirebaseHelper.lookupTrainerByEmail(trainerEmailInput) { resolvedId ->
                        if (resolvedId == null) {
                            isLoadingTrainer = false
                            trainerLoadError = "Тренера з таким email не знайдено"
                        } else {
                            savedTrainerId = resolvedId
                            appMode = "client"
                            onboardingDone = true
                            prefs.edit()
                                .putString("saved_trainer_id", resolvedId)
                                .putString("app_mode", "client")
                                .putBoolean("onboarding_done", true)
                                .apply()
                            val uid = myUserId
                            if (uid != null) startClientListeners(uid, resolvedId, prefs)
                        }
                    }
                },
                pendingChatPartnerId = pendingChatSenderId,
                onPendingChatConsumed = { pendingChatSenderId = null },
                pendingClientAvailabilityId = pendingClientAvailabilityId,
                onPendingClientAvailabilityConsumed = { pendingClientAvailabilityId = null },
                onAskClientAvailability = { clientFirebaseId, message ->
                    val uid = myUserId ?: return@App
                    val trainerId2 = uid
                    FirebaseHelper.sendChatMessage(trainerId2, clientFirebaseId, uid, message) { _, _ -> }
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (intent.getStringExtra("action")) {
            "create_session"         -> createSessionVersion++
            "show_free_time"         -> showFreeTimeVersion++
            "create_client"          -> createClientVersion++
            "show_availability"      -> showAvailabilityVersion++
            "open_chat"              -> pendingChatSenderId = intent.getStringExtra("chat_sender_id")
            "open_client_availability" -> pendingClientAvailabilityId = intent.getStringExtra("client_firebase_id")
        }
        handleDeepLink(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        trainerListener?.remove()
        clientSessionsListener?.remove()
        bookingRequestsListener?.remove()
        clientBookingRequestsListener?.remove()
        clientAvailabilityListener?.remove()
        myAvailabilityListener?.remove()
        chatListener?.remove()
        connectionsListener?.remove()
    }

    private fun startBookingRequestsListener(uid: String, prefs: android.content.SharedPreferences) {
        bookingRequestsListener?.remove()
        bookingRequestsListener = FirebaseHelper.listenBookingRequests(uid) { requests ->
            pendingBookingRequests = requests
            checkAndNotifyBookingRequests(requests, prefs)
        }
        // Trainer listens to client connections (for auto-linking by email)
        connectionsListener?.remove()
        connectionsListener = FirebaseHelper.listenTrainerConnections(uid) { connections ->
            connectedClients = connections
            // Auto-link clients by email: if a connection email matches a known client, link them
            val dbHelper = LinkDatabaseHelper(applicationContext)
            val clientRepo = AndroidClientRepository(dbHelper)
            val localClients = clientRepo.getAll()
            connections.forEach { (firebaseId, _, email) ->
                if (email.isNullOrBlank()) return@forEach
                val matched = localClients.find {
                    it.firebaseClientId.isNullOrBlank() &&
                    it.email.equals(email, ignoreCase = true)
                }
                if (matched != null) {
                    clientRepo.update(matched.copy(firebaseClientId = firebaseId))
                    refreshVersion++
                    val wStart = prefs.getInt("work_hours_start", 7)
                    val wEnd = prefs.getInt("work_hours_end", 22)
                    val availRepo = AndroidAvailabilityRepository(dbHelper)
                    val sessionRepo = AndroidSessionRepository(dbHelper)
                    val locationRepo = AndroidLocationRepository(dbHelper)
                    FirebaseHelper.publishTrainerData(uid, availRepo.getAll(), sessionRepo.getAll(),
                        locationRepo.getAll(), clientRepo.getAll(), wStart, wEnd) { _ -> }
                }
            }
        }
        // Trainer also listens to client availability
        clientAvailabilityListener?.remove()
        clientAvailabilityListener = FirebaseHelper.listenClientAvailabilityForTrainer(uid) { slots ->
            val prev = clientAvailabilitySlots
            clientAvailabilitySlots = slots
            // Notify about new slots from clients
            val newClientIds = slots.map { it.clientFirebaseId }.toSet() - prev.map { it.clientFirebaseId }.toSet()
            newClientIds.forEach { cid ->
                val seenKey = "seen_client_avail_$cid"
                val seenCount = prefs.getInt(seenKey, 0)
                val newCount = slots.count { it.clientFirebaseId == cid }
                if (newCount > seenCount) {
                    NotificationHelper.showClientAvailabilityNotification(this, cid, null)
                    prefs.edit().putInt(seenKey, newCount).apply()
                }
            }
        }
    }

    private fun startClientListeners(uid: String, trainerId: String, prefs: android.content.SharedPreferences) {
        // Register this client with the trainer so trainer can link/see us
        FirebaseHelper.registerClientWithTrainer(trainerId, uid, android.os.Build.MODEL, clientEmail.ifBlank { null }) {}

        trainerListener?.remove()
        trainerListener = FirebaseHelper.listenTrainerData(trainerId) { data ->
            isLoadingTrainer = false
            if (data != null) {
                trainerData = data
                trainerLoadError = null
            } else if (trainerData == null) {
                trainerLoadError = "Тренера не знайдено. Перевірте ID."
            }
        }

        clientSessionsListener?.remove()
        clientSessionsListener = FirebaseHelper.listenClientSessions(uid) { sessions ->
            clientSessions = sessions
            checkAndNotifyClientSessions(sessions, prefs)
        }

        clientBookingRequestsListener?.remove()
        clientBookingRequestsListener = FirebaseHelper.listenClientBookingRequests(uid, trainerId) { requests ->
            // Notify client when a previously pending request becomes confirmed
            val prevById = clientBookingRequests.associateBy { it.id }
            requests.forEach { req ->
                val prev = prevById[req.id]
                if (req.status == "confirmed" && prev != null && prev.status != "confirmed") {
                    NotificationHelper.showBookingConfirmedNotification(this, req)
                }
            }
            clientBookingRequests = requests
        }

        // Load client's own availability
        myAvailabilityListener?.remove()
        myAvailabilityListener = FirebaseHelper.listenClientAvailability(uid) { slots ->
            myAvailabilitySlots = slots
        }

        // Listen to chat with trainer
        chatListener?.remove()
        chatListener = FirebaseHelper.listenChat(trainerId, uid) { messages ->
            chatMessages = messages
            checkAndNotifyChatMessages(messages, prefs, uid,
                trainerId = trainerId, clientId = uid)
        }
    }

    private fun handleDeepLink(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != "linkease" || data.host != "trainer") return
        val trainerId = data.lastPathSegment?.takeIf { it.isNotBlank() } ?: return

        val wasUser = appMode == "user"
        appMode = "client"
        prefs.edit().putString("app_mode", "client").apply()
        savedTrainerId = trainerId
        prefs.edit().putString("saved_trainer_id", trainerId).apply()

        val uid = myUserId
        if (uid != null) {
            if (wasUser) {
                bookingRequestsListener?.remove(); bookingRequestsListener = null
                clientAvailabilityListener?.remove(); clientAvailabilityListener = null
                pendingBookingRequests = emptyList(); clientAvailabilitySlots = emptyList()
            }
            trainerListener?.remove()
            clientSessionsListener?.remove()
            clientBookingRequestsListener?.remove()
            myAvailabilityListener?.remove()
            chatListener?.remove()
            startClientListeners(uid, trainerId, prefs)
        }
        Toast.makeText(this, "Підключення до тренера...", Toast.LENGTH_SHORT).show()
    }

    private fun checkAndNotifyClientAvailability(clientId: String, prefs: android.content.SharedPreferences) {
        // Trainer-side: handled in startBookingRequestsListener
    }

    private fun checkAndNotifyChatMessages(
        messages: List<ChatMessage>,
        prefs: android.content.SharedPreferences,
        myId: String,
        trainerId: String = "",
        clientId: String = "",
    ) {
        val seenIds = prefs.getStringSet("seen_chat_messages", emptySet()) ?: emptySet()
        messages.filter { it.id !in seenIds && it.senderId != myId }.forEach { msg ->
            NotificationHelper.showChatNotification(
                context = this,
                senderId = msg.senderId,
                text = msg.text,
                trainerId = trainerId,
                clientId = clientId,
                myId = myId,
            )
        }
        prefs.edit().putStringSet("seen_chat_messages", (seenIds + messages.map { it.id }).toSet()).apply()
    }

    // ─── Notification sound ──────────────────────────────────────────────────

    private fun launchSoundPicker() {
        val currentUri = notificationSoundUriState?.let(Uri::parse) ?: Settings.System.DEFAULT_NOTIFICATION_URI
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Звук нагадування")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
        }
        pickNotificationSound.launch(intent)
    }

    private fun notificationSoundDisplayName(uriString: String?): String {
        if (uriString == null) return "Стандартний"
        return try {
            RingtoneManager.getRingtone(this, Uri.parse(uriString))?.getTitle(this) ?: "Стандартний"
        } catch (e: SecurityException) {
            "Стандартний"
        }
    }

    // ─── Import helper ───────────────────────────────────────────────────────

    private fun importFromUri(uri: Uri) {
        val data = DataSyncHelper.parseFromUri(uri, this)
        if (data == null) {
            Toast.makeText(this, "Помилка читання файлу резервної копії", Toast.LENGTH_SHORT).show()
            return
        }
        DataSyncHelper.replaceAllData(this, data)
        refreshVersion++
        Toast.makeText(
            this,
            "✅ Відновлено: ${data.clients.size} клієнтів, ${data.sessions.size} занять",
            Toast.LENGTH_LONG
        ).show()
    }

    // ─── Export helpers ──────────────────────────────────────────────────────

    private fun shareText(text: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(i, "Поділитися"))
    }

    private fun exportSessionViaIntent(session: Session, clients: List<Client>, location: Location?) {
        val tz      = TimeZone.currentSystemDefault()
        val startMs = session.date.atTime(session.startTime).toInstant(tz).toEpochMilliseconds()
        val endMs   = session.date.atTime(session.endTime).toInstant(tz).toEpochMilliseconds()
        startActivity(
            Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, clients.joinToString(", ") { it.name }.ifBlank { "Заняття" })
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
                .putExtra(CalendarContract.Events.EVENT_LOCATION, location?.name ?: "")
                .putExtra(CalendarContract.Events.DESCRIPTION, session.notes)
        )
    }

    // Exports all sessions for a day directly into the calendar (no UI per session).
    private fun exportDayToCalendar(
        date: LocalDate,
        sessions: List<Session>,
        allClients: List<Client>,
        allLocations: List<Location>,
    ) {
        if (sessions.isEmpty()) {
            Toast.makeText(this, "Немає занять на цей день", Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            requestCalendarPermissions.launch(arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
            ))
            Toast.makeText(this, "Надайте дозвіл і спробуйте ще раз", Toast.LENGTH_SHORT).show()
            return
        }

        val calId = getDefaultCalendarId()
        if (calId == null) {
            // No writable calendar found — open intent for each session
            sessions.forEach { s ->
                exportSessionViaIntent(s, allClients.filter { it.id in s.clientIds }, allLocations.firstOrNull { it.id == s.locationId })
            }
            return
        }

        val tz = TimeZone.currentSystemDefault()
        sessions.forEach { session ->
            val title    = allClients.filter { it.id in session.clientIds }.joinToString(", ") { it.name }.ifBlank { "Заняття" }
            val locName  = allLocations.firstOrNull { it.id == session.locationId }?.name ?: ""
            val startMs  = date.atTime(session.startTime).toInstant(tz).toEpochMilliseconds()
            val endMs    = date.atTime(session.endTime).toInstant(tz).toEpochMilliseconds()
            contentResolver.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startMs)
                put(CalendarContract.Events.DTEND, endMs)
                put(CalendarContract.Events.EVENT_LOCATION, locName)
                put(CalendarContract.Events.DESCRIPTION, session.notes ?: "")
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            })
        }
        Toast.makeText(this, "✅ ${sessions.size} занять додано до Calendar", Toast.LENGTH_SHORT).show()
    }

    private fun getDefaultCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection,
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC",
        )?.use { c -> if (c.moveToFirst()) return c.getLong(0) }
        return null
    }

    // ─── Birthday alarm ──────────────────────────────────────────────────────

    private fun scheduleBirthdayAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, BirthdayReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            this, 9000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(tz)
        val nextFireDate = if (now.time < LocalTime(9, 0)) now.date else now.date.plus(1, DateTimeUnit.DAY)
        val nextFire9am = LocalDateTime(nextFireDate, LocalTime(9, 0)).toInstant(tz).toEpochMilliseconds()
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, nextFire9am, AlarmManager.INTERVAL_DAY, pending)
    }

    // ─── Firebase notification helpers ──────────────────────────────────────

    private fun checkAndNotifyBookingRequests(requests: List<BookingRequest>, prefs: android.content.SharedPreferences) {
        val seenIds = prefs.getStringSet("seen_booking_requests", emptySet()) ?: emptySet()
        requests.filter { it.id !in seenIds }.forEach { NotificationHelper.showBookingRequestNotification(this, it) }
        prefs.edit().putStringSet("seen_booking_requests", (seenIds + requests.map { it.id }).toSet()).apply()
    }

    private fun checkAndNotifyClientSessions(sessions: List<ClientSession>, prefs: android.content.SharedPreferences) {
        val uid = myUserId ?: return
        val seenIds = prefs.getStringSet("seen_client_sessions", emptySet()) ?: emptySet()
        sessions.filter { it.id !in seenIds && !it.clientConfirmed }.forEach {
            NotificationHelper.showClientSessionNotification(this, it, uid)
        }
        prefs.edit().putStringSet("seen_client_sessions", (seenIds + sessions.map { it.id }).toSet()).apply()
    }

    // ─── Backup folder helpers ───────────────────────────────────────────────

    private fun resolveFolderName(uri: Uri): String {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            docId.substringAfterLast('/').ifBlank { uri.lastPathSegment ?: "Папка вибрана" }
        } catch (_: Exception) { "Папка вибрана" }
    }
}
