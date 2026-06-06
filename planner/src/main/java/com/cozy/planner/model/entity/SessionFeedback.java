package com.cozy.planner.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("session_feedback")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class SessionFeedback {
    @Id
    private Long id;

    @Column("session_id")
    private Long sessionId;

    @Column("from_mentor_id")
    private Long fromMentorId;

    @Column("from_trainee_id")
    private Long fromTraineeId;

    @Column("to_mentor_id")
    private Long toMentorId;

    @Column("to_trainee_id")
    private Long toTraineeId;

    @Column("session_title")
    private String sessionTitle;

    private String text;
    private String tags;
    private Short rating;

    @Column("is_read")
    @Builder.Default
    private Boolean isRead = false;

    @Column("created_at")
    private LocalDateTime createdAt;
}
