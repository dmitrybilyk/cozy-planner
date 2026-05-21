package com.cozy.planner.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class LoggingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getRawPath();
        String query = exchange.getRequest().getURI().getRawQuery();
        String full = query != null ? path + "?" + query : path;

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    long elapsed = System.currentTimeMillis() - start;
                    int status = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value() : 0;
                    if (status >= 400) {
                        log.warn("{} {} → {} ({}ms)", method, full, status, elapsed);
                    } else {
                        log.info("{} {} → {} ({}ms)", method, full, status, elapsed);
                    }
                })
                .doOnError(error -> {
                    log.error("{} {} failed: {}", method, full, error.getMessage(), error);
                });
    }
}
