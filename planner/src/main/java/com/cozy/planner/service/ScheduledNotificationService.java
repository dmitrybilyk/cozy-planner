package com.cozy.planner.service;

import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.model.entity.Session;
import com.cozy.planner.repositories.LocationRepository;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.TraineeRepository;
import com.cozy.planner.repositories.SessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class ScheduledNotificationService {

    private final TelegramService telegramService;
    private final SessionRepository sessionRepository;
    private final MentorRepository mentorRepository;
    private final LocationRepository locationRepository;
    private final TraineeRepository traineeRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public ScheduledNotificationService(TelegramService telegramService, 
                                         SessionRepository sessionRepository,
                                         MentorRepository mentorRepository,
                                         LocationRepository locationRepository,
                                         TraineeRepository traineeRepository) {
        this.telegramService = telegramService;
        this.sessionRepository = sessionRepository;
        this.mentorRepository = mentorRepository;
        this.locationRepository = locationRepository;
        this.traineeRepository = traineeRepository;
    }

    @Scheduled(cron = "${app.scheduler.weekend-reminder-cron:0 0 16 * * FRI}")
    public void sendWeekendReminders() {
        if (!telegramService.isEnabled()) {
            log.info("Telegram not enabled, skipping weekend reminders");
            return;
        }

        log.info("Starting weekend availability reminders...");

        traineeRepository.findAllTraineesWithWeekendRemindersEnabled()
                .flatMap(trainee -> {
                    log.info("Sending weekend reminder to: {} (chatId: {})", trainee.getName(), trainee.getTelegramChatId());
                    return telegramService.sendWeekendReminder(trainee, baseUrl)
                            .doOnNext(success -> {
                                if (success) {
                                    log.info("Weekend reminder sent to {}", trainee.getName());
                                } else {
                                    log.warn("Failed to send weekend reminder to {}", trainee.getName());
                                }
                            });
                })
                .subscribe(
                        success -> {},
                        error -> log.error("Error in weekend reminders: {}", error.getMessage()),
                        () -> log.info("Weekend reminders completed")
                );
    }

    @Scheduled(cron = "${app.scheduler.session-reminder-cron:0 */5 * * * *}")
    public void sendSessionReminders() {
        if (!telegramService.isEnabled()) {
            log.info("Telegram not enabled, skipping session reminders");
            return;
        }

        log.info("Checking for upcoming sessions to remind...");

        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        sessionRepository.findUpcomingWithoutReminder(today, tomorrow)
                .flatMap(session -> mentorRepository.findById(session.getMentorId())
                        .flatMap(mentor -> {
                            if (!mentor.isSessionReminderEnabled() || !mentor.hasTelegram()) {
                                return reactor.core.publisher.Mono.empty();
                            }

                            ZoneId mentorZone = ZoneId.of(mentor.getTimezone() != null ? mentor.getTimezone() : "Europe/Kiev");
                            LocalDateTime now = LocalDateTime.now(mentorZone);
                            LocalDateTime sessionStart = LocalDateTime.of(session.getWorkoutDate(), session.getStartTime());

                            long minutesUntilSession = ChronoUnit.MINUTES.between(now, sessionStart);
                            int reminderMinutes = mentor.getSessionReminderMinutes() != null ? mentor.getSessionReminderMinutes() : 60;

                            int buffer = 3;
                            if (minutesUntilSession >= (reminderMinutes - buffer) && minutesUntilSession <= (reminderMinutes + buffer)) {
                                return processSessionReminder(session, mentor, minutesUntilSession);
                            }

                            if (minutesUntilSession < -1440) {
                                session.setReminderSent(true);
                                return sessionRepository.save(session).then(reactor.core.publisher.Mono.empty());
                            }

                            return reactor.core.publisher.Mono.empty();
                        })
                )
                .subscribe(
                        success -> log.debug("Session reminder processed"),
                        error -> log.error("Error in session reminders: {}", error.getMessage()),
                        () -> log.debug("Session reminder check completed")
                );
    }

    private reactor.core.publisher.Mono<Void> processSessionReminder(Session session, Mentor mentor, long minutesUntilSession) {
        return locationRepository.findById(session.getLocationId() != null ? session.getLocationId() : -1L)
                .switchIfEmpty(reactor.core.publisher.Mono.just(com.cozy.planner.model.entity.Location.builder().name(null).build()))
                .flatMap(location -> {
                    String locationName = location.getName();

                    String formattedDate = formatDate(session.getWorkoutDate());
                    String formattedTime = formatTime(session.getStartTime(), session.getEndTime());

                    log.info("Sending session reminder to mentor {} for session '{}' at {} ({} minutes)",
                            mentor.getName(), session.getTitle(), formattedTime, minutesUntilSession);

                    return telegramService.sendSessionReminderToMentor(
                                    mentor,
                                    session.getTitle(),
                                    formattedDate,
                                    formattedTime,
                                    locationName,
                                    (int) minutesUntilSession
                            )
                            .doOnNext(success -> {
                                if (success) {
                                    log.info("Session reminder sent to mentor {}", mentor.getName());
                                } else {
                                    log.warn("Failed to send session reminder to mentor {}", mentor.getName());
                                }
                            })
                            .then(markReminderSent(session));
                });
    }

    private reactor.core.publisher.Mono<Void> markReminderSent(Session session) {
        session.setReminderSent(true);
        return sessionRepository.save(session).then();
    }

    private String formatDate(LocalDate date) {
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy (EEE)", java.util.Locale.forLanguageTag("uk"));
        return date.format(formatter);
    }

    private String formatTime(LocalTime start, LocalTime end) {
        java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        if (end != null) {
            return start.format(timeFormatter) + " - " + end.format(timeFormatter);
        }
        return start.format(timeFormatter);
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
