package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.AuditEvent;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface AuditEventRepository extends ReactiveCrudRepository<AuditEvent, Long> {

    @Query("SELECT * FROM audit_events ORDER BY timestamp DESC LIMIT 200")
    Flux<AuditEvent> findRecent();
}
