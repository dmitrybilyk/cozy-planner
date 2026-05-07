package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Workout;
import com.cozy.planner.repositories.WorkoutRepository;
import com.planner.api.WorkoutsApi;
import com.planner.model.CreateWorkoutRequest;
import com.planner.model.WorkoutDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;

@RestController
public class WorkoutController implements WorkoutsApi {

    private final WorkoutRepository workoutRepository;

    public WorkoutController(WorkoutRepository workoutRepository) {
        this.workoutRepository = workoutRepository;
    }

    // --- READ ---
    @Override
    public Mono<ResponseEntity<Flux<WorkoutDTO>>> getWorkouts(
            LocalDate startDate,
            LocalDate endDate,
            Long coachId,
            Long athleteId,
            ServerWebExchange exchange) {

        Flux<Workout> workoutFlux = (athleteId != null)
                ? workoutRepository.findAllByCoachIdAndAthleteIdAndWorkoutDateBetween(coachId, athleteId, startDate, endDate)
                : workoutRepository.findAllByCoachIdAndWorkoutDateBetween(coachId, startDate, endDate);

        return Mono.just(ResponseEntity.ok(workoutFlux.map(this::mapToDto)));
    }

    // --- CREATE / UPDATE (Upsert) ---
    @Override
    public Mono<ResponseEntity<WorkoutDTO>> createWorkout(
            Mono<CreateWorkoutRequest> createWorkoutRequest,
            ServerWebExchange exchange) {

        return createWorkoutRequest
                .flatMap(request -> {
                    Workout entity = Workout.builder()
                            .id(request.getId()) // Якщо ID є, R2DBC оновить запис, якщо null — створить
                            .title(request.getTitle())
                            .description(request.getDescription())
                            .workoutDate(request.getDate())
                            .workoutTime(request.getTime() != null ? LocalTime.parse(request.getTime()) : null)
                            .durationMinutes(request.getDurationMinutes())
                            .athleteId(request.getAthleteId())
                            .coachId(request.getCoachId())
                            .build();

                    return workoutRepository.save(entity);
                })
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(mapToDto(saved)));
    }

    // --- DELETE ---
    @Override
    public Mono<ResponseEntity<Void>> deleteWorkout(Long workoutId, ServerWebExchange exchange) {
        return workoutRepository.findById(workoutId)
                .flatMap(workout -> workoutRepository.delete(workout)
                        .then(Mono.just(ResponseEntity.noContent().<Void>build())))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // --- READ SINGLE (якщо додано в OpenAPI) ---
    public Mono<ResponseEntity<WorkoutDTO>> getWorkoutById(Long workoutId) {
        return workoutRepository.findById(workoutId)
                .map(workout -> ResponseEntity.ok(mapToDto(workout)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private WorkoutDTO mapToDto(Workout entity) {
        WorkoutDTO dto = new WorkoutDTO();
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setDate(entity.getWorkoutDate());
        dto.setTime(entity.getWorkoutTime() != null ? entity.getWorkoutTime().toString() : null);
        dto.setDurationMinutes(entity.getDurationMinutes());
        dto.setAthleteId(entity.getAthleteId());
        dto.setCoachId(entity.getCoachId());
        return dto;
    }
}