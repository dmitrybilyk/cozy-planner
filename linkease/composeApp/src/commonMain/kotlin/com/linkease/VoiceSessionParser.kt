package com.linkease

import kotlinx.datetime.*

data class ParsedVoiceSession(
    val clientId: Long?,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val locationId: Long?,
    val rawText: String,
    val isAvailability: Boolean = false,
)

fun parseVoiceSession(
    text: String,
    clients: List<Client>,
    locations: List<Location>,
    today: LocalDate,
    defaultDurationMinutes: Int = 60,
): ParsedVoiceSession {
    val lower = text.lowercase().trim()
    val isAvailability = lower.startsWith("доступність") || lower.startsWith("доступнiсть")

    val locationId = matchLocation(lower, locations)
    val date = parseDate(lower, today)
    val startTime = parseTime(lower) ?: LocalTime(10, 0)
    val durationMins = parseDuration(lower) ?: defaultDurationMinutes
    val endTime = minutesToLocalTime((startTime.toMinutes() + durationMins).coerceAtMost(22 * 60))

    // Client matching only for sessions, not availability
    val clientId = if (isAvailability) null else matchClient(lower, clients)

    return ParsedVoiceSession(
        clientId = clientId,
        date = date,
        startTime = startTime,
        endTime = endTime,
        locationId = locationId,
        rawText = text,
        isAvailability = isAvailability,
    )
}

private fun matchClient(lower: String, clients: List<Client>): Long? {
    if (clients.isEmpty()) return null
    // Score each client: count how many words of the name appear in the text
    val words = lower.split(Regex("\\s+"))
    return clients.maxByOrNull { client ->
        val nameWords = client.name.lowercase().split(Regex("\\s+"))
        nameWords.count { nw -> words.any { w -> w.contains(nw) || nw.contains(w) } }
    }?.takeIf { client ->
        val nameWords = client.name.lowercase().split(Regex("\\s+"))
        nameWords.any { nw -> words.any { w -> w.contains(nw) || nw.contains(w) } }
    }?.id
}

private fun matchLocation(lower: String, locations: List<Location>): Long? {
    if (locations.isEmpty()) return null
    val words = lower.split(Regex("\\s+"))
    return locations.firstOrNull { loc ->
        val nameWords = loc.name.lowercase().split(Regex("\\s+"))
        nameWords.any { nw -> nw.length >= 3 && words.any { w -> w.contains(nw) || nw.contains(w) } }
    }?.id
}

private val MONTH_NAMES_UK = mapOf(
    "січня" to 1, "лютого" to 2, "березня" to 3, "квітня" to 4,
    "травня" to 5, "червня" to 6, "липня" to 7, "серпня" to 8,
    "вересня" to 9, "жовтня" to 10, "листопада" to 11, "грудня" to 12,
    // nominative forms
    "січень" to 1, "лютий" to 2, "березень" to 3, "квітень" to 4,
    "травень" to 5, "червень" to 6, "липень" to 7, "серпень" to 8,
    "вересень" to 9, "жовтень" to 10, "листопад" to 11, "грудень" to 12,
)

private fun parseDate(lower: String, today: LocalDate): LocalDate {
    if (lower.contains("сьогодні") || lower.contains("сьогодня")) return today
    if (lower.contains("завтра")) return today.plus(1, DateTimeUnit.DAY)
    if (lower.contains("після завтра") || lower.contains("після завтра")) return today.plus(2, DateTimeUnit.DAY)

    val weekdays = mapOf(
        "понеділок" to DayOfWeek.MONDAY,
        "вівторок" to DayOfWeek.TUESDAY,
        "середу" to DayOfWeek.WEDNESDAY, "середа" to DayOfWeek.WEDNESDAY,
        "четвер" to DayOfWeek.THURSDAY,
        "п'ятницю" to DayOfWeek.FRIDAY, "пятницю" to DayOfWeek.FRIDAY,
        "пятницю" to DayOfWeek.FRIDAY, "п'ятницю" to DayOfWeek.FRIDAY,
        "суботу" to DayOfWeek.SATURDAY, "субота" to DayOfWeek.SATURDAY,
        "неділю" to DayOfWeek.SUNDAY, "неділя" to DayOfWeek.SUNDAY,
    )
    for ((word, dow) in weekdays) {
        if (lower.contains(word)) {
            return nextWeekday(today, dow)
        }
    }

    // "24 червня" — day + Ukrainian month name
    val monthPattern = MONTH_NAMES_UK.keys.joinToString("|")
    val dayMonthRegex = Regex("(\\d{1,2})\\s+($monthPattern)")
    dayMonthRegex.find(lower)?.let { match ->
        val day = match.groupValues[1].toIntOrNull() ?: return@let
        val month = MONTH_NAMES_UK[match.groupValues[2]] ?: return@let
        return try {
            val candidate = LocalDate(today.year, month, day)
            if (candidate < today) LocalDate(today.year + 1, month, day) else candidate
        } catch (_: Exception) { today }
    }

    // Try to parse day number like "п'ятого", "шостого", "15-го", "15 числа"
    val dayOrdMap = mapOf(
        "першого" to 1, "другого" to 2, "третього" to 3, "четвертого" to 4,
        "п'ятого" to 5, "пятого" to 5, "шостого" to 6, "сьомого" to 7, "восьмого" to 8,
        "дев'ятого" to 9, "девятого" to 9, "десятого" to 10, "одинадцятого" to 11,
        "дванадцятого" to 12, "тринадцятого" to 13, "чотирнадцятого" to 14,
        "п'ятнадцятого" to 15, "пятнадцятого" to 15, "шістнадцятого" to 16,
        "сімнадцятого" to 17, "вісімнадцятого" to 18, "дев'ятнадцятого" to 19,
        "двадцятого" to 20, "двадцять першого" to 21, "двадцять другого" to 22,
        "двадцять третього" to 23, "двадцять четвертого" to 24, "двадцять п'ятого" to 25,
        "двадцять шостого" to 26, "двадцять сьомого" to 27, "двадцять восьмого" to 28,
        "двадцять дев'ятого" to 29, "тридцятого" to 30, "тридцять першого" to 31,
    )
    for ((word, day) in dayOrdMap) {
        if (lower.contains(word)) {
            return try {
                val candidate = LocalDate(today.year, today.month, day)
                if (candidate < today) candidate.plus(1, DateTimeUnit.MONTH) else candidate
            } catch (_: Exception) { today }
        }
    }

    // Numeric pattern: "15-го", "15 числа", standalone number
    val numRegex = Regex("\\b(\\d{1,2})[-]?(?:го|числа)?\\b")
    numRegex.find(lower)?.let { match ->
        val day = match.groupValues[1].toIntOrNull()
        if (day != null && day in 1..31) {
            return try {
                val candidate = LocalDate(today.year, today.month, day)
                if (candidate < today) candidate.plus(1, DateTimeUnit.MONTH) else candidate
            } catch (_: Exception) { today }
        }
    }

    return today
}

private fun nextWeekday(from: LocalDate, target: DayOfWeek): LocalDate {
    var date = from.plus(1, DateTimeUnit.DAY)
    repeat(7) {
        if (date.dayOfWeek == target) return date
        date = date.plus(1, DateTimeUnit.DAY)
    }
    return date
}

private fun parseTime(lower: String): LocalTime? {
    // Ukrainian word numbers for hours
    val wordHours = mapOf(
        "першій" to 1, "першу" to 1, "першого" to 1,
        "другій" to 2, "другу" to 2,
        "третій" to 3, "третю" to 3,
        "четвертій" to 4, "четверту" to 4,
        "п'ятій" to 5, "п'яту" to 5, "пятій" to 5, "пяту" to 5,
        "шостій" to 6, "шосту" to 6,
        "сьомій" to 7, "сьому" to 7,
        "восьмій" to 8, "восьму" to 8,
        "дев'ятій" to 9, "дев'яту" to 9, "девятій" to 9, "девяту" to 9,
        "десятій" to 10, "десяту" to 10,
        "одинадцятій" to 11, "одинадцяту" to 11,
        "дванадцятій" to 12, "дванадцяту" to 12,
        "тринадцятій" to 13, "чотирнадцятій" to 14,
        "п'ятнадцятій" to 15, "шістнадцятій" to 16, "сімнадцятій" to 17,
        "вісімнадцятій" to 18, "дев'ятнадцятій" to 19, "двадцятій" to 20,
        "двадцять першій" to 21, "двадцять другій" to 22,
    )

    // "пів на X" — half past (X-1)
    val pivNa = Regex("пів на (\\w+)")
    pivNa.find(lower)?.let { match ->
        val word = match.groupValues[1]
        val hour = wordHours[word]
        if (hour != null) return LocalTime(hour - 1, 30)
        val num = word.toIntOrNull()
        if (num != null && num in 1..23) return LocalTime(num - 1, 30)
    }

    // "о HH:MM" or "о HH"
    val digitalRegex = Regex("о\\s+(\\d{1,2})(?::(\\d{2}))?")
    digitalRegex.find(lower)?.let { match ->
        val h = match.groupValues[1].toIntOrNull() ?: return@let
        val m = match.groupValues[2].toIntOrNull() ?: 0
        if (h in 0..23) return LocalTime(h, m)
    }

    // "в HH:MM" or "в HH годин"
    val vDigital = Regex("в\\s+(\\d{1,2})(?::(\\d{2}))?")
    vDigital.find(lower)?.let { match ->
        val h = match.groupValues[1].toIntOrNull() ?: return@let
        val m = match.groupValues[2].toIntOrNull() ?: 0
        if (h in 6..23) return LocalTime(h, m)
    }

    // "о [word]" — word-based hour
    for ((word, hour) in wordHours) {
        val pattern = Regex("о\\s+$word")
        if (pattern.containsMatchIn(lower)) return LocalTime(hour, 0)
    }

    // Standalone HH:MM anywhere
    val standalone = Regex("\\b(\\d{1,2}):(\\d{2})\\b")
    standalone.find(lower)?.let { match ->
        val h = match.groupValues[1].toIntOrNull() ?: return@let
        val m = match.groupValues[2].toIntOrNull() ?: return@let
        if (h in 0..23 && m in 0..59) return LocalTime(h, m)
    }

    return null
}

private fun parseDuration(lower: String): Int? {
    // "пів години" → 30
    if (lower.contains("пів години") || lower.contains("пів-години")) return 30
    // "три чверті години" → 45
    if (lower.contains("три чверті")) return 45
    // "чверть години" → 15
    if (lower.contains("чверть години")) return 15

    // "X годин[у/и] і пів" / "X з половиною годин"
    val hoursAndHalf = Regex("(\\d+)\\s+(?:з половиною|і пів)\\s+годин")
    hoursAndHalf.find(lower)?.let { match ->
        val h = match.groupValues[1].toIntOrNull() ?: return@let
        return h * 60 + 30
    }
    val oneAndHalf = Regex("годину і пів|годину й пів")
    if (oneAndHalf.containsMatchIn(lower)) return 90

    // Numeric "X годин" — must come before word map to avoid "год" inside "години" false match
    val numHours = Regex("(\\d+)\\s+годин")
    numHours.find(lower)?.let { match ->
        val h = match.groupValues[1].toIntOrNull() ?: return@let
        if (h in 1..12) return h * 60
    }

    // "X хвилин" (numeric)
    val numMins = Regex("(\\d+)\\s+хвилин")
    numMins.find(lower)?.let { match ->
        val m = match.groupValues[1].toIntOrNull() ?: return@let
        if (m in 5..480) return m
    }

    // Word-number hours (bare "год" removed — too greedy inside "години"/"годину")
    val wordHourMap = mapOf(
        "одну годину" to 60, "одна година" to 60,
        "дві години" to 120, "три години" to 180, "чотири години" to 240,
        "п'ять годин" to 300, "шість годин" to 360,
        "годину" to 60,
    )
    for ((phrase, mins) in wordHourMap) {
        if (lower.contains(phrase)) return mins
    }

    return null
}
