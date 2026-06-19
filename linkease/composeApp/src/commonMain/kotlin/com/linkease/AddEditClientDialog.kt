package com.linkease

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    onConfirm: (name: String, phone: String, email: String, colorHex: String, hourlyRate: Double,
                packageTotal: Int, packageUsed: Int, birthDate: String?, firebaseClientId: String?) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    val colorHex = initial?.colorHex ?: CLIENT_COLORS.first()
    var pkgTotalText by remember { mutableStateOf(if ((initial?.packageTotal ?: 0) > 0) initial!!.packageTotal.toString() else "") }
    var pkgUsedText by remember { mutableStateOf(if ((initial?.packageUsed ?: 0) > 0) initial!!.packageUsed.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новий клієнт" else "Редагувати клієнта", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ім'я *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Телефон") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    label = { Text("Email клієнта") },
                    placeholder = { Text("email@example.com", color = Color.LightGray) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pkgTotalText,
                        onValueChange = { pkgTotalText = it.filter { c -> c.isDigit() } },
                        label = { Text("Пакет (всього)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = pkgUsedText,
                        onValueChange = { pkgUsedText = it.filter { c -> c.isDigit() } },
                        label = { Text("Пакет (використано)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    val pkgTotal = pkgTotalText.toIntOrNull() ?: 0
                    val pkgUsed = pkgUsedText.toIntOrNull() ?: 0
                    onConfirm(name.trim(), phone.trim(), email.trim(), colorHex,
                        initial?.hourlyRate ?: 0.0, pkgTotal, pkgUsed, initial?.birthDate, initial?.firebaseClientId)
                }
            }) {
                Text("Зберегти")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

private fun parseBirthdayInput(text: String): String? {
    if (text.isBlank()) return null
    val parts = text.split(".")
    if (parts.size != 2) return null
    val day = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    if (day !in 1..31 || month !in 1..12) return null
    return "${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
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
