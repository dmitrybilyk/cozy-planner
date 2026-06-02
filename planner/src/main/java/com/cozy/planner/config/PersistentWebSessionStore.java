package com.cozy.planner.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.WebSessionStore;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PersistentWebSessionStore implements WebSessionStore {

    private static final Logger log = LoggerFactory.getLogger(PersistentWebSessionStore.class);
    private final R2dbcEntityTemplate template;
    private final ObjectMapper objectMapper;

    public PersistentWebSessionStore(R2dbcEntityTemplate template) {
        this.template = template;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("java.util")
                        .allowIfSubType("java.time")
                        .allowIfSubType("org.springframework.security")
                        .allowIfSubType("com.cozy.planner")
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );
        log.info("PersistentWebSessionStore constructor, template={}", template != null ? "present" : "null");
    }

    @Override
    public Mono<WebSession> createWebSession() {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        log.info("createWebSession id={}", id);
        SessionData data = new SessionData();
        data.id = id;
        data.creationTime = now;
        data.lastAccessTime = now;
        data.maxIdleSeconds = Duration.ofDays(30).toSeconds();
        data.attributesJson = "{}";
        return Mono.just(new PersistentWebSession(data));
    }

    @Override
    public Mono<WebSession> retrieveSession(String sessionId) {
        log.info("retrieveSession id={}", sessionId);
        return template.getDatabaseClient().sql(
            "SELECT id, creation_time, last_access_time, max_idle_seconds, attributes FROM user_sessions WHERE id = $1"
        )
        .bind("$1", sessionId)
        .fetch()
        .one()
        .flatMap(row -> {
            log.info("retrieveSession found row for id={}", sessionId);
            SessionData data = mapRow(row);
            WebSession session = new PersistentWebSession(data);
            if (session.isExpired()) {
                log.info("retrieveSession session expired, removing id={}", sessionId);
                return removeSession(sessionId).then(Mono.empty());
            }
            return Mono.just(session);
        })
        .switchIfEmpty(Mono.defer(() -> {
            log.info("retrieveSession no row found for id={}", sessionId);
            return Mono.empty();
        }));
    }

    @Override
    public Mono<Void> removeSession(String sessionId) {
        log.info("removeSession id={}", sessionId);
        return template.getDatabaseClient().sql("DELETE FROM user_sessions WHERE id = $1")
                .bind("$1", sessionId)
                .fetch()
                .rowsUpdated()
                .doOnNext(count -> log.info("removeSession deleted {} rows for id={}", count, sessionId))
                .then();
    }

    @Override
    public Mono<WebSession> updateLastAccessTime(WebSession session) {
        log.debug("updateLastAccessTime id={}", session.getId());
        return Mono.fromSupplier(() -> {
            ((PersistentWebSession) session).setLastAccessTime(Instant.now());
            return session;
        });
    }

    private Mono<Void> storeWebSession(WebSession session) {
        log.info("storeWebSession id={}, expired={}", session.getId(), session.isExpired());
        if (session.isExpired()) {
            return removeSession(session.getId());
        }
        return Mono.defer(() -> {
            try {
                Map<String, Object> attrs = session.getAttributes();
                log.info("storeWebSession attribute keys before serialization: {}", attrs.keySet());
                String attrsJson = objectMapper.writeValueAsString(attrs);
                log.info("storeWebSession attributes size={}", attrsJson.length());
                LocalDateTime creationTime = LocalDateTime.ofInstant(session.getCreationTime(), ZoneOffset.UTC);
                LocalDateTime lastAccessTime = LocalDateTime.ofInstant(session.getLastAccessTime(), ZoneOffset.UTC);
                long maxIdleSeconds = session.getMaxIdleTime().getSeconds();

                return template.getDatabaseClient().sql(
                    "INSERT INTO user_sessions (id, creation_time, last_access_time, max_idle_seconds, attributes) " +
                    "VALUES ($1, $2, $3, $4, $5) " +
                    "ON CONFLICT (id) DO UPDATE SET " +
                    "last_access_time = EXCLUDED.last_access_time, " +
                    "max_idle_seconds = EXCLUDED.max_idle_seconds, " +
                    "attributes = EXCLUDED.attributes"
                )
                .bind("$1", session.getId())
                .bind("$2", creationTime)
                .bind("$3", lastAccessTime)
                .bind("$4", maxIdleSeconds)
                .bind("$5", attrsJson)
                .fetch()
                .rowsUpdated()
                .doOnNext(count -> log.info("storeWebSession upserted id={}, rows={}", session.getId(), count))
                .then();
            } catch (Exception e) {
                log.error("Failed to serialize session attributes", e);
                return Mono.empty();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private SessionData mapRow(Map<String, Object> row) {
        SessionData data = new SessionData();
        data.id = (String) row.get("id");
        Object ct = row.get("creation_time");
        Object lat = row.get("last_access_time");
        data.creationTime = toInstant(ct, Instant.now());
        data.lastAccessTime = toInstant(lat, Instant.now());
        Number mis = (Number) row.get("max_idle_seconds");
        data.maxIdleSeconds = mis != null ? mis.longValue() : 86400L;
        data.attributesJson = (String) row.get("attributes");
        return data;
    }

    private Instant toInstant(Object value, Instant fallback) {
        if (value instanceof LocalDateTime ldt) return ldt.toInstant(ZoneOffset.UTC);
        if (value instanceof Instant i) return i;
        return fallback;
    }

    static class SessionData {
        String id;
        Instant creationTime;
        Instant lastAccessTime;
        long maxIdleSeconds;
        String attributesJson;
    }

    class PersistentWebSession implements WebSession {
        private final String id;
        private final Instant creationTime;
        private volatile Instant lastAccessTime;
        private volatile Duration maxIdleTime;
        private final Map<String, Object> attributes;
        private volatile boolean expired;
        private volatile boolean started;

        PersistentWebSession(SessionData data) {
            this.id = data.id;
            this.creationTime = data.creationTime;
            this.lastAccessTime = data.lastAccessTime;
            this.maxIdleTime = Duration.ofSeconds(data.maxIdleSeconds);
            this.attributes = parseAttributes(data.attributesJson);
            this.expired = false;
            this.started = true;
            log.info("PersistentWebSession created id={}, attrsKeys={}, started={}", id, attributes.keySet(), started);
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> parseAttributes(String json) {
            if (json == null || json.isBlank() || "{}".equals(json)) {
                log.info("parseAttributes empty json for id={}", id);
                return new HashMap<>();
            }
            try {
                Map<String, Object> result = (Map<String, Object>) objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                log.info("parseAttributes parsed {} keys for id={}", result.size(), id);
                return result;
            } catch (Exception e) {
                log.warn("Failed to parse session attributes for {}", id, e);
                return new HashMap<>();
            }
        }

        @Override
        public String getId() { return id; }

        @Override
        public Map<String, Object> getAttributes() { return attributes; }

        @Override
        public Instant getCreationTime() { return creationTime; }

        @Override
        public Instant getLastAccessTime() { return lastAccessTime; }

        @Override
        public Duration getMaxIdleTime() { return maxIdleTime; }

        @Override
        public void setMaxIdleTime(Duration duration) { this.maxIdleTime = duration; }

        void setLastAccessTime(Instant instant) { this.lastAccessTime = instant; }

        @Override
        public boolean isExpired() {
            if (expired) return true;
            if (maxIdleTime != null && maxIdleTime.toMillis() >= 0) {
                boolean exp = Instant.now().minus(maxIdleTime).compareTo(lastAccessTime) >= 0;
                if (exp) log.info("PersistentWebSession expired id={}, lastAccess={}, maxIdle={}", id, lastAccessTime, maxIdleTime);
                return exp;
            }
            return false;
        }

        @Override
        public void start() { this.started = true; }

        @Override
        public boolean isStarted() { return started; }

        @Override
        public Mono<Void> changeSessionId() {
            return Mono.error(new UnsupportedOperationException("Session ID change not supported"));
        }

        @Override
        public Mono<Void> invalidate() {
            log.info("PersistentWebSession invalidate id={}", id);
            this.expired = true;
            return removeSession(id);
        }

        @Override
        public Mono<Void> save() {
            log.info("PersistentWebSession.save() id={}, started={}, expired={}", id, started, expired);
            return storeWebSession(PersistentWebSession.this);
        }
    }

}
