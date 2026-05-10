package com.cozy.planner.controllers;

import com.cozy.planner.service.TelegramService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
@Slf4j
public class TelegramController {

    private final TelegramService telegramService;
    private final ObjectMapper objectMapper;

    public TelegramController(TelegramService telegramService, ObjectMapper objectMapper) {
        this.telegramService = telegramService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/telegram/webhook")
    public Mono<ResponseEntity<String>> webhook(@RequestBody String body) {
        if (!telegramService.isEnabled()) {
            return Mono.just(ResponseEntity.ok().build());
        }

        log.debug("Received Telegram webhook: {}", body);

        try {
            JsonNode json = objectMapper.readTree(body);
            JsonNode message = json.path("message");
            JsonNode callbackQuery = json.path("callback_query");

            if (!message.isMissingNode()) {
                return handleMessage(message);
            }

            if (!callbackQuery.isMissingNode()) {
                return handleCallbackQuery(callbackQuery);
            }

        } catch (Exception e) {
            log.error("Failed to parse Telegram webhook: {}", e.getMessage());
        }

        return Mono.just(ResponseEntity.ok().build());
    }

    private Mono<ResponseEntity<String>> handleMessage(JsonNode message) {
        String text = message.path("text").asText("");
        String chatId = message.path("chat").path("id").asText();
        String username = message.path("from").path("username").asText();

        if (text.startsWith("/start ")) {
            String token = text.substring("/start ".length()).trim();
            log.info("Processing /start with token: {} from chatId: {}", token, chatId);

            return telegramService.connectAthleteByToken(token, chatId, username)
                    .map(athlete -> {
                        if (athlete != null) {
                            return ResponseEntity.ok().build();
                        } else {
                            telegramService.sendMessage(chatId,
                                    "❌ Невірний токен.\nЗвернись до тренера за коректним посиланням.")
                                    .subscribe();
                            return ResponseEntity.ok().build();
                        }
                    });
        } else if (text.equals("/start")) {
            telegramService.sendMessage(chatId,
                    "👋 Привіт!\n\n" +
                    "Щоб підключитись, надішли мені посилання або команду " +
                    "/start [токен], яку дав тренер.\n\n" +
                    "Наприклад: `/start abc123xyz`")
                    .subscribe();
            return Mono.just(ResponseEntity.ok().build());
        } else if (text.startsWith("/")) {
            telegramService.sendMessage(chatId,
                    "Доступні команди:\n" +
                    "/start - Підключити акаунт\n" +
                    "/help - Допомога")
                    .subscribe();
        }

        return Mono.just(ResponseEntity.ok().build());
    }

    private Mono<ResponseEntity<String>> handleCallbackQuery(JsonNode callbackQuery) {
        return Mono.just(ResponseEntity.ok().build());
    }
}
