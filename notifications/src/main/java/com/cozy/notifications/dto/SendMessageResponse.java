package com.cozy.notifications.dto;

public class SendMessageResponse {
    private boolean success;
    private String message;

    public SendMessageResponse() {}

    public SendMessageResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public static SendMessageResponse ok() {
        return new SendMessageResponse(true, null);
    }

    public static SendMessageResponse error(String message) {
        return new SendMessageResponse(false, message);
    }
}
