package com.linkease

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object FreeTimeImageHelper {

    fun share(context: Context, title: String, lines: List<Pair<String, Boolean>>) {
        try {
            val uri = saveBitmapToCache(context, title, lines)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Поділитися").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(context: Context, title: String, lines: List<Pair<String, Boolean>>) {
        try {
            val uri = saveBitmapToCache(context, title, lines)
            val clip = ClipData.newUri(context.contentResolver, "free_time", uri)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Картинку скопійовано", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, title: String, lines: List<ScheduleImageLine>, heading: String = "Вільний час") {
        try {
            val uri = saveBitmapToCache(context, title, lines, heading)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Поділитися").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(context: Context, title: String, lines: List<ScheduleImageLine>, heading: String = "Вільний час") {
        try {
            val uri = saveBitmapToCache(context, title, lines, heading)
            val clip = ClipData.newUri(context.contentResolver, "free_time", uri)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Картинку скопійовано", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBitmapToCache(context: Context, title: String, lines: List<Pair<String, Boolean>>): android.net.Uri {
        val bitmap = generateBitmap(title, lines)
        val file = File(context.cacheDir, "free_time.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private fun saveBitmapToCache(context: Context, title: String, lines: List<ScheduleImageLine>, heading: String): android.net.Uri {
        val bitmap = generateBitmapLines(title, lines, heading)
        val file = File(context.cacheDir, "free_time.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private val DARK_BLUE = Color.parseColor("#1A237E")
    private val ACCENT    = Color.parseColor("#3949AB")
    private val TEXT_DARK = Color.parseColor("#212121")
    private val TEXT_GRAY = Color.parseColor("#757575")
    private val DIVIDER   = Color.parseColor("#E8EAED")
    private val SLOT_BG   = Color.parseColor("#F1F3F4")

    private fun generateBitmapLines(title: String, lines: List<ScheduleImageLine>, heading: String): Bitmap {
        val W = 800
        val padH = 52
        val headerH = 140
        val slotH = 64
        val sectionGap = 14

        var h = headerH + 20
        lines.forEach { line -> h += if (line.isHeader) slotH + sectionGap else slotH }
        h += 48

        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val cv  = Canvas(bmp)
        cv.drawColor(Color.WHITE)

        cv.drawRect(0f, 0f, W.toFloat(), headerH.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DARK_BLUE })
        cv.drawRect(0f, (headerH - 6).toFloat(), W.toFloat(), headerH.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT })

        cv.drawText(heading, padH.toFloat(), 66f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = 46f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })
        cv.drawText(title, padH.toFloat(), 108f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, 255, 255, 255); textSize = 30f
            })

        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DARK_BLUE; textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val slotTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_DARK; textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val slotDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_GRAY; textSize = 26f }
        val slotBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SLOT_BG }
        val divPaint = Paint().apply { color = DIVIDER; strokeWidth = 1.5f }

        var y = (headerH + 24).toFloat()
        lines.forEach { line ->
            if (line.isHeader) {
                y += sectionGap
                cv.drawLine(padH.toFloat(), y + 4f, (W - padH).toFloat(), y + 4f, divPaint)
                cv.drawText(line.text, padH.toFloat(), y + slotH * 0.65f, headerTextPaint)
                y += slotH
            } else {
                cv.drawRoundRect(RectF(padH.toFloat(), y + 6f, (W - padH).toFloat(), y + slotH - 4f), 10f, 10f, slotBgPaint)
                val dotColor = line.colorHex?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: ACCENT
                cv.drawCircle(padH + 24f, y + slotH / 2f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor })
                val parts = line.text.split("  ", limit = 2)
                cv.drawText(parts[0].trim(), padH + 44f, y + slotH * 0.62f, slotTimePaint)
                if (parts.size > 1) {
                    val dx = padH + 44f + slotTimePaint.measureText(parts[0].trim()) + 12f
                    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (line.colorHex != null) dotColor else TEXT_GRAY
                        textSize = 26f
                    }
                    cv.drawText(parts[1].trim(), dx, y + slotH * 0.62f, namePaint)
                }
                y += slotH
            }
        }
        return bmp
    }

    private fun generateBitmap(title: String, lines: List<Pair<String, Boolean>>): Bitmap {
        val W = 800
        val padH = 52
        val headerH = 140
        val slotH = 64
        val sectionGap = 14

        var h = headerH + 20
        lines.forEach { (_, isHeader) -> h += if (isHeader) slotH + sectionGap else slotH }
        h += 48

        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val cv  = Canvas(bmp)
        cv.drawColor(Color.WHITE)

        // Header
        cv.drawRect(0f, 0f, W.toFloat(), headerH.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DARK_BLUE })
        cv.drawRect(0f, (headerH - 6).toFloat(), W.toFloat(), headerH.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT })

        cv.drawText("Вільний час", padH.toFloat(), 66f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = 46f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })
        cv.drawText(title, padH.toFloat(), 108f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, 255, 255, 255); textSize = 30f
            })

        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DARK_BLUE; textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val slotTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_DARK; textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val slotDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_GRAY; textSize = 26f }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
        val slotBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SLOT_BG }
        val divPaint = Paint().apply { color = DIVIDER; strokeWidth = 1.5f }

        var y = (headerH + 24).toFloat()
        lines.forEach { (text, isHeader) ->
            if (isHeader) {
                y += sectionGap
                cv.drawLine(padH.toFloat(), y + 4f, (W - padH).toFloat(), y + 4f, divPaint)
                cv.drawText(text, padH.toFloat(), y + slotH * 0.65f, headerTextPaint)
                y += slotH
            } else {
                cv.drawRoundRect(RectF(padH.toFloat(), y + 6f, (W - padH).toFloat(), y + slotH - 4f), 10f, 10f, slotBgPaint)
                cv.drawCircle(padH + 24f, y + slotH / 2f, 8f, dotPaint)
                val parts = text.split("  ", limit = 2)
                cv.drawText(parts[0].trim(), padH + 44f, y + slotH * 0.62f, slotTimePaint)
                if (parts.size > 1) {
                    val dx = padH + 44f + slotTimePaint.measureText(parts[0].trim()) + 12f
                    cv.drawText(parts[1].trim(), dx, y + slotH * 0.62f, slotDetailPaint)
                }
                y += slotH
            }
        }
        return bmp
    }
}
