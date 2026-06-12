package com.cozy.planner.config.security;

import com.cozy.planner.service.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.WebSessionIdResolver;

import java.net.URI;
import java.time.Duration;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final AuditService auditService;

    public SecurityConfig(AuditService auditService) {
        this.auditService = auditService;
    }

    @Bean
    public WebSessionIdResolver webSessionIdResolver() {
        var resolver = new CookieWebSessionIdResolver();
        resolver.setCookieMaxAge(Duration.ofDays(30));
        return resolver;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        var logoutHandler = new RedirectServerLogoutSuccessHandler();
        logoutHandler.setLogoutSuccessUrl(URI.create("/signin"));

        return http
                .securityContextRepository(new WebSessionServerSecurityContextRepository())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/", "/css/**", "/js/**", "/signin", "/login**", "/demo-login", "/k6-login", "/oauth2/**", "/webjars/**", "/error**", "/api/v1/events", "/api/v1/ws", "/trainee/**", "/api/v1/trainee/**", "/api/v1/trainee/invite", "/api/v1/me", "/api/v1/trainees/*/availability", "/api/v1/availability/**", "/api/v1/notifications/**", "/api/v1/push/**", "/api/v1/telegram/webhook", "/api/v1/telegram/webhook/**", "/api/v1/telegram/config", "/actuator/**", "/shared/**", "/api/v1/shared/**", "/api/v1/dev/**", "/api/v1/feedback/**")
                        .permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authenticationSuccessHandler((webFilterExchange, authentication) -> {
                            var principal = (OidcUser) authentication.getPrincipal();
                            boolean isAdmin = "dmitry.mediastore@gmail.com".equals(principal.getEmail());
                            var session = webFilterExchange.getExchange().getSession();
                            return session.flatMap(s -> {
                                s.getAttributes().put("google_sub", principal.getSubject());
                                s.getAttributes().put("user_email", principal.getEmail());
                                s.getAttributes().put("user_name", principal.getGivenName());
                                log.info("OAuth2 login SUCCESS: sid={} sub={} email={} name={} → redirect={}",
                                        s.getId(), principal.getSubject(), principal.getEmail(), principal.getGivenName(),
                                        isAdmin ? "/admin" : "/setup");
                                auditService.log(isAdmin ? "ADMIN_LOGIN" : "MENTOR_LOGIN", principal.getEmail(), null,
                                        "Login: " + principal.getGivenName() + " <" + principal.getEmail() + ">").subscribe();
                                var response = webFilterExchange.getExchange().getResponse();
                                response.setStatusCode(org.springframework.http.HttpStatus.FOUND);
                                response.getHeaders().setLocation(URI.create(isAdmin ? "/admin" : "/setup"));
                                return response.setComplete();
                            });
                        })
                        .authenticationFailureHandler((wfe, exception) -> {
                            log.error("OAuth2 login FAILED for {}: {}", exception.getClass().getSimpleName(), exception.getMessage());
                            var response = wfe.getExchange().getResponse();
                            response.getHeaders().setLocation(URI.create("/login?error"));
                            response.setStatusCode(HttpStatus.FOUND);
                            return response.setComplete();
                        })
                )
                .logout(logout -> logout
                        .logoutSuccessHandler(logoutHandler)
                        .requiresLogout(ServerWebExchangeMatchers.pathMatchers("/logout"))
                )
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((exchange, e) -> {
                            var req = exchange.getRequest();
                            String path = req.getURI().getPath();
                            String ua = req.getHeaders().getFirst("User-Agent");
                            var remoteAddr = req.getRemoteAddress();
                            String ip = (remoteAddr != null && remoteAddr.getAddress() != null) ? remoteAddr.getAddress().getHostAddress() : "?";
                            log.warn("[security] UNAUTHENTICATED access → 302 /signin | path={} ip={} ua={}", path, ip, ua);
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
