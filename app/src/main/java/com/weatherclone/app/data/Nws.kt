package com.weatherclone.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The National Weather Service's gridded forecast for US places. It's the official forecast:
 * statistically corrected model blends (NBM) edited by local forecasters, and its rain chance is a
 * real probability of precipitation. Open-Meteo's raw models run a few degrees off in places, and its
 * chance is counted from a coarse ensemble, so for US places we lay NWS temperature, feels-like and
 * chance over the Open-Meteo hours it covers (about seven days) and keep Open-Meteo for the rest.
 */
object Nws {
    private val json = Json { ignoreUnknownKeys = true }
    private val keep = listOf("temperature", "apparentTemperature", "probabilityOfPrecipitation")

    class Point(val temp: Double?, val feels: Double?, val chance: Int?)

    fun pointsUrl(lat: Double, lon: Double) = String.format(Locale.US, "https://api.weather.gov/points/%.4f,%.4f", lat, lon)

    /** The gridpoint forecast URL from a /points response. */
    fun gridUrl(points: String): String? =
        ((json.parseToJsonElement(points).jsonObject["properties"] as? JsonObject)?.get("forecastGridData") as? JsonPrimitive)?.contentOrNull

    /** Cuts a gridpoint response (hundreds of KB) down to the series we use, in the same shape. */
    fun trim(grid: String): String {
        val props = json.parseToJsonElement(grid).jsonObject["properties"] as? JsonObject ?: error("no properties")
        val kept = buildJsonObject {
            props["updateTime"]?.let { put("updateTime", it) }
            keep.forEach { k -> props[k]?.let { put(k, it) } }
        }
        return buildJsonObject { put("properties", kept) }.toString()
    }

    /** Hourly values in [zone]'s local time, or null if the forecast is missing or more than a day old. */
    fun parse(raw: String, zone: ZoneId, now: Instant = Instant.now()): Map<LocalDateTime, Point>? {
        val props = json.parseToJsonElement(raw).jsonObject["properties"] as? JsonObject ?: return null
        val updated = (props["updateTime"] as? JsonPrimitive)?.contentOrNull?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        if (updated == null || Duration.between(updated, now).toHours() >= 24) return null
        val temp = series(props, "temperature", zone)
        val feels = series(props, "apparentTemperature", zone)
        val chance = series(props, "probabilityOfPrecipitation", zone)
        if (temp.isEmpty()) return null
        return (temp.keys + feels.keys + chance.keys).associateWith { t ->
            Point(temp[t], feels[t], chance[t]?.roundToInt())
        }
    }

    /** Expands NWS "start/duration" values ("2026-10-13T00:00:00+00:00/PT3H") to one value per hour. */
    private fun series(props: JsonObject, key: String, zone: ZoneId): Map<LocalDateTime, Double> {
        val values = ((props[key] as? JsonObject)?.get("values") as? JsonArray) ?: return emptyMap()
        val out = HashMap<LocalDateTime, Double>()
        for (v in values) {
            val o = v as? JsonObject ?: continue
            val value = (o["value"] as? JsonPrimitive)?.doubleOrNull ?: continue
            val (startS, durS) = (o["validTime"] as? JsonPrimitive)?.contentOrNull?.split("/")?.takeIf { it.size == 2 } ?: continue
            val start = runCatching { OffsetDateTime.parse(startS).toInstant() }.getOrNull() ?: continue
            val hours = runCatching { Duration.parse(durS).toHours() }.getOrNull()?.coerceIn(1, 24 * 8) ?: continue
            for (k in 0 until hours) {
                out[start.plus(k, ChronoUnit.HOURS).atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)] = value
            }
        }
        return out
    }

    /** [hours], [days] and [current] with NWS values wherever the grid covers them. */
    fun overlay(grid: Map<LocalDateTime, Point>, hours: List<Hour>, days: List<Day>, current: Current, now: LocalDateTime): Triple<List<Hour>, List<Day>, Current> {
        val covered = HashSet<LocalDateTime>()
        val newHours = hours.map { h ->
            val p = grid[h.time] ?: return@map h
            if (p.temp != null) covered += h.time
            h.copy(temp = p.temp ?: h.temp, feels = p.feels ?: h.feels, precipChance = p.chance ?: h.precipChance)
        }
        // Highs, lows and the day's chance follow the hourly values, as Apple's do, once NWS covers
        // (nearly) the whole day; days at the far end keep Open-Meteo's.
        val byDate: Map<LocalDate, List<Hour>> = newHours.filter { it.time in covered }.groupBy { it.time.toLocalDate() }
        val newDays = days.map { d ->
            val hs = byDate[d.date]?.takeIf { it.size >= 20 } ?: return@map d
            d.copy(
                high = hs.maxOf { it.temp }, low = hs.minOf { it.temp },
                feelsHigh = hs.maxOf { it.feels }, feelsLow = hs.minOf { it.feels },
                precipChance = hs.maxOf { it.precipChance },
            )
        }
        // Current temperature: the NWS hourly values either side of now, blended by the minute.
        val h0 = now.truncatedTo(ChronoUnit.HOURS)
        val a = grid[h0]; val b = grid[h0.plusHours(1)]
        val f = now.minute / 60.0
        fun mix(x: Double?, y: Double?) = if (x != null && y != null) x + (y - x) * f else x
        val newCurrent = current.copy(
            temp = mix(a?.temp, b?.temp) ?: current.temp,
            feels = mix(a?.feels, b?.feels) ?: current.feels,
        )
        return Triple(newHours, newDays, newCurrent)
    }
}
