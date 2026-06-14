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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    clients: List<Client>,
    onSettingsClick: () -> Unit,
    onSave: (name: String, phone: String, email: String, colorHex: String) -> Unit,
    onUpdate: (Client) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Client?>(null) }
    var deleteCandidate by remember { mutableStateOf<Client?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Клієнти", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onSettingsClick) { Text("⚙", fontSize = 20.sp) } },
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
            onConfirm = { name, phone, email, colorHex ->
                val e = editing
                if (e == null) onSave(name, phone, email, colorHex)
                else onUpdate(e.copy(name = name, phone = phone, email = email, colorHex = colorHex))
                showDialog = false; editing = null
            }
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
private fun ClientCard(client: Client, onEdit: () -> Unit, onDelete: () -> Unit) {
    val color = hexToColor(client.colorHex)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        onClick = onEdit
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
            }
            TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("✕")
            }
        }
    }
}
