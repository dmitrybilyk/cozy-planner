package com.linkease

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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

        var telegramLinked by mutableStateOf(fetchTelegramLinked())

        ComposeViewport(document.body!!) {
            App(
                sessionRepository = sessionRepository,
                clientRepository = clientRepository,
                locationRepository = locationRepository,
                availabilityRepository = availabilityRepository,
                onShare = { text ->
                    // Try Web Share API, fall back to clipboard
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
            )
        }
    }
}

private suspend fun isAuthenticated(): Boolean =
    try {
        apiGetRaw("/api/me")
        true
    } catch (e: ApiException) {
        false
    }

private suspend fun fetchTelegramLinked(): Boolean =
    try {
        apiGet("/api/telegram/status", TelegramStatusDto.serializer()).linked
    } catch (e: ApiException) {
        false
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
