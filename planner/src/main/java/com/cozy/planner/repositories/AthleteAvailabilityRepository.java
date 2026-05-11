package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.AthleteAvailability;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

public interface AthleteAvailabilityRepository extends ReactiveCrudRepository<AthleteAvailability, Long> {

    @Query("SELECT * FROM trainee_availability WHERE trainee_id = :athleteId AND date BETWEEN :start AND :end ORDER BY date, start_time")
    Flux<AthleteAvailability> findByAthleteIdAndDateBetween(Long athleteId, LocalDate start, LocalDate end);

    @Query("SELECT * FROM trainee_availability WHERE trainee_id = :athleteId AND date = :date ORDER BY start_time")
    Flux<AthleteAvailability> findByAthleteIdAndDate(Long athleteId, LocalDate date);
}
