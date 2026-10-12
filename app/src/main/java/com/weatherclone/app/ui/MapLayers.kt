package com.weatherclone.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan

enum class MapLayer(val title: String) {
    Precipitation("Precipitation"), Temperature("Temperature"), Wind("Wind"), AirQuality("Air Quality")
}

/** Web-mercator in 0..1 world units. */
object Mercator {
    fun x(lon: Double) = (lon + 180) / 360
    fun y(lat: Double): Double {
        val r = lat.coerceIn(-85.0, 85.0) * PI / 180
        return (1 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2
    }
    fun lon(x: Double) = x * 360 - 180
    fun lat(y: Double): Double {
        val n = PI - 2 * PI * y
        return 180 / PI * atan(0.5 * (exp(n) - exp(-n)))
    }
}

/**
 * Model values sampled on a regular grid (in mercator space) for the next hours.
 * values[layer][hour][row * nx + col]; wind also carries directions.
 */
class WeatherGrid(
    val left: Double, val top: Double, val right: Double, val bottom: Double,
    val nx: Int, val ny: Int,
    val times: List<Long>,
    val values: Map<MapLayer, List<FloatArray>>,
    val windDir: List<FloatArray>,
    val zoom: Float,
) {
    private val bitmaps = HashMap<Pair<MapLayer, Int>, ImageBitmap>()

    fun lat(row: Int) = Mercator.lat(top + (row + 0.5) * (bottom - top) / ny)
    fun lon(col: Int) = Mercator.lon(left + (col + 0.5) * (right - left) / nx)

    fun covers(l: Double, t: Double, r: Double, b: Double) = l >= left && r <= right && t >= top && b <= bottom

    fun bitmap(layer: MapLayer, hour: Int): ImageBitmap? {
        val frames = values[layer] ?: return null
        val h = hour.coerceIn(0, frames.size - 1)
        return bitmaps.getOrPut(layer to h) {
            val px = IntArray(nx * ny) { colorFor(layer, frames[h][it]) }
            Bitmap.createBitmap(px, nx, ny, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }

    companion object {
        const val HOURS = 13

        /** Grid sample points for a mercator rectangle. */
        fun points(left: Double, top: Double, right: Double, bottom: Double, nx: Int, ny: Int): List<Pair<Double, Double>> =
            (0 until ny).flatMap { r -> (0 until nx).map { c ->
                Mercator.lat(top + (r + 0.5) * (bottom - top) / ny) to Mercator.lon(left + (c + 0.5) * (right - left) / nx)
            } }

        fun forecastUrl(pts: List<Pair<Double, Double>>) = "https://api.open-meteo.com/v1/forecast?" +
            "latitude=" + pts.joinToString(",") { String.format(Locale.US, "%.3f", it.first) } +
            "&longitude=" + pts.joinToString(",") { String.format(Locale.US, "%.3f", it.second) } +
            "&hourly=temperature_2m,precipitation,wind_speed_10m,wind_direction_10m&forecast_hours=$HOURS&timeformat=unixtime&timezone=GMT"

        fun airUrl(pts: List<Pair<Double, Double>>) = "https://air-quality-api.open-meteo.com/v1/air-quality?" +
            "latitude=" + pts.joinToString(",") { String.format(Locale.US, "%.3f", it.first) } +
            "&longitude=" + pts.joinToString(",") { String.format(Locale.US, "%.3f", it.second) } +
            "&hourly=us_aqi&forecast_hours=$HOURS&timeformat=unixtime&timezone=GMT"

        private val json = Json { ignoreUnknownKeys = true }

        private fun locations(raw: String): List<JsonObject> {
            val el = json.parseToJsonElement(raw)
            return when (el) {
                is JsonArray -> el.mapNotNull { it as? JsonObject }
                is JsonObject -> listOf(el)
                else -> emptyList()
            }
        }

        private fun series(o: JsonObject, key: String): FloatArray {
            val arr = ((o["hourly"] as? JsonObject)?.get(key) as? JsonArray) ?: return FloatArray(HOURS) { Float.NaN }
            return FloatArray(HOURS) { i -> ((arr.getOrNull(i) as? JsonPrimitive)?.doubleOrNull ?: Double.NaN).toFloat() }
        }

        fun parse(
            forecast: String, air: String?,
            left: Double, top: Double, right: Double, bottom: Double, nx: Int, ny: Int, zoom: Float,
        ): WeatherGrid? {
            val locs = locations(forecast)
            if (locs.size != nx * ny) return null
            val times = ((locs.first()["hourly"] as? JsonObject)?.get("time") as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull?.toLong() } ?: emptyList()
            fun layer(key: String, src: List<JsonObject>): List<FloatArray> {
                val per = src.map { series(it, key) }
                return List(HOURS) { h -> FloatArray(nx * ny) { i -> per.getOrNull(i)?.get(h) ?: Float.NaN } }
            }
            val values = mutableMapOf(
                MapLayer.Temperature to layer("temperature_2m", locs),
                MapLayer.Precipitation to layer("precipitation", locs),
                MapLayer.Wind to layer("wind_speed_10m", locs),
            )
            air?.let { locations(it) }?.takeIf { it.size == nx * ny }?.let { values[MapLayer.AirQuality] = layer("us_aqi", it) }
            return WeatherGrid(left, top, right, bottom, nx, ny, times, values, layer("wind_direction_10m", locs), zoom)
        }

        private val precipStops = listOf(
            0.1f to Color(0x6688DDEE), 0.5f to Color(0xA000A8E8), 2f to Color(0xB80070C8),
            5f to Color(0xC8FFE400), 10f to Color(0xD8FF7A00), 20f to Color(0xE8E81E1E),
        )
        private val windStops = listOf(
            0f to Color(0xFF5E9CF0), 15f to Color(0xFF4FC3C0), 30f to Color(0xFF8BD45A),
            45f to Color(0xFFF2D04A), 65f to Color(0xFFF2913A), 90f to Color(0xFFE5483B), 120f to Color(0xFFB0307A),
        )

        private fun ramp(stops: List<Pair<Float, Color>>, v: Float): Color {
            if (v <= stops.first().first) return stops.first().second
            if (v >= stops.last().first) return stops.last().second
            val i = stops.indexOfFirst { it.first > v }
            val (a, ca) = stops[i - 1]; val (b, cb) = stops[i]
            return lerp(ca, cb, (v - a) / (b - a))
        }

        fun colorFor(layer: MapLayer, v: Float): Int {
            if (v.isNaN()) return 0
            return when (layer) {
                MapLayer.Precipitation -> if (v < 0.1f) 0 else ramp(precipStops, v).toArgb()
                MapLayer.Temperature -> tempColor(v.toDouble()).copy(alpha = 0.62f).toArgb()
                MapLayer.Wind -> ramp(windStops, v).copy(alpha = 0.55f).toArgb()
                MapLayer.AirQuality -> AqiColors[(v / 50).toInt().coerceIn(0, AqiColors.size - 1)].copy(alpha = 0.55f).toArgb()
            }
        }

        fun legend(layer: MapLayer): List<Color> = when (layer) {
            MapLayer.Precipitation -> precipStops.map { it.second.copy(alpha = 1f) }
            MapLayer.Temperature -> listOf(-20.0, -5.0, 5.0, 15.0, 25.0, 35.0).map { tempColor(it) }
            MapLayer.Wind -> windStops.map { it.second }
            MapLayer.AirQuality -> AqiColors
        }
    }
}
