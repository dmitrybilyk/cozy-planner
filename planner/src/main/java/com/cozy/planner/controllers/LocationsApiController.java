package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Location;
import com.cozy.planner.repositories.LocationRepository;
import com.cozy.planner.service.EventBroadcastService;
import com.planner.api.LocationsApi;
import com.planner.model.CreateLocationRequest;
import com.planner.model.LocationDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class LocationsApiController implements LocationsApi {

    private final LocationRepository locationRepository;
    private final EventBroadcastService eventBroadcastService;

    public LocationsApiController(LocationRepository locationRepository, EventBroadcastService eventBroadcastService) {
        this.locationRepository = locationRepository;
        this.eventBroadcastService = eventBroadcastService;
    }

    @Override
    public Mono<ResponseEntity<LocationDTO>> createLocation(Mono<CreateLocationRequest> createLocationRequest, ServerWebExchange exchange) {
        return createLocationRequest
                .flatMap(request -> {
                    Location location = Location.builder()
                            .name(request.getName())
                            .description(request.getDescription())
                            .color(request.getColor())
                            .coachId(request.getCoachId())
                            .build();
                    return locationRepository.save(location);
                })
                .doOnSuccess(saved -> eventBroadcastService.broadcast("location_changed"))
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(mapToDto(saved)));
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteLocation(Long locationId, ServerWebExchange exchange) {
        return locationRepository.findById(locationId)
                .flatMap(location -> locationRepository.delete(location)
                        .then(Mono.fromRunnable(() -> eventBroadcastService.broadcast("location_changed")))
                        .then(Mono.just(ResponseEntity.noContent().<Void>build())))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<LocationDTO>> getLocationById(Long locationId, ServerWebExchange exchange) {
        return locationRepository.findById(locationId)
                .map(this::mapToDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<Flux<LocationDTO>>> getCoachLocations(Long coachId, ServerWebExchange exchange) {
        Flux<LocationDTO> locationFlux = locationRepository.findAllByCoachId(coachId)
                .map(this::mapToDto);
        return Mono.just(ResponseEntity.ok(locationFlux));
    }

    @Override
    public Mono<ResponseEntity<LocationDTO>> updateLocation(Long locationId, Mono<LocationDTO> locationDTO, ServerWebExchange exchange) {
        return locationDTO
                .flatMap(dto -> locationRepository.findById(locationId)
                        .flatMap(existing -> {
                            if (dto.getName() != null) {
                                existing.setName(dto.getName());
                            }
                            if (dto.getDescription() != null) {
                                existing.setDescription(dto.getDescription());
                            }
                            if (dto.getColor() != null) {
                                existing.setColor(dto.getColor());
                            }
                            if (dto.getCoachId() != null) {
                                existing.setCoachId(dto.getCoachId());
                            }
                            return locationRepository.save(existing);
                        }))
                .doOnSuccess(updated -> eventBroadcastService.broadcast("location_changed"))
                .map(updated -> ResponseEntity.ok(mapToDto(updated)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private LocationDTO mapToDto(Location entity) {
        LocationDTO dto = new LocationDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setColor(entity.getColor());
        dto.setCoachId(entity.getCoachId());
        return dto;
    }
}
