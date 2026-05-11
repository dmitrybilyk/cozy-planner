package com.cozy.planner.controllers;

import com.cozy.planner.config.TelegramConfig;
import com.cozy.planner.model.entity.Athlete;
import com.cozy.planner.repositories.AthleteRepository;
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
    private final AthleteRepository athleteRepository;
    private final TelegramConfig telegramConfig;

    public MeController(UserRepository userRepository,
                        ClubRepository clubRepository,
                        CoachRepository coachRepository,
                        AthleteRepository athleteRepository,
                        TelegramConfig telegramConfig) {
        this.userRepository = userRepository;
        this.clubRepository = clubRepository;
        this.coachRepository = coachRepository;
        this.athleteRepository = athleteRepository;
        this.telegramConfig = telegramConfig;
    }

    @GetMapping("/api/v1/me")
    public Mono<Map<String, Object>> me(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            String googleSub = session.getAttribute("google_sub");
            if (googleSub != null) {
                return userRepository.findByGoogleSub(googleSub)
                        .flatMap(user -> clubRepository.findByUserId(user.getId())
                                .next()
                                .flatMap(club -> coachRepository.findAllByClubId(club.getId())
                                        .next()
                                        .map(coach -> {
                                            session.getAttributes().put("coach_id", coach.getId());
                                            Map<String, Object> r = new HashMap<>();
                                            r.put("user", Map.of("id", user.getId(), "email", user.getEmail(), "name", user.getName()));
                                            r.put("club", Map.of("id", club.getId(), "name", club.getName()));
                                            r.put("coach", Map.of("id", coach.getId(), "name", coach.getName()));
                                            r.put("mentor", Map.of("id", coach.getId(), "name", coach.getName()));
                                            return r;
                                        })
                                )
                                .defaultIfEmpty(defaultWithUser(user, "Демо"))
                        )
                        .defaultIfEmpty(defaultWithUser(googleSub));
            }

            Object traineeId = session.getAttribute("athlete_id");
            if (traineeId instanceof Number) {
                return athleteRepository.findById(((Number) traineeId).longValue())
                        .map(athlete -> {
                            Map<String, Object> r = new HashMap<>();
                            r.put("traineeId", athlete.getId());
                            r.put("athleteId", athlete.getId());
                            r.put("name", athlete.getName());
                            r.put("inviteToken", athlete.getInviteToken());
                            
                            boolean tgEnabled = telegramConfig.isEnabled() 
                                    && telegramConfig.getBotToken() != null 
                                    && !telegramConfig.getBotToken().isBlank();
                            r.put("telegramEnabled", tgEnabled);
                            r.put("telegramConnected", athlete.hasTelegram());
                            r.put("telegramUsername", athlete.getTelegramUsername());
                            
                            if (tgEnabled && athlete.getInviteToken() != null 
                                    && !athlete.getInviteToken().isBlank()
                                    && telegramConfig.getBotUsername() != null 
                                    && !telegramConfig.getBotUsername().isBlank()) {
                                r.put("telegramConnectLink", 
                                        "https://t.me/" + telegramConfig.getBotUsername() 
                                        + "?start=" + athlete.getInviteToken());
                            } else {
                                r.put("telegramConnectLink", null);
                            }
                            
                            return r;
                        });
            }

            {
                Map<String, Object> r = new HashMap<>();
                r.put("user", Map.of("email", "demo@cozyplanner.app", "name", "Демо"));
                r.put("club", Map.of());
                r.put("coach", Map.of("id", -1, "name", "Демо"));
                r.put("mentor", Map.of("id", -1, "name", "Демо"));
                return Mono.just(r);
            }
        });
    }

    private Map<String, Object> defaultWithUser(com.cozy.planner.model.entity.User user, String fallbackName) {
        Map<String, Object> r = new HashMap<>();
        r.put("user", Map.of("id", user.getId(), "email", user.getEmail(), "name", user.getName()));
        r.put("club", Map.of());
        r.put("coach", Map.of("id", -1, "name", fallbackName));
        r.put("mentor", Map.of("id", -1, "name", fallbackName));
        return r;
    }

    private Map<String, Object> defaultWithUser(String googleSub) {
        Map<String, Object> r = new HashMap<>();
        r.put("user", Map.of("email", googleSub, "name", "Демо"));
        r.put("club", Map.of());
        r.put("coach", Map.of("id", -1, "name", "Демо"));
        r.put("mentor", Map.of("id", -1, "name", "Демо"));
        return r;
    }
}
