package com.linkease.server.config

import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.util.matcher.AntPathRequestMatcher

// Single-tenant login: only app.owner-email may use this app. Mirrors planner's Google
// OAuth2-login shape (same secret env var names so the same secrets file values work),
// but with its own session store/code — planner itself is never touched.
@Configuration
@EnableWebSecurity
class SecurityConfig(private val appProperties: AppProperties) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val apiUnauthorizedEntryPoint = AuthenticationEntryPointStatus(HttpServletResponse.SC_UNAUTHORIZED)
        val entryPointResolver = DelegatingAuthenticationEntryPoint(
            linkedMapOf<org.springframework.security.web.util.matcher.RequestMatcher, org.springframework.security.web.AuthenticationEntryPoint>(
                AntPathRequestMatcher("/api/**") to apiUnauthorizedEntryPoint,
            ),
        ).apply {
            setDefaultEntryPoint(LoginUrlAuthenticationEntryPoint("/oauth2/authorization/google"))
        }

        return http
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/", "/index.html", "/*.js", "/*.wasm", "/*.css", "/*.ico",
                        "/oauth2/**", "/login/**", "/error", "/actuator/health",
                        "/api/me", "/api/telegram/webhook/**",
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { it.authenticationEntryPoint(entryPointResolver) }
            .oauth2Login { oauth2 ->
                oauth2.successHandler(ownerOnlySuccessHandler())
                oauth2.failureHandler { _, response, _ -> response.sendRedirect("/?error=login_failed") }
            }
            .logout { it.logoutSuccessUrl("/") }
            .build()
    }

    private fun ownerOnlySuccessHandler() = AuthenticationSuccessHandler { request, response, authentication ->
        val email = (authentication.principal as OidcUser).email
        if (appProperties.ownerEmail.isBlank() || email != appProperties.ownerEmail) {
            request.session.invalidate()
            response.sendRedirect("/?error=unauthorized")
        } else {
            response.sendRedirect("/")
        }
    }
}

private class AuthenticationEntryPointStatus(
    private val status: Int,
) : org.springframework.security.web.AuthenticationEntryPoint {
    override fun commence(
        request: jakarta.servlet.http.HttpServletRequest,
        response: HttpServletResponse,
        authException: org.springframework.security.core.AuthenticationException,
    ) {
        response.status = status
    }
}
