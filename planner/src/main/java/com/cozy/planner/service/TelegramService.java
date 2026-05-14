package com.cozy.planner.service;

import com.cozy.planner.config.TelegramConfig;
import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.TraineeRepository;
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
    private final TraineeRepository traineeRepository;
    private final MentorRepository mentorRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final EventBroadcastService eventBroadcastService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.base-url:}")
    private String appBaseUrl;

    public TelegramService(TelegramConfig config, 
                           TraineeRepository traineeRepository,
                           MentorRepository mentorRepository,
                           ObjectMapper objectMapper,
                           EventBroadcastService eventBroadcastService) {
        this.config = config;
        this.traineeRepository = traineeRepository;
        this.mentorRepository = mentorRepository;
        this.objectMapper = objectMapper;
        this.eventBroadcastService = eventBroadcastService;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.telegram.org")
                .build();
    }

    public boolean isEnabled() {
        boolean mainBotOk = config.isEnabled() && config.getBotToken() != null && !config.getBotToken().isBlank();
        boolean mentorBotOk = config.isEnabled() && isMentorBotEnabled();
        return mainBotOk || mentorBotOk;
    }
    
    public boolean isMentorBotEnabled() {
        return config.isMentorBotEnabled();
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
    
    public Mono<Boolean> sendMessageToMentor(String chatId, String text) {
        return sendMessageToMentor(chatId, text, null);
    }
    
    public Mono<Boolean> sendMessageToMentor(String chatId, String text, Object replyMarkup) {
        if (!isEnabled() || chatId == null || chatId.isBlank()) {
            return Mono.just(false);
        }
        String botToken = isMentorBotEnabled() ? config.getMentorBotToken() : config.getBotToken();
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

    public Mono<Boolean> sendAvailabilityReminder(Trainee trainee, String baseUrl) {
        return sendAvailabilityReminder(trainee, baseUrl, null);
    }

    public Mono<Boolean> sendAvailabilityReminder(Trainee trainee, String baseUrl, String customMessage) {
        if (!trainee.hasTelegram()) {
            return Mono.just(false);
        }

        String link = baseUrl + "/trainee/" + trainee.getInviteToken();
        log.info("Sending availability reminder to trainee: {}, link: {}", trainee.getName(), link);
        
        return mentorRepository.findById(trainee.getMentorId())
                .defaultIfEmpty(Mentor.builder().profile("sport").build())
                .flatMap(mentor -> {
                    String profile = mentor.getProfile() != null ? mentor.getProfile() : "sport";
                    
                    StringBuilder text = new StringBuilder();
                    text.append("👋 <b>").append(escapeHtml(trainee.getName())).append("</b>!\n\n");
                    text.append(ProfileLabels.get(profile, "telegram_reminder_request"));
                    
                    if (customMessage != null && !customMessage.isBlank()) {
                        text.append("💬 <b>").append(escapeHtml(customMessage)).append("</b>\n\n");
                    }
                    
                    text.append(link).append("\n\n");
                    text.append(ProfileLabels.get(profile, "telegram_reminder_instruction"));

                    Map<String, Object> keyboard = createInlineUrlButton(ProfileLabels.get(profile, "telegram_open_calendar"), link);
                    return sendMessage(trainee.getTelegramChatId(), text.toString(), keyboard);
                });
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    public Mono<Boolean> sendFridayReminder(Trainee trainee, String baseUrl) {
        return Mono.just(false);
    }

    public Mono<Boolean> sendWeekendReminder(Trainee trainee, String baseUrl) {
        if (!trainee.hasTelegram()) {
            return Mono.just(false);
        }

        String link = baseUrl + "/trainee/" + trainee.getInviteToken();
        log.info("Sending weekend reminder to trainee: {}, link: {}", trainee.getName(), link);
        
        return mentorRepository.findById(trainee.getMentorId())
                .defaultIfEmpty(Mentor.builder().profile("sport").build())
                .flatMap(mentor -> {
                    String profile = mentor.getProfile() != null ? mentor.getProfile() : "sport";
                    
                    String text = ProfileLabels.get(profile, "telegram_weekend_title") +
                            "👋 Привіт, " + escapeHtml(trainee.getName()) + "!\n\n" +
                            ProfileLabels.get(profile, "telegram_weekend_body") +
                            link + "\n\n" +
                            ProfileLabels.get(profile, "telegram_weekend_instruction");

                    Map<String, Object> keyboard = createInlineUrlButton(ProfileLabels.get(profile, "telegram_open_calendar"), link);
                    return sendMessage(trainee.getTelegramChatId(), text, keyboard);
                });
    }

    public Mono<Boolean> sendMentorTraineeAvailabilityUpdateNotification(Mentor mentor, Trainee trainee) {
        if (!mentor.hasTelegram()) {
            return Mono.just(false);
        }

        String profile = mentor.getProfile() != null ? mentor.getProfile() : "sport";

        StringBuilder text = new StringBuilder();
        text.append(ProfileLabels.get(profile, "telegram_update_title"));
        text.append(ProfileLabels.get(profile, "telegram_update_body"))
            .append(escapeHtml(trainee.getName()))
            .append(ProfileLabels.get(profile, "telegram_update_footer"));
        
        if (appBaseUrl != null && !appBaseUrl.isBlank() 
                && !appBaseUrl.contains("localhost") 
                && !appBaseUrl.contains("127.0.0.1")) {
            String plannerLink = appBaseUrl.endsWith("/") ? appBaseUrl + "planner" : appBaseUrl + "/planner";
            text.append(plannerLink).append("\n\n");
        }
        
        text.append(ProfileLabels.get(profile, "telegram_update_hint"));

        if (appBaseUrl != null && !appBaseUrl.isBlank() 
                && !appBaseUrl.contains("localhost") 
                && !appBaseUrl.contains("127.0.0.1")) {
            String plannerLink = appBaseUrl.endsWith("/") ? appBaseUrl + "planner" : appBaseUrl + "/planner";
            Map<String, Object> keyboard = createInlineUrlButton(ProfileLabels.get(profile, "telegram_open_planner"), plannerLink);
            return sendMessageToMentor(mentor.getTelegramChatId(), text.toString(), keyboard);
        }

        return sendMessageToMentor(mentor.getTelegramChatId(), text.toString());
    }

    public Mono<Trainee> connectTraineeByToken(String token, String chatId, String username) {
        return traineeRepository.findByInviteToken(token)
                .flatMap(trainee -> {
                    trainee.setTelegramChatId(chatId);
                    trainee.setTelegramUsername(username);
                    trainee.setTelegramConnectedAt(LocalDateTime.now());
                    return traineeRepository.save(trainee);
                })
                .flatMap(trainee ->
                    mentorRepository.findById(trainee.getMentorId())
                            .defaultIfEmpty(Mentor.builder().profile("sport").build())
                            .doOnNext(mentor -> {
                                log.info("Trainee {} connected Telegram: chatId={}", trainee.getId(), chatId);
                                eventBroadcastService.broadcast("trainee_changed");
                                String profile = mentor.getProfile() != null ? mentor.getProfile() : "sport";
                                sendWelcomeMessage(chatId, trainee.getName(), profile);
                            })
                            .thenReturn(trainee)
                );
    }

    private void sendWelcomeMessage(String chatId, String traineeName, String profile) {
        String welcome = "✅ <b>Привіт, " + escapeHtml(traineeName) + "!</b>\n\n" +
                ProfileLabels.get(profile, "welcome_trainee_message");

        sendMessage(chatId, welcome).subscribe();
    }

    public String getBotUsername() {
        return config.getBotUsername() != null ? config.getBotUsername() : "";
    }

    public Mono<Mentor> connectMentorByToken(String token, String chatId, String username) {
        return mentorRepository.findByTelegramToken(token)
                .flatMap(mentor -> {
                    mentor.setTelegramChatId(chatId);
                    mentor.setTelegramUsername(username);
                    mentor.setTelegramConnectedAt(LocalDateTime.now());
                    return mentorRepository.save(mentor);
                })
                .doOnNext(m -> {
                    if (m != null) {
                        log.info("Mentor {} connected Telegram: chatId={}", m.getId(), chatId);
                        eventBroadcastService.broadcast("mentor_changed");
                        String profile = m.getProfile() != null ? m.getProfile() : "sport";
                        sendMentorWelcomeMessage(chatId, m.getName(), profile);
                    }
                });
    }

    private void sendMentorWelcomeMessage(String chatId, String mentorName, String profile) {
        String welcome = "✅ <b>Привіт, " + escapeHtml(mentorName) + "!</b>\n\n" +
                ProfileLabels.get(profile, "welcome_mentor_message");

        sendMessageToMentor(chatId, welcome).subscribe();
    }

    public Mono<String> generateMentorTelegramToken(Long mentorId) {
        return mentorRepository.findById(mentorId)
                .flatMap(mentor -> {
                    String existingToken = mentor.getTelegramToken();
                    if (existingToken != null && !existingToken.isBlank() && !mentor.hasTelegram()) {
                        return Mono.just(existingToken);
                    }
                    if (mentor.hasTelegram()) {
                        return Mono.just(existingToken != null ? existingToken : "");
                    }
                    byte[] bytes = new byte[24];
                    secureRandom.nextBytes(bytes);
                    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                    mentor.setTelegramToken(token);
                    return mentorRepository.save(mentor).thenReturn(token);
                });
    }

    public Mono<Boolean> sendSessionReminderToMentor(Mentor mentor, String sessionTitle, String sessionDate, String sessionTime, String locationName, int minutesBefore) {
        if (!mentor.hasTelegram()) {
            return Mono.just(false);
        }

        String profile = mentor.getProfile() != null ? mentor.getProfile() : "sport";

        StringBuilder text = new StringBuilder();
        text.append(ProfileLabels.get(profile, "telegram_session_reminder_title"));
        text.append("<b>").append(escapeHtml(sessionTitle)).append("</b>\n");
        text.append("📅 ").append(sessionDate).append("\n");
        text.append(ProfileLabels.get(profile, "telegram_session_reminder_time"))
            .append(sessionTime)
            .append(ProfileLabels.get(profile, "telegram_session_reminder_minutes"))
            .append(minutesBefore).append(" хвилин)\n");
        
        if (locationName != null && !locationName.isBlank()) {
            text.append("📍 ").append(escapeHtml(locationName)).append("\n");
        }

        text.append("\n");
        text.append(ProfileLabels.get(profile, "telegram_session_reminder_footer"));

        if (appBaseUrl != null && !appBaseUrl.isBlank() 
                && !appBaseUrl.contains("localhost") 
                && !appBaseUrl.contains("127.0.0.1")) {
            String plannerLink = appBaseUrl.endsWith("/") ? appBaseUrl + "planner" : appBaseUrl + "/planner";
            Map<String, Object> keyboard = createInlineUrlButton(ProfileLabels.get(profile, "telegram_open_planner"), plannerLink);
            return sendMessageToMentor(mentor.getTelegramChatId(), text.toString(), keyboard);
        }

        return sendMessageToMentor(mentor.getTelegramChatId(), text.toString());
    }

}
