package com.cozy.planner.controllers;

import com.cozy.planner.config.TelegramConfig;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.repositories.TraineeRepository;
import com.cozy.planner.service.AuditService;
import com.cozy.planner.service.EventBroadcastService;
import com.cozy.planner.service.NotificationService;
import com.planner.api.TraineesApi;
import com.planner.model.TraineeDTO;
import com.planner.model.CreateTraineeRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class TraineesApiController implements TraineesApi {

    private final TraineeRepository traineeRepository;
    private final TelegramConfig telegramConfig;
    private final EventBroadcastService eventBroadcastService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Value("${app.base-url:}")
    private String appBaseUrl;

    public TraineesApiController(TraineeRepository traineeRepository,
                                   TelegramConfig telegramConfig,
                                   EventBroadcastService eventBroadcastService,
                                   NotificationService notificationService,
                                   AuditService auditService) {
        this.traineeRepository = traineeRepository;
        this.telegramConfig = telegramConfig;
        this.eventBroadcastService = eventBroadcastService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Override
    public Mono<ResponseEntity<TraineeDTO>> createTrainee(Mono<CreateTraineeRequest> createTraineeRequest, ServerWebExchange exchange) {
        return createTraineeRequest
                .flatMap(request -> {
                    String name = request.getName();
                    if (name == null || name.isBlank()) {
                        return Mono.error(new IllegalArgumentException("Name is required"));
                    }
                    Trainee trainee = Trainee.builder()
                            .name(name.trim())
                            .description(request.getDescription())
                            .mentorId(request.getMentorId())
                            .weekendReminderEnabled(false)
                            .build();
                    return traineeRepository.save(trainee);
                })
                .flatMap(saved -> auditService.log("TRAINEE_ADDED", null, saved.getMentorId(),
                        "Trainee added: " + saved.getName() + " (mentor " + saved.getMentorId() + ")")
                        .thenReturn(saved))
                .doOnSuccess(saved -> eventBroadcastService.broadcast("trainee_changed"))
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(mapToDto(saved)))
                .onErrorResume(e -> {
                    if (e instanceof IllegalArgumentException || 
                            (e.getCause() != null && e.getCause() instanceof java.sql.SQLIntegrityConstraintViolationException) ||
                            e.getMessage() != null && (e.getMessage().contains("unique") || e.getMessage().contains("duplicate"))) {
                        return Mono.just(ResponseEntity.badRequest().build());
                    }
                    return Mono.error(e);
                });
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteTrainee(Long traineeId, ServerWebExchange exchange) {
        return traineeRepository.findById(traineeId)
                .flatMap(trainee -> traineeRepository.delete(trainee)
                        .then(Mono.fromRunnable(() -> eventBroadcastService.broadcast("trainee_changed")))
                        .then(Mono.just(ResponseEntity.noContent().<Void>build())))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<TraineeDTO>> getTraineeById(Long traineeId, ServerWebExchange exchange) {
        return traineeRepository.findById(traineeId)
                .map(this::mapToDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<TraineeDTO>> updateTrainee(Long traineeId, Mono<TraineeDTO> traineeDTO, ServerWebExchange exchange) {
        return traineeDTO
                .flatMap(dto -> traineeRepository.findById(traineeId)
                        .flatMap(existing -> {
                            if (dto.getName() != null) {
                                if (dto.getName().isBlank()) {
                                    return Mono.error(new IllegalArgumentException("Name cannot be blank"));
                                }
                                existing.setName(dto.getName().trim());
                            }
                            if (dto.getDescription() != null) {
                                existing.setDescription(dto.getDescription());
                            }
                            if (dto.getMentorId() != null) {
                                existing.setMentorId(dto.getMentorId());
                            }
                            return traineeRepository.save(existing);
                        }))
                .doOnSuccess(updated -> eventBroadcastService.broadcast("trainee_changed"))
                .map(updated -> ResponseEntity.ok(mapToDto(updated)))
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .onErrorResume(e -> {
                    if (e instanceof IllegalArgumentException || 
                            (e.getCause() != null && e.getCause() instanceof java.sql.SQLIntegrityConstraintViolationException) ||
                            e.getMessage() != null && (e.getMessage().contains("unique") || e.getMessage().contains("duplicate"))) {
                        return Mono.just(ResponseEntity.badRequest().build());
                    }
                    return Mono.error(e);
                });
    }

    @PostMapping("/api/v1/trainees/{traineeId}/photo")
    public Mono<ResponseEntity<Map<String, Object>>> updatePhoto(@PathVariable Long traineeId,
                                                                    @RequestBody Map<String, Object> body,
                                                                    ServerWebExchange exchange) {
        Object photoObj = body.get("photoBase64");
        String photoBase64 = photoObj != null ? photoObj.toString() : null;
        
        return traineeRepository.findById(traineeId)
                .flatMap(existing -> {
                    existing.setPhotoBase64(photoBase64);
                    return traineeRepository.save(existing);
                })
                .doOnSuccess(saved -> eventBroadcastService.broadcast("trainee_changed"))
                .map(saved -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("photoBase64", saved.getPhotoBase64());
                    return ResponseEntity.ok(result);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/v1/trainees/{traineeId}/weekend-reminder")
    public Mono<ResponseEntity<Map<String, Object>>> updateWeekendReminder(@PathVariable Long traineeId,
                                                                               @RequestBody Map<String, Object> body,
                                                                               ServerWebExchange exchange) {
        Object enabledObj = body.get("enabled");
        boolean enabled = enabledObj != null && Boolean.TRUE.equals(enabledObj);
        
        return traineeRepository.findById(traineeId)
                .flatMap(existing -> {
                    existing.setWeekendReminderEnabled(enabled);
                    return traineeRepository.save(existing);
                })
                .doOnSuccess(saved -> eventBroadcastService.broadcast("trainee_changed"))
                .map(saved -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("weekendReminderEnabled", saved.isWeekendReminderEnabled());
                    return ResponseEntity.ok(result);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/v1/trainees/{traineeId}/session-reminder")
    public Mono<ResponseEntity<Map<String, Object>>> updateSessionReminder(@PathVariable Long traineeId,
                                                                                @RequestBody Map<String, Object> body,
                                                                                ServerWebExchange exchange) {
        Object enabledObj = body.get("enabled");
        boolean enabled = enabledObj != null && Boolean.TRUE.equals(enabledObj);
        
        return traineeRepository.findById(traineeId)
                .flatMap(existing -> {
                    existing.setSessionReminderEnabled(enabled);
                    return traineeRepository.save(existing);
                })
                .doOnSuccess(saved -> eventBroadcastService.broadcast("trainee_changed"))
                .map(saved -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("sessionReminderEnabled", saved.isSessionReminderEnabled());
                    return ResponseEntity.ok(result);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/v1/trainees/{traineeId}/timezone")
    public Mono<ResponseEntity<Map<String, Object>>> updateTimezone(@PathVariable Long traineeId,
                                                                        @RequestBody Map<String, Object> body,
                                                                        ServerWebExchange exchange) {
        Object tzObj = body.get("timezone");
        String timezone = tzObj != null ? tzObj.toString() : null;

        return traineeRepository.findById(traineeId)
                .flatMap(existing -> {
                    existing.setTimezone(timezone);
                    return traineeRepository.save(existing);
                })
                .doOnSuccess(saved -> eventBroadcastService.broadcast("trainee_changed"))
                .map(saved -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("timezone", saved.getTimezone());
                    return ResponseEntity.ok(result);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/v1/trainees/{traineeId}/notify-availability")
    public Mono<ResponseEntity<Map<String, Object>>> notifyAvailability(@PathVariable Long traineeId,
                                                                             @RequestBody Map<String, Object> body,
                                                                             ServerWebExchange exchange) {
        return traineeRepository.findById(traineeId)
                .flatMap(trainee -> {
                    String dayType = body.containsKey("dayType") ? body.get("dayType").toString() : "tomorrow";
                    String targetDate = body.containsKey("targetDate") ? body.get("targetDate").toString() : null;
                    if ("today".equals(dayType) && targetDate == null) {
                        targetDate = java.time.LocalDate.now().toString();
                    }
                    String customMessage = body.containsKey("customMessage") ? body.get("customMessage").toString() : null;

                    String baseUrl = appBaseUrl;
                    if (baseUrl == null || baseUrl.isBlank()) {
                        baseUrl = exchange.getRequest().getURI().getScheme() + "://" + exchange.getRequest().getURI().getHost();
                        int port = exchange.getRequest().getURI().getPort();
                        if (port > 0 && port != 80 && port != 443) {
                            baseUrl += ":" + port;
                        }
                    }

                    return notificationService.sendAvailabilityReminder(trainee, baseUrl, customMessage, dayType, targetDate)
                            .map(success -> {
                                Map<String, Object> result = new HashMap<>();
                                if (success) {
                                    result.put("success", true);
                                } else {
                                    result.put("success", false);
                                    result.put("reason", "Telegram not connected");
                                }
                                return ResponseEntity.ok(result);
                            });
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/v1/trainees/bulk-notify-availability")
    public Mono<ResponseEntity<Map<String, Object>>> bulkNotifyAvailability(@RequestBody Map<String, Object> body,
                                                                             ServerWebExchange exchange) {
        Object idsObj = body.get("traineeIds");
        if (!(idsObj instanceof List)) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("reason", "traineeIds required");
            return Mono.just(ResponseEntity.badRequest().body(err));
        }
        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) idsObj;
        String dayType = body.containsKey("dayType") ? body.get("dayType").toString() : "tomorrow";
        String customMessage = body.containsKey("customMessage") ? body.get("customMessage").toString() : null;

        String baseUrl = appBaseUrl;
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = exchange.getRequest().getURI().getScheme() + "://" + exchange.getRequest().getURI().getHost();
            int port = exchange.getRequest().getURI().getPort();
            if (port > 0 && port != 80 && port != 443) baseUrl += ":" + port;
        }
        final String resolvedBaseUrl = baseUrl;

        return Flux.fromIterable(rawIds)
                .map(id -> ((Number) id).longValue())
                .flatMap(id -> traineeRepository.findById(id)
                        .flatMap(trainee -> notificationService.sendAvailabilityReminder(
                                trainee, resolvedBaseUrl, customMessage, dayType, null))
                        .defaultIfEmpty(false))
                .collectList()
                .map(results -> {
                    long sent = results.stream().filter(Boolean.TRUE::equals).count();
                    Map<String, Object> r = new HashMap<>();
                    r.put("success", true);
                    r.put("sent", sent);
                    r.put("total", results.size());
                    return ResponseEntity.ok(r);
                });
    }

    private TraineeDTO mapToDto(Trainee entity) {
        TraineeDTO dto = new TraineeDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setMentorId(entity.getMentorId());
        return dto;
    }
}
