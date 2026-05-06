package com.cozy.planner.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalTime;

// Workout.java
@Table("workouts")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Workout {
    @Id
    private Long id;
    private String title;
    private String description;
    private LocalDate workoutDate;
    private LocalTime workoutTime;
    private Integer durationMinutes;
    private Long athleteId;
    private Long coachId;
}