package com.weatherclone.app.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Low-precision sun and moon positions (Paul Schlyter's method), good to a few minutes
 * for rise/set times — plenty for a weather card.
 */
object Astro {
    private const val RAD = PI / 180
    private const val SYNODIC = 29.530588853

    private fun rev(x: Double) = x - floor(x / 360.0) * 360.0
    private fun sind(x: Double) = sin(x * RAD)
    private fun cosd(x: Double) = cos(x * RAD)
    private fun atan2d(y: Double, x: Double) = atan2(y, x) / RAD

    /** Days since 2000 Jan 0.0 UT. */
    private fun dayNumber(t: Instant): Double = t.epochSecond / 86400.0 + 2440587.5 - 2451543.5

    private data class Eq(val ra: Double, val dec: Double)

    private fun sunLongitude(d: Double): Pair<Double, Double> {
        val w = 282.9404 + 4.70935e-5 * d
        val e = 0.016709 - 1.151e-9 * d
        val m = rev(356.0470 + 0.9856002585 * d)
        val ecc = m + e / RAD * sind(m) * (1 + e * cosd(m))
        val xv = cosd(ecc) - e
        val yv = sqrt(1 - e * e) * sind(ecc)
        val v = atan2d(yv, xv)
        return rev(v + w) to m
    }

    private fun toEquatorial(lon: Double, lat: Double, d: Double): Eq {
        val ecl = 23.4393 - 3.563e-7 * d
        val xe = cosd(lon) * cosd(lat)
        val ye = cosd(ecl) * sind(lon) * cosd(lat) - sind(ecl) * sind(lat)
        val ze = sind(ecl) * sind(lon) * cosd(lat) + cosd(ecl) * sind(lat)
        return Eq(rev(atan2d(ye, xe)), atan2d(ze, sqrt(xe * xe + ye * ye)))
    }

    private fun sunEq(d: Double): Eq = toEquatorial(sunLongitude(d).first, 0.0, d)

    private fun moonEq(d: Double): Eq {
        val n = rev(125.1228 - 0.0529538083 * d)
        val i = 5.1454
        val w = rev(318.0634 + 0.1643573223 * d)
        val e = 0.054900
        val m = rev(115.3654 + 13.0649929509 * d)
        var ecc = m + e / RAD * sind(m) * (1 + e * cosd(m))
        repeat(3) { ecc -= (ecc - e / RAD * sind(ecc) - m) / (1 - e * cosd(ecc)) }
        val xv = 60.2666 * (cosd(ecc) - e)
        val yv = 60.2666 * sqrt(1 - e * e) * sind(ecc)
        val v = atan2d(yv, xv)
        val r = sqrt(xv * xv + yv * yv)
        val xh = r * (cosd(n) * cosd(v + w) - sind(n) * sind(v + w) * cosd(i))
        val yh = r * (sind(n) * cosd(v + w) + cosd(n) * sind(v + w) * cosd(i))
        val zh = r * sind(v + w) * sind(i)
        var lon = atan2d(yh, xh)
        var lat = atan2d(zh, sqrt(xh * xh + yh * yh))
        val (ls, ms) = sunLongitude(d)
        val lm = n + w + m
        val dd = lm - ls
        val f = lm - n
        lon += -1.274 * sind(m - 2 * dd) + 0.658 * sind(2 * dd) - 0.186 * sind(ms) -
            0.059 * sind(2 * m - 2 * dd) - 0.057 * sind(m - 2 * dd + ms) + 0.053 * sind(m + 2 * dd) +
            0.046 * sind(2 * dd - ms) + 0.041 * sind(m - ms) - 0.035 * sind(dd) - 0.031 * sind(m + ms) -
            0.015 * sind(2 * f - 2 * dd) + 0.011 * sind(m - 4 * dd)
        lat += -0.173 * sind(f - 2 * dd) - 0.055 * sind(m - f - 2 * dd) - 0.046 * sind(m + f - 2 * dd) +
            0.033 * sind(f + 2 * dd) + 0.017 * sind(2 * m + f)
        return toEquatorial(lon, lat, d)
    }

    private fun altitude(eq: Eq, d: Double, t: Instant, lat: Double, lon: Double): Double {
        // Sun's mean longitude (w + M) gives Greenwich sidereal time at 0h UT.
        val d0 = floor(d)
        val l = rev(282.9404 + 4.70935e-5 * d0 + 356.0470 + 0.9856002585 * d0)
        val ut = (t.epochSecond % 86400 + 86400) % 86400 / 3600.0
        val lst = rev(l + 180 + ut * 15.04107 + lon)
        val ha = lst - eq.ra
        return asin(sind(lat) * sind(eq.dec) + cosd(lat) * cosd(eq.dec) * cosd(ha)) / RAD
    }

    fun sunAltitude(t: Instant, lat: Double, lon: Double): Double {
        val d = dayNumber(t)
        return altitude(sunEq(d), d, t, lat, lon)
    }

    fun moonAltitude(t: Instant, lat: Double, lon: Double): Double {
        val d = dayNumber(t)
        return altitude(moonEq(d), d, t, lat, lon)
    }

    /** Times on [date] when [alt] crosses [threshold] going up / going down. */
    private fun crossings(date: LocalDate, zone: ZoneId, threshold: Double, alt: (Instant) -> Double): Pair<LocalDateTime?, LocalDateTime?> {
        val start = date.atStartOfDay(zone).toInstant()
        val step = 300L
        var rise: Instant? = null
        var set: Instant? = null
        var prevT = start
        var prev = alt(start) - threshold
        var s = step
        while (s <= 86400) {
            val t = start.plusSeconds(s)
            val cur = alt(t) - threshold
            if (prev < 0 && cur >= 0 && rise == null) rise = interp(prevT, t, prev, cur)
            if (prev >= 0 && cur < 0 && set == null) set = interp(prevT, t, prev, cur)
            prev = cur; prevT = t; s += step
        }
        return rise?.atZone(zone)?.toLocalDateTime() to set?.atZone(zone)?.toLocalDateTime()
    }

    private fun interp(a: Instant, b: Instant, va: Double, vb: Double): Instant {
        val f = va / (va - vb)
        return a.plusSeconds((Duration.between(a, b).seconds * f).toLong())
    }

    fun moonTimes(date: LocalDate, zone: ZoneId, lat: Double, lon: Double) =
        crossings(date, zone, 0.125) { moonAltitude(it, lat, lon) }

    /** Civil twilight: first light / last light. */
    fun twilight(date: LocalDate, zone: ZoneId, lat: Double, lon: Double) =
        crossings(date, zone, -6.0) { sunAltitude(it, lat, lon) }

    fun sunTimes(date: LocalDate, zone: ZoneId, lat: Double, lon: Double) =
        crossings(date, zone, -0.833) { sunAltitude(it, lat, lon) }

    // --- Moon phase --------------------------------------------------------

    private val refNewMoon: Instant = Instant.parse("2000-01-06T18:14:00Z")

    /** Moon age in days, 0 = new moon. */
    fun moonAge(t: Instant): Double {
        val days = Duration.between(refNewMoon, t).seconds / 86400.0
        return ((days % SYNODIC) + SYNODIC) % SYNODIC
    }

    fun illumination(t: Instant): Double = (1 - cos(2 * PI * moonAge(t) / SYNODIC)) / 2

    fun phaseName(t: Instant): String {
        val age = moonAge(t)
        val f = age / SYNODIC
        return when {
            f < 0.0339 || f > 0.9661 -> "New Moon"
            f < 0.2161 -> "Waxing Crescent"
            f < 0.2839 -> "First Quarter"
            f < 0.4661 -> "Waxing Gibbous"
            f < 0.5339 -> "Full Moon"
            f < 0.7161 -> "Waning Gibbous"
            f < 0.7839 -> "Last Quarter"
            else -> "Waning Crescent"
        }
    }

    /** Days until the next full moon (0 when the moon is full today). */
    fun daysUntilFull(t: Instant): Int {
        val age = moonAge(t)
        var d = SYNODIC / 2 - age
        if (d < -0.5) d += SYNODIC
        return kotlin.math.max(0, kotlin.math.round(d).toInt())
    }

    fun daysUntilNew(t: Instant): Int {
        val d = SYNODIC - moonAge(t)
        return kotlin.math.round(d).toInt() % 30
    }
}
