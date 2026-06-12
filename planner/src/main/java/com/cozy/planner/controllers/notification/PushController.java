package com.cozy.planner.controllers.notification;

import com.cozy.planner.model.entity.PushSubscription;
import com.cozy.planner.repositories.PushSubscriptionRepository;
import com.cozy.planner.service.push.PushService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/push")
public class PushController {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PushService pushService;

    public PushController(PushSubscriptionRepository pushSubscriptionRepository,
                          PushService pushService) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.pushService = pushService;
    }

    @GetMapping("/vapid-key")
    public Mono<ResponseEntity<Map<String, String>>> getVapidKey() {
        String key = pushService.getVapidPublicKeyRawB64();
        if (key == null) return Mono.just(ResponseEntity.ok(Map.of()));
        return Mono.just(ResponseEntity.ok(Map.of("vapidKey", key)));
    }

    @PostMapping("/subscribe")
    public Mono<ResponseEntity<Map<String, Object>>> subscribe(@RequestBody Map<String, String> body,
                                                                ServerWebExchange exchange) {
        String endpoint = body.get("endpoint");
        String authKey = body.get("authKey");
        String p256dhKey = body.get("p256dhKey");
        if (endpoint == null || authKey == null || p256dhKey == null) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "Missing fields")));
        }
        return exchange.getSession().flatMap(session -> {
            Object traineeId = session.getAttribute("trainee_id");
            Object mentorId = session.getAttribute("mentor_id");
            PushSubscription sub = new PushSubscription();
            sub.setEndpoint(endpoint);
            sub.setAuthKey(authKey);
            sub.setP256dhKey(p256dhKey);
            sub.setCreatedAt(LocalDateTime.now());
            if (traineeId instanceof Number) {
                sub.setTraineeId(((Number) traineeId).longValue());
            } else if (mentorId instanceof Number) {
                sub.setMentorId(((Number) mentorId).longValue());
            } else {
                return Mono.just(ResponseEntity.status(401).body(Map.of("error", "Not authenticated")));
            }
            return pushSubscriptionRepository.deleteByEndpoint(endpoint)
                    .then(pushSubscriptionRepository.save(sub))
                    .then(Mono.fromCallable(() -> ResponseEntity.ok(Map.of("success", true))));
        });
    }

    @PostMapping("/unsubscribe")
    public Mono<ResponseEntity<Map<String, Object>>> unsubscribe(@RequestBody Map<String, String> body) {
        String endpoint = body.get("endpoint");
        if (endpoint == null) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "Missing endpoint")));
        }
        return pushSubscriptionRepository.deleteByEndpoint(endpoint)
                .then(Mono.fromCallable(() -> ResponseEntity.ok(Map.of("success", true))));
    }
}
