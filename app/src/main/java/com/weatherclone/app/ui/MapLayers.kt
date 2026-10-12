package com.weatherclone.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
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
) {
    private val bitmaps = HashMap<Pair<MapLayer, Int>, ImageBitmap>()

    fun lat(row: Int) = Mercator.lat(top + (row + 0.5) * (bottom - top) / ny)
    fun lon(col: Int) = Mercator.lon(left + (col + 0.5) * (right - left) / nx)

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

        /** Splits a multi-location response into per-location series for each of [keys]. */
        internal fun parseLocations(raw: String, keys: List<String>): Pair<List<Long>, List<Map<String, FloatArray>>> {
            val locs = locations(raw)
            val times = ((locs.firstOrNull()?.get("hourly") as? JsonObject)?.get("time") as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull?.toLong() } ?: emptyList()
            return times to locs.map { o -> keys.associateWith { series(o, it) } }
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

/**
 * Model data cached in fixed mercator blocks of [BLOCK] x [BLOCK] points. Blocks line up the same
 * way every time, so panning reuses what is loaded and only fetches new ground, and reopening the
 * map within the hour is instant. Open-Meteo counts every point as an API call (600 a minute), so
 * re-fetching the whole view on each pan used to hit the limit and leave the map stuck on the area
 * it first loaded. Lives in the ViewModel; only touched from the main thread.
 */
class GridCache(private val fetch: suspend (String) -> String) {
    data class Key(val level: Int, val x: Int, val y: Int)

    /** A range of blocks at one level. x may run past the antimeridian; keys wrap it. */
    data class View(val level: Int, val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        fun keys() = (y0..y1).flatMap { y -> (x0..x1).map { x -> Key(level, Math.floorMod(x, 1 shl level), y) } }.distinct()
    }

    private val forecast = HashMap<Key, Map<String, List<FloatArray>>>()
    private val air = HashMap<Key, Map<String, List<FloatArray>>>()
    private val pending = HashSet<Pair<Key, Boolean>>()
    private val failedAt = HashMap<Pair<Key, Boolean>, Long>()
    private var times: List<Long> = emptyList()
    private var hour = -1L

    /** Bumped whenever blocks arrive. */
    var version by mutableIntStateOf(0)
        private set
    var loading by mutableIntStateOf(0)
        private set

    /** Blocks covering a mercator rectangle; each block spans two map tiles at [zoom]. */
    fun view(left: Double, top: Double, right: Double, bottom: Double, zoom: Float): View {
        val level = (floor(zoom).toInt() - 1).coerceIn(1, 10)
        val n = 1 shl level
        return View(
            level, floor(left * n).toInt(), floor(top * n).toInt().coerceIn(0, n - 1),
            floor(right * n).toInt(), floor(bottom * n).toInt().coerceIn(0, n - 1),
        )
    }

    fun isComplete(view: View, airLayer: Boolean) = view.keys().all { it in store(airLayer) }
    fun isPending(view: View, airLayer: Boolean) = view.keys().any { (it to airLayer) in pending }

    private fun store(airLayer: Boolean) = if (airLayer) air else forecast

    /** Fetches whatever part of [view] is missing. Air quality is only fetched when it is shown. */
    suspend fun load(view: View, airLayer: Boolean) {
        val h = System.currentTimeMillis() / 3_600_000
        if (h != hour) { hour = h; forecast.clear(); air.clear(); failedAt.clear(); times = emptyList() }
        val now = System.currentTimeMillis()
        val want = view.keys().filter { k ->
            k !in store(airLayer) && (k to airLayer) !in pending && now - (failedAt[k to airLayer] ?: 0L) > 20_000
        }
        if (want.isEmpty()) return
        want.forEach { pending += it to airLayer }
        loading++
        try {
            coroutineScope {
                want.chunked(MAX_POINTS / (BLOCK * BLOCK)).map { async { fetchBlocks(it, airLayer) } }.awaitAll()
            }
        } finally {
            loading--
            want.forEach { pending -= it to airLayer }
        }
    }

    private suspend fun fetchBlocks(keys: List<Key>, airLayer: Boolean) {
        val pts = keys.flatMap { points(it) }
        val vars = if (airLayer) AIR_VARS else FORECAST_VARS
        val parsed = runCatching {
            val raw = fetch(if (airLayer) WeatherGrid.airUrl(pts) else WeatherGrid.forecastUrl(pts))
            withContext(Dispatchers.Default) { WeatherGrid.parseLocations(raw, vars) }
        }.getOrNull()
        if (parsed == null || parsed.second.size != pts.size) {
            keys.forEach { failedAt[it to airLayer] = System.currentTimeMillis() }
            return
        }
        val (t, locs) = parsed
        if (times.isEmpty()) times = t
        val per = BLOCK * BLOCK
        keys.forEachIndexed { i, k ->
            val slice = locs.subList(i * per, (i + 1) * per)
            store(airLayer)[k] = vars.associateWith { v -> slice.map { it.getValue(v) } }
        }
        version++
    }

    /** Sample points of a block, row by row, at the centres of its cells. */
    private fun points(k: Key): List<Pair<Double, Double>> {
        val n = (1 shl k.level).toDouble()
        return (0 until BLOCK * BLOCK).map { p ->
            Mercator.lat((k.y + (p / BLOCK + 0.5) / BLOCK) / n) to Mercator.lon((k.x + (p % BLOCK + 0.5) / BLOCK) / n)
        }
    }

    /** The loaded part of [view] stitched into one grid (missing blocks are NaN); null if none is loaded. */
    fun composite(view: View): WeatherGrid? {
        val n = 1 shl view.level
        val nx = (view.x1 - view.x0 + 1) * BLOCK
        val ny = (view.y1 - view.y0 + 1) * BLOCK
        fun empty() = List(WeatherGrid.HOURS) { FloatArray(nx * ny) { Float.NaN } }
        val out = (FORECAST_VARS + AIR_VARS).associateWith { empty() }
        var anyForecast = false; var anyAir = false
        for (by in view.y0..view.y1) for (bx in view.x0..view.x1) {
            val k = Key(view.level, Math.floorMod(bx, n), by)
            for ((src, vars) in listOf(forecast[k] to FORECAST_VARS, air[k] to AIR_VARS)) {
                if (src == null) continue
                if (vars === AIR_VARS) anyAir = true else anyForecast = true
                for (p in 0 until BLOCK * BLOCK) {
                    val i = ((by - view.y0) * BLOCK + p / BLOCK) * nx + (bx - view.x0) * BLOCK + p % BLOCK
                    for (v in vars) {
                        val series = src.getValue(v)[p]
                        val frames = out.getValue(v)
                        for (h in 0 until WeatherGrid.HOURS) frames[h][i] = series[h]
                    }
                }
            }
        }
        if (!anyForecast && !anyAir) return null
        val values = buildMap {
            if (anyForecast) {
                put(MapLayer.Temperature, out.getValue("temperature_2m"))
                put(MapLayer.Precipitation, out.getValue("precipitation"))
                put(MapLayer.Wind, out.getValue("wind_speed_10m"))
            }
            if (anyAir) put(MapLayer.AirQuality, out.getValue("us_aqi"))
        }
        return WeatherGrid(
            view.x0.toDouble() / n, view.y0.toDouble() / n, (view.x1 + 1).toDouble() / n, (view.y1 + 1).toDouble() / n,
            nx, ny, times, values, out.getValue("wind_direction_10m"),
        )
    }

    companion object {
        const val BLOCK = 4
        /** Points per request; Open-Meteo handles this comfortably and the URL stays a few KB. */
        private const val MAX_POINTS = 192
        private val FORECAST_VARS = listOf("temperature_2m", "precipitation", "wind_speed_10m", "wind_direction_10m")
        private val AIR_VARS = listOf("us_aqi")
    }
}
