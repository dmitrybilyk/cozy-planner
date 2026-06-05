package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Club;
import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.User;
import com.cozy.planner.repositories.ClubRepository;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.UserRepository;
import com.cozy.planner.service.AuditService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final MentorRepository mentorRepository;
    private final AuditService auditService;

    public AuthController(UserRepository userRepository,
                          ClubRepository clubRepository,
                          MentorRepository mentorRepository,
                          AuditService auditService) {
        this.userRepository = userRepository;
        this.clubRepository = clubRepository;
        this.mentorRepository = mentorRepository;
        this.auditService = auditService;
    }

    @GetMapping("/setup")
    public Mono<String> setupPage(ServerWebExchange exchange, org.springframework.ui.Model model) {
        return exchange.getSession().flatMap(session -> {
            String googleSub = session.getAttribute("google_sub");
            if (googleSub == null) {
                return Mono.just("redirect:/login");
            }
            String email = session.getAttribute("user_email");
            if ("dmitry.mediastore@gmail.com".equals(email)) {
                return Mono.just("redirect:/admin");
            }
            String name = session.getAttribute("user_name");
            if (name != null) {
                model.addAttribute("googleName", name);
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

    @GetMapping("/setup/finish")
    public Mono<String> setupFinish(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            if (session.getAttribute("google_sub") == null) {
                return Mono.just("redirect:/login");
            }
            return Mono.just("setup-finish");
        });
    }

    @PostMapping("/setup")
    public Mono<String> setup(ServerWebExchange exchange) {
        return exchange.getFormData().flatMap(formData -> {
            String clubName = formData.getFirst("clubName");
            String mentorName = formData.getFirst("mentorName");
            String profile = formData.getFirst("profile");
            String workStart = formData.getFirst("workStart");
            String workEnd = formData.getFirst("workEnd");
            String availStepStr = formData.getFirst("availStep");
            if (profile == null || profile.isBlank()) profile = "sport";
            if (workStart == null || workStart.isBlank()) workStart = "08:00";
            if (workEnd == null || workEnd.isBlank()) workEnd = "22:00";
            int availStep = 30;
            try { if (availStepStr != null) availStep = Integer.parseInt(availStepStr); } catch (NumberFormatException ignored) {}

            final String profileFinal = profile;
            final String workStartFinal = workStart;
            final String workEndFinal = workEnd;
            final int availStepFinal = availStep;

            return exchange.getSession().flatMap(session -> {
                String googleSub = session.getAttribute("google_sub");
                String email = session.getAttribute("user_email");
                String name = session.getAttribute("user_name");
                if (googleSub == null) {
                    return Mono.just("redirect:/login");
                }
                return userRepository.findByGoogleSub(googleSub)
                        .flatMap(existingUser -> createClubAndMentor(existingUser, clubName, mentorName, profileFinal, workStartFinal, workEndFinal, availStepFinal)
                                .flatMap(savedMentor -> {
                                    session.getAttributes().put("mentor_id", savedMentor.getId());
                                    return Mono.just("redirect:/setup/finish");
                                }))
                        .switchIfEmpty(
                                Mono.defer(() -> {
                                    User newUser = User.builder()
                                            .email(email)
                                            .name(name != null ? name : mentorName)
                                            .googleSub(googleSub)
                                            .createdAt(LocalDateTime.now())
                                            .build();
                                    return userRepository.save(newUser)
                                            .flatMap(user -> createClubAndMentor(user, clubName, mentorName, profileFinal, workStartFinal, workEndFinal, availStepFinal))
                                            .flatMap(savedMentor -> {
                                                session.getAttributes().put("mentor_id", savedMentor.getId());
                                                return auditService.log("MENTOR_REGISTERED", email, savedMentor.getId(),
                                                        "New mentor registered: " + mentorName + " (" + email + "), club: " + clubName)
                                                        .thenReturn("redirect:/setup/finish");
                                            });
                                })
                        );
            });
        });
    }

    private final SecureRandom secureRandom = new SecureRandom();

    private Mono<Mentor> createClubAndMentor(User user, String clubName, String mentorName, String profile,
                                              String workStart, String workEnd, int availStep) {
        Club club = Club.builder().name(clubName).userId(user.getId()).build();
        return clubRepository.save(club).flatMap(savedClub -> {
            Mentor mentor = Mentor.builder()
                    .name(mentorName)
                    .clubId(savedClub.getId())
                    .profile(profile)
                    .workStart(workStart)
                    .workEnd(workEnd)
                    .availStep(availStep)
                    .shareToken(generateShareToken())
                    .build();
            return mentorRepository.save(mentor);
        });
    }

    private String generateShareToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
