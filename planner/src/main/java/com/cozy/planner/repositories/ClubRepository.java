package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Club;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClubRepository extends ReactiveCrudRepository<Club, Long> {
}