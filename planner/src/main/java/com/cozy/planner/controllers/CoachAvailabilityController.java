package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Location;
import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.MentorAvailability;
import com.cozy.planner.model.entity.MentorDayOff;
import com.cozy.planner.model.entity.Session;
import com.cozy.planner.repositories.LocationRepository;
import com.cozy.planner.repositories.MentorAvailabilityRepository;
import com.cozy.planner.repositories.MentorDayOffRepository;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.SessionRepository;
import com.cozy.planner.service.EventBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@RestController
public class CoachAvailabilityController {

    private final MentorRepository mentorRepository;
    private final MentorAvailabilityRepository availabilityRepository;
    private final MentorDayOffRepository dayOffRepository;
    private final SessionRepository sessionRepository;
    private final LocationRepository locationRepository;
    private final EventBroadcastService eventService;
    private static final Logger log = LoggerFactory.getLogger(CoachAvailabilityController.class);
    private final SecureRandom secureRandom = new SecureRandom();

    public CoachAvailabilityController(MentorRepository mentorRepository,
                                       MentorAvailabilityRepository availabilityRepository,
                                       MentorDayOffRepository dayOffRepository,
                                       SessionRepository sessionRepository,
                                       LocationRepository locationRepository,
                                       EventBroadcastService eventService) {
        this.mentorRepository = mentorRepository;
        this.availabilityRepository = availabilityRepository;
        this.dayOffRepository = dayOffRepository;
        this.sessionRepository = sessionRepository;
        this.locationRepository = locationRepository;
        this.eventService = eventService;
    }

    private Mono<Long> getMentorId(ServerWebExchange exchange) {
        return exchange.getSession().map(session -> {
            Object mentorId = session.getAttribute("mentor_id");
            if (mentorId instanceof Number) return ((Number) mentorId).longValue();
            return -1L;
        });
    }

    @GetMapping("/api/v1/coach/availability")
    public Flux<MentorAvailability> getCoachAvailability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            ServerWebExchange exchange) {
        return getMentorId(exchange)
                .flatMapMany(mentorId -> availabilityRepository.findByMentorIdAndDateBetween(mentorId, startDate, endDate));
    }

    @PostMapping("/api/v1/coach/availability")
    public Mono<ResponseEntity<Void>> setCoachAvailability(@RequestBody List<SlotEntry> entries,
                                                            ServerWebExchange exchange) {
        return getMentorId(exchange).flatMap(mentorId -> {
            Set<LocalDate> uniqueDates = entries.stream()
                    .map(SlotEntry::date)
                    .collect(java.util.stream.Collectors.toSet());

            if (uniqueDates.isEmpty()) {
                return Mono.just(ResponseEntity.ok().<Void>build());
            }

            List<MentorAvailability> toSave = entries.stream()
                    .map(e -> {
                        MentorAvailability ma = new MentorAvailability();
                        ma.setMentorId(mentorId);
                        ma.setDate(e.date);
                        ma.setStartTime(e.startTime);
                        ma.setEndTime(e.endTime);
                        ma.setLocationId(e.locationId);
                        return ma;
                    })
                    .toList();

            return Flux.fromIterable(uniqueDates)
                    .flatMap(date -> availabilityRepository.findByMentorIdAndDate(mentorId, date))
                    .flatMap(availabilityRepository::delete)
                    .thenMany(Flux.fromIterable(toSave))
                    .flatMap(availabilityRepository::save)
                    .then()
                    .then(Mono.fromRunnable(() -> eventService.broadcast("coach_availability_changed")))
                    .then(Mono.just(ResponseEntity.ok().<Void>build()));
        });
    }

    @DeleteMapping("/api/v1/coach/availability")
    public Mono<ResponseEntity<Void>> clearCoachAvailability(@RequestParam String dates,
                                                             ServerWebExchange exchange) {
        return getMentorId(exchange).flatMap(mentorId -> {
            List<LocalDate> dateList = Arrays.stream(dates.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(LocalDate::parse)
                    .toList();

            if (dateList.isEmpty()) {
                return Mono.just(ResponseEntity.ok().<Void>build());
            }

            return Flux.fromIterable(dateList)
                    .flatMap(date -> availabilityRepository.findByMentorIdAndDate(mentorId, date))
                    .flatMap(availabilityRepository::delete)
                    .then()
                    .then(Mono.fromRunnable(() -> eventService.broadcast("coach_availability_changed")))
                    .then(Mono.just(ResponseEntity.ok().<Void>build()));
        });
    }

    @GetMapping("/api/v1/coach/day-off")
    public Flux<LocalDate> getDayOffs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            ServerWebExchange exchange) {
        return getMentorId(exchange)
                .flatMapMany(mentorId -> dayOffRepository.findByMentorIdAndDateBetween(mentorId, startDate, endDate)
                        .map(MentorDayOff::getDate));
    }

    @PostMapping("/api/v1/coach/day-off")
    public Mono<ResponseEntity<Map<String, Object>>> toggleDayOff(@RequestBody Map<String, Object> body,
                                                                   ServerWebExchange exchange) {
        return getMentorId(exchange).flatMap(mentorId -> {
            String dateStr = body.get("date").toString();
            LocalDate date = LocalDate.parse(dateStr);
            return dayOffRepository.findByMentorIdAndDate(mentorId, date)
                    .flatMap(existing -> dayOffRepository.deleteByMentorIdAndDate(mentorId, date)
                            .then(Mono.fromRunnable(() -> eventService.broadcast("coach_availability_changed")))
                            .then(Mono.fromCallable(() -> {
                                Map<String, Object> r = new HashMap<>();
                                r.put("dayOff", false);
                                return ResponseEntity.ok(r);
                            })))
                    .switchIfEmpty(Mono.defer(() -> {
                        MentorDayOff dayOff = MentorDayOff.builder()
                                .mentorId(mentorId)
                                .date(date)
                                .build();
                        return dayOffRepository.save(dayOff)
                                .then(Mono.fromRunnable(() -> eventService.broadcast("coach_availability_changed")))
                                .then(Mono.fromCallable(() -> {
                                    Map<String, Object> r = new HashMap<>();
                                    r.put("dayOff", true);
                                    return ResponseEntity.ok(r);
                                }));
                                            }));
                            });
    }

    @GetMapping("/api/v1/coach/sessions")
    public Flux<Map<String, Object>> getCoachSessions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            ServerWebExchange exchange) {
        return getMentorId(exchange)
                .flatMapMany(mentorId -> sessionRepository
                        .findAllByMentorIdAndWorkoutDateBetween(mentorId, startDate, endDate)
                        .map(session -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("id", session.getId());
                            m.put("title", session.getTitle());
                            m.put("description", session.getDescription());
                            m.put("date", session.getWorkoutDate().toString());
                            m.put("startTime", session.getStartTime().toString());
                            m.put("endTime", session.getEndTime().toString());
                            m.put("locationId", session.getLocationId());
                            m.put("confirmationStatus", session.getConfirmationStatus());
                            return m;
                        }));
    }

    @PostMapping("/api/v1/coach/share-token")
    public Mono<ResponseEntity<Map<String, String>>> generateShareToken(ServerWebExchange exchange) {
        return getMentorId(exchange).flatMap(mentorId ->
                mentorRepository.findById(mentorId).flatMap(mentor -> {
                    if (mentor.getShareToken() != null) {
                        Map<String, String> body = new HashMap<>();
                        body.put("shareToken", mentor.getShareToken());
                        return Mono.just(ResponseEntity.ok(body));
                    }
                    String token = generateToken();
                    mentor.setShareToken(token);
                    return mentorRepository.save(mentor).map(saved -> {
                        Map<String, String> body = new HashMap<>();
                        body.put("shareToken", saved.getShareToken());
                        return ResponseEntity.ok(body);
                    });
                }).defaultIfEmpty(ResponseEntity.notFound().build())
        );
    }

    @GetMapping("/api/v1/shared/{shareToken}")
    public Mono<ResponseEntity<Map<String, Object>>> getSharedAvailability(@PathVariable String shareToken,
                                                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return mentorRepository.findByShareToken(shareToken)
                .flatMap(mentor -> {
                    Set<Long> locationIds = new HashSet<>();
                    return dayOffRepository.findByMentorIdAndDateBetween(mentor.getId(), startDate, endDate)
                            .map(MentorDayOff::getDate)
                            .collectList()
                            .flatMap(dayOffDates -> availabilityRepository
                                    .findByMentorIdAndDateBetween(mentor.getId(), startDate, endDate)
                                    .collectList()
                                    .flatMap(slots -> sessionRepository
                                            .findAllByMentorIdAndWorkoutDateBetween(mentor.getId(), startDate, endDate)
                                            .collectList()
                                            .flatMap(sessions -> {
                                                List<MentorAvailability> freeSlots = splitBySessions(slots, sessions);
                                                for (MentorAvailability s : freeSlots) {
                                                    if (s.getLocationId() != null) locationIds.add(s.getLocationId());
                                                }
                                                if (locationIds.isEmpty()) {
                                                    return buildSharedResponse(mentor, freeSlots, Map.of(), dayOffDates);
                                                }
                                                return Flux.fromIterable(locationIds)
                                                        .flatMap(id -> locationRepository.findById(id)
                                                                .map(l -> {
                                                                    Map<String, Object> locInfo = new HashMap<>();
                                                                    locInfo.put("name", l.getName());
                                                                    locInfo.put("description", l.getDescription());
                                                                    locInfo.put("color", l.getColor());
                                                                    return Map.entry(id, locInfo);
                                                                })
                                                                .defaultIfEmpty(Map.entry(id, Map.<String, Object>of())))
                                                        .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                                                        .flatMap(locMap -> buildSharedResponse(mentor, freeSlots, locMap, dayOffDates))
                                                        .onErrorResume(e -> {
                                                            log.error("Failed to resolve locations for shared availability", e);
                                                            return buildSharedResponse(mentor, freeSlots, Map.of(), dayOffDates);
                                                        });
                                            }))
                            );
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @SuppressWarnings("unchecked")
    private Mono<ResponseEntity<Map<String, Object>>> buildSharedResponse(Mentor mentor,
                                                                            List<MentorAvailability> slots,
                                                                            Map<Long, Map<String, Object>> locMap,
                                                                            List<LocalDate> dayOffDates) {
        Map<String, Object> body = new HashMap<>();
        body.put("mentorId", mentor.getId());
        body.put("mentorName", mentor.getName());
        body.put("profile", mentor.getProfile());
        body.put("workStart", mentor.getWorkStart() != null ? mentor.getWorkStart() : "06:00");
        body.put("workEnd", mentor.getWorkEnd() != null ? mentor.getWorkEnd() : "22:00");
        body.put("dayOffDates", dayOffDates.stream().map(LocalDate::toString).toList());
        body.put("slots", slots.stream().map(s -> {
            Map<String, Object> slot = new HashMap<>();
            slot.put("id", s.getId());
            slot.put("date", s.getDate().toString());
            slot.put("startTime", s.getStartTime().toString());
            slot.put("endTime", s.getEndTime().toString());
            slot.put("locationId", s.getLocationId());
            Map<String, Object> loc = s.getLocationId() != null ? locMap.getOrDefault(s.getLocationId(), Map.of()) : Map.of();
            slot.put("locationName", loc.getOrDefault("name", ""));
            slot.put("locationDescription", loc.getOrDefault("description", ""));
            slot.put("locationColor", loc.getOrDefault("color", ""));
            return slot;
        }).toList());
        return Mono.just(ResponseEntity.ok(body));
    }

    @GetMapping("/api/v1/shared/{shareToken}/mentor")
    public Mono<ResponseEntity<Map<String, Object>>> getSharedMentorInfo(@PathVariable String shareToken,
                                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return mentorRepository.findByShareToken(shareToken)
                .flatMap(mentor -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("id", mentor.getId());
                    body.put("name", mentor.getName());
                    body.put("profile", mentor.getProfile());
                    body.put("workStart", mentor.getWorkStart() != null ? mentor.getWorkStart() : "06:00");
                    body.put("workEnd", mentor.getWorkEnd() != null ? mentor.getWorkEnd() : "22:00");
                    if (startDate != null && endDate != null) {
                        return dayOffRepository.findByMentorIdAndDateBetween(mentor.getId(), startDate, endDate)
                                .map(MentorDayOff::getDate)
                                .map(LocalDate::toString)
                                .collectList()
                                .map(dayOffs -> {
                                    body.put("dayOffDates", dayOffs);
                                    return ResponseEntity.ok(body);
                                });
                    }
                    return Mono.just(ResponseEntity.ok(body));
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/v1/shared/{shareToken}/image")
    public Mono<ResponseEntity<byte[]>> getSharedAvailabilityImage(@PathVariable String shareToken,
                                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return mentorRepository.findByShareToken(shareToken)
                .flatMap(mentor -> {
                    LocalDate today = LocalDate.now();
                    LocalDate startDate = date != null ? date : today;
                    LocalDate endDate = date != null ? date : today.plusDays(13);
                    Set<Long> locationIds = new HashSet<>();
                    return dayOffRepository.findByMentorIdAndDateBetween(mentor.getId(), startDate, endDate)
                            .map(MentorDayOff::getDate)
                            .collectList()
                            .flatMap(dayOffDates -> availabilityRepository
                                    .findByMentorIdAndDateBetween(mentor.getId(), startDate, endDate)
                                    .collectList()
                                    .flatMap(slots -> sessionRepository
                                            .findAllByMentorIdAndWorkoutDateBetween(mentor.getId(), startDate, endDate)
                                            .collectList()
                                            .flatMap(sessions -> {
                                                List<MentorAvailability> freeSlots = splitBySessions(slots, sessions);
                                                for (MentorAvailability s : freeSlots) {
                                                    if (s.getLocationId() != null) locationIds.add(s.getLocationId());
                                                }
                                                Map<Long, Map<String, Object>> locMap = new HashMap<>();
                                                if (!locationIds.isEmpty()) {
                                                    return Flux.fromIterable(locationIds)
                                                            .flatMap(id -> locationRepository.findById(id)
                                                                    .map(l -> {
                                                                        Map<String, Object> info = new HashMap<>();
                                                                        info.put("name", l.getName());
                                                                        info.put("color", l.getColor());
                                                                        return Map.entry(id, info);
                                                                    })
                                                                    .defaultIfEmpty(Map.entry(id, Map.<String, Object>of())))
                                                            .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                                                            .flatMap(lm -> {
                                                                locMap.putAll(lm);
                                                                if (date != null) {
                                                                    return generateSingleDayImage(mentor, freeSlots, locMap, date, dayOffDates);
                                                                }
                                                                return generateAvailabilityImage(mentor, freeSlots, locMap, today, dayOffDates);
                                                            })
                                                            .onErrorResume(e -> {
                                                                log.error("Failed to resolve locations for shared image", e);
                                                                if (date != null) {
                                                                    return generateSingleDayImage(mentor, freeSlots, Map.of(), date, dayOffDates);
                                                                }
                                                                return generateAvailabilityImage(mentor, freeSlots, Map.of(), today, dayOffDates);
                            });
                                                }
                                                if (date != null) {
                                                    return generateSingleDayImage(mentor, freeSlots, locMap, date, dayOffDates);
                                                }
                                                return generateAvailabilityImage(mentor, freeSlots, locMap, today, dayOffDates);
                                            }))
                            );
                })
                .map(bytes -> ResponseEntity.ok()
                        .header("Content-Type", "image/svg+xml")
                        .body(bytes))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private Mono<byte[]> generateAvailabilityImage(Mentor mentor, List<MentorAvailability> slots,
                                                     Map<Long, Map<String, Object>> locMap, LocalDate startDate,
                                                     List<LocalDate> dayOffDates) {
        return Mono.fromCallable(() -> {
            int dayCount = 14;
            int cellW = 80, cellH = 14;
            int topMargin = 70, bottomMargin = 40, leftMargin = 48, rightMargin = 20;
            int rows = (22 - 6) * 2;
            int totalW = leftMargin + dayCount * cellW + rightMargin;
            int totalH = topMargin + rows * cellH + bottomMargin;

            String[] days = {"Пн","Вт","Ср","Чт","Пт","Сб","Нд"};
            String[] months = {"січ","лют","бер","кві","тра","чер","лип","сер","вер","жов","лис","гру"};

            Map<Integer, List<MentorAvailability>> byDate = new HashMap<>();
            for (MentorAvailability s : slots) {
                int doy = (int) (s.getDate().toEpochDay() - startDate.toEpochDay());
                byDate.computeIfAbsent(doy, k -> new ArrayList<>()).add(s);
            }

            StringBuilder svg = new StringBuilder();
            svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(totalW).append("\" height=\"").append(totalH).append("\">\n");
            svg.append("<rect width=\"").append(totalW).append("\" height=\"").append(totalH).append("\" fill=\"#121212\"/>\n");

            svg.append("<text x=\"20\" y=\"26\" fill=\"#ffffff\" font-family=\"Arial,sans-serif\" font-size=\"16\" font-weight=\"bold\">")
                .append(xmlEscape(mentor.getName())).append(" — графік доступності</text>\n");

            for (int hi = 0; hi < rows; hi++) {
                int hh = 6 + hi / 2;
                int mm = (hi % 2) * 30;
                int y = topMargin + hi * cellH;
                String time = String.format("%02d:%02d", hh, mm);
                svg.append("<text x=\"5\" y=\"").append(y + cellH - 4).append("\" fill=\"#666666\" font-family=\"Arial,sans-serif\" font-size=\"9\" font-weight=\"bold\">")
                    .append(time).append("</text>\n");
                String rowColor = hi % 2 == 0 ? "#282828" : "#1c1c1c";
                svg.append("<line x1=\"").append(leftMargin).append("\" y1=\"").append(y).append("\" x2=\"").append(leftMargin + dayCount * cellW).append("\" y2=\"").append(y).append("\" stroke=\"").append(rowColor).append("\"/>\n");
            }

            for (int di = 0; di < dayCount; di++) {
                int x = leftMargin + di * cellW;
                LocalDate d = startDate.plusDays(di);
                boolean isToday = d.equals(LocalDate.now());

                String dayColor = isToday ? "#3b82f6" : "#a0a0a0";
                String dayLabel = days[(d.getDayOfWeek().getValue() + 6) % 7] + " " + d.getDayOfMonth() + " " + months[d.getMonthValue() - 1];
                svg.append("<text x=\"").append(x + 3).append("\" y=\"").append(topMargin - 5).append("\" fill=\"").append(dayColor).append("\" font-family=\"Arial,sans-serif\" font-size=\"11\" font-weight=\"bold\">")
                    .append(dayLabel).append("</text>\n");
                if (isToday) {
                    svg.append("<text x=\"").append(x + 3).append("\" y=\"").append(topMargin - 16).append("\" fill=\"#3b82f6\" font-family=\"Arial,sans-serif\" font-size=\"8\" font-weight=\"bold\">СЬОГОДНІ</text>\n");
                }

                svg.append("<line x1=\"").append(x).append("\" y1=\"").append(topMargin).append("\" x2=\"").append(x).append("\" y2=\"").append(topMargin + rows * cellH).append("\" stroke=\"#1e1e1e\"/>\n");

                boolean isDayOff = dayOffDates.contains(d);
                if (isDayOff) {
                    svg.append("<rect x=\"").append(x + 2).append("\" y=\"").append(topMargin).append("\" width=\"").append(cellW - 4).append("\" height=\"").append(rows * cellH).append("\" rx=\"4\" ry=\"4\" fill=\"#2a1a1a\" opacity=\"0.6\"/>\n");
                    svg.append("<text x=\"").append(x + cellW / 2).append("\" y=\"").append(topMargin + rows * cellH / 2).append("\" fill=\"#ef4444\" font-family=\"Arial,sans-serif\" font-size=\"10\" font-weight=\"bold\" text-anchor=\"middle\">ВИХІДНИЙ</text>\n");
                }

                List<MentorAvailability> daySlots = byDate.getOrDefault(di, List.of());
                for (MentorAvailability s : daySlots) {
                    int startMin = s.getStartTime().getHour() * 60 + s.getStartTime().getMinute();
                    int endMin = s.getEndTime().getHour() * 60 + s.getEndTime().getMinute();
                    int startRow = Math.max(0, (startMin - 360) / 30);
                    int endRow = Math.min(rows, (endMin - 360 + 29) / 30);

                    String colorStr = "#3b82f6";
                    String locName = "";
                    if (s.getLocationId() != null && locMap.containsKey(s.getLocationId())) {
                        Map<String, Object> loc = locMap.get(s.getLocationId());
                        colorStr = (String) loc.getOrDefault("color", "#3b82f6");
                        locName = (String) loc.getOrDefault("name", "");
                    }

                    int slotY = topMargin + startRow * cellH;
                    int slotH = (endRow - startRow) * cellH;
                    svg.append("<rect x=\"").append(x + 2).append("\" y=\"").append(slotY).append("\" width=\"").append(cellW - 4).append("\" height=\"").append(slotH).append("\" rx=\"4\" ry=\"4\" fill=\"").append(colorStr).append("\"/>\n");
                    if (!locName.isEmpty() && slotH > 20) {
                        svg.append("<text x=\"").append(x + 4).append("\" y=\"").append(slotY + 12).append("\" fill=\"#c8c8c8\" font-family=\"Arial,sans-serif\" font-size=\"8\">")
                            .append(xmlEscape(locName)).append("</text>\n");
                    }
                }
            }

            int legendY = totalH - 20;
            svg.append("<line x1=\"").append(leftMargin).append("\" y1=\"").append(topMargin + rows * cellH).append("\" x2=\"").append(leftMargin + dayCount * cellW).append("\" y2=\"").append(topMargin + rows * cellH).append("\" stroke=\"#3c3c3c\"/>\n");
            svg.append("<rect x=\"20\" y=\"").append(legendY - 5).append("\" width=\"12\" height=\"12\" rx=\"2\" ry=\"2\" fill=\"#3b82f6\"/>\n");
            svg.append("<text x=\"36\" y=\"").append(legendY + 4).append("\" fill=\"#c8c8c8\" font-family=\"Arial,sans-serif\" font-size=\"9\">Вільно</text>\n");

            int lx = 140;
            for (Map.Entry<Long, Map<String, Object>> e : locMap.entrySet()) {
                Map<String, Object> loc = e.getValue();
                String name = (String) loc.getOrDefault("name", "");
                String colorStr = (String) loc.getOrDefault("color", "#3b82f6");
                if (name.isEmpty()) continue;
                svg.append("<rect x=\"").append(lx).append("\" y=\"").append(legendY - 5).append("\" width=\"12\" height=\"12\" rx=\"2\" ry=\"2\" fill=\"").append(colorStr).append("\"/>\n");
                svg.append("<text x=\"").append(lx + 16).append("\" y=\"").append(legendY + 4).append("\" fill=\"#c8c8c8\" font-family=\"Arial,sans-serif\" font-size=\"9\">Вільно на ").append(xmlEscape(name)).append("</text>\n");
                lx += textWidth("Вільно на " + name, 9) + 24;
            }

            svg.append("</svg>");
            return svg.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        });
    }

    private Mono<byte[]> generateSingleDayImage(Mentor mentor, List<MentorAvailability> slots,
                                                   Map<Long, Map<String, Object>> locMap, LocalDate date,
                                                   List<LocalDate> dayOffDates) {
        return Mono.fromCallable(() -> {
            int cellW = 1088, cellH = 28;
            int topMargin = 100, bottomMargin = 60, leftMargin = 60, rightMargin = 40;
            int rows = (22 - 6) * 2;
            int totalW = leftMargin + cellW + rightMargin;
            int totalH = topMargin + rows * cellH + bottomMargin;

            String[] days = {"Пн","Вт","Ср","Чт","Пт","Сб","Нд"};
            String[] months = {"січ","лют","бер","кві","тра","чер","лип","сер","вер","жов","лис","гру"};
            int dayIdx = (date.getDayOfWeek().getValue() + 6) % 7;
            String dateStr = days[dayIdx] + ", " + date.getDayOfMonth() + " " + months[date.getMonthValue() - 1];

            StringBuilder svg = new StringBuilder();
            svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(totalW).append("\" height=\"").append(totalH).append("\">\n");
            svg.append("<rect width=\"").append(totalW).append("\" height=\"").append(totalH).append("\" fill=\"#121212\"/>\n");

            svg.append("<text x=\"20\" y=\"36\" fill=\"#ffffff\" font-family=\"Arial,sans-serif\" font-size=\"22\" font-weight=\"bold\">")
                .append(xmlEscape(mentor.getName())).append("</text>\n");
            svg.append("<text x=\"20\" y=\"61\" fill=\"#a0a0a0\" font-family=\"Arial,sans-serif\" font-size=\"16\" font-weight=\"bold\">")
                .append(dateStr).append("</text>\n");

            if (date.equals(LocalDate.now())) {
                svg.append("<rect x=\"20\" y=\"68\" width=\"62\" height=\"14\" rx=\"7\" ry=\"7\" fill=\"#3b82f6\"/>\n");
                svg.append("<text x=\"51\" y=\"79\" fill=\"#ffffff\" font-family=\"Arial,sans-serif\" font-size=\"10\" font-weight=\"bold\" text-anchor=\"middle\">СЬОГОДНІ</text>\n");
            }

            int startMin = 360;
            for (int hi = 0; hi < rows; hi++) {
                int hh = 6 + hi / 2;
                int mm = (hi % 2) * 30;
                int y = topMargin + hi * cellH;
                String time = String.format("%02d:%02d", hh, mm);
                svg.append("<text x=\"5\" y=\"").append(y + cellH - 8).append("\" fill=\"#a0a0a0\" font-family=\"Arial,sans-serif\" font-size=\"13\" font-weight=\"bold\">")
                    .append(time).append("</text>\n");
                String rowColor = hi % 2 == 0 ? "#282828" : "#1c1c1c";
                svg.append("<rect x=\"").append(leftMargin).append("\" y=\"").append(y).append("\" width=\"").append(cellW).append("\" height=\"").append(cellH).append("\" fill=\"").append(rowColor).append("\"/>\n");
                svg.append("<line x1=\"").append(leftMargin).append("\" y1=\"").append(y).append("\" x2=\"").append(leftMargin + cellW).append("\" y2=\"").append(y).append("\" stroke=\"#323232\"/>\n");
            }

            svg.append("<line x1=\"").append(leftMargin + cellW).append("\" y1=\"").append(topMargin).append("\" x2=\"").append(leftMargin + cellW).append("\" y2=\"").append(topMargin + rows * cellH).append("\" stroke=\"#323232\"/>\n");

            boolean isDayOff = dayOffDates.contains(date);
            if (isDayOff) {
                svg.append("<rect x=\"").append(leftMargin + 3).append("\" y=\"").append(topMargin + 2).append("\" width=\"").append(cellW - 6).append("\" height=\"").append(rows * cellH - 4).append("\" rx=\"10\" ry=\"10\" fill=\"#2a1a1a\" opacity=\"0.5\"/>\n");
                svg.append("<text x=\"").append(leftMargin + cellW / 2).append("\" y=\"").append(topMargin + rows * cellH / 2).append("\" fill=\"#ef4444\" font-family=\"Arial,sans-serif\" font-size=\"24\" font-weight=\"bold\" text-anchor=\"middle\">ВИХІДНИЙ</text>\n");
            }

            for (MentorAvailability s : slots) {
                if (!s.getDate().equals(date)) continue;
                int sStart = s.getStartTime().getHour() * 60 + s.getStartTime().getMinute();
                int sEnd = s.getEndTime().getHour() * 60 + s.getEndTime().getMinute();
                int startRow = Math.max(0, (sStart - startMin) / 30);
                int endRow = Math.min(rows, (sEnd - startMin + 29) / 30);

                String colorStr = "#3b82f6";
                String locName = "";
                if (s.getLocationId() != null && locMap.containsKey(s.getLocationId())) {
                    Map<String, Object> loc = locMap.get(s.getLocationId());
                    colorStr = (String) loc.getOrDefault("color", "#3b82f6");
                    locName = (String) loc.getOrDefault("name", "");
                }

                int slotY = topMargin + startRow * cellH;
                int slotH = (endRow - startRow) * cellH;
                svg.append("<rect x=\"").append(leftMargin + 3).append("\" y=\"").append(slotY + 2).append("\" width=\"").append(cellW - 6).append("\" height=\"").append(slotH - 2).append("\" rx=\"6\" ry=\"6\" fill=\"").append(colorStr).append("\"/>\n");
                if (!locName.isEmpty() && slotH > 28) {
                    svg.append("<text x=\"").append(leftMargin + 10).append("\" y=\"").append(slotY + 20).append("\" fill=\"#ffffff\" font-family=\"Arial,sans-serif\" font-size=\"13\" font-weight=\"bold\">")
                        .append(xmlEscape(locName)).append("</text>\n");
                }
            }

            int legendY = totalH - 28;
            svg.append("<line x1=\"").append(leftMargin).append("\" y1=\"").append(topMargin + rows * cellH).append("\" x2=\"").append(leftMargin + cellW).append("\" y2=\"").append(topMargin + rows * cellH).append("\" stroke=\"#3c3c3c\"/>\n");
            svg.append("<rect x=\"20\" y=\"").append(legendY - 5).append("\" width=\"14\" height=\"14\" rx=\"2\" ry=\"2\" fill=\"#3b82f6\"/>\n");
            svg.append("<text x=\"40\" y=\"").append(legendY + 8).append("\" fill=\"#c8c8c8\" font-family=\"Arial,sans-serif\" font-size=\"11\">Вільно</text>\n");

            int lx = 120;
            for (Map.Entry<Long, Map<String, Object>> e : locMap.entrySet()) {
                Map<String, Object> loc = e.getValue();
                String name = (String) loc.getOrDefault("name", "");
                String colorStr = (String) loc.getOrDefault("color", "#3b82f6");
                if (name.isEmpty()) continue;
                svg.append("<rect x=\"").append(lx).append("\" y=\"").append(legendY - 5).append("\" width=\"14\" height=\"14\" rx=\"2\" ry=\"2\" fill=\"").append(colorStr).append("\"/>\n");
                svg.append("<text x=\"").append(lx + 20).append("\" y=\"").append(legendY + 8).append("\" fill=\"#c8c8c8\" font-family=\"Arial,sans-serif\" font-size=\"11\">Вільно на ").append(xmlEscape(name)).append("</text>\n");
                lx += textWidth("Вільно на " + name, 11) + 30;
            }

            svg.append("</svg>");
            return svg.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        });
    }

    private String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private int textWidth(String text, int fontSize) {
        return text.length() * fontSize / 2;
    }

    private List<MentorAvailability> splitBySessions(List<MentorAvailability> slots, List<Session> sessions) {
        if (sessions.isEmpty()) return slots;
        List<MentorAvailability> result = new ArrayList<>();
        for (MentorAvailability slot : slots) {
            List<Session> overlapping = sessions.stream()
                    .filter(s -> s.getWorkoutDate().equals(slot.getDate())
                            && s.getEndTime() != null
                            && slot.getEndTime() != null
                            && slot.getStartTime().isBefore(s.getEndTime())
                            && s.getStartTime().isBefore(slot.getEndTime()))
                    .sorted(Comparator.comparing(Session::getStartTime))
                    .toList();
            if (overlapping.isEmpty()) {
                result.add(slot);
                continue;
            }
            LocalTime cur = slot.getStartTime();
            for (Session session : overlapping) {
                if (cur.isBefore(session.getStartTime())) {
                    MentorAvailability free = new MentorAvailability();
                    free.setMentorId(slot.getMentorId());
                    free.setDate(slot.getDate());
                    free.setStartTime(cur);
                    free.setEndTime(session.getStartTime());
                    free.setLocationId(slot.getLocationId());
                    result.add(free);
                }
                if (session.getEndTime().isAfter(cur)) {
                    cur = session.getEndTime();
                }
            }
            if (cur.isBefore(slot.getEndTime())) {
                MentorAvailability free = new MentorAvailability();
                free.setMentorId(slot.getMentorId());
                free.setDate(slot.getDate());
                free.setStartTime(cur);
                free.setEndTime(slot.getEndTime());
                free.setLocationId(slot.getLocationId());
                result.add(free);
            }
        }
        return result;
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record SlotEntry(LocalDate date, LocalTime startTime, LocalTime endTime, Long locationId) {}
}
