package com.linkease.server.kafka

import com.linkease.server.config.AppProperties
import com.linkease.server.telegram.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class KafkaNotificationService(
    private val kafkaTemplate: KafkaTemplate<String, Map<String, Any>>,
    private val appProperties: AppProperties,
) : NotificationService {

    private val log = LoggerFactory.getLogger(KafkaNotificationService::class.java)

    override fun sendReminder(chatId: Long, text: String) {
        val topic = appProperties.notificationTopic
        val payload = mapOf("chatId" to chatId, "text" to text)
        log.info("Publishing reminder to Kafka topic={} chatId={}", topic, chatId)
        kafkaTemplate.send(topic, chatId.toString(), payload)
    }
}
