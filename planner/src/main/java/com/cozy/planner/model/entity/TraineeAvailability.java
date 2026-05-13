package com.cozy.planner.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDate;
import java.time.LocalTime;

@Table("trainee_availability")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class TraineeAvailability {
    @Id
    private Long id;

    @Column("trainee_id")
    private Long traineeId;

    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
}
