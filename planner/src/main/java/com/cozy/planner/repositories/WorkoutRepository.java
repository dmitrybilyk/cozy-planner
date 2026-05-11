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

    @Query("SELECT * FROM meetings WHERE mentor_id = :coachId AND meeting_date BETWEEN :start AND :end")
    Flux<Workout> findAllByCoachIdAndWorkoutDateBetween(Long coachId, LocalDate start, LocalDate end);

    @Query("SELECT w.* FROM meetings w " +
            "JOIN meeting_trainees mt ON w.id = mt.meeting_id " +
            "WHERE w.mentor_id = :coachId AND mt.trainee_id = :athleteId " +
            "AND w.meeting_date BETWEEN :start AND :end")
    Flux<Workout> findAllByCoachAndAthleteInPeriod(Long coachId, Long athleteId, LocalDate start, LocalDate end);

    @Query("SELECT trainee_id FROM meeting_trainees WHERE meeting_id = :workoutId")
    Flux<Long> findAthleteIdsByWorkoutId(Long workoutId);

    @Query("DELETE FROM meeting_trainees WHERE meeting_id = :workoutId")
    Mono<Void> deleteAthleteLinks(Long workoutId);

    @Query("INSERT INTO meeting_trainees (meeting_id, trainee_id) VALUES (:workoutId, :athleteId)")
    Mono<Void> linkAthleteToWorkout(Long workoutId, Long athleteId);

    @Query("SELECT * FROM meetings WHERE reminder_sent = FALSE AND meeting_date BETWEEN :startDate AND :endDate")
    Flux<Workout> findUpcomingWithoutReminder(LocalDate startDate, LocalDate endDate);
}
