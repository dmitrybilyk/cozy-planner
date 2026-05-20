package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Notification;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface NotificationRepository extends ReactiveCrudRepository<Notification, Long> {
    Flux<Notification> findAllByTraineeIdOrderByCreatedAtDesc(Long traineeId);
    Flux<Notification> findAllByMentorIdOrderByCreatedAtDesc(Long mentorId);
    Mono<Long> countByTraineeIdAndIsReadFalse(Long traineeId);
    Mono<Long> countByMentorIdAndIsReadFalse(Long mentorId);
}
