package com.cozy.planner.controllers;

import com.planner.api.CoachesApi;
import com.planner.model.AthleteDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class CoachController implements CoachesApi {

    // You will later inject your CoachService or AthleteRepository here
    // private final AthleteRepository athleteRepository;

    @Override
    public Mono<ResponseEntity<Flux<AthleteDTO>>> getCoachAthletes(Long coachId, ServerWebExchange exchange) {
        // Mocking the reactive response
        AthleteDTO athlete1 = new AthleteDTO();
        athlete1.setId(101L);
        athlete1.setName("John Doe");
        athlete1.setCoachId(coachId);

        AthleteDTO athlete2 = new AthleteDTO();
        athlete2.setId(102L);
        athlete2.setName("Jane Smith");
        athlete2.setCoachId(coachId);

        Flux<AthleteDTO> athleteFlux = Flux.just(athlete1, athlete2);
        
        return Mono.just(ResponseEntity.ok(athleteFlux));
    }
}