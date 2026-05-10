package com.cozy.planner.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

import java.net.URI;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        var logoutHandler = new RedirectServerLogoutSuccessHandler();
        logoutHandler.setLogoutSuccessUrl(URI.create("/signin"));

        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/signin", "/login**", "/demo-login", "/oauth2/**", "/webjars/**", "/error**", "/api/v1/events", "/api/v1/ws", "/athlete/**", "/api/v1/athlete/**", "/api/v1/athlete/invite", "/api/v1/me", "/api/v1/athletes/*/availability")
                        .permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authenticationSuccessHandler((webFilterExchange, authentication) -> {
                            var principal = (OidcUser) authentication.getPrincipal();
                            var session = webFilterExchange.getExchange().getSession();
                            return session.flatMap(s -> {
                                s.getAttributes().put("google_sub", principal.getSubject());
                                s.getAttributes().put("user_email", principal.getEmail());
                                s.getAttributes().put("user_name", principal.getGivenName());
                                var response = webFilterExchange.getExchange().getResponse();
                                response.setStatusCode(org.springframework.http.HttpStatus.FOUND);
                                response.getHeaders().setLocation(URI.create("/setup"));
                                return response.setComplete();
                            });
                        })
                )
                .logout(logout -> logout
                        .logoutSuccessHandler(logoutHandler)
                        .requiresLogout(ServerWebExchangeMatchers.pathMatchers("/logout"))
                )
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((exchange, e) -> {
                            var response = exchange.getResponse();
                            response.getHeaders().setLocation(URI.create("/signin"));
                            response.setStatusCode(HttpStatus.FOUND);
                            return response.setComplete();
                        })
                )
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }
}
