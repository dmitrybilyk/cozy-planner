package com.cozy.planner.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("places")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Location {
    @Id
    private Long id;
    private String name;
    private String description;
    private String color;

    @Column("google_maps_url")
    private String googleMapsUrl;

    @Column("mentor_id")
    private Long mentorId;
}
