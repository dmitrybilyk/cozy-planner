package com.linkease

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    workHoursStart: Int = 7,
    workHoursEnd: Int = 22,
    onWorkHoursChange: ((Int, Int) -> Unit)? = null,
    gcalSync: Boolean = false,
    onGcalSyncChange: ((Boolean) -> Unit)? = null,
    onSyncAllFutureSessions: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onAvailabilityClick: () -> Unit,
    onCreateClick: () -> Unit,
    onFreeTimeClick: () -> Unit,
    onClientsClick: () -> Unit,
    onLocationsClick: () -> Unit,
    onReportClick: (() -> Unit)? = null,
    onExportBackup: (() -> Unit)? = null,
    onImportBackup: (() -> Unit)? = null,
    onEraseAllData: (() -> Unit)? = null,
    onSendTestNotification: (() -> Unit)? = null,
    telegramLinked: Boolean = false,
    onLinkTelegram: ((code: String) -> Unit)? = null,
    autoBackupEnabled: Boolean = false,
    onAutoBackupToggle: ((Boolean) -> Unit)? = null,
    backupFolderName: String? = null,
    onPickBackupFolder: (() -> Unit)? = null,
    appMode: String = "user",
    onAppModeChange: ((String) -> Unit)? = null,
    myUserId: String? = null,
    onCopyMyId: (() -> Unit)? = null,
    onPublishToFirebase: (() -> Unit)? = null,
    trainerEmail: String = "",
    onSaveTrainerEmail: ((String) -> Unit)? = null,
    onCopyTrainerEmail: (() -> Unit)? = null,
    pendingBookingRequests: List<BookingRequest> = emptyList(),
    onConfirmBookingRequest: ((BookingRequest) -> Unit)? = null,
    onDeclineBookingRequest: ((BookingRequest) -> Unit)? = null,
    clients: List<Client> = emptyList(),
    clientAvailabilitySlots: List<ClientAvailabilitySlot> = emptyList(),
    onClientAvailabilityCreateSession: ((ClientAvailabilitySlot) -> Unit)? = null,
    onOpenChatWithClient: ((clientFirebaseId: String, clientName: String) -> Unit)? = null,
    connectedClients: List<Triple<String, String, String?>> = emptyList(),
    onLinkClientFirebaseId: ((firebaseId: String, localClientId: Long) -> Unit)? = null,
    onClearFirebaseData: (() -> Unit)? = null,
    highlightClientFirebaseId: String? = null,
    onHighlightConsumed: (() -> Unit)? = null,
    onAskClientAvailability: ((clientFirebaseId: String, message: String) -> Unit)? = null,
) {
    var localStart by remember { mutableStateOf(workHoursStart) }
    var localEnd   by remember { mutableStateOf(workHoursEnd) }
    var selectedTab by remember { mutableStateOf(0) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var showEraseConfirm by remember { mutableStateOf(false) }
    var linkingFirebaseId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(highlightClientFirebaseId) {
        if (highlightClientFirebaseId != null) {
            selectedTab = 1 // Дані tab
            onHighlightConsumed?.invoke()
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("Відновити дані?") },
            text = { Text("Це замінить усі поточні дані на дані з файлу. Цю дію неможливо скасувати.") },
            confirmButton = {
                TextButton(onClick = { showImportConfirm = false; onImportBackup?.invoke() }) {
                    Text("Відновити", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showImportConfirm = false }) { Text("Скасувати") } }
        )
    }

    if (showEraseConfirm) {
        AlertDialog(
            onDismissRequest = { showEraseConfirm = false },
            title = { Text("Видалити всі дані?") },
            text = { Text("Усі клієнти, локації, сесії та доступність будуть видалені незворотно.") },
            confirmButton = {
                TextButton(onClick = { showEraseConfirm = false; onEraseAllData?.invoke() }) {
                    Text("Видалити все", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showEraseConfirm = false }) { Text("Скасувати") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Налаштування", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            MainBottomNav(
                currentScreen = Screen.SETTINGS,
                onHomeClick = onFreeTimeClick,
                onAvailabilityClick = onAvailabilityClick,
                onCreateClick = onCreateClick,
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Tabs ──────────────────────────────────────────────────────────
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Загальне", fontSize = 13.sp) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("Дані", fontSize = 13.sp) })
            }

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {

                    // ── Tab 0: Загальне ───────────────────────────────────────
                    0 -> {
                        Spacer(Modifier.height(8.dp))

                        SettingsItem(icon = "👥", title = "Клієнти", subtitle = "Додати або редагувати клієнтів", onClick = onClientsClick)
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                        SettingsItem(icon = "🏢", title = "Локації", subtitle = "Додати або редагувати локації", onClick = onLocationsClick)
                        if (onReportClick != null) {
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                            SettingsItem(icon = "📊", title = "Звіт", subtitle = "Статистика по клієнтах та доходах", onClick = onReportClick)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

                        Text("Робочий час", style = MaterialTheme.typography.labelMedium, color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Початок", fontSize = 14.sp, modifier = Modifier.weight(1f))
                            HourStepper(hour = localStart, min = 0, max = localEnd - 1,
                                onChange = { localStart = it; onWorkHoursChange?.invoke(it, localEnd) })
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Кінець", fontSize = 14.sp, modifier = Modifier.weight(1f))
                            HourStepper(hour = localEnd, min = localStart + 1, max = 24,
                                onChange = { localEnd = it; onWorkHoursChange?.invoke(localStart, it) })
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

                        Text("Інтеграції", style = MaterialTheme.typography.labelMedium, color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("📅", fontSize = 22.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Синхронізація з Google Calendar", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text("Нові та відредаговані сесії синхронізуються автоматично", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(checked = gcalSync, onCheckedChange = { onGcalSyncChange?.invoke(it) },
                                colors = SwitchDefaults.colors(
                                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                                ))
                        }
                        if (gcalSync && onSyncAllFutureSessions != null) {
                            OutlinedButton(onClick = onSyncAllFutureSessions,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Text("Синхронізувати всі майбутні сесії", fontSize = 13.sp)
                            }
                        }

                        if (pendingBookingRequests.isNotEmpty() && onConfirmBookingRequest != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
                            Text("Запити на бронювання (${pendingBookingRequests.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            pendingBookingRequests.forEach { request ->
                                val reqClientName = clients.find { it.firebaseClientId == request.clientFirebaseId }?.name
                                BookingRequestItem(request = request,
                                    clientName = reqClientName,
                                    onConfirm = { onConfirmBookingRequest(request) },
                                    onDecline = { onDeclineBookingRequest?.invoke(request) })
                            }
                        }
                    }

                    // ── Tab 1: Дані ───────────────────────────────────────────
                    1 -> {
                        Spacer(Modifier.height(8.dp))

                        if (appMode == "user" && myUserId != null) {
                            if (onSaveTrainerEmail != null) {
                                var editingEmail by remember { mutableStateOf(trainerEmail.isBlank()) }
                                var emailInput by remember { mutableStateOf(trainerEmail) }
                                Text("Мій email (для підключення клієнтів)",
                                    style = MaterialTheme.typography.labelMedium, color = Color.Gray,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                if (editingEmail) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = emailInput,
                                            onValueChange = { emailInput = it.trim() },
                                            label = { Text("Email") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Button(
                                            onClick = { onSaveTrainerEmail(emailInput); editingEmail = false },
                                            enabled = emailInput.isNotBlank(),
                                        ) { Text("Зберегти") }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("✅ $trainerEmail", fontSize = 13.sp, color = Color(0xFF2E7D32),
                                            modifier = Modifier.weight(1f))
                                        if (onCopyTrainerEmail != null) {
                                            OutlinedButton(onClick = onCopyTrainerEmail,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                                Text("Копіювати", fontSize = 12.sp)
                                            }
                                        }
                                        OutlinedButton(onClick = { editingEmail = true },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                            Text("✏️", fontSize = 12.sp)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                Spacer(Modifier.height(8.dp))
                            }

                            if (onClearFirebaseData != null) {
                                OutlinedButton(
                                    onClick = onClearFirebaseData,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                ) {
                                    Text("🗑️ Видалити дані з Firebase")
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
                        }

                        if (onSendTestNotification != null) {
                            Text("Сповіщення", style = MaterialTheme.typography.labelMedium, color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            SettingsItem(icon = "🔊", title = "Надіслати тестове сповіщення",
                                subtitle = "Перевірити, як воно виглядає і звучить",
                                onClick = onSendTestNotification)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
                        }

                        if (onLinkTelegram != null) {
                            if (telegramLinked) {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("✈️", fontSize = 22.sp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Telegram", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                        Text("✅ Підключено", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                            } else {
                                var telegramCode by remember { mutableStateOf("") }
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("✈️", fontSize = 22.sp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Підключити Telegram", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                            Text("Напишіть боту, отримайте код і введіть його тут", fontSize = 12.sp, color = Color.Gray)
                                        }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(value = telegramCode, onValueChange = { telegramCode = it },
                                            label = { Text("Код") }, singleLine = true, modifier = Modifier.weight(1f))
                                        Button(onClick = { onLinkTelegram(telegramCode); telegramCode = "" },
                                            enabled = telegramCode.isNotBlank()) { Text("OK") }
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
                        }

                        if (onPickBackupFolder != null) {
                            Text("Авто-резервне копіювання",
                                style = MaterialTheme.typography.labelMedium, color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("☁️", fontSize = 22.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Щоденне резервне копіювання", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                    Text(if (backupFolderName != null) "Папка: $backupFolderName" else "Папку не вибрано",
                                        fontSize = 12.sp, color = Color.Gray)
                                }
                                Switch(checked = autoBackupEnabled, onCheckedChange = { onAutoBackupToggle?.invoke(it) },
                                    enabled = backupFolderName != null,
                                    colors = SwitchDefaults.colors(
                                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                                    ))
                            }
                            SettingsItem(icon = "📁", title = "Вибрати папку для копій",
                                subtitle = "Google Drive або локальна папка", onClick = onPickBackupFolder)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
                        }

                        if (onExportBackup != null || onImportBackup != null) {
                            Text("Резервна копія", style = MaterialTheme.typography.labelMedium, color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            if (onExportBackup != null) {
                                SettingsItem(icon = "📤", title = "Поділитися резервною копією",
                                    subtitle = "Надіслати JSON-файл на інший пристрій", onClick = onExportBackup)
                            }
                            if (onImportBackup != null) {
                                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                                SettingsItem(icon = "📥", title = "Відновити з файлу",
                                    subtitle = "Завантажити JSON-файл резервної копії",
                                    onClick = { showImportConfirm = true })
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
                        }

                        if (onEraseAllData != null) {
                            SettingsItem(icon = "🗑️", title = "Видалити всі дані",
                                subtitle = "Стерти клієнтів, локації, сесії та доступність",
                                onClick = { showEraseConfirm = true })
                        }

                        Spacer(Modifier.height(8.dp))
                    }

                }
            }
        }
    }

    linkingFirebaseId?.let { fbId ->
        LinkClientDialog(
            firebaseId = fbId,
            unlinkedClients = clients.filter { it.firebaseClientId == null },
            onLink = { localClientId ->
                onLinkClientFirebaseId?.invoke(fbId, localClientId)
                linkingFirebaseId = null
            },
            onDismiss = { linkingFirebaseId = null }
        )
    }
}

@Composable
private fun LinkClientDialog(
    firebaseId: String,
    unlinkedClients: List<Client>,
    onLink: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Хто підключився?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (unlinkedClients.isEmpty()) {
                    Text("Немає клієнтів без прив'язки. Спочатку додайте клієнта.", fontSize = 13.sp, color = Color.Gray)
                } else {
                    Text("Виберіть клієнта зі списку:", fontSize = 13.sp, color = Color.Gray)
                    unlinkedClients.forEach { client ->
                        TextButton(onClick = { onLink(client.id) }, modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(client.name, modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start, fontSize = 15.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

@Composable
private fun HourStepper(hour: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { if (hour > min) onChange(hour - 1) }, modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors()) {
            Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Text("${hour.toString().padStart(2, '0')}:00", fontSize = 15.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp).width(52.dp))
        FilledTonalIconButton(onClick = { if (hour < max) onChange(hour + 1) }, modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors()) {
            Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BookingRequestItem(request: BookingRequest, clientName: String? = null, onConfirm: () -> Unit, onDecline: () -> Unit) {
    val dateStr = "${request.date.dayOfMonth}.${request.date.monthNumber.toString().padStart(2,'0')}.${request.date.year}"
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            if (!clientName.isNullOrBlank()) Text(clientName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("$dateStr · ${request.startTime.toStorageString()}–${request.endTime.toStorageString()}",
                fontWeight = FontWeight.Medium, fontSize = 13.sp)
            if (!request.clientNote.isNullOrBlank()) Text(request.clientNote, fontSize = 12.sp, color = Color.Gray)
        }
        TextButton(onClick = onConfirm,
            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF2E7D32))) { Text("✓ Так") }
        TextButton(onClick = onDecline,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("✕") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClientAvailabilityRow(
    client: Client,
    slots: List<ClientAvailabilitySlot>,
    isHighlighted: Boolean = false,
    onSlotClick: (ClientAvailabilitySlot) -> Unit,
    onChatClick: (() -> Unit)? = null,
    onAskAvailability: ((message: String) -> Unit)? = null,
) {
    var showAskDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
            .then(if (isHighlighted) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) else Modifier)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("👤 ${client.name}", fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (onAskAvailability != null) {
                TextButton(onClick = { showAskDialog = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("📅 Запит", fontSize = 12.sp)
                }
            }
            if (onChatClick != null) {
                TextButton(onClick = onChatClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("💬", fontSize = 14.sp)
                }
            }
        }
        if (slots.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                slots.sortedWith(compareBy({ it.date }, { it.startTime.toMinutes() })).forEach { slot ->
                    val label = "${slot.date.dayOfMonth}.${slot.date.monthNumber.toString().padStart(2,'0')} ${slot.startTime.toStorageString()}–${slot.endTime.toStorageString()}"
                    SuggestionChip(onClick = { onSlotClick(slot) },
                        label = { Text(label, fontSize = 11.sp) },
                        icon = { Text("📆", fontSize = 12.sp) })
                }
            }
        }
    }

    if (showAskDialog && onAskAvailability != null) {
        AskAvailabilityDialog(
            clientName = client.name,
            onDismiss = { showAskDialog = false },
            onSend = { msg -> onAskAvailability(msg); showAskDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskAvailabilityDialog(
    clientName: String,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Запросити доступність") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Надіслати запит $clientName встановити доступність:", fontSize = 13.sp, color = Color.Gray)
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Коментар (необов'язково)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val msg = if (comment.isBlank())
                    "Будь ласка, вкажіть свою доступність для занять 📅"
                else
                    "Будь ласка, вкажіть доступність: $comment"
                onSend(msg)
            }) { Text("Надіслати") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

@Composable
private fun SettingsItem(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(icon, fontSize = 24.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
        Text("›", fontSize = 20.sp, color = Color.LightGray)
    }
}
