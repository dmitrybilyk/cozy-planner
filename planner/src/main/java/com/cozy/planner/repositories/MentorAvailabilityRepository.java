package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.MentorAvailability;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface MentorAvailabilityRepository extends ReactiveCrudRepository<MentorAvailability, Long> {

    @Query("SELECT * FROM mentor_availability WHERE mentor_id = :mentorId AND date BETWEEN :start AND :end ORDER BY date, start_time")
    Flux<MentorAvailability> findByMentorIdAndDateBetween(Long mentorId, LocalDate start, LocalDate end);

    @Query("SELECT * FROM mentor_availability WHERE mentor_id = :mentorId AND date = :date ORDER BY start_time")
    Flux<MentorAvailability> findByMentorIdAndDate(Long mentorId, LocalDate date);

    @Query("DELETE FROM mentor_availability WHERE mentor_id = :mentorId")
    Mono<Void> deleteByMentorId(Long mentorId);
}
