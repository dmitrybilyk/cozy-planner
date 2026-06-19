package com.linkease

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    openDialogVersion: Long = 0L,
    clients: List<Client>,
    sessions: List<Session> = emptyList(),
    locations: List<Location> = emptyList(),
    clientAvailabilitySlots: List<ClientAvailabilitySlot> = emptyList(),
    onAvailabilitySlotClick: ((client: Client, slot: ClientAvailabilitySlot) -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onSave: (name: String, phone: String, email: String, colorHex: String, hourlyRate: Double,
             packageTotal: Int, packageUsed: Int, birthDate: String?, firebaseClientId: String?) -> Unit,
    onUpdate: (Client) -> Unit,
    onDelete: (Long) -> Unit,
    connectedClients: List<Triple<String, String, String?>> = emptyList(),
    onLinkClientFirebaseId: ((firebaseId: String, localClientId: Long) -> Unit)? = null,
    onCreateAndLinkClient: ((firebaseId: String, email: String?) -> Unit)? = null,
    onChatClick: ((firebaseId: String, name: String) -> Unit)? = null,
    onAskClientAvailability: ((firebaseId: String, message: String) -> Unit)? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Client?>(null) }
    var linkingFirebaseId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(openDialogVersion) {
        if (openDialogVersion > 0L) { editing = null; showDialog = true }
    }
    var deleteCandidate by remember { mutableStateOf<Client?>(null) }
    var viewingClient by remember { mutableStateOf<Client?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Клієнти", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { TextButton(onClick = onSettingsClick) { Text("◀ Назад") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showDialog = true }) {
                Text("+", fontSize = 24.sp)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            val linkedFirebaseIds = clients.mapNotNull { it.firebaseClientId }.toSet()
            val unlinked = connectedClients.filter { (uid, _, _) -> uid !in linkedFirebaseIds }
            if (unlinked.isNotEmpty() && onLinkClientFirebaseId != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🔔 Нові підключення (${unlinked.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.SemiBold)
                            unlinked.forEach { (uid, deviceModel, clientEmail) ->
                                Row(modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (!clientEmail.isNullOrBlank()) {
                                            Text(clientEmail, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer)
                                            Text("📱 $deviceModel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                                        } else {
                                            Text("📱 $deviceModel", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        }
                                    }
                                    Button(onClick = { linkingFirebaseId = uid },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                        Text("Хто це?", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            items(clients, key = { it.id }) { client ->
                val slots = clientAvailabilitySlots.filter { it.clientFirebaseId == client.firebaseClientId }
                ClientCard(
                    client = client,
                    sessionCount = sessions.count { client.id in it.clientIds },
                    availabilitySlots = slots,
                    onTap = { viewingClient = client },
                    onEdit = { editing = client; showDialog = true },
                    onDelete = { deleteCandidate = client },
                    onSlotClick = if (onAvailabilitySlotClick != null) {
                        { slot -> onAvailabilitySlotClick(client, slot) }
                    } else null,
                )
            }
            if (clients.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                        Text("Немає клієнтів. Натисніть + щоб додати.", color = Color.Gray)
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddEditClientDialog(
            initial = editing,
            onDismiss = { showDialog = false; editing = null },
            onConfirm = { name, phone, email, colorHex, hourlyRate, pkgTotal, pkgUsed, birthDate, fbId ->
                val e = editing
                if (e == null) onSave(name, phone, email, colorHex, hourlyRate, pkgTotal, pkgUsed, birthDate, fbId)
                else onUpdate(e.copy(name = name, phone = phone, email = email, colorHex = colorHex,
                    hourlyRate = hourlyRate, packageTotal = pkgTotal, packageUsed = pkgUsed,
                    birthDate = birthDate, firebaseClientId = fbId))
                showDialog = false; editing = null
            }
        )
    }

    viewingClient?.let { client ->
        val slots = clientAvailabilitySlots.filter { it.clientFirebaseId == client.firebaseClientId }
        ClientSessionsDialog(
            client = client,
            sessions = sessions.filter { client.id in it.clientIds }.sortedByDescending { it.date },
            locations = locations,
            availabilitySlots = slots,
            onSlotClick = if (onAvailabilitySlotClick != null) {
                { slot -> onAvailabilitySlotClick(client, slot) }
            } else null,
            onEdit = { editing = client; showDialog = true; viewingClient = null },
            onDismiss = { viewingClient = null },
            onChatClick = if (client.firebaseClientId != null && onChatClick != null) {
                { onChatClick(client.firebaseClientId, client.name) }
            } else null,
            onAskAvailability = if (client.firebaseClientId != null && onAskClientAvailability != null) {
                { msg -> onAskClientAvailability(client.firebaseClientId, msg) }
            } else null,
        )
    }

    linkingFirebaseId?.let { fbId ->
        val connection = connectedClients.find { it.first == fbId }
        LinkClientDialog(
            firebaseId = fbId,
            clientEmail = connection?.third,
            unlinkedClients = clients.filter { it.firebaseClientId == null },
            onLink = { localClientId ->
                onLinkClientFirebaseId?.invoke(fbId, localClientId)
                linkingFirebaseId = null
            },
            onCreateNew = if (onCreateAndLinkClient != null) {
                { email -> onCreateAndLinkClient(fbId, email); linkingFirebaseId = null }
            } else null,
            onDismiss = { linkingFirebaseId = null }
        )
    }

    deleteCandidate?.let { client ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Видалити клієнта?") },
            text = { Text("Видалити \"${client.name}\"?") },
            confirmButton = {
                Button(
                    onClick = { onDelete(client.id); deleteCandidate = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Видалити") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Скасувати") } }
        )
    }
}

@Composable
private fun ClientCard(
    client: Client,
    sessionCount: Int,
    availabilitySlots: List<ClientAvailabilitySlot> = emptyList(),
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSlotClick: ((ClientAvailabilitySlot) -> Unit)? = null,
) {
    val color = hexToColor(client.colorHex)
    val hasPkg = client.packageTotal > 0
    val pkgRemaining = client.packageTotal - client.packageUsed
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        onClick = onTap
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = if (availabilitySlots.isNotEmpty()) 8.dp else 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(client.name.first().uppercaseChar().toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(client.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        if (client.firebaseClientId != null) Text("🔗", fontSize = 12.sp)
                    }
                    if (client.phone.isNotBlank()) Text(client.phone, fontSize = 13.sp, color = Color.Gray)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hasPkg) {
                            Text(
                                "$pkgRemaining/${client.packageTotal} ос.",
                                fontSize = 12.sp,
                                color = if (pkgRemaining <= 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (sessionCount > 0) Text("$sessionCount сес.", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                TextButton(onClick = onEdit, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)) {
                    Text("✎")
                }
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("✕")
                }
            }
            if (availabilitySlots.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availabilitySlots.forEach { slot ->
                        Surface(
                            onClick = { onSlotClick?.invoke(slot) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                "${slot.date.dayOfMonth} ${MONTHS_UK_SHORT.getOrNull(slot.date.month.ordinal) ?: ""} ${slot.startTime.toStorageString()}–${slot.endTime.toStorageString()}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatRate(rate: Double): String =
    if (rate == rate.toLong().toDouble()) rate.toLong().toString() else rate.toString()

@Composable
private fun ClientSessionsDialog(
    client: Client,
    sessions: List<Session>,
    locations: List<Location>,
    availabilitySlots: List<ClientAvailabilitySlot> = emptyList(),
    onSlotClick: ((ClientAvailabilitySlot) -> Unit)? = null,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    onChatClick: (() -> Unit)? = null,
    onAskAvailability: ((message: String) -> Unit)? = null,
) {
    var showAskDialog by remember { mutableStateOf(false) }
    if (showAskDialog && onAskAvailability != null) {
        AskAvailabilityDialog(
            clientName = client.name,
            onDismiss = { showAskDialog = false },
            onSend = { msg -> onAskAvailability(msg); showAskDialog = false }
        )
    }
    val color = hexToColor(client.colorHex)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    Text(client.name.first().uppercaseChar().toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(client.name, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onChatClick != null || onAskAvailability != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onChatClick != null) {
                            OutlinedButton(onClick = onChatClick,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)) {
                                Text("💬 Чат", fontSize = 13.sp)
                            }
                        }
                        if (onAskAvailability != null) {
                            OutlinedButton(onClick = { showAskDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)) {
                                Text("📅 Запит", fontSize = 13.sp)
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
                if (availabilitySlots.isNotEmpty()) {
                    Text("Доступність клієнта", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    availabilitySlots.sortedWith(compareBy({ it.date }, { it.startTime.toMinutes() })).forEach { slot ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${slot.date.dayOfMonth} ${MONTHS_UK_SHORT.getOrNull(slot.date.month.ordinal) ?: ""} ${slot.startTime.toStorageString()}–${slot.endTime.toStorageString()}",
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary
                            )
                            if (onSlotClick != null) {
                                TextButton(
                                    onClick = { onSlotClick(slot); onDismiss() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) { Text("+ Сесія", fontSize = 12.sp) }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
                if (client.packageTotal > 0) {
                    val remaining = client.packageTotal - client.packageUsed
                    Text(
                        "Пакет: ${client.packageUsed}/${client.packageTotal} занять (залишилось $remaining)",
                        fontSize = 13.sp,
                        color = if (remaining <= 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium
                    )
                }
                if (sessions.isEmpty()) {
                    Text("Сесій не знайдено", fontSize = 14.sp, color = Color.Gray)
                } else {
                    val totalMins = sessions.sumOf { it.endTime.toMinutes() - it.startTime.toMinutes() }
                    Text("${sessions.size} сесій · ${formatDuration(totalMins)}", fontSize = 13.sp, color = Color.Gray)
                    if (client.hourlyRate > 0) {
                        val revenue = totalMins / 60.0 * client.hourlyRate
                        Text("Дохід: ${formatRevenue(revenue)} ₴", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    sessions.take(20).forEach { s ->
                        val loc = locations.find { it.id == s.locationId }
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${s.date.dayOfMonth} ${MONTHS_UK_SHORT.getOrNull(s.date.month.ordinal) ?: ""} ${s.date.year}",
                                    fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                if (loc != null) Text(loc.name, fontSize = 11.sp, color = hexToColor(loc.colorHex))
                            }
                            Text("${s.startTime.toStorageString()}–${s.endTime.toStorageString()}",
                                fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                    if (sessions.size > 20) Text("… ще ${sessions.size - 20}", fontSize = 12.sp, color = Color.Gray)
                }
            }
        },
        confirmButton = { Button(onClick = onEdit) { Text("Редагувати") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрити") } }
    )
}

private fun formatRevenue(amount: Double): String = kotlin.math.round(amount).toString()

@Composable
private fun LinkClientDialog(
    firebaseId: String,
    clientEmail: String? = null,
    unlinkedClients: List<Client>,
    onLink: (Long) -> Unit,
    onCreateNew: ((email: String?) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Хто підключився?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!clientEmail.isNullOrBlank()) {
                    Text("📧 $clientEmail", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                }
                if (onCreateNew != null) {
                    Button(onClick = { onCreateNew(clientEmail) }, modifier = Modifier.fillMaxWidth()) {
                        Text("➕ Створити нового клієнта")
                    }
                    if (unlinkedClients.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        Text("або прив'язати до існуючого:", fontSize = 13.sp, color = Color.Gray)
                    }
                } else if (unlinkedClients.isEmpty()) {
                    Text("Немає клієнтів без прив'язки. Спочатку додайте клієнта.", fontSize = 13.sp, color = Color.Gray)
                } else {
                    Text("Виберіть клієнта зі списку:", fontSize = 13.sp, color = Color.Gray)
                }
                unlinkedClients.forEach { client ->
                    TextButton(onClick = { onLink(client.id) }, modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(client.name, modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
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
