package com.andrives.geosnap_cam.media

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.andrives.geosnap_cam.data.model.LocationData
import com.andrives.geosnap_cam.data.model.WatermarkConfig
import com.andrives.geosnap_cam.data.model.WatermarkTemplateType
import com.andrives.geosnap_cam.util.FlagEmoji
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min
import android.util.SizeF

/**
 * WatermarkRenderer — Renders the GPS watermark overlay using Android Canvas.
 *
 * This is the pixel-perfect Kotlin equivalent of Flutter's WatermarkPainter (852 lines).
 * Uses Android's Canvas, Paint, TextPaint and StaticLayout for text measurement/rendering.
 */
class WatermarkRenderer(
    private val location: LocationData?,
    private val config: WatermarkConfig,
    private val date: Date = Date(),
    private val mapImage: Bitmap? = null,
    private val canvasWidth: Float = DEFAULT_CANVAS_WIDTH,
) {
    companion object {
        const val DEFAULT_CANVAS_WIDTH = 760f
        internal const val PADDING = 22f
        internal const val HORIZONTAL_GAP = 18f
        internal const val MAP_WIDTH = 176f
        private const val GOOGLE_MULTICOLOR = 0
        private val GOOGLE_COLORS = intArrayOf(
            0xFF4285F4.toInt(),
            0xFFEA4335.toInt(),
            0xFFFBBC05.toInt(),
            0xFF4285F4.toInt(),
            0xFF34A853.toInt(),
            0xFFEA4335.toInt(),
        )

        /**
         * Measure the size of the watermark without actually rendering it.
         */
        fun measureSize(
            location: LocationData?,
            config: WatermarkConfig,
            date: Date = Date(),
            canvasWidth: Float = DEFAULT_CANVAS_WIDTH,
        ): SizeF {
            val m = measure(location, config, date, canvasWidth)
            return SizeF(m.actualTotalWidth, m.totalHeight)
        }
    }

    /**
     * Render the watermark to a new Bitmap at the given scale.
     */
    fun renderToBitmap(scale: Float = 3f): Bitmap {
        val measured = measure(location, config, date, canvasWidth)
        val width = (measured.actualTotalWidth * scale).toInt().coerceIn(1, 8192)
        val height = (measured.totalHeight * scale).toInt().coerceIn(1, 8192)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        paint(canvas, measured.actualTotalWidth, measured.totalHeight)
        return bitmap
    }

    fun paint(canvas: Canvas, width: Float, height: Float) {
        val measured = measure(location, config, date, canvasWidth)

        val offsetX = (width - measured.actualTotalWidth) / 2f
        if (offsetX > 0) {
            canvas.translate(offsetX, 0f)
        }

        val isPill = config.template == WatermarkTemplateType.PILL
        val isCinema = config.template == WatermarkTemplateType.CINEMA
        val isCrystal = config.template == WatermarkTemplateType.CRYSTAL

        val textContainerX = measured.mapWidth + HORIZONTAL_GAP
        val textContainerWidth = measured.contentWidth + PADDING * 2
        val textContainerRect = RectF(
            textContainerX, 0f,
            textContainerX + textContainerWidth, measured.totalHeight
        )
        val cornerRadius = 24f

        // ── Draw text container background ──
        when {
            isPill -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFDFDFD.toInt()
                    style = Paint.Style.FILL
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFE0E0E0.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                }
                canvas.drawRoundRect(textContainerRect, cornerRadius, cornerRadius, bgPaint)
                canvas.drawRoundRect(textContainerRect, cornerRadius, cornerRadius, borderPaint)
            }
            isCinema -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF0A0A0A.toInt()
                    style = Paint.Style.FILL
                }
                val redGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        textContainerX + textContainerWidth / 2, measured.totalHeight / 2,
                        textContainerWidth * 0.8f,
                        intArrayOf(0x33E50914, 0x00E50914),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    style = Paint.Style.FILL
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = alphaColor(0xFFE50914.toInt(), 0.6f)
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
                canvas.drawRoundRect(textContainerRect, cornerRadius, cornerRadius, bgPaint)
                canvas.drawRoundRect(textContainerRect, cornerRadius, cornerRadius, redGlow)
                canvas.drawRoundRect(textContainerRect, cornerRadius, cornerRadius, borderPaint)
            }
            else -> {
                // Crystal glass
                val glassColor = config.glassColorValue
                val glassAlpha = config.glassOpacity.coerceIn(0.0, 1.0).toFloat()

                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        textContainerX, 0f,
                        textContainerX, measured.totalHeight,
                        intArrayOf(
                            alphaColor(glassColor, glassAlpha * 0.62f),
                            alphaColor(Color.BLACK, glassAlpha * 0.72f),
                            alphaColor(glassColor, glassAlpha * 0.36f),
                        ),
                        floatArrayOf(0f, 0.52f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    style = Paint.Style.FILL
                }

                val waterGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        textContainerX, 0f,
                        textContainerX + textContainerWidth, measured.totalHeight,
                        intArrayOf(
                            alphaColor(Color.WHITE, glassAlpha * 0.20f),
                            alphaColor(glassColor, glassAlpha * 0.12f),
                            Color.TRANSPARENT,
                        ),
                        floatArrayOf(0f, 0.36f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    style = Paint.Style.FILL
                }

                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = alphaColor(Color.WHITE, glassAlpha * 0.22f)
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                }

                canvas.drawRoundRect(textContainerRect, cornerRadius, cornerRadius, bgPaint)
                canvas.drawRoundRect(textContainerRect, cornerRadius, cornerRadius, waterGlowPaint)
                canvas.drawRoundRect(textContainerRect, cornerRadius, cornerRadius, borderPaint)
            }
        }

        // ── Draw map ──
        val mapRect = RectF(0f, 0f, measured.mapWidth, measured.totalHeight)
        drawMap(canvas, mapRect, isCinema)

        // ── Draw text content ──
        val contentX = PADDING + measured.mapWidth + HORIZONTAL_GAP
        val textMaxWidth = measured.contentWidth
        var y = PADDING

        // Title
        y = paintParagraph(
            canvas, measured.title,
            if (isPill) withColor(measured.titlePaint, Color.BLACK) else measured.titlePaint,
            contentX, y, textMaxWidth, isPill, null
        )

        // Title divider
        y += measured.titleDividerGap
        val dividerPaint = Paint().apply {
            color = if (isPill) alphaColor(Color.BLACK, 0.05f) else alphaColor(Color.WHITE, 0.18f)
            strokeWidth = 1f
        }
        if (!isCrystal && !isPill) {
            canvas.drawLine(contentX, y, contentX + textMaxWidth, y, dividerPaint)
        }
        y += measured.titleDividerGap

        val bodyPaint = if (isPill) withColor(measured.bodyPaint, alphaColor(Color.BLACK, 0.87f))
        else measured.bodyPaint

        // Address
        if (config.showAddress) {
            y = paintParagraph(
                canvas, measured.addressLine,
                bodyPaint, contentX, y, textMaxWidth, isPill, 0xFFFFEBEB.toInt()
            )
            y += measured.lineGap
        }

        // Coordinates
        if (config.showCityCoords) {
            y = paintParagraph(
                canvas, measured.coordsLine,
                bodyPaint, contentX, y, textMaxWidth, isPill, 0xFFFFEBEB.toInt()
            )
            y += measured.lineGap
        }

        // Date
        if (config.showDate) {
            paintParagraph(
                canvas, measured.dateLine,
                bodyPaint, contentX, y, textMaxWidth, isPill, 0xFFE3F2FD.toInt()
            )
        }

        // ── Weather metrics row ──
        if (config.showWeather) {
            val weatherTop = PADDING + measured.innerHeight - measured.weatherRowHeight
            if (!isCrystal && !isPill) {
                canvas.drawLine(
                    contentX, weatherTop - measured.weatherDividerGap,
                    contentX + textMaxWidth, weatherTop - measured.weatherDividerGap,
                    dividerPaint
                )
            }
            drawMetricRow(canvas, contentX, weatherTop, textMaxWidth, measured, isPill)
        }
    }

    // ── Map drawing ──────────────────────────────────────────────────────────

    private fun drawMap(canvas: Canvas, rect: RectF, isCinema: Boolean) {
        canvas.save()
        val path = Path().apply {
            addRoundRect(rect, 16f, 16f, Path.Direction.CW)
        }
        canvas.clipPath(path)

        if (mapImage != null) {
            val src = Rect(0, 0, mapImage.width, mapImage.height)
            val dst = RectF(rect)
            canvas.drawBitmap(mapImage, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            // Fallback gradient grid
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    intArrayOf(0xFF4A545F.toInt(), 0xFF232B36.toInt()),
                    null, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(rect, fallbackPaint)

            val linePaint = Paint().apply {
                color = alphaColor(Color.WHITE, 0.25f)
                strokeWidth = 1f
            }
            for (i in 1..5) {
                val dx = rect.left + (rect.width() / 6) * i
                val dy = rect.top + (rect.height() / 6) * i
                canvas.drawLine(dx, rect.top, dx, rect.bottom, linePaint)
                canvas.drawLine(rect.left, dy, rect.right, dy, linePaint)
            }
        }

        // Map attribution
        drawMapAttribution(canvas, rect)

        // Center pin
        val cx = rect.centerX()
        val cy = rect.centerY()
        val pinColor = if (isCinema) 0xFFE50914.toInt() else 0xFFFF3B30.toInt()
        canvas.drawCircle(cx, cy, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pinColor })
        canvas.drawCircle(cx, cy, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

        canvas.restore()
    }

    private fun drawMapAttribution(canvas: Canvas, rect: RectF) {
        val color = config.mapAttributionColorValue
        val scale = config.mapAttributionScale.coerceIn(0.7, 2.2).toFloat()
        val outlineWidth = config.mapAttributionOutlineWidth.coerceIn(0.0, 4.0).toFloat()

        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f * scale
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            this.color = color
        }

        val text = "Google"
        val textX = rect.left + 8f + 5f * scale
        val textY = rect.bottom - 9f - 2f * scale - tp.descent()

        if (outlineWidth > 0) {
            val outlinePaint = TextPaint(tp).apply {
                style = Paint.Style.STROKE
                strokeWidth = outlineWidth * scale
                strokeJoin = Paint.Join.ROUND
                this.color = Color.BLACK
            }
            canvas.drawText(text, textX, textY, outlinePaint)
        }
        if (color == GOOGLE_MULTICOLOR) {
            drawGoogleMulticolor(canvas, textX, textY, tp)
        } else {
            canvas.drawText(text, textX, textY, tp)
        }
    }

    private fun drawGoogleMulticolor(canvas: Canvas, x: Float, y: Float, paint: TextPaint) {
        var letterX = x
        "Google".forEachIndexed { index, letter ->
            val value = letter.toString()
            paint.color = GOOGLE_COLORS[index]
            canvas.drawText(value, letterX, y, paint)
            letterX += paint.measureText(value)
        }
    }

    // ── Paragraph rendering ──────────────────────────────────────────────────

    private fun paintParagraph(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        maxWidth: Float,
        isPill: Boolean,
        pillColor: Int?,
    ): Float {
        val layout = parseParagraph(text, paint, maxWidth)

        val mainPaint = TextPaint(paint)
        val sl = createStaticLayout(layout.mainText, mainPaint, layout.textMaxWidth.toInt().coerceAtLeast(1))
        val textHeight = sl.height.toFloat()

        if (isPill && pillColor != null) {
            val totalWidth = layout.indent + sl.getLineWidth(0)
            val bgRect = RectF(x - 8, y - 4, x + totalWidth + 8, y + textHeight + 4)
            canvas.drawRoundRect(bgRect, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pillColor
            })
        }

        if (layout.leadingEmoji != null) {
            canvas.drawText(layout.leadingEmoji, x, y - paint.ascent(), paint)
        }

        canvas.save()
        canvas.translate(x + layout.indent, y)
        sl.draw(canvas)
        canvas.restore()

        return y + textHeight
    }

    private fun drawMetricRow(
        canvas: Canvas,
        contentX: Float,
        y: Float,
        maxWidth: Float,
        measured: MeasuredLayout,
        isPill: Boolean,
    ) {
        val metrics = listOf(
            "☁\uFE0F ${measured.temperatureLabel}",
            "\uD83D\uDCA8 ${measured.windLabel}",
            "☀\uFE0F UV ${measured.uvLabel}",
        )
        val pillColors = intArrayOf(
            0xFFE8F5E9.toInt(),
            0xFFE3F2FD.toInt(),
            0xFFFFF3E0.toInt(),
        )

        val eachWidth = maxWidth / 3f
        val metricPaint = if (isPill) withColor(measured.metricPaint, alphaColor(Color.BLACK, 0.87f))
        else measured.metricPaint

        for (i in metrics.indices) {
            val dx = contentX + eachWidth * i
            val label = metrics[i]
            val textWidth = metricPaint.measureText(label).coerceAtMost(eachWidth - 6)

            if (isPill) {
                val bgRect = RectF(dx - 6, y - 4, dx + textWidth + 6, y + measured.weatherRowHeight)
                canvas.drawRoundRect(bgRect, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = pillColors[i]
                })
            }

            canvas.drawText(label, dx, y - metricPaint.ascent(), metricPaint)
        }
    }

    // ── Text measurement helpers ─────────────────────────────────────────────

    private data class ParagraphLayout(
        val leadingEmoji: String?,
        val mainText: String,
        val indent: Float,
        val textMaxWidth: Float,
    )

    private fun parseParagraph(text: String, paint: TextPaint, maxWidth: Float): ParagraphLayout {
        val bulletEmojis = listOf("📍", "🎯", "🕒")
        for (emoji in bulletEmojis) {
            if (text.startsWith(emoji)) {
                val indent = (paint.textSize * 1.35f)
                return ParagraphLayout(
                    leadingEmoji = emoji,
                    mainText = text.removePrefix(emoji).trim(),
                    indent = indent,
                    textMaxWidth = (maxWidth - indent).coerceAtLeast(0f),
                )
            }
        }
        return ParagraphLayout(null, text, 0f, maxWidth)
    }

    @Suppress("DEPRECATION")
    private fun createStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return if (android.os.Build.VERSION.SDK_INT >= 23) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .build()
        } else {
            StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
        }
    }

    // ── Color utilities ──────────────────────────────────────────────────────

    private fun alphaColor(baseColor: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (baseColor and 0x00FFFFFF) or (a shl 24)
    }

    private fun withColor(source: TextPaint, newColor: Int): TextPaint {
        return TextPaint(source).apply { color = newColor }
    }
}

// ── Measurement data ─────────────────────────────────────────────────────────

private data class MeasuredLayout(
    val title: String,
    val addressLine: String,
    val coordsLine: String,
    val dateLine: String,
    val titlePaint: TextPaint,
    val bodyPaint: TextPaint,
    val metricPaint: TextPaint,
    val mapWidth: Float,
    val contentWidth: Float,
    val titleDividerGap: Float,
    val lineGap: Float,
    val weatherDividerGap: Float,
    val weatherRowHeight: Float,
    val innerHeight: Float,
    val totalHeight: Float,
    val actualTotalWidth: Float,
    val temperatureLabel: String,
    val windLabel: String,
    val uvLabel: String,
)

// ── Static measurement function ──────────────────────────────────────────────

private fun measure(
    location: LocationData?,
    config: WatermarkConfig,
    date: Date,
    maxWidth: Float,
): MeasuredLayout {
    val region = location?.region?.trim() ?: ""
    val city = location?.city?.trim() ?: ""
    val country = location?.country?.trim() ?: ""
    val countryCode = location?.countryCode?.trim() ?: ""

    val title = buildTitle(region, city, country, countryCode)
    val addressLine = buildAddressLine(location)
    val coordsLine = "🎯 Lat ${coordValue(location?.latitude)}, Long ${coordValue(location?.longitude)}"

    val cal = Calendar.getInstance().apply { time = date }
    val timezoneLabel = timezoneLabel(location?.timezone ?: "", cal.timeZone)
    val dateLine = "🕒 ${dateLabel(date, countryCode)}  $timezoneLabel"

    val titleS = config.titleScale.coerceIn(0.4, 1.6).toFloat()
    val s = config.textScale.coerceIn(0.4, 1.6).toFloat()
    val layoutS = max(titleS, s)
    val titleColor = config.titleColorValue
    val textColor = config.textColorValue

    val roboto = Typeface.create("sans-serif", Typeface.NORMAL)
    val robotoBold = Typeface.create("sans-serif", Typeface.BOLD)
    val robotoMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = titleColor
        textSize = 44f * titleS
        typeface = robotoBold
    }
    val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = alphaColorStatic(textColor, 0.94f)
        textSize = 28f * s
        typeface = robotoMedium
    }
    val metricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = 32f * s
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    val mapW = 176f
    val maxAllowedContentWidth = maxWidth - WatermarkRenderer.PADDING - mapW -
        WatermarkRenderer.HORIZONTAL_GAP - WatermarkRenderer.PADDING

    var maxTextWidth = 0f
    maxTextWidth = max(maxTextWidth, textWidth(title, titlePaint))
    if (config.showAddress) {
        maxTextWidth = max(maxTextWidth, textWidth(addressLine, bodyPaint))
    }
    if (config.showCityCoords) {
        maxTextWidth = max(maxTextWidth, textWidth(coordsLine, bodyPaint))
    }
    if (config.showDate) {
        maxTextWidth = max(maxTextWidth, textWidth(dateLine, bodyPaint))
    }

    val weatherDividerGap = if (config.showWeather) 10f * s else 0f
    val weatherWidth = textWidth("? --.- °C", metricPaint) * 3 + weatherDividerGap * 2
    if (config.showWeather) {
        maxTextWidth = max(maxTextWidth, weatherWidth)
    }

    val contentWidth = min(maxTextWidth, maxAllowedContentWidth)
    val actualTotalWidth = WatermarkRenderer.PADDING + mapW + WatermarkRenderer.HORIZONTAL_GAP +
        contentWidth + WatermarkRenderer.PADDING

    val isPill = config.template == WatermarkTemplateType.PILL
    val titleDividerGap = if (isPill) 14f * layoutS else 10f * layoutS
    val lineGap = if (isPill) 14f * s else 5f * s

    var textBlockHeight = 0f
    textBlockHeight += textHeight(title, titlePaint, contentWidth)
    textBlockHeight += (titleDividerGap * 2) + 1

    if (config.showAddress) {
        textBlockHeight += textHeight(addressLine, bodyPaint, contentWidth)
        textBlockHeight += lineGap
    }
    if (config.showCityCoords) {
        textBlockHeight += textHeight(coordsLine, bodyPaint, contentWidth)
        textBlockHeight += lineGap
    }
    if (config.showDate) {
        textBlockHeight += textHeight(dateLine, bodyPaint, contentWidth)
    }

    val weatherRowHeight = if (config.showWeather) {
        textHeight("? --.- °C", metricPaint, contentWidth / 3) + 2
    } else {
        0f
    }
    val weatherExtraHeight = if (config.showWeather) {
        (weatherDividerGap * 2) + weatherRowHeight + 4
    } else {
        0f
    }
    val rightMinHeight = textBlockHeight + weatherExtraHeight
    val innerHeight = max(190f * layoutS, rightMinHeight)
    val totalHeight = innerHeight + (WatermarkRenderer.PADDING * 2)

    return MeasuredLayout(
        title = title,
        addressLine = addressLine,
        coordsLine = coordsLine,
        dateLine = dateLine,
        titlePaint = titlePaint,
        bodyPaint = bodyPaint,
        metricPaint = metricPaint,
        mapWidth = mapW,
        contentWidth = contentWidth,
        titleDividerGap = titleDividerGap,
        lineGap = lineGap,
        weatherDividerGap = weatherDividerGap,
        weatherRowHeight = weatherRowHeight,
        innerHeight = innerHeight,
        totalHeight = totalHeight,
        actualTotalWidth = actualTotalWidth,
        temperatureLabel = temperatureLabel(location),
        windLabel = windLabel(location),
        uvLabel = uvLabel(location),
    )
}

// ── Text measurement ─────────────────────────────────────────────────────────

private fun textWidth(text: String, paint: TextPaint): Float {
    val bulletEmojis = listOf("📍", "🎯", "🕒")
    var indent = 0f
    var mainText = text
    for (emoji in bulletEmojis) {
        if (text.startsWith(emoji)) {
            indent = paint.textSize * 1.35f
            mainText = text.removePrefix(emoji).trim()
            break
        }
    }
    return indent + paint.measureText(mainText)
}

@Suppress("DEPRECATION")
private fun textHeight(text: String, paint: TextPaint, maxWidth: Float): Float {
    val bulletEmojis = listOf("📍", "🎯", "🕒")
    var effectiveMaxWidth = maxWidth
    var mainText = text
    for (emoji in bulletEmojis) {
        if (text.startsWith(emoji)) {
            val indent = paint.textSize * 1.35f
            effectiveMaxWidth = (maxWidth - indent).coerceAtLeast(0f)
            mainText = text.removePrefix(emoji).trim()
            break
        }
    }
    val width = effectiveMaxWidth.toInt().coerceAtLeast(1)
    val sl = if (android.os.Build.VERSION.SDK_INT >= 23) {
        StaticLayout.Builder.obtain(mainText, 0, mainText.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .build()
    } else {
        StaticLayout(mainText, paint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
    }
    return sl.height.toFloat()
}

// ── String builders ──────────────────────────────────────────────────────────

private fun buildTitle(region: String, city: String, country: String, countryCode: String): String {
    val parts = mutableListOf<String>()
    val counts = mutableMapOf<String, Int>()

    appendTitleParts(parts, counts, city)
    appendTitleParts(parts, counts, region)
    if (country.isNotEmpty()) parts.add(country)

    val fallback = if (parts.isEmpty()) "Ubicación GPS" else parts.joinToString(", ")
    val flag = FlagEmoji.fromCountryCode(countryCode)
    return if (flag.isEmpty()) fallback else "$fallback $flag"
}

private fun appendTitleParts(parts: MutableList<String>, counts: MutableMap<String, Int>, value: String) {
    for (rawPart in value.split(",")) {
        val part = rawPart.trim()
        if (part.isEmpty()) continue
        val key = part.lowercase()
        val count = counts.getOrDefault(key, 0)
        if (count >= 2) continue
        parts.add(part)
        counts[key] = count + 1
    }
}

private fun buildAddressLine(location: LocationData?): String {
    if (location == null) return "📍 Dirección no disponible"
    val parts = listOfNotNull(
        location.address.trim().ifEmpty { null },
        location.postalCode.trim().ifEmpty { null },
        location.city.trim().ifEmpty { null },
        location.country.trim().ifEmpty { null },
    )
    return if (parts.isEmpty()) "📍 Dirección no disponible" else "📍 ${parts.joinToString(", ")}"
}

private fun coordValue(value: Double?): String {
    return if (value == null) "--.------" else String.format(Locale.US, "%.6f", value)
}

private fun dateLabel(date: Date, countryCode: String): String {
    val englishCountries = setOf("US", "GB", "AU", "CA", "NZ", "IE")
    val isEnglish = englishCountries.contains(countryCode.trim().uppercase())

    val daysEs = arrayOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    val daysEn = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    val cal = Calendar.getInstance().apply { time = date }
    // Calendar.DAY_OF_WEEK: 1=Sunday, 2=Monday...
    val dayIndex = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7)  // Convert to 0=Monday
    val dayName = if (isEnglish) daysEn[dayIndex] else daysEs[dayIndex]

    val formatter = SimpleDateFormat("dd/MM/yyyy h:mm a", Locale.getDefault())
    return "$dayName, ${formatter.format(date)}"
}

private fun timezoneLabel(timezone: String, tz: TimeZone): String {
    val offsetMs = tz.rawOffset + tz.dstSavings
    val hours = offsetMs / 3600000
    val minutes = Math.abs((offsetMs % 3600000) / 60000)
    val sign = if (hours >= 0) "+" else "-"
    val gmt = String.format("GMT %s%02d:%02d", sign, Math.abs(hours), minutes)
    return if (timezone.trim().isEmpty()) gmt else "${timezone.trim()}  $gmt"
}

private fun temperatureLabel(location: LocationData?): String {
    val value = location?.temperatureC ?: return "--.- °C"
    return String.format(Locale.US, "%.1f °C", value)
}

private fun windLabel(location: LocationData?): String {
    val value = location?.windKmh ?: return "--.- km/h"
    return String.format(Locale.US, "%.1f km/h", value)
}

private fun uvLabel(location: LocationData?): String {
    val value = location?.uvIndex ?: return "--.-"
    return String.format(Locale.US, "%.1f", value)
}

private fun alphaColorStatic(baseColor: Int, alpha: Float): Int {
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    return (baseColor and 0x00FFFFFF) or (a shl 24)
}
