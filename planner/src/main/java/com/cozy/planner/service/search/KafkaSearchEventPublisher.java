package com.cozy.planner.service.search;

import com.cozy.planner.model.entity.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class KafkaSearchEventPublisher implements SearchEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaSearchEventPublisher.class);

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    @Value("${app.search-topic:search-events}")
    private String topic;

    public KafkaSearchEventPublisher(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public Mono<Void> publishSessionEvent(String action, Session session) {
        if (session.getId() == null) {
            return Mono.empty();
        }

        Map<String, Object> sessionData = new LinkedHashMap<>();
        sessionData.put("id", session.getId());
        sessionData.put("title", session.getTitle());
        sessionData.put("description", session.getDescription());
        sessionData.put("workoutDate", session.getWorkoutDate() != null ? session.getWorkoutDate().toString() : null);
        sessionData.put("startTime", session.getStartTime() != null ? session.getStartTime().toString() : null);
        sessionData.put("endTime", session.getEndTime() != null ? session.getEndTime().toString() : null);
        sessionData.put("mentorId", session.getMentorId());
        sessionData.put("locationId", session.getLocationId());
        sessionData.put("confirmationStatus", session.getConfirmationStatus());
        sessionData.put("createdBy", session.getCreatedBy());

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("action", action);
        event.put("session", sessionData);

        log.info("Publishing search event: action={}, sessionId={}", action, session.getId());

        return Mono.fromCallable(() -> kafkaTemplate.send(topic, String.valueOf(session.getId()), event))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::fromFuture)
                .then();
    }
}
