package com.cozy.planner.controllers;

import com.cozy.planner.config.TelegramConfig;
import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.repositories.ClubRepository;
import com.cozy.planner.repositories.LocationRepository;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.TraineeRepository;
import com.cozy.planner.repositories.UserRepository;
import com.cozy.planner.service.ProfileLabels;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class MeController {

    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final MentorRepository mentorRepository;
    private final TraineeRepository traineeRepository;
    private final LocationRepository locationRepository;
    private final TelegramConfig telegramConfig;

    public MeController(UserRepository userRepository,
                         ClubRepository clubRepository,
                         MentorRepository mentorRepository,
                         TraineeRepository traineeRepository,
                         LocationRepository locationRepository,
                         TelegramConfig telegramConfig) {
        this.userRepository = userRepository;
        this.clubRepository = clubRepository;
        this.mentorRepository = mentorRepository;
        this.traineeRepository = traineeRepository;
        this.locationRepository = locationRepository;
        this.telegramConfig = telegramConfig;
    }

    @GetMapping("/api/v1/me")
    public Mono<Map<String, Object>> me(ServerWebExchange exchange,
                                         @RequestParam(name = "inviteToken", required = false) String inviteToken) {
        return exchange.getSession().flatMap(session -> {
            Object traineeIdFromSession = session.getAttribute("trainee_id");

            if (traineeIdFromSession == null && inviteToken != null && !inviteToken.isBlank()) {
                return traineeRepository.findByInviteToken(inviteToken)
                        .flatMap(trainee -> {
                            session.getAttributes().put("trainee_id", trainee.getId());
                            return buildTraineeResponse(trainee);
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            Map<String, Object> r = new HashMap<>();
                            r.put("traineeId", null);
                            return Mono.just(r);
                        }));
            }

            String googleSub = session.getAttribute("google_sub");
            if (googleSub != null) {
                return userRepository.findByGoogleSub(googleSub)
                        .flatMap(user -> clubRepository.findByUserId(user.getId())
                                .next()
                                .flatMap(club -> mentorRepository.findAllByClubId(club.getId())
                                        .next()
                                        .map(mentor -> {
                                            session.getAttributes().put("mentor_id", mentor.getId());
                                            String profile = mentor.getProfile() != null ? mentor.getProfile() : "sport";
                                            Map<String, Object> r = new HashMap<>();
                                            r.put("user", Map.of("id", user.getId(), "email", user.getEmail(), "name", user.getName()));
                                            r.put("club", Map.of("id", club.getId(), "name", club.getName()));
                                            r.put("coach", Map.of("id", mentor.getId(), "name", mentor.getName()));
                                            Map<String, Object> mentorMap = new HashMap<>();
                                            mentorMap.put("id", mentor.getId());
                                            mentorMap.put("name", mentor.getName());
                                            mentorMap.put("profile", profile);
                                            mentorMap.put("shareToken", mentor.getShareToken());
                                            mentorMap.put("workStart", mentor.getWorkStart());
                                            mentorMap.put("workEnd", mentor.getWorkEnd());
                                            mentorMap.put("photoUrl", mentor.getPhotoUrl());
                                            r.put("mentor", mentorMap);
                                            r.put("labels", ProfileLabels.getLabels(profile));
                                            return r;
                                        })
                                )
                                .defaultIfEmpty(defaultWithUser(user, "Демо"))
                        )
                        .defaultIfEmpty(defaultWithUser(googleSub));
            }

            Object traineeId = session.getAttribute("trainee_id");
            if (traineeId instanceof Number) {
                return traineeRepository.findById(((Number) traineeId).longValue())
                        .flatMap(this::buildTraineeResponse);
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

    private Mono<Map<String, Object>> buildTraineeResponse(Trainee trainee) {
        Map<String, Object> r = new HashMap<>();
        r.put("traineeId", trainee.getId());
        r.put("athleteId", trainee.getId());
        r.put("name", trainee.getName());
        r.put("inviteToken", trainee.getInviteToken());

        boolean tgEnabled = telegramConfig.isEnabled()
                && telegramConfig.getBotToken() != null
                && !telegramConfig.getBotToken().isBlank();
        r.put("telegramEnabled", tgEnabled);
        r.put("telegramConnected", trainee.hasTelegram());
        r.put("telegramUsername", trainee.getTelegramUsername());

        if (tgEnabled && trainee.getInviteToken() != null
                && !trainee.getInviteToken().isBlank()
                && telegramConfig.getBotUsername() != null
                && !telegramConfig.getBotUsername().isBlank()) {
            r.put("telegramConnectLink",
                    "https://t.me/" + telegramConfig.getBotUsername()
                    + "?start=" + trainee.getInviteToken());
        } else {
            r.put("telegramConnectLink", null);
        }

        return mentorRepository.findById(trainee.getMentorId())
                .flatMap(mentor -> {
                    r.put("mentorName", mentor.getName());
                    r.put("mentorPhotoUrl", mentor.getPhotoUrl());
                    r.put("mentorTelegramConnected", mentor.hasTelegram());
                    r.put("mentorShareToken", mentor.getShareToken());
                    r.put("mentorProfile", mentor.getProfile() != null ? mentor.getProfile() : "sport");
                    r.put("mentorWorkStart", mentor.getWorkStart() != null ? mentor.getWorkStart() : "06:00");
                    r.put("mentorWorkEnd", mentor.getWorkEnd() != null ? mentor.getWorkEnd() : "22:00");
                    return locationRepository.findAllByMentorId(mentor.getId())
                            .map(loc -> Map.of("id", loc.getId(), "name", loc.getName(), "color", loc.getColor()))
                            .collectList()
                            .map(locs -> {
                                r.put("locations", locs);
                                return r;
                            });
                })
                .switchIfEmpty(Mono.fromCallable(() -> {
                    r.put("mentorName", null);
                    r.put("mentorPhotoUrl", null);
                    r.put("mentorTelegramConnected", false);
                    r.put("mentorShareToken", null);
                    r.put("mentorProfile", "sport");
                    r.put("mentorWorkStart", "06:00");
                    r.put("mentorWorkEnd", "22:00");
                    r.put("locations", List.of());
                    return r;
                }));
    }
}
