package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.AvailabilityRange;
import com.cozy.planner.model.entity.Location;
import com.cozy.planner.model.entity.Session;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.repositories.AvailabilityRangeRepository;
import com.cozy.planner.repositories.LocationRepository;
import com.cozy.planner.repositories.SessionRepository;
import com.cozy.planner.repositories.TraineeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@RestController
public class TourDemoController {

    private final SessionRepository sessionRepository;
    private final TraineeRepository traineeRepository;
    private final LocationRepository locationRepository;
    private final AvailabilityRangeRepository rangeRepository;

    public TourDemoController(SessionRepository sessionRepository,
                               TraineeRepository traineeRepository,
                               LocationRepository locationRepository,
                               AvailabilityRangeRepository rangeRepository) {
        this.sessionRepository = sessionRepository;
        this.traineeRepository = traineeRepository;
        this.locationRepository = locationRepository;
        this.rangeRepository = rangeRepository;
    }

    @PostMapping("/api/v1/tour/demo")
    public Mono<ResponseEntity<Map<String, Object>>> createTourDemo(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            Object mentorIdObj = session.getAttribute("mentor_id");
            if (!(mentorIdObj instanceof Number)) {
                return Mono.just(ResponseEntity.status(401).<Map<String, Object>>build());
            }
            long mentorId = ((Number) mentorIdObj).longValue();
            if (mentorId <= 0) {
                return Mono.just(ResponseEntity.badRequest().<Map<String, Object>>build());
            }

            return cleanupDemoIds(session)
                    .then(Mono.defer(() -> {
                        LocalDate today = LocalDate.now();

                        Location loc1 = Location.builder()
                                .name("Зал А")
                                .color("#10b981")
                                .mentorId(mentorId)
                                .build();
                        Location loc2 = Location.builder()
                                .name("Зал Б")
                                .color("#f97316")
                                .mentorId(mentorId)
                                .build();

                        return Mono.zip(locationRepository.save(loc1), locationRepository.save(loc2))
                                .flatMap(locs -> {
                                    Location savedLoc1 = locs.getT1();
                                    Location savedLoc2 = locs.getT2();

                                    Trainee trainee = Trainee.builder()
                                            .name("Демо-клієнт")
                                            .mentorId(mentorId)
                                            .inviteToken("demo-tour-" + mentorId + "-" + System.currentTimeMillis())
                                            .build();

                                    AvailabilityRange range1 = AvailabilityRange.builder()
                                            .userId(mentorId)
                                            .userType("mentor")
                                            .date(today)
                                            .startTime(today.atTime(9, 0).atOffset(ZoneOffset.UTC))
                                            .endTime(today.atTime(12, 0).atOffset(ZoneOffset.UTC))
                                            .locationId(savedLoc1.getId())
                                            .freeAllDay(false)
                                            .build();

                                    AvailabilityRange range2 = AvailabilityRange.builder()
                                            .userId(mentorId)
                                            .userType("mentor")
                                            .date(today)
                                            .startTime(today.atTime(14, 0).atOffset(ZoneOffset.UTC))
                                            .endTime(today.atTime(18, 0).atOffset(ZoneOffset.UTC))
                                            .locationId(savedLoc2.getId())
                                            .freeAllDay(false)
                                            .build();

                                    return Mono.zip(
                                            traineeRepository.save(trainee),
                                            rangeRepository.save(range1),
                                            rangeRepository.save(range2)
                                    ).flatMap(tuple -> {
                                        Trainee savedTrainee = tuple.getT1();
                                        AvailabilityRange savedRange1 = tuple.getT2();
                                        AvailabilityRange savedRange2 = tuple.getT3();

                                        Session s = Session.builder()
                                                .title("Демо")
                                                .mentorId(mentorId)
                                                .locationId(savedLoc1.getId())
                                                .workoutDate(today)
                                                .startTime(LocalTime.of(10, 0))
                                                .endTime(LocalTime.of(11, 0))
                                                .confirmationStatus("CONFIRMED")
                                                .build();

                                        return sessionRepository.save(s).flatMap(savedSession ->
                                                sessionRepository.linkTraineeToSession(savedSession.getId(), savedTrainee.getId())
                                                        .then(Mono.fromSupplier(() -> {
                                                            session.getAttributes().put("tour_demo_session_id", savedSession.getId());
                                                            session.getAttributes().put("tour_demo_trainee_id", savedTrainee.getId());
                                                            session.getAttributes().put("tour_demo_location_id_1", savedLoc1.getId());
                                                            session.getAttributes().put("tour_demo_location_id_2", savedLoc2.getId());
                                                            session.getAttributes().put("tour_demo_range_id_1", savedRange1.getId());
                                                            session.getAttributes().put("tour_demo_range_id_2", savedRange2.getId());
                                                            Map<String, Object> result = new HashMap<>();
                                                            result.put("sessionId", savedSession.getId());
                                                            result.put("traineeId", savedTrainee.getId());
                                                            result.put("locationId1", savedLoc1.getId());
                                                            result.put("locationId2", savedLoc2.getId());
                                                            return ResponseEntity.ok(result);
                                                        }))
                                        );
                                    });
                                });
                    }));
        });
    }

    @DeleteMapping("/api/v1/tour/demo")
    public Mono<ResponseEntity<Void>> deleteTourDemo(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session ->
                cleanupDemoIds(session).then(Mono.just(ResponseEntity.ok().build()))
        );
    }

    private Mono<Void> cleanupDemoIds(WebSession session) {
        Object sessionIdObj = session.getAttribute("tour_demo_session_id");
        Object traineeIdObj = session.getAttribute("tour_demo_trainee_id");
        Object locId1Obj    = session.getAttribute("tour_demo_location_id_1");
        Object locId2Obj    = session.getAttribute("tour_demo_location_id_2");
        Object rangeId1Obj  = session.getAttribute("tour_demo_range_id_1");
        Object rangeId2Obj  = session.getAttribute("tour_demo_range_id_2");

        Mono<Void> work = Mono.empty();

        if (sessionIdObj instanceof Number) {
            long sid = ((Number) sessionIdObj).longValue();
            work = work.then(sessionRepository.deleteTraineeLinks(sid))
                       .then(sessionRepository.deleteById(sid)).then();
        }
        if (traineeIdObj instanceof Number) {
            long tid = ((Number) traineeIdObj).longValue();
            work = work.then(traineeRepository.deleteById(tid)).then();
        }
        if (rangeId1Obj instanceof Number) {
            work = work.then(rangeRepository.deleteById(((Number) rangeId1Obj).longValue())).then();
        }
        if (rangeId2Obj instanceof Number) {
            work = work.then(rangeRepository.deleteById(((Number) rangeId2Obj).longValue())).then();
        }
        if (locId1Obj instanceof Number) {
            work = work.then(locationRepository.deleteById(((Number) locId1Obj).longValue())).then();
        }
        if (locId2Obj instanceof Number) {
            work = work.then(locationRepository.deleteById(((Number) locId2Obj).longValue())).then();
        }

        return work.doOnSuccess(v -> {
            session.getAttributes().remove("tour_demo_session_id");
            session.getAttributes().remove("tour_demo_trainee_id");
            session.getAttributes().remove("tour_demo_location_id_1");
            session.getAttributes().remove("tour_demo_location_id_2");
            session.getAttributes().remove("tour_demo_range_id_1");
            session.getAttributes().remove("tour_demo_range_id_2");
        });
    }
}
