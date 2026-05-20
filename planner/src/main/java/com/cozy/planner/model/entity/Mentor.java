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
    private Boolean sessionReminderEnabled = true;

    @Column("session_reminder_minutes")
    @Builder.Default
    private Integer sessionReminderMinutes = 60;

    @Builder.Default
    private String profile = "sport";

    @Column("share_token")
    private String shareToken;

    @Column("photo_url")
    private String photoUrl;

    @Column("work_start")
    private String workStart;

    @Column("work_end")
    private String workEnd;

    public boolean hasTelegram() {
        return telegramChatId != null && !telegramChatId.isBlank();
    }

    public boolean isSessionReminderEnabled() {
        return Boolean.TRUE.equals(sessionReminderEnabled);
    }
}
