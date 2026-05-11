package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Workout;
import com.cozy.planner.repositories.WorkoutRepository;
import com.cozy.planner.service.EventBroadcastService;
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
import java.util.ArrayList;
import java.util.List;

@RestController
public class WorkoutsApiController implements WorkoutsApi {

    private final WorkoutRepository workoutRepository;
    private final EventBroadcastService eventBroadcastService;

    public WorkoutsApiController(WorkoutRepository workoutRepository, EventBroadcastService eventBroadcastService) {
        this.workoutRepository = workoutRepository;
        this.eventBroadcastService = eventBroadcastService;
    }

    @Override
    public Mono<ResponseEntity<WorkoutDTO>> createWorkout(Mono<CreateWorkoutRequest> createWorkoutRequest, ServerWebExchange exchange) {
        return createWorkoutRequest
                .flatMap(request -> {
                    Long workoutId = request.getId();
                    
                    if (workoutId != null && workoutId > 0) {
                        return workoutRepository.findById(workoutId)
                                .flatMap(existing -> updateWorkout(existing, request))
                                .switchIfEmpty(Mono.defer(() -> createNewWorkout(request)));
                    } else {
                        return createNewWorkout(request);
                    }
                })
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved));
    }

    private Mono<WorkoutDTO> createNewWorkout(CreateWorkoutRequest request) {
        Workout workout = Workout.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .workoutDate(request.getDate())
                .startTime(parseTime(request.getTime()))
                .endTime(parseTime(request.getEndTime()))
                .coachId(request.getCoachId())
                .locationId(request.getLocationId())
                .build();

        return workoutRepository.save(workout)
                .flatMap(saved -> saveAthleteLinks(saved.getId(), request.getAthleteIds())
                        .then(loadAthleteIds(saved))
                        .doOnSuccess(w -> eventBroadcastService.broadcast("workout_changed"))
                        .map(this::mapToDto));
    }

    private Mono<WorkoutDTO> updateWorkout(Workout existing, CreateWorkoutRequest request) {
        if (request.getTitle() != null) {
            existing.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getDate() != null) {
            existing.setWorkoutDate(request.getDate());
        }
        if (request.getTime() != null) {
            existing.setStartTime(parseTime(request.getTime()));
        }
        if (request.getEndTime() != null) {
            existing.setEndTime(parseTime(request.getEndTime()));
        }
        if (request.getLocationId() != null) {
            existing.setLocationId(request.getLocationId());
        }

        List<Long> athleteIds = request.getAthleteIds();

        return workoutRepository.save(existing)
                .flatMap(saved -> {
                    if (athleteIds != null) {
                        return workoutRepository.deleteAthleteLinks(saved.getId())
                                .then(saveAthleteLinks(saved.getId(), athleteIds))
                                .then(loadAthleteIds(saved))
                                .doOnSuccess(w -> eventBroadcastService.broadcast("workout_changed"))
                                .map(this::mapToDto);
                    }
                    return loadAthleteIds(saved)
                            .doOnSuccess(w -> eventBroadcastService.broadcast("workout_changed"))
                            .map(this::mapToDto);
                });
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteWorkout(Long workoutId, ServerWebExchange exchange) {
        return workoutRepository.findById(workoutId)
                .flatMap(workout -> workoutRepository.deleteAthleteLinks(workoutId)
                        .then(workoutRepository.delete(workout))
                        .then(Mono.fromRunnable(() -> eventBroadcastService.broadcast("workout_changed")))
                        .then(Mono.just(ResponseEntity.noContent().<Void>build())))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<Flux<WorkoutDTO>>> getWorkouts(LocalDate startDate, LocalDate endDate, Long coachId, Long athleteId, ServerWebExchange exchange) {
        Flux<WorkoutDTO> workoutFlux;
        
        if (athleteId != null && coachId != null) {
            workoutFlux = workoutRepository.findAllByCoachAndAthleteInPeriod(coachId, athleteId, startDate, endDate)
                    .flatMap(this::loadAthleteIds)
                    .map(this::mapToDto);
        } else if (coachId != null) {
            workoutFlux = workoutRepository.findAllByCoachIdAndWorkoutDateBetween(coachId, startDate, endDate)
                    .flatMap(this::loadAthleteIds)
                    .map(this::mapToDto);
        } else {
            workoutFlux = Flux.empty();
        }
        
        return Mono.just(ResponseEntity.ok(workoutFlux));
    }

    private Mono<Void> saveAthleteLinks(Long workoutId, List<Long> athleteIds) {
        if (athleteIds == null || athleteIds.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(athleteIds)
                .flatMap(id -> workoutRepository.linkAthleteToWorkout(workoutId, id))
                .then();
    }

    private Mono<Workout> loadAthleteIds(Workout workout) {
        return workoutRepository.findAthleteIdsByWorkoutId(workout.getId())
                .collectList()
                .doOnNext(workout::setAthleteIds)
                .thenReturn(workout);
    }

    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }
        return LocalTime.parse(timeStr);
    }

    private String formatTime(LocalTime time) {
        if (time == null) {
            return null;
        }
        return time.toString();
    }

    private WorkoutDTO mapToDto(Workout entity) {
        WorkoutDTO dto = new WorkoutDTO();
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setDate(entity.getWorkoutDate());
        dto.setTime(formatTime(entity.getStartTime()));
        dto.setEndTime(formatTime(entity.getEndTime()));
        dto.setCoachId(entity.getCoachId());
        dto.setLocationId(entity.getLocationId());
        
        if (entity.getAthleteIds() != null) {
            dto.setAthleteIds(new ArrayList<>(entity.getAthleteIds()));
        }
        
        return dto;
    }
}
