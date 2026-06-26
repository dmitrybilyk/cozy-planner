package com.linkease.server.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
class EventController(private val sseService: SseService) {

    @GetMapping("/api/events", produces = ["text/event-stream"])
    fun events(): SseEmitter = sseService.register()
}
