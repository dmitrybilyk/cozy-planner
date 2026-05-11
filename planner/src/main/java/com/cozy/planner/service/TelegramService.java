package com.cozy.planner.service;

import com.cozy.planner.config.TelegramConfig;
import com.cozy.planner.model.entity.Athlete;
import com.cozy.planner.model.entity.Coach;
import com.cozy.planner.repositories.AthleteRepository;
import com.cozy.planner.repositories.CoachRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.base-url:}")
    private String appBaseUrl;

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

        log.info("Sending Telegram message to chatId: {}, text length: {}", chatId, text.length());
        log.debug("Message text: {}", text);

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "HTML");
        if (replyMarkup != null) {
            body.put("reply_markup", replyMarkup);
        }

        String path = "/bot" + botToken + "/sendMessage";
        log.debug("Using API path: {}", path);

        return webClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> log.debug("Telegram send response: {}", response))
                .map(response -> {
                    try {
                        JsonNode json = objectMapper.readTree(response);
                        boolean ok = json.path("ok").asBoolean(false);
                        if (!ok) {
                            log.warn("Telegram API returned not OK: {}", response);
                        }
                        return ok;
                    } catch (Exception e) {
                        log.error("Failed to parse Telegram response: {}", e.getMessage());
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
        return sendMessageToCoach(chatId, text, null);
    }
    
    public Mono<Boolean> sendMessageToCoach(String chatId, String text, Object replyMarkup) {
        if (!isEnabled() || chatId == null || chatId.isBlank()) {
            return Mono.just(false);
        }
        String botToken = isCoachBotEnabled() ? config.getCoachBotToken() : config.getBotToken();
        return sendMessageWithToken(botToken, chatId, text, replyMarkup);
    }

    private Map<String, Object> createInlineUrlButton(String buttonText, String url) {
        if (url == null || url.contains("localhost") || url.contains("127.0.0.1")) {
            log.warn("Skipping inline keyboard for localhost URL: {}", url);
            return null;
        }
        Map<String, Object> button = new HashMap<>();
        button.put("text", buttonText);
        button.put("url", url);

        Map<String, Object> keyboard = new HashMap<>();
        keyboard.put("inline_keyboard", java.util.Collections.singletonList(
                java.util.Collections.singletonList(button)
        ));
        return keyboard;
    }

    public Mono<Boolean> sendAvailabilityReminder(Athlete athlete, String baseUrl) {
        return sendAvailabilityReminder(athlete, baseUrl, null);
    }

    public Mono<Boolean> sendAvailabilityReminder(Athlete athlete, String baseUrl, String customMessage) {
        if (!athlete.hasTelegram()) {
            return Mono.just(false);
        }

        String link = baseUrl + "/athlete/" + athlete.getInviteToken();
        log.info("Sending availability reminder to athlete: {}, link: {}", athlete.getName(), link);
        
        StringBuilder text = new StringBuilder();
        text.append("👋 <b>").append(escapeHtml(athlete.getName())).append("</b>!\n\n");
        text.append("📋 Тренер просить вказати доступність на найближчі дні.\n\n");
        
        if (customMessage != null && !customMessage.isBlank()) {
            text.append("💬 <b>").append(escapeHtml(customMessage)).append("</b>\n\n");
        }
        
        text.append(link).append("\n\n");
        text.append("<i>Будь ласка, заповни години, коли ти можеш зустрітися.</i>");

        Map<String, Object> keyboard = createInlineUrlButton("📅 Відкрити календар", link);
        return sendMessage(athlete.getTelegramChatId(), text.toString(), keyboard);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    public Mono<Boolean> sendFridayReminder(Athlete athlete, String baseUrl) {
        if (!athlete.hasTelegram()) {
            return Mono.just(false);
        }

        String link = baseUrl + "/athlete/" + athlete.getInviteToken();
        log.info("Sending Friday reminder to athlete: {}, link: {}", athlete.getName(), link);
        
        String text = "📅 <b>Напоминалка на кінець тижня</b>\n\n" +
                "Будь ласка, заповни доступність на наступний тиждень!\n\n" +
                link + "\n\n" +
                "<i>Це допоможе тренеру складати розклад.</i>";

        Map<String, Object> keyboard = createInlineUrlButton("📅 Відкрити календар", link);
        return sendMessage(athlete.getTelegramChatId(), text, keyboard);
    }

    public Mono<Boolean> sendWeekendReminder(Athlete athlete, String baseUrl) {
        if (!athlete.hasTelegram()) {
            return Mono.just(false);
        }

        String link = baseUrl + "/athlete/" + athlete.getInviteToken();
        log.info("Sending weekend reminder to athlete: {}, link: {}", athlete.getName(), link);
        
        String text = "🌴 <b>Запит на доступність у вихідні</b>\n\n" +
                "👋 Привіт, " + escapeHtml(athlete.getName()) + "!\n\n" +
                "Будь ласка, заповни доступність на <b>суботу та неділю</b>!\n\n" +
                link + "\n\n" +
                "<i>Тренеру потрібно знати, коли ти зможеш зайнятися у вихідні.</i>";

        Map<String, Object> keyboard = createInlineUrlButton("📅 Відкрити календар", link);
        return sendMessage(athlete.getTelegramChatId(), text, keyboard);
    }

    public Mono<Boolean> sendCoachAthleteAvailabilityUpdateNotification(Coach coach, Athlete athlete) {
        if (!coach.hasTelegram()) {
            return Mono.just(false);
        }

        StringBuilder text = new StringBuilder();
        text.append("✅ <b>Оновлення доступності</b>\n\n");
        text.append("Учень <b>").append(escapeHtml(athlete.getName())).append("</b> щойно оновив(ла) свою доступність.\n\n");
        
        if (appBaseUrl != null && !appBaseUrl.isBlank() 
                && !appBaseUrl.contains("localhost") 
                && !appBaseUrl.contains("127.0.0.1")) {
            String plannerLink = appBaseUrl.endsWith("/") ? appBaseUrl + "planner" : appBaseUrl + "/planner";
            text.append(plannerLink).append("\n\n");
        }
        
        text.append("<i>Перевірте календар для планування.</i>");

        if (appBaseUrl != null && !appBaseUrl.isBlank() 
                && !appBaseUrl.contains("localhost") 
                && !appBaseUrl.contains("127.0.0.1")) {
            String plannerLink = appBaseUrl.endsWith("/") ? appBaseUrl + "planner" : appBaseUrl + "/planner";
            Map<String, Object> keyboard = createInlineUrlButton("📋 Відкрити планувальник", plannerLink);
            return sendMessageToCoach(coach.getTelegramChatId(), text.toString(), keyboard);
        }

        return sendMessageToCoach(coach.getTelegramChatId(), text.toString());
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
        String welcome = "✅ <b>Привіт, " + escapeHtml(athleteName) + "!</b>\n\n" +
                "Ти успішно підключив Telegram до Cozy Planner.\n\n" +
                "Тепер ти будеш отримувати нагадування про необхідність заповнити доступність.\n\n" +
                "Чекаємо на твої оновлення! ✨";

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
        String welcome = "✅ <b>Привіт, " + escapeHtml(coachName) + "!</b>\n\n" +
                "Ти успішно підключив(ла) Telegram до Cozy Planner.\n\n" +
                "🔔 Тепер ти будеш отримувати сповіщення:\n" +
                "• Коли учень заповнить свою доступність\n" +
                "• Про майбутні зустрічі\n\n" +
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
