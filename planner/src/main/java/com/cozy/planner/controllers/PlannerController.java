package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Athlete;
import com.cozy.planner.repositories.AthleteRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Controller
public class PlannerController {

    private final AthleteRepository athleteRepository;

    public PlannerController(AthleteRepository athleteRepository) {
        this.athleteRepository = athleteRepository;
    }

    @GetMapping("/planner")
    public String getPlanner() {
        return "coach-view";
    }

    @GetMapping("/planner23")
    public String getPlanner2() {
        return "coach-view";
    }

    @GetMapping("/athlete/{token}")
    public Mono<String> getAthleteInvite(@PathVariable String token, ServerWebExchange exchange) {
        return exchange.getSession()
                .flatMap(session -> athleteRepository.findByInviteToken(token)
                        .map(athlete -> {
                            session.getAttributes().put("athlete_id", athlete.getId());
                            session.getAttributes().put("trainee_id", athlete.getId());
                            return "athlete-availability";
                        })
                        .defaultIfEmpty("redirect:/signin"));
    }
}
