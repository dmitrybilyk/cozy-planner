package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Athlete;
import com.cozy.planner.model.entity.AthleteAvailability;
import com.cozy.planner.repositories.AthleteAvailabilityRepository;
import com.cozy.planner.repositories.AthleteRepository;
import com.cozy.planner.service.EventBroadcastService;
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
    private final EventBroadcastService eventService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AvailabilityController(AthleteRepository athleteRepository,
                                  AthleteAvailabilityRepository availabilityRepository,
                                  EventBroadcastService eventService) {
        this.athleteRepository = athleteRepository;
        this.availabilityRepository = availabilityRepository;
        this.eventService = eventService;
    }

    @PostMapping("/api/v1/athletes/{athleteId}/generate-invite")
    public Mono<ResponseEntity<Map<String, String>>> generateInvite(@PathVariable Long athleteId, ServerWebExchange exchange) {
        return getCoachId(exchange)
                .flatMap(coachId -> athleteRepository.findById(athleteId)
                        .flatMap(athlete -> {
                            if (!athlete.getCoachId().equals(coachId)) {
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

    @GetMapping("/api/v1/athlete/invite")
    public Mono<ResponseEntity<Map<String, Object>>> checkInvite(@RequestParam String token) {
        return athleteRepository.findByInviteToken(token)
                .map(athlete -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("athleteId", athlete.getId());
                    body.put("name", athlete.getName());
                    return ResponseEntity.ok(body);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/v1/athletes/{athleteId}/availability")
    public Flux<AthleteAvailability> getAvailability(@PathVariable Long athleteId,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return availabilityRepository.findByAthleteIdAndDateBetween(athleteId, startDate, endDate);
    }

    @GetMapping("/api/v1/coaches/{coachId}/availability")
    public Flux<Map<String, Object>> getAthletesAvailability(@PathVariable Long coachId,
                                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return athleteRepository.findAllByCoachId(coachId)
                .flatMap(athlete -> availabilityRepository.findByAthleteIdAndDateBetween(athlete.getId(), startDate, endDate)
                        .collectList()
                        .map(availList -> {
                            Map<String, Object> entry = new HashMap<>();
                            entry.put("athleteId", athlete.getId());
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

    @PostMapping("/api/v1/athlete/availability")
    public Mono<ResponseEntity<Void>> setAvailability(@RequestBody List<SlotEntry> entries,
                                                       ServerWebExchange exchange) {
        return getAthleteId(exchange)
                .flatMap(athleteId -> {
                    Set<LocalDate> uniqueDates = entries.stream()
                            .map(SlotEntry::date)
                            .collect(Collectors.toSet());
                    
                    if (uniqueDates.isEmpty()) {
                        return Mono.just(ResponseEntity.ok().<Void>build());
                    }

                    List<AthleteAvailability> toSave = entries.stream()
                            .map(e -> {
                                AthleteAvailability aa = new AthleteAvailability();
                                aa.setAthleteId(athleteId);
                                aa.setDate(e.date);
                                aa.setStartTime(e.startTime);
                                aa.setEndTime(e.endTime);
                                return aa;
                            })
                            .toList();

                    return Flux.fromIterable(uniqueDates)
                            .flatMap(date -> availabilityRepository.findByAthleteIdAndDate(athleteId, date))
                            .flatMap(availabilityRepository::delete)
                            .thenMany(Flux.fromIterable(toSave))
                            .flatMap(availabilityRepository::save)
                            .then(Mono.fromRunnable(() -> eventService.broadcast("availability_changed")))
                            .then(Mono.just(ResponseEntity.ok().<Void>build()));
                });
    }

    @DeleteMapping("/api/v1/athlete/availability")
    public Mono<ResponseEntity<Void>> clearAvailability(@RequestParam String dates,
                                                         ServerWebExchange exchange) {
        return getAthleteId(exchange)
                .flatMap(athleteId -> {
                    List<LocalDate> dateList = Arrays.stream(dates.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(LocalDate::parse)
                            .toList();
                    
                    if (dateList.isEmpty()) {
                        return Mono.just(ResponseEntity.ok().<Void>build());
                    }

                    return Flux.fromIterable(dateList)
                            .flatMap(date -> availabilityRepository.findByAthleteIdAndDate(athleteId, date))
                            .flatMap(availabilityRepository::delete)
                            .then()
                            .then(Mono.fromRunnable(() -> eventService.broadcast("availability_changed")))
                            .then(Mono.just(ResponseEntity.ok().<Void>build()));
                });
    }

    private Mono<Long> getCoachId(ServerWebExchange exchange) {
        return exchange.getSession().map(session -> {
            Object coachId = session.getAttribute("coach_id");
            if (coachId instanceof Number) return ((Number) coachId).longValue();
            return -1L;
        });
    }

    private Mono<Long> getAthleteId(ServerWebExchange exchange) {
        return exchange.getSession().map(session -> {
            Object athleteId = session.getAttribute("athlete_id");
            if (athleteId instanceof Number) return ((Number) athleteId).longValue();
            return -1L;
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
