package com.cozy.planner.controllers;

import com.cozy.planner.config.TelegramConfig;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.repositories.TraineeRepository;
import com.cozy.planner.service.EventBroadcastService;
import com.planner.api.TraineesApi;
import com.planner.model.TraineeDTO;
import com.planner.model.CreateTraineeRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
public class TraineesApiController implements TraineesApi {

    private final TraineeRepository traineeRepository;
    private final TelegramConfig telegramConfig;
    private final EventBroadcastService eventBroadcastService;

    public TraineesApiController(TraineeRepository traineeRepository, 
                                   TelegramConfig telegramConfig,
                                   EventBroadcastService eventBroadcastService) {
        this.traineeRepository = traineeRepository;
        this.telegramConfig = telegramConfig;
        this.eventBroadcastService = eventBroadcastService;
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

    private TraineeDTO mapToDto(Trainee entity) {
        TraineeDTO dto = new TraineeDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setMentorId(entity.getMentorId());
        return dto;
    }
}
