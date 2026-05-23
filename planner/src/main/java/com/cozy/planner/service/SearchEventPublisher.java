package com.cozy.planner.service;

import com.cozy.planner.model.entity.Session;
import reactor.core.publisher.Mono;

public interface SearchEventPublisher {
    Mono<Void> publishSessionEvent(String action, Session session);
}
