package com.reminderwidget

import java.util.Calendar

object NlpParser {
    data class Result(
        val startMs: Long,
        val title: String,
        val durationOverrideMs: Long? = null,
        val rrule: String? = null,
        val hasTime: Boolean = true,
    )

    // Ukrainian + English word numbers (common spoken forms)
    private val WORD_NUMS = mapOf(
        "одну" to 1, "один" to 1, "одна" to 1, "одне" to 1, "one" to 1,
        "дві" to 2, "два" to 2, "two" to 2,
        "три" to 3, "three" to 3,
        "чотири" to 4, "four" to 4,
        "п'ять" to 5, "пять" to 5, "five" to 5,
        "шість" to 6, "six" to 6,
        "сім" to 7, "seven" to 7,
        "вісім" to 8, "eight" to 8,
        "дев'ять" to 9, "девять" to 9, "nine" to 9,
        "десять" to 10, "ten" to 10,
        "одинадцять" to 11, "eleven" to 11,
        "дванадцять" to 12, "twelve" to 12,
        "тринадцять" to 13, "thirteen" to 13,
        "чотирнадцять" to 14, "fourteen" to 14,
        "п'ятнадцять" to 15, "пятнадцять" to 15, "fifteen" to 15,
        "двадцять" to 20, "twenty" to 20,
        "тридцять" to 30, "thirty" to 30,
        "сорок" to 40, "forty" to 40,
        "п'ятдесят" to 50, "пятдесят" to 50, "fifty" to 50,
    )

    private val WEEKDAY_CAL = mapOf(
        "monday" to Calendar.MONDAY, "mondays" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY, "tuesdays" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "wednesdays" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY, "thursdays" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY, "fridays" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY, "saturdays" to Calendar.SATURDAY,
        "sunday" to Calendar.SUNDAY, "sundays" to Calendar.SUNDAY,
        "понеділок" to Calendar.MONDAY, "вівторок" to Calendar.TUESDAY,
        "середу" to Calendar.WEDNESDAY, "четвер" to Calendar.THURSDAY,
        "п'ятницю" to Calendar.FRIDAY, "суботу" to Calendar.SATURDAY, "неділю" to Calendar.SUNDAY,
    )
    private val CAL_BYDAY = mapOf(
        Calendar.MONDAY to "MO", Calendar.TUESDAY to "TU", Calendar.WEDNESDAY to "WE",
        Calendar.THURSDAY to "TH", Calendar.FRIDAY to "FR",
        Calendar.SATURDAY to "SA", Calendar.SUNDAY to "SU",
    )

    // Build alternation pattern: digits OR word numbers (longest first to avoid partial matches)
    private val NUM_ALT: String = (
        WORD_NUMS.keys.sortedByDescending { it.length }.map { Regex.escape(it) } + listOf("""\d+""")
    ).joinToString("|")

    private val MIN_UNIT  = """(?:хвилин(?:у|ки|и)?|хв\.?|min(?:utes?)?)"""
    private val HOUR_UNIT = """(?:годин(?:у|и)?|год\.?|h(?:ou)?rs?)"""
    private val DAY_UNIT  = """(?:дн(?:ів|я|і)|день|days?)"""

    fun parse(input: String, fallbackStartMs: Long): Result {
        val lower = input.lowercase().trim()
        val now   = System.currentTimeMillis()

        // ── RECURRING ────────────────────────────────────────────────────────

        if (Regex("""(?:every|кожн[уи])\s+hours?|кожну\s+годину""").containsMatchIn(lower)) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            val stripped = lower.replace(Regex("""(?:every|кожн[уи])\s+hours?|кожну\s+годину""", RegexOption.IGNORE_CASE), "")
            return Result(cal.timeInMillis, cleanTitle(input, stripped), 3_600_000L, "FREQ=HOURLY")
        }

        val everyDayRx = Regex("""every\s+day|щодня|кожен\s+день|кожного\s+дня""")
        if (everyDayRx.containsMatchIn(lower)) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            return Result(cal.timeInMillis, cleanTitle(input, everyDayRx.replace(lower, "")), null, "FREQ=DAILY")
        }

        val everyWeekRx    = Regex("""every\s+week|щотижня|кожного\s+тижня""")
        val everyWeekdayRx = Regex("""every\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mondays|tuesdays|wednesdays|thursdays|fridays|saturdays|sundays|понеділок|вівторок|середу|четвер|п'ятницю|суботу|неділю)""")
        val isEveryWeek    = everyWeekRx.containsMatchIn(lower)
        val everyWdMatch   = everyWeekdayRx.find(lower)
        if (isEveryWeek || everyWdMatch != null) {
            val wdName     = everyWdMatch?.groupValues?.get(1) ?: WEEKDAY_CAL.keys.firstOrNull { lower.contains(it) }
            val wdCalConst = wdName?.let { WEEKDAY_CAL[it] }
            val byDay      = wdCalConst?.let { CAL_BYDAY[it] }
            val atRx       = Regex("""at\s+(\d{1,2})(?::(\d{2}))?(?:\s*(am|pm))?""")
            val atM        = atRx.find(lower)
            val hour       = atM?.groupValues?.get(1)?.toIntOrNull()?.let { h ->
                when (atM.groupValues.getOrNull(3)) { "pm" -> if (h < 12) h + 12 else h; "am" -> if (h == 12) 0 else h; else -> h }
            } ?: 9
            val minute = atM?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
                if (wdCalConst != null) { var diff = wdCalConst - get(Calendar.DAY_OF_WEEK); if (diff <= 0) diff += 7; add(Calendar.DAY_OF_YEAR, diff) }
                else add(Calendar.WEEK_OF_YEAR, 1)
            }
            val rrule = if (byDay != null) "FREQ=WEEKLY;BYDAY=$byDay" else "FREQ=WEEKLY"
            var stripped = lower
            everyWeekRx.find(stripped)?.let { stripped = stripped.replace(it.value, "") }
            everyWdMatch?.let { stripped = stripped.replace(it.value, "") }
            atM?.let { stripped = stripped.replace(it.value, "") }
            wdName?.let { stripped = stripped.replace(Regex("""(?:on\s+)?${Regex.escape(it)}""", RegexOption.IGNORE_CASE), "") }
            return Result(cal.timeInMillis, cleanTitle(input, stripped), 3_600_000L, rrule)
        }

        // ── ONE-TIME OFFSET (через / in) ──────────────────────────────────

        // "через пів години" / "in half an hour"
        if (Regex("""(?:через\s+пів\s+год(?:ини)?|in\s+half\s+an?\s+hour)""").containsMatchIn(lower)) {
            val m = Regex("""через\s+пів\s+год(?:ини)?|in\s+half\s+an?\s+hour""").find(lower)!!
            return Result(now + 30 * 60_000L, cleanMatch(input, m.value))
        }

        // "через годину" (without number = 1 hour)
        if (Regex("""через\s+годину\b""").containsMatchIn(lower)) {
            val m = Regex("""через\s+годину""").find(lower)!!
            return Result(now + 3_600_000L, cleanMatch(input, m.value))
        }

        // "через хвилину" (without number = 1 minute)
        if (Regex("""через\s+хвилину\b""").containsMatchIn(lower)) {
            val m = Regex("""через\s+хвилину""").find(lower)!!
            return Result(now + 60_000L, cleanMatch(input, m.value))
        }

        // General: "через NUMBER UNIT" / "in NUMBER UNIT"
        parseOffset(lower, now, input)?.let { return it }

        // ── TOMORROW ────────────────────────────────────────────────────────

        if (Regex("""\b(?:tomorrow|завтра)\b""").containsMatchIn(lower)) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            val m = Regex("""\b(?:tomorrow|завтра)\b""", RegexOption.IGNORE_CASE).find(input)
            return Result(cal.timeInMillis, cleanMatch(input, m?.value ?: ""))
        }

        // ── ON WEEKDAY ───────────────────────────────────────────────────────

        for ((name, dayConst) in WEEKDAY_CAL) {
            if (lower.contains(name)) {
                val cal = Calendar.getInstance()
                var diff = dayConst - cal.get(Calendar.DAY_OF_WEEK)
                if (diff <= 0) diff += 7
                cal.add(Calendar.DAY_OF_YEAR, diff)
                cal.set(Calendar.HOUR_OF_DAY, 9); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                return Result(cal.timeInMillis, cleanMatch(input, Regex("""(?:on |в )?${Regex.escape(name)}""", RegexOption.IGNORE_CASE).find(input)?.value ?: name))
            }
        }

        // ── AT TIME ───────────────────────────────────────────────────────────

        Regex("""(?:at |о )(\d{1,2}):(\d{2})""").find(lower)?.also { m ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, m.groupValues[1].toInt())
                set(Calendar.MINUTE, m.groupValues[2].toInt())
                set(Calendar.SECOND, 0)
                if (timeInMillis < now) add(Calendar.DAY_OF_YEAR, 1)
            }
            return Result(cal.timeInMillis, cleanMatch(input, m.value))
        }

        return Result(fallbackStartMs, input.trim(), hasTime = false)
    }

    private fun parseOffset(lower: String, now: Long, original: String): Result? {
        val prefix = """(?:через\s+|за\s+|in\s+)"""
        val minRx  = Regex("""$prefix($NUM_ALT)\s*$MIN_UNIT""")
        val hourRx = Regex("""$prefix($NUM_ALT)\s*$HOUR_UNIT""")
        val dayRx  = Regex("""$prefix($NUM_ALT)\s*$DAY_UNIT""")

        minRx.find(lower)?.let { m ->
            val n = resolveNum(m.groupValues[1]) ?: return@let
            return Result(now + n * 60_000L, cleanMatch(original, m.value))
        }
        hourRx.find(lower)?.let { m ->
            val n = resolveNum(m.groupValues[1]) ?: return@let
            return Result(now + n * 3_600_000L, cleanMatch(original, m.value))
        }
        dayRx.find(lower)?.let { m ->
            val n = resolveNum(m.groupValues[1]) ?: return@let
            return Result(now + n * 86_400_000L, cleanMatch(original, m.value))
        }
        return null
    }

    private fun resolveNum(s: String): Long? = s.toLongOrNull() ?: WORD_NUMS[s.lowercase()]?.toLong()

    private fun cleanMatch(original: String, toRemove: String): String =
        original.replace(toRemove, "", ignoreCase = true).tidyUp().ifBlank { original.trim() }

    private fun cleanTitle(original: String, stripped: String): String {
        val words = stripped.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        var result = original
        words.forEach { w -> result = result.replace(w, "", ignoreCase = true) }
        return result.tidyUp().ifBlank { original.trim() }
    }

    private fun String.tidyUp() = trim().trimStart('-', '–', ',', '.').trim().replace(Regex("""\s{2,}"""), " ").trim()
}
