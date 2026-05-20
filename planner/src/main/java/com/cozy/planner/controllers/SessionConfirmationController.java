package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Location;
import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.Notification;
import com.cozy.planner.model.entity.Session;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.repositories.LocationRepository;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.NotificationRepository;
import com.cozy.planner.repositories.SessionRepository;
import com.cozy.planner.repositories.TraineeRepository;
import com.cozy.planner.service.EventBroadcastService;
import com.cozy.planner.service.ProfileLabels;
import com.cozy.planner.service.PushService;
import com.cozy.planner.service.TelegramService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SessionConfirmationController {

    private final SessionRepository sessionRepository;
    private final TraineeRepository traineeRepository;
    private final MentorRepository mentorRepository;
    private final LocationRepository locationRepository;
    private final TelegramService telegramService;
    private final EventBroadcastService eventBroadcastService;
    private final NotificationRepository notificationRepository;
    private final PushService pushService;

    public SessionConfirmationController(SessionRepository sessionRepository,
                                          TraineeRepository traineeRepository,
                                          MentorRepository mentorRepository,
                                          LocationRepository locationRepository,
                                          TelegramService telegramService,
                                          EventBroadcastService eventBroadcastService,
                                          NotificationRepository notificationRepository,
                                          PushService pushService) {
        this.sessionRepository = sessionRepository;
        this.traineeRepository = traineeRepository;
        this.mentorRepository = mentorRepository;
        this.locationRepository = locationRepository;
        this.telegramService = telegramService;
        this.eventBroadcastService = eventBroadcastService;
        this.notificationRepository = notificationRepository;
        this.pushService = pushService;
    }

    @PostMapping("/sessions/{sessionId}/confirm")
    public Mono<ResponseEntity<Map<String, Object>>> confirmSession(@PathVariable Long sessionId) {
        return sessionRepository.findById(sessionId)
                .flatMap(session -> {
                    session.setConfirmationStatus("CONFIRMED");
                    return sessionRepository.save(session);
                })
                .flatMap(saved -> notifyOtherParty(saved, true))
                .doOnSuccess(v -> eventBroadcastService.broadcast("session_changed"))
                .map(saved -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("confirmationStatus", "CONFIRMED");
                    return ResponseEntity.ok(result);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/sessions/{sessionId}/reject")
    public Mono<ResponseEntity<Map<String, Object>>> rejectSession(@PathVariable Long sessionId) {
        return sessionRepository.findById(sessionId)
                .flatMap(session -> {
                    session.setConfirmationStatus("REJECTED");
                    return sessionRepository.save(session);
                })
                .flatMap(saved -> notifyOtherParty(saved, false))
                .doOnSuccess(v -> eventBroadcastService.broadcast("session_changed"))
                .map(saved -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("confirmationStatus", "REJECTED");
                    return ResponseEntity.ok(result);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/trainee/sessions/{sessionId}/confirm")
    public Mono<ResponseEntity<Map<String, Object>>> traineeConfirmSession(@PathVariable Long sessionId,
                                                                             ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            Object traineeIdObj = session.getAttribute("trainee_id");
            if (!(traineeIdObj instanceof Number)) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("reason", "Not authenticated as trainee");
                return Mono.just(ResponseEntity.badRequest().body(err));
            }
            return sessionRepository.findById(sessionId)
                    .flatMap(s -> sessionRepository.findTraineeIdsBySessionId(s.getId())
                            .any(tId -> tId.equals(((Number) traineeIdObj).longValue()))
                            .flatMap(isTrainee -> {
                                if (!isTrainee) {
                                    Map<String, Object> err = new HashMap<>();
                                    err.put("success", false);
                                    err.put("reason", "Not your session");
                                    return Mono.just(ResponseEntity.badRequest().body(err));
                                }
                                s.setConfirmationStatus("CONFIRMED");
                                return sessionRepository.save(s)
                                        .flatMap(saved -> notifyOtherParty(saved, true))
                                        .doOnSuccess(v -> eventBroadcastService.broadcast("session_changed"))
                                        .map(saved -> {
                                            Map<String, Object> result = new HashMap<>();
                                            result.put("success", true);
                                            result.put("confirmationStatus", "CONFIRMED");
                                            return ResponseEntity.ok(result);
                                        });
                            }))
                    .defaultIfEmpty(ResponseEntity.notFound().build());
        });
    }

    @PostMapping("/trainee/sessions/{sessionId}/reject")
    public Mono<ResponseEntity<Map<String, Object>>> traineeRejectSession(@PathVariable Long sessionId,
                                                                            ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            Object traineeIdObj = session.getAttribute("trainee_id");
            if (!(traineeIdObj instanceof Number)) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("reason", "Not authenticated as trainee");
                return Mono.just(ResponseEntity.badRequest().body(err));
            }
            return sessionRepository.findById(sessionId)
                    .flatMap(s -> sessionRepository.findTraineeIdsBySessionId(s.getId())
                            .any(tId -> tId.equals(((Number) traineeIdObj).longValue()))
                            .flatMap(isTrainee -> {
                                if (!isTrainee) {
                                    Map<String, Object> err = new HashMap<>();
                                    err.put("success", false);
                                    err.put("reason", "Not your session");
                                    return Mono.just(ResponseEntity.badRequest().body(err));
                                }
                                s.setConfirmationStatus("REJECTED");
                                return sessionRepository.save(s)
                                        .flatMap(saved -> notifyOtherParty(saved, false))
                                        .doOnSuccess(v -> eventBroadcastService.broadcast("session_changed"))
                                        .map(saved -> {
                                            Map<String, Object> result = new HashMap<>();
                                            result.put("success", true);
                                            result.put("confirmationStatus", "REJECTED");
                                            return ResponseEntity.ok(result);
                                        });
                            }))
                    .defaultIfEmpty(ResponseEntity.notFound().build());
        });
    }

    @PostMapping("/sessions/{sessionId}/request-trainee-confirmation")
    public Mono<ResponseEntity<Map<String, Object>>> requestTraineeConfirmation(@PathVariable Long sessionId) {
        return sessionRepository.findById(sessionId)
                .flatMap(session -> {
                    session.setConfirmationStatus("PENDING");
                    return sessionRepository.save(session);
                })
                .flatMap(saved -> mentorRepository.findById(saved.getMentorId())
                        .defaultIfEmpty(Mentor.builder().profile("sport").build())
                        .flatMap(mentor -> {
                            String profile = getProfile(mentor);
                            String sessionLabel = ProfileLabels.get(profile, "session");
                            String mentorLabel = ProfileLabels.get(profile, "mentor");
                            return sessionRepository.findTraineeIdsBySessionId(saved.getId())
                                    .next()
                                    .flatMap(traineeId -> traineeRepository.findById(traineeId))
                                    .flatMap(trainee -> {
                                        String nTitle = mentorLabel + " створив " + sessionLabel.toLowerCase();
                                        String nMessage = saved.getTitle() + " — " + saved.getWorkoutDate() + " " + saved.getStartTime();
                                        return createAndBroadcastNotification(trainee.getId(), null, nTitle, nMessage, "SESSION_CREATED", saved.getId())
                                                .then(Mono.defer(() -> {
                                                    if (trainee.hasTelegram()) {
                                                        String tmpl = String.format(
                                                                ProfileLabels.get(profile, "telegram_session_confirmation_request"),
                                                                saved.getWorkoutDate().toString(),
                                                                saved.getStartTime().toString(),
                                                                saved.getEndTime() != null ? saved.getEndTime().toString() : "",
                                                                saved.getTitle() != null ? saved.getTitle() : "");
                                                        Map<String, Object> keyboard = createConfirmButton();
                                                        return telegramService.sendMessage(trainee.getTelegramChatId(), tmpl, keyboard);
                                                    }
                                                    return Mono.just(true);
                                                })).thenReturn(saved);
                                    });
                        })
                )
                .map(saved -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("confirmationStatus", "PENDING");
                    return ResponseEntity.ok(result);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/trainee/sessions/{sessionId}/request-coach-confirmation")
    public Mono<ResponseEntity<Map<String, Object>>> requestCoachConfirmation(@PathVariable Long sessionId,
                                                                               ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            Object traineeIdObj = session.getAttribute("trainee_id");
            if (!(traineeIdObj instanceof Number)) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("reason", "Not authenticated as trainee");
                return Mono.just(ResponseEntity.badRequest().body(err));
            }
            Long traineeId = ((Number) traineeIdObj).longValue();
            return sessionRepository.findById(sessionId)
                    .flatMap(s -> sessionRepository.findTraineeIdsBySessionId(s.getId())
                            .any(tId -> tId.equals(traineeId))
                            .flatMap(isTrainee -> {
                                if (!isTrainee) {
                                    Map<String, Object> err = new HashMap<>();
                                    err.put("success", false);
                                    err.put("reason", "Not your session");
                                    return Mono.just(ResponseEntity.badRequest().body(err));
                                }
                                s.setConfirmationStatus("PENDING");
                                return sessionRepository.save(s)
                                        .flatMap(saved -> traineeRepository.findById(traineeId)
                                                .flatMap(trainee -> notifyCoachAboutNewSession(saved, trainee))
                                                .switchIfEmpty(Mono.just(saved)))
                                        .doOnSuccess(v -> eventBroadcastService.broadcast("session_changed"))
                                        .map(saved -> {
                                            Map<String, Object> result = new HashMap<>();
                                            result.put("success", true);
                                            result.put("confirmationStatus", "PENDING");
                                            return ResponseEntity.ok(result);
                                        });
                            }))
                    .defaultIfEmpty(ResponseEntity.notFound().build());
        });
    }

    @GetMapping("/trainee/sessions")
    public Mono<ResponseEntity<List<Map<String, Object>>>> getTraineeSessions(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            Object traineeIdObj = session.getAttribute("trainee_id");
            if (!(traineeIdObj instanceof Number)) {
                return Mono.just(ResponseEntity.badRequest().build());
            }
            Long traineeId = ((Number) traineeIdObj).longValue();
            return sessionRepository.findAllByTraineeId(traineeId)
                    .flatMap(s -> {
                        Mono<String> mentorNameMono = mentorRepository.findById(s.getMentorId())
                                .map(Mentor::getName)
                                .defaultIfEmpty("");
                        Mono<Map<String, Object>> locationMono;
                        if (s.getLocationId() != null) {
                            locationMono = locationRepository.findById(s.getLocationId())
                                    .map(loc -> {
                                        Map<String, Object> locMap = new HashMap<>();
                                        locMap.put("id", loc.getId());
                                        locMap.put("name", loc.getName());
                                        locMap.put("color", loc.getColor());
                                        return locMap;
                                    })
                                    .defaultIfEmpty(Map.of());
                        } else {
                            locationMono = Mono.just(Map.of());
                        }
                        Mono<Map<Long, String>> traineeNamesMono = sessionRepository.findTraineeIdsBySessionId(s.getId())
                                .collectList()
                                .flatMap(tIds -> {
                                    if (tIds.isEmpty()) return Mono.just(Map.<Long, String>of());
                                    return Flux.fromIterable(tIds)
                                            .flatMap(id -> traineeRepository.findById(id)
                                                    .map(t -> Map.entry(id, t.getName()))
                                                    .defaultIfEmpty(Map.entry(id, "?")))
                                            .collectMap(Map.Entry::getKey, Map.Entry::getValue);
                                });
                        return Mono.zip(mentorNameMono, locationMono, traineeNamesMono)
                                .map(tuple -> {
                                    String mentorName = tuple.getT1();
                                    Map<String, Object> location = tuple.getT2();
                                    Map<Long, String> traineeNames = tuple.getT3();
                                    Map<String, Object> m = new HashMap<>();
                                    m.put("id", s.getId());
                                    m.put("title", s.getTitle());
                                    m.put("description", s.getDescription());
                                    m.put("date", s.getWorkoutDate().toString());
                                    m.put("time", s.getStartTime().toString());
                                    m.put("endTime", s.getEndTime() != null ? s.getEndTime().toString() : null);
                                    m.put("confirmationStatus", s.getConfirmationStatus());
                                    m.put("createdBy", s.getCreatedBy());
                                    m.put("mentorName", mentorName);
                                    m.put("color", "#3b82f6");
                                    m.put("traineeIds", new ArrayList<>(traineeNames.keySet()));
                                    m.put("traineeNames", traineeNames);
                                    m.put("location", location);
                                    return m;
                                });
                    })
                    .collectList()
                    .map(ResponseEntity::ok);
        });
    }

    @PostMapping("/trainee/sessions")
    public Mono<ResponseEntity<Map<String, Object>>> createTraineeSession(@RequestBody Map<String, Object> body,
                                                                           ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            Object traineeIdObj = session.getAttribute("trainee_id");
            if (!(traineeIdObj instanceof Number)) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("reason", "Not authenticated as trainee");
                return Mono.just(ResponseEntity.badRequest().body(err));
            }
            Long traineeId = ((Number) traineeIdObj).longValue();
            return traineeRepository.findById(traineeId)
                    .flatMap(trainee -> {
                        String title = (String) body.get("title");
                        String description = (String) body.get("description");
                        String dateStr = (String) body.get("date");
                        String timeStr = (String) body.get("time");
                        String endTimeStr = (String) body.get("endTime");
                        Long locationId = null;
                        Object locObj = body.get("locationId");
                        if (locObj instanceof Number) {
                            locationId = ((Number) locObj).longValue();
                        }

                        if (title == null || dateStr == null || timeStr == null) {
                            Map<String, Object> err = new HashMap<>();
                            err.put("success", false);
                            err.put("reason", "Missing required fields");
                            return Mono.just(ResponseEntity.badRequest().body(err));
                        }

                        Session newSession = Session.builder()
                                .title(title)
                                .description(description)
                                .workoutDate(LocalDate.parse(dateStr))
                                .startTime(LocalTime.parse(timeStr))
                                .endTime(endTimeStr != null ? LocalTime.parse(endTimeStr) : null)
                                .mentorId(trainee.getMentorId())
                                .locationId(locationId)
                                .createdBy("TRAINEE")
                                .confirmationStatus("PENDING")
                                .build();

                        return sessionRepository.save(newSession)
                                .flatMap(saved -> {
                                    List<Long> tIds = List.of(traineeId);
                                    return Flux.fromIterable(tIds)
                                            .flatMap(id -> sessionRepository.linkTraineeToSession(saved.getId(), id))
                                            .then(Mono.just(saved));
                                })
                                .flatMap(saved -> notifyCoachAboutNewSession(saved, trainee))
                                .doOnSuccess(s -> eventBroadcastService.broadcast("session_changed"))
                                .map(saved -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("success", true);
                                    result.put("id", saved.getId());
                                    result.put("confirmationStatus", "PENDING");
                                    return ResponseEntity.ok(result);
                                });
                    })
                    .defaultIfEmpty(ResponseEntity.badRequest().build());
        });
    }

    private Mono<Session> notifyCoachAboutNewSession(Session session, Trainee trainee) {
        return mentorRepository.findById(trainee.getMentorId())
                .flatMap(mentor -> {
                    String profile = getProfile(mentor);
                    String nTitle = trainee.getName() + " створив " + ProfileLabels.get(profile, "session").toLowerCase();
                    String nMessage = session.getTitle() + " — " + session.getWorkoutDate() + " " + session.getStartTime();
                    createAndBroadcastNotification(null, mentor.getId(), nTitle, nMessage, "SESSION_CREATED", session.getId()).subscribe();

                    if (!mentor.hasTelegram()) return Mono.just(session);
                    String message = String.format(
                            ProfileLabels.get(profile, "telegram_new_session_from_trainee"),
                            trainee.getName(),
                            session.getTitle() != null ? session.getTitle() : "",
                            session.getWorkoutDate().toString(),
                            session.getStartTime().toString(),
                            session.getEndTime() != null ? session.getEndTime().toString() : "");

                    Map<String, Object> keyboard = createConfirmRejectKeyboard(session.getId());
                    return telegramService.sendMessageToMentor(mentor.getTelegramChatId(), message, keyboard)
                            .thenReturn(session);
                })
                .defaultIfEmpty(session);
    }

    private Mono<Session> notifyOtherParty(Session session, boolean confirmed) {
        String status = confirmed ? "✅ <b>Підтверджено</b>" : "❌ <b>Відхилено</b>";
        String action = confirmed ? "telegram_session_confirmed_action" : "telegram_session_rejected_action";
        String verb = confirmed ? "підтвердив" : "відхилив";
        if ("TRAINEE".equals(session.getCreatedBy())) {
            return sessionRepository.findTraineeIdsBySessionId(session.getId())
                    .next()
                    .flatMap(traineeId -> traineeRepository.findById(traineeId))
                    .flatMap(trainee -> {
                        return mentorRepository.findById(session.getMentorId())
                                .defaultIfEmpty(Mentor.builder().profile("sport").build())
                                .flatMap(mentor -> {
                                    String profile = getProfile(mentor);
                                    String mentorLabel = ProfileLabels.get(profile, "mentor");
                                    String sessionLabel = ProfileLabels.get(profile, "session").toLowerCase();
                                    String nTitle = mentorLabel + " " + verb + " " + sessionLabel;
                                    String nMessage = session.getTitle() + " — " + session.getWorkoutDate() + " " + session.getStartTime();
                                    createAndBroadcastNotification(trainee.getId(), null, nTitle, nMessage, confirmed ? "SESSION_CONFIRMED" : "SESSION_REJECTED", session.getId()).subscribe();
                                    if (!trainee.hasTelegram()) return Mono.just(session);
                                    String text = String.format(
                                            ProfileLabels.get(profile, "telegram_session_decision_mentor"),
                                            status,
                                            ProfileLabels.get(profile, action),
                                            session.getTitle() != null ? session.getTitle() : "",
                                            session.getWorkoutDate().toString(),
                                            session.getStartTime().toString(),
                                            session.getEndTime() != null ? session.getEndTime().toString() : "");
                                    return telegramService.sendMessage(trainee.getTelegramChatId(), text).thenReturn(session);
                                });
                    })
                    .defaultIfEmpty(session);
        }
        return mentorRepository.findById(session.getMentorId())
                .flatMap(mentor -> {
                    String profile = getProfile(mentor);
                    String traineeLabel = ProfileLabels.get(profile, "trainee");
                    String sessionLabel = ProfileLabels.get(profile, "session").toLowerCase();
                    String nTitle = traineeLabel + " " + verb + " " + sessionLabel;
                    String nMessage = session.getTitle() + " — " + session.getWorkoutDate() + " " + session.getStartTime();
                    createAndBroadcastNotification(null, mentor.getId(), nTitle, nMessage, confirmed ? "SESSION_CONFIRMED" : "SESSION_REJECTED", session.getId()).subscribe();
                    if (!mentor.hasTelegram()) return Mono.just(session);
                    String text = String.format(
                            ProfileLabels.get(profile, "telegram_session_decision_trainee"),
                            status,
                            ProfileLabels.get(profile, action),
                            session.getTitle() != null ? session.getTitle() : "",
                            session.getWorkoutDate().toString(),
                            session.getStartTime().toString(),
                            session.getEndTime() != null ? session.getEndTime().toString() : "");
                    return telegramService.sendMessageToMentor(mentor.getTelegramChatId(), text).thenReturn(session);
                })
                .defaultIfEmpty(session);
    }

    @PostMapping("/telegram/callback/confirm-session")
    public Mono<ResponseEntity<String>> handleConfirmCallback(@RequestBody Map<String, Object> body) {
        Long sessionId = body.get("sessionId") instanceof Number ? ((Number) body.get("sessionId")).longValue() : null;
        boolean confirmed = "true".equals(String.valueOf(body.get("confirmed")));
        if (sessionId == null) return Mono.just(ResponseEntity.badRequest().build());
        return (confirmed ? confirmSession(sessionId) : rejectSession(sessionId))
                .thenReturn(ResponseEntity.ok("OK"));
    }

    private Map<String, Object> createConfirmRejectKeyboard(Long sessionId) {
        Map<String, Object> confirmBtn = new HashMap<>();
        confirmBtn.put("text", "✅ Підтвердити");
        confirmBtn.put("callback_data", "confirm_session:" + sessionId);

        Map<String, Object> rejectBtn = new HashMap<>();
        rejectBtn.put("text", "❌ Відхилити");
        rejectBtn.put("callback_data", "reject_session:" + sessionId);

        Map<String, Object> keyboard = new HashMap<>();
        keyboard.put("inline_keyboard", List.of(List.of(confirmBtn, rejectBtn)));
        return keyboard;
    }

    private Map<String, Object> createConfirmButton() {
        Map<String, Object> btn = new HashMap<>();
        btn.put("text", "✅ Підтвердити");
        btn.put("callback_data", "trainee_confirm_session");

        Map<String, Object> keyboard = new HashMap<>();
        keyboard.put("inline_keyboard", List.of(List.of(btn)));
        return keyboard;
    }

    private Mono<Void> createAndBroadcastNotification(Long traineeId, Long mentorId, String title, String message, String type, Long sessionId) {
        Notification n = Notification.builder()
                .traineeId(traineeId)
                .mentorId(mentorId)
                .title(title)
                .message(message)
                .type(type)
                .sessionId(sessionId)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        return notificationRepository.save(n)
                .flatMap(saved -> {
                    Map<String, Object> evt = new HashMap<>();
                    evt.put("type", "notification");
                    evt.put("id", saved.getId());
                    evt.put("traineeId", saved.getTraineeId());
                    evt.put("mentorId", saved.getMentorId());
                    evt.put("title", saved.getTitle());
                    evt.put("message", saved.getMessage());
                    evt.put("notificationType", saved.getType());
                    evt.put("sessionId", saved.getSessionId());
                    evt.put("isRead", saved.getIsRead());
                    evt.put("createdAt", saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : null);
                    eventBroadcastService.broadcastJson(evt);
                    if (traineeId != null) pushService.sendToTrainee(traineeId, title, message).subscribe();
                    if (mentorId != null) pushService.sendToMentor(mentorId, title, message).subscribe();
                    return Mono.empty();
                });
    }

    private String getProfile(Mentor mentor) {
        return mentor != null && mentor.getProfile() != null ? mentor.getProfile() : "sport";
    }
}
