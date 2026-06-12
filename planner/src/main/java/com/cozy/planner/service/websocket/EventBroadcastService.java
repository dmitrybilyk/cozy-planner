package com.cozy.planner.service.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;

@Service
public class EventBroadcastService {

    private final Sinks.Many<String> eventBus;
    private final ObjectMapper objectMapper;

    public EventBroadcastService(ObjectMapper objectMapper) {
        this.eventBus = Sinks.many().multicast().onBackpressureBuffer(256, false);
        this.objectMapper = objectMapper;
    }

    public Flux<String> getEventStream() {
        return eventBus.asFlux();
    }

    public void broadcast(String event) {
        eventBus.tryEmitNext(event);
    }

    public void broadcastJson(Map<String, Object> event) {
        try {
            broadcast(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            broadcast("notification:error");
        }
    }
}
