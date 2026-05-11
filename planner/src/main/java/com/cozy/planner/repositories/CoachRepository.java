package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Coach;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface CoachRepository extends ReactiveCrudRepository<Coach, Long> {

    Flux<Coach> findAllByClubId(Long clubId);

    Flux<Coach> findAllByNameContainingIgnoreCase(String name);

    @Query("SELECT * FROM mentors WHERE telegram_token = :token")
    Mono<Coach> findByTelegramToken(String token);
}
