package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Workout;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import java.time.LocalDate;

public interface WorkoutRepository extends ReactiveCrudRepository<Workout, Long> {
    
    // Spring Data will automatically generate the logic based on the method name
    Flux<Workout> findAllByCoachIdAndAthleteIdAndWorkoutDateBetween(
            Long coachId, 
            Long athleteId, 
            LocalDate startDate, 
            LocalDate endDate
    );
}