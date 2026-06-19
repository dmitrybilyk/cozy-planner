package com.linkease

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class BookedSlot(
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
)

data class TrainerData(
    val trainerId: String,
    val workHoursStart: Int = 7,
    val workHoursEnd: Int = 22,
    val availability: List<AvailabilitySlot> = emptyList(),
    val sessionSlots: List<BookedSlot> = emptyList(),
    val locations: List<Location> = emptyList(),
)

data class ClientSession(
    val id: String,
    val trainerId: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val notes: String? = null,
    val clientConfirmed: Boolean = false,
)

data class BookingRequest(
    val id: String = "",
    val trainerId: String,
    val clientFirebaseId: String,
    val clientName: String? = null,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val clientNote: String? = null,
    val status: String = "pending",
)

data class ClientAvailabilitySlot(
    val id: String,
    val clientFirebaseId: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
)

data class ChatMessage(
    val id: String = "",
    val senderId: String,
    val text: String,
    val timestamp: Long = 0L,
)
