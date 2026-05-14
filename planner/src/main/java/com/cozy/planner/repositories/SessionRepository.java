package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Session;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Repository
public interface SessionRepository extends ReactiveCrudRepository<Session, Long> {

    @Query("SELECT * FROM meetings WHERE mentor_id = :mentorId AND meeting_date BETWEEN :start AND :end")
    Flux<Session> findAllByMentorIdAndWorkoutDateBetween(Long mentorId, LocalDate start, LocalDate end);

    @Query("SELECT w.* FROM meetings w " +
            "JOIN meeting_trainees mt ON w.id = mt.meeting_id " +
            "WHERE w.mentor_id = :mentorId AND mt.trainee_id = :traineeId " +
            "AND w.meeting_date BETWEEN :start AND :end")
    Flux<Session> findAllByMentorAndTraineeInPeriod(Long mentorId, Long traineeId, LocalDate start, LocalDate end);

    @Query("SELECT trainee_id FROM meeting_trainees WHERE meeting_id = :sessionId")
    Flux<Long> findTraineeIdsBySessionId(Long sessionId);

    @Query("DELETE FROM meeting_trainees WHERE meeting_id = :sessionId")
    Mono<Void> deleteTraineeLinks(Long sessionId);

    @Query("INSERT INTO meeting_trainees (meeting_id, trainee_id) VALUES (:sessionId, :traineeId)")
    Mono<Void> linkTraineeToSession(Long sessionId, Long traineeId);

    @Query("SELECT * FROM meetings WHERE reminder_sent = FALSE AND meeting_date BETWEEN :startDate AND :endDate")
    Flux<Session> findUpcomingWithoutReminder(LocalDate startDate, LocalDate endDate);

    @Query("SELECT * FROM meetings WHERE trainee_reminder_sent = FALSE AND meeting_date BETWEEN :startDate AND :endDate")
    Flux<Session> findUpcomingWithoutTraineeReminder(LocalDate startDate, LocalDate endDate);
}
