package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.AvailabilityRange;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

public interface AvailabilityRangeRepository extends ReactiveCrudRepository<AvailabilityRange, Long> {

    Flux<AvailabilityRange> findByUserIdAndUserTypeAndDateBetween(Long userId, String userType, LocalDate startDate, LocalDate endDate);

    @Query("DELETE FROM availability_ranges WHERE user_id = :userId AND user_type = :userType AND date = :date")
    Mono<Integer> deleteByUserIdAndUserTypeAndDate(Long userId, String userType, LocalDate date);

    @Query("SELECT DISTINCT ON (user_id, user_type, date, start_time) * FROM availability_ranges WHERE user_id = :userId AND user_type = :userType AND date = :date")
    Flux<AvailabilityRange> findByUserIdAndUserTypeAndDate(Long userId, String userType, LocalDate date);
}
