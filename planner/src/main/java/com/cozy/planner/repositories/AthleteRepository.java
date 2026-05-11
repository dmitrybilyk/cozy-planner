package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Trainee;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Deprecated
public interface AthleteRepository extends ReactiveCrudRepository<Trainee, Long> {

    @Query("SELECT * FROM trainees WHERE mentor_id = :coachId")
    Flux<Trainee> findAllByCoachId(Long coachId);

    @Query("SELECT * FROM trainees WHERE invite_token = :token")
    Mono<Trainee> findByInviteToken(String token);
    
    @Query("SELECT * FROM trainees WHERE mentor_id = :coachId AND weekend_reminder_enabled = TRUE AND telegram_chat_id IS NOT NULL AND telegram_chat_id != ''")
    Flux<Trainee> findAllByCoachIdForWeekendReminders(Long coachId);
    
    @Query("SELECT * FROM trainees WHERE weekend_reminder_enabled = TRUE AND telegram_chat_id IS NOT NULL AND telegram_chat_id != ''")
    Flux<Trainee> findAllAthletesWithWeekendRemindersEnabled();
    
    @Query("SELECT * FROM trainees WHERE weekend_reminder_enabled = TRUE AND telegram_chat_id IS NOT NULL AND telegram_chat_id != ''")
    Flux<Trainee> findAllTraineesWithWeekendRemindersEnabled();
}
