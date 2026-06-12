package com.cozy.planner.service.notification;

import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.service.availability.KafkaAvailabilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Primary
@Service
public class ResilienceNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(ResilienceNotificationService.class);

    private final KafkaNotificationService kafka;
    private final TelegramService telegram;
    private final KafkaAvailabilityService kafkaAvailability;

    public ResilienceNotificationService(KafkaNotificationService kafka,
                                          TelegramService telegram,
                                          KafkaAvailabilityService kafkaAvailability) {
        this.kafka = kafka;
        this.telegram = telegram;
        this.kafkaAvailability = kafkaAvailability;
    }

    private <T> Mono<T> route(Mono<T> kafkaMono, Mono<T> telegramMono) {
        if (!kafkaAvailability.isAvailable()) {
            log.info("Routing notification → Telegram (Kafka unavailable)");
            return telegramMono;
        }
        log.info("Routing notification → Kafka");
        return kafkaMono
                .doOnError(e -> kafkaAvailability.markUnavailable())
                .onErrorResume(e -> {
                    log.warn("Kafka send failed, falling back to Telegram: {}", e.getMessage());
                    return telegramMono;
                });
    }

    @Override
    public Mono<Boolean> sendMessage(String chatId, String text) {
        return route(kafka.sendMessage(chatId, text), telegram.sendMessage(chatId, text));
    }

    @Override
    public Mono<Boolean> sendMessage(String chatId, String text, Object replyMarkup) {
        return route(kafka.sendMessage(chatId, text, replyMarkup), telegram.sendMessage(chatId, text, replyMarkup));
    }

    @Override
    public Mono<Boolean> sendMessageToMentor(String chatId, String text) {
        return route(kafka.sendMessageToMentor(chatId, text), telegram.sendMessageToMentor(chatId, text));
    }

    @Override
    public Mono<Boolean> sendMessageToMentor(String chatId, String text, Object replyMarkup) {
        return route(kafka.sendMessageToMentor(chatId, text, replyMarkup), telegram.sendMessageToMentor(chatId, text, replyMarkup));
    }

    @Override
    public Mono<Boolean> sendAvailabilityReminder(Trainee trainee, String baseUrl) {
        return route(kafka.sendAvailabilityReminder(trainee, baseUrl), telegram.sendAvailabilityReminder(trainee, baseUrl));
    }

    @Override
    public Mono<Boolean> sendAvailabilityReminder(Trainee trainee, String baseUrl, String customMessage) {
        return route(kafka.sendAvailabilityReminder(trainee, baseUrl, customMessage), telegram.sendAvailabilityReminder(trainee, baseUrl, customMessage));
    }

    @Override
    public Mono<Boolean> sendAvailabilityReminder(Trainee trainee, String baseUrl, String customMessage, String dayType, String targetDate) {
        return route(
                kafka.sendAvailabilityReminder(trainee, baseUrl, customMessage, dayType, targetDate),
                telegram.sendAvailabilityReminder(trainee, baseUrl, customMessage, dayType, targetDate)
        );
    }

    @Override
    public Mono<Boolean> sendWeekendReminder(Trainee trainee, String baseUrl) {
        return route(kafka.sendWeekendReminder(trainee, baseUrl), telegram.sendWeekendReminder(trainee, baseUrl));
    }

    @Override
    public Mono<Boolean> sendSessionReminderToTrainee(Trainee trainee, Long sessionId, String sessionTitle, String sessionDate, String sessionTime, String locationName) {
        return route(
                kafka.sendSessionReminderToTrainee(trainee, sessionId, sessionTitle, sessionDate, sessionTime, locationName),
                telegram.sendSessionReminderToTrainee(trainee, sessionId, sessionTitle, sessionDate, sessionTime, locationName)
        );
    }

    @Override
    public Mono<Boolean> sendSessionReminderToMentor(Mentor mentor, String sessionTitle, String sessionDate, String sessionTime, String locationName, int minutesBefore) {
        return route(
                kafka.sendSessionReminderToMentor(mentor, sessionTitle, sessionDate, sessionTime, locationName, minutesBefore),
                telegram.sendSessionReminderToMentor(mentor, sessionTitle, sessionDate, sessionTime, locationName, minutesBefore)
        );
    }
}
