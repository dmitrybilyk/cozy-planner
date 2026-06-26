package com.linkease

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MODE_KEY = "linkease_app_mode"

@JsFun("(key) => window.localStorage.getItem(key)")
private external fun lsGetItem(key: String): JsString?

@JsFun("(key, value) => window.localStorage.setItem(key, value)")
private external fun lsSetItem(key: String, value: String)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    GlobalScope.launch {
        if (!isAuthenticated()) {
            renderSignInPrompt()
            return@launch
        }

        val clientRepository = ApiClientRepository()
        val locationRepository = ApiLocationRepository()
        val sessionRepository = ApiSessionRepository()
        val availabilityRepository = ApiAvailabilityRepository()
        clientRepository.load()
        locationRepository.load()
        sessionRepository.load()
        availabilityRepository.load()

        val tgStatus = fetchTelegramStatus()
        var telegramLinked by mutableStateOf(tgStatus.linked)
        var telegramBotName by mutableStateOf(tgStatus.botUsername)
        var refreshVersion by mutableStateOf(0L)

        val savedMode = lsGetItem(MODE_KEY)?.toString()
        var appMode by mutableStateOf(savedMode ?: "")

        openSse("/api/events")
        GlobalScope.launch {
            while (true) {
                delay(300)
                val raw = drainSseQueue().toString()
                if (raw.isNotEmpty()) {
                    val entities = raw.split(",").toSet()
                    if ("sessions" in entities)     sessionRepository.load()
                    if ("clients" in entities)      clientRepository.load()
                    if ("locations" in entities)    locationRepository.load()
                    if ("availability" in entities) availabilityRepository.load()
                    refreshVersion++
                }
            }
        }

        ComposeViewport(document.body!!) {
            if (appMode.isEmpty()) {
                ModePicker(
                    onSelectMode = { mode ->
                        lsSetItem(MODE_KEY, mode)
                        appMode = mode
                    }
                )
            } else {
                App(
                    sessionRepository = sessionRepository,
                    clientRepository = clientRepository,
                    locationRepository = locationRepository,
                    availabilityRepository = availabilityRepository,
                    onShare = { text ->
                        window.navigator.clipboard.writeText(text)
                        window.alert("Текст скопійовано в буфер обміну:\n\n$text")
                    },
                    onCopyToClipboard = { text -> window.navigator.clipboard.writeText(text) },
                    onOpenUrl = { url -> window.open(url, "_blank") },
                    telegramLinked = telegramLinked,
                    onLinkTelegram = { code ->
                        GlobalScope.launch {
                            if (linkTelegram(code)) telegramLinked = true
                        }
                    },
                    telegramBotName = telegramBotName,
                    appMode = appMode,
                    onAppModeChange = { mode ->
                        lsSetItem(MODE_KEY, mode)
                        appMode = mode
                    },
                    refreshVersion = refreshVersion,
                )
            }
        }
    }
}

@Composable
private fun ModePicker(onSelectMode: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Оберіть режим", fontSize = 22.sp, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSelectMode("user") },
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("Тренер", fontSize = 16.sp)
            }
            OutlinedButton(
                onClick = { onSelectMode("client") },
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("Клієнт", fontSize = 16.sp)
            }
        }
    }
}

private suspend fun isAuthenticated(): Boolean =
    try {
        apiGetRaw("/api/me")
        true
    } catch (e: Throwable) {
        println("isAuthenticated: ${e::class.simpleName} — ${e.message}")
        false
    }

private suspend fun fetchTelegramStatus(): TelegramStatusDto =
    try {
        apiGet("/api/telegram/status", TelegramStatusDto.serializer())
    } catch (e: Throwable) {
        TelegramStatusDto(linked = false)
    }

private suspend fun linkTelegram(code: String): Boolean =
    apiPostRaw("/api/telegram/link", TelegramLinkRequestDto(code), TelegramLinkRequestDto.serializer())

private fun renderSignInPrompt() {
    document.body?.innerHTML = """
        <div style="display:flex;align-items:center;justify-content:center;height:100vh;font-family:sans-serif">
            <a href="/oauth2/authorization/google" style="font-size:1.2rem;padding:0.75rem 1.5rem;background:#2196F3;color:white;border-radius:8px;text-decoration:none">
                Увійти через Google
            </a>
        </div>
    """.trimIndent()
}
