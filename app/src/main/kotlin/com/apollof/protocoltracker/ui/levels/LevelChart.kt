package com.apollof.protocoltracker.ui.levels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollof.protocoltracker.domain.pk.GroupSeries
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Left gutter of the charts, where the y labels are. */
internal val CHART_LEFT = 44.dp

/** Rounds up to 1, 2, 2.5 or 5 × 10ⁿ so axis labels are readable. */
internal fun niceCeil(v: Double): Double {
    if (v <= 0) return 1.0
    val exp = 10.0.pow(floor(log10(v)))
    val f = v / exp
    val nice = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it >= f - 1e-9 }
    return nice * exp
}

/** "Sat 26 Sep 14:00" in the chosen date order and clock. */
internal fun chartTime(ms: Long, zone: ZoneId): String {
    val at = Instant.ofEpochMilli(ms)
    return "${at.atZone(zone).format(Formats.dayShort)} ${Formats.time(at, zone)}"
}

/** A lab result drawn on a curve, already in the curve's unit. */
data class MeasuredPoint(val atMs: Long, val value: Double, val label: String)

/**
 * Level curve with phase bands, dose ticks, a now marker and a cursor that reads the value at a time.
 * Logged history is solid, planned future dashed. Lab results, when given, are drawn as open diamonds.
 * Gestures are described in [chartInput].
 */
@Composable
fun LevelChart(
    series: GroupSeries,
    fromMs: Long,
    toMs: Long,
    nowMs: Long,
    bands: List<PhaseBand>,
    cursorMs: Long?,
    scrub: Boolean,
    callbacks: ChartCallbacks,
    modifier: Modifier = Modifier,
    measured: List<MeasuredPoint> = emptyList(),
    height: Dp = 220.dp,
) {
    val colors = MaterialTheme.colorScheme
    val t = Tracker.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TrackerType.micro.copy(color = t.muted)
    val line = t.series(series.colorArgb)
    val zone = remember { ZoneId.systemDefault() }
    val values = series.series.values
    val times = series.series.times
    val shownMeasured = measured.filter { it.atMs in fromMs..toMs }
    val yMax = niceCeil(maxOf(values.maxOrNull() ?: 0.0, shownMeasured.maxOfOrNull { it.value } ?: 0.0) * 1.08)
    val unit = series.unitLabel
    val now = values.indices.minByOrNull { abs(times[it] - nowMs) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                contentDescription = "${series.group} estimated level chart. " +
                    (now?.let { "Now ${formatNumber(values[it], 1)} $unit. " } ?: "") + "Peak in view ${formatNumber(values.maxOrNull() ?: 0.0, 1)} $unit." +
                    if (shownMeasured.isNotEmpty()) " ${shownMeasured.size} lab results shown." else ""
            }
            .chartInput(scrub, CHART_LEFT, callbacks),
    ) {
        val left = CHART_LEFT.toPx()
        val bottom = 20.dp.toPx()
        val top = 8.dp.toPx()
        val plotW = size.width - left
        val plotH = size.height - bottom - top
        fun x(ms: Long) = left + ((ms - fromMs).toDouble() / (toMs - fromMs) * plotW).toFloat()
        fun y(v: Double) = top + (plotH * (1 - v / yMax)).toFloat()

        // Phase bands and names.
        bands.forEach { b ->
            val x0 = x(maxOf(b.startMs, fromMs)); val x1 = x(minOf(b.endMs, toMs))
            drawRect(t.series(b.colorArgb).copy(alpha = 0.08f), Offset(x0, top), Size(x1 - x0, plotH))
            if (x1 - x0 > 40.dp.toPx()) drawText(measurer, b.name, Offset(x0 + 4.dp.toPx(), top), labelStyle.copy(color = t.series(b.colorArgb)), maxLines = 1,
                size = Size(x1 - x0 - 8.dp.toPx(), 16.sp.toPx()))
        }

        // Horizontal grid + y labels.
        for (i in 0..4) {
            val v = yMax * i / 4
            val yy = y(v)
            drawLine(t.line2, Offset(left, yy), Offset(size.width, yy), strokeWidth = 1f)
            val text = formatNumber(v, if (yMax < 10) 1 else 0)
            val layout = measurer.measure(text, labelStyle)
            drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), yy - layout.size.height / 2))
        }

        dayAxis(fromMs, toMs, zone, left, top, plotH, measurer, labelStyle, colors.outlineVariant.copy(alpha = 0.5f), ::x)

        clipRect(left, 0f, size.width, size.height) {
            // Curve: solid up to now, dashed after (planned/projection).
            val past = Path(); val future = Path()
            var pastStarted = false; var futureStarted = false
            for (i in values.indices) {
                val px = x(times[i]); val py = y(values[i])
                if (times[i] <= nowMs) {
                    if (!pastStarted) { past.moveTo(px, py); pastStarted = true } else past.lineTo(px, py)
                } else {
                    if (!futureStarted) {
                        if (i > 0) future.moveTo(x(times[i - 1]), y(values[i - 1])) else future.moveTo(px, py)
                        futureStarted = true
                    }
                    future.lineTo(px, py)
                }
            }
            val stroke = 2.5.dp.toPx()
            drawPath(past, line, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(future, line.copy(alpha = 0.8f), style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))

            // Dose ticks along the bottom.
            series.events.forEach { e ->
                if (e.atMs in fromMs..toMs) {
                    val xx = x(e.atMs)
                    drawLine(line.copy(alpha = if (e.planned) 0.45f else 1f), Offset(xx, top + plotH), Offset(xx, top + plotH - 8.dp.toPx()), strokeWidth = 2.dp.toPx())
                }
            }

            // Lab results: open diamonds, so they read as measurements next to the estimate.
            shownMeasured.forEach { m -> diamond(Offset(x(m.atMs), y(m.value)), 6.dp.toPx(), t.ink, t.bg) }

            // Now marker.
            if (nowMs in fromMs..toMs) {
                drawLine(colors.onSurface.copy(alpha = 0.6f), Offset(x(nowMs), top), Offset(x(nowMs), top + plotH), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            }

            cursorMs?.takeIf { it in fromMs..toMs && values.isNotEmpty() }?.let { at ->
                val i = nearestIndex(times, at)
                val measuredHere = shownMeasured.firstOrNull { abs(x(it.atMs) - x(at)) < 8.dp.toPx() }
                val text = buildString {
                    append("${formatNumber(values[i], 1)} $unit · ${chartTime(times[i], zone)}")
                    measuredHere?.let { append("\n${it.label}") }
                }
                cursor(x(times[i]), y(values[i]), text, line, colors.inverseSurface, colors.inverseOnSurface, measurer, top + plotH)
            }
        }
    }
}

/** Index of the sample closest to [at]; [times] is sorted. */
internal fun nearestIndex(times: LongArray, at: Long): Int {
    var lo = 0
    var hi = times.size - 1
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (times[mid] < at) lo = mid + 1 else hi = mid
    }
    return if (lo > 0 && abs(times[lo - 1] - at) <= abs(times[lo] - at)) lo - 1 else lo
}

/** Day labels along the bottom at a readable step, with faint day lines. */
internal fun DrawScope.dayAxis(
    fromMs: Long, toMs: Long, zone: ZoneId, left: Float, top: Float, plotH: Float,
    measurer: TextMeasurer, labelStyle: androidx.compose.ui.text.TextStyle, gridColor: Color, x: (Long) -> Float,
) {
    val days = (toMs - fromMs) / 86_400_000.0
    val step = listOf(1, 2, 3, 7, 14, 30, 61).first { days / it <= 6 }.toLong()
    var day = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate().plusDays(1)
    while (day.toEpochDay() % step != 0L) day = day.plusDays(1)
    while (true) {
        val ms = day.atStartOfDay(zone).toInstant().toEpochMilli()
        if (ms > toMs) break
        val xx = x(ms)
        drawLine(gridColor, Offset(xx, top), Offset(xx, top + plotH), strokeWidth = 1f)
        val layout = measurer.measure(day.format(Formats.dayMonth), labelStyle)
        if (xx + layout.size.width / 2 < size.width && xx - layout.size.width / 2 > left) {
            drawText(layout, topLeft = Offset(xx - layout.size.width / 2, size.height - layout.size.height))
        }
        day = day.plusDays(step)
    }
}

private fun DrawScope.diamond(center: Offset, r: Float, stroke: Color, fill: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - r); lineTo(center.x + r, center.y); lineTo(center.x, center.y + r); lineTo(center.x - r, center.y); close()
    }
    drawPath(path, fill)
    drawPath(path, stroke, style = Stroke(2.dp.toPx()))
}

private fun DrawScope.cursor(
    px: Float, py: Float, text: String, color: Color, bg: Color, fg: Color, measurer: TextMeasurer, plotBottom: Float,
) {
    drawLine(color.copy(alpha = 0.5f), Offset(px, 0f), Offset(px, plotBottom), strokeWidth = 1.dp.toPx())
    drawCircle(color, 5.dp.toPx(), Offset(px, py))
    val layout = measurer.measure(text, NumericStyle.copy(fontSize = 12.sp, color = fg))
    val pad = 6.dp.toPx()
    val w = layout.size.width + pad * 2; val h = layout.size.height + pad * 2
    val bx = (px - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
    // Above the point when there is room, else below it, so the label never hides the curve under the finger.
    val above = py - h - 10.dp.toPx()
    val by = if (above >= 0f) above else (py + 10.dp.toPx()).coerceAtMost(plotBottom - h)
    drawRoundRect(bg, Offset(bx, by), Size(w, h), CornerRadius(8.dp.toPx()))
    drawText(layout, topLeft = Offset(bx + pad, by + pad))
}
