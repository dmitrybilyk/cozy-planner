package com.cozy.planner.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Table("meetings")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Workout {
    @Id
    private Long id;
    private String title;
    private String description;

    @Column("meeting_date")
    private LocalDate workoutDate;

    @Column("start_time")
    private LocalTime startTime;

    @Column("end_time")
    private LocalTime endTime;

    @Column("mentor_id")
    private Long coachId;

    @Column("place_id")
    private Long locationId;

    @Transient
    private List<Long> athleteIds;
}
