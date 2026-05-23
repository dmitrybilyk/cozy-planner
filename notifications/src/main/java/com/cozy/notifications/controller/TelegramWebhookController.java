package com.cozy.notifications.controller;

import com.cozy.notifications.config.TelegramConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/telegram/webhook")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final TelegramConfig telegramConfig;
    private final RestClient restClient;
    private final String plannerBaseUrl;

    public TelegramWebhookController(TelegramConfig telegramConfig,
                                     @Value("${app.planner-base-url:http://localhost:8080}") String plannerBaseUrl) {
        this.telegramConfig = telegramConfig;
        this.plannerBaseUrl = plannerBaseUrl;
        this.restClient = RestClient.builder().build();
    }

    @PostMapping
    public ResponseEntity<String> webhook(@RequestBody String body) {
        return forward("/api/v1/telegram/webhook", body);
    }

    @PostMapping("/notification")
    public ResponseEntity<String> webhookNotification(@RequestBody String body) {
        return forward("/api/v1/telegram/webhook/notification", body);
    }

    @PostMapping("/mentor")
    public ResponseEntity<String> webhookMentor(@RequestBody String body) {
        return forward("/api/v1/telegram/webhook/mentor", body);
    }

    private ResponseEntity<String> forward(String path, String body) {
        if (!telegramConfig.isEnabled()) {
            return ResponseEntity.ok().build();
        }
        log.debug("Forwarding webhook to planner: {}{}", plannerBaseUrl, path);
        try {
            String response = restClient.post()
                    .uri(plannerBaseUrl + path)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to forward webhook to planner: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }
    }
}
