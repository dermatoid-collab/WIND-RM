package com.windrm.app.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Parses Open-Meteo's UTC, zone-less "yyyy-MM-dd'T'HH:mm" timestamps. */
private val hourlyTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

fun parseOpenMeteoInstant(raw: String): Instant =
    LocalDateTime.parse(raw, hourlyTimeFormatter).toInstant(ZoneOffset.UTC)

/**
 * A parsed hourly time axis plus lookup helpers to read/interpolate values at an arbitrary
 * [Instant] that falls within (or beyond the edges of) the series.
 */
class HourlySeries(rawTimes: List<String>) {
    val times: List<Instant> = rawTimes.map(::parseOpenMeteoInstant)

    /** Returns the bracketing index and the 0..1 fraction of the way to the next index. */
    private fun locate(target: Instant): Pair<Int, Double> {
        if (times.isEmpty()) return 0 to 0.0
        if (target <= times.first()) return 0 to 0.0
        if (target >= times.last()) return times.lastIndex to 0.0
        for (i in 0 until times.size - 1) {
            val a = times[i]
            val b = times[i + 1]
            if (!target.isBefore(a) && !target.isAfter(b)) {
                val spanS = Duration.between(a, b).seconds.toDouble()
                val fraction = if (spanS <= 0.0) 0.0 else Duration.between(a, target).seconds / spanS
                return i to fraction
            }
        }
        return times.lastIndex to 0.0
    }

    /** Linear interpolation between the two hourly samples bracketing [target]. */
    fun valueAt(target: Instant, values: List<Double?>?): Double {
        if (values.isNullOrEmpty()) return 0.0
        val (index, fraction) = locate(target)
        val a = values.getOrNull(index) ?: 0.0
        val b = values.getOrNull((index + 1).coerceAtMost(values.lastIndex)) ?: a
        return a + (b - a) * fraction
    }

    /** Nearest-hour lookup, appropriate for non-continuous or circular values (e.g. wind direction). */
    fun nearestAt(target: Instant, values: List<Double?>?): Double {
        if (values.isNullOrEmpty()) return 0.0
        val (index, fraction) = locate(target)
        val nearestIndex = if (fraction >= 0.5) (index + 1).coerceAtMost(values.lastIndex) else index
        return values.getOrNull(nearestIndex) ?: 0.0
    }

    fun nearestIntAt(target: Instant, values: List<Int?>?): Int {
        if (values.isNullOrEmpty()) return 0
        val (index, fraction) = locate(target)
        val nearestIndex = if (fraction >= 0.5) (index + 1).coerceAtMost(values.lastIndex) else index
        return values.getOrNull(nearestIndex) ?: 0
    }
}
