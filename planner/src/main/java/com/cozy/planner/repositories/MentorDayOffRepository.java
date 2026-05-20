package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.MentorDayOff;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface MentorDayOffRepository extends ReactiveCrudRepository<MentorDayOff, Long> {

    @Query("SELECT * FROM mentor_day_offs WHERE mentor_id = :mentorId AND date BETWEEN :start AND :end ORDER BY date")
    Flux<MentorDayOff> findByMentorIdAndDateBetween(Long mentorId, LocalDate start, LocalDate end);

    @Query("SELECT * FROM mentor_day_offs WHERE mentor_id = :mentorId AND date = :date")
    Mono<MentorDayOff> findByMentorIdAndDate(Long mentorId, LocalDate date);

    @Query("DELETE FROM mentor_day_offs WHERE mentor_id = :mentorId AND date = :date")
    Mono<Void> deleteByMentorIdAndDate(Long mentorId, LocalDate date);
}
