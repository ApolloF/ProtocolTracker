package com.apollof.protocoltracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apollof.protocoltracker.ui.levels.rememberScrubHaptics
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

enum class TrendLine { SOLID, DASHED, NONE }

/** Marks carry meaning by shape: diamonds for lab results, dots and rings for averages or ratings. */
enum class TrendMark { DIAMOND, DOT, RING }

data class TrendPoint(val at: Instant, val value: Double)

data class TrendSeries(val points: List<TrendPoint>, val line: TrendLine = TrendLine.SOLID, val mark: TrendMark = TrendMark.DOT)

/** Left gutter of a [TrendChart], where the y labels are. */
internal val TREND_LEFT = 40.dp

/** Space on both sides of the plot, so marks at the window's start and end are drawn whole. */
internal val TREND_INSET = 8.dp

/** How far beside a point a tap may land and still select it. */
internal val TREND_TAP_RADIUS = 24.dp

private val TREND_TOP = 8.dp
private val TREND_BOTTOM = 20.dp

/** Pixel x of every point time on a chart [width] wide. */
private fun Float.plotXs(times: List<Instant>, from: Instant, to: Instant, left: Float, inset: Float): List<Float> =
    times.map { TrendAxis.x(it, from, to, left + inset, this - inset) }

/**
 * A small time chart for bloodwork, blood pressure and mood. x runs by time from [from] to [to]; y covers the data
 * padded by 10 % and widened to [band], or the fixed [yRange]. Round y labels, the start and end dates.
 *
 * Static when [onSelect] is null: no gestures, so a page scrolls through it, and no cursor. Otherwise a tap selects
 * the point nearest in time within 24 dp, a sideways slide moves the selection point by point with a light tick, and
 * a vertical drag is left to the page; no pan or zoom, and the system back gesture is excluded. [selectedAt] draws a
 * cursor and a larger ring; the caller says what the point means. [description] is the content description.
 */
@Composable
fun TrendChart(
    series: List<TrendSeries>,
    from: Instant,
    to: Instant,
    description: String,
    modifier: Modifier = Modifier,
    selectedAt: Instant? = null,
    onSelect: ((Instant) -> Unit)? = null,
    band: TrendBand? = null,
    yRange: ClosedFloatingPointRange<Double>? = null,
    height: Dp = 160.dp,
) {
    val t = Tracker.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TrackerType.micro.copy(color = t.muted)
    val zone = remember { ZoneId.systemDefault() }
    val range = remember(series, band, yRange) { TrendAxis.yRange(series.flatMap { s -> s.points.map { it.value } }, band, yRange) }
    val ticks = remember(range) { TrendAxis.yTicks(range) }
    val times = remember(series) { series.flatMap { s -> s.points.map { it.at } }.distinct().sorted() }
    val dateFormat = if (from.atZone(zone).year == to.atZone(zone).year) Formats.dayMonth else Formats.date
    val startLabel = from.atZone(zone).format(dateFormat)
    val endLabel = to.atZone(zone).format(dateFormat)

    val input = if (onSelect == null) Modifier else {
        val select by rememberUpdatedState<(Instant) -> Unit>(onSelect)
        val selected by rememberUpdatedState(selectedAt)
        val haptics = rememberScrubHaptics()
        Modifier
            .systemGestureExclusion()
            .pointerInput(times, from, to) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val xs = size.width.toFloat().plotXs(times, from, to, TREND_LEFT.toPx(), TREND_INSET.toPx())
                    val slop = viewConfiguration.touchSlop
                    var sliding = false
                    var dx = 0f
                    var dy = 0f
                    var current = selected?.let { times.indexOf(it) }?.takeIf { it >= 0 }
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!sliding) TrendAxis.nearest(xs, down.position.x, TREND_TAP_RADIUS.toPx())?.let { select(times[it]) }
                            break
                        }
                        if (!sliding) {
                            // The page took the drag for scrolling.
                            if (change.isConsumed) break
                            val d = change.positionChange()
                            dx += d.x
                            dy += d.y
                            if (abs(dx) > slop && abs(dx) > abs(dy)) sliding = true
                            else if (abs(dy) > slop) break
                            else continue
                        }
                        change.consume()
                        val i = TrendAxis.nearest(xs, change.position.x) ?: continue
                        if (i != current) {
                            if (current != null) haptics.tick()
                            current = i
                            select(times[i])
                        }
                    }
                }
            }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .then(input),
    ) {
        val left = TREND_LEFT.toPx()
        val inset = TREND_INSET.toPx()
        val top = TREND_TOP.toPx()
        val bottom = size.height - TREND_BOTTOM.toPx()
        fun x(at: Instant) = TrendAxis.x(at, from, to, left + inset, size.width - inset)
        fun y(v: Double) = bottom - (TrendAxis.yFraction(v, range) * (bottom - top)).toFloat()

        band?.let { TrendAxis.bandSpan(it, range) }?.let { b ->
            drawRect(t.accentSoft, Offset(left, y(b.endInclusive)), Size(size.width - left, y(b.start) - y(b.endInclusive)))
        }
        ticks.forEach { tick ->
            val yy = y(tick.value)
            drawLine(t.line2, Offset(left, yy), Offset(size.width, yy), strokeWidth = 1f)
            val layout = measurer.measure(tick.label, labelStyle)
            drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), yy - layout.size.height / 2))
        }
        drawLine(t.line, Offset(left, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
        val start = measurer.measure(startLabel, labelStyle)
        val end = measurer.measure(endLabel, labelStyle)
        drawText(start, topLeft = Offset(left, size.height - start.size.height))
        if (left + start.size.width + 8.dp.toPx() < size.width - end.size.width) {
            drawText(end, topLeft = Offset(size.width - end.size.width, size.height - end.size.height))
        }

        val selectedTime = selectedAt?.takeIf { onSelect != null && it in times }
        selectedTime?.let { at ->
            drawLine(t.ink.copy(alpha = 0.4f), Offset(x(at), top), Offset(x(at), bottom), strokeWidth = 1.dp.toPx())
        }
        series.forEach { s ->
            val points = s.points.sortedBy { it.at }
            if (s.line != TrendLine.NONE && points.size >= 2) {
                val path = Path()
                points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.at), y(p.value)) else path.lineTo(x(p.at), y(p.value)) }
                val dash = if (s.line == TrendLine.DASHED) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null
                drawPath(path, t.accent, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash))
            }
            points.forEach { p ->
                val c = Offset(x(p.at), y(p.value))
                mark(s.mark, c, t.accent, t.surface)
                if (p.at == selectedTime) drawCircle(t.ink, 8.dp.toPx(), c, style = Stroke(2.dp.toPx()))
            }
        }
    }
}

private fun DrawScope.mark(mark: TrendMark, c: Offset, color: Color, fill: Color) {
    when (mark) {
        TrendMark.DOT -> drawCircle(color, 3.5.dp.toPx(), c)
        TrendMark.RING -> {
            drawCircle(fill, 4.dp.toPx(), c)
            drawCircle(color, 4.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
        }
        TrendMark.DIAMOND -> {
            val r = 5.dp.toPx()
            val path = Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }
            drawPath(path, fill)
            drawPath(path, color, style = Stroke(1.5.dp.toPx()))
        }
    }
}
