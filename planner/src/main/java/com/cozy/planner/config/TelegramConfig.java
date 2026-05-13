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
    
    public boolean isMentorBotEnabled() {
        return mentorBotToken != null && !mentorBotToken.isBlank();
    }
}
