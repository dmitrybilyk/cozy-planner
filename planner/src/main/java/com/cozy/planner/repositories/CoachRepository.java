package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.Coach;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface CoachRepository extends ReactiveCrudRepository<Coach, Long> {

    /**
     * Знайти всіх тренерів, які належать до певного клубу.
     * Використовується для ініціалізації списку тренерів при виборі клубу в системі.
     */
    Flux<Coach> findAllByClubId(Long clubId);

    /**
     * Пошук тренера за іменем (корисно для майбутнього пошуку або перевірки дублікатів).
     */
    Flux<Coach> findAllByNameContainingIgnoreCase(String name);

    @Query("SELECT * FROM coaches WHERE telegram_token = :token")
    Mono<Coach> findByTelegramToken(String token);
}