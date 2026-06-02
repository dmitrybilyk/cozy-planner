package com.cozy.planner.service;

import com.cozy.planner.model.entity.Session;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.model.entity.TraineeAvailability;
import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.repositories.SessionRepository;
import com.cozy.planner.repositories.TraineeRepository;
import com.cozy.planner.repositories.TraineeAvailabilityRepository;
import com.cozy.planner.repositories.MentorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating session creation suggestions based on trainee availability.
 * Excludes times that already have sessions scheduled and respects coach's timezone.
 */
@Service
@Slf4j
public class SessionCreationSuggestionService {

    private final TraineeAvailabilityRepository traineeAvailabilityRepository;
    private final SessionRepository sessionRepository;
    private final TraineeRepository traineeRepository;
    private final MentorRepository mentorRepository;

    public SessionCreationSuggestionService(TraineeAvailabilityRepository traineeAvailabilityRepository,
                                          SessionRepository sessionRepository,
                                          TraineeRepository traineeRepository,
                                          MentorRepository mentorRepository) {
        this.traineeAvailabilityRepository = traineeAvailabilityRepository;
        this.sessionRepository = sessionRepository;
        this.traineeRepository = traineeRepository;
        this.mentorRepository = mentorRepository;
    }

    /**
     * Generates session creation suggestions when coach clicks on a trainee availability interval.
     * Returns available time slots and pre-filled form values.
     *
     * @param traineeId The trainee whose availability was clicked
     * @param mentorId The coach/mentor
     * @param date The date of the clicked availability
     * @param clickedStartTime The start time of clicked interval
     * @return Map with availableSlots, suggestedStartTime, suggestedEndTime
     */
    public Mono<Map<String, Object>> generateSessionSuggestion(Long traineeId, Long mentorId, LocalDate date, LocalTime clickedStartTime) {
        return mentorRepository.findById(mentorId)
                .flatMap(mentor -> {
                    String coachTimezone = mentor.getTimezone() != null ? mentor.getTimezone() : "Europe/Kiev";
                    ZoneId coachZone = ZoneId.of(coachTimezone);
                    
                    // Get all trainee availability for the date
                    return traineeAvailabilityRepository.findByTraineeIdAndDate(traineeId, date)
                            .collectList()
                            .flatMap(allAvailability -> {
                                // Get all sessions for the date to exclude busy times
                                return sessionRepository.findAllByMentorIdAndWorkoutDate(mentorId, date)
                                        .collectList()
                                        .flatMap(sessionsOnDate -> {
                                            Map<String, Object> response = new HashMap<>();
                                            
                                            // Calculate available time slots
                                            List<Map<String, String>> availableSlots = calculateAvailableSlots(
                                                    allAvailability, 
                                                    sessionsOnDate,
                                                    coachZone
                                            );
                                            
                                            // Set suggested times based on clicked interval
                                            LocalTime suggestedStart = clickedStartTime;
                                            LocalTime suggestedEnd = clickedStartTime.plusHours(1);
                                            
                                            response.put("date", date.toString());
                                            response.put("traineeId", traineeId);
                                            response.put("availableSlots", availableSlots);
                                            response.put("suggestedStartTime", suggestedStart.toString());
                                            response.put("suggestedEndTime", suggestedEnd.toString());
                                            
                                            return Mono.just(response);
                                        });
                            });
                })
                .doOnError(e -> log.error("Error generating session suggestion", e));
    }

    /**
     * Calculates available time slots by combining all trainee availability
     * and excluding times that have existing sessions.
     */
    private List<Map<String, String>> calculateAvailableSlots(List<TraineeAvailability> allAvailability,
                                                              List<Session> sessionsOnDate,
                                                              ZoneId coachZone) {
        List<Map<String, String>> slots = new ArrayList<>();
        
        // Convert availability to sortable intervals
        List<TimeInterval> availableIntervals = allAvailability.stream()
                .map(a -> new TimeInterval(a.getStartTime(), a.getEndTime()))
                .collect(Collectors.toList());
        
        // Convert sessions to exclusion intervals
        List<TimeInterval> busyIntervals = sessionsOnDate.stream()
                .filter(s -> s.getEndTime() != null)
                .map(s -> new TimeInterval(s.getStartTime(), s.getEndTime()))
                .collect(Collectors.toList());
        
        // Calculate free intervals
        List<TimeInterval> freeIntervals = calculateFreeIntervals(availableIntervals, busyIntervals);
        
        // Convert to response format
        for (TimeInterval interval : freeIntervals) {
            Map<String, String> slot = new HashMap<>();
            slot.put("startTime", interval.start.toString());
            slot.put("endTime", interval.end.toString());
            slots.add(slot);
        }
        
        return slots;
    }

    /**
     * Calculates free time intervals by removing busy intervals from available intervals.
     */
    private List<TimeInterval> calculateFreeIntervals(List<TimeInterval> available, List<TimeInterval> busyIntervals) {
        if (available.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Merge overlapping availability intervals
        List<TimeInterval> mergedAvailable = mergeIntervals(available);
        
        // Merge overlapping busy intervals
        List<TimeInterval> mergedBusy = mergeIntervals(busyIntervals);
        
        // Remove busy times from available times
        List<TimeInterval> free = new ArrayList<>(mergedAvailable);
        
        for (TimeInterval busySlot : mergedBusy) {
            List<TimeInterval> newFree = new ArrayList<>();
            for (TimeInterval freeSlot : free) {
                newFree.addAll(subtractInterval(freeSlot, busySlot));
            }
            free = newFree;
        }
        
        return free;
    }

    /**
     * Merges overlapping intervals.
     */
    private List<TimeInterval> mergeIntervals(List<TimeInterval> intervals) {
        if (intervals.isEmpty()) {
            return new ArrayList<>();
        }
        
        intervals.sort(Comparator.comparing(i -> i.start));
        
        List<TimeInterval> merged = new ArrayList<>();
        TimeInterval current = intervals.get(0);
        
        for (int i = 1; i < intervals.size(); i++) {
            TimeInterval next = intervals.get(i);
            if (current.end.isAfter(next.start) || current.end.equals(next.start)) {
                // Overlapping or adjacent, merge
                current = new TimeInterval(current.start, 
                    next.end.isAfter(current.end) ? next.end : current.end);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        
        return merged;
    }

    /**
     * Subtracts a busy interval from a free interval.
     */
    private List<TimeInterval> subtractInterval(TimeInterval free, TimeInterval busy) {
        List<TimeInterval> result = new ArrayList<>();
        
        // No overlap
        if (busy.end.isAfter(free.end) && busy.start.isAfter(free.end)) {
            result.add(free);
            return result;
        }
        if (busy.end.isBefore(free.start) || busy.end.equals(free.start)) {
            result.add(free);
            return result;
        }
        
        // Busy completely covers free
        if (busy.start.isBefore(free.start) && busy.end.isAfter(free.end)) {
            return result;  // Empty, nothing free
        }
        
        // Busy at start
        if (busy.start.isBefore(free.start) || busy.start.equals(free.start)) {
            if (busy.end.isBefore(free.end)) {
                result.add(new TimeInterval(busy.end, free.end));
            }
            return result;
        }
        
        // Busy at end
        if (busy.end.isAfter(free.end) || busy.end.equals(free.end)) {
            if (busy.start.isBefore(free.end)) {
                result.add(new TimeInterval(free.start, busy.start));
            }
            return result;
        }
        
        // Busy in middle
        result.add(new TimeInterval(free.start, busy.start));
        result.add(new TimeInterval(busy.end, free.end));
        
        return result;
    }

    /**
     * Simple interval representation.
     */
    private static class TimeInterval {
        LocalTime start;
        LocalTime end;

        TimeInterval(LocalTime start, LocalTime end) {
            this.start = start;
            this.end = end;
        }
    }
}

