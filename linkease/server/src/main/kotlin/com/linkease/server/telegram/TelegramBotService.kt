package com.linkease.server.telegram

import com.linkease.server.config.TelegramProperties
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.util.concurrent.ConcurrentHashMap

private const val CHAT_ID_SETTING_KEY = "telegram_chat_id"

@Service
class TelegramBotService(
    private val properties: TelegramProperties,
    private val jdbc: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(TelegramBotService::class.java)
    private val restClient = RestClient.create()

    // Linking codes are short-lived and only ever held by one mentor at a time —
    // no need to persist them, a restart just means generating a fresh code.
    private val pendingCodes = ConcurrentHashMap<String, Long>()

    fun linkedChatId(): Long? =
        jdbc.query("select value from app_settings where key = ?", { rs, _ -> rs.getString("value") }, CHAT_ID_SETTING_KEY)
            .firstOrNull()?.toLongOrNull()

    fun handleIncomingMessage(chatId: Long, text: String?) {
        val code = (100000..999999).random().toString()
        pendingCodes[code] = chatId
        sendMessage(chatId, "Код підключення: $code\n\nВведіть його в Налаштуваннях LinkEase, щоб увімкнути нагадування сюди.")
    }

    fun confirmLink(code: String): Boolean {
        val chatId = pendingCodes.remove(code) ?: return false
        jdbc.update(
            "insert into app_settings (key, value) values (?, ?) on conflict (key) do update set value = excluded.value",
            CHAT_ID_SETTING_KEY, chatId.toString(),
        )
        sendMessage(chatId, "✅ Підключено! Тепер нагадування про заняття приходитимуть сюди.")
        return true
    }

    fun sendMessage(chatId: Long, text: String) {
        if (!properties.enabled || properties.botToken.isBlank()) return
        try {
            restClient.post()
                .uri("https://api.telegram.org/bot${properties.botToken}/sendMessage")
                .body(mapOf("chat_id" to chatId, "text" to text))
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            log.warn("Failed to send Telegram message to {}: {}", chatId, e.message)
        }
    }
}
