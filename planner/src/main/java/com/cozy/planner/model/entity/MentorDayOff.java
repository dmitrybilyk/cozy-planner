package com.cozy.planner.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDate;

@Table("mentor_day_offs")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class MentorDayOff {
    @Id
    private Long id;

    @Column("mentor_id")
    private Long mentorId;

    private LocalDate date;
}
