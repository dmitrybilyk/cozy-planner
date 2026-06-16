package com.linkease.server.telegram

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class LinkRequest(val code: String)
data class LinkStatusResponse(val linked: Boolean)

@RestController
class TelegramLinkController(private val botService: TelegramBotService) {
    @PostMapping("/api/telegram/link")
    fun link(@RequestBody request: LinkRequest): ResponseEntity<Void> =
        if (botService.confirmLink(request.code)) ResponseEntity.ok().build() else ResponseEntity.badRequest().build()

    @GetMapping("/api/telegram/status")
    fun status(): LinkStatusResponse = LinkStatusResponse(linked = botService.linkedChatId() != null)
}
