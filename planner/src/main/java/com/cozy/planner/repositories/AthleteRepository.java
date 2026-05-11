package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Athlete;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AthleteRepository extends ReactiveCrudRepository<Athlete, Long> {

    @Query("SELECT * FROM trainees WHERE mentor_id = :coachId")
    Flux<Athlete> findAllByCoachId(Long coachId);

    @Query("SELECT * FROM trainees WHERE invite_token = :token")
    Mono<Athlete> findByInviteToken(String token);
    
    @Query("SELECT * FROM trainees WHERE mentor_id = :coachId AND weekend_reminder_enabled = TRUE AND telegram_chat_id IS NOT NULL AND telegram_chat_id != ''")
    Flux<Athlete> findAllByCoachIdForWeekendReminders(Long coachId);
    
    @Query("SELECT * FROM trainees WHERE weekend_reminder_enabled = TRUE AND telegram_chat_id IS NOT NULL AND telegram_chat_id != ''")
    Flux<Athlete> findAllAthletesWithWeekendRemindersEnabled();
}
