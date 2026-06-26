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

// Splits this slot around every session that overlaps it, returning the leftover pieces.
private fun FreeSlot.minus(sessions: List<Session>): List<FreeSlot> {
    var pieces = listOf(this)
    for (s in sessions) {
        val sStart = s.startTime.toMinutes()
        val sEnd = s.endTime.toMinutes()
        if (sEnd <= sStart) continue
        pieces = pieces.flatMap { p ->
            val pStart = p.startTime.toMinutes()
            val pEnd = p.endTime.toMinutes()
            if (sEnd <= pStart || sStart >= pEnd) listOf(p)
            else buildList {
                if (sStart > pStart) add(FreeSlot(p.startTime, minutesToLocalTime(sStart), p.locationId))
                if (sEnd < pEnd) add(FreeSlot(minutesToLocalTime(sEnd), p.endTime, p.locationId))
            }
        }
    }
    return pieces
}

fun calculateFreeSlots(
    sessions: List<Session>,
    availSlots: List<AvailabilitySlot>,
    date: LocalDate,
    workHoursStart: Int = CALENDAR_HOURS_START,
    workHoursEnd: Int = CALENDAR_HOURS_END,
): List<FreeSlot> {
    val dayAvail = availSlots.filter { it.date == date }

    if (dayAvail.isEmpty()) return emptyList()
    val effectiveSlots = dayAvail

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

    // Belt-and-suspenders: carve out any session overlap that survived the per-window
    // sweep above (e.g. a session straddling two overlapping availability windows).
    // Guarantees a session's time range can never be reported as free.
    val sessionFree = raw.flatMap { it.minus(sessions) }

    return sessionFree
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
