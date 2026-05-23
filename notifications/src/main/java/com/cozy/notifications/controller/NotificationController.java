package com.cozy.notifications.controller;

import com.cozy.notifications.dto.SendMessageRequest;
import com.cozy.notifications.dto.SendMessageResponse;
import com.cozy.notifications.service.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);
    private final NotificationSender notificationSender;

    public NotificationController(NotificationSender notificationSender) {
        this.notificationSender = notificationSender;
    }

    @PostMapping("/send")
    public ResponseEntity<SendMessageResponse> send(@RequestBody SendMessageRequest request) {
        log.info("Received send request for chatId: {}", request.getChatId());
        boolean ok = notificationSender.sendMessage(
                request.getChatId(),
                request.getText(),
                request.getParseMode(),
                request.getReplyMarkup()
        );
        return ResponseEntity.ok(ok ? SendMessageResponse.ok() : SendMessageResponse.error("Failed to send"));
    }

    @PostMapping("/send-mentor")
    public ResponseEntity<SendMessageResponse> sendMentor(@RequestBody SendMessageRequest request) {
        log.info("Received send-mentor request for chatId: {}", request.getChatId());
        boolean ok = notificationSender.sendMessageToMentor(
                request.getChatId(),
                request.getText(),
                request.getParseMode(),
                request.getReplyMarkup()
        );
        return ResponseEntity.ok(ok ? SendMessageResponse.ok() : SendMessageResponse.error("Failed to send"));
    }

    @GetMapping("/health")
    public ResponseEntity<SendMessageResponse> health() {
        return ResponseEntity.ok(SendMessageResponse.ok());
    }
}
