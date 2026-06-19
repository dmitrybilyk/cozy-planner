package com.linkease

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(
    emailHint: String = "",
    isLoading: Boolean = false,
    error: String? = null,
    onSelectTrainer: (email: String) -> Unit,
    onSelectClient: (myEmail: String, trainerEmail: String) -> Unit,
) {
    var role by remember { mutableStateOf<String?>(null) }
    var myEmailInput by remember { mutableStateOf(emailHint) }
    var trainerEmailInput by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when (role) {
                null -> {
                    Text("Ласкаво просимо!", fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    Text(
                        "Як ви хочете використовувати Linkease?",
                        fontSize = 15.sp, color = Color.Gray, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { role = "user"; myEmailInput = emailHint },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("🏋  Я надаю послуги", fontSize = 15.sp) }
                    OutlinedButton(
                        onClick = { role = "client"; myEmailInput = emailHint; trainerEmailInput = "" },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("🙋  Я отримую послуги", fontSize = 15.sp) }
                }

                "user" -> {
                    Text("Ваш email", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(
                        "Клієнти зможуть підключитись до вас за цим email",
                        fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center
                    )
                    OutlinedTextField(
                        value = myEmailInput,
                        onValueChange = { myEmailInput = it.trim() },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        onClick = { if (myEmailInput.isNotBlank()) onSelectTrainer(myEmailInput) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = myEmailInput.isNotBlank()
                    ) { Text("Продовжити", fontSize = 15.sp) }
                    TextButton(onClick = { role = null }) { Text("← Назад") }
                }

                "client" -> {
                    Text("Підключення до тренера", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    OutlinedTextField(
                        value = myEmailInput,
                        onValueChange = { myEmailInput = it.trim() },
                        label = { Text("Ваш email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = trainerEmailInput,
                        onValueChange = { trainerEmailInput = it.trim() },
                        label = { Text("Email тренера") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    if (error != null) {
                        Text(
                            error, color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp, textAlign = TextAlign.Center
                        )
                    }
                    Button(
                        onClick = {
                            if (myEmailInput.isNotBlank() && trainerEmailInput.isNotBlank())
                                onSelectClient(myEmailInput, trainerEmailInput)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = myEmailInput.isNotBlank() && trainerEmailInput.isNotBlank() && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp, color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Підключитись", fontSize = 15.sp)
                    }
                    TextButton(onClick = { role = null }) { Text("← Назад") }
                }
            }
        }
    }
}
