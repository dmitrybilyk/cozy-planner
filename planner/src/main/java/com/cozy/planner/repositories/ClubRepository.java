package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Club;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface ClubRepository extends ReactiveCrudRepository<Club, Long> {
    Flux<Club> findByUserId(Long userId);
}