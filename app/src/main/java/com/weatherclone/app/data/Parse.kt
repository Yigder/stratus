package com.weatherclone.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** Turns raw Open-Meteo / NWS JSON into the [Weather] model. */
object Parse {
    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.arr(k: String) = this[k] as? JsonArray
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.dbl(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonArray?.d(i: Int, def: Double = 0.0) = (this?.getOrNull(i) as? JsonPrimitive)?.doubleOrNull ?: def
    private fun JsonArray?.s(i: Int) = (this?.getOrNull(i) as? JsonPrimitive)?.contentOrNull

    fun zoneOf(root: JsonObject): ZoneId =
        runCatching { ZoneId.of(root.str("timezone")) }.getOrNull()
            ?: ZoneOffset.ofTotalSeconds((root.dbl("utc_offset_seconds") ?: 0.0).toInt())

    fun weather(
        place: Place,
        forecast: String,
        air: String?,
        alerts: String?,
        archive: String?,
        fetchedAt: Instant,
    ): Weather {
        val root = json.parseToJsonElement(forecast).jsonObject
        val zone = zoneOf(root)

        val h = root.obj("hourly") ?: error("no hourly data")
        val ht = h.arr("time")!!
        val hours = List(ht.size) { i ->
            Hour(
                time = LocalDateTime.parse(ht.s(i)),
                temp = h.arr("temperature_2m").d(i),
                feels = h.arr("apparent_temperature").d(i),
                humidity = h.arr("relative_humidity_2m").d(i).roundToInt(),
                dewPoint = h.arr("dew_point_2m").d(i),
                precipChance = h.arr("precipitation_probability").d(i).roundToInt(),
                precip = h.arr("precipitation").d(i),
                code = h.arr("weather_code").d(i).toInt(),
                cloud = h.arr("cloud_cover").d(i).roundToInt(),
                visibility = h.arr("visibility").d(i, 24000.0),
                wind = h.arr("wind_speed_10m").d(i),
                windDir = h.arr("wind_direction_10m").d(i).roundToInt(),
                gust = h.arr("wind_gusts_10m").d(i),
                uv = h.arr("uv_index").d(i),
                isDay = h.arr("is_day").d(i, 1.0) > 0.5,
                pressure = h.arr("pressure_msl").d(i, 1013.0),
            )
        }

        val dj = root.obj("daily") ?: error("no daily data")
        val dt = dj.arr("time")!!
        val days = List(dt.size) { i ->
            Day(
                date = LocalDate.parse(dt.s(i)),
                code = dj.arr("weather_code").d(i).toInt(),
                high = dj.arr("temperature_2m_max").d(i),
                low = dj.arr("temperature_2m_min").d(i),
                feelsHigh = dj.arr("apparent_temperature_max").d(i),
                feelsLow = dj.arr("apparent_temperature_min").d(i),
                sunrise = dj.arr("sunrise").s(i)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
                sunset = dj.arr("sunset").s(i)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
                daylightSeconds = dj.arr("daylight_duration").d(i),
                uvMax = dj.arr("uv_index_max").d(i),
                precipSum = dj.arr("precipitation_sum").d(i),
                precipChance = dj.arr("precipitation_probability_max").d(i).roundToInt(),
                windMax = dj.arr("wind_speed_10m_max").d(i),
                gustMax = dj.arr("wind_gusts_10m_max").d(i),
                windDir = dj.arr("wind_direction_10m_dominant").d(i).roundToInt(),
            )
        }

        val minutes = root.obj("minutely_15")?.let { m ->
            val mt = m.arr("time") ?: return@let emptyList()
            List(mt.size) { i -> Minute(LocalDateTime.parse(mt.s(i)), m.arr("precipitation").d(i)) }
        } ?: emptyList()

        val c = root.obj("current")
        val nowIdx = hours.indexOfLast { !it.time.isAfter(LocalDateTime.now(zone)) }.coerceAtLeast(0)
        val nh = hours.getOrNull(nowIdx)
        val current = Current(
            time = c?.str("time")?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now(zone),
            temp = c?.dbl("temperature_2m") ?: nh?.temp ?: 0.0,
            feels = c?.dbl("apparent_temperature") ?: nh?.feels ?: 0.0,
            humidity = (c?.dbl("relative_humidity_2m") ?: nh?.humidity?.toDouble() ?: 0.0).roundToInt(),
            dewPoint = c?.dbl("dew_point_2m") ?: nh?.dewPoint ?: 0.0,
            code = (c?.dbl("weather_code") ?: nh?.code?.toDouble() ?: 0.0).toInt(),
            isDay = (c?.dbl("is_day") ?: 1.0) > 0.5,
            cloud = (c?.dbl("cloud_cover") ?: nh?.cloud?.toDouble() ?: 0.0).roundToInt(),
            pressure = c?.dbl("pressure_msl") ?: nh?.pressure ?: 1013.0,
            wind = c?.dbl("wind_speed_10m") ?: nh?.wind ?: 0.0,
            windDir = (c?.dbl("wind_direction_10m") ?: nh?.windDir?.toDouble() ?: 0.0).roundToInt(),
            gust = c?.dbl("wind_gusts_10m") ?: nh?.gust ?: 0.0,
            visibility = c?.dbl("visibility") ?: nh?.visibility ?: 24000.0,
            uv = c?.dbl("uv_index") ?: nh?.uv ?: 0.0,
            precip = c?.dbl("precipitation") ?: 0.0,
        )

        return Weather(
            place = place,
            zone = zone,
            fetchedAt = fetchedAt,
            current = current,
            hours = hours,
            days = days,
            minutes = minutes,
            air = air?.let { runCatching { airQuality(it, zone) }.getOrNull() },
            alerts = alerts?.let { Alerts.decode(it) } ?: emptyList(),
            normals = archive?.let { runCatching { normals(it, LocalDate.now(zone)) }.getOrNull() },
        )
    }

    fun airQuality(raw: String, zone: ZoneId): AirQuality? {
        val root = json.parseToJsonElement(raw).jsonObject
        val c = root.obj("current") ?: return null
        val aqi = c.dbl("us_aqi")?.roundToInt() ?: return null
        val h = root.obj("hourly")
        val times = h?.arr("time")
        val hourly = if (times != null) List(times.size) { i ->
            LocalDateTime.parse(times.s(i)) to h.arr("us_aqi").d(i, -1.0).roundToInt()
        }.filter { it.second >= 0 } else emptyList()
        val yesterdayTarget = LocalDateTime.now(zone).minusDays(1)
        val yesterday = hourly.minByOrNull { kotlin.math.abs(java.time.Duration.between(it.first, yesterdayTarget).toMinutes()) }
            ?.takeIf { kotlin.math.abs(java.time.Duration.between(it.first, yesterdayTarget).toMinutes()) < 90 }
        return AirQuality(
            aqi = aqi,
            pm25 = c.dbl("pm2_5"), pm10 = c.dbl("pm10"), ozone = c.dbl("ozone"),
            no2 = c.dbl("nitrogen_dioxide"), so2 = c.dbl("sulphur_dioxide"), co = c.dbl("carbon_monoxide"),
            yesterdayAqi = yesterday?.second,
            hourly = hourly,
        )
    }

    fun normals(raw: String, today: LocalDate): Normals? {
        val root = json.parseToJsonElement(raw).jsonObject
        val d = root.obj("daily") ?: return null
        val t = d.arr("time") ?: return null
        val hi = d.arr("temperature_2m_max")
        val lo = d.arr("temperature_2m_min")
        val monthHi = Array(12) { mutableListOf<Double>() }
        val monthLo = Array(12) { mutableListOf<Double>() }
        val nearHi = mutableListOf<Double>()
        val nearLo = mutableListOf<Double>()
        val todayMd = MonthDay.from(today)
        for (i in 0 until t.size) {
            val date = LocalDate.parse(t.s(i) ?: continue)
            val maxV = (hi?.getOrNull(i) as? JsonPrimitive)?.doubleOrNull ?: continue
            val minV = (lo?.getOrNull(i) as? JsonPrimitive)?.doubleOrNull ?: continue
            monthHi[date.monthValue - 1] += maxV
            monthLo[date.monthValue - 1] += minV
            val doy = todayMd.atYear(date.year).dayOfYear
            if (kotlin.math.abs(date.dayOfYear - doy) <= 3) { nearHi += maxV; nearLo += minV }
        }
        if (nearHi.isEmpty()) return null
        return Normals(
            todayHigh = nearHi.average(),
            todayLow = nearLo.average(),
            monthlyHigh = monthHi.map { if (it.isEmpty()) Double.NaN else it.average() },
            monthlyLow = monthLo.map { if (it.isEmpty()) Double.NaN else it.average() },
        )
    }

    fun geocodeResults(raw: String): List<Place> {
        val root = json.parseToJsonElement(raw).jsonObject
        val results = root.arr("results") ?: return emptyList()
        return results.mapNotNull { r ->
            val o = r.jsonObject
            Place(
                id = "geo_" + (o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null),
                name = o.str("name") ?: return@mapNotNull null,
                region = o.str("admin1") ?: "",
                district = o.str("admin2") ?: "",
                country = o.str("country") ?: "",
                countryCode = o.str("country_code") ?: "",
                lat = o.dbl("latitude") ?: return@mapNotNull null,
                lon = o.dbl("longitude") ?: return@mapNotNull null,
            )
        }
    }

    /** bigdatacloud reverse geocoding response → (city, region, country, countryCode). */
    fun reverse(raw: String): List<String> {
        val o = json.parseToJsonElement(raw).jsonObject
        val city = o.str("city")?.takeIf { it.isNotBlank() } ?: o.str("locality") ?: ""
        return listOf(city, o.str("principalSubdivision") ?: "", o.str("countryName") ?: "", o.str("countryCode") ?: "")
    }

    fun radarFrames(raw: String): Pair<String, List<Pair<Long, String>>> {
        val o = json.parseToJsonElement(raw).jsonObject
        val host = o.str("host") ?: "https://tilecache.rainviewer.com"
        val radar = o.obj("radar")
        val frames = (radar?.arr("past")?.toList() ?: emptyList()) + (radar?.arr("nowcast")?.toList() ?: emptyList())
        return host to frames.mapNotNull { f ->
            val fo = f.jsonObject
            val time = fo.dbl("time")?.toLong() ?: return@mapNotNull null
            val path = fo.str("path") ?: return@mapNotNull null
            time to path
        }
    }
}
