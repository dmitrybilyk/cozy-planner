package com.cozy.planner.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("audit_events")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class AuditEvent {
    @Id
    private Long id;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    private String eventType;
    private String actorEmail;
    private Long mentorId;
    private String description;
}
