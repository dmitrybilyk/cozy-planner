package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.TraineeAvailability;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

public interface TraineeAvailabilityRepository extends ReactiveCrudRepository<TraineeAvailability, Long> {

    @Query("SELECT * FROM trainee_availability WHERE trainee_id = :traineeId AND date BETWEEN :start AND :end ORDER BY date, start_time")
    Flux<TraineeAvailability> findByTraineeIdAndDateBetween(Long traineeId, LocalDate start, LocalDate end);

    @Query("SELECT * FROM trainee_availability WHERE trainee_id = :traineeId AND date = :date ORDER BY start_time")
    Flux<TraineeAvailability> findByTraineeIdAndDate(Long traineeId, LocalDate date);
}
