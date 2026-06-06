package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Location;
import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.Session;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.repositories.LocationRepository;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.SessionRepository;
import com.cozy.planner.repositories.TraineeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dev-only convenience endpoint. Requires X-Dev-Key header matching app.dev-key.
 * Lets Claude Code inspect and create data directly without a browser session.
 */
@RestController
@RequestMapping("/api/v1/dev")
public class DevController {

    @Value("${app.dev-key:}")
    private String devKey;

    private final MentorRepository mentorRepository;
    private final TraineeRepository traineeRepository;
    private final LocationRepository locationRepository;
    private final SessionRepository sessionRepository;

    public DevController(MentorRepository mentorRepository,
                         TraineeRepository traineeRepository,
                         LocationRepository locationRepository,
                         SessionRepository sessionRepository) {
        this.mentorRepository = mentorRepository;
        this.traineeRepository = traineeRepository;
        this.locationRepository = locationRepository;
        this.sessionRepository = sessionRepository;
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private Mono<Void> auth(ServerWebExchange exchange) {
        if (devKey.isBlank()) return Mono.empty(); // open in local dev (no DEV_KEY configured)
        String key = exchange.getRequest().getHeaders().getFirst("X-Dev-Key");
        if (!devKey.equals(key)) {
            return Mono.error(new SecurityException("Invalid or missing X-Dev-Key"));
        }
        return Mono.empty();
    }

    private Mono<Long> resolveMentor(ServerWebExchange exchange, Long mentorIdParam) {
        return auth(exchange).then(mentorIdParam != null
                ? Mono.just(mentorIdParam)
                : mentorRepository.findAll().next()
                        .map(Mentor::getId)
                        .switchIfEmpty(Mono.error(new IllegalStateException("No mentors found"))));
    }

    private static Mono<ResponseEntity<Map<String, Object>>> forbidden(Throwable e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.getMessage());
        return Mono.just(ResponseEntity.status(403).body(body));
    }

    private static ResponseEntity<Map<String, Object>> ok(Map<String, Object> data) {
        return ResponseEntity.ok(data);
    }

    // ─── Overview: everything in one call ──────────────────────────────────────

    @GetMapping("/overview")
    public Mono<ResponseEntity<Map<String, Object>>> overview(
            @RequestParam(required = false) Long mentorId,
            ServerWebExchange exchange) {
        return resolveMentor(exchange, mentorId).flatMap(mid ->
                mentorRepository.findById(mid).flatMap(mentor ->
                        Mono.zip(
                                traineeRepository.findAllByMentorId(mid).collectList(),
                                locationRepository.findAllByMentorId(mid).collectList(),
                                sessionRepository.findAllByMentorIdAndWorkoutDateBetween(
                                        mid, LocalDate.now().minusDays(7), LocalDate.now().plusDays(60)).collectList()
                        ).flatMap(t -> {
                            List<Trainee> trainees = t.getT1();
                            List<Location> locations = t.getT2();
                            List<Session> sessions = t.getT3();

                            // Fetch trainee IDs for each session
                            return Flux.fromIterable(sessions)
                                    .concatMap(s -> sessionRepository.findTraineeIdsBySessionId(s.getId())
                                            .collectList()
                                            .map(ids -> {
                                                s.setTraineeIds(ids);
                                                return s;
                                            }))
                                    .collectList()
                                    .map(enrichedSessions -> {
                                        Map<String, Object> result = new LinkedHashMap<>();

                                        // Mentor
                                        Map<String, Object> m = new LinkedHashMap<>();
                                        m.put("id", mentor.getId());
                                        m.put("name", mentor.getName());
                                        m.put("telegramConnected", mentor.hasTelegram());
                                        m.put("telegramChatId", mentor.getTelegramChatId());
                                        m.put("telegramToken", mentor.getTelegramToken());
                                        result.put("mentor", m);

                                        // Trainees
                                        result.put("trainees", trainees.stream().map(tr -> {
                                            Map<String, Object> r = new LinkedHashMap<>();
                                            r.put("id", tr.getId());
                                            r.put("name", tr.getName());
                                            r.put("description", tr.getDescription());
                                            r.put("telegramConnected", tr.getTelegramChatId() != null && !tr.getTelegramChatId().isBlank());
                                            r.put("telegramChatId", tr.getTelegramChatId());
                                            r.put("telegramUsername", tr.getTelegramUsername());
                                            r.put("inviteToken", tr.getInviteToken());
                                            r.put("weekendReminderEnabled", tr.getWeekendReminderEnabled());
                                            r.put("sessionReminderEnabled", tr.getSessionReminderEnabled());
                                            return r;
                                        }).collect(Collectors.toList()));

                                        // Locations
                                        result.put("locations", locations.stream().map(l -> {
                                            Map<String, Object> r = new LinkedHashMap<>();
                                            r.put("id", l.getId());
                                            r.put("name", l.getName());
                                            r.put("color", l.getColor());
                                            r.put("description", l.getDescription());
                                            return r;
                                        }).collect(Collectors.toList()));

                                        // Sessions
                                        Map<Long, String> traineeNames = trainees.stream()
                                                .collect(Collectors.toMap(Trainee::getId, Trainee::getName));
                                        Map<Long, String> locationNames = locations.stream()
                                                .collect(Collectors.toMap(Location::getId, Location::getName));

                                        result.put("sessions", enrichedSessions.stream().map(s -> {
                                            Map<String, Object> r = new LinkedHashMap<>();
                                            r.put("id", s.getId());
                                            r.put("title", s.getTitle());
                                            r.put("date", s.getWorkoutDate().toString());
                                            r.put("startTime", s.getStartTime().toString());
                                            r.put("endTime", s.getEndTime() != null ? s.getEndTime().toString() : null);
                                            r.put("confirmationStatus", s.getConfirmationStatus());
                                            r.put("location", s.getLocationId() != null ? locationNames.get(s.getLocationId()) : null);
                                            r.put("trainees", s.getTraineeIds() != null
                                                    ? s.getTraineeIds().stream()
                                                            .map(tid -> traineeNames.getOrDefault(tid, "id=" + tid))
                                                            .collect(Collectors.toList())
                                                    : List.of());
                                            return r;
                                        }).collect(Collectors.toList()));

                                        return ok(result);
                                    });
                        })
                )
        ).onErrorResume(SecurityException.class, DevController::forbidden);
    }

    // ─── Connect mentor to Telegram ────────────────────────────────────────────

    @PostMapping("/mentor/telegram")
    public Mono<ResponseEntity<Map<String, Object>>> connectMentorTelegram(
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        Long mentorIdParam = body.get("mentorId") instanceof Number n ? n.longValue() : null;
        String chatId = (String) body.get("chatId");
        String username = (String) body.getOrDefault("username", null);
        if (chatId == null || chatId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().<Map<String, Object>>body(Map.of("error", "chatId is required")));
        }
        return resolveMentor(exchange, mentorIdParam).flatMap(mid ->
                mentorRepository.findById(mid).flatMap(mentor -> {
                    mentor.setTelegramChatId(chatId);
                    if (username != null) mentor.setTelegramUsername(username);
                    return mentorRepository.save(mentor).map(saved -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("mentorId", saved.getId());
                        r.put("name", saved.getName());
                        r.put("telegramChatId", saved.getTelegramChatId());
                        r.put("connected", true);
                        return ok(r);
                    });
                })
        ).onErrorResume(SecurityException.class, DevController::forbidden);
    }

    // ─── Connect trainee to Telegram ───────────────────────────────────────────

    @PostMapping("/trainee/{id}/telegram")
    public Mono<ResponseEntity<Map<String, Object>>> connectTraineeTelegram(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        String chatId = (String) body.get("chatId");
        String username = (String) body.getOrDefault("username", null);
        if (chatId == null || chatId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().<Map<String, Object>>body(Map.of("error", "chatId is required")));
        }
        return auth(exchange).then(
                traineeRepository.findById(id).flatMap(trainee -> {
                    trainee.setTelegramChatId(chatId);
                    if (username != null) trainee.setTelegramUsername(username);
                    return traineeRepository.save(trainee).map(saved -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("traineeId", saved.getId());
                        r.put("name", saved.getName());
                        r.put("telegramChatId", saved.getTelegramChatId());
                        r.put("connected", true);
                        return ok(r);
                    });
                }).switchIfEmpty(Mono.just(ResponseEntity.notFound().<Map<String, Object>>build()))
        ).onErrorResume(SecurityException.class, DevController::forbidden);
    }

    // ─── Create trainee ────────────────────────────────────────────────────────

    @PostMapping("/trainee")
    public Mono<ResponseEntity<Map<String, Object>>> createTrainee(
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        Long mentorIdParam = body.get("mentorId") instanceof Number n ? n.longValue() : null;
        return resolveMentor(exchange, mentorIdParam).flatMap(mid -> {
            Trainee t = new Trainee();
            t.setName((String) body.get("name"));
            t.setDescription((String) body.getOrDefault("description", null));
            t.setMentorId(mid);
            t.setInviteToken(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            return traineeRepository.save(t).map(saved -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", saved.getId());
                r.put("name", saved.getName());
                r.put("inviteToken", saved.getInviteToken());
                return ok(r);
            });
        }).onErrorResume(SecurityException.class, DevController::forbidden);
    }

    // ─── Create location ───────────────────────────────────────────────────────

    @PostMapping("/location")
    public Mono<ResponseEntity<Map<String, Object>>> createLocation(
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        Long mentorIdParam = body.get("mentorId") instanceof Number n ? n.longValue() : null;
        return resolveMentor(exchange, mentorIdParam).flatMap(mid -> {
            Location l = new Location();
            l.setName((String) body.get("name"));
            l.setColor((String) body.getOrDefault("color", "#6366f1"));
            l.setDescription((String) body.getOrDefault("description", null));
            l.setMentorId(mid);
            return locationRepository.save(l).map(saved -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", saved.getId());
                r.put("name", saved.getName());
                r.put("color", saved.getColor());
                return ok(r);
            });
        }).onErrorResume(SecurityException.class, DevController::forbidden);
    }

    // ─── Create session ────────────────────────────────────────────────────────

    @PostMapping("/session")
    public Mono<ResponseEntity<Map<String, Object>>> createSession(
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        Long mentorIdParam = body.get("mentorId") instanceof Number n ? n.longValue() : null;
        return resolveMentor(exchange, mentorIdParam).flatMap(mid -> {
            Session s = new Session();
            s.setTitle((String) body.get("title"));
            s.setWorkoutDate(LocalDate.parse((String) body.get("date")));
            s.setStartTime(LocalTime.parse((String) body.get("startTime")));
            String endTimeRaw = (String) body.getOrDefault("endTime", null);
            s.setEndTime(endTimeRaw != null ? LocalTime.parse(endTimeRaw) : s.getStartTime().plusHours(1));
            s.setMentorId(mid);
            if (body.get("locationId") instanceof Number n) s.setLocationId(n.longValue());
            s.setCreatedBy("DEV");
            s.setConfirmationStatus("NONE");

            List<Long> traineeIds = body.get("traineeIds") instanceof List<?> list
                    ? list.stream().map(o -> ((Number) o).longValue()).collect(Collectors.toList())
                    : List.of();

            return sessionRepository.save(s)
                    .flatMap(saved -> {
                        if (traineeIds.isEmpty()) return Mono.just(saved);
                        return Flux.fromIterable(traineeIds)
                                .concatMap(tid -> sessionRepository.linkTraineeToSession(saved.getId(), tid))
                                .then(Mono.just(saved));
                    })
                    .map(saved -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("id", saved.getId());
                        r.put("title", saved.getTitle());
                        r.put("date", saved.getWorkoutDate().toString());
                        r.put("startTime", saved.getStartTime().toString());
                        r.put("traineeIds", traineeIds);
                        return ok(r);
                    });
        }).onErrorResume(SecurityException.class, DevController::forbidden);
    }
}
