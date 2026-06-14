package com.linkease

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val CLIENT_COLORS = listOf(
    "#C62828", "#AD1457", "#6A1B9A", "#4527A0",
    "#283593", "#1565C0", "#00838F", "#00695C",
    "#2E7D32", "#558B2F", "#F57F17", "#E65100"
)

@Composable
fun AddEditClientDialog(
    initial: Client? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, email: String, colorHex: String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var colorHex by remember { mutableStateOf(initial?.colorHex ?: CLIENT_COLORS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новий клієнт" else "Редагувати клієнта", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ім'я *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Телефон") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Колір", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                ColorPicker(selected = colorHex, colors = CLIENT_COLORS, onSelect = { colorHex = it })
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name.trim(), phone.trim(), email.trim(), colorHex) }) {
                Text("Зберегти")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: String, colors: List<String>, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { hex ->
            val isSelected = hex == selected
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(hexToColor(hex))
                    .then(if (isSelected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                    .clickable { onSelect(hex) }
            )
        }
    }
}
