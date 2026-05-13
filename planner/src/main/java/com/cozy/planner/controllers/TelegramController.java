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
        return webhookTrainee(body);
    }

    @PostMapping("/telegram/webhook/trainee")
    public Mono<ResponseEntity<String>> webhookTrainee(@RequestBody String body) {
        if (!telegramService.isEnabled()) {
            return Mono.just(ResponseEntity.ok().build());
        }
        log.debug("Received Trainee Telegram webhook: {}", body);
        return processWebhook(body, BotType.ATHLETE);
    }

    @PostMapping("/telegram/webhook/mentor")
    public Mono<ResponseEntity<String>> webhookMentor(@RequestBody String body) {
        if (!telegramService.isEnabled()) {
            return Mono.just(ResponseEntity.ok().build());
        }
        log.debug("Received Mentor Telegram webhook: {}", body);
        return processWebhook(body, BotType.COACH);
    }

    private Mono<ResponseEntity<String>> processWebhook(String body, BotType botType) {
        try {
            JsonNode json = objectMapper.readTree(body);
            JsonNode message = json.path("message");
            JsonNode callbackQuery = json.path("callback_query");

            if (!message.isMissingNode()) {
                return handleMessage(message, botType);
            }

            if (!callbackQuery.isMissingNode()) {
                return handleCallbackQuery(callbackQuery);
            }

        } catch (Exception e) {
            log.error("Failed to parse Telegram webhook: {}", e.getMessage());
        }

        return Mono.just(ResponseEntity.ok().build());
    }

    private Mono<ResponseEntity<String>> handleMessage(JsonNode message, BotType botType) {
        String text = message.path("text").asText("");
        String chatId = message.path("chat").path("id").asText();
        String username = message.path("from").path("username").asText();

        log.info("Received message: '{}' from chatId: {} via {} bot", text, chatId, botType);

        if (text.startsWith("/start ")) {
            String token = text.substring("/start ".length()).trim();
            log.info("Processing /start with token: {}", token);

            return telegramService.connectTraineeByToken(token, chatId, username)
                    .<ResponseEntity<String>>map(trainee -> {
                        log.info("Trainee connected successfully with token: {}", token);
                        return ResponseEntity.ok().build();
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        log.info("Trainee not found with token: {}, trying mentor...", token);
                        return telegramService.connectMentorByToken(token, chatId, username)
                                .<ResponseEntity<String>>map(mentor -> {
                                    log.info("Mentor connected successfully with token: {}", token);
                                    return ResponseEntity.ok().build();
                                })
                                .switchIfEmpty(Mono.defer(() -> {
                                    log.warn("Neither trainee nor mentor found with token: {}", token);
                                    sendResponse(chatId, "❌ Невірний токен.\nЗвернись для уточнення.", botType);
                                    return Mono.just(ResponseEntity.ok().build());
                                }));
                    }));
        } else if (text.equals("/start")) {
            log.info("Received /start without token from chatId: {}", chatId);
            sendResponse(chatId,
                    "👋 Привіт!\n\n" +
                    "Щоб підключитись, надішли мені посилання або команду " +
                    "/start [токен], яку дав тренер.\n\n" +
                    "Наприклад: `/start abc123xyz`",
                    botType);
            return Mono.just(ResponseEntity.ok().build());
        } else if (text.startsWith("/")) {
            sendResponse(chatId,
                    "Доступні команди:\n" +
                    "/start - Підключити акаунт\n" +
                    "/help - Допомога",
                    botType);
        }

        return Mono.just(ResponseEntity.ok().build());
    }

    private void sendResponse(String chatId, String text, BotType botType) {
        if (botType == BotType.COACH && telegramService.isMentorBotEnabled()) {
            telegramService.sendMessageToMentor(chatId, text).subscribe();
        } else {
            telegramService.sendMessage(chatId, text).subscribe();
        }
    }

    private Mono<ResponseEntity<String>> handleCallbackQuery(JsonNode callbackQuery) {
        return Mono.just(ResponseEntity.ok().build());
    }

    private enum BotType {
        ATHLETE, COACH
    }
}
