package com.cozy.planner.controllers;

import com.cozy.planner.repositories.AthleteRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Controller
public class AthletePageController {

    private final AthleteRepository athleteRepository;

    public AthletePageController(AthleteRepository athleteRepository) {
        this.athleteRepository = athleteRepository;
    }

    @GetMapping("/athlete/register")
    public Mono<String> athleteRegisterPage(@RequestParam String token, ServerWebExchange exchange) {
        return athleteRepository.findByInviteToken(token)
                .flatMap(athlete -> exchange.getSession().map(session -> {
                    session.getAttributes().put("athlete_id", athlete.getId());
                    session.getAttributes().put("athlete_invite_token", token);
                    return "redirect:/athlete/availability";
                }))
                .defaultIfEmpty("redirect:/signin?error=invalid-invite");
    }

    @GetMapping("/athlete/availability")
    public Mono<String> athleteAvailabilityPage(ServerWebExchange exchange) {
        return exchange.getSession().map(session -> {
            if (session.getAttribute("athlete_id") == null) {
                return "redirect:/signin";
            }
            return "athlete-availability";
        });
    }
}
