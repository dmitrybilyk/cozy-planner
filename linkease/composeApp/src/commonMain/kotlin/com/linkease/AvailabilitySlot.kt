package com.linkease

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

const val CALENDAR_HOURS_START = 7
const val CALENDAR_HOURS_END   = 22

data class AvailabilitySlot(
    val id: Long = 0,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val locationId: Long? = null
)

data class FreeSlot(val startTime: LocalTime, val endTime: LocalTime, val locationId: Long?)

fun calculateFreeSlots(
    sessions: List<Session>,
    availSlots: List<AvailabilitySlot>,
    date: LocalDate,
    workHoursStart: Int = CALENDAR_HOURS_START,
    workHoursEnd: Int = CALENDAR_HOURS_END,
): List<FreeSlot> {
    val dayAvail = availSlots.filter { it.date == date }

    val effectiveSlots = if (dayAvail.isEmpty()) {
        listOf(AvailabilitySlot(
            date = date,
            startTime = LocalTime(workHoursStart, 0),
            endTime   = LocalTime(workHoursEnd, 0),
            locationId = null
        ))
    } else dayAvail

    val workStartMin = workHoursStart * 60
    val workEndMin   = workHoursEnd * 60

    val raw = effectiveSlots.flatMap { avail ->
        val relevant = sessions
            .filter { it.startTime.toMinutes() < avail.endTime.toMinutes() && it.endTime.toMinutes() > avail.startTime.toMinutes() }
            .sortedBy { it.startTime.toMinutes() }
        val free = mutableListOf<FreeSlot>()
        var cursor = avail.startTime.toMinutes()
        relevant.forEach { s ->
            val sStart = s.startTime.toMinutes().coerceAtLeast(avail.startTime.toMinutes())
            val sEnd   = s.endTime.toMinutes().coerceAtMost(avail.endTime.toMinutes())
            if (cursor < sStart) free.add(FreeSlot(minutesToLocalTime(cursor), minutesToLocalTime(sStart), avail.locationId))
            cursor = maxOf(cursor, sEnd)
        }
        if (cursor < avail.endTime.toMinutes()) free.add(FreeSlot(minutesToLocalTime(cursor), avail.endTime, avail.locationId))
        free
    }

    return raw
        .filter { it.endTime.toMinutes() > workStartMin && it.startTime.toMinutes() < workEndMin }
        .map { slot ->
            FreeSlot(
                startTime = if (slot.startTime.toMinutes() < workStartMin) minutesToLocalTime(workStartMin) else slot.startTime,
                endTime   = if (slot.endTime.toMinutes()   > workEndMin)   minutesToLocalTime(workEndMin)   else slot.endTime,
                locationId = slot.locationId
            )
        }
        .filter { it.endTime.toMinutes() > it.startTime.toMinutes() }
}
