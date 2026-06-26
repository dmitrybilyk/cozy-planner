package com.linkease.server.telegram

import com.linkease.SessionRepository
import com.linkease.server.config.TelegramProperties
import com.linkease.toMinutes
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ReminderScheduler(
    private val sessionRepository: SessionRepository,
    private val reminderRepository: ReminderRepository,
    private val botService: TelegramBotService,
    private val notificationService: NotificationService,
    private val properties: TelegramProperties,
) {
    @Scheduled(cron = "0 */5 * * * *")
    fun sendDueReminders() {
        if (!properties.enabled) return
        val chatId = botService.linkedChatId() ?: return

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val dueIds = reminderRepository.unremindedSessionIds().toSet()
        if (dueIds.isEmpty()) return

        sessionRepository.getAll()
            .filter { it.id in dueIds && it.date == now.date }
            .filter { session ->
                val minutesUntil = (session.startTime.toMinutes() - now.time.toMinutes()).toLong()
                minutesUntil in 0..properties.reminderMinutesBefore
            }
            .forEach { session ->
                notificationService.sendReminder(chatId, "Нагадування: заняття о ${session.startTime} ${session.notes}".trim())
                reminderRepository.markReminded(session.id)
            }
    }
}
