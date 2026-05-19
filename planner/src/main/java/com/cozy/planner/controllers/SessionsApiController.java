package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Session;
import com.cozy.planner.repositories.SessionRepository;
import com.cozy.planner.service.EventBroadcastService;
import com.planner.api.SessionsApi;
import com.planner.model.CreateSessionRequest;
import com.planner.model.SessionDTO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class SessionsApiController implements SessionsApi {

    private final SessionRepository sessionRepository;
    private final EventBroadcastService eventBroadcastService;

    public SessionsApiController(SessionRepository sessionRepository, EventBroadcastService eventBroadcastService) {
        this.sessionRepository = sessionRepository;
        this.eventBroadcastService = eventBroadcastService;
    }

    @Override
    public Mono<ResponseEntity<SessionDTO>> createSession(Mono<CreateSessionRequest> createSessionRequest, ServerWebExchange exchange) {
        return createSessionRequest
                .flatMap(request -> {
                    Long sessionId = request.getId();
                    
                    if (sessionId != null && sessionId > 0) {
                        return sessionRepository.findById(sessionId)
                                .flatMap(existing -> updateSession(existing, request))
                                .switchIfEmpty(Mono.defer(() -> createNewSession(request)));
                    } else {
                        return createNewSession(request);
                    }
                })
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved));
    }

    private Mono<SessionDTO> createNewSession(CreateSessionRequest request) {
        Session session = Session.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .workoutDate(request.getDate())
                .startTime(parseTime(request.getTime()))
                .endTime(parseTime(request.getEndTime()))
                .mentorId(request.getMentorId())
                .locationId(request.getLocationId())
                .build();

        return sessionRepository.save(session)
                .flatMap(saved -> saveTraineeLinks(saved.getId(), request.getTraineeIds())
                        .then(loadTraineeIds(saved))
                        .doOnSuccess(w -> eventBroadcastService.broadcast("session_changed"))
                        .map(this::mapToDto));
    }

    private Mono<SessionDTO> updateSession(Session existing, CreateSessionRequest request) {
        if (request.getTitle() != null) {
            existing.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
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

        return sessionRepository.save(existing)
                .flatMap(saved -> {
                    if (traineeIds != null) {
                        return sessionRepository.deleteTraineeLinks(saved.getId())
                                .then(saveTraineeLinks(saved.getId(), traineeIds))
                                .then(loadTraineeIds(saved))
                                .doOnSuccess(w -> eventBroadcastService.broadcast("session_changed"))
                                .map(this::mapToDto);
                    }
                    return loadTraineeIds(saved)
                            .doOnSuccess(w -> eventBroadcastService.broadcast("session_changed"))
                            .map(this::mapToDto);
                });
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteSession(Long sessionId, ServerWebExchange exchange) {
        return sessionRepository.findById(sessionId)
                .flatMap(session -> sessionRepository.deleteTraineeLinks(sessionId)
                        .then(sessionRepository.delete(session))
                        .then(Mono.fromRunnable(() -> eventBroadcastService.broadcast("session_changed")))
                        .then(Mono.just(ResponseEntity.noContent().<Void>build())))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<Flux<SessionDTO>>> getSessions(LocalDate startDate, LocalDate endDate, Long mentorId, Long traineeId, ServerWebExchange exchange) {
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
                .flatMapMany(sessions -> loadTraineeIdsBatch(sessions).map(this::mapToDto));
        
        return Mono.just(ResponseEntity.ok(dtoFlux));
    }

    @GetMapping("/api/v1/sessions/counts")
    public Mono<ResponseEntity<Map<String, Long>>> getSessionCounts(
            @RequestParam Long mentorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return sessionRepository.findDatesByMentorAndPeriod(mentorId, startDate, endDate)
                .collectList()
                .map(dates -> {
                    Map<String, Long> counts = new HashMap<>();
                    for (LocalDate date : dates) {
                        counts.merge(date.toString(), 1L, Long::sum);
                    }
                    return ResponseEntity.ok(counts);
                });
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
        
        return dto;
    }
}
