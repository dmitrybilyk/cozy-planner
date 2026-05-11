package com.cozy.planner.controllers;

import com.cozy.planner.config.TelegramConfig;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.repositories.TraineeRepository;
import com.cozy.planner.service.EventBroadcastService;
import com.planner.api.AthletesApi;
import com.planner.model.AthleteDTO;
import com.planner.model.CreateAthleteRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AthletesApiController implements AthletesApi {

    private final TraineeRepository traineeRepository;
    private final TelegramConfig telegramConfig;
    private final EventBroadcastService eventBroadcastService;

    public AthletesApiController(TraineeRepository traineeRepository, 
                                   TelegramConfig telegramConfig,
                                   EventBroadcastService eventBroadcastService) {
        this.traineeRepository = traineeRepository;
        this.telegramConfig = telegramConfig;
        this.eventBroadcastService = eventBroadcastService;
    }

    @Override
    public Mono<ResponseEntity<AthleteDTO>> createAthlete(Mono<CreateAthleteRequest> createAthleteRequest, ServerWebExchange exchange) {
        return createAthleteRequest
                .flatMap(request -> {
                    String name = request.getName();
                    if (name == null || name.isBlank()) {
                        return Mono.error(new IllegalArgumentException("Name is required"));
                    }
                    Trainee trainee = Trainee.builder()
                            .name(name.trim())
                            .description(request.getDescription())
                            .mentorId(request.getCoachId())
                            .weekendReminderEnabled(false)
                            .build();
                    return traineeRepository.save(trainee);
                })
                .doOnSuccess(saved -> eventBroadcastService.broadcast("athlete_changed"))
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
    public Mono<ResponseEntity<Void>> deleteAthlete(Long athleteId, ServerWebExchange exchange) {
        return traineeRepository.findById(athleteId)
                .flatMap(trainee -> traineeRepository.delete(trainee)
                        .then(Mono.fromRunnable(() -> eventBroadcastService.broadcast("athlete_changed")))
                        .then(Mono.just(ResponseEntity.noContent().<Void>build())))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<AthleteDTO>> getAthleteById(Long athleteId, ServerWebExchange exchange) {
        return traineeRepository.findById(athleteId)
                .map(this::mapToDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<AthleteDTO>> updateAthlete(Long athleteId, Mono<AthleteDTO> athleteDTO, ServerWebExchange exchange) {
        return athleteDTO
                .flatMap(dto -> traineeRepository.findById(athleteId)
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
                            if (dto.getCoachId() != null) {
                                existing.setMentorId(dto.getCoachId());
                            }
                            return traineeRepository.save(existing);
                        }))
                .doOnSuccess(updated -> eventBroadcastService.broadcast("athlete_changed"))
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

    @PostMapping("/api/v1/athletes/{athleteId}/photo")
    public Mono<ResponseEntity<Map<String, Object>>> updatePhoto(@PathVariable Long athleteId,
                                                                   @RequestBody Map<String, Object> body,
                                                                   ServerWebExchange exchange) {
        Object photoObj = body.get("photoBase64");
        String photoBase64 = photoObj != null ? photoObj.toString() : null;
        
        return traineeRepository.findById(athleteId)
                .flatMap(existing -> {
                    existing.setPhotoBase64(photoBase64);
                    return traineeRepository.save(existing);
                })
                .doOnSuccess(saved -> eventBroadcastService.broadcast("athlete_changed"))
                .map(saved -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("photoBase64", saved.getPhotoBase64());
                    return ResponseEntity.ok(result);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/v1/athletes/{athleteId}/weekend-reminder")
    public Mono<ResponseEntity<Map<String, Object>>> updateWeekendReminder(@PathVariable Long athleteId,
                                                                              @RequestBody Map<String, Object> body,
                                                                              ServerWebExchange exchange) {
        Object enabledObj = body.get("enabled");
        boolean enabled = enabledObj != null && Boolean.TRUE.equals(enabledObj);
        
        return traineeRepository.findById(athleteId)
                .flatMap(existing -> {
                    existing.setWeekendReminderEnabled(enabled);
                    return traineeRepository.save(existing);
                })
                .doOnSuccess(saved -> eventBroadcastService.broadcast("athlete_changed"))
                .map(saved -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("weekendReminderEnabled", saved.isWeekendReminderEnabled());
                    return ResponseEntity.ok(result);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private AthleteDTO mapToDto(Trainee entity) {
        AthleteDTO dto = new AthleteDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setCoachId(entity.getMentorId());
        return dto;
    }
}
