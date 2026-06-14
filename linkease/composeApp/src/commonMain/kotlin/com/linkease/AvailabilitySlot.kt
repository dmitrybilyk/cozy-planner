package com.linkease

import kotlinx.datetime.LocalTime

const val CALENDAR_HOURS_START = 7
const val CALENDAR_HOURS_END   = 22

data class AvailabilitySlot(
    val id: Long = 0,
    val dayOfWeek: Int,  // 1=Mon … 7=Sun (ISO)
    val startTime: LocalTime,
    val endTime: LocalTime,
    val locationId: Long? = null
)

data class FreeSlot(val startTime: LocalTime, val endTime: LocalTime, val locationId: Long?)

fun calculateFreeSlots(
    sessions: List<Session>,
    availSlots: List<AvailabilitySlot>,
    dayOfWeek: Int
): List<FreeSlot> {
    val dayAvail = availSlots.filter { it.dayOfWeek == dayOfWeek }

    // If no explicit availability for this day → assume free all day
    val effectiveSlots = if (dayAvail.isEmpty()) {
        listOf(AvailabilitySlot(
            dayOfWeek = dayOfWeek,
            startTime = LocalTime(CALENDAR_HOURS_START, 0),
            endTime   = LocalTime(CALENDAR_HOURS_END, 0),
            locationId = null
        ))
    } else dayAvail

    return effectiveSlots.flatMap { avail ->
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
}
