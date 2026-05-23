package com.cozy.notifications.service;

import com.cozy.notifications.controller.NotificationController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);
    private final NotificationSender notificationSender;

    public NotificationEventConsumer(NotificationSender notificationSender) {
        this.notificationSender = notificationSender;
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "${app.notification-topic:notification-events}", groupId = "${spring.kafka.consumer.group-id:notifications-group}")
    public void consume(Map<String, Object> event) {
        String type = (String) event.get("type");
        String chatId = (String) event.get("chatId");
        String text = (String) event.get("text");
        String parseMode = (String) event.get("parseMode");
        Map<String, Object> replyMarkup = (Map<String, Object>) event.get("replyMarkup");

        log.info("Consumed {} event for chatId={}", type, chatId);

        boolean success;
        if ("send-mentor".equals(type)) {
            success = notificationSender.sendMessageToMentor(chatId, text, parseMode, replyMarkup);
        } else {
            success = notificationSender.sendMessage(chatId, text, parseMode, replyMarkup);
        }

        if (success) {
            log.info("Successfully processed {} event for chatId={}", type, chatId);
        } else {
            log.warn("Failed to process {} event for chatId={}", type, chatId);
        }
    }
}
