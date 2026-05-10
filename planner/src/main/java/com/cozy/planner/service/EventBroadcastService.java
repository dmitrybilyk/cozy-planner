package com.cozy.planner.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class EventBroadcastService {

    private final Sinks.Many<String> sink;
    private final WebSocketSessionManager webSocketSessionManager;

    public EventBroadcastService(WebSocketSessionManager webSocketSessionManager) {
        this.sink = Sinks.many().multicast().onBackpressureBuffer();
        this.webSocketSessionManager = webSocketSessionManager;
    }

    public Flux<String> getEventStream() {
        return sink.asFlux();
    }

    public void broadcast(String event) {
        sink.tryEmitNext(event);
        webSocketSessionManager.broadcast(event);
    }
}
