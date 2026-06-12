package com.cozy.planner.controllers.notification;

import com.cozy.planner.service.notification.TelegramService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@Slf4j
public class ContactController {

    private final TelegramService telegramService;

    public ContactController(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    @PostMapping("/api/v1/contact-developer")
    public Mono<ResponseEntity<Map<String, String>>> contactDeveloper(
            @RequestBody Map<String, String> body,
            ServerWebExchange exchange) {

        return exchange.getSession().flatMap(session -> {
            String email = session.getAttribute("user_email");
            String message = body.getOrDefault("message", "").trim();

            if (message.isEmpty()) {
                return Mono.just(ResponseEntity.badRequest()
                        .<Map<String, String>>body(Map.of("error", "Message is empty")));
            }

            String text = "📩 Повідомлення від користувача\n\n"
                    + "📧 Email: " + (email != null ? email : "невідомо") + "\n\n"
                    + "💬 " + message;

            return telegramService.sendToDeveloper(text)
                    .map(ok -> ok
                            ? ResponseEntity.ok(Map.of("status", "sent"))
                            : ResponseEntity.internalServerError()
                                    .<Map<String, String>>body(Map.of("error", "Failed to send")));
        });
    }
}
