package com.weatherclone.app.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherclone.app.data.Weather
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** In-memory tile cache shared by all maps; bumps [version] when a tile arrives. */
object Tiles {
    var client: OkHttpClient? = null
    private val cache = LruCache<String, ImageBitmap>(220)
    private val inFlight = HashSet<String>()
    private val failed = HashSet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var version by mutableIntStateOf(0)

    fun get(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        val c = client ?: return null
        synchronized(inFlight) {
            if (url in inFlight || url in failed) return null
            inFlight += url
        }
        scope.launch {
            val bmp = runCatching {
                c.newCall(Request.Builder().url(url).header("User-Agent", com.weatherclone.app.data.WeatherRepository.USER_AGENT).build()).execute().use { r ->
                    if (!r.isSuccessful) null else BitmapFactory.decodeStream(r.body.byteStream())?.asImageBitmap()
                }
            }.getOrNull()
            synchronized(inFlight) { inFlight -= url; if (bmp == null) failed += url }
            if (bmp != null) {
                cache.put(url, bmp)
                withContext(Dispatchers.Main) { version++ }
            }
        }
        return null
    }
}

data class Radar(val host: String, val frames: List<Pair<Long, String>>)

data class MapMarker(val lat: Double, val lon: Double, val label: String, val color: Color, val current: Boolean)

class MapState(lat: Double, lon: Double, zoom: Float) {
    var lat by mutableDoubleStateOf(lat)
    var lon by mutableDoubleStateOf(lon)
    var zoom by mutableFloatStateOf(zoom)
}

private const val RADAR_MAX_Z = 7
private const val ESRI = "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas"

/** On-screen size of one 256 px tile: 1.5x on a ~3x screen keeps labels crisp yet readable. */
private fun tileBase(density: Float) = 256f * (density / 2f).coerceAtLeast(1f)

private fun worldX(lon: Double, z: Int) = (lon + 180) / 360 * (1 shl z)
private fun worldY(lat: Double, z: Int): Double {
    val r = lat.coerceIn(-85.0, 85.0) * PI / 180
    return (1 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2 * (1 shl z)
}
private fun lonOf(x: Double, z: Int) = x / (1 shl z) * 360 - 180
private fun latOf(y: Double, z: Int): Double {
    val n = PI - 2 * PI * y / (1 shl z)
    return 180 / PI * atan(0.5 * (exp(n) - exp(-n)))
}

@Composable
fun TileMap(
    state: MapState,
    radar: Radar?,
    frame: Int,
    markers: List<MapMarker>,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    grid: WeatherGrid? = null,
    gridLayer: MapLayer? = null,
    gridHour: Int = 0,
    onSize: (IntSize) -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val units = LocalUnits.current
    val gestures = if (interactive) Modifier
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoomChange, _ ->
                val z = floor(state.zoom).toInt()
                val tile = tileBase(density) * 2f.pow(state.zoom - z)
                val cx = worldX(state.lon, z) * tile - pan.x
                val cy = worldY(state.lat, z) * tile - pan.y
                state.lon = lonOf(cx / tile, z)
                state.lat = latOf(cy / tile, z).coerceIn(-80.0, 80.0)
                if (zoomChange != 1f) {
                    state.zoom = (state.zoom + kotlin.math.log2(zoomChange)).coerceIn(2f, 11f)
                }
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { state.zoom = (state.zoom + 1).coerceAtMost(11f) })
        }
    else Modifier

    Canvas(modifier.background(Color(0xFF1B1D21)).onSizeChanged(onSize).then(gestures)) {
        @Suppress("UNUSED_VARIABLE") val v = Tiles.version
        val z = floor(state.zoom).toInt()
        val scale = 2f.pow(state.zoom - z)
        val tile = tileBase(density) * scale
        val cx = worldX(state.lon, z) * tile
        val cy = worldY(state.lat, z) * tile
        val left = cx - size.width / 2
        val top = cy - size.height / 2
        val n = 1 shl z
        val x0 = floor(left / tile).toInt(); val x1 = floor((left + size.width) / tile).toInt()
        val y0 = floor(top / tile).toInt().coerceAtLeast(0); val y1 = floor((top + size.height) / tile).toInt().coerceAtMost(n - 1)
        val radarPath = radar?.frames?.getOrNull(frame)?.second
        for (ty in y0..y1) for (tx in x0..x1) {
            val wx = ((tx % n) + n) % n
            val dst = IntOffset((tx * tile - left).toInt(), (ty * tile - top).toInt())
            val dstSize = IntSize(tile.toInt() + 1, tile.toInt() + 1)
            Tiles.get("$ESRI/World_Dark_Gray_Base/MapServer/tile/$z/$ty/$wx")?.let {
                drawImage(it, IntOffset.Zero, IntSize(it.width, it.height), dst, dstSize, filterQuality = FilterQuality.Medium)
            }
            if (radar != null && radarPath != null) {
                val rz = minOf(z, RADAR_MAX_Z)
                val f = 1 shl (z - rz)
                val px = wx / f; val py = ty / f
                Tiles.get("${radar.host}$radarPath/256/$rz/$px/$py/2/1_1.png")?.let { img ->
                    val sub = img.width / f
                    drawImage(
                        img, IntOffset((wx % f) * sub, (ty % f) * sub), IntSize(sub, sub), dst, dstSize, alpha = 0.8f,
                    )
                }
            }
        }
        if (grid != null && gridLayer != null) drawGrid(grid, gridLayer, gridHour, left, top, tile * n, measurer, units)
        // Place labels go above the weather layers, as in Apple's map.
        for (ty in y0..y1) for (tx in x0..x1) {
            val wx = ((tx % n) + n) % n
            Tiles.get("$ESRI/World_Dark_Gray_Reference/MapServer/tile/$z/$ty/$wx")?.let {
                drawImage(
                    it, IntOffset.Zero, IntSize(it.width, it.height),
                    IntOffset((tx * tile - left).toInt(), (ty * tile - top).toInt()), IntSize(tile.toInt() + 1, tile.toInt() + 1),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
        markers.forEach { m ->
            val mx = worldX(m.lon, z) * tile - left
            val my = worldY(m.lat, z) * tile - top
            drawMarker(measurer, m, Offset(mx.toFloat(), my.toFloat()))
        }
    }
}

/** Model grid drawn as a smooth (bilinear-scaled) colour field, plus wind arrows / temperature labels. */
private fun DrawScope.drawGrid(
    g: WeatherGrid, layer: MapLayer, hour: Int, left: Double, top: Double, worldPx: Float,
    measurer: TextMeasurer, units: com.weatherclone.app.data.Units,
) {
    val img = g.bitmap(layer, hour) ?: return
    val x0 = g.left * worldPx - left; val y0 = g.top * worldPx - top
    val x1 = g.right * worldPx - left; val y1 = g.bottom * worldPx - top
    drawImage(
        img, IntOffset.Zero, IntSize(g.nx, g.ny),
        IntOffset(x0.toInt(), y0.toInt()), IntSize((x1 - x0).toInt(), (y1 - y0).toInt()),
        filterQuality = FilterQuality.Low,
    )
    val cw = (x1 - x0) / g.nx; val ch = (y1 - y0) / g.ny
    val frame = g.values[layer]?.getOrNull(hour.coerceIn(0, WeatherGrid.HOURS - 1)) ?: return
    when (layer) {
        MapLayer.Wind -> {
            val dirs = g.windDir.getOrNull(hour.coerceIn(0, WeatherGrid.HOURS - 1)) ?: return
            for (r in 0 until g.ny) for (c in 0 until g.nx) {
                val d = dirs[r * g.nx + c]; if (d.isNaN()) continue
                val center = Offset((x0 + (c + 0.5) * cw).toFloat(), (y0 + (r + 0.5) * ch).toFloat())
                val len = 10 * density
                rotate(d + 180f, center) {
                    drawLine(Color.White.copy(alpha = 0.85f), Offset(center.x, center.y + len), Offset(center.x, center.y - len), 1.8f * density, StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.85f), Offset(center.x, center.y - len), Offset(center.x - 4 * density, center.y - len + 5 * density), 1.8f * density, StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.85f), Offset(center.x, center.y - len), Offset(center.x + 4 * density, center.y - len + 5 * density), 1.8f * density, StrokeCap.Round)
                }
            }
        }
        MapLayer.Temperature, MapLayer.AirQuality -> {
            for (r in 0 until g.ny step 2) for (c in (r / 2) % 2 until g.nx step 2) {
                val v = frame[r * g.nx + c]; if (v.isNaN()) continue
                val label = if (layer == MapLayer.Temperature) "${units.tempInt(v.toDouble())}°" else v.toInt().toString()
                val t = measurer.measure(label, Type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 4f)))
                val p = Offset((x0 + (c + 0.5) * cw).toFloat(), (y0 + (r + 0.5) * ch).toFloat())
                drawText(t, topLeft = Offset(p.x - t.size.width / 2, p.y - t.size.height / 2))
            }
        }
        MapLayer.Precipitation -> Unit
    }
}

private fun DrawScope.drawMarker(measurer: TextMeasurer, m: MapMarker, p: Offset) {
    if (m.current && m.label.isEmpty()) {
        drawCircle(Color(0x553D8BFF), 16 * density, p)
        drawCircle(Color.White, 8 * density, p)
        drawCircle(Color(0xFF0A84FF), 6 * density, p)
        return
    }
    val r = 17 * density
    drawCircle(Color.Black.copy(alpha = 0.35f), r + 2 * density, p.copy(y = p.y + 1.5f * density))
    drawCircle(m.color, r, p)
    drawCircle(Color.White, r, p, style = Stroke(2f * density))
    val t = measurer.measure(m.label, Type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White))
    drawText(t, topLeft = Offset(p.x - t.size.width / 2, p.y - t.size.height / 2))
}

@Composable
fun MapCard(w: Weather, radar: Radar?, onClick: () -> Unit) {
    val u = LocalUnits.current
    val state = remember(w.place.id) { MapState(w.place.lat, w.place.lon, 6f) }
    WeatherCard(title = "Precipitation", icon = Icons.Filled.Umbrella, onClick = onClick) {
        Box(Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(10.dp))) {
            TileMap(
                state, radar, (radar?.frames?.size ?: 1) - 1,
                listOf(MapMarker(w.place.lat, w.place.lon, u.temp(w.current.temp), tempColor(w.current.temp), w.place.isCurrent)),
                Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().clickable(onClick = onClick))
        }
    }
}

@Composable
fun MapScreen(
    weathers: List<Weather>,
    start: Weather?,
    radar: Radar?,
    grids: GridCache,
    onDone: () -> Unit,
    onList: () -> Unit,
) {
    val u = LocalUnits.current
    val center = start ?: weathers.firstOrNull()
    val state = remember { MapState(center?.place?.lat ?: 39.0, center?.place?.lon ?: -98.0, 6.5f) }
    var layer by remember { mutableStateOf(MapLayer.Precipitation) }
    var layerMenu by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf<WeatherGrid?>(null) }
    var gridLevel by remember { mutableIntStateOf(-1) }
    var view by remember { mutableStateOf<GridCache.View?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val loading = grids.loading > 0

    // Work out which blocks are on screen once the map settles (straight away on open) and fetch
    // the missing ones. Loads run in the screen's scope, so a further pan doesn't cancel them.
    LaunchedEffect(size, layer) {
        if (size == IntSize.Zero) return@LaunchedEffect
        var first = true
        snapshotFlow { Triple(state.lat, state.lon, state.zoom) }.collectLatest { (lat, lon, zoom) ->
            if (!first) delay(250)
            first = false
            val worldPx = tileBase(density) * 2.0.pow(zoom.toDouble())
            val w = size.width / worldPx; val h = size.height / worldPx
            val cx = Mercator.x(lon); val cy = Mercator.y(lat)
            val v = grids.view(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, zoom)
            view = v
            scope.launch { grids.load(v, layer == MapLayer.AirQuality) }
        }
    }
    // Show what's loaded for the current view. Within a level that only ever adds ground; after a
    // zoom changes the level, keep the old grid until the new one is complete (or has given up).
    LaunchedEffect(view, grids.version, grids.loading, layer) {
        val v = view ?: return@LaunchedEffect
        val air = layer == MapLayer.AirQuality
        if (v.level == gridLevel || grids.isComplete(v, air) || !grids.isPending(v, air)) {
            grids.composite(v)?.let { grid = it; gridLevel = v.level }
        }
    }

    val radarFrames = radar?.frames ?: emptyList()
    val forecastHours = if (grid != null) WeatherGrid.HOURS - 1 else 0
    val frameCount = if (layer == MapLayer.Precipitation) radarFrames.size + forecastHours else if (grid != null) WeatherGrid.HOURS else 1
    val nowIndex = if (layer == MapLayer.Precipitation) (radarFrames.size - 1).coerceAtLeast(0) else 0
    var frame by remember(layer) { mutableIntStateOf(nowIndex) }
    LaunchedEffect(nowIndex, layer) { if (frame >= frameCount) frame = nowIndex }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(playing, frameCount) {
        while (playing && frameCount > 1) {
            delay(if (layer == MapLayer.Precipitation && frame < radarFrames.size) 350 else 600)
            frame = (frame + 1) % frameCount
        }
    }

    // What to draw for the current frame.
    val radarFrame = if (layer == MapLayer.Precipitation && frame < radarFrames.size) frame else -1
    val gridHour = if (layer == MapLayer.Precipitation) frame - radarFrames.size + 1 else frame
    val showGrid = layer != MapLayer.Precipitation || frame >= radarFrames.size || radarFrames.isEmpty()
    val frameTime: Long? = when {
        radarFrame >= 0 -> radarFrames[radarFrame].first
        else -> grid?.times?.getOrNull(gridHour.coerceIn(0, WeatherGrid.HOURS - 1))
    }

    val markers = weathers.map { MapMarker(it.place.lat, it.place.lon, u.temp(it.current.temp), tempColor(it.current.temp), it.place.isCurrent) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        TileMap(
            state, radar.takeIf { radarFrame >= 0 }, radarFrame.coerceAtLeast(0),
            if (layer == MapLayer.Temperature) emptyList() else markers,
            Modifier.fillMaxSize(), interactive = true,
            grid = grid.takeIf { showGrid }, gridLayer = layer, gridHour = gridHour.coerceAtLeast(0),
            onSize = { size = it },
        )

        Row(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Pill(onClick = onDone) { Text("Done", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xE61C1C1E)).width(46.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(46.dp).clickable(onClick = onList), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, "Locations", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Box(Modifier.width(30.dp).height(0.5.dp).background(Hairline))
                Box(Modifier.size(46.dp).clickable {
                    val c = weathers.firstOrNull { it.place.isCurrent } ?: center
                    if (c != null) { state.lat = c.place.lat; state.lon = c.place.lon; state.zoom = 7f }
                }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MyLocation, "My location", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Box(Modifier.width(30.dp).height(0.5.dp).background(Hairline))
                Box {
                    Box(Modifier.size(46.dp).clickable { layerMenu = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Layers, "Map layers", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    DropdownMenu(layerMenu, { layerMenu = false }, containerColor = Color(0xFF2C2C2E), shape = RoundedCornerShape(14.dp)) {
                        MapLayer.entries.forEach { l ->
                            DropdownMenuItem(
                                text = { Text(l.title, color = Color.White) },
                                trailingIcon = { if (l == layer) Icon(Icons.Filled.Check, null, tint = Color.White) },
                                onClick = { layer = l; playing = false; layerMenu = false },
                            )
                        }
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).fillMaxWidth()
                .clip(RoundedCornerShape(18.dp)).background(Color(0xE61C1C1E)).padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { playing = !playing },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(layer.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    val label = when {
                        frameTime == null -> if (loading) "Loading…" else "Unavailable"
                        frame == nowIndex -> "Now"
                        else -> DateTimeFormatter.ofPattern(if (u.use24h) "EEE HH:mm" else "EEE h:mm a", Locale.US)
                            .format(Instant.ofEpochSecond(frameTime).atZone(ZoneId.systemDefault()))
                    }
                    Text(label, color = Secondary, fontSize = 14.sp)
                }
                if (loading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            }
            if (frameCount > 1) {
                IosSlider(
                    value = frame.toFloat().coerceIn(0f, (frameCount - 1).toFloat()),
                    onValueChange = { frame = it.roundToInt(); playing = false },
                    valueRange = 0f..(frameCount - 1).toFloat(),
                    marker = if (layer == MapLayer.Precipitation && frameCount > 1) nowIndex / (frameCount - 1).toFloat() else null,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (layer == MapLayer.Precipitation && radarFrames.isNotEmpty()) {
                        Text("−2h", color = Secondary, fontSize = 12.sp)
                        Text("Now", color = Secondary, fontSize = 12.sp)
                        if (forecastHours > 0) Text("+12h", color = Secondary, fontSize = 12.sp)
                    } else {
                        Text("Now", color = Secondary, fontSize = 12.sp)
                        Text("+12h", color = Secondary, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val (lo, hi) = when (layer) {
                MapLayer.Precipitation -> "Light" to "Heavy"
                MapLayer.Temperature -> u.temp(-20.0) to u.temp(35.0)
                MapLayer.Wind -> "Calm" to u.wind(120.0)
                MapLayer.AirQuality -> "Good" to "Hazardous"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lo, color = Secondary, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.horizontalGradient(WeatherGrid.legend(layer))))
                Spacer(Modifier.width(8.dp))
                Text(hi, color = Secondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (layer == MapLayer.Precipitation) "Map: Esri, HERE, Garmin, OpenStreetMap contributors · Radar © RainViewer · Forecast: Open-Meteo"
                else "Map: Esri, HERE, Garmin, OpenStreetMap contributors · Forecast model data: Open-Meteo",
                color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp,
            )
        }
    }
}

/** Thin track with a round white thumb, like UISlider. [marker] draws a tick (e.g. "Now"). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun IosSlider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, marker: Float?) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        thumb = {
            Box(
                Modifier.size(24.dp)
                    .shadow(3.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
                    .background(Color.White, CircleShape)
            )
        },
        track = { state ->
            val span = (state.valueRange.endInclusive - state.valueRange.start).takeIf { it > 0f } ?: 1f
            val frac = ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val h = size.height
                drawRoundRect(Color.White.copy(alpha = 0.22f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
                drawRoundRect(Color.White, size = androidx.compose.ui.geometry.Size(size.width * frac, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
                if (marker != null) drawCircle(Color.White, h * 0.9f, Offset(size.width * marker, h / 2))
            }
        },
    )
}

@Composable
fun Pill(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0xE61C1C1E))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
