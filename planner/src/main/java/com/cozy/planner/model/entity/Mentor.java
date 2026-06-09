package com.cozy.planner.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("mentors")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Mentor {
    @Id
    private Long id;
    private String name;
    private String specialization;

    @Column("club_id")
    private Long clubId;

    @Column("telegram_chat_id")
    private String telegramChatId;

    @Column("telegram_username")
    private String telegramUsername;

    @Column("telegram_connected_at")
    private LocalDateTime telegramConnectedAt;

    @Column("telegram_token")
    private String telegramToken;

    @Builder.Default
    private String timezone = "Europe/Kiev";

    @Column("session_reminder_enabled")
    @Builder.Default
    private Boolean sessionReminderEnabled = false;

    @Column("session_reminder_minutes")
    @Builder.Default
    private Integer sessionReminderMinutes = 60;

    @Builder.Default
    private String profile = "sport";

    @Builder.Default
    private String theme = "default";

    @Column("intro_seen")
    @Builder.Default
    private Boolean introSeen = false;

    @Column("share_token")
    private String shareToken;

    @Column("photo_url")
    private String photoUrl;

    @Column("work_start")
    private String workStart;

    @Column("work_end")
    private String workEnd;

    @Column("avail_step")
    @Builder.Default
    private Integer availStep = 30;

    @Column("share_availability")
    @Builder.Default
    private Boolean shareAvailability = false;

    @Column("multi_location")
    @Builder.Default
    private Boolean multiLocation = false;

    @Column("session_confirmations")
    @Builder.Default
    private Boolean sessionConfirmations = false;

    @Column("telegram_integration")
    @Builder.Default
    private Boolean telegramIntegration = false;

    @Column("trainee_comm")
    @Builder.Default
    private Boolean traineeComm = false;

    public boolean hasTelegram() {
        return telegramChatId != null && !telegramChatId.isBlank();
    }

    public boolean isSessionReminderEnabled() {
        return Boolean.TRUE.equals(sessionReminderEnabled);
    }
}
