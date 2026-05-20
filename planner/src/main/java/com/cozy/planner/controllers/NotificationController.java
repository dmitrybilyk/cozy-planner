package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Notification;
import com.cozy.planner.repositories.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/notifications")
    public Mono<ResponseEntity<?>> getNotifications(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            Object traineeId = session.getAttribute("trainee_id");
            Object mentorId = session.getAttribute("mentor_id");
            Object userId = session.getAttribute("google_sub");

            if (traineeId instanceof Number) {
                return notificationRepository.findAllByTraineeIdOrderByCreatedAtDesc(((Number) traineeId).longValue())
                        .collectList()
                        .map(ResponseEntity::ok);
            }
            if (mentorId instanceof Number) {
                return notificationRepository.findAllByMentorIdOrderByCreatedAtDesc(((Number) mentorId).longValue())
                        .collectList()
                        .map(ResponseEntity::ok);
            }
            if (userId != null) {
                return Mono.just(ResponseEntity.ok(java.util.List.of()));
            }
            return Mono.just(ResponseEntity.ok(java.util.List.of()));
        });
    }

    @GetMapping("/notifications/unread-count")
    public Mono<ResponseEntity<Map<String, Object>>> getUnreadCount(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            Object traineeId = session.getAttribute("trainee_id");
            Object mentorId = session.getAttribute("mentor_id");

            Mono<Long> countMono;
            if (traineeId instanceof Number) {
                countMono = notificationRepository.countByTraineeIdAndIsReadFalse(((Number) traineeId).longValue());
            } else if (mentorId instanceof Number) {
                countMono = notificationRepository.countByMentorIdAndIsReadFalse(((Number) mentorId).longValue());
            } else {
                countMono = Mono.just(0L);
            }

            return countMono.map(count -> {
                Map<String, Object> r = new HashMap<>();
                r.put("count", count);
                return ResponseEntity.ok(r);
            });
        });
    }

    @PostMapping("/notifications/{id}/read")
    public Mono<ResponseEntity<Map<String, Object>>> markAsRead(@PathVariable Long id) {
        return notificationRepository.findById(id)
                .flatMap(n -> {
                    n.setIsRead(true);
                    return notificationRepository.save(n);
                })
                .map(n -> {
                    Map<String, Object> r = new HashMap<>();
                    r.put("success", true);
                    return ResponseEntity.ok(r);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/notifications/read-all")
    public Mono<ResponseEntity<Map<String, Object>>> markAllAsRead(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            Object traineeId = session.getAttribute("trainee_id");
            Object mentorId = session.getAttribute("mentor_id");

            Flux<com.cozy.planner.model.entity.Notification> notifications;
            if (traineeId instanceof Number) {
                notifications = notificationRepository.findAllByTraineeIdOrderByCreatedAtDesc(((Number) traineeId).longValue());
            } else if (mentorId instanceof Number) {
                notifications = notificationRepository.findAllByMentorIdOrderByCreatedAtDesc(((Number) mentorId).longValue());
            } else {
                notifications = Flux.empty();
            }

            return notifications
                    .filter(n -> !n.getIsRead())
                    .flatMap(n -> {
                        n.setIsRead(true);
                        return notificationRepository.save(n);
                    })
                    .then(Mono.fromCallable(() -> {
                        Map<String, Object> r = new HashMap<>();
                        r.put("success", true);
                        return ResponseEntity.ok(r);
                    }));
        });
    }
}
