package com.cozy.planner.service;

import com.cozy.planner.model.entity.Athlete;
import com.cozy.planner.repositories.AthleteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ScheduledNotificationService {

    private final TelegramService telegramService;
    private final AthleteRepository athleteRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public ScheduledNotificationService(TelegramService telegramService, AthleteRepository athleteRepository) {
        this.telegramService = telegramService;
        this.athleteRepository = athleteRepository;
    }

    @Scheduled(cron = "0 0 17 * * FRI")
    public void sendFridayReminders() {
        if (!telegramService.isEnabled()) {
            log.info("Telegram not enabled, skipping Friday reminders");
            return;
        }

        log.info("Starting Friday 17:00 availability reminders...");

        athleteRepository.findAll()
                .filter(Athlete::hasTelegram)
                .flatMap(athlete -> {
                    log.info("Sending Friday reminder to athlete: {} (chatId: {})", athlete.getName(), athlete.getTelegramChatId());
                    return telegramService.sendFridayReminder(athlete, baseUrl)
                            .doOnNext(success -> {
                                if (success) {
                                    log.info("Reminder sent successfully to {}", athlete.getName());
                                } else {
                                    log.warn("Failed to send reminder to {}", athlete.getName());
                                }
                            });
                })
                .subscribe(
                        success -> {},
                        error -> log.error("Error in Friday reminders: {}", error.getMessage()),
                        () -> log.info("Friday reminders completed")
                );
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
