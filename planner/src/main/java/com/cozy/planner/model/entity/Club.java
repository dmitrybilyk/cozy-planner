package com.cozy.planner.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("clubs")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Club {
    @Id
    private Long id;
    private String name;
    private String description;
}