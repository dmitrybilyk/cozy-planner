package com.cozy.planner.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("trainees")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Athlete {
    @Id
    private Long id;
    private String name;
    private String description;

    @Column("mentor_id")
    private Long coachId;

    @Column("invite_token")
    private String inviteToken;

    @Column("telegram_chat_id")
    private String telegramChatId;

    @Column("telegram_username")
    private String telegramUsername;

    @Column("telegram_connected_at")
    private LocalDateTime telegramConnectedAt;

    @Column("photo_base64")
    private String photoBase64;

    @Column("weekend_reminder_enabled")
    @Builder.Default
    private Boolean weekendReminderEnabled = false;

    public boolean hasTelegram() {
        return telegramChatId != null && !telegramChatId.isBlank();
    }

    public boolean isWeekendReminderEnabled() {
        return Boolean.TRUE.equals(weekendReminderEnabled);
    }
}
