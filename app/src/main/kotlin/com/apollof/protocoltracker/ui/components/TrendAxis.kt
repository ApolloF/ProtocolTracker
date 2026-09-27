package com.apollof.protocoltracker.ui.components

import com.apollof.protocoltracker.domain.units.formatNumber
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

/** A shaded value range on a [TrendChart]; a null side is open and shades to the plot edge. */
data class TrendBand(val low: Double?, val high: Double?)

/** A y label of a [TrendChart]. */
data class TrendTick(val value: Double, val label: String)

/** Axis math of [TrendChart]. Pure Kotlin, so it is tested on the JVM. */
object TrendAxis {
    /** Share of the value span added above and below the data. */
    const val PADDING = 0.10

    /**
     * The time window of a chart: from the earliest of [times] to the latest (or [end] when that is later), widened
     * back from the end to at least [minSpan]. With no times, the [minSpan] up to [end].
     */
    fun xWindow(times: Collection<Instant>, minSpan: Duration, end: Instant? = null): Pair<Instant, Instant> {
        val to = listOfNotNull(times.maxOrNull(), end).maxOrNull() ?: error("xWindow needs a time or an end")
        val from = minOf(times.minOrNull() ?: to, to.minus(minSpan))
        return from to to
    }

    /** Where [at] lies between [start] and [end] (pixels), proportional to time; the middle for an empty window. */
    fun x(at: Instant, from: Instant, to: Instant, start: Float, end: Float): Float {
        val span = to.toEpochMilli() - from.toEpochMilli()
        val f = if (span <= 0) 0.5 else (at.toEpochMilli() - from.toEpochMilli()).toDouble() / span
        return start + (f * (end - start)).toFloat()
    }

    /**
     * The value range: [fixed] when given; otherwise the data and both [band] limits, padded by [PADDING] of their
     * span on each side and never below 0 when nothing is negative. A single value gets a span of 20 % of itself.
     */
    fun yRange(values: Collection<Double>, band: TrendBand? = null, fixed: ClosedFloatingPointRange<Double>? = null): ClosedFloatingPointRange<Double> {
        if (fixed != null) return fixed
        val all = values + listOfNotNull(band?.low, band?.high)
        if (all.isEmpty()) return 0.0..1.0
        var lo = all.min()
        var hi = all.max()
        if (hi - lo < 1e-9) {
            val half = max(abs(lo) * 0.2, 1.0) / 2
            lo -= half
            hi += half
        }
        val pad = (hi - lo) * PADDING
        val bottom = if (all.min() >= 0) max(lo - pad, 0.0) else lo - pad
        return bottom..hi + pad
    }

    /** Where [v] lies in [range], 0 at the bottom and 1 at the top. */
    fun yFraction(v: Double, range: ClosedFloatingPointRange<Double>): Double {
        val span = range.endInclusive - range.start
        return if (span <= 0) 0.5 else (v - range.start) / span
    }

    /** The shaded part of [range]: an open side reaches the plot edge. Null when nothing of the band is in view. */
    fun bandSpan(band: TrendBand, range: ClosedFloatingPointRange<Double>): ClosedFloatingPointRange<Double>? {
        if (band.low == null && band.high == null) return null
        val lo = (band.low ?: range.start).coerceIn(range.start, range.endInclusive)
        val hi = (band.high ?: range.endInclusive).coerceIn(range.start, range.endInclusive)
        return if (hi > lo) lo..hi else null
    }

    /** Round y labels inside [range]: the finest 1, 2, 2.5 or 5 × 10ⁿ step that gives at most three (two or more). */
    fun yTicks(range: ClosedFloatingPointRange<Double>): List<TrendTick> {
        val span = range.endInclusive - range.start
        if (span <= 0) return listOf(TrendTick(range.start, formatNumber(range.start, 1)))
        val exp = floor(log10(span)).toInt()
        val steps = (exp - 3..exp + 1).flatMap { e -> listOf(1.0, 2.0, 2.5, 5.0).map { it * 10.0.pow(e) } }
        fun indices(step: Double) = ceil(range.start / step - 1e-9).toLong()..floor(range.endInclusive / step + 1e-9).toLong()
        val fit = steps.indexOfFirst { indices(it).count() <= 3 }
        val step = if (fit > 0 && indices(steps[fit]).count() < 2) steps[fit - 1] else steps[fit]
        val decimals = (0..6).first { d -> 10.0.pow(d).let { m -> abs(step * m - (step * m).roundToLong()) < 1e-6 } }
        // One number of decimals for every label, so "0.8" sits above "1.0", not "1".
        return indices(step).map { i -> (i * step).let { TrendTick(it, String.format(Locale.ROOT, "%.${decimals}f", it)) } }
    }

    /** The index in [xs] nearest to [x], if within [maxDistance]. */
    fun nearest(xs: List<Float>, x: Float, maxDistance: Float = Float.MAX_VALUE): Int? =
        xs.indices.minByOrNull { abs(xs[it] - x) }?.takeIf { abs(xs[it] - x) <= maxDistance }
}
