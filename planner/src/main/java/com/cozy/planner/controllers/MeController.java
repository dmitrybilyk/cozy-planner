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
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
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
        String ua = exchange.getRequest().getHeaders().getFirst("User-Agent");
        var remoteAddr = exchange.getRequest().getRemoteAddress();
        String ip = (remoteAddr != null && remoteAddr.getAddress() != null) ? remoteAddr.getAddress().getHostAddress() : "?";
        return exchange.getSession().flatMap(session -> {
            String sessionId = session.getId();
            Object traineeIdFromSession = session.getAttribute("trainee_id");
            String googleSub = session.getAttribute("google_sub");
            log.info("[/me] sid={} ip={} googleSub={} traineeId={} inviteToken={} ua={}",
                    sessionId, ip, googleSub != null ? googleSub.substring(0, Math.min(6, googleSub.length())) + "..." : "null",
                    traineeIdFromSession, inviteToken != null ? "present" : "null", ua);

            if (traineeIdFromSession == null && inviteToken != null && !inviteToken.isBlank()) {
                log.info("[/me] sid={} path=inviteToken token={}", sessionId, inviteToken.substring(0, Math.min(8, inviteToken.length())) + "...");
                return traineeRepository.findByInviteToken(inviteToken)
                        .flatMap(trainee -> {
                            log.info("[/me] sid={} inviteToken resolved traineeId={}", sessionId, trainee.getId());
                            session.getAttributes().put("trainee_id", trainee.getId());
                            return buildTraineeResponse(trainee);
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("[/me] sid={} inviteToken not found — returning empty traineeId", sessionId);
                            Map<String, Object> r = new HashMap<>();
                            r.put("traineeId", null);
                            return Mono.just(r);
                        }));
            }

            if (googleSub != null) {
                log.info("[/me] sid={} path=mentor googleSub={}...", sessionId, googleSub.substring(0, Math.min(6, googleSub.length())));
                return userRepository.findByGoogleSub(googleSub)
                        .flatMap(user -> {
                            log.info("[/me] sid={} user found id={} email={}", sessionId, user.getId(), user.getEmail());
                            return clubRepository.findByUserId(user.getId())
                                    .next()
                                    .flatMap(club -> {
                                        log.info("[/me] sid={} club found id={} name={}", sessionId, club.getId(), club.getName());
                                        return mentorRepository.findAllByClubId(club.getId())
                                                .next()
                                                .map(mentor -> {
                                                    log.info("[/me] sid={} mentor found id={} name={} introSeen={}", sessionId, mentor.getId(), mentor.getName(), mentor.getIntroSeen());
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
                                                    mentorMap.put("timezone", mentor.getTimezone());
                                                    mentorMap.put("availStep", mentor.getAvailStep());
                                                    mentorMap.put("theme", mentor.getTheme() != null ? mentor.getTheme() : "default");
                                                    mentorMap.put("introSeen", Boolean.TRUE.equals(mentor.getIntroSeen()));
                                                    mentorMap.put("sessionReminderEnabled", Boolean.TRUE.equals(mentor.getSessionReminderEnabled()));
                                                    mentorMap.put("shareAvailability", Boolean.TRUE.equals(mentor.getShareAvailability()));
                                                    mentorMap.put("multiLocation", Boolean.TRUE.equals(mentor.getMultiLocation()));
                                                    mentorMap.put("sessionConfirmations", Boolean.TRUE.equals(mentor.getSessionConfirmations()));
                                                    mentorMap.put("telegramIntegration", Boolean.TRUE.equals(mentor.getTelegramIntegration()));
                                                    mentorMap.put("traineeComm", Boolean.TRUE.equals(mentor.getTraineeComm()));
                                                    boolean isDemo = "demo-seed".equals(googleSub);
                                                    mentorMap.put("isDemo", isDemo);
                                                    if (isDemo) {
                                                        mentorMap.put("telegramIntegration", false);
                                                        mentorMap.put("sessionReminderEnabled", false);
                                                        mentorMap.put("shareAvailability", false);
                                                        mentorMap.put("sessionConfirmations", false);
                                                        mentorMap.put("traineeComm", false);
                                                    }
                                                    r.put("mentor", mentorMap);
                                                    r.put("labels", ProfileLabels.getLabels(profile));
                                                    return r;
                                                })
                                                .doOnSuccess(r -> log.info("[/me] sid={} returning mentorId={}", sessionId, r != null ? ((Map<?,?>)r.get("mentor")).get("id") : "null"))
                                                .switchIfEmpty(Mono.defer(() -> {
                                                    log.warn("[/me] sid={} NO mentor found for clubId={} — returning id=-1", sessionId, club.getId());
                                                    return Mono.just(defaultWithUser(user, "Демо"));
                                                }));
                                    })
                                    .switchIfEmpty(Mono.defer(() -> {
                                        log.warn("[/me] sid={} NO club found for userId={} — returning id=-1", sessionId, user.getId());
                                        return Mono.just(defaultWithUser(user, "Демо"));
                                    }));
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("[/me] sid={} NO user found for googleSub — returning id=-1", sessionId);
                            return Mono.just(defaultWithUser(googleSub));
                        }));
            }

            Object traineeId = session.getAttribute("trainee_id");
            if (traineeId instanceof Number) {
                log.info("[/me] sid={} path=trainee traineeId={}", sessionId, traineeId);
                return traineeRepository.findById(((Number) traineeId).longValue())
                        .flatMap(this::buildTraineeResponse)
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("[/me] sid={} traineeId={} not found in DB — returning empty", sessionId, traineeId);
                            Map<String, Object> r = new HashMap<>();
                            r.put("traineeId", null);
                            r.put("mentor", Map.of("id", -1, "name", "Демо"));
                            return Mono.just(r);
                        }));
            }

            log.warn("[/me] sid={} ip={} ANONYMOUS — no googleSub, no traineeId, no inviteToken — returning id=-1 (will redirect to /setup)", sessionId, ip);
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
        r.put("mentorId", trainee.getMentorId());
        r.put("name", trainee.getName());
        r.put("inviteToken", trainee.getInviteToken());
        r.put("timezone", trainee.getTimezone() != null ? trainee.getTimezone() : "Europe/Kiev");

        r.put("sessionReminderEnabled", trainee.isSessionReminderEnabled());

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
                    r.put("mentorAvailStep", mentor.getAvailStep() != null ? mentor.getAvailStep() : 30);
                    r.put("mentorWorkStart", mentor.getWorkStart() != null ? mentor.getWorkStart() : "08:00");
                    r.put("mentorWorkEnd", mentor.getWorkEnd() != null ? mentor.getWorkEnd() : "21:00");
                    r.put("mentorTimezone", mentor.getTimezone() != null ? mentor.getTimezone() : "Europe/Kiev");
                    r.put("mentorShareAvailability", Boolean.TRUE.equals(mentor.getShareAvailability()));
                    r.put("mentorMultiLocation", Boolean.TRUE.equals(mentor.getMultiLocation()));
                    r.put("mentorSessionConfirmations", Boolean.TRUE.equals(mentor.getSessionConfirmations()));
                    r.put("mentorTraineeComm", Boolean.TRUE.equals(mentor.getTraineeComm()));
                    r.put("mentorTelegramIntegration", Boolean.TRUE.equals(mentor.getTelegramIntegration()) && mentor.hasTelegram());
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
                    r.put("mentorWorkStart", "08:00");
                    r.put("mentorWorkEnd", "21:00");
                    r.put("mentorTimezone", "Europe/Kiev");
                    r.put("mentorShareAvailability", false);
                    r.put("mentorMultiLocation", false);
                    r.put("mentorSessionConfirmations", false);
                    r.put("mentorTraineeComm", false);
                    r.put("mentorTelegramIntegration", false);
                    r.put("locations", List.of());
                    return r;
                }));
    }
}
