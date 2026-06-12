package com.cozy.planner.service;

import com.cozy.planner.service.availability.AvailabilityMergeService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Tests for the AvailabilityMergeService.
 * Verifies that consecutive intervals are properly merged.
 */
public class AvailabilityMergeServiceTest {

    @Test
    public void testMergeCoachIntervalsConsecutive() {
        // Arrange
        LocalDate date = LocalDate.of(2026, 6, 2);
        Long locationId = 1L;
        
        List<AvailabilityMergeService.CoachSlot> inputs = List.of(
            new AvailabilityMergeService.CoachSlot(date, LocalTime.of(9, 0), LocalTime.of(10, 0), locationId),
            new AvailabilityMergeService.CoachSlot(date, LocalTime.of(10, 0), LocalTime.of(11, 0), locationId),
            new AvailabilityMergeService.CoachSlot(date, LocalTime.of(11, 0), LocalTime.of(12, 0), locationId)
        );
        
        // Act
        List<AvailabilityMergeService.CoachSlot> result = AvailabilityMergeService.mergeCoachIntervals(inputs);
        
        // Assert
        assertEquals(1, result.size());
        assertEquals(LocalTime.of(9, 0), result.get(0).startTime());
        assertEquals(LocalTime.of(12, 0), result.get(0).endTime());
        assertEquals(locationId, result.get(0).locationId());
    }

    @Test
    public void testMergeCoachIntervalsWithGap() {
        // Arrange
        LocalDate date = LocalDate.of(2026, 6, 2);
        Long locationId = 1L;
        
        List<AvailabilityMergeService.CoachSlot> inputs = List.of(
            new AvailabilityMergeService.CoachSlot(date, LocalTime.of(9, 0), LocalTime.of(10, 0), locationId),
            new AvailabilityMergeService.CoachSlot(date, LocalTime.of(10, 30), LocalTime.of(11, 0), locationId)
        );
        
        // Act
        List<AvailabilityMergeService.CoachSlot> result = AvailabilityMergeService.mergeCoachIntervals(inputs);
        
        // Assert
        assertEquals(2, result.size(), "Intervals with gaps should not be merged");
    }

    @Test
    public void testMergeCoachIntervalsDifferentLocations() {
        // Arrange
        LocalDate date = LocalDate.of(2026, 6, 2);
        
        List<AvailabilityMergeService.CoachSlot> inputs = List.of(
            new AvailabilityMergeService.CoachSlot(date, LocalTime.of(9, 0), LocalTime.of(10, 0), 1L),
            new AvailabilityMergeService.CoachSlot(date, LocalTime.of(10, 0), LocalTime.of(11, 0), 2L)
        );
        
        // Act
        List<AvailabilityMergeService.CoachSlot> result = AvailabilityMergeService.mergeCoachIntervals(inputs);
        
        // Assert
        assertEquals(2, result.size(), "Intervals with different locations should not be merged");
    }

    @Test
    public void testMergeCoachIntervalsNullLocation() {
        // Arrange
        LocalDate date = LocalDate.of(2026, 6, 2);
        
        List<AvailabilityMergeService.CoachSlot> inputs = List.of(
            new AvailabilityMergeService.CoachSlot(date, LocalTime.of(9, 0), LocalTime.of(10, 0), null),
            new AvailabilityMergeService.CoachSlot(date, LocalTime.of(10, 0), LocalTime.of(11, 0), null)
        );
        
        // Act
        List<AvailabilityMergeService.CoachSlot> result = AvailabilityMergeService.mergeCoachIntervals(inputs);
        
        // Assert
        assertEquals(1, result.size(), "Consecutive intervals with null locations should be merged");
        assertEquals(LocalTime.of(9, 0), result.get(0).startTime());
        assertEquals(LocalTime.of(11, 0), result.get(0).endTime());
    }

    @Test
    public void testMergeTraineeIntervalsConsecutive() {
        // Arrange
        LocalDate date = LocalDate.of(2026, 6, 2);
        
        List<AvailabilityMergeService.TraineeSlot> inputs = List.of(
            new AvailabilityMergeService.TraineeSlot(date, LocalTime.of(9, 0), LocalTime.of(10, 0)),
            new AvailabilityMergeService.TraineeSlot(date, LocalTime.of(10, 0), LocalTime.of(11, 0)),
            new AvailabilityMergeService.TraineeSlot(date, LocalTime.of(11, 0), LocalTime.of(12, 0))
        );
        
        // Act
        List<AvailabilityMergeService.TraineeSlot> result = AvailabilityMergeService.mergeTraineeIntervals(inputs);
        
        // Assert
        assertEquals(1, result.size());
        assertEquals(LocalTime.of(9, 0), result.get(0).startTime());
        assertEquals(LocalTime.of(12, 0), result.get(0).endTime());
    }

    @Test
    public void testMergeTraineeIntervalsWithGap() {
        // Arrange
        LocalDate date = LocalDate.of(2026, 6, 2);
        
        List<AvailabilityMergeService.TraineeSlot> inputs = List.of(
            new AvailabilityMergeService.TraineeSlot(date, LocalTime.of(9, 0), LocalTime.of(10, 0)),
            new AvailabilityMergeService.TraineeSlot(date, LocalTime.of(10, 30), LocalTime.of(11, 0))
        );
        
        // Act
        List<AvailabilityMergeService.TraineeSlot> result = AvailabilityMergeService.mergeTraineeIntervals(inputs);
        
        // Assert
        assertEquals(2, result.size(), "Intervals with gaps should not be merged");
    }

    @Test
    public void testMergeMultipleDates() {
        // Arrange
        LocalDate date1 = LocalDate.of(2026, 6, 2);
        LocalDate date2 = LocalDate.of(2026, 6, 3);
        Long locationId = 1L;
        
        List<AvailabilityMergeService.CoachSlot> inputs = List.of(
            new AvailabilityMergeService.CoachSlot(date1, LocalTime.of(9, 0), LocalTime.of(10, 0), locationId),
            new AvailabilityMergeService.CoachSlot(date1, LocalTime.of(10, 0), LocalTime.of(11, 0), locationId),
            new AvailabilityMergeService.CoachSlot(date2, LocalTime.of(9, 0), LocalTime.of(10, 0), locationId),
            new AvailabilityMergeService.CoachSlot(date2, LocalTime.of(10, 0), LocalTime.of(11, 0), locationId)
        );
        
        // Act
        List<AvailabilityMergeService.CoachSlot> result = AvailabilityMergeService.mergeCoachIntervals(inputs);
        
        // Assert
        assertEquals(2, result.size(), "Each date should be merged independently");
        assertEquals(date1, result.get(0).date());
        assertEquals(LocalTime.of(9, 0), result.get(0).startTime());
        assertEquals(LocalTime.of(11, 0), result.get(0).endTime());
        assertEquals(date2, result.get(1).date());
        assertEquals(LocalTime.of(9, 0), result.get(1).startTime());
        assertEquals(LocalTime.of(11, 0), result.get(1).endTime());
    }
}

