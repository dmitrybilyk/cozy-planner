package com.cozy.planner.controllers;

import com.cozy.planner.config.TelegramConfig;
import com.cozy.planner.model.entity.Athlete;
import com.cozy.planner.repositories.AthleteRepository;
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

    private final AthleteRepository athleteRepository;
    private final TelegramConfig telegramConfig;
    private final EventBroadcastService eventBroadcastService;

    public AthletesApiController(AthleteRepository athleteRepository, 
                                  TelegramConfig telegramConfig,
                                  EventBroadcastService eventBroadcastService) {
        this.athleteRepository = athleteRepository;
        this.telegramConfig = telegramConfig;
        this.eventBroadcastService = eventBroadcastService;
    }

    @Override
    public Mono<ResponseEntity<AthleteDTO>> createAthlete(Mono<CreateAthleteRequest> createAthleteRequest, ServerWebExchange exchange) {
        return createAthleteRequest
                .flatMap(request -> {
                    Athlete athlete = Athlete.builder()
                            .name(request.getName())
                            .description(request.getDescription())
                            .coachId(request.getCoachId())
                            .weekendReminderEnabled(false)
                            .build();
                    return athleteRepository.save(athlete);
                })
                .doOnSuccess(saved -> eventBroadcastService.broadcast("athlete_changed"))
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(mapToDto(saved)));
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteAthlete(Long athleteId, ServerWebExchange exchange) {
        return athleteRepository.findById(athleteId)
                .flatMap(athlete -> athleteRepository.delete(athlete)
                        .then(Mono.fromRunnable(() -> eventBroadcastService.broadcast("athlete_changed")))
                        .then(Mono.just(ResponseEntity.noContent().<Void>build())))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<AthleteDTO>> getAthleteById(Long athleteId, ServerWebExchange exchange) {
        return athleteRepository.findById(athleteId)
                .map(this::mapToDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<AthleteDTO>> updateAthlete(Long athleteId, Mono<AthleteDTO> athleteDTO, ServerWebExchange exchange) {
        return athleteDTO
                .flatMap(dto -> athleteRepository.findById(athleteId)
                        .flatMap(existing -> {
                            if (dto.getName() != null) {
                                existing.setName(dto.getName());
                            }
                            if (dto.getDescription() != null) {
                                existing.setDescription(dto.getDescription());
                            }
                            if (dto.getCoachId() != null) {
                                existing.setCoachId(dto.getCoachId());
                            }
                            return athleteRepository.save(existing);
                        }))
                .doOnSuccess(updated -> eventBroadcastService.broadcast("athlete_changed"))
                .map(updated -> ResponseEntity.ok(mapToDto(updated)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/v1/athletes/{athleteId}/photo")
    public Mono<ResponseEntity<Map<String, Object>>> updatePhoto(@PathVariable Long athleteId,
                                                                  @RequestBody Map<String, Object> body,
                                                                  ServerWebExchange exchange) {
        Object photoObj = body.get("photoBase64");
        String photoBase64 = photoObj != null ? photoObj.toString() : null;
        
        return athleteRepository.findById(athleteId)
                .flatMap(existing -> {
                    existing.setPhotoBase64(photoBase64);
                    return athleteRepository.save(existing);
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
        
        return athleteRepository.findById(athleteId)
                .flatMap(existing -> {
                    existing.setWeekendReminderEnabled(enabled);
                    return athleteRepository.save(existing);
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

    private AthleteDTO mapToDto(Athlete entity) {
        AthleteDTO dto = new AthleteDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setCoachId(entity.getCoachId());
        return dto;
    }
}
