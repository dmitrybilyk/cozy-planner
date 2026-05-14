package com.cozy.planner.controllers;

import com.cozy.planner.config.TelegramConfig;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.repositories.TraineeRepository;
import com.cozy.planner.service.TelegramService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final TelegramService telegramService;
    private final TraineeRepository traineeRepository;
    private final TelegramConfig telegramConfig;

    @Value("${app.base-url:}")
    private String configuredBaseUrl;

    public NotificationController(TelegramService telegramService, TraineeRepository traineeRepository, TelegramConfig telegramConfig) {
        this.telegramService = telegramService;
        this.traineeRepository = traineeRepository;
        this.telegramConfig = telegramConfig;
    }

    @GetMapping("/telegram/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", telegramService.isEnabled());
        result.put("botUsername", telegramConfig.getBotUsername());
        return ResponseEntity.ok().body(result);
    }

    @GetMapping(path = {"/mentors/{mentorId}/trainees-telegram"})
    public Flux<Map<String, Object>> getTraineesWithTelegram(@PathVariable Long mentorId) {
        return traineeRepository.findAllByMentorId(mentorId)
                .map(this::toTelegramStatus);
    }

    @GetMapping(path = {"/trainees/{traineeId}/telegram-status"})
    public Mono<ResponseEntity<Map<String, Object>>> getTelegramStatus(@PathVariable Long traineeId) {
        return traineeRepository.findById(traineeId)
                .map(this::toTelegramStatus)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping(path = {"/trainees/{traineeId}/notify-availability"})
    public Mono<ResponseEntity<Map<String, Object>>> notifyTrainee(
            @PathVariable Long traineeId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {

        if (!telegramService.isEnabled()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("reason", "Telegram not configured");
            return Mono.just(ResponseEntity.badRequest().body(result));
        }

        String customMessage = null;
        if (body != null && body.containsKey("customMessage")) {
            Object msg = body.get("customMessage");
            if (msg != null && !msg.toString().isBlank()) {
                customMessage = msg.toString().trim();
            }
        }

        String dayType = null;
        if (body != null && body.containsKey("dayType")) {
            Object dt = body.get("dayType");
            if (dt != null && !dt.toString().isBlank()) {
                dayType = dt.toString().trim();
            }
        }

        String rawTargetDate = null;
        if (body != null && body.containsKey("targetDate")) {
            Object td = body.get("targetDate");
            if (td != null && !td.toString().isBlank()) {
                rawTargetDate = td.toString().trim();
            }
        }

        final String finalCustomMessage = customMessage;
        final String finalDayType = resolveDayType(dayType);
        final String finalTargetDate = resolveTargetDate(finalDayType, rawTargetDate);

        return traineeRepository.findById(traineeId)
                .flatMap(trainee -> {
                    Map<String, Object> result = new HashMap<>();
                    
                    if (!trainee.hasTelegram()) {
                        result.put("success", false);
                        result.put("reason", "Telegram not connected");
                        result.put("telegramConnected", false);
                        result.put("connectLink", getConnectLink(trainee.getInviteToken()));
                        return Mono.just(ResponseEntity.ok().body(result));
                    }

                    String baseUrl = getBaseUrl(exchange);
                    return telegramService.sendAvailabilityReminder(trainee, baseUrl, finalCustomMessage, finalDayType, finalTargetDate)
                            .map(sent -> {
                                result.put("success", sent);
                                result.put("telegramConnected", true);
                                if (sent) {
                                    result.put("message", "Notification sent to " + trainee.getName());
                                } else {
                                    result.put("reason", "Failed to send (chat may be blocked)");
                                }
                                return ResponseEntity.ok(result);
                            });
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private String resolveDayType(String dayType) {
        if (dayType == null) return null;
        return switch (dayType) {
            case "tomorrow", "specific_day", "weekend" -> dayType;
            default -> null;
        };
    }

    private String resolveTargetDate(String dayType, String rawTargetDate) {
        if (dayType == null) return null;
        LocalDate today = LocalDate.now();
        return switch (dayType) {
            case "tomorrow" -> today.plusDays(1).toString();
            case "specific_day" -> {
                if (rawTargetDate != null && !rawTargetDate.isBlank()) {
                    try {
                        yield LocalDate.parse(rawTargetDate).toString();
                    } catch (Exception e) {
                        yield today.plusDays(1).toString();
                    }
                }
                yield today.plusDays(1).toString();
            }
            case "weekend" -> {
                LocalDate saturday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
                if (saturday.equals(today)) {
                    saturday = saturday.plusDays(7);
                }
                yield saturday.toString();
            }
            default -> null;
        };
    }

    private Map<String, Object> toTelegramStatus(Trainee trainee) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", trainee.getId());
        result.put("telegramEnabled", telegramService.isEnabled());
        result.put("telegramConnected", trainee.hasTelegram());
        result.put("telegramUsername", trainee.getTelegramUsername());
        result.put("connectLink", getConnectLink(trainee.getInviteToken()));
        result.put("photoBase64", trainee.getPhotoBase64());
        result.put("weekendReminderEnabled", trainee.isWeekendReminderEnabled());
        result.put("sessionReminderEnabled", trainee.isSessionReminderEnabled());
        return result;
    }

    private String getConnectLink(String inviteToken) {
        if (!telegramService.isEnabled()) return null;
        if (inviteToken == null || inviteToken.isBlank()) return null;
        String botUsername = telegramConfig.getBotUsername();
        if (botUsername == null || botUsername.isBlank()) return null;
        return "https://t.me/" + botUsername + "?start=" + inviteToken;
    }

    private String getBaseUrl(ServerWebExchange exchange) {
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank() 
                && !configuredBaseUrl.contains("localhost") 
                && !configuredBaseUrl.contains("127.0.0.1")) {
            return configuredBaseUrl;
        }

        var request = exchange.getRequest();
        var headers = request.getHeaders();

        String forwardedProto = headers.getFirst("X-Forwarded-Proto");
        String forwardedHost = headers.getFirst("X-Forwarded-Host");

        if (forwardedHost != null) {
            String proto = (forwardedProto != null) ? forwardedProto : "http";
            return proto + "://" + forwardedHost;
        }

        String scheme = request.getURI().getScheme();
        int port = request.getURI().getPort();
        String host = request.getURI().getHost();

        if (port > 0 && ((scheme.equals("http") && port != 80) || (scheme.equals("https") && port != 443))) {
            return scheme + "://" + host + ":" + port;
        }
        return scheme + "://" + host;
    }
}
