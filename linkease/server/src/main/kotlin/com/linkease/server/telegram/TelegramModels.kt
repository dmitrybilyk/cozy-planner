package com.linkease.server.telegram

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramUpdate(val message: TelegramMessage?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramMessage(val chat: TelegramChat, val text: String?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramChat(val id: Long)
