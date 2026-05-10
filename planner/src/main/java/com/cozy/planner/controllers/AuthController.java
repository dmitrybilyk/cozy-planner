package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Club;
import com.cozy.planner.model.entity.Coach;
import com.cozy.planner.model.entity.User;
import com.cozy.planner.repositories.ClubRepository;
import com.cozy.planner.repositories.CoachRepository;
import com.cozy.planner.repositories.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final CoachRepository coachRepository;

    public AuthController(UserRepository userRepository,
                          ClubRepository clubRepository,
                          CoachRepository coachRepository) {
        this.userRepository = userRepository;
        this.clubRepository = clubRepository;
        this.coachRepository = coachRepository;
    }

    @GetMapping("/setup")
    public Mono<String> setupPage(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            String googleSub = session.getAttribute("google_sub");
            if (googleSub == null) {
                return Mono.just("redirect:/login");
            }
            return userRepository.findByGoogleSub(googleSub)
                    .flatMap(user -> clubRepository.findByUserId(user.getId())
                            .next()
                            .map(club -> "redirect:/planner")
                            .defaultIfEmpty("setup")
                    )
                    .defaultIfEmpty("setup");
        });
    }

    @PostMapping("/setup")
    public Mono<String> setup(ServerWebExchange exchange) {
        return exchange.getFormData().flatMap(formData -> {
            String clubName = formData.getFirst("clubName");
            String coachName = formData.getFirst("coachName");
            return exchange.getSession().flatMap(session -> {
                String googleSub = session.getAttribute("google_sub");
                String email = session.getAttribute("user_email");
                String name = session.getAttribute("user_name");
                if (googleSub == null) {
                    return Mono.just("redirect:/login");
                }
                return userRepository.findByGoogleSub(googleSub)
                        .flatMap(existingUser -> createClubAndCoach(existingUser, clubName, coachName)
                                .thenReturn("redirect:/planner"))
                        .switchIfEmpty(
                                Mono.defer(() -> {
                                    User newUser = User.builder()
                                            .email(email)
                                            .name(name != null ? name : coachName)
                                            .googleSub(googleSub)
                                            .createdAt(LocalDateTime.now())
                                            .build();
                                    return userRepository.save(newUser)
                                            .flatMap(user -> createClubAndCoach(user, clubName, coachName))
                                            .thenReturn("redirect:/planner");
                                })
                        );
            });
        });
    }

    private Mono<Void> createClubAndCoach(User user, String clubName, String coachName) {
        Club club = Club.builder().name(clubName).userId(user.getId()).build();
        return clubRepository.save(club).flatMap(savedClub -> {
            Coach coach = Coach.builder()
                    .name(coachName)
                    .clubId(savedClub.getId())
                    .build();
            return coachRepository.save(coach).then();
        });
    }
}
