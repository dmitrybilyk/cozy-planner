package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.AvailabilityRange;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface AvailabilityRangeRepository extends ReactiveCrudRepository<AvailabilityRange, Long> {

    Flux<AvailabilityRange> findByUserIdAndUserTypeAndDateBetween(@Param("userId") Long userId, @Param("userType") String userType, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("DELETE FROM availability_ranges WHERE user_id = :userId AND user_type = :userType AND date = :date")
    Mono<Integer> deleteByUserIdAndUserTypeAndDate(@Param("userId") Long userId, @Param("userType") String userType, @Param("date") LocalDate date);

    @Query("SELECT DISTINCT ON (user_id, user_type, date, start_time) * FROM availability_ranges WHERE user_id = :userId AND user_type = :userType AND date = :date")
    Flux<AvailabilityRange> findByUserIdAndUserTypeAndDate(@Param("userId") Long userId, @Param("userType") String userType, @Param("date") LocalDate date);
}
