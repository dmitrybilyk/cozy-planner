package com.linkease.server.kafka

import com.linkease.server.telegram.NotificationService
import com.linkease.server.telegram.TelegramBotService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service

@Primary
@Service
class ResilienceNotificationService(
    private val kafka: KafkaNotificationService,
    private val telegram: TelegramBotService,
    private val kafkaAvailability: KafkaAvailabilityService,
) : NotificationService {

    private val log = LoggerFactory.getLogger(ResilienceNotificationService::class.java)

    override fun sendReminder(chatId: Long, text: String) {
        if (!kafkaAvailability.isAvailable()) {
            log.info("Routing reminder → Telegram (Kafka unavailable)")
            telegram.sendReminder(chatId, text)
            return
        }
        log.info("Routing reminder → Kafka")
        try {
            kafka.sendReminder(chatId, text)
        } catch (e: Exception) {
            kafkaAvailability.markUnavailable()
            log.warn("Kafka send failed, falling back to Telegram: {}", e.message)
            telegram.sendReminder(chatId, text)
        }
    }
}
