package com.apollof.protocoltracker.ui.levels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import com.apollof.protocoltracker.domain.pk.GroupSeries
import com.apollof.protocoltracker.domain.units.formatNumber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val dayLabel = DateTimeFormatter.ofPattern("d MMM")
private val tooltipLabel = DateTimeFormatter.ofPattern("EEE d MMM HH:mm")

/** Rounds up to 1, 2, 2.5 or 5 × 10ⁿ so axis labels are readable. */
private fun niceCeil(v: Double): Double {
    if (v <= 0) return 1.0
    val exp = 10.0.pow(floor(log10(v)))
    val f = v / exp
    val nice = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it >= f - 1e-9 }
    return nice * exp
}

/**
 * Level curve with phase bands, dose ticks, a now marker and tap-to-read values.
 * Drag pans, pinch zooms; logged history is solid, planned future dashed.
 */
@Composable
fun LevelChart(
    series: GroupSeries,
    fromMs: Long,
    toMs: Long,
    nowMs: Long,
    bands: List<PhaseBand>,
    onPan: (Float) -> Unit,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val t = Tracker.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TrackerType.micro.copy(color = t.muted)
    val line = t.series(series.colorArgb)
    val zone = remember { ZoneId.systemDefault() }
    var selected by remember(series, fromMs, toMs) { mutableStateOf<Int?>(null) }
    val values = series.series.values
    val times = series.series.times
    val yMax = niceCeil((values.maxOrNull() ?: 0.0) * 1.08)
    val unit = series.scale.label
    val now = values.indices.minByOrNull { kotlin.math.abs(times[it] - nowMs) }
    val currentOnPan by rememberUpdatedState(onPan)
    val currentOnZoom by rememberUpdatedState(onZoom)
    val currentWindow by rememberUpdatedState(fromMs to toMs)
    val currentTimes by rememberUpdatedState(times)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics {
                contentDescription = "${series.group} estimated level chart. " +
                    (now?.let { "Now ${formatNumber(values[it], 1)} $unit. " } ?: "") + "Peak in view ${formatNumber(values.maxOrNull() ?: 0.0, 1)} $unit."
            }
            // Keyed on Unit: the window changes on every pan step and must not restart an ongoing gesture.
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (pan.x != 0f) currentOnPan(-pan.x / size.width)
                    if (zoom != 1f) currentOnZoom(zoom)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val left = 44.dp.toPx()
                    val (start, end) = currentWindow
                    val t = start + ((pos.x - left) / (size.width - left) * (end - start)).toLong()
                    val ts = currentTimes
                    selected = ts.indices.minByOrNull { kotlin.math.abs(ts[it] - t) }
                }
            },
    ) {
        val left = 44.dp.toPx()
        val bottom = 20.dp.toPx()
        val top = 8.dp.toPx()
        val plotW = size.width - left
        val plotH = size.height - bottom - top
        fun x(t: Long) = left + ((t - fromMs).toDouble() / (toMs - fromMs) * plotW).toFloat()
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

        // X day labels at a readable step.
        val days = (toMs - fromMs) / 86_400_000.0
        val step = listOf(1, 2, 3, 7, 14, 30, 61).first { days / it <= 6 }.toLong()
        var day = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate().plusDays(1)
        while (day.toEpochDay() % step != 0L) day = day.plusDays(1)
        while (true) {
            val t = day.atStartOfDay(zone).toInstant().toEpochMilli()
            if (t > toMs) break
            val xx = x(t)
            drawLine(colors.outlineVariant.copy(alpha = 0.5f), Offset(xx, top), Offset(xx, top + plotH), strokeWidth = 1f)
            val layout = measurer.measure(day.format(dayLabel), labelStyle)
            if (xx + layout.size.width / 2 < size.width && xx - layout.size.width / 2 > left) {
                drawText(layout, topLeft = Offset(xx - layout.size.width / 2, size.height - layout.size.height))
            }
            day = day.plusDays(step)
        }

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

            // Now marker.
            if (nowMs in fromMs..toMs) {
                drawLine(colors.onSurface.copy(alpha = 0.6f), Offset(x(nowMs), top), Offset(x(nowMs), top + plotH), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            }

            selected?.takeIf { it in values.indices }?.let { i -> tooltip(i, times, values, ::x, ::y, line, unit, zone, colors.inverseSurface, colors.inverseOnSurface, measurer, plotH + top) }
        }
    }
}

private fun DrawScope.tooltip(
    i: Int, times: LongArray, values: DoubleArray, x: (Long) -> Float, y: (Double) -> Float, color: Color, unit: String, zone: ZoneId,
    bg: Color, fg: Color, measurer: androidx.compose.ui.text.TextMeasurer, plotBottom: Float,
) {
    val px = x(times[i]); val py = y(values[i])
    drawLine(color.copy(alpha = 0.5f), Offset(px, 0f), Offset(px, plotBottom), strokeWidth = 1.dp.toPx())
    drawCircle(color, 5.dp.toPx(), Offset(px, py))
    val text = "${formatNumber(values[i], 1)} $unit · ${Instant.ofEpochMilli(times[i]).atZone(zone).format(tooltipLabel)}"
    val layout = measurer.measure(text, NumericStyle.copy(fontSize = 12.sp, color = fg))
    val pad = 6.dp.toPx()
    val w = layout.size.width + pad * 2; val h = layout.size.height + pad * 2
    val bx = (px - w / 2).coerceIn(0f, size.width - w)
    val by = (py - h - 10.dp.toPx()).coerceAtLeast(0f)
    drawRoundRect(bg, Offset(bx, by), Size(w, h), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
    drawText(layout, topLeft = Offset(bx + pad, by + pad))
}
