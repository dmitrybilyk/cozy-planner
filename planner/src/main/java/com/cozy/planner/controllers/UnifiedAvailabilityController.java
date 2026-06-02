package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.AvailabilityRange;
import com.cozy.planner.repositories.AvailabilityRangeRepository;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.TraineeRepository;
import com.cozy.planner.service.EventBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class UnifiedAvailabilityController {

    private static final Logger log = LoggerFactory.getLogger(UnifiedAvailabilityController.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final AvailabilityRangeRepository rangeRepository;
    private final MentorRepository mentorRepository;
    private final TraineeRepository traineeRepository;
    private final EventBroadcastService eventService;

    public UnifiedAvailabilityController(AvailabilityRangeRepository rangeRepository,
                                          MentorRepository mentorRepository,
                                          TraineeRepository traineeRepository,
                                          EventBroadcastService eventService) {
        this.rangeRepository = rangeRepository;
        this.mentorRepository = mentorRepository;
        this.traineeRepository = traineeRepository;
        this.eventService = eventService;
    }

    @GetMapping("/api/v1/availability/ranges")
    public Mono<List<Map<String, Object>>> getRanges(
            @RequestParam("userId") Long userId,
            @RequestParam("userType") String userType,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("getRanges userId={}, userType={}, start={}, end={}", userId, userType, startDate, endDate);
        return resolveZoneId(userId, userType)
                .flatMapMany(zoneId ->
                        rangeRepository.findByUserIdAndUserTypeAndDateBetween(userId, userType, startDate, endDate)
                                .map(range -> toResponseMap(range, zoneId))
                )
                .onErrorResume(e -> {
                    log.error("Error loading availability ranges: userId={}, userType={}", userId, userType, e);
                    return Flux.empty();
                })
                .collectList()
                .doOnNext(list -> log.info("getRanges found {} items", list.size()));
    }

    @PutMapping("/api/v1/availability/ranges")
    public Mono<ResponseEntity<Map<String, Object>>> saveRanges(@RequestBody Map<String, Object> body) {
        Long userId = ((Number) body.get("userId")).longValue();
        String userType = (String) body.get("userType");
        String dateStr = (String) body.get("date");
        LocalDate date = LocalDate.parse(dateStr);
        Boolean freeAllDay = body.get("freeAllDay") instanceof Boolean ? (Boolean) body.get("freeAllDay") : false;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ranges = (List<Map<String, Object>>) body.get("ranges");

        return resolveZoneId(userId, userType)
                .flatMap(zoneId -> {
                    ZoneId z = zoneId != null ? zoneId : ZoneId.of("Europe/Kiev");
                    return rangeRepository.deleteByUserIdAndUserTypeAndDate(userId, userType, date)
                            .then(Mono.defer(() -> {
                                if (freeAllDay) {
                                    AvailabilityRange freeDay = AvailabilityRange.builder()
                                            .userId(userId)
                                            .userType(userType)
                                            .date(date)
                                            .freeAllDay(true)
                                            .build();
                                    return rangeRepository.save(freeDay)
                                            .then(Mono.defer(() -> {
                                                eventService.broadcast("availability_changed");
                                                return Mono.just(ResponseEntity.ok(Map.of("success", true)));
                                            }));
                                }
                                if (ranges == null || ranges.isEmpty()) {
                                    return Mono.just(ResponseEntity.ok(Map.of("success", true)));
                                }
                                List<AvailabilityRange> entities = ranges.stream()
                                        .map(r -> toEntity(r, userId, userType, date, z))
                                        .collect(Collectors.toList());
                                return rangeRepository.saveAll(entities)
                                        .then(Mono.defer(() -> {
                                            eventService.broadcast("availability_changed");
                                            return Mono.just(ResponseEntity.ok(Map.of("success", true)));
                                        }));
                            }));
                });
    }

    private Mono<ZoneId> resolveZoneId(Long userId, String userType) {
        if ("COACH".equals(userType)) {
            return mentorRepository.findById(userId)
                    .map(m -> ZoneId.of(m.getTimezone() != null ? m.getTimezone() : "Europe/Kiev"))
                    .defaultIfEmpty(ZoneId.of("Europe/Kiev"));
        } else if ("TRAINEE".equals(userType)) {
            return traineeRepository.findById(userId)
                    .map(t -> ZoneId.of(t.getTimezone() != null ? t.getTimezone() : "Europe/Kiev"))
                    .defaultIfEmpty(ZoneId.of("Europe/Kiev"));
        }
        return Mono.just(ZoneId.of("Europe/Kiev"));
    }

    static OffsetDateTime toUtc(LocalDate date, String timeStr, ZoneId zone) {
        if (timeStr == null || timeStr.isEmpty()) return null;
        LocalTime lt = LocalTime.parse(timeStr);
        return date.atTime(lt).atZone(zone).toOffsetDateTime();
    }

    static String formatInZone(OffsetDateTime odt, ZoneId zone) {
        if (odt == null || zone == null) return "";
        return odt.atZoneSameInstant(zone).format(TIME_FMT);
    }

    private AvailabilityRange toEntity(Map<String, Object> r, Long userId, String userType, LocalDate date, ZoneId zone) {
        String startTimeStr = (String) r.get("startTime");
        String endTimeStr = (String) r.get("endTime");
        Number locId = (Number) r.get("locationId");

        return AvailabilityRange.builder()
                .userId(userId)
                .userType(userType)
                .date(date)
                .startTime(startTimeStr != null ? toUtc(date, startTimeStr, zone) : null)
                .endTime(endTimeStr != null ? toUtc(date, endTimeStr, zone) : null)
                .locationId(locId != null ? locId.longValue() : null)
                .build();
    }

    private Map<String, Object> toResponseMap(AvailabilityRange range, ZoneId zone) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", range.getId());
        m.put("userId", range.getUserId());
        m.put("userType", range.getUserType());
        m.put("date", range.getDate().toString());
        m.put("startTime", formatInZone(range.getStartTime(), zone));
        m.put("endTime", formatInZone(range.getEndTime(), zone));
        if (range.getLocationId() != null) m.put("locationId", range.getLocationId());
        m.put("freeAllDay", range.getFreeAllDay() != null && range.getFreeAllDay());
        return m;
    }
}
