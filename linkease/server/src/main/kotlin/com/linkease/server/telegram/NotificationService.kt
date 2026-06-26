package com.linkease.server.telegram

interface NotificationService {
    fun sendReminder(chatId: Long, text: String)
}
