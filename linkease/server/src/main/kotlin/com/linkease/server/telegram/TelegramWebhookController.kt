package com.linkease.server.telegram

import com.linkease.server.config.TelegramProperties
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class TelegramWebhookController(
    private val properties: TelegramProperties,
    private val botService: TelegramBotService,
) {
    @PostMapping("/api/telegram/webhook")
    fun webhook(
        @RequestBody update: TelegramUpdate,
        @RequestHeader("X-Telegram-Bot-Api-Secret-Token", required = false) secretToken: String?,
    ): ResponseEntity<Void> {
        if (properties.webhookSecret.isNotBlank() && secretToken != properties.webhookSecret) {
            return ResponseEntity.status(401).build()
        }
        update.message?.let { botService.handleIncomingMessage(it.chat.id, it.text) }
        return ResponseEntity.ok().build()
    }
}
