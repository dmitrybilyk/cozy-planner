package com.cozy.planner.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

@Controller
public class DemoAuthController {

    private final ServerSecurityContextRepository securityContextRepo =
            new WebSessionServerSecurityContextRepository();

    @PostMapping("/demo-login")
    public Mono<Void> demoLogin(ServerWebExchange exchange) {
        var auth = new UsernamePasswordAuthenticationToken(
                "demo-user", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContext context = new SecurityContextImpl(auth);

        return exchange.getSession()
                .flatMap(session -> {
                    session.getAttributes().put("google_sub", "demo-seed");
                    session.getAttributes().put("user_email", "demo@cozyplanner.app");
                    session.getAttributes().put("user_name", "Демо");
                    return securityContextRepo.save(exchange, context);
                })
                .then(Mono.fromRunnable(() -> {
                    var response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.FOUND);
                    response.getHeaders().setLocation(URI.create("/planner"));
                }));
    }
}
