package com.reminderwidget

import java.util.Calendar

object NlpParser {
    data class Result(
        val startMs: Long,
        val title: String,
        val durationOverrideMs: Long? = null,
        val rrule: String? = null,
        val hasTime: Boolean = true,
        val count: Int = 1,
        val intervalMs: Long? = null,
    )

    data class TimePrefs(
        val morningHour: Int = 8,  val morningMin: Int = 0,
        val dayHour: Int    = 13,  val dayMin: Int    = 0,
        val eveningHour: Int = 19, val eveningMin: Int = 0,
    )

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
        "шістнадцять" to 16, "sixteen" to 16,
        "сімнадцять" to 17, "seventeen" to 17,
        "вісімнадцять" to 18, "eighteen" to 18,
        "дев'ятнадцять" to 19, "девятнадцять" to 19, "nineteen" to 19,
        "двадцять" to 20, "twenty" to 20,
        "тридцять" to 30, "thirty" to 30,
        "сорок" to 40, "forty" to 40,
        "п'ятдесят" to 50, "пятдесят" to 50, "fifty" to 50,
    )

    private val MONTH_MAP = mapOf(
        "січня" to 1,  "січень" to 1,  "january" to 1,  "jan" to 1,
        "лютого" to 2, "лютий" to 2,  "february" to 2, "feb" to 2,
        "березня" to 3, "березень" to 3, "march" to 3, "mar" to 3,
        "квітня" to 4, "квітень" to 4, "april" to 4,  "apr" to 4,
        "травня" to 5, "травень" to 5, "may" to 5,
        "червня" to 6, "червень" to 6, "june" to 6,   "jun" to 6,
        "липня" to 7,  "липень" to 7,  "july" to 7,   "jul" to 7,
        "серпня" to 8, "серпень" to 8, "august" to 8, "aug" to 8,
        "вересня" to 9,   "вересень" to 9,   "september" to 9, "sep" to 9, "sept" to 9,
        "жовтня" to 10,   "жовтень" to 10,   "october" to 10,  "oct" to 10,
        "листопада" to 11, "листопад" to 11, "november" to 11, "nov" to 11,
        "грудня" to 12,   "грудень" to 12,   "december" to 12, "dec" to 12,
    )

    // Ukrainian accusative ordinal hours for "пів на третю" = 2:30
    private val ORDINAL_HOUR_UK = mapOf(
        "першу" to 1, "другу" to 2, "третю" to 3, "четверту" to 4,
        "п'яту" to 5, "пяту" to 5, "шосту" to 6, "сьому" to 7, "восьму" to 8,
        "дев'яту" to 9, "девяту" to 9, "десяту" to 10, "одинадцяту" to 11, "дванадцяту" to 12,
    )

    // Ukrainian locative/prepositional ordinal hours for "о сімнадцятій" = 17:00
    private val ORDINAL_HOUR_LOC = mapOf(
        "двадцять першій" to 21, "двадцять другій" to 22,
        "двадцять третій" to 23, "двадцять четвертій" to 0, "двадцять четвертою" to 0,
        "першій" to 1, "другій" to 2, "третій" to 3, "четвертій" to 4,
        "п'ятій" to 5, "пятій" to 5, "шостій" to 6, "сьомій" to 7,
        "восьмій" to 8, "дев'ятій" to 9, "девятій" to 9, "десятій" to 10,
        "одинадцятій" to 11, "дванадцятій" to 12, "тринадцятій" to 13,
        "чотирнадцятій" to 14, "п'ятнадцятій" to 15, "пятнадцятій" to 15,
        "шістнадцятій" to 16, "сімнадцятій" to 17, "вісімнадцятій" to 18,
        "дев'ятнадцятій" to 19, "девятнадцятій" to 19, "двадцятій" to 20,
    )

    private val WEEKDAY_CAL = mapOf(
        "monday" to Calendar.MONDAY, "mondays" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY, "tuesdays" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "wednesdays" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY, "thursdays" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY, "fridays" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY, "saturdays" to Calendar.SATURDAY,
        "sunday" to Calendar.SUNDAY, "sundays" to Calendar.SUNDAY,
        "понеділок" to Calendar.MONDAY,  "понеділка" to Calendar.MONDAY,
        "вівторок"  to Calendar.TUESDAY, "вівторка"  to Calendar.TUESDAY,
        "середу"    to Calendar.WEDNESDAY, "середа"  to Calendar.WEDNESDAY, "середи" to Calendar.WEDNESDAY,
        "четвер"    to Calendar.THURSDAY, "четверга" to Calendar.THURSDAY,
        "п'ятницю"  to Calendar.FRIDAY,  "п'ятниця" to Calendar.FRIDAY, "п'ятниці" to Calendar.FRIDAY,
        "пятницю"   to Calendar.FRIDAY,  "пятниці"  to Calendar.FRIDAY,
        "суботу"    to Calendar.SATURDAY, "субота"   to Calendar.SATURDAY, "суботи" to Calendar.SATURDAY, "суботах" to Calendar.SATURDAY,
        "неділю"    to Calendar.SUNDAY,   "неділя"   to Calendar.SUNDAY,  "неділі" to Calendar.SUNDAY,  "неділях" to Calendar.SUNDAY,
        "понеділках" to Calendar.MONDAY,
        "вівторках"  to Calendar.TUESDAY,
        "середах"    to Calendar.WEDNESDAY,
        "четвергах"  to Calendar.THURSDAY,
        "п'ятницях"  to Calendar.FRIDAY,  "пятницях" to Calendar.FRIDAY,
    )

    private val EVERY_WEEKDAY_UK = mapOf(
        "щопонеділка" to Calendar.MONDAY,  "щопонеділок" to Calendar.MONDAY,
        "щовівторка"  to Calendar.TUESDAY,
        "щосереди"    to Calendar.WEDNESDAY, "щосереда"  to Calendar.WEDNESDAY,
        "щочетверга"  to Calendar.THURSDAY,
        "щоп'ятниці"  to Calendar.FRIDAY,  "щопятниці" to Calendar.FRIDAY,
        "щосуботи"    to Calendar.SATURDAY,
        "щонеділі"    to Calendar.SUNDAY,
    )

    private val CAL_BYDAY = mapOf(
        Calendar.MONDAY to "MO", Calendar.TUESDAY to "TU", Calendar.WEDNESDAY to "WE",
        Calendar.THURSDAY to "TH", Calendar.FRIDAY to "FR",
        Calendar.SATURDAY to "SA", Calendar.SUNDAY to "SU",
    )

    private val NUM_ALT: String = (
        WORD_NUMS.keys.sortedByDescending { it.length }.map { Regex.escape(it) } + listOf("""\d+""")
    ).joinToString("|")

    private val MIN_UNIT  = """(?:хвилин(?:у|ки|и)?|хв\.?|min(?:utes?)?)"""
    private val HOUR_UNIT = """(?:годин(?:у|и)?|год\.?|h(?:ou)?rs?)"""
    private val DAY_UNIT  = """(?:дн(?:ів|я|і)|день|days?)"""
    private val WEEK_UNIT = """(?:тижн(?:ів|я|і)|тиждень|weeks?)"""

    fun parse(input: String, fallbackStartMs: Long, timePrefs: TimePrefs = TimePrefs()): Result {
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
            val clock      = extractClockTime(lower, timePrefs)
            val razyRx     = Regex("""($NUM_ALT)\s*(?:раз(?:ів|и)?|times?)""")
            val countRazyM = razyRx.find(lower)
            val countRazy  = countRazyM?.let { resolveNum(it.groupValues[1])?.toInt() }
            val evDayVal   = everyDayRx.find(lower)?.value ?: ""
            var title      = if (evDayVal.isNotEmpty()) cleanMatch(input, evDayVal) else input.trim()
            clock?.let      { title = cleanMatch(title, it.third) }
            countRazyM?.let { title = cleanMatch(title, it.value) }
            if (countRazy != null && countRazy > 1) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, clock?.first ?: 9)
                    set(Calendar.MINUTE,      clock?.second ?: 0); set(Calendar.SECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                }
                return Result(cal.timeInMillis, title, null, null, count = countRazy, intervalMs = 86_400_000L)
            }
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, clock?.first ?: 9)
                set(Calendar.MINUTE,      clock?.second ?: 0); set(Calendar.SECOND, 0)
            }
            return Result(cal.timeInMillis, title, null, "FREQ=DAILY")
        }

        for ((ukWord, dayConst) in EVERY_WEEKDAY_UK) {
            if (lower.contains(ukWord)) {
                val clock = extractClockTime(lower, timePrefs)
                val cal = Calendar.getInstance().apply {
                    var diff = dayConst - get(Calendar.DAY_OF_WEEK); if (diff <= 0) diff += 7
                    add(Calendar.DAY_OF_YEAR, diff)
                    set(Calendar.HOUR_OF_DAY, clock?.first ?: 9)
                    set(Calendar.MINUTE,      clock?.second ?: 0); set(Calendar.SECOND, 0)
                }
                val byDay = CAL_BYDAY[dayConst]
                var title = cleanMatch(input, Regex(Regex.escape(ukWord), RegexOption.IGNORE_CASE).find(input)?.value ?: ukWord)
                clock?.let { title = cleanMatch(title, it.third) }
                return Result(cal.timeInMillis, title, 3_600_000L, if (byDay != null) "FREQ=WEEKLY;BYDAY=$byDay" else "FREQ=WEEKLY")
            }
        }

        // ── EVERY WEEKDAY: кожного понеділка / по вівторках 3 тижні / кожного вівторка о 15:00 ─
        val kozhnRx      = Regex("""кожн\p{L}*""")
        val poWdRx       = Regex("""по\s+(\p{L}+)""")
        val hasKozhn     = kozhnRx.containsMatchIn(lower)
        val poWdM        = if (!hasKozhn) poWdRx.find(lower) else null
        val hasPoWeekday = poWdM != null && WEEKDAY_CAL.containsKey(poWdM.groupValues[1])
        if (hasKozhn || hasPoWeekday) {
            val wdEntry = WEEKDAY_CAL.entries.sortedByDescending { it.key.length }
                .firstOrNull { lower.contains(it.key) }
            if (wdEntry != null) {
                val clock       = extractClockTime(lower, timePrefs)
                val razyRx      = Regex("""($NUM_ALT)\s*(?:раз(?:ів|и)?|times?)""")
                val countWeeksM = Regex("""($NUM_ALT)\s*(?:тижн(?:ів|я|і)|тиждень|weeks?)""").find(lower)
                val countRazyM  = razyRx.find(lower)
                val countM      = countWeeksM ?: countRazyM
                val count       = countM?.let { resolveNum(it.groupValues[1])?.toInt() }
                val cal = Calendar.getInstance().apply {
                    var diff = wdEntry.value - get(Calendar.DAY_OF_WEEK)
                    if (diff <= 0) diff += 7
                    add(Calendar.DAY_OF_YEAR, diff)
                    set(Calendar.HOUR_OF_DAY, clock?.first ?: 9)
                    set(Calendar.MINUTE,      clock?.second ?: 0); set(Calendar.SECOND, 0)
                }
                val kozhnM   = kozhnRx.find(lower)
                val stripStr = kozhnM?.value ?: poWdM?.value ?: ""
                var title    = if (stripStr.isNotBlank()) cleanMatch(input, stripStr) else input.trim()
                val dayRmv   = Regex("""(?:по\s+|в\s+|у\s+)?${Regex.escape(wdEntry.key)}""", RegexOption.IGNORE_CASE).find(input)?.value ?: wdEntry.key
                title = cleanMatch(title, dayRmv)
                clock?.let  { title = cleanMatch(title, it.third) }
                countM?.let { title = cleanMatch(title, it.value) }
                if (count != null && count > 1) {
                    return Result(cal.timeInMillis, title, 3_600_000L, null, count = count, intervalMs = 7L * 86_400_000L)
                }
                val byDay = CAL_BYDAY[wdEntry.value]
                val rrule = buildString {
                    append("FREQ=WEEKLY")
                    if (byDay != null) append(";BYDAY=$byDay")
                }
                return Result(cal.timeInMillis, title, 3_600_000L, rrule)
            }
        }

        val everyWeekRx    = Regex("""every\s+week|щотижня|кожного\s+тижня""")
        val wdNames        = WEEKDAY_CAL.keys.sortedByDescending { it.length }
        val everyWeekdayRx = Regex("""every\s+(${wdNames.joinToString("|") { Regex.escape(it) }})""")
        val isEveryWeek    = everyWeekRx.containsMatchIn(lower)
        val everyWdMatch   = everyWeekdayRx.find(lower)
        if (isEveryWeek || everyWdMatch != null) {
            val wdName     = everyWdMatch?.groupValues?.get(1) ?: WEEKDAY_CAL.keys.firstOrNull { lower.contains(it) }
            val wdCalConst = wdName?.let { WEEKDAY_CAL[it] }
            val byDay      = wdCalConst?.let { CAL_BYDAY[it] }
            val clock  = extractClockTime(lower, timePrefs)
            val atRx   = Regex("""at\s+(\d{1,2})(?::(\d{2}))?(?:\s*(am|pm))?""")
            val atM    = atRx.find(lower)
            val hour   = clock?.first ?: atM?.groupValues?.get(1)?.toIntOrNull()?.let { h ->
                when (atM?.groupValues?.getOrNull(3)) { "pm" -> if (h < 12) h + 12 else h; "am" -> if (h == 12) 0 else h; else -> h }
            } ?: 9
            val minute = clock?.second ?: atM?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
                if (wdCalConst != null) { var diff = wdCalConst - get(Calendar.DAY_OF_WEEK); if (diff <= 0) diff += 7; add(Calendar.DAY_OF_YEAR, diff) }
                else { var diff = Calendar.MONDAY - get(Calendar.DAY_OF_WEEK); if (diff <= 0) diff += 7; add(Calendar.DAY_OF_YEAR, diff) }
            }
            val rrule = if (byDay != null) "FREQ=WEEKLY;BYDAY=$byDay" else "FREQ=WEEKLY"
            var stripped = lower
            everyWeekRx.find(stripped)?.let { stripped = stripped.replace(it.value, "") }
            everyWdMatch?.let { stripped = stripped.replace(it.value, "") }
            atM?.let { stripped = stripped.replace(it.value, "") }
            clock?.let { stripped = stripped.replace(it.third, "") }
            wdName?.let { stripped = stripped.replace(Regex("""(?:on\s+)?${Regex.escape(it)}""", RegexOption.IGNORE_CASE), "") }
            return Result(cal.timeInMillis, cleanTitle(input, stripped), 3_600_000L, rrule)
        }

        // ── MONTHLY ──────────────────────────────────────────────────────────

        val monthlyRx = Regex("""раз\s+на\s+місяць|once\s+a\s+month|щомісяця|кожного\s+місяця""")
        if (monthlyRx.containsMatchIn(lower)) {
            val clock = extractClockTime(lower, timePrefs)
            val cal   = Calendar.getInstance().apply {
                add(Calendar.MONTH, 1)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, clock?.first ?: timePrefs.morningHour)
                set(Calendar.MINUTE,      clock?.second ?: timePrefs.morningMin); set(Calendar.SECOND, 0)
            }
            val mVal = monthlyRx.find(lower)?.value ?: ""
            var title = if (mVal.isNotEmpty()) cleanMatch(input, mVal) else input.trim()
            clock?.let { title = cleanMatch(title, it.third) }
            return Result(cal.timeInMillis, title, null, "FREQ=MONTHLY")
        }

        // ── ONE-TIME OFFSET (через / in) ──────────────────────────────────

        if (Regex("""(?:через\s+пів\s+год(?:ини)?|in\s+half\s+an?\s+hour)""").containsMatchIn(lower)) {
            val m = Regex("""через\s+пів\s+год(?:ини)?|in\s+half\s+an?\s+hour""").find(lower)!!
            return Result(now + 30 * 60_000L, cleanMatch(input, m.value))
        }

        if (Regex("""через\s+годину\b""").containsMatchIn(lower)) {
            val m = Regex("""через\s+годину""").find(lower)!!
            return Result(now + 3_600_000L, cleanMatch(input, m.value))
        }

        if (Regex("""через\s+хвилину\b""").containsMatchIn(lower)) {
            val m = Regex("""через\s+хвилину""").find(lower)!!
            return Result(now + 60_000L, cleanMatch(input, m.value))
        }

        if (Regex("""через\s+тиждень\b""").containsMatchIn(lower)) {
            val m = Regex("""через\s+тиждень""").find(lower)!!
            return Result(now + 7 * 86_400_000L, cleanMatch(input, m.value))
        }

        parseOffset(lower, now, input, timePrefs)?.let { return it }

        // ── N DAYS (bare, no через prefix) → N consecutive events ────────────

        Regex("""($NUM_ALT)\s*(?:дн(?:ів|я|і)|день|days?)""").find(lower)?.let { m ->
            val n = resolveNum(m.groupValues[1])?.toInt() ?: return@let
            if (n < 1 || n > 365) return@let
            val clock = extractClockTime(lower, timePrefs)
            val cal   = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, clock?.first ?: 9)
                set(Calendar.MINUTE,      clock?.second ?: 0); set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            var title = cleanMatch(input, m.value)
            clock?.let { title = cleanMatch(title, it.third) }
            return if (n > 1) Result(cal.timeInMillis, title, null, null, count = n, intervalMs = 86_400_000L)
                   else        Result(cal.timeInMillis, title)
        }

        // ── TODAY ────────────────────────────────────────────────────────────

        if (Regex("""\b(?:today|сьогодні)\b""").containsMatchIn(lower)) {
            val clock = extractClockTime(lower, timePrefs)
            val cal   = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, clock?.first ?: 9)
                set(Calendar.MINUTE,      clock?.second ?: 0); set(Calendar.SECOND, 0)
                if (timeInMillis < now) add(Calendar.DAY_OF_YEAR, 1)
            }
            val m   = Regex("""\b(?:today|сьогодні)\b""", RegexOption.IGNORE_CASE).find(input)
            var title = cleanMatch(input, m?.value ?: "")
            clock?.let { title = cleanMatch(title, it.third) }
            return Result(cal.timeInMillis, title)
        }

        // ── SPECIFIC DATE ─────────────────────────────────────────────────────

        extractSpecificDate(lower)?.let { (day, month, raw) ->
            val clock = extractClockTime(lower, timePrefs)
            val cal   = Calendar.getInstance().apply {
                set(Calendar.MONTH,        month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY,  clock?.first ?: 9)
                set(Calendar.MINUTE,       clock?.second ?: 0); set(Calendar.SECOND, 0)
                if (timeInMillis < now) add(Calendar.YEAR, 1)
            }
            var title = cleanMatch(input, raw)
            clock?.let { title = cleanMatch(title, it.third) }
            return Result(cal.timeInMillis, title)
        }

        // ── NEXT WEEK ─────────────────────────────────────────────────────────

        val nextWeekRx = Regex("""наступного\s+тижня|next\s+week""")
        if (nextWeekRx.containsMatchIn(lower)) {
            val clock    = extractClockTime(lower, timePrefs)
            var stripped = nextWeekRx.replace(lower, "")
            val wdEntry  = WEEKDAY_CAL.entries.firstOrNull { stripped.contains(it.key) }
            val cal      = Calendar.getInstance().apply { add(Calendar.WEEK_OF_YEAR, 1) }
            if (wdEntry != null) {
                cal.set(Calendar.DAY_OF_WEEK, wdEntry.value)
                stripped = stripped.replace(wdEntry.key, "")
            }
            cal.set(Calendar.HOUR_OF_DAY, clock?.first ?: 9)
            cal.set(Calendar.MINUTE,      clock?.second ?: 0); cal.set(Calendar.SECOND, 0)
            clock?.let { stripped = stripped.replace(it.third, "") }
            return Result(cal.timeInMillis, cleanTitle(input, stripped))
        }

        // ── TOMORROW ────────────────────────────────────────────────────────

        if (Regex("""\b(?:tomorrow|завтра)\b""").containsMatchIn(lower)) {
            val clock = extractClockTime(lower, timePrefs)
            val cal   = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, clock?.first ?: 9)
                set(Calendar.MINUTE,      clock?.second ?: 0); set(Calendar.SECOND, 0)
            }
            val m   = Regex("""\b(?:tomorrow|завтра)\b""", RegexOption.IGNORE_CASE).find(input)
            var title = cleanMatch(input, m?.value ?: "")
            clock?.let { title = cleanMatch(title, it.third) }
            return Result(cal.timeInMillis, title)
        }

        // ── ON WEEKDAY ───────────────────────────────────────────────────────

        for ((name, dayConst) in WEEKDAY_CAL) {
            if (lower.contains(name)) {
                val clock = extractClockTime(lower, timePrefs)
                val cal   = Calendar.getInstance()
                var diff  = dayConst - cal.get(Calendar.DAY_OF_WEEK)
                if (diff <= 0) diff += 7
                cal.add(Calendar.DAY_OF_YEAR, diff)
                cal.set(Calendar.HOUR_OF_DAY, clock?.first ?: 9)
                cal.set(Calendar.MINUTE,      clock?.second ?: 0); cal.set(Calendar.SECOND, 0)
                val dayRemove = Regex("""(?:on |в |у )?${Regex.escape(name)}""", RegexOption.IGNORE_CASE).find(input)?.value ?: name
                var title = cleanMatch(input, dayRemove)
                clock?.let { title = cleanMatch(title, it.third) }
                return Result(cal.timeInMillis, title)
            }
        }

        // ── AT TIME ───────────────────────────────────────────────────────────

        extractClockTime(lower, timePrefs)?.also { (h, min, raw) ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min); set(Calendar.SECOND, 0)
                if (timeInMillis < now) add(Calendar.DAY_OF_YEAR, 1)
            }
            return Result(cal.timeInMillis, cleanMatch(input, raw))
        }

        return Result(fallbackStartMs, input.trim(), hasTime = false)
    }

    private fun extractSpecificDate(lower: String): Triple<Int, Int, String>? {
        val monthAlt = MONTH_MAP.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        // "15 червня" or "15 june"
        Regex("""(\d{1,2})\s+(${monthAlt})""").find(lower)?.let { m ->
            val day   = m.groupValues[1].toIntOrNull() ?: return@let null
            val month = MONTH_MAP[m.groupValues[2]] ?: return@let null
            if (day in 1..31) return Triple(day, month, m.value)
        }
        // "червня 15" or "june 15"
        Regex("""(${monthAlt})\s+(\d{1,2})""").find(lower)?.let { m ->
            val month = MONTH_MAP[m.groupValues[1]] ?: return@let null
            val day   = m.groupValues[2].toIntOrNull() ?: return@let null
            if (day in 1..31) return Triple(day, month, m.value)
        }
        // "15.06" or "15.06.2026"
        Regex("""(\d{1,2})\.(\d{1,2})(?:\.\d{2,4})?""").find(lower)?.let { m ->
            val day   = m.groupValues[1].toIntOrNull() ?: return@let null
            val month = m.groupValues[2].toIntOrNull() ?: return@let null
            if (day in 1..31 && month in 1..12) return Triple(day, month, m.value)
        }
        return null
    }

    /** Extracts time from a lowercase string. */
    private fun extractClockTime(lower: String, tp: TimePrefs = TimePrefs()): Triple<Int, Int, String>? {
        // "о пів на третю" = 2:30, "пів на дванадцяту" = 11:30
        val halvAlt = ORDINAL_HOUR_UK.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        Regex("""(?:о\s+)?пів\s+на\s+(${halvAlt})""").find(lower)?.let { m ->
            val h    = ORDINAL_HOUR_UK[m.groupValues[1]] ?: return@let null
            val prev = if (h == 1) 12 else h - 1
            return Triple(prev, 30, m.value)
        }
        // "о сімнадцятій шістнадцять" = 17:16, "о першій" = 1:00
        val locAlt = ORDINAL_HOUR_LOC.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        Regex("""(?:о|об)\s+(${locAlt})(?:\s+(${NUM_ALT}))?""").find(lower)?.let { m ->
            val h   = ORDINAL_HOUR_LOC[m.groupValues[1]] ?: return@let null
            val raw = m.groupValues[2]
            val min = if (raw.isEmpty()) 0 else (resolveNum(raw)?.toInt() ?: return@let null)
            if (min in 0..59) return Triple(h, min, m.value)
        }
        // HH:mm form
        Regex("""(?:at\s+|о\s+)(\d{1,2}):(\d{2})""").find(lower)?.let { m ->
            val h   = m.groupValues[1].toIntOrNull() ?: return@let null
            val min = m.groupValues[2].toIntOrNull() ?: return@let null
            if (h in 0..23 && min in 0..59) return Triple(h, min, m.value)
        }
        // HH only
        Regex("""(?:at\s+|о\s+)(\d{1,2})\b""").find(lower)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@let null
            if (h in 0..23) return Triple(h, 0, m.value)
        }
        // Time-of-day words (configurable); include split-word forms produced by voice recognition
        val timeOfDay = listOf(
            Regex("""вранці|вранку|зранку|з\s+ранку|\bmorning\b""") to (tp.morningHour to tp.morningMin),
            Regex("""вдень|в\s+день|опівдні|\bafternoon\b""")        to (tp.dayHour    to tp.dayMin),
            Regex("""ввечері|ввечір|увечері|у\s+вечері|в\s+вечері|\bevening\b""") to (tp.eveningHour to tp.eveningMin),
            Regex("""вночі|у\s+ночі|в\s+ночі|\bnight\b""")           to (23 to 0),
            Regex("""опівночі""")                                      to (0  to 0),
        )
        for ((rx, hm) in timeOfDay) {
            rx.find(lower)?.let { m -> return Triple(hm.first, hm.second, m.value) }
        }
        return null
    }

    private fun parseOffset(lower: String, now: Long, original: String, tp: TimePrefs = TimePrefs()): Result? {
        val prefix = """(?:через\s+|за\s+|in\s+)"""
        val minRx  = Regex("""$prefix($NUM_ALT)\s*$MIN_UNIT""")
        val hourRx = Regex("""$prefix($NUM_ALT)\s*$HOUR_UNIT""")
        val dayRx  = Regex("""$prefix($NUM_ALT)\s*$DAY_UNIT""")
        val weekRx = Regex("""$prefix($NUM_ALT)\s*$WEEK_UNIT""")

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
            val clock = extractClockTime(lower, tp)
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, n.toInt())
                if (clock != null) { set(Calendar.HOUR_OF_DAY, clock.first); set(Calendar.MINUTE, clock.second) }
                set(Calendar.SECOND, 0)
            }
            var title = cleanMatch(original, m.value)
            clock?.let { title = cleanMatch(title, it.third) }
            return Result(cal.timeInMillis, title)
        }
        weekRx.find(lower)?.let { m ->
            val n = resolveNum(m.groupValues[1]) ?: return@let
            val clock = extractClockTime(lower, tp)
            val cal = Calendar.getInstance().apply {
                add(Calendar.WEEK_OF_YEAR, n.toInt())
                if (clock != null) { set(Calendar.HOUR_OF_DAY, clock.first); set(Calendar.MINUTE, clock.second) }
                set(Calendar.SECOND, 0)
            }
            var title = cleanMatch(original, m.value)
            clock?.let { title = cleanMatch(title, it.third) }
            return Result(cal.timeInMillis, title)
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
