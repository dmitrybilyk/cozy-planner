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
    
    // Separate coach bot (optional)
    private String coachBotToken;
    private String coachBotUsername;
    
    public boolean isCoachBotEnabled() {
        return coachBotToken != null && !coachBotToken.isBlank();
    }
}
