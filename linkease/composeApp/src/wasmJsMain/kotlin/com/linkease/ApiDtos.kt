package com.linkease

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class ClientApiDto(
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val colorHex: String = "#2196F3",
    val hourlyRate: Double = 0.0,
    val packageTotal: Int = 0,
    val packageUsed: Int = 0,
    val birthDate: String? = null,
    val firebaseClientId: String? = null,
)

fun Client.toApiDto() = ClientApiDto(id, name, phone, email, colorHex, hourlyRate, packageTotal, packageUsed, birthDate, firebaseClientId)
fun ClientApiDto.toDomain() = Client(id, name, phone, email, colorHex, hourlyRate, packageTotal, packageUsed, birthDate, firebaseClientId)

@Serializable
data class LocationApiDto(
    val id: Long = 0,
    val name: String,
    val address: String = "",
    val colorHex: String = "#4CAF50",
    val mapsLink: String? = null,
)

fun Location.toApiDto() = LocationApiDto(id, name, address, colorHex, mapsLink)
fun LocationApiDto.toDomain() = Location(id, name, address, colorHex, mapsLink)

@Serializable
data class SessionApiDto(
    val id: Long = 0,
    val date: String,
    val startTime: String,
    val endTime: String,
    val clientIds: List<Long> = emptyList(),
    val locationId: Long? = null,
    val notes: String = "",
    val confirmed: Boolean = false,
    val paid: Boolean = false,
)

fun Session.toApiDto() = SessionApiDto(
    id, date.toString(), startTime.toStorageString(), endTime.toStorageString(), clientIds, locationId, notes, confirmed, paid,
)

fun SessionApiDto.toDomain() = Session(
    id, LocalDate.parse(date), parseStorageTime(startTime), parseStorageTime(endTime), clientIds, locationId, notes, confirmed, paid,
)

@Serializable
data class AvailabilitySlotApiDto(
    val id: Long = 0,
    val date: String,
    val startTime: String,
    val endTime: String,
    val locationId: Long? = null,
)

fun AvailabilitySlot.toApiDto() = AvailabilitySlotApiDto(id, date.toString(), startTime.toStorageString(), endTime.toStorageString(), locationId)
fun AvailabilitySlotApiDto.toDomain() = AvailabilitySlot(id, LocalDate.parse(date), parseStorageTime(startTime), parseStorageTime(endTime), locationId)

@Serializable
data class TelegramStatusDto(val linked: Boolean)

@Serializable
data class TelegramLinkRequestDto(val code: String)
