package com.linkease

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Зали / Локації", fontWeight = FontWeight.SemiBold) },
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
            items(locations, key = { it.id }) { loc ->
                LocationCard(
                    location = loc,
                    onEdit = { editing = loc; showDialog = true },
                    onDelete = { deleteCandidate = loc },
                    onOpenMap = onOpenMap,
                    onCopyLocation = onCopyToClipboard?.let { copy -> { copy(locationShareText(loc)) } },
                    onShareLocation = { onShare(locationShareText(loc)) },
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
}

private fun locationShareText(location: Location): String = buildString {
    append(location.name)
    if (location.address.isNotBlank()) { append("\n"); append(location.address) }
    location.mapsUrl()?.let { append("\n"); append(it) }
}

@Composable
private fun LocationCard(
    location: Location,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenMap: ((String) -> Unit)? = null,
    onCopyLocation: (() -> Unit)? = null,
    onShareLocation: (() -> Unit)? = null,
) {
    val color = hexToColor(location.colorHex)
    val mapsUrl = location.mapsUrl()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        onClick = onEdit
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.width(6.dp).height(44.dp).clip(RoundedCornerShape(3.dp)).background(color)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(location.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (location.address.isNotBlank()) Text(location.address, fontSize = 13.sp, color = Color.Gray)
                }
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("✕")
                }
            }
            if (mapsUrl != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onOpenMap != null) {
                        TextButton(onClick = { onOpenMap(mapsUrl) }) { Text("📍 Карта", fontSize = 13.sp) }
                    }
                    if (onCopyLocation != null) {
                        TextButton(onClick = onCopyLocation) { Text("📋", fontSize = 15.sp) }
                    }
                    if (onShareLocation != null) {
                        TextButton(onClick = onShareLocation) { Text("↗", fontSize = 17.sp) }
                    }
                }
            }
        }
    }
}
