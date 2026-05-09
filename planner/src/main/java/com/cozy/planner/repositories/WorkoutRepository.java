package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Workout;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Repository
public interface WorkoutRepository extends ReactiveCrudRepository<Workout, Long> {

    // Знайти всі тренування тренера за період
    Flux<Workout> findAllByCoachIdAndWorkoutDateBetween(Long coachId, LocalDate start, LocalDate end);

    // Знайти тренування для конкретного атлета через JOIN з таблицею зв'язку
    @Query("SELECT w.* FROM workouts w " +
            "JOIN workout_athletes wa ON w.id = wa.workout_id " +
            "WHERE w.coach_id = :coachId AND wa.athlete_id = :athleteId " +
            "AND w.workout_date BETWEEN :start AND :end")
    Flux<Workout> findAllByCoachAndAthleteInPeriod(Long coachId, Long athleteId, LocalDate start, LocalDate end);

    // Отримати всі ID атлетів для конкретного тренування
    @Query("SELECT athlete_id FROM workout_athletes WHERE workout_id = :workoutId")
    Flux<Long> findAthleteIdsByWorkoutId(Long workoutId);

    // Видалити старі зв'язки (потрібно при оновленні тренування)
    @Query("DELETE FROM workout_athletes WHERE workout_id = :workoutId")
    Mono<Void> deleteAthleteLinks(Long workoutId);

    // Додати зв'язок атлета до тренування
    @Query("INSERT INTO workout_athletes (workout_id, athlete_id) VALUES (:workoutId, :athleteId)")
    Mono<Void> linkAthleteToWorkout(Long workoutId, Long athleteId);
}