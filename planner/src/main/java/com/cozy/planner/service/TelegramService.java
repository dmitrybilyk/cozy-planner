package com.cozy.planner.service;

import com.cozy.planner.config.TelegramConfig;
import com.cozy.planner.model.entity.Athlete;
import com.cozy.planner.model.entity.Coach;
import com.cozy.planner.repositories.AthleteRepository;
import com.cozy.planner.repositories.CoachRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class TelegramService {

    private final TelegramConfig config;
    private final AthleteRepository athleteRepository;
    private final CoachRepository coachRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public TelegramService(TelegramConfig config, 
                           AthleteRepository athleteRepository,
                           CoachRepository coachRepository,
                           ObjectMapper objectMapper) {
        this.config = config;
        this.athleteRepository = athleteRepository;
        this.coachRepository = coachRepository;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.telegram.org")
                .build();
    }

    public boolean isEnabled() {
        boolean mainBotOk = config.isEnabled() && config.getBotToken() != null && !config.getBotToken().isBlank();
        boolean coachBotOk = config.isEnabled() && isCoachBotEnabled();
        return mainBotOk || coachBotOk;
    }
    
    public boolean isCoachBotEnabled() {
        return config.isCoachBotEnabled();
    }

    private Mono<Boolean> sendMessageWithToken(String botToken, String chatId, String text, Object replyMarkup) {
        if (botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {
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
                .uri("/bot{token}/sendMessage", botToken)
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

    public Mono<Boolean> sendMessage(String chatId, String text) {
        return sendMessage(chatId, text, null);
    }

    public Mono<Boolean> sendMessage(String chatId, String text, Object replyMarkup) {
        if (!isEnabled() || chatId == null || chatId.isBlank()) {
            return Mono.just(false);
        }
        return sendMessageWithToken(config.getBotToken(), chatId, text, replyMarkup);
    }
    
    public Mono<Boolean> sendMessageToCoach(String chatId, String text) {
        if (!isEnabled() || chatId == null || chatId.isBlank()) {
            return Mono.just(false);
        }
        String botToken = isCoachBotEnabled() ? config.getCoachBotToken() : config.getBotToken();
        return sendMessageWithToken(botToken, chatId, text, null);
    }

    public Mono<Boolean> sendAvailabilityReminder(Athlete athlete, String baseUrl) {
        return sendAvailabilityReminder(athlete, baseUrl, null);
    }

    public Mono<Boolean> sendAvailabilityReminder(Athlete athlete, String baseUrl, String customMessage) {
        if (!athlete.hasTelegram()) {
            return Mono.just(false);
        }

        String link = baseUrl + "/athlete/" + athlete.getInviteToken();
        
        StringBuilder text = new StringBuilder();
        text.append("👋 *").append(athlete.getName()).append("*!\n\n");
        text.append("📋 Тренер просить вказати доступність на найближчі дні.\n\n");
        
        if (customMessage != null && !customMessage.isBlank()) {
            text.append("💬 *").append(customMessage).append("*\n\n");
        }
        
        text.append("[Відкрити календар →](").append(link).append(")\n\n");
        text.append("_Будь ласка, заповни години, коли ти можеш зайнятися._");

        return sendMessage(athlete.getTelegramChatId(), text.toString());
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

    public Mono<Boolean> sendWeekendReminder(Athlete athlete, String baseUrl) {
        if (!athlete.hasTelegram()) {
            return Mono.just(false);
        }

        String link = baseUrl + "/athlete/" + athlete.getInviteToken();
        String text = "🌴 *Запит на доступність у вихідні*\n\n" +
                "👋 Привіт, " + athlete.getName() + "!\n\n" +
                "Будь ласка, заповни доступність на **суботу та неділю**!\n\n" +
                "[Відкрити календар →](" + link + ")\n\n" +
                "_Тренеру потрібно знати, коли ти зможеш зайнятися у вихідні._";

        return sendMessage(athlete.getTelegramChatId(), text);
    }

    public Mono<Boolean> sendCoachAthleteAvailabilityUpdateNotification(Coach coach, Athlete athlete) {
        if (!coach.hasTelegram()) {
            return Mono.just(false);
        }

        String text = "✅ *Оновлення доступності*\n\n" +
                "Учень *" + athlete.getName() + "* щойно оновив(ла) свою доступність.\n\n" +
                "_Перевірте календар для планування занять._";

        return sendMessageToCoach(coach.getTelegramChatId(), text);
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

    public Mono<Coach> connectCoachByToken(String token, String chatId, String username) {
        return coachRepository.findByTelegramToken(token)
                .flatMap(coach -> {
                    coach.setTelegramChatId(chatId);
                    coach.setTelegramUsername(username);
                    coach.setTelegramConnectedAt(LocalDateTime.now());
                    return coachRepository.save(coach);
                })
                .doOnSuccess(c -> {
                    if (c != null) {
                        log.info("Coach {} connected Telegram: chatId={}", c.getId(), chatId);
                        sendCoachWelcomeMessage(chatId, c.getName());
                    }
                });
    }

    private void sendCoachWelcomeMessage(String chatId, String coachName) {
        String welcome = "✅ *Привіт, " + coachName + "!*\n\n" +
                "Ти успішно підключив(ла) Telegram до Cozy Planner.\n\n" +
                "🔔 Тепер ти будеш отримувати сповіщення:\n" +
                "• Коли учень заповнить свою доступність\n" +
                "• Про майбутні заняття\n\n" +
                "Гарного дня!";

        sendMessageToCoach(chatId, welcome).subscribe();
    }

    public Mono<String> generateCoachTelegramToken(Long coachId) {
        return coachRepository.findById(coachId)
                .flatMap(coach -> {
                    String existingToken = coach.getTelegramToken();
                    if (existingToken != null && !existingToken.isBlank() && !coach.hasTelegram()) {
                        return Mono.just(existingToken);
                    }
                    if (coach.hasTelegram()) {
                        return Mono.just(existingToken != null ? existingToken : "");
                    }
                    byte[] bytes = new byte[24];
                    secureRandom.nextBytes(bytes);
                    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                    coach.setTelegramToken(token);
                    return coachRepository.save(coach).thenReturn(token);
                });
    }
}
