package com.reminderwidget

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GeminiParser {
    private const val TAG     = "GeminiParser"
    private const val TIMEOUT = 8_000
    private const val MODEL   = "gemini-2.0-flash"
    private const val BASE    = "https://generativelanguage.googleapis.com/v1beta/models"

    fun parse(input: String, nowMs: Long): NlpParser.Result? {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) return null

        val nowStr = SimpleDateFormat("EEEE dd MMMM yyyy HH:mm", Locale("uk")).format(Date(nowMs))
        val prompt = buildPrompt(input, nowStr, nowMs)

        return try {
            val url  = URL("$BASE/$MODEL:generateContent?key=$key")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = TIMEOUT
                readTimeout    = TIMEOUT
                doOutput       = true
            }
            val bodyJson = """{"contents":[{"parts":[{"text":${JSONObject.quote(prompt)}}]}],"generationConfig":{"temperature":0,"responseMimeType":"application/json"}}"""
            conn.outputStream.use { it.write(bodyJson.toByteArray()) }

            if (conn.responseCode != 200) {
                Log.w(TAG, "HTTP ${conn.responseCode}: ${conn.errorStream?.bufferedReader()?.readText()}")
                return null
            }

            val raw  = conn.inputStream.bufferedReader().readText()
            val text = JSONObject(raw)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0)
                .getString("text")

            val json    = JSONObject(text)
            val startMs = json.getLong("startMs")
            val title   = json.optString("title", "").trim().ifBlank { input.trim() }
            val rrule   = json.optString("rrule", "").takeIf { it.isNotBlank() && it != "null" }

            // sanity: don't accept timestamps more than 2 years out or before now-1min
            val twoYears = 2L * 365 * 24 * 3600 * 1000
            if (startMs < nowMs - 60_000L || startMs > nowMs + twoYears) {
                Log.w(TAG, "Suspicious startMs=$startMs, falling back")
                return null
            }

            Log.d(TAG, "Gemini: title='$title' startMs=$startMs rrule=$rrule")
            NlpParser.Result(startMs = startMs, title = title, rrule = rrule)
        } catch (e: Exception) {
            Log.e(TAG, "Gemini parse failed", e)
            null
        }
    }

    private fun buildPrompt(input: String, nowStr: String, nowMs: Long) = """
You are a reminder parser. Current date/time: $nowStr (unix ms: $nowMs).

Parse the text below. Return ONLY a JSON object — no markdown, no explanation:
{
  "startMs": <unix timestamp in ms when the reminder should fire>,
  "title": "<reminder text without any date/time words>",
  "rrule": "<RRULE string if recurring, e.g. FREQ=DAILY or FREQ=WEEKLY;BYDAY=MO, otherwise null>"
}

Rules:
- If no specific time given, default to 09:00 of the target day
- "вранці" / "morning" = 08:00; "вдень" / "afternoon" = 13:00; "ввечері" / "evening" = 19:00; "вночі" / "night" = 22:00
- If the target time is already in the past today, schedule for tomorrow (or the next valid occurrence)
- Strip ALL temporal expressions from the title — keep only the task/event description
- Input can be in Ukrainian, English, or mixed

Text: "$input"
""".trimIndent()
}
