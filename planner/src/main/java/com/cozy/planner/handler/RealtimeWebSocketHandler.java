package com.cozy.planner.handler;

import com.cozy.planner.service.WebSocketSessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class RealtimeWebSocketHandler implements WebSocketHandler {

    private final WebSocketSessionManager sessionManager;

    public RealtimeWebSocketHandler(WebSocketSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        sessionManager.registerSession(session);

        var outgoing = sessionManager.getEventStream()
                .map(event -> session.textMessage(event))
                .delayElements(Duration.ofMillis(10));

        var incoming = session.receive()
                .doFinally(s -> sessionManager.unregisterSession(session))
                .then();

        return session.send(outgoing).and(incoming);
    }
}
