package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Workout;
import com.cozy.planner.repositories.LocationRepository;
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
import java.util.Collections;

@RestController
public class WorkoutController implements WorkoutsApi {

    private static final String DEFAULT_COLOR = "#3b82f6";

    private final WorkoutRepository workoutRepository;
    private final LocationRepository locationRepository;
    private final EventBroadcastService eventService;

    public WorkoutController(WorkoutRepository workoutRepository, LocationRepository locationRepository, EventBroadcastService eventService) {
        this.workoutRepository = workoutRepository;
        this.locationRepository = locationRepository;
        this.eventService = eventService;
    }

    @Override
    public Mono<ResponseEntity<Flux<WorkoutDTO>>> getWorkouts(
            LocalDate startDate, LocalDate endDate,
            Long coachId, Long athleteId, ServerWebExchange exchange) {

        Flux<Workout> workouts = (athleteId != null)
                ? workoutRepository.findAllByCoachAndAthleteInPeriod(coachId, athleteId, startDate, endDate)
                : workoutRepository.findAllByCoachIdAndWorkoutDateBetween(coachId, startDate, endDate);

        Flux<WorkoutDTO> dtoFlux = workouts.flatMap(w ->
                workoutRepository.findAthleteIdsByWorkoutId(w.getId())
                        .collectList()
                        .flatMap(ids -> {
                            w.setAthleteIds(ids);
                            return enrichWithColor(w);
                        })
        );

        return Mono.just(ResponseEntity.ok(dtoFlux));
    }

    @Override
    public Mono<ResponseEntity<WorkoutDTO>> createWorkout(
            Mono<CreateWorkoutRequest> createWorkoutRequest, ServerWebExchange exchange) {

        return createWorkoutRequest.flatMap(req -> {
            Workout entity = Workout.builder()
                    .id(req.getId())
                    .title(req.getTitle())
                    .description(req.getDescription())
                    .workoutDate(req.getDate())
                    .startTime(req.getTime() != null ? LocalTime.parse(req.getTime()) : null)
                    .endTime(req.getEndTime() != null ? LocalTime.parse(req.getEndTime()) : null)
                    .coachId(req.getCoachId())
                    .locationId(req.getLocationId())
                    .build();

            return workoutRepository.save(entity)
                    .flatMap(saved -> {
                        Mono<Void> cleanOldLinks = (req.getId() != null)
                                ? workoutRepository.deleteAthleteLinks(saved.getId())
                                : Mono.empty();

                        Mono<Void> addNewLinks = Flux.fromIterable(
                                        req.getAthleteIds() != null ? req.getAthleteIds() : Collections.<Long>emptyList())
                                .flatMap(aId -> workoutRepository.linkAthleteToWorkout(saved.getId(), aId))
                                .then();

                        return cleanOldLinks.then(addNewLinks).then(Mono.just(saved));
                    })
                    .flatMap(saved -> {
                        saved.setAthleteIds(req.getAthleteIds());
                        return enrichWithColor(saved);
                    })
                    .map(dto -> {
                        eventService.broadcast("workout_changed");
                        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
                    });
        });
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteWorkout(Long workoutId, ServerWebExchange exchange) {
        return workoutRepository.findById(workoutId)
                .flatMap(w -> workoutRepository.delete(w)
                        .then(Mono.fromRunnable(() -> eventService.broadcast("workout_changed")))
                        .then(Mono.just(ResponseEntity.noContent().<Void>build())))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private Mono<WorkoutDTO> enrichWithColor(Workout entity) {
        Mono<String> colorMono;
        if (entity.getLocationId() != null) {
            colorMono = locationRepository.findById(entity.getLocationId())
                    .map(loc -> loc.getColor() != null ? loc.getColor() : DEFAULT_COLOR)
                    .defaultIfEmpty(DEFAULT_COLOR);
        } else {
            colorMono = Mono.just(DEFAULT_COLOR);
        }

        return colorMono.map(color -> {
            WorkoutDTO dto = mapToDto(entity);
            dto.setColor(color);
            return dto;
        });
    }

    private WorkoutDTO mapToDto(Workout entity) {
        WorkoutDTO dto = new WorkoutDTO();
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setDate(entity.getWorkoutDate());
        dto.setTime(entity.getStartTime() != null ? entity.getStartTime().toString() : null);
        dto.setEndTime(entity.getEndTime() != null ? entity.getEndTime().toString() : null);
        dto.setCoachId(entity.getCoachId());
        dto.setLocationId(entity.getLocationId());
        dto.setAthleteIds(entity.getAthleteIds());
        return dto;
    }
}
