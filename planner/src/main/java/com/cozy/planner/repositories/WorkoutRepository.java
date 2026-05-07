package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Workout;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import java.time.LocalDate;

public interface WorkoutRepository extends ReactiveCrudRepository<Workout, Long> {

    // Для конкретного атлета
    Flux<Workout> findAllByCoachIdAndAthleteIdAndWorkoutDateBetween(Long coachId, Long athleteId, LocalDate start, LocalDate end);

    // Для всієї команди тренера
    Flux<Workout> findAllByCoachIdAndWorkoutDateBetween(Long coachId, LocalDate start, LocalDate end);
}