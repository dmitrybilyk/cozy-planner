package com.cozy.planner.service;

import com.cozy.planner.model.entity.AuditEvent;
import com.cozy.planner.repositories.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditEventRepository repository;
    private final Sinks.Many<AuditEvent> sink = Sinks.many().replay().limit(200);

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public Mono<Void> log(String eventType, String actorEmail, Long mentorId, String description) {
        AuditEvent event = AuditEvent.builder()
                .timestamp(LocalDateTime.now())
                .eventType(eventType)
                .actorEmail(actorEmail)
                .mentorId(mentorId)
                .description(description)
                .build();
        return repository.save(event)
                .doOnNext(saved -> sink.tryEmitNext(saved))
                .onErrorResume(e -> { log.warn("Audit log failed ({}): {}", eventType, e.getMessage()); return Mono.empty(); })
                .then();
    }

    public Flux<AuditEvent> stream() {
        return sink.asFlux();
    }

    public Flux<AuditEvent> recent() {
        return repository.findRecent();
    }
}
