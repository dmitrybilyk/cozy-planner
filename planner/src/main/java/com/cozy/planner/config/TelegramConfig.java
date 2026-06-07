package com.cozy.planner.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
@Data
public class TelegramConfig {
    private String botToken;
    private String botUsername;
    private String webhookUrl;
    private boolean enabled = false;
    
    // Separate mentor bot (optional)
    private String mentorBotToken;
    private String mentorBotUsername;
    
    // Developer feedback bot — receives "write to developer" messages and new-user alerts
    private String developerBotToken;
    private String developerChatId;

    public boolean isMentorBotEnabled() {
        return mentorBotToken != null && !mentorBotToken.isBlank();
    }

    public boolean isDeveloperBotEnabled() {
        return developerBotToken != null && !developerBotToken.isBlank()
            && developerChatId != null && !developerChatId.isBlank();
    }
}
