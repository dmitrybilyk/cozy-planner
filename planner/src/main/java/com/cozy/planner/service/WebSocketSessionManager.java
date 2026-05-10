package com.cozy.planner.service;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    private final Sinks.Many<String> eventSink;
    private final ConcurrentHashMap<String, WebSocketSession> sessions;

    public WebSocketSessionManager() {
        this.eventSink = Sinks.many().multicast().onBackpressureBuffer();
        this.sessions = new ConcurrentHashMap<>();
    }

    public void registerSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void unregisterSession(WebSocketSession session) {
        sessions.remove(session.getId());
    }

    public Flux<String> getEventStream() {
        return eventSink.asFlux();
    }

    public void broadcast(String event) {
        eventSink.tryEmitNext(event);
    }

    public int getSessionCount() {
        return sessions.size();
    }
}
