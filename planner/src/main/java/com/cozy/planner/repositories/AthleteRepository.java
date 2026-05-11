package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Athlete;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AthleteRepository extends ReactiveCrudRepository<Athlete, Long> {
    Flux<Athlete> findAllByCoachId(Long coachId);
    @Query("SELECT * FROM athletes WHERE invite_token = :token")
    Mono<Athlete> findByInviteToken(String token);
    
    @Query("SELECT * FROM athletes WHERE coach_id = :coachId AND weekend_reminder_enabled = TRUE AND telegram_chat_id IS NOT NULL AND telegram_chat_id != ''")
    Flux<Athlete> findAllByCoachIdForWeekendReminders(Long coachId);
    
    @Query("SELECT * FROM athletes WHERE weekend_reminder_enabled = TRUE AND telegram_chat_id IS NOT NULL AND telegram_chat_id != ''")
    Flux<Athlete> findAllAthletesWithWeekendRemindersEnabled();
}