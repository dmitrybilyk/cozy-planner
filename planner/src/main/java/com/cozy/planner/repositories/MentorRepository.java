package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Mentor;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MentorRepository extends ReactiveCrudRepository<Mentor, Long> {

    Flux<Mentor> findAllByClubId(Long clubId);

    @Query("SELECT * FROM mentors WHERE telegram_token = :token")
    Mono<Mentor> findByTelegramToken(String token);

    @Query("SELECT * FROM mentors WHERE share_token = :token")
    Mono<Mentor> findByShareToken(String token);

    @Query("UPDATE mentors SET telegram_chat_id = NULL, telegram_username = NULL, telegram_connected_at = NULL WHERE telegram_chat_id = :chatId AND id != :excludeId")
    Mono<Integer> clearTelegramChatIdForOtherMentors(String chatId, Long excludeId);

    @Query("DELETE FROM mentors WHERE id = :mentorId")
    Mono<Void> deleteAllByMentorId(Long mentorId);
}
