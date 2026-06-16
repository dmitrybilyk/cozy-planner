package com.linkease

import android.Manifest
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
    private var refreshVersion by mutableStateOf(0L)
    private var notificationSoundUriState by mutableStateOf<String?>(null)

    private val pickBackupFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) importFromUri(uri)
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

        val prefs = getSharedPreferences("linkease_prefs", MODE_PRIVATE)
        notificationSoundUriState = prefs.getString("notification_sound_uri", null)
        NotificationHelper.createChannel(this, notificationSoundUriState?.let(Uri::parse))
        QuickNotification.createChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            requestCalendarPermissions.launch(arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
            ))
        }

        if (intent.getStringExtra("action") == "create_session") createSessionVersion++

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
                createSessionVersion = createSessionVersion,
                onExportDayDirect  = { date, sessions, clients, locations ->
                    exportDayToCalendar(date, sessions, clients, locations)
                },
                onDataChanged = { sessions, clients, locations, availability ->
                    DataSyncHelper.exportAll(this, sessions, clients, locations, availability)
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
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra("action") == "create_session") createSessionVersion++
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
}
