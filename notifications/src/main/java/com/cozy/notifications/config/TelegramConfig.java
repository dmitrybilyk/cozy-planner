package com.cozy.notifications.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public class TelegramConfig {
    private boolean enabled = false;
    private String botToken;
    private String botUsername;
    private String webhookUrl;
    private String mentorBotToken;
    private String mentorBotUsername;
    private String mentorWebhookUrl;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBotToken() { return botToken; }
    public void setBotToken(String botToken) { this.botToken = botToken; }
    public String getBotUsername() { return botUsername; }
    public void setBotUsername(String botUsername) { this.botUsername = botUsername; }
    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    public String getMentorBotToken() { return mentorBotToken; }
    public void setMentorBotToken(String mentorBotToken) { this.mentorBotToken = mentorBotToken; }
    public String getMentorBotUsername() { return mentorBotUsername; }
    public void setMentorBotUsername(String mentorBotUsername) { this.mentorBotUsername = mentorBotUsername; }
    public String getMentorWebhookUrl() { return mentorWebhookUrl; }
    public void setMentorWebhookUrl(String mentorWebhookUrl) { this.mentorWebhookUrl = mentorWebhookUrl; }
    public boolean isMentorBotEnabled() {
        return mentorBotToken != null && !mentorBotToken.isBlank();
    }
}
