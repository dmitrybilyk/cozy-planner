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

    @Scheduled(cron = "${app.scheduler.weekly-reminder-cron:0 0 17 * * FRI}")
    public void sendFridayReminders() {
        if (!telegramService.isEnabled()) {
            log.info("Telegram not enabled, skipping Friday reminders");
            return;
        }

        log.info("Starting Friday weekly availability reminders...");

        athleteRepository.findAll()
                .filter(Athlete::hasTelegram)
                .flatMap(athlete -> {
                    log.info("Sending weekly reminder to: {} (chatId: {})", athlete.getName(), athlete.getTelegramChatId());
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
                        () -> log.info("Friday weekly reminders completed")
                );
    }

    @Scheduled(cron = "${app.scheduler.weekend-reminder-cron:0 0 16 * * FRI}")
    public void sendWeekendReminders() {
        if (!telegramService.isEnabled()) {
            log.info("Telegram not enabled, skipping weekend reminders");
            return;
        }

        log.info("Starting weekend availability reminders...");

        athleteRepository.findAllAthletesWithWeekendRemindersEnabled()
                .flatMap(athlete -> {
                    log.info("Sending weekend reminder to: {} (chatId: {})", athlete.getName(), athlete.getTelegramChatId());
                    return telegramService.sendWeekendReminder(athlete, baseUrl)
                            .doOnNext(success -> {
                                if (success) {
                                    log.info("Weekend reminder sent to {}", athlete.getName());
                                } else {
                                    log.warn("Failed to send weekend reminder to {}", athlete.getName());
                                }
                            });
                })
                .subscribe(
                        success -> {},
                        error -> log.error("Error in weekend reminders: {}", error.getMessage()),
                        () -> log.info("Weekend reminders completed")
                );
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
