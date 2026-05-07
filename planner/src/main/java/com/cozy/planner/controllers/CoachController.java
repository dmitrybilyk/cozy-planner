package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Coach;
import com.cozy.planner.repositories.AthleteRepository;
import com.cozy.planner.repositories.CoachRepository;
import com.planner.api.CoachesApi;
import com.planner.model.AthleteDTO;
import com.planner.model.CoachDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class CoachController implements CoachesApi {

    private final AthleteRepository athleteRepository;
    private final CoachRepository coachRepository;

    public CoachController(AthleteRepository athleteRepository, CoachRepository coachRepository) {
        this.athleteRepository = athleteRepository;
        this.coachRepository = coachRepository;
    }

    /**
     * ПРИМІТКА: Якщо після генерації тут все ще Mono<ResponseEntity<Void>>,
     * перевір свій openapi.yaml (див. пункт 2 нижче).
     */
    @Override
    public Mono<ResponseEntity<Flux<CoachDTO>>> getClubCoaches(Long clubId, ServerWebExchange exchange) {
        Flux<CoachDTO> coachFlux = coachRepository.findAllByClubId(clubId)
                .map(this::mapToCoachDto);

        return Mono.just(ResponseEntity.ok(coachFlux));
    }

    @Override
    public Mono<ResponseEntity<Flux<AthleteDTO>>> getCoachAthletes(Long coachId, ServerWebExchange exchange) {
        Flux<AthleteDTO> athleteFlux = athleteRepository.findAllByCoachId(coachId)
                .map(entity -> {
                    AthleteDTO dto = new AthleteDTO();
                    dto.setId(entity.getId());
                    dto.setName(entity.getName());
                    dto.setDescription(entity.getDescription());
                    dto.setCoachId(entity.getCoachId());
                    return dto;
                });

        return Mono.just(ResponseEntity.ok(athleteFlux));
    }

    private CoachDTO mapToCoachDto(Coach entity) {
        CoachDTO dto = new CoachDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setSpecialization(entity.getSpecialization());
        dto.setClubId(entity.getClubId());
        return dto;
    }
}