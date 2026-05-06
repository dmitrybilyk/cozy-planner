package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Workout;
import com.cozy.planner.repositories.WorkoutRepository;
import com.planner.api.WorkoutsApi;
import com.planner.model.CreateWorkoutRequest;
import com.planner.model.Intensity;
import com.planner.model.WorkoutDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@RestController
public class WorkoutController implements WorkoutsApi {

    private final WorkoutRepository workoutRepository;

    public WorkoutController(WorkoutRepository workoutRepository) {
        this.workoutRepository = workoutRepository;
    }

    @Override
    public Mono<ResponseEntity<Flux<WorkoutDTO>>> getWorkouts(
            LocalDate startDate,
            LocalDate endDate,
            Long coachId,
            Long athleteId,
            ServerWebExchange exchange) {

        // 1. Fetch from DB (returns Flux<Workout>)
        Flux<WorkoutDTO> workoutDtoFlux = workoutRepository
                .findAllByCoachIdAndAthleteIdAndWorkoutDateBetween(coachId, athleteId, startDate, endDate)
                // 2. Map Entity to DTO
                .map(entity -> {
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
                });

        // 3. Wrap the Flux in a ResponseEntity and Mono
        return Mono.just(ResponseEntity.ok(workoutDtoFlux));
    }

    @Override
    public Mono<ResponseEntity<WorkoutDTO>> createWorkout(
            Mono<CreateWorkoutRequest> createWorkoutRequest,
            ServerWebExchange exchange) {

        return createWorkoutRequest
                .flatMap(request -> {
                    // 1. Map CreateWorkoutRequest (DTO) -> Workout (Entity)
                    Workout entity = Workout.builder()
                            .title(request.getTitle())
                            .description(request.getDescription())
                            .workoutDate(request.getDate())
                            .workoutTime(request.getTime() != null ? java.time.LocalTime.parse(request.getTime()) : null)
                            .durationMinutes(request.getDurationMinutes())
                            .athleteId(request.getAthleteId())
                            .coachId(request.getCoachId())
                            .build();

                    // 2. Save to PostgreSQL via R2DBC
                    return workoutRepository.save(entity);
                })
                .map(savedEntity -> {
                    // 3. Map Saved Workout (Entity) -> WorkoutDTO (Response DTO)
                    WorkoutDTO dto = new WorkoutDTO();
                    dto.setId(savedEntity.getId());
                    dto.setTitle(savedEntity.getTitle());
                    dto.setDescription(savedEntity.getDescription());
                    dto.setDate(savedEntity.getWorkoutDate());
                    dto.setTime(savedEntity.getWorkoutTime() != null ? savedEntity.getWorkoutTime().toString() : null);
                    dto.setDurationMinutes(savedEntity.getDurationMinutes());
                    dto.setAthleteId(savedEntity.getAthleteId());
                    dto.setCoachId(savedEntity.getCoachId());

                    return ResponseEntity.status(HttpStatus.CREATED).body(dto);
                });
    }
}