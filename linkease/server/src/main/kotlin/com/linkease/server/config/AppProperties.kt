package com.linkease.server.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val baseUrl: String,
    /** Only this Google account may log in — single-tenant app, no multi-user model. */
    val ownerEmail: String,
    val notificationService: String = "direct",
    val notificationTopic: String = "linkease-notifications",
)

@ConfigurationProperties(prefix = "telegram")
data class TelegramProperties(
    val enabled: Boolean,
    val botToken: String,
    val botUsername: String,
    val webhookUrl: String,
    val webhookSecret: String,
    val reminderMinutesBefore: Long,
    val developerBotToken: String = "",
    val developerChatId: Long = 0,
) {
    fun isDeveloperBotEnabled() = developerBotToken.isNotBlank() && developerChatId != 0L
}
