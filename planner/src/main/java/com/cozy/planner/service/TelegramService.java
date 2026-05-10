package com.cozy.planner.service;

import com.cozy.planner.config.TelegramConfig;
import com.cozy.planner.model.entity.Athlete;
import com.cozy.planner.repositories.AthleteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class TelegramService {

    private final TelegramConfig config;
    private final AthleteRepository athleteRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public TelegramService(TelegramConfig config, AthleteRepository athleteRepository, ObjectMapper objectMapper) {
        this.config = config;
        this.athleteRepository = athleteRepository;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.telegram.org")
                .build();
    }

    public boolean isEnabled() {
        return config.isEnabled() && config.getBotToken() != null && !config.getBotToken().isBlank();
    }

    public Mono<Boolean> sendMessage(String chatId, String text) {
        return sendMessage(chatId, text, null);
    }

    public Mono<Boolean> sendMessage(String chatId, String text, Object replyMarkup) {
        if (!isEnabled() || chatId == null || chatId.isBlank()) {
            return Mono.just(false);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "Markdown");
        if (replyMarkup != null) {
            body.put("reply_markup", replyMarkup);
        }

        return webClient.post()
                .uri("/bot{token}/sendMessage", config.getBotToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> log.debug("Telegram send response: {}", response))
                .map(response -> {
                    try {
                        JsonNode json = objectMapper.readTree(response);
                        return json.path("ok").asBoolean(false);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .doOnError(e -> log.error("Failed to send Telegram message: {}", e.getMessage()))
                .onErrorResume(e -> Mono.just(false));
    }

    public Mono<Boolean> sendAvailabilityReminder(Athlete athlete, String baseUrl) {
        if (!athlete.hasTelegram()) {
            return Mono.just(false);
        }

        String link = baseUrl + "/athlete/" + athlete.getInviteToken();
        String text = "👋 *" + athlete.getName() + "*!\n\n" +
                "Тренер просить вказати доступність на найближчі дні.\n\n" +
                "[Відкрити календар →](" + link + ")\n\n" +
                "_Потрібно вказати години, коли ти можеш тренуватися._";

        return sendMessage(athlete.getTelegramChatId(), text);
    }

    public Mono<Boolean> sendFridayReminder(Athlete athlete, String baseUrl) {
        if (!athlete.hasTelegram()) {
            return Mono.just(false);
        }

        String link = baseUrl + "/athlete/" + athlete.getInviteToken();
        String text = "📅 *Напоминалка на кінець тижня*\n\n" +
                "Будь ласка, заповни доступність на наступний тиждень!\n\n" +
                "[Відкрити календар →](" + link + ")\n\n" +
                "_Це допоможе тренеру складати розклад._";

        return sendMessage(athlete.getTelegramChatId(), text);
    }

    public Mono<Athlete> connectAthleteByToken(String token, String chatId, String username) {
        return athleteRepository.findByInviteToken(token)
                .flatMap(athlete -> {
                    athlete.setTelegramChatId(chatId);
                    athlete.setTelegramUsername(username);
                    athlete.setTelegramConnectedAt(LocalDateTime.now());
                    return athleteRepository.save(athlete);
                })
                .doOnSuccess(a -> {
                    if (a != null) {
                        log.info("Athlete {} connected Telegram: chatId={}", a.getId(), chatId);
                        sendWelcomeMessage(chatId, a.getName());
                    }
                });
    }

    private void sendWelcomeMessage(String chatId, String athleteName) {
        String welcome = "✅ *Привіт, " + athleteName + "!*\n\n" +
                "Ти успішно підключив Telegram до Cozy Planner.\n\n" +
                "Тепер ти будеш отримувати нагадування про необхідність заповнити доступність для тренувань.\n\n" +
                "Хороших тренувань! 🏋️";

        sendMessage(chatId, welcome).subscribe();
    }

    public String getBotUsername() {
        return config.getBotUsername() != null ? config.getBotUsername() : "";
    }
}
