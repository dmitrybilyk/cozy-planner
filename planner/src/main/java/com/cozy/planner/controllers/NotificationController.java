package com.cozy.planner.controllers;

import com.cozy.planner.config.TelegramConfig;
import com.cozy.planner.model.entity.Athlete;
import com.cozy.planner.repositories.AthleteRepository;
import com.cozy.planner.service.TelegramService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final TelegramService telegramService;
    private final AthleteRepository athleteRepository;
    private final TelegramConfig telegramConfig;

    @Value("${app.base-url:}")
    private String configuredBaseUrl;

    public NotificationController(TelegramService telegramService, AthleteRepository athleteRepository, TelegramConfig telegramConfig) {
        this.telegramService = telegramService;
        this.athleteRepository = athleteRepository;
        this.telegramConfig = telegramConfig;
    }

    @GetMapping("/telegram/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", telegramService.isEnabled());
        result.put("botUsername", telegramConfig.getBotUsername());
        return ResponseEntity.ok().body(result);
    }

    @GetMapping(path = {"/coaches/{coachId}/athletes-telegram", "/mentors/{mentorId}/trainees-telegram"})
    public Flux<Map<String, Object>> getTraineesWithTelegram(@PathVariable(required = false) Long mentorId,
                                                               @PathVariable(required = false) Long coachId) {
        Long id = (mentorId != null) ? mentorId : coachId;
        return athleteRepository.findAllByCoachId(id)
                .map(this::toTelegramStatus);
    }

    @GetMapping(path = {"/athletes/{traineeId}/telegram-status", "/trainees/{traineeId}/telegram-status"})
    public Mono<ResponseEntity<Map<String, Object>>> getTelegramStatus(@PathVariable Long traineeId) {
        return athleteRepository.findById(traineeId)
                .map(this::toTelegramStatus)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping(path = {"/athletes/{traineeId}/notify-availability", "/trainees/{traineeId}/notify-availability"})
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

        final String finalCustomMessage = customMessage;

        return athleteRepository.findById(traineeId)
                .flatMap(athlete -> {
                    Map<String, Object> result = new HashMap<>();
                    
                    if (!athlete.hasTelegram()) {
                        result.put("success", false);
                        result.put("reason", "Telegram not connected");
                        result.put("telegramConnected", false);
                        result.put("connectLink", getConnectLink(athlete.getInviteToken()));
                        return Mono.just(ResponseEntity.ok().body(result));
                    }

                    String baseUrl = getBaseUrl(exchange);
                    return telegramService.sendAvailabilityReminder(athlete, baseUrl, finalCustomMessage)
                            .map(sent -> {
                                result.put("success", sent);
                                result.put("telegramConnected", true);
                                if (sent) {
                                    result.put("message", "Notification sent to " + athlete.getName());
                                } else {
                                    result.put("reason", "Failed to send (chat may be blocked)");
                                }
                                return ResponseEntity.ok().body(result);
                            });
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toTelegramStatus(Athlete athlete) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", athlete.getId());
        result.put("telegramEnabled", telegramService.isEnabled());
        result.put("telegramConnected", athlete.hasTelegram());
        result.put("telegramUsername", athlete.getTelegramUsername());
        result.put("connectLink", getConnectLink(athlete.getInviteToken()));
        result.put("photoBase64", athlete.getPhotoBase64());
        result.put("weekendReminderEnabled", athlete.isWeekendReminderEnabled());
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
