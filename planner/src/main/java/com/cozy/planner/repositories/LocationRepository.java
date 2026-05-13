package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Location;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;

@Repository
public interface LocationRepository extends ReactiveCrudRepository<Location, Long> {

    @Query("SELECT * FROM places WHERE mentor_id = :mentorId")
    Flux<Location> findAllByMentorId(Long mentorId);
}
