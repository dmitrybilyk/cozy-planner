package com.cozy.planner.controllers.session;

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

            return cleanupDemoIds(session, mentorId)
                    .then(Mono.defer(() -> {
                        LocalDate today    = LocalDate.now();
                        LocalDate tomorrow = today.plusDays(1);
                        long ts = System.currentTimeMillis();

                        Location loc1 = Location.builder().name("Зал А").color("#10b981").mentorId(mentorId).build();
                        Location loc2 = Location.builder().name("Зал Б").color("#f97316").mentorId(mentorId).build();

                        return Mono.zip(locationRepository.save(loc1), locationRepository.save(loc2))
                                .flatMap(locs -> {
                                    long loc1Id = locs.getT1().getId();
                                    long loc2Id = locs.getT2().getId();

                                    Trainee t1 = Trainee.builder().name("Тур Учень 1").mentorId(mentorId)
                                            .inviteToken("demo-t1-" + mentorId + "-" + ts).build();
                                    Trainee t2 = Trainee.builder().name("Тур Учень 2").mentorId(mentorId)
                                            .inviteToken("demo-t2-" + mentorId + "-" + ts).build();

                                    AvailabilityRange r1 = range(mentorId, today, 9, 12, loc1Id);
                                    AvailabilityRange r2 = range(mentorId, today, 14, 18, loc2Id);
                                    AvailabilityRange r3 = range(mentorId, tomorrow, 10, 13, loc1Id);
                                    AvailabilityRange r4 = range(mentorId, tomorrow, 15, 17, loc2Id);

                                    Mono<Trainee> saveT1 = traineeRepository.findByNameAndMentorId("Тур Учень 1", mentorId)
                                            .switchIfEmpty(traineeRepository.save(t1));
                                    Mono<Trainee> saveT2 = traineeRepository.findByNameAndMentorId("Тур Учень 2", mentorId)
                                            .switchIfEmpty(traineeRepository.save(t2));

                                    return Mono.zip(
                                            saveT1, saveT2,
                                            rangeRepository.save(r1), rangeRepository.save(r2),
                                            rangeRepository.save(r3), rangeRepository.save(r4)
                                    ).flatMap(tuple -> {
                                        Trainee st1 = tuple.getT1();
                                        Trainee st2 = tuple.getT2();
                                        AvailabilityRange sr1 = tuple.getT3();
                                        AvailabilityRange sr2 = tuple.getT4();
                                        AvailabilityRange sr3 = tuple.getT5();
                                        AvailabilityRange sr4 = tuple.getT6();

                                        Session s = Session.builder()
                                                .title("Демо")
                                                .mentorId(mentorId)
                                                .locationId(loc1Id)
                                                .workoutDate(today)
                                                .startTime(LocalTime.of(10, 0))
                                                .endTime(LocalTime.of(11, 0))
                                                .confirmationStatus("CONFIRMED")
                                                .confirmedTraineeIds(String.valueOf(st1.getId()))
                                                .rejectedTraineeIds(String.valueOf(st2.getId()))
                                                .build();

                                        return sessionRepository.save(s).flatMap(savedSession ->
                                                sessionRepository.linkTraineeToSession(savedSession.getId(), st1.getId())
                                                        .then(sessionRepository.linkTraineeToSession(savedSession.getId(), st2.getId()))
                                                        .then(Mono.defer(() -> {
                                                            session.getAttributes().put("tour_demo_session_id",    savedSession.getId());
                                                            session.getAttributes().put("tour_demo_trainee_id_1",  st1.getId());
                                                            session.getAttributes().put("tour_demo_trainee_id_2",  st2.getId());
                                                            session.getAttributes().put("tour_demo_location_id_1", loc1Id);
                                                            session.getAttributes().put("tour_demo_location_id_2", loc2Id);
                                                            session.getAttributes().put("tour_demo_range_id_1",    sr1.getId());
                                                            session.getAttributes().put("tour_demo_range_id_2",    sr2.getId());
                                                            session.getAttributes().put("tour_demo_range_id_3",    sr3.getId());
                                                            session.getAttributes().put("tour_demo_range_id_4",    sr4.getId());
                                                            Map<String, Object> result = new HashMap<>();
                                                            result.put("sessionId", savedSession.getId());
                                                            return session.save()
                                                                    .thenReturn(ResponseEntity.<Map<String, Object>>ok(result));
                                                        }))
                                        );
                                    });
                                });
                    }));
        });
    }

    @DeleteMapping("/api/v1/tour/demo")
    public Mono<ResponseEntity<Void>> deleteTourDemo(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            Object mentorIdObj = session.getAttribute("mentor_id");
            long mentorId = mentorIdObj instanceof Number ? ((Number) mentorIdObj).longValue() : 0;
            return cleanupDemoIds(session, mentorId).then(Mono.just(ResponseEntity.ok().build()));
        });
    }

    private AvailabilityRange range(long mentorId, LocalDate date, int startHour, int endHour, long locationId) {
        return AvailabilityRange.builder()
                .userId(mentorId).userType("COACH").date(date)
                .startTime(date.atTime(startHour, 0).atOffset(ZoneOffset.UTC))
                .endTime(date.atTime(endHour, 0).atOffset(ZoneOffset.UTC))
                .locationId(locationId).freeAllDay(false)
                .build();
    }

    private Mono<Void> cleanupDemoIds(WebSession session, long mentorId) {
        Object sessionIdObj  = session.getAttribute("tour_demo_session_id");
        Object traineeId1Obj = session.getAttribute("tour_demo_trainee_id_1");
        Object traineeId2Obj = session.getAttribute("tour_demo_trainee_id_2");
        Object locId1Obj     = session.getAttribute("tour_demo_location_id_1");
        Object locId2Obj     = session.getAttribute("tour_demo_location_id_2");
        Object rangeId1Obj   = session.getAttribute("tour_demo_range_id_1");
        Object rangeId2Obj   = session.getAttribute("tour_demo_range_id_2");
        Object rangeId3Obj   = session.getAttribute("tour_demo_range_id_3");
        Object rangeId4Obj   = session.getAttribute("tour_demo_range_id_4");

        Mono<Void> work = Mono.empty();

        if (sessionIdObj instanceof Number) {
            long sid = ((Number) sessionIdObj).longValue();
            work = work.then(sessionRepository.deleteTraineeLinks(sid))
                       .then(sessionRepository.deleteById(sid)).then();
        }
        for (Object tid : new Object[]{traineeId1Obj, traineeId2Obj}) {
            if (tid instanceof Number)
                work = work.then(traineeRepository.deleteById(((Number) tid).longValue())).then();
        }
        for (Object rid : new Object[]{rangeId1Obj, rangeId2Obj, rangeId3Obj, rangeId4Obj}) {
            if (rid instanceof Number)
                work = work.then(rangeRepository.deleteById(((Number) rid).longValue())).then();
        }
        for (Object lid : new Object[]{locId1Obj, locId2Obj}) {
            if (lid instanceof Number)
                work = work.then(locationRepository.deleteById(((Number) lid).longValue())).then();
        }

        // Also clean up orphaned tour demo data by mentor_id + known names/titles
        Mono<Void> orphanCleanup = sessionRepository.findByMentorIdAndTitle(mentorId, "Демо")
            .flatMap(s -> sessionRepository.deleteTraineeLinks(s.getId())
                .then(sessionRepository.deleteById(s.getId())))
            .then(Mono.defer(() ->
                traineeRepository.findAllByMentorId(mentorId)
                    .filter(t -> "Тур Учень 1".equals(t.getName()) || "Тур Учень 2".equals(t.getName()))
                    .flatMap(t -> traineeRepository.deleteById(t.getId()))
                    .then()
            ));

        return work.then(orphanCleanup).doOnSuccess(v -> {
            for (String key : new String[]{
                    "tour_demo_session_id",
                    "tour_demo_trainee_id_1", "tour_demo_trainee_id_2",
                    "tour_demo_location_id_1", "tour_demo_location_id_2",
                    "tour_demo_range_id_1", "tour_demo_range_id_2",
                    "tour_demo_range_id_3", "tour_demo_range_id_4"
            }) session.getAttributes().remove(key);
        });
    }
}
