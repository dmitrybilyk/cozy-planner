package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Mentor;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MentorRepository extends ReactiveCrudRepository<Mentor, Long> {

    Flux<Mentor> findAllByClubId(Long clubId);

    Flux<Mentor> findAllByNameContainingIgnoreCase(String name);

    @Query("SELECT * FROM mentors WHERE telegram_token = :token")
    Mono<Mentor> findByTelegramToken(String token);

    @Deprecated
    default Flux<Mentor> findAllByCoachId(Long clubId) {
        return findAllByClubId(clubId);
    }
}
