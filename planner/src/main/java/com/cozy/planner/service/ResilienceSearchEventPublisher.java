package com.cozy.planner.service;

import com.cozy.planner.model.entity.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Primary
@Service
public class ResilienceSearchEventPublisher implements SearchEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ResilienceSearchEventPublisher.class);

    private final KafkaSearchEventPublisher kafka;
    private final KafkaAvailabilityService kafkaAvailability;

    public ResilienceSearchEventPublisher(KafkaSearchEventPublisher kafka,
                                           KafkaAvailabilityService kafkaAvailability) {
        this.kafka = kafka;
        this.kafkaAvailability = kafkaAvailability;
    }

    @Override
    public Mono<Void> publishSessionEvent(String action, Session session) {
        if (!kafkaAvailability.isAvailable()) {
            return Mono.empty();
        }
        return kafka.publishSessionEvent(action, session)
                .doOnError(e -> {
                    kafkaAvailability.markUnavailable();
                    log.warn("Kafka search event failed, dropping event: {}", e.getMessage());
                })
                .onErrorResume(e -> Mono.empty());
    }
}
