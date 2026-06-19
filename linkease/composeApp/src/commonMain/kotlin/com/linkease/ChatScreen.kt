package com.linkease

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDialog(
    myId: String,
    partnerName: String,
    messages: List<ChatMessage>,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("💬", fontSize = 18.sp)
                Text(partnerName, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("Немає повідомлень", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    }
                    items(messages, key = { it.id }) { msg ->
                        ChatBubble(msg = msg, isMe = msg.senderId == myId)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Повідомлення…", fontSize = 13.sp) },
                        maxLines = 3,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    )
                    Button(
                        onClick = {
                            val t = text.trim()
                            if (t.isNotEmpty()) { onSend(t); text = "" }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        enabled = text.isNotBlank()
                    ) { Text("▶", fontSize = 16.sp) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрити") } }
    )
}

@Composable
private fun ChatBubble(msg: ChatMessage, isMe: Boolean) {
    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor  = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
    val tz = TimeZone.currentSystemDefault()
    val time = if (msg.timestamp > 0L) {
        val ldt = Instant.fromEpochMilliseconds(msg.timestamp).toLocalDateTime(tz)
        "${ldt.hour.toString().padStart(2,'0')}:${ldt.minute.toString().padStart(2,'0')}"
    } else ""

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(
                    topStart = 12.dp, topEnd = 12.dp,
                    bottomStart = if (isMe) 12.dp else 2.dp,
                    bottomEnd = if (isMe) 2.dp else 12.dp
                ))
                .background(bubbleColor)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            Text(msg.text, color = textColor, fontSize = 13.sp)
            if (time.isNotEmpty()) {
                Text(time, color = textColor.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }
    }
}
