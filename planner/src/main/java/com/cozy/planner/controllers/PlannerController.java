package com.cozy.planner.controllers;

import com.cozy.planner.config.TelegramConfig;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.repositories.LocationRepository;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.TraineeRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class PlannerController {

    private final TraineeRepository traineeRepository;
    private final MentorRepository mentorRepository;
    private final LocationRepository locationRepository;
    private final TelegramConfig telegramConfig;

    public PlannerController(TraineeRepository traineeRepository,
                             MentorRepository mentorRepository,
                             LocationRepository locationRepository,
                             TelegramConfig telegramConfig) {
        this.traineeRepository = traineeRepository;
        this.mentorRepository = mentorRepository;
        this.locationRepository = locationRepository;
        this.telegramConfig = telegramConfig;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/planner";
    }

    @GetMapping("/planner")
    public Mono<String> getPlanner(ServerWebExchange exchange) {
        return exchange.getSession().map(session -> {
            String email = session.getAttribute("user_email");
            if ("dmitry.mediastore@gmail.com".equals(email)) {
                return "redirect:/admin";
            }
            return "mentor-view";
        });
    }

    @GetMapping("/planner23")
    public String getPlanner2() {
        return "mentor-view";
    }

    @GetMapping("/coach/availability")
    public String getCoachAvailability() {
        return "coach-availability";
    }

    @GetMapping("/shared/{shareToken}")
    public Mono<String> getSharedAvailability(@PathVariable String shareToken) {
        return mentorRepository.findByShareToken(shareToken)
                .map(mentor -> "shared-availability")
                .defaultIfEmpty("redirect:/signin");
    }

    @GetMapping("/trainee/{token}")
    public Mono<String> getTraineeInvite(@PathVariable String token, ServerWebExchange exchange, Model model) {
        return exchange.getSession()
                .flatMap(session -> traineeRepository.findByInviteToken(token)
                        .flatMap(trainee -> {
                            session.getAttributes().put("trainee_id", trainee.getId());
                            return mentorRepository.findById(trainee.getMentorId())
                                    .flatMap(mentor -> locationRepository.findAllByMentorId(mentor.getId())
                                            .map(loc -> {
                                                Map<String, Object> locMap = new HashMap<>();
                                                locMap.put("id", loc.getId());
                                                locMap.put("name", loc.getName());
                                                locMap.put("color", loc.getColor());
                                                return locMap;
                                            })
                                            .collectList()
                                            .map(locs -> {
                                                Map<String, Object> traineeData = buildTraineeData(trainee, mentor, locs);
                                                model.addAttribute("traineeData", traineeData);
                                                return "trainee-sessions";
                                            }))
                                    .switchIfEmpty(Mono.fromCallable(() -> {
                                        Map<String, Object> traineeData = buildTraineeData(trainee, null, List.of());
                                        model.addAttribute("traineeData", traineeData);
                                        return "trainee-sessions";
                                    }));
                        })
                        .defaultIfEmpty("redirect:/signin"));
    }

    @GetMapping("/trainee/{token}/sessions")
    public Mono<String> getTraineeSessions(@PathVariable String token, ServerWebExchange exchange, Model model) {
        return exchange.getSession()
                .flatMap(session -> traineeRepository.findByInviteToken(token)
                        .flatMap(trainee -> {
                            session.getAttributes().put("trainee_id", trainee.getId());
                            return mentorRepository.findById(trainee.getMentorId())
                                    .flatMap(mentor -> locationRepository.findAllByMentorId(mentor.getId())
                                            .map(loc -> {
                                                Map<String, Object> locMap = new HashMap<>();
                                                locMap.put("id", loc.getId());
                                                locMap.put("name", loc.getName());
                                                locMap.put("color", loc.getColor());
                                                return locMap;
                                            })
                                            .collectList()
                                            .map(locs -> {
                                                Map<String, Object> traineeData = buildTraineeData(trainee, mentor, locs);
                                                model.addAttribute("traineeData", traineeData);
                                                return "trainee-sessions";
                                            }))
                                    .switchIfEmpty(Mono.fromCallable(() -> {
                                        Map<String, Object> traineeData = buildTraineeData(trainee, null, List.of());
                                        model.addAttribute("traineeData", traineeData);
                                        return "trainee-sessions";
                                    }));
                        })
                        .defaultIfEmpty("redirect:/signin"));
    }

    @GetMapping(value = "/trainee/{token}/manifest.json", produces = "application/manifest+json")
    @ResponseBody
    public Mono<ResponseEntity<Map<String, Object>>> getTraineeManifest(@PathVariable String token) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("name", "Cozy Planner");
        manifest.put("short_name", "Cozy Planner");
        manifest.put("description", "Планувальник тренувань");
        manifest.put("start_url", "/trainee/" + token);
        manifest.put("scope", "/");
        manifest.put("display", "standalone");
        manifest.put("display_override", List.of("standalone", "browser"));
        manifest.put("background_color", "#121212");
        manifest.put("theme_color", "#1a1a1a");
        manifest.put("orientation", "portrait");
        manifest.put("categories", List.of("productivity", "lifestyle"));
        manifest.put("icons", List.of(
            Map.of("src", "/icon.svg", "sizes", "any", "type", "image/svg+xml", "purpose", "any maskable"),
            Map.of("src", "/apple-touch-icon.png", "sizes", "180x180", "type", "image/png"),
            Map.of("src", "/icon-192.png", "sizes", "192x192", "type", "image/png", "purpose", "any"),
            Map.of("src", "/icon-512.png", "sizes", "512x512", "type", "image/png", "purpose", "any maskable")
        ));
        manifest.put("screenshots", List.of());
        manifest.put("prefer_related_applications", false);
        return Mono.just(ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/manifest+json"))
            .body(manifest));
    }

    private Map<String, Object> buildTraineeData(Trainee trainee, com.cozy.planner.model.entity.Mentor mentor, List<Map<String, Object>> locations) {
        Map<String, Object> r = new HashMap<>();
        r.put("traineeId", trainee.getId());
        r.put("athleteId", trainee.getId());
        r.put("mentorId", trainee.getMentorId());
        r.put("name", trainee.getName());
        r.put("inviteToken", trainee.getInviteToken());
        r.put("timezone", trainee.getTimezone() != null ? trainee.getTimezone() : "Europe/Kiev");

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

        if (mentor != null) {
            r.put("mentorName", mentor.getName());
            r.put("mentorTelegramConnected", mentor.hasTelegram());
            r.put("mentorShareToken", mentor.getShareToken());
            r.put("mentorProfile", mentor.getProfile() != null ? mentor.getProfile() : "sport");
            r.put("mentorTimezone", mentor.getTimezone() != null ? mentor.getTimezone() : "Europe/Kiev");
            r.put("mentorAvailStep", mentor.getAvailStep() != null ? mentor.getAvailStep() : 30);
            r.put("mentorWorkStart", mentor.getWorkStart() != null ? mentor.getWorkStart() : "06:00");
            r.put("mentorWorkEnd", mentor.getWorkEnd() != null ? mentor.getWorkEnd() : "22:00");
        } else {
            r.put("mentorName", null);
            r.put("mentorTelegramConnected", false);
            r.put("mentorShareToken", null);
            r.put("mentorProfile", "sport");
            r.put("mentorTimezone", "Europe/Kiev");
            r.put("mentorAvailStep", 30);
            r.put("mentorWorkStart", "06:00");
            r.put("mentorWorkEnd", "22:00");
        }
        r.put("locations", locations);
        return r;
    }
}
