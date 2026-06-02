package com.cozy.planner.service;

import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.Trainee;
import reactor.core.publisher.Mono;

public interface NotificationService {

    default boolean isEnabled() { return true; }

    Mono<Boolean> sendMessage(String chatId, String text);

    Mono<Boolean> sendMessage(String chatId, String text, Object replyMarkup);

    Mono<Boolean> sendMessageToMentor(String chatId, String text);

    Mono<Boolean> sendMessageToMentor(String chatId, String text, Object replyMarkup);

    Mono<Boolean> sendAvailabilityReminder(Trainee trainee, String baseUrl);

    Mono<Boolean> sendAvailabilityReminder(Trainee trainee, String baseUrl, String customMessage);

    Mono<Boolean> sendAvailabilityReminder(Trainee trainee, String baseUrl, String customMessage, String dayType, String targetDate);

    Mono<Boolean> sendFridayReminder(Trainee trainee, String baseUrl);

    Mono<Boolean> sendWeekendReminder(Trainee trainee, String baseUrl);

    Mono<Boolean> sendMentorTraineeAvailabilityUpdateNotification(Mentor mentor, Trainee trainee);

    Mono<Boolean> sendSessionReminderToTrainee(Trainee trainee, Long sessionId, String sessionTitle, String sessionDate, String sessionTime, String locationName);

    Mono<Boolean> sendSessionReminderToMentor(Mentor mentor, String sessionTitle, String sessionDate, String sessionTime, String locationName, int minutesBefore);
}
