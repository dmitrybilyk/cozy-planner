package com.linkease

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        App(
            sessionRepository = BrowserSessionRepository(),
            clientRepository = BrowserClientRepository(),
            locationRepository = BrowserLocationRepository(),
            availabilityRepository = BrowserAvailabilityRepository(),
            onShare = { text ->
                // Try Web Share API, fall back to clipboard
                window.navigator.clipboard.writeText(text)
                window.alert("Текст скопійовано в буфер обміну:\n\n$text")
            },
            onCopyToClipboard = { text -> window.navigator.clipboard.writeText(text) },
            onOpenUrl = { url -> window.open(url, "_blank") },
        )
    }
}
