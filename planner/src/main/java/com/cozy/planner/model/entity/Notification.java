package com.cozy.planner.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("notifications")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Notification {
    @Id
    private Long id;

    @Column("trainee_id")
    private Long traineeId;

    @Column("mentor_id")
    private Long mentorId;

    private String title;
    private String message;
    private String type;

    @Column("session_id")
    private Long sessionId;

    @Column("is_read")
    @Builder.Default
    private Boolean isRead = false;

    @Column("created_at")
    private LocalDateTime createdAt;
}
