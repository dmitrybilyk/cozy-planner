package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Athlete;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface AthleteRepository extends ReactiveCrudRepository<Athlete, Long> {
    // Пошук усіх атлетів конкретного тренера
    Flux<Athlete> findAllByCoachId(Long coachId);
}