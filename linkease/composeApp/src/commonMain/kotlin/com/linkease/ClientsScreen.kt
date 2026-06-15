package com.linkease

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    clients: List<Client>,
    sessions: List<Session> = emptyList(),
    locations: List<Location> = emptyList(),
    onSettingsClick: () -> Unit,
    onSave: (name: String, phone: String, email: String, colorHex: String, hourlyRate: Double) -> Unit,
    onUpdate: (Client) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Client?>(null) }
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
            items(clients, key = { it.id }) { client ->
                ClientCard(
                    client = client,
                    sessionCount = sessions.count { client.id in it.clientIds },
                    onTap = { viewingClient = client },
                    onEdit = { editing = client; showDialog = true },
                    onDelete = { deleteCandidate = client }
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
            onConfirm = { name, phone, email, colorHex, hourlyRate ->
                val e = editing
                if (e == null) onSave(name, phone, email, colorHex, hourlyRate)
                else onUpdate(e.copy(name = name, phone = phone, email = email, colorHex = colorHex, hourlyRate = hourlyRate))
                showDialog = false; editing = null
            }
        )
    }

    viewingClient?.let { client ->
        ClientSessionsDialog(
            client = client,
            sessions = sessions.filter { client.id in it.clientIds }.sortedByDescending { it.date },
            locations = locations,
            onEdit = { editing = client; showDialog = true; viewingClient = null },
            onDismiss = { viewingClient = null }
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
private fun ClientCard(client: Client, sessionCount: Int, onTap: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val color = hexToColor(client.colorHex)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        onClick = onTap
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
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
                Text(client.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                if (client.phone.isNotBlank()) Text(client.phone, fontSize = 13.sp, color = Color.Gray)
                if (client.email.isNotBlank()) Text(client.email, fontSize = 13.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (client.hourlyRate > 0) Text("${formatRate(client.hourlyRate)} ₴/год", fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
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
    }
}

private fun formatRate(rate: Double): String =
    if (rate == rate.toLong().toDouble()) rate.toLong().toString() else rate.toString()

@Composable
private fun ClientSessionsDialog(
    client: Client,
    sessions: List<Session>,
    locations: List<Location>,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
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
                if (client.hourlyRate > 0) {
                    Text("Ставка: ${formatRate(client.hourlyRate)} ₴/год", fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
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
