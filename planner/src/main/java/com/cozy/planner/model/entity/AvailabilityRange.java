package com.cozy.planner.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Table("availability_ranges")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class AvailabilityRange {
    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("user_type")
    private String userType;

    private LocalDate date;

    @Column("start_time")
    private OffsetDateTime startTime;

    @Column("end_time")
    private OffsetDateTime endTime;

    @Column("location_id")
    private Long locationId;

    @Column("free_all_day")
    private Boolean freeAllDay;
}
