package com.cozy.planner.controllers;

import com.cozy.planner.repositories.ClubRepository;
import com.cozy.planner.repositories.CoachRepository;
import com.cozy.planner.repositories.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
public class MeController {

    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final CoachRepository coachRepository;

    public MeController(UserRepository userRepository,
                        ClubRepository clubRepository,
                        CoachRepository coachRepository) {
        this.userRepository = userRepository;
        this.clubRepository = clubRepository;
        this.coachRepository = coachRepository;
    }

    @GetMapping("/api/v1/me")
    public Mono<Map<String, Object>> me(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            String googleSub = session.getAttribute("google_sub");
            if (googleSub == null) {
                Map<String, Object> r = new HashMap<>();
                r.put("user", Map.of("email", "demo@cozyplanner.app", "name", "Демо"));
                r.put("club", Map.of());
                r.put("coach", Map.of("id", -1, "name", "Демо"));
                return Mono.just(r);
            }
            return userRepository.findByGoogleSub(googleSub)
                    .flatMap(user -> clubRepository.findByUserId(user.getId())
                            .next()
                            .flatMap(club -> coachRepository.findAllByClubId(club.getId())
                                    .next()
                                    .map(coach -> {
                                        Map<String, Object> r = new HashMap<>();
                                        r.put("user", Map.of("id", user.getId(), "email", user.getEmail(), "name", user.getName()));
                                        r.put("club", Map.of("id", club.getId(), "name", club.getName()));
                                        r.put("coach", Map.of("id", coach.getId(), "name", coach.getName()));
                                        return r;
                                    })
                            )
                            .defaultIfEmpty(defaultWithUser(user, "Демо"))
                    )
                    .defaultIfEmpty(defaultWithUser(googleSub));
        });
    }

    private Map<String, Object> defaultWithUser(com.cozy.planner.model.entity.User user, String fallbackName) {
        Map<String, Object> r = new HashMap<>();
        r.put("user", Map.of("id", user.getId(), "email", user.getEmail(), "name", user.getName()));
        r.put("club", Map.of());
        r.put("coach", Map.of("id", -1, "name", fallbackName));
        return r;
    }

    private Map<String, Object> defaultWithUser(String googleSub) {
        Map<String, Object> r = new HashMap<>();
        r.put("user", Map.of("email", googleSub, "name", "Демо"));
        r.put("club", Map.of());
        r.put("coach", Map.of("id", -1, "name", "Демо"));
        return r;
    }
}
