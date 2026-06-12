package com.cozy.planner.service.search;

import com.cozy.planner.model.entity.Session;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(name = "app.search-enabled", havingValue = "false", matchIfMissing = true)
public class NoopSearchEventPublisher implements SearchEventPublisher {
    @Override
    public Mono<Void> publishSessionEvent(String action, Session session) {
        return Mono.empty();
    }
}
