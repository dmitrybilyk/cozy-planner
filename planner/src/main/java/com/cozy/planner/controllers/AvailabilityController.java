package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Athlete;
import com.cozy.planner.model.entity.AthleteAvailability;
import com.cozy.planner.repositories.AthleteAvailabilityRepository;
import com.cozy.planner.repositories.AthleteRepository;
import com.cozy.planner.repositories.CoachRepository;
import com.cozy.planner.service.EventBroadcastService;
import com.cozy.planner.service.TelegramService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class AvailabilityController {

    private final AthleteRepository athleteRepository;
    private final AthleteAvailabilityRepository availabilityRepository;
    private final CoachRepository coachRepository;
    private final EventBroadcastService eventService;
    private final TelegramService telegramService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AvailabilityController(AthleteRepository athleteRepository,
                                  AthleteAvailabilityRepository availabilityRepository,
                                  CoachRepository coachRepository,
                                  EventBroadcastService eventService,
                                  TelegramService telegramService) {
        this.athleteRepository = athleteRepository;
        this.availabilityRepository = availabilityRepository;
        this.coachRepository = coachRepository;
        this.eventService = eventService;
        this.telegramService = telegramService;
    }

    @PostMapping(path = {"/api/v1/athletes/{traineeId}/generate-invite", "/api/v1/trainees/{traineeId}/generate-invite"})
    public Mono<ResponseEntity<Map<String, String>>> generateInvite(@PathVariable Long traineeId, ServerWebExchange exchange) {
        return getMentorId(exchange)
                .flatMap(mentorId -> athleteRepository.findById(traineeId)
                        .flatMap(athlete -> {
                            if (!athlete.getCoachId().equals(mentorId)) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).<Map<String, String>>build());
                            }
                            if (athlete.getInviteToken() != null) {
                                Map<String, String> body = new HashMap<>();
                                body.put("inviteUrl", buildInviteUrl(exchange, athlete.getInviteToken()));
                                return Mono.just(ResponseEntity.ok(body));
                            }
                            String token = generateToken();
                            athlete.setInviteToken(token);
                            return athleteRepository.save(athlete).map(saved -> {
                                Map<String, String> body = new HashMap<>();
                                body.put("inviteUrl", buildInviteUrl(exchange, saved.getInviteToken()));
                                return ResponseEntity.ok(body);
                            });
                        })
                        .defaultIfEmpty(ResponseEntity.notFound().build()));
    }

    @GetMapping(path = {"/api/v1/athlete/invite", "/api/v1/trainee/invite"})
    public Mono<ResponseEntity<Map<String, Object>>> checkInvite(@RequestParam String token) {
        return athleteRepository.findByInviteToken(token)
                .map(athlete -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("traineeId", athlete.getId());
                    body.put("athleteId", athlete.getId());
                    body.put("name", athlete.getName());
                    return ResponseEntity.ok(body);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping(path = {"/api/v1/athletes/{traineeId}/availability", "/api/v1/trainees/{traineeId}/availability"})
    public Flux<AthleteAvailability> getAvailability(@PathVariable Long traineeId,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return availabilityRepository.findByAthleteIdAndDateBetween(traineeId, startDate, endDate);
    }

    @GetMapping(path = {"/api/v1/coaches/{coachId}/availability", "/api/v1/mentors/{mentorId}/availability"})
    public Flux<Map<String, Object>> getTraineesAvailability(@PathVariable(required = false) Long mentorId,
                                                              @PathVariable(required = false) Long coachId,
                                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long id = (mentorId != null) ? mentorId : coachId;
        return athleteRepository.findAllByCoachId(id)
                .flatMap(athlete -> availabilityRepository.findByAthleteIdAndDateBetween(athlete.getId(), startDate, endDate)
                        .collectList()
                        .map(availList -> {
                            Map<String, Object> entry = new HashMap<>();
                            entry.put("traineeId", athlete.getId());
                            entry.put("athleteId", athlete.getId());
                            entry.put("traineeName", athlete.getName());
                            entry.put("athleteName", athlete.getName());
                            entry.put("slots", availList.stream().map(a -> {
                                Map<String, String> slot = new HashMap<>();
                                slot.put("date", a.getDate().toString());
                                slot.put("startTime", a.getStartTime().toString());
                                slot.put("endTime", a.getEndTime().toString());
                                return slot;
                            }).toList());
                            return entry;
                        }));
    }

    @PostMapping(path = {"/api/v1/athlete/availability", "/api/v1/trainee/availability"})
    public Mono<ResponseEntity<Void>> setAvailability(@RequestBody List<SlotEntry> entries,
                                                       ServerWebExchange exchange) {
        return getTraineeId(exchange)
                .flatMap(traineeId -> {
                    Set<LocalDate> uniqueDates = entries.stream()
                            .map(SlotEntry::date)
                            .collect(Collectors.toSet());
                    
                    if (uniqueDates.isEmpty()) {
                        return Mono.just(ResponseEntity.ok().<Void>build());
                    }

                    List<AthleteAvailability> toSave = entries.stream()
                            .map(e -> {
                                AthleteAvailability ta = new AthleteAvailability();
                                ta.setAthleteId(traineeId);
                                ta.setDate(e.date);
                                ta.setStartTime(e.startTime);
                                ta.setEndTime(e.endTime);
                                return ta;
                            })
                            .toList();

                     return Flux.fromIterable(uniqueDates)
                            .flatMap(date -> availabilityRepository.findByAthleteIdAndDate(traineeId, date))
                            .flatMap(availabilityRepository::delete)
                            .thenMany(Flux.fromIterable(toSave))
                            .flatMap(availabilityRepository::save)
                            .then()
                            .then(notifyMentorIfNeeded(traineeId))
                            .then(Mono.fromRunnable(() -> eventService.broadcast("availability_changed")))
                            .then(Mono.just(ResponseEntity.ok().<Void>build()));
                });
    }
    
    private Mono<Void> notifyMentorIfNeeded(Long traineeId) {
        return athleteRepository.findById(traineeId)
                .flatMap(athlete -> {
                    if (athlete.getCoachId() == null) {
                        return Mono.empty();
                    }
                    return coachRepository.findById(athlete.getCoachId())
                            .flatMap(coach -> {
                                if (coach.hasTelegram()) {
                                    return telegramService.sendCoachAthleteAvailabilityUpdateNotification(coach, athlete)
                                            .then();
                                }
                                return Mono.empty();
                            });
                })
                .onErrorResume(e -> {
                    return Mono.empty();
                });
    }

    @DeleteMapping(path = {"/api/v1/athlete/availability", "/api/v1/trainee/availability"})
    public Mono<ResponseEntity<Void>> clearAvailability(@RequestParam String dates,
                                                         ServerWebExchange exchange) {
        return getTraineeId(exchange)
                .flatMap(traineeId -> {
                    List<LocalDate> dateList = Arrays.stream(dates.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(LocalDate::parse)
                            .toList();
                    
                    if (dateList.isEmpty()) {
                        return Mono.just(ResponseEntity.ok().<Void>build());
                    }

                     return Flux.fromIterable(dateList)
                            .flatMap(date -> availabilityRepository.findByAthleteIdAndDate(traineeId, date))
                            .flatMap(availabilityRepository::delete)
                            .then()
                            .then(notifyMentorIfNeeded(traineeId))
                            .then(Mono.fromRunnable(() -> eventService.broadcast("availability_changed")))
                            .then(Mono.just(ResponseEntity.ok().<Void>build()));
                });
    }

    private Mono<Long> getMentorId(ServerWebExchange exchange) {
        return exchange.getSession().map(session -> {
            Object coachId = session.getAttribute("coach_id");
            if (coachId instanceof Number) return ((Number) coachId).longValue();
            return -1L;
        });
    }

    private Mono<Long> getTraineeId(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            Object traineeId = session.getAttribute("athlete_id");
            if (traineeId instanceof Number) {
                long id = ((Number) traineeId).longValue();
                if (id > 0) {
                    return athleteRepository.findById(id)
                            .map(athlete -> athlete.getId());
                }
            }
            return Mono.error(new RuntimeException("Trainee not authenticated"));
        });
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String buildInviteUrl(ServerWebExchange exchange, String token) {
        String host = exchange.getRequest().getURI().getHost();
        int port = exchange.getRequest().getURI().getPort();
        String scheme = exchange.getRequest().getURI().getScheme();
        String base = scheme + "://" + host + (port > 0 ? ":" + port : "");
        return base + "/athlete/" + token;
    }

    public record SlotEntry(LocalDate date, LocalTime startTime, LocalTime endTime) {}
}
