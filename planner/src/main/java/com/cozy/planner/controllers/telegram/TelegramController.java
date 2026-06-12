package com.cozy.planner.controllers.telegram;

import com.cozy.planner.config.TelegramConfig;
import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.Session;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.SessionRepository;
import com.cozy.planner.repositories.TraineeRepository;
import com.cozy.planner.service.websocket.EventBroadcastService;
import com.cozy.planner.service.ProfileLabels;
import com.cozy.planner.service.notification.TelegramService;
import com.cozy.planner.service.search.SearchEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;



@RestController
@RequestMapping("/api/v1")
@Slf4j
public class TelegramController {

    private final TelegramService telegramService;
    private final ObjectMapper objectMapper;
    private final SessionRepository sessionRepository;
    private final MentorRepository mentorRepository;
    private final TraineeRepository traineeRepository;
    private final TelegramConfig telegramConfig;
    private final EventBroadcastService eventBroadcastService;
    private final SearchEventPublisher searchEventPublisher;

    public TelegramController(TelegramService telegramService, ObjectMapper objectMapper, SessionRepository sessionRepository, MentorRepository mentorRepository, TraineeRepository traineeRepository, TelegramConfig telegramConfig, EventBroadcastService eventBroadcastService, SearchEventPublisher searchEventPublisher) {
        this.telegramService = telegramService;
        this.objectMapper = objectMapper;
        this.sessionRepository = sessionRepository;
        this.mentorRepository = mentorRepository;
        this.traineeRepository = traineeRepository;
        this.telegramConfig = telegramConfig;
        this.eventBroadcastService = eventBroadcastService;
        this.searchEventPublisher = searchEventPublisher;
    }

    @GetMapping("/telegram/config")
    public Mono<Map<String, Object>> getConfig() {
        boolean enabled = telegramConfig.isEnabled()
                && telegramConfig.getBotToken() != null
                && !telegramConfig.getBotToken().isBlank();
        return Mono.just(Map.of("enabled", enabled));
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

    @PostMapping("/telegram/webhook/notification")
    public Mono<ResponseEntity<String>> webhookNotification(@RequestBody String body) {
        if (!telegramService.isEnabled()) {
            return Mono.just(ResponseEntity.ok().build());
        }
        log.debug("Received Notification (trainee) Telegram webhook: {}", body);
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

        Mono<Void> ack = ackCallback(callbackId, botType);

        if (data.startsWith("confirm_session:")) {
            Long sessionId = parseSessionId(data);
            if (sessionId == null) return ack.thenReturn(ResponseEntity.ok().build());
            Mono<Void> logic = sessionRepository.findById(sessionId)
                    .flatMap(session -> sessionRepository.findTraineeIdsBySessionId(session.getId())
                            .collectList()
                            .flatMap(traineeIds -> {
                                session.setConfirmationStatus("CONFIRMED");
                                session.setConfirmedTraineeIds(traineeIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
                                session.setRejectedTraineeIds("");
                                return sessionRepository.save(session)
                                        .flatMap(saved -> searchEventPublisher.publishSessionEvent("UPDATED", saved).thenReturn(saved));
                            }))
                    .doOnSuccess(v -> eventBroadcastService.broadcast("session_changed"))
                    .flatMap(saved -> mentorRepository.findById(saved.getMentorId())
                            .defaultIfEmpty(Mentor.builder().profile("sport").build())
                            .flatMap(mentor -> {
                                String profile = mentor.getProfile() != null ? mentor.getProfile() : "sport";
                                String text = ProfileLabels.get(profile, "telegram_callback_confirmed");
                                Mono<Boolean> msg;
                                if (botType == BotType.COACH && telegramService.isMentorBotEnabled()) {
                                    msg = telegramService.sendMessageToMentor(chatId, text);
                                } else {
                                    msg = telegramService.sendMessage(chatId, text);
                                }
                                return msg.thenReturn(saved);
                            }))
                    .doOnError(e -> log.error("Error processing confirm_session callback: {}", e.getMessage()))
                    .onErrorResume(e -> Mono.empty())
                    .then();
            return ack.then(logic).thenReturn(ResponseEntity.ok().build());
        } else if (data.startsWith("reject_session:")) {
            Long sessionId = parseSessionId(data);
            if (sessionId == null) return ack.thenReturn(ResponseEntity.ok().build());
            Mono<Void> logic = sessionRepository.findById(sessionId)
                    .flatMap(session -> sessionRepository.findTraineeIdsBySessionId(session.getId())
                            .collectList()
                            .flatMap(traineeIds -> {
                                session.setConfirmationStatus("REJECTED");
                                session.setConfirmedTraineeIds("");
                                session.setRejectedTraineeIds(traineeIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
                                return sessionRepository.save(session)
                                        .flatMap(saved -> searchEventPublisher.publishSessionEvent("UPDATED", saved).thenReturn(saved));
                            }))
                    .doOnSuccess(v -> eventBroadcastService.broadcast("session_changed"))
                    .flatMap(saved -> mentorRepository.findById(saved.getMentorId())
                            .defaultIfEmpty(Mentor.builder().profile("sport").build())
                            .flatMap(mentor -> {
                                String profile = mentor.getProfile() != null ? mentor.getProfile() : "sport";
                                String text = ProfileLabels.get(profile, "telegram_callback_rejected");
                                Mono<Boolean> msg;
                                if (botType == BotType.COACH && telegramService.isMentorBotEnabled()) {
                                    msg = telegramService.sendMessageToMentor(chatId, text);
                                } else {
                                    msg = telegramService.sendMessage(chatId, text);
                                }
                                return msg.thenReturn(saved);
                            }))
                    .doOnError(e -> log.error("Error processing reject_session callback: {}", e.getMessage()))
                    .onErrorResume(e -> Mono.empty())
                    .then();
            return ack.then(logic).thenReturn(ResponseEntity.ok().build());
        } else if (data.startsWith("trainee_confirm_session:")) {
            Long sessionId = parseSessionId(data);
            if (sessionId == null) return ack.thenReturn(ResponseEntity.ok().build());
            Mono<Void> logic = traineeRepository.findByTelegramChatId(chatId)
                    .flatMap(trainee -> sessionRepository.findById(sessionId)
                            .flatMap(s -> sessionRepository.findTraineeIdsBySessionId(s.getId())
                                    .collectList()
                                    .flatMap(allTraineeIds -> {
                                        if (!allTraineeIds.contains(trainee.getId())) {
                                            return Mono.just(s);
                                        }
                                        List<Long> confirmed = parseConfirmedIds(s.getConfirmedTraineeIds());
                                        if (!confirmed.contains(trainee.getId())) {
                                            confirmed = new ArrayList<>(confirmed);
                                            confirmed.add(trainee.getId());
                                        }
                                        List<Long> rejected = parseConfirmedIds(s.getRejectedTraineeIds());
                                        rejected = new ArrayList<>(rejected);
                                        rejected.remove(trainee.getId());
                                        s.setConfirmedTraineeIds(confirmed.stream().map(String::valueOf).collect(Collectors.joining(",")));
                                        s.setRejectedTraineeIds(rejected.stream().map(String::valueOf).collect(Collectors.joining(",")));
                                        if (allTraineeIds.stream().allMatch(confirmed::contains)) {
                                            s.setConfirmationStatus("CONFIRMED");
                                        } else {
                                            s.setConfirmationStatus("PENDING");
                                        }
                                        return sessionRepository.save(s)
                                                .flatMap(sv -> searchEventPublisher.publishSessionEvent("UPDATED", sv).thenReturn(sv));
                                    }))
                            .doOnSuccess(v -> eventBroadcastService.broadcast("session_changed"))
                            .flatMap(saved -> {
                                String text = String.format("✅ Дякую, %s! Сесію підтверджено. Тренер отримає сповіщення.", trainee.getName());
                                return telegramService.sendMessage(chatId, text)
                                        .thenReturn(saved);
                            })
                            .flatMap(saved -> mentorRepository.findById(saved.getMentorId())
                                    .defaultIfEmpty(Mentor.builder().profile("sport").build())
                                    .flatMap(mentor -> {
                                        String profile = mentor.getProfile() != null ? mentor.getProfile() : "sport";
                                        String confirmedLabel = "CONFIRMED".equals(saved.getConfirmationStatus())
                                                ? "✅ <b>Підтверджено</b>"
                                                : "⏳ <b>Частково підтверджено</b>";
                                        String mentorText = String.format(
                                                ProfileLabels.get(profile, "telegram_session_decision_trainee"),
                                                confirmedLabel,
                                                trainee.getName() + " " + ProfileLabels.get(profile, "telegram_session_confirmed_action"),
                                                saved.getTitle() != null ? saved.getTitle() : "",
                                                saved.getWorkoutDate().toString(),
                                                saved.getStartTime().toString(),
                                                saved.getEndTime() != null ? saved.getEndTime().toString() : "");
                                        if (mentor.hasTelegram()) {
                                            return telegramService.sendMessageToMentor(mentor.getTelegramChatId(), mentorText);
                                        }
                                        return Mono.just(true);
                                    })))
                    .switchIfEmpty(Mono.defer(() -> {
                        log.warn("No trainee found for chatId: {}", chatId);
                        return Mono.empty();
                    }))
                    .doOnError(e -> log.error("Error processing trainee_confirm_session callback: {}", e.getMessage()))
                    .onErrorResume(e -> Mono.empty())
                    .then();
            return ack.then(logic).thenReturn(ResponseEntity.ok().build());
        } else if (data.startsWith("trainee_reject_session:")) {
            Long sessionId = parseSessionId(data);
            if (sessionId == null) return ack.thenReturn(ResponseEntity.ok().build());
            Mono<Void> logic = traineeRepository.findByTelegramChatId(chatId)
                    .flatMap(trainee -> sessionRepository.findById(sessionId)
                            .flatMap(s -> sessionRepository.findTraineeIdsBySessionId(s.getId())
                                    .collectList()
                                    .flatMap(allTraineeIds -> {
                                        if (!allTraineeIds.contains(trainee.getId())) {
                                            return Mono.just(s);
                                        }
                                        List<Long> confirmed = parseConfirmedIds(s.getConfirmedTraineeIds());
                                        confirmed = new ArrayList<>(confirmed);
                                        confirmed.remove(trainee.getId());
                                        List<Long> rejected = parseConfirmedIds(s.getRejectedTraineeIds());
                                        if (!rejected.contains(trainee.getId())) {
                                            rejected = new ArrayList<>(rejected);
                                            rejected.add(trainee.getId());
                                        }
                                        s.setConfirmedTraineeIds(confirmed.stream().map(String::valueOf).collect(Collectors.joining(",")));
                                        s.setRejectedTraineeIds(rejected.stream().map(String::valueOf).collect(Collectors.joining(",")));
                                        return sessionRepository.save(s)
                                                .flatMap(sv -> searchEventPublisher.publishSessionEvent("UPDATED", sv).thenReturn(sv));
                                    }))
                            .doOnSuccess(v -> eventBroadcastService.broadcast("session_changed"))
                            .flatMap(saved -> {
                                String text = "❌ Сесію відхилено. Тренер отримає сповіщення.";
                                return telegramService.sendMessage(chatId, text)
                                        .thenReturn(saved);
                            })
                            .flatMap(saved -> mentorRepository.findById(saved.getMentorId())
                                    .defaultIfEmpty(Mentor.builder().profile("sport").build())
                                    .flatMap(mentor -> {
                                        String profile = mentor.getProfile() != null ? mentor.getProfile() : "sport";
                                        int respondedCount = parseConfirmedIds(saved.getConfirmedTraineeIds()).size() + parseConfirmedIds(saved.getRejectedTraineeIds()).size();
                                        String mentorText = String.format(
                                                "❌ %s відхилив сесію \"%s\" (%d)",
                                                trainee.getName(),
                                                saved.getTitle() != null ? saved.getTitle() : "",
                                                respondedCount);
                                        if (botType == BotType.COACH && telegramService.isMentorBotEnabled()) {
                                            return telegramService.sendMessageToMentor(chatId, mentorText);
                                        }
                                        if (mentor.hasTelegram()) {
                                            return telegramService.sendMessageToMentor(mentor.getTelegramChatId(), mentorText);
                                        }
                                        return Mono.just(true);
                                    })))
                    .switchIfEmpty(Mono.defer(() -> {
                        log.warn("No trainee found for chatId: {}", chatId);
                        return Mono.empty();
                    }))
                    .doOnError(e -> log.error("Error processing trainee_reject_session callback: {}", e.getMessage()))
                    .onErrorResume(e -> Mono.empty())
                    .then();
            return ack.then(logic).thenReturn(ResponseEntity.ok().build());
        }

        return ack.thenReturn(ResponseEntity.ok().build());
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

    private List<Long> parseConfirmedIds(String ids) {
        if (ids == null || ids.isBlank()) return List.of();
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
    }

    private enum BotType {
        ATHLETE, COACH
    }
}
