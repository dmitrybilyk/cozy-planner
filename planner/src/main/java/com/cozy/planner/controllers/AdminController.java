package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.AuditEvent;
import com.cozy.planner.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AdminController {

    private static final String ADMIN_EMAIL = "dmitry.mediastore@gmail.com";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditService auditService;
    private final DatabaseClient db;

    public AdminController(AuditService auditService, DatabaseClient db) {
        this.auditService = auditService;
        this.db = db;
    }

    @GetMapping("/admin")
    public Mono<String> adminPage(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            String email = session.getAttribute("user_email");
            if (!ADMIN_EMAIL.equals(email)) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return Mono.just("redirect:/signin");
            }
            return Mono.just("admin");
        });
    }

    @GetMapping(value = "/admin/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<ServerSentEvent<Map<String, Object>>> adminEventStream(ServerWebExchange exchange) {
        return exchange.getSession().flatMapMany(session -> {
            String email = session.getAttribute("user_email");
            if (!ADMIN_EMAIL.equals(email)) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return Flux.empty();
            }
            return auditService.stream().map(this::toSse);
        });
    }

    @GetMapping("/admin/recent")
    @ResponseBody
    public Mono<ResponseEntity<List<Map<String, Object>>>> recentEvents(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            String email = session.getAttribute("user_email");
            if (!ADMIN_EMAIL.equals(email)) {
                return Mono.just(ResponseEntity.<List<Map<String, Object>>>status(HttpStatus.FORBIDDEN).build());
            }
            return auditService.recent()
                    .map(this::toMap)
                    .collectList()
                    .map(ResponseEntity::ok);
        });
    }

    @GetMapping("/admin/stats")
    @ResponseBody
    public Mono<ResponseEntity<Map<String, Object>>> stats(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            String email = session.getAttribute("user_email");
            if (!ADMIN_EMAIL.equals(email)) {
                return Mono.just(ResponseEntity.<Map<String, Object>>status(HttpStatus.FORBIDDEN).build());
            }

            Mono<Long> userCount = db.sql("SELECT COUNT(*) FROM users").map(r -> r.get(0, Long.class)).one();
            Mono<Long> mentorCount = db.sql("SELECT COUNT(*) FROM mentors").map(r -> r.get(0, Long.class)).one();
            Mono<Long> traineeCount = db.sql("SELECT COUNT(*) FROM trainees").map(r -> r.get(0, Long.class)).one();
            Mono<Long> sessionCount = db.sql("SELECT COUNT(*) FROM meetings").map(r -> r.get(0, Long.class)).one();

            Mono<List<Map<String, Object>>> users = db.sql("""
                    SELECT u.id, u.name, u.email,
                           TO_CHAR(u.created_at, 'YYYY-MM-DD HH24:MI') AS registered_at,
                           m.name AS mentor_name,
                           m.profile AS profile,
                           COALESCE((SELECT COUNT(*) FROM trainees WHERE mentor_id = m.id), 0) AS trainee_count,
                           COALESCE((SELECT COUNT(*) FROM meetings WHERE mentor_id = m.id), 0) AS session_count
                    FROM users u
                    LEFT JOIN clubs c ON c.user_id = u.id
                    LEFT JOIN mentors m ON m.club_id = c.id
                    ORDER BY u.created_at DESC NULLS LAST
                    """)
                    .map(row -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", orEmpty(row.get("id")));
                        m.put("name", orEmpty(row.get("name")));
                        m.put("email", orEmpty(row.get("email")));
                        m.put("registeredAt", orEmpty(row.get("registered_at")));
                        m.put("mentorName", orEmpty(row.get("mentor_name")));
                        m.put("profile", orEmpty(row.get("profile")));
                        m.put("traineeCount", orZero(row.get("trainee_count")));
                        m.put("sessionCount", orZero(row.get("session_count")));
                        return m;
                    })
                    .all()
                    .collectList();

            return Mono.zip(userCount, mentorCount, traineeCount, sessionCount, users)
                    .map(t -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("userCount", t.getT1());
                        result.put("mentorCount", t.getT2());
                        result.put("traineeCount", t.getT3());
                        result.put("sessionCount", t.getT4());
                        result.put("users", t.getT5());
                        return ResponseEntity.ok(result);
                    });
        });
    }

    private String orEmpty(Object v) {
        return v != null ? v.toString() : "";
    }

    private long orZero(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
    }

    private ServerSentEvent<Map<String, Object>> toSse(AuditEvent e) {
        return ServerSentEvent.<Map<String, Object>>builder().data(toMap(e)).build();
    }

    private Map<String, Object> toMap(AuditEvent e) {
        return Map.of(
                "id", e.getId() != null ? e.getId() : 0L,
                "timestamp", e.getTimestamp() != null ? e.getTimestamp().format(FMT) : "",
                "eventType", e.getEventType() != null ? e.getEventType() : "",
                "actorEmail", e.getActorEmail() != null ? e.getActorEmail() : "",
                "mentorId", e.getMentorId() != null ? e.getMentorId() : 0L,
                "description", e.getDescription() != null ? e.getDescription() : ""
        );
    }
}
