package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Location;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface LocationRepository extends ReactiveCrudRepository<Location, Long> {
    Flux<Location> findAllByCoachId(Long coachId);
}
