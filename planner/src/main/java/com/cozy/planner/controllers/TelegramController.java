package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Session;
import com.cozy.planner.repositories.SessionRepository;
import com.cozy.planner.service.ProfileLabels;
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
    private final SessionRepository sessionRepository;

    public TelegramController(TelegramService telegramService, ObjectMapper objectMapper, SessionRepository sessionRepository) {
        this.telegramService = telegramService;
        this.objectMapper = objectMapper;
        this.sessionRepository = sessionRepository;
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
                return handleCallbackQuery(callbackQuery, botType);
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
                                    sendResponse(chatId, ProfileLabels.get("sport", "telegram_invalid_token"), botType);
                                    return Mono.just(ResponseEntity.ok().build());
                                }));
                    }));
        } else if (text.equals("/start")) {
            log.info("Received /start without token from chatId: {}", chatId);
            sendResponse(chatId, ProfileLabels.get("sport", "telegram_connect_prompt"), botType);
            return Mono.just(ResponseEntity.ok().build());
        } else if (text.startsWith("/")) {
            sendResponse(chatId, ProfileLabels.get("sport", "telegram_help"), botType);
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

    private Mono<ResponseEntity<String>> handleCallbackQuery(JsonNode callbackQuery, BotType botType) {
        String data = callbackQuery.path("data").asText("");
        String chatId = callbackQuery.path("message").path("chat").path("id").asText();
        String callbackId = callbackQuery.path("id").asText();

        log.info("Received callback query: data={}, chatId={}", data, chatId);

        if (data.startsWith("confirm_session:")) {
            Long sessionId = parseSessionId(data);
            if (sessionId == null) return Mono.just(ResponseEntity.ok().build());
            return sessionRepository.findById(sessionId)
                    .flatMap(session -> {
                        session.setConfirmationStatus("CONFIRMED");
                        return sessionRepository.save(session);
                    })
                    .flatMap(saved -> {
                        String text = "✅ Сесію підтверджено!";
                        Mono<Boolean> msg;
                        if (botType == BotType.COACH && telegramService.isMentorBotEnabled()) {
                            msg = telegramService.sendMessageToMentor(chatId, text);
                        } else {
                            msg = telegramService.sendMessage(chatId, text);
                        }
                        return msg.thenReturn(saved);
                    })
                    .then(ackCallback(callbackId, botType))
                    .thenReturn(ResponseEntity.ok().build());
        } else if (data.startsWith("reject_session:")) {
            Long sessionId = parseSessionId(data);
            if (sessionId == null) return Mono.just(ResponseEntity.ok().build());
            return sessionRepository.findById(sessionId)
                    .flatMap(session -> {
                        session.setConfirmationStatus("REJECTED");
                        return sessionRepository.save(session);
                    })
                    .flatMap(saved -> {
                        String text = "❌ Сесію відхилено.";
                        Mono<Boolean> msg;
                        if (botType == BotType.COACH && telegramService.isMentorBotEnabled()) {
                            msg = telegramService.sendMessageToMentor(chatId, text);
                        } else {
                            msg = telegramService.sendMessage(chatId, text);
                        }
                        return msg.thenReturn(saved);
                    })
                    .then(ackCallback(callbackId, botType))
                    .thenReturn(ResponseEntity.ok().build());
        } else if (data.equals("trainee_confirm_session")) {
            String text = "✅ Дякую! Сесію підтверджено. Тренер отримає сповіщення.";
            return telegramService.sendMessage(chatId, text)
                    .then(ackCallback(callbackId, botType))
                    .thenReturn(ResponseEntity.ok().build());
        }

        return Mono.just(ResponseEntity.ok().build());
    }

    private Long parseSessionId(String data) {
        try {
            return Long.parseLong(data.substring(data.indexOf(':') + 1));
        } catch (Exception e) {
            log.warn("Failed to parse session id from callback data: {}", data);
            return null;
        }
    }

    private Mono<Void> ackCallback(String callbackId, BotType botType) {
        String botToken;
        if (botType == BotType.COACH && telegramService.isMentorBotEnabled()) {
            botToken = telegramService.getMentorBotToken();
        } else {
            botToken = telegramService.getBotToken();
        }
        return telegramService.answerCallbackQuery(callbackId, botToken);
    }

    private enum BotType {
        ATHLETE, COACH
    }
}
