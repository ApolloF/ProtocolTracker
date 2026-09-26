package com.apollof.protocoltracker.ui.levels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollof.protocoltracker.domain.pk.CompareSeries
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** Dash pattern per line, so lines differ by more than colour. Index 0 is solid. */
internal fun comparePattern(index: Int): FloatArray? = when (index % 4) {
    0 -> null
    1 -> floatArrayOf(18f, 10f)
    2 -> floatArrayOf(4f, 8f)
    else -> floatArrayOf(18f, 8f, 4f, 8f)
}

private fun Double.pct() = "${formatNumber(this, 0)}%"

/**
 * Several groups on one percentage axis with a dashed 100% line. Each line has its own dash pattern
 * (see [comparePattern]); tapping shows every line's percentage and estimate at that time.
 */
@Composable
fun CompareChart(
    series: List<CompareSeries>,
    fromMs: Long,
    toMs: Long,
    nowMs: Long,
    bands: List<PhaseBand>,
    onPan: (Float) -> Unit,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = Tracker.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TrackerType.micro.copy(color = t.muted)
    val zone = remember { ZoneId.systemDefault() }
    var selectedAt by remember(series, fromMs, toMs) { mutableStateOf<Long?>(null) }
    val maxPercent = series.maxOfOrNull { s -> s.percent.maxOrNull() ?: 0.0 } ?: 0.0
    // Steps of 25% keep the 100% line high on the chart.
    val yMax = kotlin.math.ceil(maxOf(maxPercent, 100.0) * 1.05 / 25) * 25
    val currentOnPan by rememberUpdatedState(onPan)
    val currentOnZoom by rememberUpdatedState(onZoom)
    val currentWindow by rememberUpdatedState(fromMs to toMs)

    fun valueAt(s: CompareSeries, at: Long): Int? = s.times.indices.minByOrNull { abs(s.times[it] - at) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(280.dp)
            .semantics {
                contentDescription = "Compare chart, percent of reference. " + series.joinToString(" ") { s ->
                    valueAt(s, nowMs)?.let { "${s.group} now ${s.percent[it].pct()}." } ?: ""
                }
            }
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
                    selectedAt = start + ((pos.x - left) / (size.width - left) * (end - start)).toLong()
                }
            },
    ) {
        val left = 44.dp.toPx()
        val bottom = 20.dp.toPx()
        val top = 8.dp.toPx()
        val plotW = size.width - left
        val plotH = size.height - bottom - top
        fun x(ms: Long) = left + ((ms - fromMs).toDouble() / (toMs - fromMs) * plotW).toFloat()
        fun y(v: Double) = top + (plotH * (1 - v / yMax)).toFloat()

        bands.forEach { b ->
            val x0 = x(maxOf(b.startMs, fromMs)); val x1 = x(minOf(b.endMs, toMs))
            drawRect(t.series(b.colorArgb).copy(alpha = 0.08f), Offset(x0, top), Size(x1 - x0, plotH))
        }
        // Gridlines at round percentages (25% steps, wider when the axis is tall).
        val gridStep = listOf(25.0, 50.0, 100.0, 250.0).first { yMax / it <= 6 }
        var v = 0.0
        while (v <= yMax + 1e-6) {
            val yy = y(v)
            drawLine(t.line2, Offset(left, yy), Offset(size.width, yy), strokeWidth = 1f)
            val layout = measurer.measure(v.pct(), labelStyle)
            drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), yy - layout.size.height / 2))
            v += gridStep
        }
        // Reference line at 100%.
        drawLine(t.accentMid, Offset(left, y(100.0)), Offset(size.width, y(100.0)), strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))

        val days = (toMs - fromMs) / 86_400_000.0
        val step = listOf(1, 2, 3, 7, 14, 30, 61).first { days / it <= 6 }.toLong()
        var day = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate().plusDays(1)
        while (day.toEpochDay() % step != 0L) day = day.plusDays(1)
        while (true) {
            val ms = day.atStartOfDay(zone).toInstant().toEpochMilli()
            if (ms > toMs) break
            val layout = measurer.measure(day.format(dayLabel), labelStyle)
            val xx = x(ms)
            if (xx + layout.size.width / 2 < size.width && xx - layout.size.width / 2 > left) {
                drawText(layout, topLeft = Offset(xx - layout.size.width / 2, size.height - layout.size.height))
            }
            day = day.plusDays(step)
        }

        clipRect(left, 0f, size.width, size.height) {
            series.forEachIndexed { index, s ->
                val path = Path()
                for (i in s.times.indices) {
                    val px = x(s.times[i]); val py = y(s.percent[i])
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(
                    path, t.series(s.colorArgb),
                    style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = comparePattern(index)?.let { PathEffect.dashPathEffect(it) }),
                )
            }
            if (nowMs in fromMs..toMs) {
                drawLine(t.ink.copy(alpha = 0.6f), Offset(x(nowMs), top), Offset(x(nowMs), top + plotH), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            }
            selectedAt?.let { at ->
                val px = x(at)
                drawLine(t.ink.copy(alpha = 0.4f), Offset(px, top), Offset(px, top + plotH), strokeWidth = 1.dp.toPx())
                val lines = series.mapNotNull { s ->
                    val i = valueAt(s, at) ?: return@mapNotNull null
                    drawCircle(t.series(s.colorArgb), 4.dp.toPx(), Offset(x(s.times[i]), y(s.percent[i])))
                    "${s.group}: ${s.percent[i].pct()} · ${formatNumber(s.raw[i], 1)} ${s.unit}"
                }
                val text = (listOf(Instant.ofEpochMilli(at).atZone(zone).format(tooltipLabel)) + lines).joinToString("\n")
                val layout = measurer.measure(text, NumericStyle.copy(fontSize = 12.sp, color = t.bg))
                val pad = 6.dp.toPx()
                val w = layout.size.width + pad * 2; val h = layout.size.height + pad * 2
                val bx = (px + 8.dp.toPx()).let { if (it + w > size.width) px - w - 8.dp.toPx() else it }.coerceAtLeast(left)
                drawRoundRect(t.ink, Offset(bx, top), Size(w, h), CornerRadius(8.dp.toPx()))
                drawText(layout, topLeft = Offset(bx + pad, top + pad))
            }
        }
    }
}

/** Legend swatch: a short line in the series colour and dash pattern. */
@Composable
fun CompareSwatch(colorArgb: Long, index: Int) {
    val color: Color = Tracker.colors.series(colorArgb)
    Canvas(Modifier.size(width = 28.dp, height = 12.dp)) {
        drawLine(
            color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = comparePattern(index)?.let { PathEffect.dashPathEffect(it.map { v -> v * 0.6f }.toFloatArray()) },
        )
    }
}
