package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Trainee;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TraineeRepository extends ReactiveCrudRepository<Trainee, Long> {

    @Query("SELECT * FROM trainees WHERE mentor_id = :mentorId")
    Flux<Trainee> findAllByMentorId(Long mentorId);

    @Query("SELECT * FROM trainees WHERE invite_token = :token")
    Mono<Trainee> findByInviteToken(String token);

    @Query("SELECT * FROM trainees WHERE telegram_chat_id = :chatId")
    Mono<Trainee> findByTelegramChatId(String chatId);
    
    @Query("UPDATE trainees SET telegram_chat_id = NULL, telegram_username = NULL, telegram_connected_at = NULL WHERE telegram_chat_id = :chatId AND id != :excludeId")
    Mono<Integer> clearTelegramChatIdForOtherTrainees(String chatId, Long excludeId);

    @Query("SELECT * FROM trainees WHERE mentor_id = :mentorId AND weekend_reminder_enabled = TRUE AND telegram_chat_id IS NOT NULL AND telegram_chat_id != ''")
    Flux<Trainee> findAllByMentorIdForWeekendReminders(Long mentorId);
    
    @Query("SELECT * FROM trainees WHERE weekend_reminder_enabled = TRUE AND telegram_chat_id IS NOT NULL AND telegram_chat_id != ''")
    Flux<Trainee> findAllTraineesWithWeekendRemindersEnabled();
    
    @Query("SELECT * FROM trainees WHERE session_reminder_enabled = TRUE AND telegram_chat_id IS NOT NULL AND telegram_chat_id != ''")
    Flux<Trainee> findAllTraineesWithSessionRemindersEnabled();

    @Query("SELECT * FROM trainees WHERE name = :name AND mentor_id = :mentorId LIMIT 1")
    Mono<Trainee> findByNameAndMentorId(String name, Long mentorId);

    @Query("DELETE FROM trainees WHERE mentor_id = :mentorId")
    Mono<Void> deleteAllByMentorId(Long mentorId);
}
