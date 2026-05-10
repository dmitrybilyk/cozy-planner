package com.cozy.planner.controllers;

import com.cozy.planner.service.EventBroadcastService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

@RestController
public class EventController {

    private final EventBroadcastService eventService;

    public EventController(EventBroadcastService eventService) {
        this.eventService = eventService;
    }

    @GetMapping(path = "/api/v1/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamEvents() {
        return eventService.getEventStream()
                .map(event -> ServerSentEvent.<String>builder()
                        .event(event)
                        .data("")
                        .build());
    }
}
