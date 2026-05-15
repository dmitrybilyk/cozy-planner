package com.cozy.planner.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDate;
import java.time.LocalTime;

@Table("mentor_availability")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class MentorAvailability {
    @Id
    private Long id;

    @Column("mentor_id")
    private Long mentorId;

    private LocalDate date;

    private LocalTime startTime;

    private LocalTime endTime;

    @Column("place_id")
    private Long locationId;
}
