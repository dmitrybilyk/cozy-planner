package com.cozy.planner.controllers.mentor;

import com.cozy.planner.model.entity.Club;
import com.cozy.planner.repositories.ClubRepository;
import com.planner.api.ClubsApi;
import com.planner.model.ClubDTO;
import com.planner.model.CreateClubRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class ClubController implements ClubsApi {

    private final ClubRepository clubRepository;

    public ClubController(ClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    @Override
    public Mono<ResponseEntity<Flux<ClubDTO>>> getClubs(ServerWebExchange exchange) {
        Flux<ClubDTO> clubFlux = clubRepository.findAll()
                .map(this::mapToDto);
        
        return Mono.just(ResponseEntity.ok(clubFlux));
    }

    @Override
    public Mono<ResponseEntity<ClubDTO>> getClubById(Long clubId, ServerWebExchange exchange) {
        return clubRepository.findById(clubId)
                .map(this::mapToDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<ClubDTO>> createClub(Mono<CreateClubRequest> createClubRequest, ServerWebExchange exchange) {
        return createClubRequest
                .flatMap(request -> {
                    Club club = Club.builder()
                            .name(request.getName())
                            .description(request.getDescription())
                            .build();
                    return clubRepository.save(club);
                })
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(mapToDto(saved)));
    }

    @Override
    public Mono<ResponseEntity<ClubDTO>> updateClub(Long clubId, Mono<ClubDTO> clubDTO, ServerWebExchange exchange) {
        return clubDTO
                .flatMap(dto -> clubRepository.findById(clubId)
                        .flatMap(existingClub -> {
                            existingClub.setName(dto.getName());
                            existingClub.setDescription(dto.getDescription());
                            return clubRepository.save(existingClub);
                        }))
                .map(updated -> ResponseEntity.ok(mapToDto(updated)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteClub(Long clubId, ServerWebExchange exchange) {
        return clubRepository.findById(clubId)
                .flatMap(club -> clubRepository.delete(club)
                        .then(Mono.just(ResponseEntity.noContent().<Void>build())))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private ClubDTO mapToDto(Club entity) {
        ClubDTO dto = new ClubDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        return dto;
    }
}