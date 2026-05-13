package com.cozy.planner.handler;

import com.cozy.planner.service.EventBroadcastService;
import com.cozy.planner.service.WebSocketSessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
public class RealtimeWebSocketHandler implements WebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final EventBroadcastService eventBroadcastService;

    public RealtimeWebSocketHandler(WebSocketSessionManager sessionManager,
                                    EventBroadcastService eventBroadcastService) {
        this.sessionManager = sessionManager;
        this.eventBroadcastService = eventBroadcastService;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        sessionManager.registerSession(session);

        var outgoing = eventBroadcastService.getEventStream()
                .map(session::textMessage);

        var incoming = session.receive()
                .doFinally(s -> sessionManager.unregisterSession(session))
                .then();

        return session.send(outgoing).and(incoming);
    }
}
