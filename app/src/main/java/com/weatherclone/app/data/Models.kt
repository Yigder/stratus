package com.weatherclone.app.data

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Serializable
data class Place(
    val id: String,
    val name: String,
    val region: String = "",
    /** County / district (admin2); used to match European alert areas. */
    val district: String = "",
    val country: String = "",
    val countryCode: String = "",
    val lat: Double,
    val lon: Double,
    val isCurrent: Boolean = false,
) {
    /** "Cupertino, California, United States" style line used in search results. */
    val fullName: String get() = listOf(name, region, country).filter { it.isNotBlank() }.distinct().joinToString(", ")

    companion object {
        const val CURRENT_ID = "current"
    }
}

/** All values are metric: °C, km/h, mm, hPa, metres. Formatting converts to the chosen units. */
data class Weather(
    val place: Place,
    val zone: ZoneId,
    val fetchedAt: Instant,
    val current: Current,
    val hours: List<Hour>,
    val days: List<Day>,
    val minutes: List<Minute>,
    val air: AirQuality?,
    val alerts: List<Alert>,
    val normals: Normals?,
) {
    fun now(): LocalDateTime = LocalDateTime.now(zone)

    fun todayIndex(): Int {
        val d = now().toLocalDate()
        return days.indexOfFirst { it.date == d }.takeIf { it >= 0 } ?: 0
    }

    val today: Day get() = days[todayIndex()]
    val yesterday: Day? get() = days.getOrNull(todayIndex() - 1)

    /** Today plus the following days (10 in total when available). */
    fun forecastDays(): List<Day> = days.drop(todayIndex()).take(10)

    fun hoursOn(date: LocalDate): List<Hour> = hours.filter { it.time.toLocalDate() == date }

    fun currentHourIndex(): Int {
        val n = now()
        val idx = hours.indexOfLast { !it.time.isAfter(n) }
        return idx.coerceAtLeast(0)
    }

    fun upcomingHours(count: Int): List<Hour> = hours.drop(currentHourIndex()).take(count)

    fun dayOf(date: LocalDate): Day? = days.firstOrNull { it.date == date }

    val condition: Condition get() = Conditions.of(current.code, current.cloud, current.isDay, current.wind, current.gust)
}

data class Current(
    val time: LocalDateTime,
    val temp: Double,
    val feels: Double,
    val humidity: Int,
    val dewPoint: Double,
    val code: Int,
    val isDay: Boolean,
    val cloud: Int,
    val pressure: Double,
    val wind: Double,
    val windDir: Int,
    val gust: Double,
    val visibility: Double,
    val uv: Double,
    val precip: Double,
)

data class Hour(
    val time: LocalDateTime,
    val temp: Double,
    val feels: Double,
    val humidity: Int,
    val dewPoint: Double,
    val precipChance: Int,
    val precip: Double,
    val code: Int,
    val cloud: Int,
    val visibility: Double,
    val wind: Double,
    val windDir: Int,
    val gust: Double,
    val uv: Double,
    val isDay: Boolean,
    val pressure: Double,
) {
    val condition: Condition get() = Conditions.of(code, cloud, isDay, wind, gust)
}

data class Day(
    val date: LocalDate,
    val code: Int,
    val high: Double,
    val low: Double,
    val feelsHigh: Double,
    val feelsLow: Double,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
    val daylightSeconds: Double,
    val uvMax: Double,
    val precipSum: Double,
    val precipChance: Int,
    val windMax: Double,
    val gustMax: Double,
    val windDir: Int,
) {
    val condition: Condition get() = Conditions.of(code, if (code >= 3) 100 else code * 30, true, windMax, gustMax)
}

data class Minute(val time: LocalDateTime, val precip: Double)

data class AirQuality(
    val aqi: Int,
    val pm25: Double?,
    val pm10: Double?,
    val ozone: Double?,
    val no2: Double?,
    val so2: Double?,
    val co: Double?,
    val yesterdayAqi: Int?,
    val hourly: List<Pair<LocalDateTime, Int>>,
)

@Serializable
data class Alert(
    val event: String,
    val headline: String,
    val description: String,
    val instruction: String,
    val severity: String,
    val sender: String,
    val endsEpoch: Long? = null,
) {
    val ends: Instant? get() = endsEpoch?.let { Instant.ofEpochMilli(it) }
    /** Stable id used to avoid notifying twice about the same alert. */
    val key: String get() = "$sender|$event|${endsEpoch ?: 0}"
}

/** Which notifications are on, and the hour for the daily forecast. */
data class NotifPrefs(val severe: Boolean, val rain: Boolean, val daily: Boolean, val hour: Int)

/** Climate normals computed from the last ten years of observations. */
data class Normals(
    val todayHigh: Double,
    val todayLow: Double,
    /** Index 0 = January. */
    val monthlyHigh: List<Double>,
    val monthlyLow: List<Double>,
)
