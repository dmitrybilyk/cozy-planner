package com.cozy.planner.controllers;

import com.cozy.planner.repositories.LocationRepository;
import com.cozy.planner.model.entity.Location;
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
public class LocationController implements LocationsApi {

    private final LocationRepository locationRepository;

    public LocationController(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Override
    public Mono<ResponseEntity<Flux<LocationDTO>>> getCoachLocations(Long coachId, ServerWebExchange exchange) {
        Flux<LocationDTO> dtoFlux = locationRepository.findAllByCoachId(coachId)
                .map(this::mapToDto);
        return Mono.just(ResponseEntity.ok(dtoFlux));
    }

    @Override
    public Mono<ResponseEntity<LocationDTO>> createLocation(Mono<CreateLocationRequest> request, ServerWebExchange exchange) {
        return request
                .flatMap(req -> {
                    Location entity = Location.builder()
                            .name(req.getName())
                            .description(req.getDescription())
                            .color(req.getColor() != null ? req.getColor() : "#3b82f6")
                            .coachId(req.getCoachId())
                            .build();
                    return locationRepository.save(entity);
                })
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(mapToDto(saved)));
    }

    @Override
    public Mono<ResponseEntity<LocationDTO>> getLocationById(Long locationId, ServerWebExchange exchange) {
        return locationRepository.findById(locationId)
                .map(this::mapToDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<LocationDTO>> updateLocation(Long locationId, Mono<LocationDTO> locationDTO, ServerWebExchange exchange) {
        return locationDTO.flatMap(dto ->
                locationRepository.findById(locationId)
                        .flatMap(existing -> {
                            existing.setName(dto.getName());
                            existing.setDescription(dto.getDescription());
                            existing.setColor(dto.getColor());
                            existing.setCoachId(dto.getCoachId());
                            return locationRepository.save(existing);
                        })
        )
                .map(this::mapToDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteLocation(Long locationId, ServerWebExchange exchange) {
        return locationRepository.findById(locationId)
                .flatMap(location -> locationRepository.delete(location)
                        .then(Mono.just(ResponseEntity.noContent().<Void>build())))
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
