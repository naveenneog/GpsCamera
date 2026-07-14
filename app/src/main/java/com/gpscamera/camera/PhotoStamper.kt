package com.gpscamera.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.gpscamera.util.GpsFormat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Burns the reference-style GPS Map Camera panel onto a photo. */
object PhotoStamper {

    private const val BASELINE_SHORT_EDGE = 1080f
    private const val PORTRAIT_PANEL_WIDTH_UNITS = 980f
    private const val LANDSCAPE_PANEL_WIDTH_UNITS = 920f

    fun stamp(
        src: Bitmap,
        details: GpsFormat.StampDetails,
        mapBitmap: Bitmap? = null,
        anchorX: Float = 0.5f,
        anchorY: Float = 0.9f,
        userScale: Float = 1f
    ): Bitmap {
        val output = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val width = output.width.toFloat()
        val height = output.height.toFloat()
        val portrait = height >= width
        val rawScale =
            min(width, height) / BASELINE_SHORT_EDGE * userScale.coerceIn(0.6f, 2f)
        val panelWidthUnits =
            if (portrait) PORTRAIT_PANEL_WIDTH_UNITS else LANDSCAPE_PANEL_WIDTH_UNITS
        val outerMargin = 18f * rawScale
        val panelWidth = min(panelWidthUnits * rawScale, width - outerMargin * 2f)
        val scale = panelWidth / panelWidthUnits

        val padding = 18f * scale
        val mapGap = 18f * scale
        val panelRadius = 18f * scale
        val panelHeight = 280f * scale
        val contentHeight = panelHeight - padding * 2f
        val mapSize = min(contentHeight, panelWidth * 0.31f)
        val margin = 16f * scale
        val centerX = anchorX * width
        val centerY = anchorY * height
        val panelLeft = (centerX - panelWidth / 2f)
            .coerceIn(margin, (width - margin - panelWidth).coerceAtLeast(margin))
        val panelTop = (centerY - panelHeight / 2f)
            .coerceIn(margin, (height - margin - panelHeight).coerceAtLeast(margin))
        val panel = RectF(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight)

        canvas.drawRoundRect(panel, panelRadius, panelRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(172, 30, 32, 34)
        })

        val mapRect = RectF(
            panel.left + padding,
            panel.top + padding,
            panel.left + padding + mapSize,
            panel.bottom - padding
        )
        drawMap(canvas, mapRect, mapBitmap, scale)

        val textLeft = mapRect.right + mapGap
        val textRight = panel.right - padding
        val textWidth = (textRight - textLeft).coerceAtLeast(1f)
        drawDetails(canvas, details, textLeft, textRight, panel.top + padding, textWidth, scale)
        return output
    }

    /** Compatibility overload for simple callers and legacy tests. */
    fun stamp(
        src: Bitmap,
        lines: List<String>,
        mapBitmap: Bitmap? = null,
        anchorX: Float = 0.5f,
        anchorY: Float = 0.9f,
        userScale: Float = 1f,
        title: String = "GPS Camera"
    ): Bitmap = stamp(
        src = src,
        details = GpsFormat.StampDetails(
            localityLine = lines.firstOrNull() ?: title,
            fullAddress = lines.getOrNull(1),
            coordinateLine = lines.getOrNull(2).orEmpty(),
            dateTimeLine = lines.getOrNull(3).orEmpty(),
            countryCode = null,
            temperatureC = null
        ),
        mapBitmap = mapBitmap,
        anchorX = anchorX,
        anchorY = anchorY,
        userScale = userScale
    )

    private fun drawDetails(
        canvas: Canvas,
        details: GpsFormat.StampDetails,
        left: Float,
        right: Float,
        top: Float,
        width: Float,
        scale: Float
    ) {
        val localityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 23f * scale
            typeface = Typeface.DEFAULT
        }
        val notePaint = Paint(bodyPaint).apply {
            color = Color.rgb(238, 238, 238)
            textSize = 21f * scale
        }
        val lineHeight = 33f * scale
        val localityBaseline = top + localityPaint.textSize
        val iconRowWidth = drawTopRightBadges(canvas, details, right, top, scale)
        drawEllipsizedText(
            canvas,
            details.localityLine,
            left,
            localityBaseline,
            (width - iconRowWidth - 10f * scale).coerceAtLeast(width * 0.42f),
            localityPaint
        )

        var y = localityBaseline + 14f * scale
        if (details.fullAddress != null) {
            y = drawWrappedText(
                canvas = canvas,
                text = details.fullAddress,
                x = left,
                firstBaseline = y + bodyPaint.textSize,
                maxWidth = width,
                paint = bodyPaint,
                lineHeight = lineHeight,
                maxLines = 2
            ) + 4f * scale
        } else {
            y += bodyPaint.textSize
        }
        drawEllipsizedText(canvas, details.coordinateLine, left, y, width, bodyPaint)
        y += lineHeight
        drawEllipsizedText(canvas, details.dateTimeLine, left, y, width, bodyPaint)
        y += lineHeight
        drawEllipsizedText(canvas, details.noteLine, left, y, width, notePaint)
    }

    private fun drawTopRightBadges(
        canvas: Canvas,
        details: GpsFormat.StampDetails,
        right: Float,
        top: Float,
        scale: Float
    ): Float {
        val iconSize = 28f * scale
        val flagWidth = 38f * scale
        val gap = 6f * scale
        val weatherWidth = 76f * scale
        val totalWidth = iconSize + flagWidth + weatherWidth + gap * 2f
        var x = right - totalWidth

        drawAppLogo(canvas, RectF(x, top, x + iconSize, top + iconSize), scale)
        x += iconSize + gap
        drawFlag(
            canvas,
            RectF(x, top + 3f * scale, x + flagWidth, top + iconSize - 3f * scale),
            details.countryCode,
            scale
        )
        x += flagWidth + gap
        drawWeatherChip(
            canvas,
            RectF(x, top, x + weatherWidth, top + iconSize),
            GpsFormat.formatTemperature(details.temperatureC),
            scale
        )
        return totalWidth
    }

    private fun drawMap(
        canvas: Canvas,
        rect: RectF,
        mapBitmap: Bitmap?,
        scale: Float
    ) {
        val radius = 12f * scale
        val clip = Path().apply {
            addRoundRect(rect, radius, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        if (mapBitmap != null) {
            canvas.drawBitmap(
                mapBitmap,
                null,
                rect,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            )
        } else {
            canvas.drawColor(Color.rgb(235, 233, 225))
            val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 5f * scale
            }
            canvas.drawLine(rect.left, rect.centerY(), rect.right, rect.top + rect.height() * 0.35f, roadPaint)
            canvas.drawLine(rect.left + rect.width() * 0.3f, rect.bottom, rect.centerX(), rect.top, roadPaint)
        }
        canvas.restore()
        canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * scale
        })
        drawMapsTag(canvas, rect, scale)
    }

    private fun drawMapsTag(canvas: Canvas, mapRect: RectF, scale: Float) {
        val tagWidth = 74f * scale
        val tagHeight = 29f * scale
        val tag = RectF(
            mapRect.left + 7f * scale,
            mapRect.bottom - tagHeight - 7f * scale,
            mapRect.left + tagWidth,
            mapRect.bottom - 7f * scale
        )
        canvas.drawRoundRect(tag, 7f * scale, 7f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 250, 250, 248)
        })
        val pinX = tag.left + 14f * scale
        val pinY = tag.centerY() - 2f * scale
        canvas.drawCircle(pinX, pinY, 5f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(46, 112, 220)
        })
        canvas.drawText(
            "Maps",
            tag.left + 25f * scale,
            tag.centerY() + 7f * scale,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(45, 45, 45)
                textSize = 17f * scale
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        )
    }

    private fun drawAppLogo(canvas: Canvas, rect: RectF, scale: Float) {
        canvas.drawRoundRect(rect, 7f * scale, 7f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 150, 136)
        })
        val body = RectF(
            rect.left + 5f * scale,
            rect.top + 8f * scale,
            rect.right - 5f * scale,
            rect.bottom - 6f * scale
        )
        canvas.drawRoundRect(body, 3f * scale, 3f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
        })
        canvas.drawCircle(rect.centerX(), body.centerY(), 4f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 179, 0)
        })
    }

    private fun drawFlag(
        canvas: Canvas,
        rect: RectF,
        countryCode: String?,
        scale: Float
    ) {
        val radius = 3f * scale
        canvas.save()
        canvas.clipPath(Path().apply {
            addRoundRect(rect, radius, radius, Path.Direction.CW)
        })
        if (countryCode.equals("IN", ignoreCase = true)) {
            val stripeHeight = rect.height() / 3f
            canvas.drawRect(rect.left, rect.top, rect.right, rect.top + stripeHeight, Paint().apply {
                color = Color.rgb(255, 153, 51)
            })
            canvas.drawRect(rect.left, rect.top + stripeHeight, rect.right, rect.top + stripeHeight * 2f, Paint().apply {
                color = Color.WHITE
            })
            canvas.drawRect(rect.left, rect.top + stripeHeight * 2f, rect.right, rect.bottom, Paint().apply {
                color = Color.rgb(19, 136, 8)
            })
            canvas.drawCircle(rect.centerX(), rect.centerY(), 4f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0, 0, 128)
                style = Paint.Style.STROKE
                strokeWidth = 1.2f * scale
            })
        } else {
            canvas.drawColor(Color.rgb(75, 82, 90))
            canvas.drawText(
                countryCode?.uppercase()?.take(2) ?: "GPS",
                rect.centerX(),
                rect.centerY() + 5f * scale,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 12f * scale
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                }
            )
        }
        canvas.restore()
    }

    private fun drawWeatherChip(
        canvas: Canvas,
        rect: RectF,
        temperature: String,
        scale: Float
    ) {
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 70, 74, 78)
        })
        val sunX = rect.left + 15f * scale
        val sunY = rect.centerY()
        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 193, 7)
            strokeWidth = 1.5f * scale
        }
        canvas.drawCircle(sunX, sunY, 5f * scale, sunPaint)
        repeat(8) { index ->
            val angle = Math.PI * 2.0 * index / 8.0
            canvas.drawLine(
                sunX + cos(angle).toFloat() * 7f * scale,
                sunY + sin(angle).toFloat() * 7f * scale,
                sunX + cos(angle).toFloat() * 10f * scale,
                sunY + sin(angle).toFloat() * 10f * scale,
                sunPaint
            )
        }
        canvas.drawText(
            temperature,
            rect.left + 29f * scale,
            rect.centerY() + 6f * scale,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 17f * scale
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        )
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        firstBaseline: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float,
        maxLines: Int
    ): Float {
        val words = text.trim().split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        val visible = lines.take(maxLines).toMutableList()
        if (lines.size > maxLines && visible.isNotEmpty()) {
            visible[visible.lastIndex] = ellipsize(visible.last() + " " + lines.drop(maxLines).joinToString(" "), maxWidth, paint)
        }
        var baseline = firstBaseline
        visible.forEach {
            canvas.drawText(ellipsize(it, maxWidth, paint), x, baseline, paint)
            baseline += lineHeight
        }
        return baseline
    }

    private fun drawEllipsizedText(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        maxWidth: Float,
        paint: Paint
    ) {
        if (text.isNotBlank()) canvas.drawText(ellipsize(text, maxWidth, paint), x, baseline, paint)
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        val suffix = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end).trimEnd() + suffix) > maxWidth) {
            end--
        }
        return text.substring(0, end).trimEnd() + suffix
    }
}
