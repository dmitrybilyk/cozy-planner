package com.linkease

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsScreen(
    locations: List<Location>,
    sessions: List<Session> = emptyList(),
    clients: List<Client> = emptyList(),
    onSettingsClick: () -> Unit,
    onSave: (name: String, address: String, colorHex: String, mapsLink: String?) -> Unit,
    onUpdate: (Location) -> Unit,
    onDelete: (Long) -> Unit,
    onShare: (String) -> Unit = {},
    onCopyToClipboard: ((String) -> Unit)? = null,
    onOpenMap: ((String) -> Unit)? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Location?>(null) }
    var deleteCandidate by remember { mutableStateOf<Location?>(null) }
    var viewingLocation by remember { mutableStateOf<Location?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Локації", fontWeight = FontWeight.SemiBold) },
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
        var searchQuery by remember { mutableStateOf("") }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Пошук локації…", fontSize = 14.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            val filtered = if (searchQuery.isBlank()) locations
                else locations.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
            items(filtered, key = { it.id }) { loc ->
                LocationCard(
                    location = loc,
                    sessionCount = sessions.count { it.locationId == loc.id },
                    onView = { viewingLocation = loc },
                    onEdit = { editing = loc; showDialog = true },
                    onDelete = { deleteCandidate = loc },
                    onCopyLocation = onCopyToClipboard?.let { copy -> { copy(locationShareText(loc)) } },
                )
            }
            if (locations.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                        Text("Немає локацій. Натисніть + щоб додати.", color = Color.Gray)
                    }
                }
            }
        }
        } // Column
    }

    if (showDialog) {
        AddEditLocationDialog(
            initial = editing,
            onDismiss = { showDialog = false; editing = null },
            onOpenMap = onOpenMap,
            onConfirm = { name, address, colorHex, mapsLink ->
                val e = editing
                if (e == null) onSave(name, address, colorHex, mapsLink)
                else onUpdate(e.copy(name = name, address = address, colorHex = colorHex, mapsLink = mapsLink))
                showDialog = false; editing = null
            }
        )
    }

    deleteCandidate?.let { loc ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Видалити локацію?") },
            text = { Text("Видалити \"${loc.name}\"?") },
            confirmButton = {
                Button(
                    onClick = { onDelete(loc.id); deleteCandidate = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Видалити") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Скасувати") } }
        )
    }

    viewingLocation?.let { loc ->
        val locSessions = sessions.filter { it.locationId == loc.id }
            .sortedWith(compareByDescending<Session> { it.date }.thenBy { it.startTime.toMinutes() })
        LocationSessionsDialog(
            location = loc,
            sessions = locSessions,
            clients = clients,
            onDismiss = { viewingLocation = null },
        )
    }
}

@Composable
private fun LocationSessionsDialog(
    location: Location,
    sessions: List<Session>,
    clients: List<Client>,
    onDismiss: () -> Unit,
) {
    val color = hexToColor(location.colorHex)
    val clientsById = clients.associateBy { it.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                Text(location.name, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (sessions.isEmpty()) {
                    Text("Сесій не знайдено", fontSize = 14.sp, color = Color.Gray)
                } else {
                    Text("${sessions.size} сесій", fontSize = 13.sp, color = Color.Gray)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    sessions.take(30).forEach { s ->
                        val sessionClients = s.clientIds.mapNotNull { clientsById[it] }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${s.date.dayOfMonth} ${MONTHS_UK_SHORT.getOrNull(s.date.month.ordinal) ?: ""} ${s.date.year}",
                                    fontSize = 13.sp, fontWeight = FontWeight.Medium
                                )
                                if (sessionClients.isNotEmpty()) {
                                    Text(sessionClients.joinToString(", ") { it.name }, fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                            Text("${s.startTime.toStorageString()}–${s.endTime.toStorageString()}",
                                fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                    if (sessions.size > 30) Text("… ще ${sessions.size - 30}", fontSize = 12.sp, color = Color.Gray)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрити") } }
    )
}

private fun locationShareText(location: Location): String = buildString {
    append(location.name)
    if (location.address.isNotBlank()) { append("\n"); append(location.address) }
    location.mapsUrl()?.let { append("\n"); append(it) }
}

@Composable
private fun LocationCard(
    location: Location,
    sessionCount: Int = 0,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopyLocation: (() -> Unit)? = null,
) {
    val color = hexToColor(location.colorHex)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.width(5.dp).height(36.dp).clip(RoundedCornerShape(3.dp)).background(color))
            Column(modifier = Modifier.weight(1f)) {
                Text(location.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (location.address.isNotBlank()) Text(location.address, fontSize = 12.sp, color = Color.Gray)
                if (sessionCount > 0) Text("$sessionCount сесій", fontSize = 11.sp, color = Color.Gray)
            }
            if (onCopyLocation != null) {
                TextButton(onClick = onCopyLocation, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                ) { Text("📋", fontSize = 15.sp) }
            }
            TextButton(onClick = onView, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
            ) { Text("→", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) { Text("✎", fontSize = 18.sp) }
            TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("✕", fontSize = 14.sp) }
        }
    }
}
