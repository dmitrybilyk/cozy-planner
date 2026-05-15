package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Location;
import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.MentorAvailability;
import com.cozy.planner.model.entity.Session;
import com.cozy.planner.repositories.LocationRepository;
import com.cozy.planner.repositories.MentorAvailabilityRepository;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.SessionRepository;
import com.cozy.planner.service.EventBroadcastService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@RestController
public class CoachAvailabilityController {

    private final MentorRepository mentorRepository;
    private final MentorAvailabilityRepository availabilityRepository;
    private final SessionRepository sessionRepository;
    private final LocationRepository locationRepository;
    private final EventBroadcastService eventService;
    private final SecureRandom secureRandom = new SecureRandom();

    public CoachAvailabilityController(MentorRepository mentorRepository,
                                       MentorAvailabilityRepository availabilityRepository,
                                       SessionRepository sessionRepository,
                                       LocationRepository locationRepository,
                                       EventBroadcastService eventService) {
        this.mentorRepository = mentorRepository;
        this.availabilityRepository = availabilityRepository;
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
                    return availabilityRepository
                            .findByMentorIdAndDateBetween(mentor.getId(), startDate, endDate)
                            .collectList()
                            .flatMap(slots -> {
                                for (MentorAvailability s : slots) {
                                    if (s.getLocationId() != null) locationIds.add(s.getLocationId());
                                }
                                if (locationIds.isEmpty()) {
                                    return buildSharedResponse(mentor, slots, Map.of());
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
                                        .flatMap(locMap -> buildSharedResponse(mentor, slots, locMap));
                            });
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @SuppressWarnings("unchecked")
    private Mono<ResponseEntity<Map<String, Object>>> buildSharedResponse(Mentor mentor,
                                                                           List<MentorAvailability> slots,
                                                                           Map<Long, Map<String, Object>> locMap) {
        Map<String, Object> body = new HashMap<>();
        body.put("mentorId", mentor.getId());
        body.put("mentorName", mentor.getName());
        body.put("profile", mentor.getProfile());
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
    public Mono<ResponseEntity<Map<String, Object>>> getSharedMentorInfo(@PathVariable String shareToken) {
        return mentorRepository.findByShareToken(shareToken)
                .map(mentor -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("id", mentor.getId());
                    body.put("name", mentor.getName());
                    body.put("profile", mentor.getProfile());
                    return ResponseEntity.ok(body);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/v1/shared/{shareToken}/image")
    public Mono<ResponseEntity<byte[]>> getSharedAvailabilityImage(@PathVariable String shareToken) {
        return mentorRepository.findByShareToken(shareToken)
                .flatMap(mentor -> {
                    LocalDate today = LocalDate.now();
                    LocalDate endDate = today.plusDays(13);
                    Set<Long> locationIds = new HashSet<>();
                    return availabilityRepository
                            .findByMentorIdAndDateBetween(mentor.getId(), today, endDate)
                            .collectList()
                            .flatMap(slots -> {
                                for (MentorAvailability s : slots) {
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
                                                return generateAvailabilityImage(mentor, slots, locMap, today);
                                            });
                                }
                                return generateAvailabilityImage(mentor, slots, locMap, today);
                            });
                })
                .map(bytes -> ResponseEntity.ok()
                        .header("Content-Type", "image/png")
                        .body(bytes))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private Mono<byte[]> generateAvailabilityImage(Mentor mentor, List<MentorAvailability> slots,
                                                    Map<Long, Map<String, Object>> locMap, LocalDate startDate) {
        return Mono.fromCallable(() -> {
            int dayCount = 7;
            int cellW = 90, cellH = 14;
            int headerH = 50, topMargin = 70, bottomMargin = 40, leftMargin = 55, rightMargin = 20;
            int timeLabelW = 45;
            int rows = (22 - 6) * 2;
            int totalW = leftMargin + dayCount * cellW + rightMargin;
            int totalH = topMargin + rows * cellH + bottomMargin;

            BufferedImage img = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.setColor(new Color(18, 18, 18));
            g.fillRect(0, 0, totalW, totalH);

            g.setColor(new Color(255, 255, 255));
            g.setFont(new Font("Arial", Font.BOLD, 16));
            g.drawString(mentor.getName() + " — графік доступності", 20, 30);

            String[] days = {"Пн","Вт","Ср","Чт","Пт","Сб","Нд"};
            String[] months = {"січ","лют","бер","кві","тра","чер","лип","сер","вер","жов","лис","гру"};

            Map<Integer, java.util.List<MentorAvailability>> byDate = new HashMap<>();
            for (MentorAvailability s : slots) {
                int doy = (int) (s.getDate().toEpochDay() - startDate.toEpochDay());
                byDate.computeIfAbsent(doy, k -> new ArrayList<>()).add(s);
            }

            g.setFont(new Font("Arial", Font.BOLD, 9));
            String[] timeLabels = new String[rows];
            for (int i = 0; i < rows; i++) {
                int h = 6 + i / 2;
                int m = (i % 2) * 30;
                timeLabels[i] = String.format("%02d:%02d", h, m);
            }
            for (int hi = 0; hi < rows; hi++) {
                int y = topMargin + hi * cellH;
                g.setColor(new Color(100, 100, 100));
                g.drawString(timeLabels[hi], 5, y + cellH - 4);

                g.setColor(hi % 2 == 0 ? new Color(40, 40, 40) : new Color(28, 28, 28));
                g.drawLine(leftMargin, y, leftMargin + dayCount * cellW, y);
            }

            for (int di = 0; di < dayCount; di++) {
                int x = leftMargin + di * cellW;
                LocalDate d = startDate.plusDays(di);
                boolean isToday = d.equals(LocalDate.now());

                g.setColor(isToday ? new Color(59, 130, 246) : new Color(160, 160, 160));
                g.setFont(new Font("Arial", Font.BOLD, 11));
                String dayLabel = days[(d.getDayOfWeek().getValue() + 6) % 7] + " " + d.getDayOfMonth() + " " + months[d.getMonthValue() - 1];
                g.drawString(dayLabel, x + 3, topMargin - 5);
                if (isToday) {
                    g.setFont(new Font("Arial", Font.BOLD, 8));
                    g.drawString("СЬОГОДНІ", x + 3, topMargin - 16);
                }

                g.setColor(new Color(30, 30, 30));
                g.drawLine(x, topMargin, x, topMargin + rows * cellH);

                java.util.List<MentorAvailability> daySlots = byDate.getOrDefault(di, List.of());
                for (MentorAvailability s : daySlots) {
                    int startMin = s.getStartTime().getHour() * 60 + s.getStartTime().getMinute();
                    int endMin = s.getEndTime().getHour() * 60 + s.getEndTime().getMinute();
                    int startRow = Math.max(0, (startMin - 360) / 30);
                    int endRow = Math.min(rows, (endMin - 360 + 29) / 30);

                    Color slotColor;
                    if (s.getLocationId() != null && locMap.containsKey(s.getLocationId())) {
                        Map<String, Object> loc = locMap.get(s.getLocationId());
                        String colorStr = (String) loc.getOrDefault("color", "#3b82f5");
                        try {
                            slotColor = Color.decode(colorStr);
                        } catch (Exception e) {
                            slotColor = new Color(59, 130, 246);
                        }
                    } else {
                        slotColor = new Color(59, 130, 246);
                    }

                    g.setColor(new Color(slotColor.getRed(), slotColor.getGreen(), slotColor.getBlue(), 100));
                    int slotY = topMargin + startRow * cellH;
                    int slotH = (endRow - startRow) * cellH;
                    g.fillRoundRect(x + 2, slotY, cellW - 4, slotH, 4, 4);

                    g.setColor(slotColor);
                    g.drawRoundRect(x + 2, slotY, cellW - 4, slotH, 4, 4);

                    if (s.getLocationId() != null && locMap.containsKey(s.getLocationId())) {
                        Map<String, Object> loc = locMap.get(s.getLocationId());
                        String locName = (String) loc.getOrDefault("name", "");
                        if (!locName.isEmpty() && slotH > 20) {
                            g.setFont(new Font("Arial", Font.PLAIN, 8));
                            g.setColor(new Color(200, 200, 200));
                            g.drawString(locName, x + 4, slotY + 12);
                        }
                    }
                }
            }

            g.setColor(new Color(60, 60, 60));
            g.drawLine(leftMargin, topMargin + rows * cellH, leftMargin + dayCount * cellW, topMargin + rows * cellH);

            g.setFont(new Font("Arial", Font.PLAIN, 9));
            g.setColor(new Color(120, 120, 120));
            int legendY = totalH - 20;
            g.fillRect(20, legendY - 5, 12, 12);
            g.setColor(new Color(59, 130, 246, 100));
            g.fillRect(20, legendY - 5, 12, 12);
            g.setColor(new Color(120, 120, 120));
            g.drawString("Доступно", 36, legendY + 4);

            g.setColor(new Color(120, 120, 120));
            g.drawRect(100, legendY - 5, 12, 12);
            g.setColor(new Color(120, 120, 120));
            g.drawString("Вільно", 116, legendY + 4);

            g.dispose();

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        });
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record SlotEntry(LocalDate date, LocalTime startTime, LocalTime endTime, Long locationId) {}
}
