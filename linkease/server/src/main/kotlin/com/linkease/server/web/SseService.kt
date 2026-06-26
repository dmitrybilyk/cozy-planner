package com.linkease.server.web

import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CopyOnWriteArrayList

@Service
class SseService {
    private val emitters = CopyOnWriteArrayList<SseEmitter>()

    fun register(): SseEmitter {
        val emitter = SseEmitter(3_600_000L) // 1-hour timeout; EventSource auto-reconnects
        emitters.add(emitter)
        val remove: (Any?) -> Unit = { emitters.remove(emitter) }
        emitter.onCompletion { remove(null) }
        emitter.onTimeout { remove(null) }
        emitter.onError(remove)
        return emitter
    }

    fun broadcast(entity: String) {
        val dead = mutableListOf<SseEmitter>()
        for (emitter in emitters) {
            try {
                emitter.send(SseEmitter.event().data(entity).build())
            } catch (_: Exception) {
                dead.add(emitter)
            }
        }
        emitters.removeAll(dead)
    }
}
