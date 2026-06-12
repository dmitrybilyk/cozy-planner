package com.cozy.planner.service.availability;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Service for merging consecutive availability intervals.
 * Merges intervals that are back-to-back and share the same location (or both have no location).
 */
public class AvailabilityMergeService {

    /**
     * Merges consecutive intervals for a coach.
     * Intervals with the same location (or both null) that are consecutive will be merged.
     */
    public static List<CoachSlot> mergeCoachIntervals(List<CoachSlot> slots) {
        if (slots.size() <= 1) {
            return new ArrayList<>(slots);
        }

        // Group by date
        Map<LocalDate, List<CoachSlot>> byDate = new TreeMap<>();
        for (CoachSlot slot : slots) {
            byDate.computeIfAbsent(slot.date(), k -> new ArrayList<>()).add(slot);
        }

        List<CoachSlot> result = new ArrayList<>();
        for (List<CoachSlot> dateSlots : byDate.values()) {
            result.addAll(mergeIntervalsForDate(dateSlots));
        }
        return result;
    }

    /**
     * Merges consecutive intervals for a trainee.
     * All consecutive intervals will be merged since trainees don't have locations.
     */
    public static List<TraineeSlot> mergeTraineeIntervals(List<TraineeSlot> slots) {
        if (slots.size() <= 1) {
            return new ArrayList<>(slots);
        }

        // Group by date
        Map<LocalDate, List<TraineeSlot>> byDate = new TreeMap<>();
        for (TraineeSlot slot : slots) {
            byDate.computeIfAbsent(slot.date(), k -> new ArrayList<>()).add(slot);
        }

        List<TraineeSlot> result = new ArrayList<>();
        for (List<TraineeSlot> dateSlots : byDate.values()) {
            result.addAll(mergeTraineeIntervalsForDate(dateSlots));
        }
        return result;
    }

    /**
     * Merges consecutive intervals for a given date (coach slots).
     * Groups by location, sorts by start time, and merges consecutive intervals.
     */
    private static List<CoachSlot> mergeIntervalsForDate(List<CoachSlot> slots) {
        // Group by location (null location is also a group)
        Map<Long, List<CoachSlot>> byLocation = new LinkedHashMap<>();
        for (CoachSlot slot : slots) {
            Long locId = slot.locationId();
            byLocation.computeIfAbsent(locId, k -> new ArrayList<>()).add(slot);
        }

        List<CoachSlot> mergedSlots = new ArrayList<>();
        for (List<CoachSlot> locSlots : byLocation.values()) {
            // Sort by start time
            locSlots.sort(Comparator.comparing(CoachSlot::startTime));

            // Merge consecutive intervals
            mergedSlots.addAll(mergeConsecutiveIntervals(locSlots));
        }

        return mergedSlots;
    }

    /**
     * Merges consecutive intervals for a given date (trainee slots).
     */
    private static List<TraineeSlot> mergeTraineeIntervalsForDate(List<TraineeSlot> slots) {
        // Sort by start time
        slots.sort(Comparator.comparing(TraineeSlot::startTime));

        // Merge consecutive intervals
        return mergeTraineeConsecutiveIntervals(slots);
    }

    /**
     * Merges consecutive intervals that share the same characteristics.
     * Returns a new list with merged intervals.
     */
    private static List<CoachSlot> mergeConsecutiveIntervals(List<CoachSlot> slots) {
        if (slots.isEmpty()) {
            return new ArrayList<>();
        }

        List<CoachSlot> result = new ArrayList<>();
        CoachSlot current = slots.getFirst();

        for (int i = 1; i < slots.size(); i++) {
            CoachSlot next = slots.get(i);

            // If the current interval ends exactly when the next one starts,
            // merge them by extending end time
            if (current.endTime().equals(next.startTime())) {
                current = new CoachSlot(current.date(), current.startTime(), next.endTime(), current.locationId());
            } else {
                result.add(current);
                current = next;
            }
        }
        result.add(current);

        return result;
    }

    /**
     * Merges consecutive trainee intervals.
     */
    private static List<TraineeSlot> mergeTraineeConsecutiveIntervals(List<TraineeSlot> slots) {
        if (slots.isEmpty()) {
            return new ArrayList<>();
        }

        List<TraineeSlot> result = new ArrayList<>();
        TraineeSlot current = slots.getFirst();

        for (int i = 1; i < slots.size(); i++) {
            TraineeSlot next = slots.get(i);

            // If the current interval ends exactly when the next one starts, merge them
            if (current.endTime().equals(next.startTime())) {
                current = new TraineeSlot(current.date(), current.startTime(), next.endTime());
            } else {
                result.add(current);
                current = next;
            }
        }
        result.add(current);

        return result;
    }

    /**
     * Immutable record for coach availability slots.
     */
    public record CoachSlot(LocalDate date, LocalTime startTime, LocalTime endTime, Long locationId) {
    }

    /**
     * Immutable record for trainee availability slots.
     */
    public record TraineeSlot(LocalDate date, LocalTime startTime, LocalTime endTime) {
    }
}

