package com.cozy.planner.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDate;
import java.time.LocalTime;

@Table("athlete_availability")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class AthleteAvailability {
    @Id
    private Long id;
    private Long athleteId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
}
