package com.cozy.planner.controllers;

import com.cozy.planner.repositories.AthleteRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Controller
public class AthletePageController {

    private final AthleteRepository athleteRepository;

    public AthletePageController(AthleteRepository athleteRepository) {
        this.athleteRepository = athleteRepository;
    }

    @GetMapping("/athlete/{token}")
    public Mono<String> athleteShortInvitePage(@PathVariable String token, ServerWebExchange exchange) {
        return athleteRepository.findByInviteToken(token)
                .flatMap(athlete -> exchange.getSession().map(session -> {
                    session.getAttributes().put("athlete_id", athlete.getId());
                    session.getAttributes().put("athlete_invite_token", token);
                    return "redirect:/athlete/availability";
                }))
                .defaultIfEmpty("redirect:/signin?error=invalid-invite");
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
    public Mono<String> athleteAvailabilityPage(
            @RequestParam(required = false) String token,
            ServerWebExchange exchange) {
        
        return exchange.getSession().flatMap(session -> {
            if (session.getAttribute("athlete_id") != null) {
                return Mono.just("athlete-availability");
            }
            
            if (token != null && !token.isBlank()) {
                return athleteRepository.findByInviteToken(token)
                        .map(athlete -> {
                    session.getAttributes().put("athlete_id", athlete.getId());
                    session.getAttributes().put("athlete_invite_token", token);
                    return "athlete-availability";
                })
                .defaultIfEmpty("redirect:/signin?error=invalid-token");
            }
            
            return Mono.just("redirect:/signin");
        });
    }
}
