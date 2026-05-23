package com.cozy.planner.service;

import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.Trainee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@ConditionalOnProperty(name = "app.notification-service", havingValue = "kafka", matchIfMissing = true)
@Primary
public class KafkaNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(KafkaNotificationService.class);

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    @Value("${app.notification-topic:notification-events}")
    private String topic;

    public KafkaNotificationService(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public Mono<Boolean> sendMessage(String chatId, String text) {
        return sendMessage(chatId, text, null);
    }

    @Override
    public Mono<Boolean> sendMessage(String chatId, String text, Object replyMarkup) {
        return publish("send", chatId, text, replyMarkup);
    }

    @Override
    public Mono<Boolean> sendMessageToMentor(String chatId, String text) {
        return sendMessageToMentor(chatId, text, null);
    }

    @Override
    public Mono<Boolean> sendMessageToMentor(String chatId, String text, Object replyMarkup) {
        return publish("send-mentor", chatId, text, replyMarkup);
    }

    @Override
    public Mono<Boolean> sendAvailabilityReminder(Trainee trainee, String baseUrl) {
        return sendAvailabilityReminder(trainee, baseUrl, null, null, null);
    }

    @Override
    public Mono<Boolean> sendAvailabilityReminder(Trainee trainee, String baseUrl, String customMessage) {
        return sendAvailabilityReminder(trainee, baseUrl, customMessage, null, null);
    }

    @Override
    public Mono<Boolean> sendAvailabilityReminder(Trainee trainee, String baseUrl, String customMessage, String dayType, String targetDate) {
        if (!trainee.hasTelegram()) return Mono.just(false);
        String text = formatAvailabilityText(trainee, baseUrl, dayType, targetDate);
        String link = buildLink(trainee, baseUrl, targetDate);
        Map<String, Object> keyboard = createInlineUrlButton("📅 Відкрити календар", link);
        return publish("send", trainee.getTelegramChatId(), text, keyboard);
    }

    @Override
    public Mono<Boolean> sendFridayReminder(Trainee trainee, String baseUrl) {
        return Mono.just(false);
    }

    @Override
    public Mono<Boolean> sendWeekendReminder(Trainee trainee, String baseUrl) {
        if (!trainee.hasTelegram()) return Mono.just(false);
        String link = baseUrl + "/trainee/" + trainee.getInviteToken();
        String text = "📅 <b>Запит на вихідні</b>\n\n" +
                "👋 Привіт, " + escapeHtml(trainee.getName()) + "!\n\n" +
                "Будь ласка, обери час для тренування на цих вихідних, натиснувши на посилання нижче.\n\n" +
                link + "\n\n" +
                "Якщо посилання не відкривається, скопіюй його в браузер.";
        Map<String, Object> keyboard = !link.contains("localhost") && !link.contains("127.0.0.1")
                ? createInlineUrlButton("📅 Відкрити календар", link) : null;
        return publish("send", trainee.getTelegramChatId(), text, keyboard);
    }

    @Override
    public Mono<Boolean> sendMentorTraineeAvailabilityUpdateNotification(Mentor mentor, Trainee trainee) {
        if (!mentor.hasTelegram()) return Mono.just(false);
        String text = "📋 <b>Оновлення доступності</b>\n\n" +
                "Тренер отримав оновлення:\n" +
                "👤 " + escapeHtml(trainee.getName()) + "\n" +
                "переглянь розклад у панелі керування.";
        return publish("send-mentor", mentor.getTelegramChatId(), text, null);
    }

    @Override
    public Mono<Boolean> sendSessionReminderToTrainee(Trainee trainee, String sessionTitle, String sessionDate, String sessionTime, String locationName) {
        if (!trainee.hasTelegram()) return Mono.just(false);
        StringBuilder text = new StringBuilder();
        text.append("⏰ <b>Нагадування про сесію</b>\n\n");
        text.append("<b>").append(escapeHtml(sessionTitle)).append("</b>\n");
        text.append("📅 ").append(sessionDate).append("\n");
        text.append("🕐 ").append(sessionTime).append("\n");
        if (locationName != null && !locationName.isBlank()) {
            text.append("📍 ").append(escapeHtml(locationName)).append("\n");
        }
        text.append("\n<i>Сесія розпочнеться за 1 годину. Будь ласка, не спізнюйтесь!</i>");
        return publish("send", trainee.getTelegramChatId(), text.toString(), null);
    }

    @Override
    public Mono<Boolean> sendSessionReminderToMentor(Mentor mentor, String sessionTitle, String sessionDate, String sessionTime, String locationName, int minutesBefore) {
        if (!mentor.hasTelegram()) return Mono.just(false);
        StringBuilder text = new StringBuilder();
        text.append("⏰ <b>Нагадування про сесію</b>\n\n");
        text.append("<b>").append(escapeHtml(sessionTitle)).append("</b>\n");
        text.append("📅 ").append(sessionDate).append("\n");
        text.append("🕐 ").append(sessionTime).append("\n");
        if (locationName != null && !locationName.isBlank()) {
            text.append("📍 ").append(escapeHtml(locationName)).append("\n");
        }
        text.append("\n<i>Сесія розпочнеться за ").append(minutesBefore).append(" хвилин.</i>");
        return publish("send-mentor", mentor.getTelegramChatId(), text.toString(), null);
    }

    private Mono<Boolean> publish(String type, String chatId, String text, Object replyMarkup) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("chatId", chatId);
        event.put("text", text);
        event.put("parseMode", "HTML");
        if (replyMarkup != null) {
            event.put("replyMarkup", replyMarkup);
        }
        log.info("Publishing {} event to topic {} for chatId={}", type, topic, chatId);
        return Mono.fromFuture(kafkaTemplate.send(topic, chatId, event))
                .map(result -> {
                    log.debug("Published to kafka: partition={}, offset={}", result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    return true;
                })
                .doOnError(e -> log.error("Failed to publish to Kafka: {}", e.getMessage()))
                .onErrorResume(e -> Mono.just(false));
    }

    private String formatAvailabilityText(Trainee trainee, String baseUrl, String dayType, String targetDate) {
        StringBuilder text = new StringBuilder();
        text.append("👋 <b>").append(escapeHtml(trainee.getName())).append("</b>!\n\n");
        if ("tomorrow".equals(dayType)) {
            text.append("📅 <b>Запит на завтра");
            if (targetDate != null) {
                text.append(" (").append(LocalDate.parse(targetDate).format(DateTimeFormatter.ofPattern("dd.MM (EEE)", Locale.forLanguageTag("uk")))).append(")");
            }
            text.append("</b>\n\n");
        } else if ("weekend".equals(dayType)) {
            text.append("📅 <b>Запит на вихідні</b>\n\n");
        } else if ("specific_day".equals(dayType) && targetDate != null) {
            text.append("📅 <b>Запитана дата: ").append(LocalDate.parse(targetDate).format(DateTimeFormatter.ofPattern("dd.MM (EEE)", Locale.forLanguageTag("uk")))).append("</b>\n\n");
        }
        text.append("Будь ласка, обери час для тренування, натиснувши на посилання нижче.\n\n");
        text.append(buildLink(trainee, baseUrl, targetDate)).append("\n\n");
        text.append("Якщо посилання не відкривається, скопіюй його в браузер.");
        return text.toString();
    }

    private String buildLink(Trainee trainee, String baseUrl, String targetDate) {
        String link = baseUrl.endsWith("/") ? baseUrl + trainee.getInviteToken() : baseUrl + "/trainee/" + trainee.getInviteToken();
        if (targetDate != null && !targetDate.isBlank()) link += "?date=" + targetDate;
        return link;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private Map<String, Object> createInlineUrlButton(String buttonText, String url) {
        if (url == null || url.contains("localhost") || url.contains("127.0.0.1")) return null;
        Map<String, Object> button = new HashMap<>();
        button.put("text", buttonText);
        button.put("url", url);
        Map<String, Object> keyboard = new HashMap<>();
        keyboard.put("inline_keyboard", Collections.singletonList(Collections.singletonList(button)));
        return keyboard;
    }
}
