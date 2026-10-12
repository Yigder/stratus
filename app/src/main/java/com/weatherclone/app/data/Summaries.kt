package com.weatherclone.app.data

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Generates the short sentences shown on the cards. */
object Summaries {

    fun hourly(w: Weather, u: Units): String {
        val hrs = w.upcomingHours(24)
        if (hrs.isEmpty()) return ""
        val first = hrs.first().condition
        val gusts = hrs.take(12).maxOf { it.gust }
        val gustText = if (gusts >= 25) " Wind gusts are up to ${u.wind(gusts)}." else ""

        val rainStart = hrs.drop(1).firstOrNull { Conditions.isPrecip(it.code) && it.precipChance >= 40 }
        if (!Conditions.isPrecip(hrs.first().code) && rainStart != null) {
            val kind = if (Conditions.isSnow(rainStart.code)) "Snow" else rainStart.condition.text.let {
                if (it.contains("Storm") || it.contains("Thunder")) "Thunderstorms" else "Rain"
            }
            return "$kind expected around ${u.hour(rainStart.time)}.$gustText"
        }
        if (Conditions.isPrecip(hrs.first().code)) {
            val end = hrs.firstOrNull { !Conditions.isPrecip(it.code) }
            val what = first.text.lowercase()
            return if (end != null) "${first.text} expected to end around ${u.hour(end.time)}.$gustText"
            else "${what.replaceFirstChar { it.uppercase() }} will continue all day.$gustText"
        }
        val change = hrs.firstOrNull { it.condition.sky != first.sky && it.time.toLocalDate() == hrs.first().time.toLocalDate() }
        return if (change != null) {
            "${change.condition.text} conditions expected around ${u.hour(change.time)}.$gustText"
        } else {
            "${first.text} conditions will continue for the rest of the day.$gustText"
        }
    }

    fun uv(w: Weather, u: Units): String {
        val now = w.now()
        val today = w.hoursOn(now.toLocalDate())
        val high = today.filter { it.uv >= 3 }
        if (high.isEmpty()) return "Low for the rest of the day."
        val start = high.first().time
        val end = high.last().time.plusHours(1)
        return when {
            now.isAfter(end) -> "Low for the rest of the day."
            now.isBefore(start) -> "Use sun protection ${u.hour(start)}–${u.hour(end)}."
            else -> "Use sun protection until ${u.hour(end)}."
        }
    }

    fun uvLevel(uv: Double): String = when (uv.roundToInt()) {
        in 0..2 -> "Low"
        in 3..5 -> "Moderate"
        in 6..7 -> "High"
        in 8..10 -> "Very High"
        else -> "Extreme"
    }

    fun feelsLike(w: Weather): String {
        val c = w.current
        val diff = c.feels - c.temp
        return when {
            abs(diff) < 1.5 -> "Similar to the actual temperature."
            diff < 0 && c.wind >= 10 -> "Wind is making it feel cooler."
            diff > 0 && c.humidity >= 50 -> "Humidity is making it feel warmer."
            diff < 0 -> "It feels cooler than the actual temperature."
            else -> "It feels warmer than the actual temperature."
        }
    }

    fun humidity(w: Weather, u: Units) = "The dew point is ${u.temp(w.current.dewPoint)} right now."

    fun visibility(w: Weather): String {
        val v = w.current.visibility
        return when {
            w.current.code in listOf(45, 48) -> "Fog is affecting visibility."
            v >= 16000 -> "It's perfectly clear right now."
            v >= 8000 -> "It's clear right now."
            v >= 4000 -> if (Conditions.isPrecip(w.current.code)) "Rain is affecting visibility." else "Haze is affecting visibility."
            else -> "Low visibility right now."
        }
    }

    fun precipitation(w: Weather, u: Units): String {
        val today = w.today
        val next24 = w.upcomingHours(24).sumOf { it.precip }
        if (next24 >= 0.25) return "${u.precip(next24)} expected in next 24h."
        val next = w.forecastDays().drop(1).firstOrNull { it.precipSum >= 0.25 }
        return if (next != null) {
            val day = next.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            "Next expected is ${u.precip(next.precipSum)} on $day."
        } else if (today.precipSum >= 0.25) "${u.precip(today.precipSum)} expected today."
        else "None expected in next 10 days."
    }

    fun last24hPrecip(w: Weather): Double {
        val idx = w.currentHourIndex()
        return w.hours.subList((idx - 23).coerceAtLeast(0), idx + 1).sumOf { it.precip }
    }

    fun aqiLevel(aqi: Int): String = when (aqi) {
        in 0..50 -> "Good"
        in 51..100 -> "Moderate"
        in 101..150 -> "Unhealthy for Sensitive Groups"
        in 151..200 -> "Unhealthy"
        in 201..300 -> "Very Unhealthy"
        else -> "Hazardous"
    }

    fun aqi(a: AirQuality): String {
        val y = a.yesterdayAqi
        val cmp = when {
            y == null -> ""
            abs(a.aqi - y) <= 5 -> ", which is similar to yesterday at about this time"
            a.aqi > y -> ", which is higher than yesterday at about this time"
            else -> ", which is lower than yesterday at about this time"
        }
        return "Air quality index is ${a.aqi}$cmp."
    }

    fun pressureTrend(w: Weather): Int {
        val idx = w.currentHourIndex()
        val before = w.hours.getOrNull(idx - 3)?.pressure ?: return 0
        val d = w.current.pressure - before
        return when { d > 0.7 -> 1; d < -0.7 -> -1; else -> 0 }
    }

    fun pressure(w: Weather): String = when (pressureTrend(w)) {
        1 -> "Rising"
        -1 -> "Falling"
        else -> "Steady"
    }

    fun daily(w: Weather, date: LocalDate, u: Units): String {
        val d = w.dayOf(date) ?: return ""
        val isToday = date == w.now().toLocalDate()
        val sb = StringBuilder()
        if (isToday) sb.append("It is currently ${u.temp(w.current.temp)} and ${w.condition.text.lowercase()}. ")
        sb.append(if (isToday) "Today, " else "On ${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, ")
        sb.append("temperatures range from ${u.temp(d.low)} to ${u.temp(d.high)}")
        sb.append(if (d.precipChance >= 30) ", with a ${roundTo10(d.precipChance)}% chance of precipitation." else ".")
        val hours = w.hoursOn(date)
        if (hours.isNotEmpty()) {
            val warmest = hours.maxBy { it.temp }
            sb.append(" The warmest part of the day is around ${u.hour(warmest.time)}.")
        }
        return sb.toString()
    }

    fun comparison(w: Weather, date: LocalDate, u: Units): String? {
        val d = w.dayOf(date) ?: return null
        val prev = w.dayOf(date.minusDays(1)) ?: return null
        val delta = u.tempDelta(d.high - prev.high)
        val dayName = if (date == w.now().toLocalDate()) "Today" else date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val prevName = if (date == w.now().toLocalDate()) "yesterday" else if (date.minusDays(1) == w.now().toLocalDate()) "today" else "the day before"
        return when {
            abs(delta) <= 1 -> "$dayName's high is similar to $prevName's."
            delta > 0 -> "$dayName's high is ${abs(delta)}° warmer than $prevName's."
            else -> "$dayName's high is ${abs(delta)}° cooler than $prevName's."
        }
    }

    fun nextHour(w: Weather): String? {
        val now = w.now()
        val upcoming = w.minutes.filter { !it.time.isBefore(now.minusMinutes(15)) }.take(9)
        if (upcoming.isEmpty() || upcoming.none { it.precip >= 0.1 }) return null
        val raining = upcoming.first().precip >= 0.1
        val isSnow = Conditions.isSnow(w.current.code)
        val kind = if (isSnow) "Snow" else "Rain"
        return if (raining) {
            val stop = upcoming.firstOrNull { it.precip < 0.1 }
            if (stop != null) {
                val mins = java.time.Duration.between(now, stop.time).toMinutes().coerceAtLeast(5)
                "$kind stopping in ${mins} min."
            } else "$kind for the next hour."
        } else {
            val start = upcoming.first { it.precip >= 0.1 }
            val mins = java.time.Duration.between(now, start.time).toMinutes().coerceAtLeast(5)
            "$kind starting in ${mins} min."
        }
    }

    fun roundTo10(p: Int) = ((p + 5) / 10) * 10
}
