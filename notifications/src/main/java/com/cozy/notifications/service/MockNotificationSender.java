package com.cozy.notifications.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
//@Primary
@ConditionalOnProperty(name = "app.notification-sender", havingValue = "mock", matchIfMissing = true)
public class MockNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(MockNotificationSender.class);

    @Override
    public boolean sendMessage(String chatId, String text, String parseMode, Map<String, Object> replyMarkup) {
        log.info("[MOCK] sendMessage to chatId={}: {}", chatId, text);
        return true;
    }

    @Override
    public boolean sendMessageToMentor(String chatId, String text, String parseMode, Map<String, Object> replyMarkup) {
        log.info("[MOCK] sendMessageToMentor to chatId={}: {}", chatId, text);
        return true;
    }
}
