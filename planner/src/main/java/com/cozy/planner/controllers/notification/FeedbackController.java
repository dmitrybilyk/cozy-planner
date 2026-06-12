package com.cozy.planner.controllers.notification;

import com.cozy.planner.model.entity.SessionFeedback;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.SessionFeedbackRepository;
import com.cozy.planner.repositories.TraineeRepository;
import com.cozy.planner.service.notification.TelegramService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

    private final SessionFeedbackRepository feedbackRepository;
    private final MentorRepository mentorRepository;
    private final TraineeRepository traineeRepository;
    private final TelegramService telegramService;

    public FeedbackController(SessionFeedbackRepository feedbackRepository,
                              MentorRepository mentorRepository,
                              TraineeRepository traineeRepository,
                              TelegramService telegramService) {
        this.feedbackRepository = feedbackRepository;
        this.mentorRepository = mentorRepository;
        this.traineeRepository = traineeRepository;
        this.telegramService = telegramService;
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> sendFeedback(
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {

        Long sessionId = body.get("sessionId") instanceof Number n ? n.longValue() : null;
        Long fromMentorId = body.get("fromMentorId") instanceof Number n ? n.longValue() : null;
        Long fromTraineeId = body.get("fromTraineeId") instanceof Number n ? n.longValue() : null;
        Long toMentorId = body.get("toMentorId") instanceof Number n ? n.longValue() : null;
        Long toTraineeId = body.get("toTraineeId") instanceof Number n ? n.longValue() : null;
        String text = body.get("text") instanceof String s ? s.trim() : null;
        String tags = body.get("tags") instanceof String s ? s.trim() : null;
        String sessionTitle = body.get("sessionTitle") instanceof String s ? s.trim() : null;
        Short rating = body.get("rating") instanceof Number n ? n.shortValue() : null;

        if (fromMentorId == null && fromTraineeId == null) {
            return Mono.just(ResponseEntity.badRequest().<Map<String, Object>>body(Map.of("error", "sender required")));
        }
        if (toMentorId == null && toTraineeId == null) {
            return Mono.just(ResponseEntity.badRequest().<Map<String, Object>>body(Map.of("error", "recipient required")));
        }

        SessionFeedback fb = SessionFeedback.builder()
                .sessionId(sessionId)
                .sessionTitle((sessionTitle == null || sessionTitle.isBlank()) ? null : sessionTitle)
                .fromMentorId(fromMentorId)
                .fromTraineeId(fromTraineeId)
                .toMentorId(toMentorId)
                .toTraineeId(toTraineeId)
                .text((text == null || text.isBlank()) ? null : text)
                .tags((tags == null || tags.isBlank()) ? null : tags)
                .rating(rating)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        final String finalText = fb.getText();
        final String finalTags = fb.getTags();
        final Short finalRating = rating;
        final Long finalFromMentorId = fromMentorId;
        final Long finalFromTraineeId = fromTraineeId;
        final Long finalToTraineeId = toTraineeId;
        final Long finalToMentorId = toMentorId;
        final String finalSessionTitle = sessionTitle;

        return feedbackRepository.save(fb)
                .flatMap(saved -> {
                    Mono<Boolean> tgSend = sendTgNotification(
                            finalFromMentorId, finalFromTraineeId,
                            finalToTraineeId, finalToMentorId,
                            finalText, finalTags, finalRating, finalSessionTitle);
                    tgSend.subscribe(
                            ok -> log.debug("TG feedback notification sent: {}", ok),
                            err -> log.warn("TG feedback notification failed: {}", err.getMessage())
                    );
                    Map<String, Object> r = toMap(saved, null);
                    return Mono.just(ResponseEntity.status(HttpStatus.CREATED).<Map<String, Object>>body(r));
                });
    }

    private Mono<Boolean> sendTgNotification(Long fromMentorId, Long fromTraineeId,
                                              Long toTraineeId, Long toMentorId,
                                              String text, String tags, Short rating, String sessionTitle) {
        if (fromMentorId != null && toTraineeId != null) {
            return mentorRepository.findById(fromMentorId)
                    .flatMap(mentor -> traineeRepository.findById(toTraineeId)
                            .filter(t -> t.hasTelegram() && mentor.isTelegramIntegrationEnabled())
                            .flatMap(trainee -> {
                                String msg = buildFeedbackMessage(mentor.getName(), text, tags, rating, sessionTitle, false);
                                return telegramService.sendMessage(trainee.getTelegramChatId(), msg);
                            }))
                    .defaultIfEmpty(false);
        }
        if (fromTraineeId != null && toMentorId != null) {
            return traineeRepository.findById(fromTraineeId)
                    .flatMap(trainee -> mentorRepository.findById(toMentorId)
                            .filter(m -> m.hasTelegram() && m.isTelegramIntegrationEnabled())
                            .flatMap(mentor -> {
                                String msg = buildFeedbackMessage(trainee.getName(), text, tags, rating, sessionTitle, true);
                                return telegramService.sendMessageToMentor(mentor.getTelegramChatId(), msg);
                            }))
                    .defaultIfEmpty(false);
        }
        return Mono.just(false);
    }

    private String buildFeedbackMessage(String senderName, String text, String tags, Short rating, String sessionTitle, boolean toMentor) {
        StringBuilder sb = new StringBuilder();
        sb.append("💬 <b>Відгук від ").append(escapeHtml(senderName)).append("</b>\n\n");
        if (rating != null && rating > 0) {
            sb.append("⭐".repeat(rating)).append("\n");
        }
        if (tags != null && !tags.isBlank()) {
            sb.append("🏷 ").append(escapeHtml(tags)).append("\n");
        }
        if (text != null && !text.isBlank()) {
            sb.append("\n").append(escapeHtml(text));
        }
        if (sessionTitle != null && !sessionTitle.isBlank()) {
            sb.append("\n\n📅 <i>").append(escapeHtml(sessionTitle)).append("</i>");
        }
        return sb.toString();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @GetMapping("/conversation")
    public Mono<ResponseEntity<List<Map<String, Object>>>> conversation(
            @RequestParam Long mentorId,
            @RequestParam Long traineeId) {
        return feedbackRepository.findConversation(mentorId, traineeId)
                .flatMap(fb -> {
                    if (fb.getFromMentorId() != null) {
                        return mentorRepository.findById(fb.getFromMentorId())
                                .map(m -> toMap(fb, m.getName()))
                                .defaultIfEmpty(toMap(fb, null));
                    }
                    if (fb.getFromTraineeId() != null) {
                        return traineeRepository.findById(fb.getFromTraineeId())
                                .map(t -> toMap(fb, t.getName()))
                                .defaultIfEmpty(toMap(fb, null));
                    }
                    return Mono.just(toMap(fb, null));
                })
                .collectList()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/received-by-trainee")
    public Mono<ResponseEntity<List<Map<String, Object>>>> receivedByTrainee(
            @RequestParam Long traineeId) {
        return feedbackRepository.findAllByToTraineeIdOrderByCreatedAtDesc(traineeId)
                .flatMap(fb -> {
                    if (fb.getFromMentorId() != null) {
                        return mentorRepository.findById(fb.getFromMentorId())
                                .map(m -> toMap(fb, m.getName()))
                                .defaultIfEmpty(toMap(fb, null));
                    }
                    return Mono.just(toMap(fb, null));
                })
                .collectList()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/received-by-mentor")
    public Mono<ResponseEntity<List<Map<String, Object>>>> receivedByMentor(
            @RequestParam Long mentorId) {
        return feedbackRepository.findAllByToMentorIdOrderByCreatedAtDesc(mentorId)
                .flatMap(fb -> {
                    if (fb.getFromTraineeId() != null) {
                        return traineeRepository.findById(fb.getFromTraineeId())
                                .map(t -> toMap(fb, t.getName()))
                                .defaultIfEmpty(toMap(fb, null));
                    }
                    return Mono.just(toMap(fb, null));
                })
                .collectList()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/sent-by-mentor")
    public Mono<ResponseEntity<List<Map<String, Object>>>> sentByMentor(
            @RequestParam Long mentorId,
            @RequestParam(required = false) Long traineeId) {
        Flux<SessionFeedback> flux = traineeId != null
                ? feedbackRepository.findAllByFromMentorIdAndToTraineeIdOrderByCreatedAtDesc(mentorId, traineeId)
                : feedbackRepository.findAllByFromMentorIdOrderByCreatedAtDesc(mentorId);
        return flux
                .flatMap(fb -> {
                    if (fb.getToTraineeId() != null) {
                        return traineeRepository.findById(fb.getToTraineeId())
                                .map(t -> toMap(fb, t.getName()))
                                .defaultIfEmpty(toMap(fb, null));
                    }
                    return Mono.just(toMap(fb, null));
                })
                .collectList()
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{id}/read")
    public Mono<ResponseEntity<Void>> markRead(@PathVariable Long id) {
        return feedbackRepository.findById(id)
                .flatMap(fb -> {
                    fb.setIsRead(true);
                    return feedbackRepository.save(fb);
                })
                .map(saved -> ResponseEntity.ok().<Void>build())
                .defaultIfEmpty(ResponseEntity.notFound().<Void>build());
    }

    @GetMapping("/unread-count")
    public Mono<ResponseEntity<Map<String, Long>>> unreadCount(
            @RequestParam(required = false) Long traineeId,
            @RequestParam(required = false) Long mentorId) {
        Mono<Long> count;
        if (traineeId != null) {
            count = feedbackRepository.countByToTraineeIdAndIsReadFalse(traineeId);
        } else if (mentorId != null) {
            count = feedbackRepository.countByToMentorIdAndIsReadFalse(mentorId);
        } else {
            count = Mono.just(0L);
        }
        return count.map(c -> ResponseEntity.ok(Map.of("count", c)));
    }

    private Map<String, Object> toMap(SessionFeedback fb, String senderName) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", fb.getId());
        r.put("sessionId", fb.getSessionId());
        r.put("fromMentorId", fb.getFromMentorId());
        r.put("fromTraineeId", fb.getFromTraineeId());
        r.put("toMentorId", fb.getToMentorId());
        r.put("toTraineeId", fb.getToTraineeId());
        r.put("senderName", senderName);
        r.put("sessionTitle", fb.getSessionTitle());
        r.put("text", fb.getText());
        r.put("tags", fb.getTags());
        r.put("rating", fb.getRating());
        r.put("isRead", fb.getIsRead());
        r.put("createdAt", fb.getCreatedAt() != null ? fb.getCreatedAt().toString() : null);
        return r;
    }
}
