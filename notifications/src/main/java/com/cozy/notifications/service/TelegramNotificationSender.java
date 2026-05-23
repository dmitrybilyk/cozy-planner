package com.cozy.notifications.service;

import com.cozy.notifications.config.TelegramConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@Service
@Primary
@ConditionalOnProperty(name = "app.notification-sender", havingValue = "telegram")
public class TelegramNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationSender.class);
    private static final String TG_API_BASE = "https://api.telegram.org";

    private final TelegramConfig config;
    private final RestClient restClient;

    public TelegramNotificationSender(TelegramConfig config) {
        this.config = config;
        this.restClient = RestClient.builder()
                .baseUrl(TG_API_BASE)
                .build();
    }

    @Override
    public boolean sendMessage(String chatId, String text, String parseMode, Map<String, Object> replyMarkup) {
        return sendWithToken(config.getBotToken(), chatId, text, parseMode, replyMarkup);
    }

    @Override
    public boolean sendMessageToMentor(String chatId, String text, String parseMode, Map<String, Object> replyMarkup) {
        String token = config.isMentorBotEnabled() ? config.getMentorBotToken() : config.getBotToken();
        return sendWithToken(token, chatId, text, parseMode, replyMarkup);
    }

    private boolean sendWithToken(String botToken, String chatId, String text, String parseMode, Map<String, Object> replyMarkup) {
        if (botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {
            return false;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", parseMode != null ? parseMode : "HTML");
        if (replyMarkup != null) {
            body.put("reply_markup", replyMarkup);
        }

        String path = "/bot" + botToken + "/sendMessage";
        log.info("Sending Telegram message to chatId: {}, text length: {}", chatId, text.length());

        try {
            String response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            log.debug("Telegram response: {}", response);
            return true;
        } catch (Exception e) {
            log.error("Failed to send Telegram message: {}", e.getMessage());
            return false;
        }
    }
}
