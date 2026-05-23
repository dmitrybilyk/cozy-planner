package com.cozy.notifications.dto;

import java.util.Map;

public class SendMessageRequest {
    private String chatId;
    private String text;
    private String parseMode;
    private Map<String, Object> replyMarkup;

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getParseMode() { return parseMode; }
    public void setParseMode(String parseMode) { this.parseMode = parseMode; }
    public Map<String, Object> getReplyMarkup() { return replyMarkup; }
    public void setReplyMarkup(Map<String, Object> replyMarkup) { this.replyMarkup = replyMarkup; }
}
