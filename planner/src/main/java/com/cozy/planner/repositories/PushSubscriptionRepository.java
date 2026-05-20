package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.PushSubscription;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PushSubscriptionRepository extends ReactiveCrudRepository<PushSubscription, Long> {
    Flux<PushSubscription> findAllByTraineeId(Long traineeId);
    Flux<PushSubscription> findAllByMentorId(Long mentorId);

    @Modifying
    @Query("DELETE FROM push_subscriptions WHERE endpoint = :endpoint")
    Mono<Void> deleteByEndpoint(String endpoint);
}
