package com.cozy.planner.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

// Athlete.java
@Table("athletes")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Athlete {
    @Id
    private Long id;
    private String name;
    private Long coachId;
}