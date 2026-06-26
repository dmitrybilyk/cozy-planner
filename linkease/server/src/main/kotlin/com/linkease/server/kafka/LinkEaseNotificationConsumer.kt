package com.linkease.server.kafka

import com.linkease.server.telegram.TelegramBotService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class LinkEaseNotificationConsumer(
    private val botService: TelegramBotService,
) {
    private val log = LoggerFactory.getLogger(LinkEaseNotificationConsumer::class.java)

    @KafkaListener(topics = ["\${app.notification-topic:linkease-notifications}"], groupId = "linkease-reminder-group")
    fun consume(payload: Map<String, Any>) {
        val chatId = when (val raw = payload["chatId"]) {
            is Long -> raw
            is Int -> raw.toLong()
            is Number -> raw.toLong()
            else -> raw?.toString()?.toLongOrNull()
        } ?: run {
            log.warn("Received notification with missing or invalid chatId: {}", payload)
            return
        }
        val text = payload["text"]?.toString() ?: return
        log.info("Consumed reminder from Kafka for chatId={}", chatId)
        botService.sendMessage(chatId, text)
    }
}
