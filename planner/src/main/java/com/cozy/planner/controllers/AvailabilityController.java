package com.cozy.planner.controllers;

import com.cozy.planner.config.TelegramConfig;
import com.cozy.planner.model.entity.Notification;
import com.cozy.planner.model.entity.TraineeAvailability;
import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.repositories.NotificationRepository;
import com.cozy.planner.repositories.AvailabilityRangeRepository;
import com.cozy.planner.repositories.TraineeAvailabilityRepository;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.TraineeRepository;
import com.cozy.planner.service.EventBroadcastService;
import com.cozy.planner.service.NotificationService;
import com.cozy.planner.service.AvailabilityMergeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class AvailabilityController {

    private final TraineeRepository traineeRepository;
    private final TraineeAvailabilityRepository availabilityRepository;
    private final AvailabilityRangeRepository rangeRepository;
    private final MentorRepository mentorRepository;
    private final EventBroadcastService eventService;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final TelegramConfig telegramConfig;
    private final SecureRandom secureRandom = new SecureRandom();

    public AvailabilityController(TraineeRepository traineeRepository,
                                   TraineeAvailabilityRepository availabilityRepository,
                                   AvailabilityRangeRepository rangeRepository,
                                   MentorRepository mentorRepository,
                                   EventBroadcastService eventService,
                                   NotificationRepository notificationRepository,
                                   NotificationService notificationService,
                                   TelegramConfig telegramConfig) {
        this.traineeRepository = traineeRepository;
        this.availabilityRepository = availabilityRepository;
        this.rangeRepository = rangeRepository;
        this.mentorRepository = mentorRepository;
        this.eventService = eventService;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.telegramConfig = telegramConfig;
    }

    @PostMapping(path = {"/api/v1/trainees/{traineeId}/generate-invite"})
    public Mono<ResponseEntity<Map<String, String>>> generateInvite(@PathVariable Long traineeId, ServerWebExchange exchange) {
        return getMentorId(exchange)
                .flatMap(mentorId -> traineeRepository.findById(traineeId)
                        .flatMap(trainee -> {
                            if (!trainee.getMentorId().equals(mentorId)) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).<Map<String, String>>build());
                            }
                            if (trainee.getInviteToken() != null) {
                                Map<String, String> body = new HashMap<>();
                                body.put("inviteUrl", buildInviteUrl(exchange, trainee.getInviteToken()));
                                return Mono.just(ResponseEntity.ok(body));
                            }
                            String token = generateToken();
                            trainee.setInviteToken(token);
                            return traineeRepository.save(trainee).map(saved -> {
                                Map<String, String> body = new HashMap<>();
                                body.put("inviteUrl", buildInviteUrl(exchange, saved.getInviteToken()));
                                return ResponseEntity.ok(body);
                            });
                        })
                        .defaultIfEmpty(ResponseEntity.notFound().build()));
    }

    @GetMapping(path = {"/api/v1/trainee/invite"})
    public Mono<ResponseEntity<Map<String, Object>>> checkInvite(@RequestParam String token) {
        return traineeRepository.findByInviteToken(token)
                .map(trainee -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("traineeId", trainee.getId());
                    body.put("athleteId", trainee.getId());
                    body.put("name", trainee.getName());
                    return ResponseEntity.ok(body);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping(path = {"/api/v1/trainees/{traineeId}/availability"})
    public Flux<TraineeAvailability> getAvailability(@PathVariable Long traineeId,
                                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return availabilityRepository.findByTraineeIdAndDateBetween(traineeId, startDate, endDate);
    }

    @GetMapping(path = {"/api/v1/mentors/{mentorId}/availability"})
    public Flux<Map<String, Object>> getTraineesAvailability(@PathVariable("mentorId") Long mentorId,
                                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        ZoneId zone = ZoneId.of("Europe/Kiev");
        return traineeRepository.findAllByMentorId(mentorId)
                .flatMap(trainee -> {
                    Flux<Map<String, String>> oldSlots = availabilityRepository
                            .findByTraineeIdAndDateBetween(trainee.getId(), startDate, endDate)
                            .map(a -> {
                                Map<String, String> slot = new HashMap<>();
                                slot.put("date", a.getDate().toString());
                                slot.put("startTime", a.getStartTime().toString());
                                slot.put("endTime", a.getEndTime().toString());
                                return slot;
                            });
                    Flux<Map<String, String>> newSlots = rangeRepository
                            .findByUserIdAndUserTypeAndDateBetween(trainee.getId(), "TRAINEE", startDate, endDate)
                            .flatMap(r -> {
                                if (r.getFreeAllDay() != null && r.getFreeAllDay()) {
                                    Map<String, String> slot = new HashMap<>();
                                    slot.put("date", r.getDate().toString());
                                    slot.put("startTime", "all_day");
                                    slot.put("endTime", "all_day");
                                    return Flux.just(slot);
                                }
                                Map<String, String> slot = new HashMap<>();
                                slot.put("date", r.getDate().toString());
                                slot.put("startTime", r.getStartTime().atZoneSameInstant(zone).toLocalTime().toString());
                                slot.put("endTime", r.getEndTime().atZoneSameInstant(zone).toLocalTime().toString());
                                return Flux.just(slot);
                            });
                    return Flux.concat(oldSlots, newSlots)
                            .distinct(s -> s.get("date") + "|" + s.get("startTime") + "|" + s.get("endTime"))
                            .collectList()
                            .map(slots -> {
                                Map<String, Object> entry = new HashMap<>();
                                entry.put("traineeId", trainee.getId());
                                entry.put("athleteId", trainee.getId());
                                entry.put("traineeName", trainee.getName());
                                entry.put("athleteName", trainee.getName());
                                entry.put("slots", slots);
                                return entry;
                            });
                });
    }

    @PostMapping(path = {"/api/v1/trainees/{traineeId}/availability"})
    public Mono<ResponseEntity<List<Map<String, Object>>>> setAvailabilityById(@PathVariable Long traineeId,
                                                            @RequestBody List<SlotEntry> entries,
                                                            ServerWebExchange exchange) {
        return saveAvailabilityInternal(traineeId, entries, baseUrl(exchange));
    }

    @PostMapping(path = {"/api/v1/trainee/availability"})
    public Mono<ResponseEntity<List<Map<String, Object>>>> setAvailability(@RequestBody List<SlotEntry> entries,
                                                         ServerWebExchange exchange) {
        return getTraineeId(exchange)
                .flatMap(traineeId -> saveAvailabilityInternal(traineeId, entries, baseUrl(exchange)));
    }

    @DeleteMapping(path = {"/api/v1/trainees/{traineeId}/availability"})
    public Mono<ResponseEntity<Void>> clearAvailabilityById(@PathVariable Long traineeId,
                                                             @RequestParam String dates,
                                                             ServerWebExchange exchange) {
        return clearAvailabilityInternal(traineeId, dates, baseUrl(exchange));
    }

    @DeleteMapping(path = {"/api/v1/trainee/availability"})
    public Mono<ResponseEntity<Void>> clearAvailability(@RequestParam String dates,
                                                           ServerWebExchange exchange) {
        return getTraineeId(exchange)
                .flatMap(traineeId -> clearAvailabilityInternal(traineeId, dates, baseUrl(exchange)));
    }

    private String baseUrl(ServerWebExchange exchange) {
        String host = exchange.getRequest().getURI().getHost();
        int port = exchange.getRequest().getURI().getPort();
        String scheme = exchange.getRequest().getURI().getScheme();
        return scheme + "://" + host + (port > 0 ? ":" + port : "");
    }

    private Mono<ResponseEntity<List<Map<String, Object>>>> saveAvailabilityInternal(Long traineeId, List<SlotEntry> entries, String baseUrl) {
        Set<LocalDate> uniqueDates = entries.stream()
                .map(SlotEntry::date)
                .collect(Collectors.toSet());

        if (uniqueDates.isEmpty()) {
            return Mono.just(ResponseEntity.ok(List.of()));
        }

        // Convert to TraineeSlot, merge, then convert to TraineeAvailability
        List<AvailabilityMergeService.TraineeSlot> traineeSlots = entries.stream()
                .map(e -> new AvailabilityMergeService.TraineeSlot(e.date, e.startTime, e.endTime))
                .toList();
        
        List<AvailabilityMergeService.TraineeSlot> mergedSlots = AvailabilityMergeService.mergeTraineeIntervals(traineeSlots);

        List<TraineeAvailability> toSave = mergedSlots.stream()
                .map(slot -> {
                    TraineeAvailability ta = new TraineeAvailability();
                    ta.setTraineeId(traineeId);
                    ta.setDate(slot.date());
                    ta.setStartTime(slot.startTime());
                    ta.setEndTime(slot.endTime());
                    return ta;
                })
                .toList();

         return traineeRepository.findById(traineeId)
                .defaultIfEmpty(Trainee.builder().mentorId(-1L).name("невідомий").build())
                .flatMap(trainee -> Flux.fromIterable(uniqueDates)
                        .flatMap(date -> availabilityRepository.findByTraineeIdAndDate(traineeId, date))
                        .flatMap(availabilityRepository::delete)
                        .thenMany(Flux.fromIterable(toSave))
                        .flatMap(availabilityRepository::save)
                        .collectList()
                        .flatMap(savedList -> {
                            eventService.broadcast("availability_changed");
                            List<Map<String, Object>> response = savedList.stream()
                                    .map(s -> {
                                        Map<String, Object> slot = new HashMap<>();
                                        slot.put("id", s.getId());
                                        slot.put("date", s.getDate().toString());
                                        slot.put("startTime", s.getStartTime().toString());
                                        slot.put("endTime", s.getEndTime().toString());
                                        return slot;
                                    })
                                    .toList();
                            return createAvailabilityNotification(trainee.getMentorId(), trainee.getName(), baseUrl)
                                    .then(Mono.just(ResponseEntity.ok(response)));
                        }));
    }

    private Mono<ResponseEntity<Void>> clearAvailabilityInternal(Long traineeId, String dates, String baseUrl) {
        List<LocalDate> dateList = Arrays.stream(dates.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(LocalDate::parse)
                .toList();

        if (dateList.isEmpty()) {
            return Mono.just(ResponseEntity.ok().<Void>build());
        }

         return traineeRepository.findById(traineeId)
                .defaultIfEmpty(Trainee.builder().mentorId(-1L).name("невідомий").build())
                .flatMap(trainee -> Flux.fromIterable(dateList)
                        .flatMap(date -> availabilityRepository.findByTraineeIdAndDate(traineeId, date))
                        .flatMap(availabilityRepository::delete)
                        .then()
                        .then(Mono.fromRunnable(() -> eventService.broadcast("availability_changed")))
                        .then(createAvailabilityNotification(trainee.getMentorId(), trainee.getName(), baseUrl))
                        .then(Mono.just(ResponseEntity.ok().<Void>build())));
    }

    private Mono<Void> createAvailabilityNotification(Long mentorId, String traineeName, String baseUrl) {
        if (mentorId == null || mentorId < 0) return Mono.empty();
        String title = "Нова доступність";
        String message = traineeName + " оновив свою доступність";
        Notification n = Notification.builder()
                .mentorId(mentorId)
                .title(title)
                .message(message)
                .type("AVAILABILITY_CHANGED")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        return notificationRepository.save(n)
                .flatMap(saved -> {
                    Map<String, Object> evt = new HashMap<>();
                    evt.put("type", "notification");
                    evt.put("id", saved.getId());
                    evt.put("mentorId", saved.getMentorId());
                    evt.put("title", saved.getTitle());
                    evt.put("message", saved.getMessage());
                    evt.put("notificationType", saved.getType());
                    evt.put("isRead", saved.getIsRead());
                    evt.put("createdAt", saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : null);
                    eventService.broadcastJson(evt);
                    return mentorRepository.findById(mentorId)
                            .filter(m -> m.getTelegramChatId() != null && !m.getTelegramChatId().isBlank() && m.isTelegramIntegrationEnabled())
                            .flatMap(m -> {
                                Map<String, Object> btn = Map.of("text", "📅 Відкрити календар", "url", baseUrl + "/planner");
                                Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(List.of(btn)));
                                return notificationService.sendMessageToMentor(m.getTelegramChatId(), title + "\n" + message, keyboard);
                            })
                            .then();
                });
    }

    private Mono<Long> getMentorId(ServerWebExchange exchange) {
        return exchange.getSession().map(session -> {
            Object mentorId = session.getAttribute("mentor_id");
            if (mentorId instanceof Number) return ((Number) mentorId).longValue();
            return -1L;
        });
    }

    private Mono<Long> getTraineeId(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            Object traineeId = session.getAttribute("trainee_id");
            if (traineeId instanceof Number) {
                long id = ((Number) traineeId).longValue();
                if (id > 0) {
                    return traineeRepository.findById(id)
                            .map(Trainee::getId);
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
        if (telegramConfig.isEnabled() && telegramConfig.getBotUsername() != null) {
            return "https://t.me/" + telegramConfig.getBotUsername() + "?start=" + token;
        }
        String host = exchange.getRequest().getURI().getHost();
        int port = exchange.getRequest().getURI().getPort();
        String scheme = exchange.getRequest().getURI().getScheme();
        String base = scheme + "://" + host + (port > 0 ? ":" + port : "");
        return base + "/trainee/" + token;
    }

    public record SlotEntry(LocalDate date, LocalTime startTime, LocalTime endTime) {}
}
