package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.Session;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.SessionRepository;
import com.cozy.planner.repositories.TraineeRepository;
import com.cozy.planner.service.EventBroadcastService;
import com.cozy.planner.service.TelegramService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SessionConfirmationController {

    private final SessionRepository sessionRepository;
    private final TraineeRepository traineeRepository;
    private final MentorRepository mentorRepository;
    private final TelegramService telegramService;
    private final EventBroadcastService eventBroadcastService;

    public SessionConfirmationController(SessionRepository sessionRepository,
                                          TraineeRepository traineeRepository,
                                          MentorRepository mentorRepository,
                                          TelegramService telegramService,
                                          EventBroadcastService eventBroadcastService) {
        this.sessionRepository = sessionRepository;
        this.traineeRepository = traineeRepository;
        this.mentorRepository = mentorRepository;
        this.telegramService = telegramService;
        this.eventBroadcastService = eventBroadcastService;
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
                .flatMap(saved -> sessionRepository.findTraineeIdsBySessionId(saved.getId())
                        .next()
                        .flatMap(traineeId -> traineeRepository.findById(traineeId))
                        .flatMap(trainee -> {
                            if (trainee.hasTelegram()) {
                                String message = "📋 <b>Підтвердження сесії</b>\n\n" +
                                        "Тренер створив сесію та чекає на твоє підтвердження:\n" +
                                        "📅 " + saved.getWorkoutDate() + "\n" +
                                        "🕐 " + saved.getStartTime() + " - " + saved.getEndTime() + "\n" +
                                        "🏷 " + saved.getTitle() + "\n\n" +
                                        "Натисни кнопку нижче, щоб підтвердити:";
                                Map<String, Object> keyboard = createConfirmButton();
                                return telegramService.sendMessage(trainee.getTelegramChatId(), message, keyboard)
                                        .thenReturn(saved);
                            }
                            return Mono.just(saved);
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
                    .flatMap(s -> mentorRepository.findById(s.getMentorId())
                            .map(Mentor::getName)
                            .defaultIfEmpty("")
                            .flatMap(mentorName -> sessionRepository.findTraineeIdsBySessionId(s.getId())
                                    .collectList()
                                    .map(tIds -> {
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
                                        m.put("traineeIds", tIds);
                                        return m;
                                    })))
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
                    if (!mentor.hasTelegram()) return Mono.just(session);
                    String message = "📋 <b>Нова сесія від учня</b>\n\n" +
                            "👤 <b>" + trainee.getName() + "</b> створив нову сесію та чекає на підтвердження:\n\n" +
                            "🏷 " + session.getTitle() + "\n" +
                            "📅 " + session.getWorkoutDate() + "\n" +
                            "🕐 " + session.getStartTime() + " - " + session.getEndTime() + "\n\n" +
                            "Оберіть дію:";

                    Map<String, Object> keyboard = createConfirmRejectKeyboard(session.getId());
                    return telegramService.sendMessageToMentor(mentor.getTelegramChatId(), message, keyboard)
                            .thenReturn(session);
                })
                .defaultIfEmpty(session);
    }

    private Mono<Session> notifyOtherParty(Session session, boolean confirmed) {
        if ("TRAINEE".equals(session.getCreatedBy())) {
            return sessionRepository.findTraineeIdsBySessionId(session.getId())
                    .next()
                    .flatMap(traineeId -> traineeRepository.findById(traineeId))
                    .flatMap(trainee -> {
                        if (!trainee.hasTelegram()) return Mono.just(session);
                        String status = confirmed ? "✅ <b>Підтверджено</b>" : "❌ <b>Відхилено</b>";
                        String text = status + "\n\n" +
                                "Тренер " + (confirmed ? "підтвердив" : "відхилив") + " сесію:\n" +
                                "🏷 " + session.getTitle() + "\n" +
                                "📅 " + session.getWorkoutDate() + "\n" +
                                "🕐 " + session.getStartTime() + " - " + session.getEndTime();
                        return telegramService.sendMessage(trainee.getTelegramChatId(), text).thenReturn(session);
                    })
                    .defaultIfEmpty(session);
        }
        return mentorRepository.findById(session.getMentorId())
                .flatMap(mentor -> {
                    if (!mentor.hasTelegram()) return Mono.just(session);
                    String status = confirmed ? "✅ <b>Підтверджено</b>" : "❌ <b>Відхилено</b>";
                    String text = status + "\n\n" +
                            "Учень " + (confirmed ? "підтвердив" : "відхилив") + " сесію:\n" +
                            "🏷 " + session.getTitle() + "\n" +
                            "📅 " + session.getWorkoutDate() + "\n" +
                            "🕐 " + session.getStartTime() + " - " + session.getEndTime();
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
}
