package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Location;
import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.MentorDayOff;
import com.cozy.planner.model.entity.Notification;
import com.cozy.planner.model.entity.Session;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.repositories.LocationRepository;
import com.cozy.planner.repositories.MentorDayOffRepository;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.NotificationRepository;
import com.cozy.planner.repositories.SessionRepository;
import com.cozy.planner.repositories.TraineeRepository;
import com.cozy.planner.service.EventBroadcastService;
import com.cozy.planner.service.ProfileLabels;
import com.cozy.planner.service.PushService;
import com.cozy.planner.service.NotificationService;
import com.cozy.planner.service.SearchEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class SessionConfirmationController {

    private final SessionRepository sessionRepository;
    private final TraineeRepository traineeRepository;
    private final MentorRepository mentorRepository;
    private final LocationRepository locationRepository;
    private final NotificationService notificationService;
    private final EventBroadcastService eventBroadcastService;
    private final NotificationRepository notificationRepository;
    private final PushService pushService;
    private final MentorDayOffRepository mentorDayOffRepository;
    private final SearchEventPublisher searchEventPublisher;

    public SessionConfirmationController(SessionRepository sessionRepository,
                                            TraineeRepository traineeRepository,
                                            MentorRepository mentorRepository,
                                            LocationRepository locationRepository,
                                            NotificationService notificationService,
                                            EventBroadcastService eventBroadcastService,
                                            NotificationRepository notificationRepository,
                                            PushService pushService,
                                            MentorDayOffRepository mentorDayOffRepository,
                                            SearchEventPublisher searchEventPublisher) {
        this.sessionRepository = sessionRepository;
        this.traineeRepository = traineeRepository;
        this.mentorRepository = mentorRepository;
        this.locationRepository = locationRepository;
        this.notificationService = notificationService;
        this.eventBroadcastService = eventBroadcastService;
        this.notificationRepository = notificationRepository;
        this.pushService = pushService;
        this.mentorDayOffRepository = mentorDayOffRepository;
        this.searchEventPublisher = searchEventPublisher;
    }

    @PostMapping("/sessions/{sessionId}/confirm")
    public Mono<ResponseEntity<Map<String, Object>>> confirmSession(@PathVariable Long sessionId) {
        return sessionRepository.findById(sessionId)
                .flatMap(session -> {
                    session.setConfirmationStatus("CONFIRMED");
                    session.setConfirmedTraineeIds("");
                    return sessionRepository.save(session)
                            .flatMap(saved -> searchEventPublisher.publishSessionEvent("UPDATED", saved).thenReturn(saved));
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
                    return sessionRepository.save(session)
                            .flatMap(saved -> searchEventPublisher.publishSessionEvent("UPDATED", saved).thenReturn(saved));
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
            Long currentTraineeId = ((Number) traineeIdObj).longValue();
            return sessionRepository.findById(sessionId)
                    .flatMap(s -> sessionRepository.findTraineeIdsBySessionId(s.getId())
                            .collectList()
                            .flatMap(allTraineeIds -> {
                                if (!allTraineeIds.contains(currentTraineeId)) {
                                    Map<String, Object> err = new HashMap<>();
                                    err.put("success", false);
                                    err.put("reason", "Not your session");
                                    return Mono.just(ResponseEntity.badRequest().body(err));
                                }
                                return traineeRepository.findById(currentTraineeId)
                                        .map(Trainee::getName)
                                        .defaultIfEmpty("")
                                        .flatMap(traineeName -> addConfirmedTrainee(s, currentTraineeId, allTraineeIds)
                                                .flatMap(updated -> notifyTraineeAction(updated, traineeName, true, allTraineeIds))
                                                .doOnSuccess(v -> eventBroadcastService.broadcast("session_changed"))
                                                .map(saved -> {
                                                    Map<String, Object> result = new HashMap<>();
                                                    result.put("success", true);
                                                    result.put("confirmationStatus", saved.getConfirmationStatus());
                                                    result.put("confirmedTraineeIds", parseCommaIds(saved.getConfirmedTraineeIds()));
                                                    result.put("rejectedTraineeIds", parseCommaIds(saved.getRejectedTraineeIds()));
                                                    return ResponseEntity.ok(result);
                                                }));
                            }))
                    .defaultIfEmpty(ResponseEntity.notFound().build());
        });
    }

    private List<Long> parseCommaIds(String ids) {
        if (ids == null || ids.isBlank()) return List.of();
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
    }

    private Mono<Session> addConfirmedTrainee(Session s, Long traineeId, List<Long> allTraineeIds) {
        List<Long> confirmed = parseCommaIds(s.getConfirmedTraineeIds());
        if (!confirmed.contains(traineeId)) {
            confirmed = new ArrayList<>(confirmed);
            confirmed.add(traineeId);
        }
        s.setConfirmedTraineeIds(confirmed.stream().map(String::valueOf).collect(Collectors.joining(",")));
        if (allTraineeIds.stream().allMatch(confirmed::contains)) {
            s.setConfirmationStatus("CONFIRMED");
        } else {
            s.setConfirmationStatus("PENDING");
        }
        return sessionRepository.save(s)
                .flatMap(saved -> searchEventPublisher.publishSessionEvent("UPDATED", saved).thenReturn(saved));
    }

    private Mono<Session> addRejectedTrainee(Session s, Long traineeId) {
        List<Long> rejected = parseCommaIds(s.getRejectedTraineeIds());
        if (!rejected.contains(traineeId)) {
            rejected = new ArrayList<>(rejected);
            rejected.add(traineeId);
        }
        s.setRejectedTraineeIds(rejected.stream().map(String::valueOf).collect(Collectors.joining(",")));
        return sessionRepository.save(s)
                .flatMap(saved -> searchEventPublisher.publishSessionEvent("UPDATED", saved).thenReturn(saved));
    }

    private Mono<Session> notifyTraineeAction(Session session, String traineeName, boolean confirmed, List<Long> allTraineeIds) {
        return mentorRepository.findById(session.getMentorId())
                .flatMap(mentor -> {
                    String profile = getProfile(mentor);
                    List<Long> confirmedIds = parseCommaIds(session.getConfirmedTraineeIds());
                    List<Long> rejectedIds = parseCommaIds(session.getRejectedTraineeIds());
                    int respondedCount = confirmedIds.size() + rejectedIds.size();
                    int totalCount = allTraineeIds.size();
                    String verb = confirmed ? "підтвердив" : "відхилив";
                    String nTitle = traineeName + " " + verb + " запрошення на сесію";
                    String nMessage = String.format("%s — %s %s (%d/%d)",
                        session.getTitle(), session.getWorkoutDate(), session.getStartTime(), respondedCount, totalCount);
                    createAndBroadcastNotification(null, mentor.getId(), nTitle, nMessage, confirmed ? "SESSION_CONFIRMED" : "SESSION_REJECTED", session.getId()).subscribe();
                    if (!mentor.hasTelegram()) return Mono.just(session);
                    String text = String.format(
                        "%s %s сесію\n\n🏷 <b>%s</b>\n📅 %s\n🕐 %s — %s\n👤 %s (%d/%d)",
                        traineeName, verb,
                        session.getTitle() != null ? session.getTitle() : "",
                        session.getWorkoutDate() != null ? session.getWorkoutDate().toString() : "",
                        session.getStartTime() != null ? session.getStartTime().toString() : "",
                        session.getEndTime() != null ? session.getEndTime().toString() : "",
                        confirmed ? "✅ Підтверджено" : "❌ Відхилено",
                        respondedCount, totalCount
                    );
                    return notificationService.sendMessageToMentor(mentor.getTelegramChatId(), text).thenReturn(session);
                })
                .defaultIfEmpty(session);
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
            Long rejectTraineeId = ((Number) traineeIdObj).longValue();
            return sessionRepository.findById(sessionId)
                    .flatMap(s -> sessionRepository.findTraineeIdsBySessionId(s.getId())
                            .any(tId -> tId.equals(rejectTraineeId))
                            .flatMap(isTrainee -> {
                                if (!isTrainee) {
                                    Map<String, Object> err = new HashMap<>();
                                    err.put("success", false);
                                    err.put("reason", "Not your session");
                                    return Mono.just(ResponseEntity.badRequest().body(err));
                                }
                                return traineeRepository.findById(rejectTraineeId)
                                        .map(Trainee::getName)
                                        .defaultIfEmpty("")
                                        .flatMap(traineeName -> sessionRepository.findTraineeIdsBySessionId(s.getId())
                                                .collectList()
                                                .flatMap(allTraineeIds -> addRejectedTrainee(s, rejectTraineeId)
                                                        .flatMap(updated -> notifyTraineeAction(updated, traineeName, false, allTraineeIds))
                                                        .doOnSuccess(v -> eventBroadcastService.broadcast("session_changed"))
                                                        .map(saved -> {
                                                            Map<String, Object> result = new HashMap<>();
                                                            result.put("success", true);
                                                            result.put("confirmationStatus", saved.getConfirmationStatus());
                                                            result.put("confirmedTraineeIds", parseCommaIds(saved.getConfirmedTraineeIds()));
                                                            result.put("rejectedTraineeIds", parseCommaIds(saved.getRejectedTraineeIds()));
                                                            return ResponseEntity.ok(result);
                                                        })));
                            }))
                    .defaultIfEmpty(ResponseEntity.notFound().build());
        });
    }

    @PostMapping("/sessions/{sessionId}/request-trainee-confirmation")
    public Mono<ResponseEntity<Map<String, Object>>> requestTraineeConfirmation(@PathVariable Long sessionId) {
        return sessionRepository.findById(sessionId)
                .flatMap(session -> {
                    session.setConfirmationStatus("PENDING");
                    return sessionRepository.save(session)
                            .flatMap(saved -> searchEventPublisher.publishSessionEvent("UPDATED", saved).thenReturn(saved));
                })
                .flatMap(saved -> mentorRepository.findById(saved.getMentorId())
                        .defaultIfEmpty(Mentor.builder().profile("sport").build())
                        .flatMap(mentor -> {
                            String profile = getProfile(mentor);
                            String sessionLabel = ProfileLabels.get(profile, "session");
                            String mentorLabel = ProfileLabels.get(profile, "mentor");
                            String nTitle = mentorLabel + " створив " + sessionLabel.toLowerCase();
                            String nMessage = saved.getTitle() + " — " + saved.getWorkoutDate() + " " + saved.getStartTime();
                            return sessionRepository.findTraineeIdsBySessionId(saved.getId())
                                    .collectList()
                                    .flatMapMany(traineeIds -> {
                                        if (traineeIds.isEmpty()) return Flux.just(saved);
                                        return Flux.fromIterable(traineeIds)
                                                .flatMap(traineeId -> traineeRepository.findById(traineeId))
                                                .flatMap(trainee -> {
                                                    return createAndBroadcastNotification(trainee.getId(), null, nTitle, nMessage, "SESSION_CREATED", saved.getId(), "trainee_confirm_session")
                                                            .then(Mono.defer(() -> {
                                                                if (trainee.hasTelegram()) {
                                                                    String tmpl = String.format(
                                                                            ProfileLabels.get(profile, "telegram_session_confirmation_request"),
                                                                            saved.getWorkoutDate().toString(),
                                                                            saved.getStartTime().toString(),
                                                                            saved.getEndTime() != null ? saved.getEndTime().toString() : "",
                                                                            saved.getTitle() != null ? saved.getTitle() : "");
                                                                    Map<String, Object> keyboard = createTraineeConfirmRejectKeyboard(saved.getId());
                                                                    return notificationService.sendMessage(trainee.getTelegramChatId(), tmpl, keyboard);
                                                                }
                                                                return Mono.just(true);
                                                            })).thenReturn(saved);
                                                });
                                    })
                                    .then(Mono.just(saved));
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
                                        .flatMap(saved -> searchEventPublisher.publishSessionEvent("UPDATED", saved).thenReturn(saved))
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
                                    m.put("confirmedTraineeIds", s.getConfirmedTraineeIds());
                                    m.put("rejectedTraineeIds", s.getRejectedTraineeIds());
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

    private static final Logger traineeLog = LoggerFactory.getLogger(SessionConfirmationController.class);

    @PostMapping("/trainee/sessions")
    public Mono<ResponseEntity<Map<String, Object>>> createTraineeSession(@RequestBody Map<String, Object> body,
                                                                           ServerWebExchange exchange) {
        traineeLog.warn("createTraineeSession: body={}", body);
        return exchange.getSession().flatMap(session -> {
            Object traineeIdObj = session.getAttribute("trainee_id");
            if (!(traineeIdObj instanceof Number)) {
                traineeLog.warn("createTraineeSession: not authenticated as trainee, attributes={}", session.getAttributes(), new RuntimeException("debug"));
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
                        final Long locationId;
                        Object locObj = body.get("locationId");
                        if (locObj instanceof Number) {
                            locationId = ((Number) locObj).longValue();
                        } else {
                            locationId = null;
                        }

                        if (title == null || dateStr == null || timeStr == null) {
                            traineeLog.warn("createTraineeSession: missing required fields, title={}, date={}, time={}, body={}", title, dateStr, timeStr, body, new RuntimeException("debug"));
                            Map<String, Object> err = new HashMap<>();
                            err.put("success", false);
                            err.put("reason", "Missing required fields");
                            return Mono.just(ResponseEntity.badRequest().body(err));
                        }

                        LocalDate sessionDate;
                        LocalTime sessionStart;
                        LocalTime sessionEnd;
                        try {
                            sessionDate = LocalDate.parse(dateStr);
                            sessionStart = LocalTime.parse(timeStr);
                            sessionEnd = endTimeStr != null ? LocalTime.parse(endTimeStr) : null;
                        } catch (Exception e) {
                            traineeLog.warn("createTraineeSession: invalid date/time format, date={}, time={}, endTime={}", dateStr, timeStr, endTimeStr, e);
                            Map<String, Object> err = new HashMap<>();
                            err.put("success", false);
                            err.put("reason", "Невірний формат дати або часу");
                            return Mono.just(ResponseEntity.badRequest().body(err));
                        }

                        Long mentorId = trainee.getMentorId();
                        if (mentorId == null) {
                            traineeLog.warn("createTraineeSession: trainee has no mentor, traineeId={}", traineeId, new RuntimeException("debug"));
                            Map<String, Object> err = new HashMap<>();
                            err.put("success", false);
                            err.put("reason", "У вас немає тренера");
                            return Mono.just(ResponseEntity.badRequest().body(err));
                        }

                        return mentorDayOffRepository.findByMentorIdAndDate(mentorId, sessionDate)
                                .map(dayOff -> true)
                                .defaultIfEmpty(false)
                                .flatMap(isDayOff -> {
                                    if (isDayOff) {
                                        traineeLog.warn("createTraineeSession: mentor day off for mentorId={}, date={}", mentorId, sessionDate, new RuntimeException("debug"));
                                        Map<String, Object> err = new HashMap<>();
                                        err.put("success", false);
                                        err.put("reason", "Цей день є вихідним для тренера");
                                        return Mono.just(ResponseEntity.badRequest().body(err));
                                    }
                                    return validateTraineeWorkHours(mentorId, sessionStart, sessionEnd)
                                            .switchIfEmpty(Mono.defer(() -> {
                                                Session newSession = Session.builder()
                                                        .title(title)
                                                        .description(description)
                                                        .workoutDate(sessionDate)
                                                        .startTime(sessionStart)
                                                        .endTime(sessionEnd)
                                                        .mentorId(mentorId)
                                                        .locationId(locationId)
                                                        .createdBy("TRAINEE")
                                                        .confirmationStatus("PENDING")
                                                        .build();
                                                return sessionRepository.save(newSession)
                                                        .flatMap(saved -> searchEventPublisher.publishSessionEvent("CREATED", saved).thenReturn(saved))
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
                                            }))
                                            .flatMap(errResponse -> Mono.just(errResponse));
                                });
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        traineeLog.warn("createTraineeSession: trainee not found for id={}", traineeId, new RuntimeException("debug"));
                        Map<String, Object> err = new HashMap<>();
                        err.put("success", false);
                        err.put("reason", "Трейні не знайдено");
                        return Mono.just(ResponseEntity.badRequest().body(err));
                    }))
                    .onErrorResume(e -> {
                        traineeLog.error("createTraineeSession: unexpected error for traineeId={}", traineeId, e);
                        Map<String, Object> err = new HashMap<>();
                        err.put("success", false);
                        err.put("reason", "Внутрішня помилка сервера");
                        return Mono.just(ResponseEntity.badRequest().body(err));
                    });
        }).onErrorResume(e -> {
            traineeLog.error("createTraineeSession: session error", e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("reason", "Внутрішня помилка сервера");
            return Mono.just(ResponseEntity.badRequest().body(err));
        });
    }

    private Mono<ResponseEntity<Map<String, Object>>> validateTraineeWorkHours(Long mentorId, LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            return Mono.empty();
        }
        return mentorRepository.findById(mentorId)
                .flatMap(mentor -> {
                    String workStart = mentor.getWorkStart();
                    String workEnd = mentor.getWorkEnd();
                    if (workStart == null || workEnd == null) {
                        return Mono.empty();
                    }
                    LocalTime ws = LocalTime.parse(workStart);
                    LocalTime we = LocalTime.parse(workEnd);
                    if (startTime.isBefore(ws) || endTime.isAfter(we)) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("success", false);
                        err.put("reason", "Час сесії має бути в межах робочих годин тренера (" + workStart + " — " + workEnd + ")");
                        traineeLog.warn("createTraineeSession: work hours validation failed for mentorId={}, session({}-{}), workHours({}-{})", mentorId, startTime, endTime, workStart, workEnd, new RuntimeException("debug"));
                        return Mono.just(ResponseEntity.badRequest().body(err));
                    }
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.fromRunnable(() ->
                    traineeLog.warn("createTraineeSession: mentor not found for work hours check, mentorId={}", mentorId, new RuntimeException("debug"))
                ));
    }

    private Mono<Session> notifyCoachAboutNewSession(Session session, Trainee trainee) {
        return mentorRepository.findById(trainee.getMentorId())
                .flatMap(mentor -> {
                    String profile = getProfile(mentor);
                    String nTitle = trainee.getName() + " створив " + ProfileLabels.get(profile, "session").toLowerCase();
                    String nMessage = session.getTitle() + " — " + session.getWorkoutDate() + " " + session.getStartTime();
                    createAndBroadcastNotification(null, mentor.getId(), nTitle, nMessage, "SESSION_CREATED", session.getId(), "coach_confirm_session").subscribe();

                    if (!mentor.hasTelegram()) return Mono.just(session);
                    String message = String.format(
                            ProfileLabels.get(profile, "telegram_new_session_from_trainee"),
                            trainee.getName(),
                            session.getTitle() != null ? session.getTitle() : "",
                            session.getWorkoutDate().toString(),
                            session.getStartTime().toString(),
                            session.getEndTime() != null ? session.getEndTime().toString() : "");

                    Map<String, Object> keyboard = createConfirmRejectKeyboard(session.getId());
                    return notificationService.sendMessageToMentor(mentor.getTelegramChatId(), message, keyboard)
                            .thenReturn(session);
                })
                .defaultIfEmpty(session);
    }

    private Mono<Session> notifyOtherParty(Session session, boolean confirmed) {
        return notifyOtherParty(session, confirmed, null);
    }

    private Mono<Session> notifyOtherParty(Session session, boolean confirmed, String traineeName) {
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
                                    return notificationService.sendMessage(trainee.getTelegramChatId(), text).thenReturn(session);
                                });
                    })
                    .defaultIfEmpty(session);
        }
        return mentorRepository.findById(session.getMentorId())
                .flatMap(mentor -> {
                    String profile = getProfile(mentor);
                    String traineeLabel = ProfileLabels.get(profile, "trainee");
                    String sessionLabel = ProfileLabels.get(profile, "session").toLowerCase();
                    String actorName = traineeName != null ? traineeName : traineeLabel;
                    String nTitle = actorName + " " + verb + " " + sessionLabel;
                    String nMessage = session.getTitle() + " — " + session.getWorkoutDate() + " " + session.getStartTime();
                    createAndBroadcastNotification(null, mentor.getId(), nTitle, nMessage, confirmed ? "SESSION_CONFIRMED" : "SESSION_REJECTED", session.getId()).subscribe();
                    if (!mentor.hasTelegram()) return Mono.just(session);
                    String text = String.format(
                            ProfileLabels.get(profile, "telegram_session_decision_trainee"),
                            status,
                            (traineeName != null ? traineeName + " " : "") + ProfileLabels.get(profile, action),
                            session.getTitle() != null ? session.getTitle() : "",
                            session.getWorkoutDate().toString(),
                            session.getStartTime().toString(),
                            session.getEndTime() != null ? session.getEndTime().toString() : "");
                    return notificationService.sendMessageToMentor(mentor.getTelegramChatId(), text).thenReturn(session);
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

    private Map<String, Object> createTraineeConfirmRejectKeyboard(Long sessionId) {
        Map<String, Object> confirmBtn = new HashMap<>();
        confirmBtn.put("text", "✅ Підтвердити");
        confirmBtn.put("callback_data", "trainee_confirm_session:" + sessionId);

        Map<String, Object> rejectBtn = new HashMap<>();
        rejectBtn.put("text", "❌ Відхилити");
        rejectBtn.put("callback_data", "trainee_reject_session:" + sessionId);

        Map<String, Object> keyboard = new HashMap<>();
        keyboard.put("inline_keyboard", List.of(List.of(confirmBtn, rejectBtn)));
        return keyboard;
    }

    private Mono<Void> createAndBroadcastNotification(Long traineeId, Long mentorId, String title, String message, String type, Long sessionId) {
        return createAndBroadcastNotification(traineeId, mentorId, title, message, type, sessionId, null);
    }

    private Mono<Void> createAndBroadcastNotification(Long traineeId, Long mentorId, String title, String message, String type, Long sessionId, String actionType) {
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
                    if (actionType != null) {
                        evt.put("actionType", actionType);
                    }
                    eventBroadcastService.broadcastJson(evt);
                    if (traineeId != null) pushService.sendToTrainee(traineeId, title, message, sessionId, actionType).subscribe();
                    if (mentorId != null) pushService.sendToMentor(mentorId, title, message, sessionId, actionType).subscribe();
                    return Mono.empty();
                });
    }

    private String getProfile(Mentor mentor) {
        return mentor != null && mentor.getProfile() != null ? mentor.getProfile() : "sport";
    }
}
