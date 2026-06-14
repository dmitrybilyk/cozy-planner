package com.linkease

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    onBack: () -> Unit,
    onAvailabilityClick: () -> Unit,
    onClientsClick: () -> Unit,
    onLocationsClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Налаштування", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("◀ Назад") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Color theme picker ──
            Text(
                "Кольорова тема",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppTheme.entries.forEach { theme ->
                    val isSelected = theme == currentTheme
                    val primary = hexToColor(theme.primaryHex)
                    val secondary = hexToColor(theme.secondaryHex)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onThemeChange(theme) }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Color swatch
                        Row(
                            modifier = Modifier.height(28.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                        ) {
                            Box(Modifier.weight(1f).fillMaxHeight().background(primary))
                            Box(Modifier.weight(1f).fillMaxHeight().background(secondary))
                        }
                        Text(
                            theme.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

            // ── Navigation items ──
            SettingsItem(icon = "📅", title = "Мій графік роботи", subtitle = "Налаштувати години доступності", onClick = onAvailabilityClick)
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingsItem(icon = "👥", title = "Клієнти", subtitle = "Додати або редагувати клієнтів", onClick = onClientsClick)
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingsItem(icon = "🏢", title = "Зали / Локації", subtitle = "Додати або редагувати локації", onClick = onLocationsClick)
        }
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
