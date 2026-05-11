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
public class Coach {
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

    public boolean hasTelegram() {
        return telegramChatId != null && !telegramChatId.isBlank();
    }
}
