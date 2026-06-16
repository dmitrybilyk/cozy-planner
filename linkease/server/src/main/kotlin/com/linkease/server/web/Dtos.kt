package com.linkease.server.web

import com.linkease.AvailabilitySlot
import com.linkease.Client
import com.linkease.Location
import com.linkease.Session
import com.linkease.parseStorageTime
import com.linkease.toStorageString
import kotlinx.datetime.LocalDate

data class ClientDto(
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val colorHex: String = "#2196F3",
    val hourlyRate: Double = 0.0,
)

fun Client.toDto() = ClientDto(id, name, phone, email, colorHex, hourlyRate)
fun ClientDto.toDomain() = Client(id, name, phone, email, colorHex, hourlyRate)

data class LocationDto(
    val id: Long = 0,
    val name: String,
    val address: String = "",
    val colorHex: String = "#4CAF50",
    val mapsLink: String? = null,
)

fun Location.toDto() = LocationDto(id, name, address, colorHex, mapsLink)
fun LocationDto.toDomain() = Location(id, name, address, colorHex, mapsLink)

data class SessionDto(
    val id: Long = 0,
    val date: String,
    val startTime: String,
    val endTime: String,
    val clientIds: List<Long> = emptyList(),
    val locationId: Long? = null,
    val notes: String = "",
)

fun Session.toDto() = SessionDto(
    id, date.toString(), startTime.toStorageString(), endTime.toStorageString(), clientIds, locationId, notes,
)

fun SessionDto.toDomain() = Session(
    id, LocalDate.parse(date), parseStorageTime(startTime), parseStorageTime(endTime), clientIds, locationId, notes,
)

data class AvailabilitySlotDto(
    val id: Long = 0,
    val date: String,
    val startTime: String,
    val endTime: String,
    val locationId: Long? = null,
)

fun AvailabilitySlot.toDto() = AvailabilitySlotDto(id, date.toString(), startTime.toStorageString(), endTime.toStorageString(), locationId)
fun AvailabilitySlotDto.toDomain() = AvailabilitySlot(id, LocalDate.parse(date), parseStorageTime(startTime), parseStorageTime(endTime), locationId)
