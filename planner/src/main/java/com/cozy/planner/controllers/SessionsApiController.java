package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.MentorDayOff;
import com.cozy.planner.model.entity.Notification;
import com.cozy.planner.model.entity.Session;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.repositories.MentorDayOffRepository;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.NotificationRepository;
import com.cozy.planner.repositories.SessionRepository;
import com.cozy.planner.repositories.TraineeRepository;
import com.cozy.planner.service.AuditService;
import com.cozy.planner.service.EventBroadcastService;
import com.cozy.planner.service.ProfileLabels;
import com.cozy.planner.service.PushService;
import com.cozy.planner.service.NotificationService;
import com.cozy.planner.service.SearchEventPublisher;
import com.cozy.planner.service.SessionCreationSuggestionService;
import com.planner.api.SessionsApi;
import com.planner.model.CreateSessionRequest;
import com.planner.model.SessionDTO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class SessionsApiController implements SessionsApi {

    private static final Logger log = LoggerFactory.getLogger(SessionsApiController.class);

    private final SessionRepository sessionRepository;
    private final MentorRepository mentorRepository;
    private final TraineeRepository traineeRepository;
    private final NotificationRepository notificationRepository;
    private final EventBroadcastService eventBroadcastService;
    private final PushService pushService;
    private final NotificationService notificationService;
    private final MentorDayOffRepository mentorDayOffRepository;
    private final SearchEventPublisher searchEventPublisher;
    private final SessionCreationSuggestionService sessionCreationSuggestionService;
    private final AuditService auditService;

    public SessionsApiController(SessionRepository sessionRepository,
                                  MentorRepository mentorRepository,
                                  TraineeRepository traineeRepository,
                                  NotificationRepository notificationRepository,
                                  EventBroadcastService eventBroadcastService,
                                  PushService pushService,
                                  NotificationService notificationService,
                                  MentorDayOffRepository mentorDayOffRepository,
                                  SearchEventPublisher searchEventPublisher,
                                  SessionCreationSuggestionService sessionCreationSuggestionService,
                                  AuditService auditService) {
        this.sessionRepository = sessionRepository;
        this.mentorRepository = mentorRepository;
        this.traineeRepository = traineeRepository;
        this.notificationRepository = notificationRepository;
        this.eventBroadcastService = eventBroadcastService;
        this.pushService = pushService;
        this.notificationService = notificationService;
        this.mentorDayOffRepository = mentorDayOffRepository;
        this.searchEventPublisher = searchEventPublisher;
        this.sessionCreationSuggestionService = sessionCreationSuggestionService;
        this.auditService = auditService;
    }

    @Override
    public Mono<ResponseEntity<SessionDTO>> createSession(Mono<CreateSessionRequest> createSessionRequest, ServerWebExchange exchange) {
        String baseUrl = baseUrl(exchange);
        return exchange.getSession().flatMap(webSession -> {
            String actorEmail = webSession.getAttribute("user_email");
            return createSessionRequest
                    .flatMap(request -> {
                        Long sessionId = request.getId();
                        log.info("[createSession] id={}, mentorId={}, date={}, time={}, title={}, isUpdate={}",
                                sessionId, request.getMentorId(), request.getDate(), request.getTime(), request.getTitle(),
                                sessionId != null && sessionId > 0);

                        if (sessionId != null && sessionId > 0) {
                            return sessionRepository.findById(sessionId)
                                    .flatMap(existing -> updateSession(existing, request, baseUrl))
                                    .switchIfEmpty(Mono.defer(() -> createNewSession(request, baseUrl, actorEmail)));
                        } else {
                            return createNewSession(request, baseUrl, actorEmail);
                        }
                    })
                    .doOnSuccess(resp -> log.info("[createSession] success"))
                    .doOnError(e -> log.error("[createSession] error", e))
                    .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved));
        });
    }

    private String baseUrl(ServerWebExchange exchange) {
        String host = exchange.getRequest().getURI().getHost();
        int port = exchange.getRequest().getURI().getPort();
        String scheme = exchange.getRequest().getURI().getScheme();
        return scheme + "://" + host + (port > 0 ? ":" + port : "");
    }

    /**
     * Get session creation suggestions when coach clicks on trainee availability.
     * Returns available time slots (excluding existing sessions) and pre-filled times.
     */
    @GetMapping("/api/v1/sessions/suggestion")
    public Mono<ResponseEntity<Map<String, Object>>> getSessionSuggestion(
            @RequestParam Long traineeId,
            @RequestParam Long mentorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime clickedStartTime) {
        
        return sessionCreationSuggestionService.generateSessionSuggestion(traineeId, mentorId, date, clickedStartTime)
                .map(ResponseEntity::ok)
                .doOnError(e -> log.error("Error generating session suggestion", e));
    }

    private Mono<SessionDTO> createNewSession(CreateSessionRequest request, String baseUrl, String actorEmail) {
        boolean recurring = Boolean.TRUE.equals(request.getRecurring());
        String groupId = recurring ? UUID.randomUUID().toString() : null;

        return validateSession(request.getMentorId(), request.getDate(), parseTime(request.getTime()), parseTime(request.getEndTime()))
                .then(Mono.defer(() -> {
                    Session session = Session.builder()
                            .title(request.getTitle())
                            .description(request.getDescription())
                            .workoutDate(request.getDate())
                            .startTime(parseTime(request.getTime()))
                            .endTime(parseTime(request.getEndTime()))
                            .mentorId(request.getMentorId())
                            .locationId(request.getLocationId())
                            .recurring(recurring)
                            .recurrenceGroupId(groupId)
                            .build();
                    return sessionRepository.save(session)
                            .flatMap(saved -> searchEventPublisher.publishSessionEvent("CREATED", saved).thenReturn(saved))
                            .flatMap(saved -> auditService.log("SESSION_CREATED", actorEmail, saved.getMentorId(),
                                    "Session created: \"" + saved.getTitle() + "\" on " + saved.getWorkoutDate()
                                    + (recurring ? " (recurring)" : "")).thenReturn(saved))
                            .flatMap(saved -> {
                                if (!recurring) return Mono.just(saved);
                                // Create 7 more weekly instances
                                return Flux.range(1, 7)
                                        .flatMap(week -> {
                                            Session copy = Session.builder()
                                                    .title(request.getTitle())
                                                    .description(request.getDescription())
                                                    .workoutDate(request.getDate().plusWeeks(week))
                                                    .startTime(parseTime(request.getTime()))
                                                    .endTime(parseTime(request.getEndTime()))
                                                    .mentorId(request.getMentorId())
                                                    .locationId(request.getLocationId())
                                                    .recurring(true)
                                                    .recurrenceGroupId(groupId)
                                                    .build();
                                            return sessionRepository.save(copy)
                                                    .flatMap(c -> saveTraineeLinks(c.getId(), request.getTraineeIds()).thenReturn(c))
                                                    .flatMap(c -> searchEventPublisher.publishSessionEvent("CREATED", c).thenReturn(c));
                                        })
                                        .then(Mono.just(saved));
                            });
                }))
                .flatMap(saved -> saveTraineeLinks(saved.getId(), request.getTraineeIds())
                        .then(loadTraineeIds(saved))
                        .flatMap(s -> {
                            List<Long> tIds = request.getTraineeIds();
                            if (tIds != null && !tIds.isEmpty()) {
                                return mentorRepository.findById(s.getMentorId())
                                        .defaultIfEmpty(Mentor.builder().profile("sport").build())
                                        .flatMapMany(mentor -> {
                                            String profile = mentor.getProfile() != null ? mentor.getProfile() : "sport";
                                            String sessionLabel = ProfileLabels.get(profile, "session").toLowerCase();
                                            String mentorLabel = ProfileLabels.get(profile, "mentor");
                                            String nTitle = mentorLabel + " створив " + sessionLabel;
                                            String nMessage = s.getTitle() + " — " + s.getWorkoutDate() + " " + s.getStartTime();
                                            return Flux.fromIterable(tIds)
                                                    .flatMap(tId -> {
                                                        Notification n = Notification.builder()
                                                                .traineeId(tId)
                                                                .title(nTitle)
                                                                .message(nMessage)
                                                                .type("SESSION_CREATED")
                                                                .sessionId(s.getId())
                                                                .isRead(false)
                                                                .createdAt(LocalDateTime.now())
                                                                .build();
                                                        return notificationRepository.save(n)
                                                                .flatMap(savedN -> {
                                                                    Map<String, Object> evt = new HashMap<>();
                                                                    evt.put("type", "notification");
                                                                    evt.put("id", savedN.getId());
                                                                    evt.put("traineeId", savedN.getTraineeId());
                                                                    evt.put("title", savedN.getTitle());
                                                                    evt.put("message", savedN.getMessage());
                                                                    evt.put("notificationType", savedN.getType());
                                                                    evt.put("sessionId", savedN.getSessionId());
                                                                    evt.put("isRead", savedN.getIsRead());
                                                                    evt.put("createdAt", savedN.getCreatedAt() != null ? savedN.getCreatedAt().toString() : null);
                                                                      evt.put("actionType", "trainee_confirm_session");
                                                                      eventBroadcastService.broadcastJson(evt);
                                                                      pushService.sendToTrainee(tId, nTitle, nMessage, s.getId(), "trainee_confirm_session").subscribe();
                                                                      return traineeRepository.findById(tId)
                                                                              .filter(Trainee::hasTelegram)
                                                                              .flatMap(t -> {
                                                                                  String greeting = "👋 <b>" + (t.getName() != null ? t.getName() : "") + "</b>!\n\n";
                                                                                  String tmpl = greeting + String.format(
                                                                                          ProfileLabels.get(profile, "telegram_session_confirmation_request"),
                                                                                          s.getWorkoutDate().toString(),
                                                                                          s.getStartTime().toString(),
                                                                                          s.getEndTime() != null ? s.getEndTime().toString() : "",
                                                                                          s.getTitle() != null ? s.getTitle() : "");
                                                                                  Map<String, Object> confirmBtn = new HashMap<>();
                                                                                  confirmBtn.put("text", "✅ Підтвердити");
                                                                                  confirmBtn.put("callback_data", "trainee_confirm_session:" + s.getId());
                                                                                  Map<String, Object> rejectBtn = new HashMap<>();
                                                                                  rejectBtn.put("text", "❌ Відхилити");
                                                                                  rejectBtn.put("callback_data", "trainee_reject_session:" + s.getId());
                                                                                  Map<String, Object> keyboard = new HashMap<>();
                                                                                  keyboard.put("inline_keyboard", List.of(List.of(confirmBtn, rejectBtn)));
                                                                                  return notificationService.sendMessage(t.getTelegramChatId(), tmpl, keyboard);
                                                                              })
                                                                              .thenReturn(savedN);
                                                                 });
                                                    })
                                                    .then();
                                        })
                                        .then(Mono.defer(() ->
                                            traineeRepository.findAllById(tIds)
                                                .filter(Trainee::hasTelegram)
                                                .hasElements()
                                                .flatMap(hasTg -> {
                                                    if (hasTg) {
                                                        s.setConfirmationStatus("PENDING");
                                                        return sessionRepository.save(s)
                                                            .flatMap(sv -> searchEventPublisher.publishSessionEvent("UPDATED", sv).thenReturn(sv));
                                                    }
                                                    return Mono.just(s);
                                                })
                                        ));
                            }
                            return Mono.just(s);
                        })
                        .doOnSuccess(w -> eventBroadcastService.broadcast("session_changed"))
                        .map(this::mapToDto));
    }

    private Mono<SessionDTO> updateSession(Session existing, CreateSessionRequest request, String baseUrl) {
        existing.setConfirmationStatus("NONE");
        if (request.getTitle() != null) {
            existing.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        LocalDate newDate = request.getDate() != null ? request.getDate() : existing.getWorkoutDate();
        LocalTime newStartTime = request.getTime() != null ? parseTime(request.getTime()) : existing.getStartTime();
        LocalTime newEndTime = request.getEndTime() != null ? parseTime(request.getEndTime()) : existing.getEndTime();
        if (request.getDate() != null) {
            existing.setWorkoutDate(request.getDate());
        }
        if (request.getTime() != null) {
            existing.setStartTime(parseTime(request.getTime()));
        }
        if (request.getEndTime() != null) {
            existing.setEndTime(parseTime(request.getEndTime()));
        }
        if (request.getLocationId() != null) {
            existing.setLocationId(request.getLocationId());
        }

        List<Long> traineeIds = request.getTraineeIds();

        return validateSession(existing.getMentorId(), newDate, newStartTime, newEndTime)
                .then(sessionRepository.save(existing))
                .flatMap(saved -> searchEventPublisher.publishSessionEvent("UPDATED", saved).thenReturn(saved))
                .flatMap(saved -> {
                    if (traineeIds != null) {
                        return sessionRepository.deleteTraineeLinks(saved.getId())
                                .then(saveTraineeLinks(saved.getId(), traineeIds))
                                .then(loadTraineeIds(saved))
                                .flatMap(s -> notifyTraineesOnSessionChange(s, traineeIds))
                                .doOnSuccess(w -> eventBroadcastService.broadcast("session_changed"))
                                .map(this::mapToDto);
                    }
                    return loadTraineeIds(saved)
                            .doOnSuccess(w -> eventBroadcastService.broadcast("session_changed"))
                            .map(this::mapToDto);
                });
    }

    private Mono<Session> notifyTraineesOnSessionChange(Session s, List<Long> tIds) {
        if (tIds == null || tIds.isEmpty()) return Mono.just(s);
        return mentorRepository.findById(s.getMentorId())
                .defaultIfEmpty(Mentor.builder().profile("sport").build())
                .flatMapMany(mentor -> {
                    String profile = mentor.getProfile() != null ? mentor.getProfile() : "sport";
                    String sessionLabel = ProfileLabels.get(profile, "session").toLowerCase();
                    String mentorLabel = ProfileLabels.get(profile, "mentor");
                    String nTitle = mentorLabel + " оновив " + sessionLabel;
                    String nMessage = s.getTitle() + " — " + (s.getWorkoutDate() != null ? s.getWorkoutDate().toString() : "") + " " + (s.getStartTime() != null ? s.getStartTime().toString() : "");
                    return Flux.fromIterable(tIds)
                            .flatMap(tId -> {
                                Notification n = Notification.builder()
                                        .traineeId(tId)
                                        .title(nTitle)
                                        .message(nMessage)
                                        .type("SESSION_UPDATED")
                                        .sessionId(s.getId())
                                        .isRead(false)
                                        .createdAt(LocalDateTime.now())
                                        .build();
                                return notificationRepository.save(n)
                                        .flatMap(savedN -> {
                                            Map<String, Object> evt = new HashMap<>();
                                            evt.put("type", "notification");
                                            evt.put("id", savedN.getId());
                                            evt.put("traineeId", savedN.getTraineeId());
                                            evt.put("title", savedN.getTitle());
                                            evt.put("message", savedN.getMessage());
                                            evt.put("notificationType", savedN.getType());
                                            evt.put("sessionId", savedN.getSessionId());
                                            evt.put("isRead", savedN.getIsRead());
                                            evt.put("createdAt", savedN.getCreatedAt() != null ? savedN.getCreatedAt().toString() : null);
                                            evt.put("actionType", "trainee_confirm_session");
                                            eventBroadcastService.broadcastJson(evt);
                                            pushService.sendToTrainee(tId, nTitle, nMessage, s.getId(), "trainee_confirm_session").subscribe();
                                            return traineeRepository.findById(tId)
                                                    .filter(Trainee::hasTelegram)
                                                    .flatMap(t -> {
                                                        String greeting = "👋 <b>" + (t.getName() != null ? t.getName() : "") + "</b>!\n\n";
                                                        String tmpl = greeting + String.format(
                                                                ProfileLabels.get(profile, "telegram_session_confirmation_request"),
                                                                s.getWorkoutDate().toString(),
                                                                s.getStartTime().toString(),
                                                                s.getEndTime() != null ? s.getEndTime().toString() : "",
                                                                s.getTitle() != null ? s.getTitle() : "");
                                                        Map<String, Object> confirmBtn = new HashMap<>();
                                                        confirmBtn.put("text", "✅ Підтвердити");
                                                        confirmBtn.put("callback_data", "trainee_confirm_session:" + s.getId());
                                                        Map<String, Object> rejectBtn = new HashMap<>();
                                                        rejectBtn.put("text", "❌ Відхилити");
                                                        rejectBtn.put("callback_data", "trainee_reject_session:" + s.getId());
                                                        Map<String, Object> keyboard = new HashMap<>();
                                                        keyboard.put("inline_keyboard", List.of(List.of(confirmBtn, rejectBtn)));
                                                        return notificationService.sendMessage(t.getTelegramChatId(), tmpl, keyboard);
                                                    })
                                                    .thenReturn(savedN);
                                        });
                            })
                            .then();
                })
                .then(Mono.just(s));
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteSession(Long sessionId, ServerWebExchange exchange) {
        log.info("[deleteSession] sessionId={}", sessionId);
        return sessionRepository.findById(sessionId)
                .flatMap(session -> {
                    log.info("[deleteSession] found session: mentorId={}, date={}, time={}", session.getMentorId(), session.getWorkoutDate(), session.getStartTime());
                    return sessionRepository.deleteTraineeLinks(sessionId)
                            .then(sessionRepository.delete(session))
                            .then(Mono.fromRunnable(() -> eventBroadcastService.broadcast("session_changed")))
                            .then(Mono.just(ResponseEntity.noContent().<Void>build()));
                })
                .doOnSuccess(v -> log.info("[deleteSession] success sessionId={}", sessionId))
                .doOnError(e -> log.error("[deleteSession] error sessionId={}", sessionId, e))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<Flux<SessionDTO>>> getSessions(LocalDate startDate, LocalDate endDate, Long mentorId, Long traineeId, ServerWebExchange exchange) {
        log.debug("[getSessions] mentorId={}, traineeId={}, start={}, end={}", mentorId, traineeId, startDate, endDate);
        Flux<Session> sessionFlux;

        if (traineeId != null && mentorId != null) {
            sessionFlux = sessionRepository.findAllByMentorAndTraineeInPeriod(mentorId, traineeId, startDate, endDate);
        } else if (mentorId != null) {
            sessionFlux = sessionRepository.findAllByMentorIdAndWorkoutDateBetween(mentorId, startDate, endDate);
        } else {
            sessionFlux = Flux.empty();
        }

        Flux<SessionDTO> dtoFlux = sessionFlux
                .collectList()
                .doOnNext(sessions -> log.debug("[getSessions] fetched {} raw sessions for mentorId={}, start={}, end={}", sessions.size(), mentorId, startDate, endDate))
                .flatMapMany(sessions -> loadTraineeIdsBatch(sessions).map(this::mapToDto))
                .collectList()
                .doOnNext(dtos -> log.debug("[getSessions] returning {} DTOs for mentorId={}", dtos.size(), mentorId))
                .flatMapMany(Flux::fromIterable);
        
        return Mono.just(ResponseEntity.ok(dtoFlux));
    }

    @GetMapping("/api/v1/sessions/counts")
    public Mono<ResponseEntity<Map<String, Long>>> getSessionCounts(
            @RequestParam Long mentorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.debug("[getSessionCounts] mentorId={}, start={}, end={}", mentorId, startDate, endDate);
        return sessionRepository.findDatesByMentorAndPeriod(mentorId, startDate, endDate)
                .collectList()
                .map(dates -> {
                    Map<String, Long> counts = new HashMap<>();
                    for (LocalDate date : dates) {
                        counts.merge(date.toString(), 1L, Long::sum);
                    }
                    log.debug("[getSessionCounts] {} unique dates for mentorId={}", dates.size(), mentorId);
                    return ResponseEntity.ok(counts);
                })
                .doOnError(e -> log.error("[getSessionCounts] error mentorId={}", mentorId, e));
    }

    private Flux<Session> loadTraineeIdsBatch(List<Session> sessions) {
        if (sessions.isEmpty()) {
            return Flux.fromIterable(sessions);
        }
        return Flux.fromIterable(sessions)
                .flatMap(session -> loadTraineeIds(session));
    }

    private Mono<Void> saveTraineeLinks(Long sessionId, List<Long> traineeIds) {
        if (traineeIds == null || traineeIds.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(traineeIds)
                .flatMap(id -> sessionRepository.linkTraineeToSession(sessionId, id))
                .then();
    }

    private Mono<Session> loadTraineeIds(Session session) {
        return sessionRepository.findTraineeIdsBySessionId(session.getId())
                .collectList()
                .doOnNext(session::setTraineeIds)
                .thenReturn(session);
    }

    private Mono<Void> validateSession(Long mentorId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        if (mentorId == null || date == null) {
            return Mono.empty();
        }
        return mentorDayOffRepository.findByMentorIdAndDate(mentorId, date)
                .flatMap(dayOff -> Mono.<Void>error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Цей день є вихідним для тренера")))
                .switchIfEmpty(Mono.defer(() -> validateWorkHours(mentorId, startTime, endTime)))
                .then();
    }

    private Mono<Void> validateWorkHours(Long mentorId, LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            return Mono.empty();
        }
        return mentorRepository.findById(mentorId)
                .flatMap(mentor -> {
                    String workStart = mentor.getWorkStart();
                    String workEnd = mentor.getWorkEnd();
                    if (workStart == null || workEnd == null) {
                        return Mono.<Void>empty();
                    }
                    LocalTime ws = LocalTime.parse(workStart);
                    LocalTime we = LocalTime.parse(workEnd);
                    if (startTime.isBefore(ws) || endTime.isAfter(we)) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Час сесії має бути в межах робочих годин (" + workStart + " — " + workEnd + ")"));
                    }
                    return Mono.<Void>empty();
                })
                .switchIfEmpty(Mono.<Void>empty());
    }

    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }
        return LocalTime.parse(timeStr);
    }

    private String formatTime(LocalTime time) {
        if (time == null) {
            return null;
        }
        return time.toString();
    }

    private SessionDTO mapToDto(Session entity) {
        SessionDTO dto = new SessionDTO();
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setDate(entity.getWorkoutDate());
        dto.setTime(formatTime(entity.getStartTime()));
        dto.setEndTime(formatTime(entity.getEndTime()));
        dto.setMentorId(entity.getMentorId());
        dto.setLocationId(entity.getLocationId());
        dto.setConfirmationStatus(entity.getConfirmationStatus());
        dto.setCreatedBy(entity.getCreatedBy());
        
        if (entity.getTraineeIds() != null) {
            dto.setTraineeIds(new ArrayList<>(entity.getTraineeIds()));
        }
        dto.setConfirmedTraineeIds(entity.getConfirmedTraineeIds());
        dto.setRejectedTraineeIds(entity.getRejectedTraineeIds());
        dto.setRecurring(Boolean.TRUE.equals(entity.getRecurring()));
        dto.setRecurrenceGroupId(entity.getRecurrenceGroupId());

        return dto;
    }
}
