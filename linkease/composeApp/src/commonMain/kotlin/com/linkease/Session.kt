package com.linkease

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class Session(
    val id: Long = 0,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val clientIds: List<Long> = emptyList(),
    val locationId: Long? = null,
    val notes: String = ""
)

fun LocalTime.toMinutes() = hour * 60 + minute
fun minutesToLocalTime(minutes: Int) = LocalTime(minutes / 60, minutes % 60)
fun LocalTime.toStorageString() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
fun formatDuration(mins: Int): String = when {
    mins < 60        -> "$mins хв"
    mins % 60 == 0   -> "${mins / 60} год"
    else             -> "${mins / 60} год ${mins % 60} хв"
}
fun parseStorageTime(s: String): LocalTime {
    val (h, m) = s.split(":").map { it.toInt() }
    return LocalTime(h, m)
}
