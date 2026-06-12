package com.cozy.planner.service.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    private final ConcurrentHashMap<String, WebSocketSession> sessions;

    public WebSocketSessionManager() {
        this.sessions = new ConcurrentHashMap<>();
    }

    public void registerSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void unregisterSession(WebSocketSession session) {
        sessions.remove(session.getId());
    }

    public int getSessionCount() {
        return sessions.size();
    }
}
