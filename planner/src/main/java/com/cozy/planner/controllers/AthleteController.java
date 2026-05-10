package com.cozy.planner.controllers;

import com.cozy.planner.repositories.AthleteRepository;
import com.cozy.planner.model.entity.Athlete;
import com.cozy.planner.service.EventBroadcastService;
import com.planner.api.AthletesApi;
import com.planner.model.AthleteDTO;
import com.planner.model.CreateAthleteRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AthleteController implements AthletesApi {

    private final AthleteRepository athleteRepository;
    private final EventBroadcastService eventService;

    public AthleteController(AthleteRepository athleteRepository, EventBroadcastService eventService) {
        this.athleteRepository = athleteRepository;
        this.eventService = eventService;
    }

    @Override
    public Mono<ResponseEntity<AthleteDTO>> createAthlete(Mono<CreateAthleteRequest> createAthleteRequest, ServerWebExchange exchange) {
        return createAthleteRequest
                .flatMap(request -> {
                    Athlete athlete = new Athlete();
                    athlete.setName(request.getName());
                    athlete.setDescription(request.getDescription());
                    athlete.setCoachId(request.getCoachId());
                    return athleteRepository.save(athlete);
                })
                .map(this::mapToDto)
                .map(dto -> {
                    eventService.broadcast("athlete_changed");
                    return ResponseEntity.status(HttpStatus.CREATED).body(dto);
                });
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
        return athleteDTO.flatMap(dto ->
                        athleteRepository.findById(athleteId)
                                .flatMap(existingAthlete -> {
                                    existingAthlete.setName(dto.getName());
                                    existingAthlete.setDescription(dto.getDescription());
                                    existingAthlete.setCoachId(dto.getCoachId());
                                    return athleteRepository.save(existingAthlete);
                                })
                )
                .map(this::mapToDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnSuccess(r -> { if (r.getStatusCode().is2xxSuccessful()) eventService.broadcast("athlete_changed"); });
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteAthlete(Long athleteId, ServerWebExchange exchange) {
        return athleteRepository.findById(athleteId)
                .flatMap(athlete -> athleteRepository.delete(athlete)
                        .then(Mono.fromRunnable(() -> eventService.broadcast("athlete_changed")))
                        .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT))))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/v1/athletes/{athleteId}/photo")
    public Mono<ResponseEntity<Map<String, Object>>> uploadPhoto(
            @PathVariable Long athleteId,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        
        return getCoachId(exchange)
                .flatMap(coachId -> athleteRepository.findById(athleteId)
                        .flatMap(athlete -> {
                            if (!athlete.getCoachId().equals(coachId)) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).<Map<String, Object>>build());
                            }
                            String photoBase64 = (String) body.get("photoBase64");
                            if (photoBase64 != null && photoBase64.length() > 0) {
                                if (photoBase64.length() > 500000) {
                                    Map<String, Object> error = new HashMap<>();
                                    error.put("success", false);
                                    error.put("reason", "Photo too large (max 500KB)");
                                    return Mono.just(ResponseEntity.badRequest().body(error));
                                }
                                athlete.setPhotoBase64(photoBase64);
                            } else {
                                athlete.setPhotoBase64(null);
                            }
                            return athleteRepository.save(athlete)
                                    .map(saved -> {
                                        eventService.broadcast("athlete_changed");
                                        Map<String, Object> result = new HashMap<>();
                                        result.put("success", true);
                                        result.put("photoBase64", saved.getPhotoBase64());
                                        return ResponseEntity.ok(result);
                                    });
                        })
                        .defaultIfEmpty(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/api/v1/athletes/{athleteId}/photo")
    public Mono<ResponseEntity<Map<String, Object>>> deletePhoto(
            @PathVariable Long athleteId,
            ServerWebExchange exchange) {
        
        return getCoachId(exchange)
                .flatMap(coachId -> athleteRepository.findById(athleteId)
                        .flatMap(athlete -> {
                            if (!athlete.getCoachId().equals(coachId)) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).<Map<String, Object>>build());
                            }
                            athlete.setPhotoBase64(null);
                            return athleteRepository.save(athlete)
                                    .map(saved -> {
                                        eventService.broadcast("athlete_changed");
                                        Map<String, Object> result = new HashMap<>();
                                        result.put("success", true);
                                        return ResponseEntity.ok(result);
                                    });
                        })
                        .defaultIfEmpty(ResponseEntity.notFound().build()));
    }

    private Mono<Long> getCoachId(ServerWebExchange exchange) {
        return exchange.getSession().map(session -> {
            Object coachId = session.getAttribute("coach_id");
            if (coachId instanceof Number) return ((Number) coachId).longValue();
            return -1L;
        });
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