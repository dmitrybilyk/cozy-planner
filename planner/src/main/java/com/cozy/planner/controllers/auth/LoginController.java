package com.cozy.planner.controllers.auth;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Controller
public class LoginController {

    @RequestMapping({"/signin", "/login"})
    public Mono<String> signin(ServerWebExchange exchange) {
        String accept = exchange.getRequest().getHeaders().getFirst("Accept");
        if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return Mono.just("forward:/api/v1/events");
        }
        return Mono.just("login");
    }
}
