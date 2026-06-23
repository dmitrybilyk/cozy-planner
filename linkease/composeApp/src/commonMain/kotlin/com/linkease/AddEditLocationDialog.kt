package com.linkease

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LOCATION_COLORS = listOf(
    "#6A1B9A", "#4527A0", "#283593", "#1565C0",
    "#01579B", "#006064", "#BF360C", "#D32F2F",
    "#C2185B", "#827717", "#E65100", "#455A64"
)

@Composable
fun AddEditLocationDialog(
    initial: Location? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, address: String, colorHex: String, mapsLink: String?) -> Unit,
    onOpenMap: ((String) -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var mapsLink by remember { mutableStateOf(initial?.mapsLink ?: "") }
    var colorHex by remember { mutableStateOf(initial?.colorHex ?: LOCATION_COLORS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Нова локація" else "Редагувати локацію", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Назва *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Адреса") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (onOpenMap != null && address.isNotBlank()) {
                    TextButton(
                        onClick = { addressMapsUrl(address.trim())?.let(onOpenMap) },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("🔍 Знайти на карті") }
                }
                OutlinedTextField(
                    value = mapsLink, onValueChange = { mapsLink = it },
                    label = { Text("Посилання Google Maps") },
                    placeholder = { Text("Вставте посилання зі застосунку Карти") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text(
                    "Відкрийте місце у застосунку Google Maps, натисніть \"Поділитися\" і вставте посилання тут — це точніше, ніж пошук за адресою.",
                    fontSize = 11.sp, color = Color.Gray, lineHeight = 14.sp
                )
                Text("Колір", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                ColorPicker(selected = colorHex, colors = LOCATION_COLORS, onSelect = { colorHex = it })
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name.trim(), address.trim(), colorHex, mapsLink.trim().ifBlank { null }) }) {
                Text("Зберегти")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}
