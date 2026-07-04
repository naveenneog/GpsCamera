package com.gpscamera.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/** Burns a legible, geotag-style information panel (optionally with a map) onto a photo. */
object PhotoStamper {

    private const val BASELINE_WIDTH = 1080f

    /**
     * Returns a new bitmap identical to [src] with an information panel drawn on it.
     *
     * @param lines     text rows (see [com.gpscamera.util.GpsFormat.buildStampLines]).
     * @param mapBitmap optional square map thumbnail drawn to the right of the text.
     * @param anchorX   horizontal centre of the panel as a fraction (0..1) of the width.
     * @param anchorY   vertical centre of the panel as a fraction (0..1) of the height.
     * @param userScale size multiplier controlled by pinch-to-resize (clamped 0.6..2.0).
     */
    fun stamp(
        src: Bitmap,
        lines: List<String>,
        mapBitmap: Bitmap? = null,
        anchorX: Float = 0.5f,
        anchorY: Float = 0.9f,
        userScale: Float = 1f,
        title: String = "GPS Camera"
    ): Bitmap {
        val output = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val w = output.width.toFloat()
        val h = output.height.toFloat()
        val scale = (w / BASELINE_WIDTH).coerceAtLeast(0.5f) * userScale.coerceIn(0.6f, 2.0f)

        val padding = 24f * scale
        val titleSize = 34f * scale
        val bodySize = 30f * scale
        val lineGap = bodySize * 1.45f
        val titleGap = titleSize * 1.6f
        val radius = 18f * scale

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = titleSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f * scale, 0f, 1f * scale, Color.BLACK)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = bodySize
            typeface = Typeface.DEFAULT
            setShadowLayer(4f * scale, 0f, 1f * scale, Color.BLACK)
        }

        val textBlockHeight = titleGap + lineGap * lines.size
        val textWidth = maxOf(
            titlePaint.measureText(title) + titleSize * 1.2f,
            lines.maxOfOrNull { bodyPaint.measureText(it) } ?: 0f
        )

        val mapSize = if (mapBitmap != null) textBlockHeight.coerceAtMost(300f * scale) else 0f
        val mapGap = if (mapBitmap != null) padding else 0f

        val contentHeight = maxOf(textBlockHeight, mapSize)
        val panelWidth = padding * 2 + 8f * scale + textWidth + mapGap + mapSize
        val panelHeight = padding * 2 + contentHeight

        val margin = 16f * scale
        val centerX = anchorX * w
        val centerY = anchorY * h
        val left = (centerX - panelWidth / 2f).coerceIn(margin, (w - margin - panelWidth).coerceAtLeast(margin))
        val top = (centerY - panelHeight / 2f).coerceIn(margin, (h - margin - panelHeight).coerceAtLeast(margin))
        val panel = RectF(left, top, left + panelWidth, top + panelHeight)

        canvas.drawRoundRect(panel, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(165, 0, 0, 0)
        })
        // Teal accent bar.
        val accent = RectF(panel.left, panel.top, panel.left + 8f * scale, panel.bottom)
        canvas.drawRoundRect(accent, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00BFA5")
        })

        val textLeft = panel.left + padding + 8f * scale
        var y = panel.top + padding + titleSize

        val dotRadius = titleSize * 0.28f
        canvas.drawCircle(
            textLeft + dotRadius, y - titleSize * 0.32f, dotRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFB300") }
        )
        canvas.drawText(title, textLeft + dotRadius * 2 + 10f * scale, y, titlePaint)
        y += titleGap
        for (line in lines) {
            canvas.drawText(line, textLeft, y, bodyPaint)
            y += lineGap
        }

        if (mapBitmap != null) {
            val mapLeft = panel.right - padding - mapSize
            val mapTop = panel.top + (panelHeight - mapSize) / 2f
            val mapRect = RectF(mapLeft, mapTop, mapLeft + mapSize, mapTop + mapSize)
            val clip = Path().apply { addRoundRect(mapRect, 12f * scale, 12f * scale, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawBitmap(
                mapBitmap,
                null,
                mapRect,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            )
            canvas.restore()
            canvas.drawRoundRect(mapRect, 12f * scale, 12f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f * scale
                color = Color.parseColor("#00BFA5")
            })
        }
        return output
    }
}
