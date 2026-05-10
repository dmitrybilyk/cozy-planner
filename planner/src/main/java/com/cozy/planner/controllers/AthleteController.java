package com.cozy.planner.controllers;

import com.cozy.planner.repositories.AthleteRepository;
import com.cozy.planner.model.entity.Athlete;
import com.cozy.planner.service.EventBroadcastService;
import com.planner.api.AthletesApi;
import com.planner.model.AthleteDTO;
import com.planner.model.CreateAthleteRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

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

    private AthleteDTO mapToDto(Athlete entity) {
        AthleteDTO dto = new AthleteDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setCoachId(entity.getCoachId());
        return dto;
    }
}