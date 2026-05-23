package com.cozy.notifications.service;

import java.util.Map;

public interface NotificationSender {
    boolean sendMessage(String chatId, String text, String parseMode, Map<String, Object> replyMarkup);
    boolean sendMessageToMentor(String chatId, String text, String parseMode, Map<String, Object> replyMarkup);
}
