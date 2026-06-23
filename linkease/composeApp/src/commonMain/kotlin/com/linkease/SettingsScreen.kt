package com.linkease

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
    finOblik: Boolean = false,
    onFinOblikChange: ((Boolean) -> Unit)? = null,
    onReportClick: (() -> Unit)? = null,
    onExportBackup: (() -> Unit)? = null,
    onImportBackup: (() -> Unit)? = null,
    onEraseAllData: (() -> Unit)? = null,
    notificationsEnabled: Boolean = true,
    onNotificationsEnabledChange: ((Boolean) -> Unit)? = null,
    notifyMinutesBefore: Int = 10,
    onNotifyMinutesBeforeChange: ((Int) -> Unit)? = null,
    onSendTestNotification: (() -> Unit)? = null,
    onOpenNotificationSettings: (() -> Unit)? = null,
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
    onMicClick: (() -> Unit)? = null,
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
                onMicClick = onMicClick,
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
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    text = { Text("🔔", fontSize = 15.sp) })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 },
                    text = { Text("Довідка", fontSize = 13.sp) })
            }

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {

                    // ── Tab 0: Загальне ───────────────────────────────────────
                    0 -> {
                        Spacer(Modifier.height(8.dp))

                        // Email at top of common settings
                        if (appMode == "user" && myUserId != null && onSaveTrainerEmail != null) {
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
                                    ) { Text("OK") }
                                }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("✅ $trainerEmail", fontSize = 13.sp, color = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                                    if (onCopyTrainerEmail != null) {
                                        TextButton(onClick = onCopyTrainerEmail,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                            Text("📋", fontSize = 15.sp)
                                        }
                                    }
                                    TextButton(onClick = { editingEmail = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text("✏️", fontSize = 15.sp)
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                        }

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

                        if (onFinOblikChange != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("💰", fontSize = 24.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Фін.облік", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                    Text("Ставка клієнта, кнопка «Оплачено», звіт", fontSize = 12.sp, color = Color.Gray)
                                }
                                Switch(checked = finOblik, onCheckedChange = { onFinOblikChange(it) },
                                    colors = SwitchDefaults.colors(
                                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                                    ))
                            }
                        }
                    }

                    // ── Tab 1: Дані ───────────────────────────────────────────
                    1 -> {
                        Spacer(Modifier.height(8.dp))

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

                        if (appMode == "user" && myUserId != null && onClearFirebaseData != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
                            OutlinedButton(
                                onClick = onClearFirebaseData,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text("🗑️ Видалити дані з Firebase") }
                        }

                        Spacer(Modifier.height(8.dp))
                    }

                    // ── Tab 2: Сповіщення ─────────────────────────────────────
                    2 -> {
                        Spacer(Modifier.height(8.dp))

                        Text("Нагадування про заняття",
                            style = MaterialTheme.typography.labelMedium, color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🔔", fontSize = 22.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Увімкнути нагадування", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text("Сповіщення перед початком заняття", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = { onNotificationsEnabledChange?.invoke(it) },
                                colors = SwitchDefaults.colors(
                                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                                )
                            )
                        }

                        if (notificationsEnabled) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            Text("За скільки хвилин нагадати",
                                style = MaterialTheme.typography.labelMedium, color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            val minuteOptions = listOf(5, 10, 15, 20, 30, 45, 60)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                minuteOptions.forEach { mins ->
                                    val selected = notifyMinutesBefore == mins
                                    Surface(
                                        onClick = { onNotifyMinutesBeforeChange?.invoke(mins) },
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp,
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                            Text("$mins", fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                            }
                            Text("хвилин", fontSize = 11.sp, color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

                        if (onSendTestNotification != null) {
                            SettingsItem(icon = "🔔", title = "Надіслати тестове сповіщення",
                                subtitle = "Перевірити, чи надходять сповіщення",
                                onClick = onSendTestNotification)
                        }
                        if (onOpenNotificationSettings != null) {
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                            SettingsItem(icon = "⚙️", title = "Системні налаштування",
                                subtitle = "Відкрити системні параметри сповіщень додатку",
                                onClick = onOpenNotificationSettings)
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // ── Tab 3: Довідка ────────────────────────────────────────
                    3 -> {
                        Spacer(Modifier.height(8.dp))

                        HelpSection(title = "🎙 Голосові команди") {
                            HelpItem(
                                label = "Сесія",
                                example = "Іванов 24 червня 13:00 2 години",
                                note = "Клієнт, дата, час початку, тривалість. Можна додати локацію."
                            )
                            HelpItem(
                                label = "Доступність",
                                example = "Доступність 24 червня 13:00 4 години Левандівка",
                                note = "Починається зі слова «Доступність». Зберігається одразу без підтвердження. Локація необов'язкова."
                            )
                            HelpItem(
                                label = "Дата",
                                example = "Завтра / Субота / 24 червня / 15-го",
                                note = null
                            )
                            HelpItem(
                                label = "Час",
                                example = "13:00 / о 14:30 / пів на третю",
                                note = null
                            )
                            HelpItem(
                                label = "Тривалість",
                                example = "2 години / 90 хвилин / пів години",
                                note = null
                            )
                        }

                        HelpSection(title = "📅 Розклад і доступність") {
                            HelpParagraph("Доступність — це вікна часу, які ви встановлюєте вручну: наприклад, вівторок 10:00–14:00 у Левандівці.")
                            HelpParagraph("Вільний час — проміжки всередині доступності, де немає занять. Якщо доступність на день не задана — вільного часу немає. Приклад: доступність 10:00–14:00, заняття 11:00–12:00 → вільно 10:00–11:00 і 12:00–14:00.")
                        }

                        HelpSection(title = "📋 Основні функції") {
                            HelpItem(label = "Клієнти", example = null,
                                note = "Ведіть базу клієнтів з телефоном та пакетами занять. Кнопка 👥 на головному екрані.")
                            HelpItem(label = "Локації", example = null,
                                note = "Зберігайте адреси місць тренувань. Кнопка 📍 на головному екрані.")
                            HelpItem(label = "Поширення розкладу", example = null,
                                note = "Кнопки «Вільний час» і «Доступність» формують зображення для відправки клієнтам.")
                            HelpItem(label = "Підключення клієнтів", example = null,
                                note = "Клієнт встановлює застосунок і вводить ваш email. Ви бачите його доступність і можете надсилати запити.")
                            HelpItem(label = "Резервне копіювання", example = null,
                                note = "Автоматичне копіювання на Google Drive кожну годину. Налаштовується у вкладці «Дані».")
                            if (finOblik) {
                                HelpItem(label = "Фінансовий облік", example = null,
                                    note = "Задайте ставку клієнта — застосунок рахує дохід. Кнопка «Оплачено» фіксує платіж. Звіт відкривається кнопкою 📊 на головному екрані.")
                            }
                        }

                        Spacer(Modifier.height(16.dp))
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

@Composable
private fun HelpSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = Color.Gray,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun HelpItem(label: String, example: String?, note: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary)
            if (example != null) {
                Text("·", fontSize = 12.sp, color = Color.Gray)
                Text(example, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.weight(1f, fill = false),
                    overflow = TextOverflow.Ellipsis, maxLines = 2)
            }
        }
        if (note != null) {
            Text(note, fontSize = 12.sp, color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun HelpParagraph(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 2.dp))
}
