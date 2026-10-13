package com.weatherclone.app

import com.weatherclone.app.data.Current
import com.weatherclone.app.data.Day
import com.weatherclone.app.data.Hour
import com.weatherclone.app.data.Nws
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** NWS gridpoint parsing, checked against a recorded New York City forecast. */
class NwsTest {
    private val zone = ZoneId.of("America/New_York")
    private val grid = File("src/test/resources/nws_grid.json").readText()
    private val issued = Instant.parse("2026-10-13T06:02:10Z")
    private fun f(c: Double) = (c * 9 / 5 + 32).roundToInt()

    @Test
    fun expandsToHourlyLocalTime() {
        val map = Nws.parse(grid, zone, issued)!!
        // 00Z on Oct 13 is 8 PM on Oct 12 in New York; the first value is 20.56 °C = 69 °F.
        val first = map[java.time.LocalDateTime.of(2026, 10, 12, 20, 0)]!!
        assertEquals(69, f(first.temp!!))
        assertTrue("about a week of hours", map.size > 140)
        assertTrue(map.values.all { it.chance == null || it.chance in 0..100 })
    }

    @Test
    fun dropsStaleForecasts() {
        assertNull(Nws.parse(grid, zone, issued.plusSeconds(25 * 3600)))
    }

    @Test
    fun overlaysHoursDaysAndCurrent() {
        val map = Nws.parse(grid, zone, issued)!!
        val date = java.time.LocalDate.of(2026, 10, 13)
        val hours = List(24) { h ->
            Hour(
                time = date.atTime(h, 0), temp = 0.0, feels = 0.0, humidity = 50, dewPoint = 0.0, precipChance = 0, precip = 0.0,
                code = 0, cloud = 0, visibility = 24000.0, wind = 0.0, windDir = 0, gust = 0.0, uv = 0.0, isDay = true, pressure = 1013.0,
            )
        }
        val day = Day(
            date = date, code = 0, high = 0.0, low = 0.0, feelsHigh = 0.0, feelsLow = 0.0, sunrise = null, sunset = null,
            daylightSeconds = 0.0, uvMax = 0.0, precipSum = 0.0, precipChance = 0, windMax = 0.0, gustMax = 0.0, windDir = 0,
        )
        val current = Current(
            time = date.atTime(12, 30), temp = 0.0, feels = 0.0, humidity = 50, dewPoint = 0.0, code = 0, isDay = true, cloud = 0,
            pressure = 1013.0, wind = 0.0, windDir = 0, gust = 0.0, visibility = 24000.0, uv = 0.0, precip = 0.0,
        )
        val (h, d, c) = Nws.overlay(map, hours, listOf(day), current, date.atTime(12, 30))
        val temps = (0 until 24).map { map[date.atTime(it, 0)]!!.temp!! }
        assertEquals(temps, h.map { it.temp })
        assertEquals(temps.max(), d[0].high, 1e-9)
        assertEquals(temps.min(), d[0].low, 1e-9)
        assertEquals((temps[12] + temps[13]) / 2, c.temp, 1e-9)
    }
}
