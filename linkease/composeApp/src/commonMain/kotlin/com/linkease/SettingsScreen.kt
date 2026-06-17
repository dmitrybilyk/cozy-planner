package com.linkease

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
) {
    var localStart by remember { mutableStateOf(workHoursStart) }
    var localEnd   by remember { mutableStateOf(workHoursEnd) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var showEraseConfirm by remember { mutableStateOf(false) }

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
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) { Text("Скасувати") }
            }
        )
    }

    if (showEraseConfirm) {
        AlertDialog(
            onDismissRequest = { showEraseConfirm = false },
            title = { Text("Видалити всі дані?") },
            text = { Text("Усі клієнти, локації, сесії та доступність будуть видалені незворотно. Цю дію неможливо скасувати.") },
            confirmButton = {
                TextButton(onClick = { showEraseConfirm = false; onEraseAllData?.invoke() }) {
                    Text("Видалити все", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseConfirm = false }) { Text("Скасувати") }
            }
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
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)) {

            // ── Clients & Locations ────────────────────────────────────────
            SettingsItem(icon = "👥", title = "Клієнти", subtitle = "Додати або редагувати клієнтів", onClick = onClientsClick)
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingsItem(icon = "🏢", title = "Локації", subtitle = "Додати або редагувати локації", onClick = onLocationsClick)
            if (onReportClick != null) {
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                SettingsItem(icon = "📊", title = "Звіт", subtitle = "Статистика по клієнтах та доходах", onClick = onReportClick)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

            // ── Working hours ──────────────────────────────────────────────
            Text(
                "Робочий час",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Початок", fontSize = 14.sp, modifier = Modifier.weight(1f))
                HourStepper(
                    hour = localStart,
                    min = 0,
                    max = localEnd - 1,
                    onChange = { localStart = it; onWorkHoursChange?.invoke(it, localEnd) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Кінець", fontSize = 14.sp, modifier = Modifier.weight(1f))
                HourStepper(
                    hour = localEnd,
                    min = localStart + 1,
                    max = 24,
                    onChange = { localEnd = it; onWorkHoursChange?.invoke(localStart, it) }
                )
            }

            if (onSendTestNotification != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
                Text(
                    "Сповіщення",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                SettingsItem(
                    icon = "🔊",
                    title = "Надіслати тестове сповіщення",
                    subtitle = "Перевірити, як воно виглядає і звучить",
                    onClick = onSendTestNotification
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

            // ── Google Calendar sync ──────────────────────────────────────
            Text(
                "Інтеграції",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("📅", fontSize = 22.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Синхронізація з Google Calendar", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text("Нові та відредаговані сесії синхронізуються автоматично", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(
                    checked = gcalSync,
                    onCheckedChange = { onGcalSyncChange?.invoke(it) },
                    colors = SwitchDefaults.colors(
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    )
                )
            }
            if (gcalSync && onSyncAllFutureSessions != null) {
                OutlinedButton(
                    onClick = onSyncAllFutureSessions,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text("Синхронізувати всі майбутні сесії", fontSize = 13.sp)
                }
            }

            if (onLinkTelegram != null) {
                if (telegramLinked) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("✈️", fontSize = 22.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Telegram", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text("✅ Підключено — нагадування надходять у Telegram", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                } else {
                    var telegramCode by remember { mutableStateOf("") }
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("✈️", fontSize = 22.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Підключити Telegram", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text("Напишіть боту, отримайте код і введіть його тут", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = telegramCode,
                                onValueChange = { telegramCode = it },
                                label = { Text("Код") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = { onLinkTelegram(telegramCode); telegramCode = "" },
                                enabled = telegramCode.isNotBlank(),
                            ) {
                                Text("OK")
                            }
                        }
                    }
                }
            }

            if (onExportBackup != null || onImportBackup != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
                Text(
                    "Резервна копія",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                if (onExportBackup != null) {
                    SettingsItem(
                        icon = "📤",
                        title = "Поділитися резервною копією",
                        subtitle = "Надіслати JSON-файл на інший пристрій",
                        onClick = onExportBackup
                    )
                }
                if (onImportBackup != null) {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    SettingsItem(
                        icon = "📥",
                        title = "Відновити з файлу",
                        subtitle = "Завантажити JSON-файл резервної копії",
                        onClick = { showImportConfirm = true }
                    )
                }
            }

            if (onEraseAllData != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
                Text(
                    "Тестування",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                SettingsItem(
                    icon = "🗑️",
                    title = "Видалити всі дані",
                    subtitle = "Стерти клієнтів, локації, сесії та доступність",
                    onClick = { showEraseConfirm = true }
                )
            }
        }
    }
}

@Composable
private fun HourStepper(hour: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { if (hour > min) onChange(hour - 1) },
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors()
        ) { Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Text(
            "${hour.toString().padStart(2, '0')}:00",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp).width(52.dp),
        )
        FilledTonalIconButton(
            onClick = { if (hour < max) onChange(hour + 1) },
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors()
        ) { Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun SettingsItem(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(icon, fontSize = 24.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
        Text("›", fontSize = 20.sp, color = Color.LightGray)
    }
}
