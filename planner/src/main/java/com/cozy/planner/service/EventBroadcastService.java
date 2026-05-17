package com.cozy.planner.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class EventBroadcastService {

    private final Sinks.Many<String> eventBus;

    public EventBroadcastService() {
        this.eventBus = Sinks.many().multicast().onBackpressureBuffer(256, false);
    }

    public Flux<String> getEventStream() {
        return eventBus.asFlux();
    }

    public void broadcast(String event) {
        eventBus.tryEmitNext(event);
    }
}
