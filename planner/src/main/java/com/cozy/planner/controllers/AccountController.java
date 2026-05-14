package com.cozy.planner.controllers;

import com.cozy.planner.repositories.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final MentorRepository mentorRepository;
    private final TraineeRepository traineeRepository;
    private final SessionRepository sessionRepository;
    private final LocationRepository locationRepository;
    private final TraineeAvailabilityRepository traineeAvailabilityRepository;

    public AccountController(UserRepository userRepository,
                             ClubRepository clubRepository,
                             MentorRepository mentorRepository,
                             TraineeRepository traineeRepository,
                             SessionRepository sessionRepository,
                             LocationRepository locationRepository,
                             TraineeAvailabilityRepository traineeAvailabilityRepository) {
        this.userRepository = userRepository;
        this.clubRepository = clubRepository;
        this.mentorRepository = mentorRepository;
        this.traineeRepository = traineeRepository;
        this.sessionRepository = sessionRepository;
        this.locationRepository = locationRepository;
        this.traineeAvailabilityRepository = traineeAvailabilityRepository;
    }

    @PostMapping("/reset")
    public Mono<ResponseEntity<Map<String, Object>>> reset(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            String googleSub = session.getAttribute("google_sub");
            if (googleSub == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("reason", "Not authenticated");
                return Mono.just(ResponseEntity.badRequest().body(result));
            }

            return userRepository.findByGoogleSub(googleSub)
                    .flatMap(user -> clubRepository.findByUserId(user.getId())
                            .next()
                            .flatMap(club -> mentorRepository.findAllByClubId(club.getId())
                                    .next()
                                    .flatMap(mentor -> deleteAllData(mentor.getId())
                                            .then(clubRepository.delete(club))
                                            .then(userRepository.delete(user))
                                            .then(session.invalidate())
                                            .thenReturn(successResult("All data deleted"))
                                    )
                                    .switchIfEmpty(Mono.defer(() ->
                                            clubRepository.delete(club)
                                                    .then(userRepository.delete(user))
                                                    .then(session.invalidate())
                                                    .thenReturn(successResult("All data deleted"))
                                    ))
                            )
                            .switchIfEmpty(Mono.defer(() ->
                                    userRepository.delete(user)
                                            .then(session.invalidate())
                                            .thenReturn(successResult("All data deleted"))
                            ))
                    )
                    .switchIfEmpty(Mono.defer(() ->
                            session.invalidate().thenReturn(successResult("No data found"))
                    ));
        });
    }

    private ResponseEntity<Map<String, Object>> successResult(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", message);
        return ResponseEntity.ok(result);
    }

    private Mono<Void> deleteAllData(Long mentorId) {
        return traineeRepository.findAllByMentorId(mentorId)
                .flatMap(trainee -> traineeAvailabilityRepository.deleteByTraineeId(trainee.getId()))
                .then(sessionRepository.deleteTraineeLinksByMentorId(mentorId))
                .then(sessionRepository.deleteAllByMentorId(mentorId))
                .then(locationRepository.deleteAllByMentorId(mentorId))
                .then(traineeRepository.deleteAllByMentorId(mentorId))
                .then(mentorRepository.deleteAllByMentorId(mentorId));
    }
}
