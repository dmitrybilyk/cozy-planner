package com.cozy.planner.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

// Coach.java
@Table("coaches")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Coach {
    @Id
    private Long id;
    private String name;
    private String specialization;
}