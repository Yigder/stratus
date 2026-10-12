package com.weatherclone.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherclone.app.data.Alert
import com.weatherclone.app.data.Astro
import com.weatherclone.app.data.Summaries
import com.weatherclone.app.data.Units
import com.weatherclone.app.data.Weather
import com.weatherclone.app.data.WxIcon
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val SheetBg = Color(0xFF1C1C1E)
private val Panel = Color(0xFF2C2C2E)

private val chartMetrics = listOf(
    Metric.Conditions, Metric.Uv, Metric.Wind, Metric.Precip, Metric.Feels,
    Metric.Humidity, Metric.Visibility, Metric.Pressure,
)

@Composable
fun DetailSheet(w: Weather, metric: Metric, date: LocalDate?, onDismiss: () -> Unit) {
    IosSheet(onDismiss, SheetBg) { close -> DetailContent(w, metric, date, close) }
}

@Composable
fun DetailContent(w: Weather, initial: Metric, initialDate: LocalDate?, onClose: () -> Unit) {
    val u = LocalUnits.current
    var metric by remember { mutableStateOf(initial) }
    val days = w.forecastDays()
    var date by remember { mutableStateOf(initialDate ?: days.first().date) }
    var menu by remember { mutableStateOf(false) }
    val showDays = metric in chartMetrics || metric == Metric.Sun || metric == Metric.Aqi

    // Horizontal paging between days: the content follows the finger, then slides out and the
    // next day slides in from the other side.
    val scope = rememberCoroutineScope()
    var slide by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    val job = remember { arrayOfNulls<Job>(1) }
    fun indexOf(d: LocalDate) = days.indexOfFirst { it.date == d }
    fun go(target: LocalDate, velocity: Float = 0f) {
        val dir = (indexOf(target) - indexOf(date)).coerceIn(-1, 1)
        job[0]?.cancel()
        job[0] = scope.launch {
            if (dir == 0 || target == date) {
                animate(slide, 0f, velocity, spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> slide = v }
                return@launch
            }
            val w = width.toFloat()
            animate(slide, -dir * w, velocity, tween(160, easing = FastOutLinearInEasing)) { v, _ -> slide = v }
            date = target
            slide = dir * w * 0.6f
            animate(slide, 0f, 0f, spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> slide = v }
        }
    }
    val swipe = Modifier.daySwipe(
        enabled = { showDays },
        onStart = { job[0]?.cancel() },
        onDrag = { dx ->
            val i = indexOf(date)
            val blocked = (dx > 0 && slide >= 0f && i <= 0) || (dx < 0 && slide <= 0f && i >= days.size - 1)
            slide += if (blocked) dx * 0.3f else dx
        },
        onEnd = { v ->
            val i = indexOf(date)
            val threshold = width * 0.22f
            val step = when {
                v < -700f || (slide < -threshold && v < 300f) -> 1
                v > 700f || (slide > threshold && v > -300f) -> -1
                else -> 0
            }
            go(days.getOrNull(i + step)?.date ?: date, v)
        },
    )

    Column(Modifier.fillMaxSize().onSizeChanged { width = it.width.coerceAtLeast(1) }.then(swipe)) {
        // Title bar
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                Text(metric.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Box(
                Modifier.align(Alignment.CenterEnd).size(30.dp).clip(CircleShape).background(Color(0xFF3A3A3C)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, null, tint = Color(0xFFAEAEB2), modifier = Modifier.size(18.dp)) }
        }

        if (showDays) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEach { d ->
                    val sel = d.date == date
                    Column(
                        Modifier.weight(1f).clickable { go(d.date) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), fontSize = 12.sp, color = Secondary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(if (sel) Color.White else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(d.date.dayOfMonth.toString(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.Black else Color.White)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()).format(date),
                fontSize = 15.sp, color = Secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(0.5.dp).background(Hairline))
        }

        Column(
            Modifier
                .graphicsLayer {
                    translationX = slide
                    alpha = 1f - (abs(slide) / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f) * 0.5f
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Spacer(Modifier.height(14.dp))
            when (metric) {
                Metric.Sun -> SunDetail(w, date)
                Metric.Moon -> MoonDetail(w)
                Metric.Averages -> AveragesDetail(w)
                else -> ChartDetail(w, metric, date, menuButton = {
                    Box {
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF3A3A3C)).clickable { menu = true }.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(metric.title, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(menu, { menu = false }, containerColor = Color(0xFF2C2C2E), shape = RoundedCornerShape(14.dp)) {
                            (chartMetrics + listOfNotNull(Metric.Aqi.takeIf { w.air != null })).forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.title, color = Color.White) },
                                    leadingIcon = { if (m == metric) Icon(Icons.Filled.Check, null, tint = Color.White) else Spacer(Modifier.size(24.dp)) },
                                    onClick = { metric = m; menu = false },
                                )
                            }
                        }
                    }
                })
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/**
 * Sideways swipes anywhere in the sheet. Children see touches first, so a drag the chart is
 * scrubbing or the content is scrolling arrives here consumed and is left alone.
 */
@Composable
private fun Modifier.daySwipe(enabled: () -> Boolean, onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: (velocity: Float) -> Unit): Modifier {
    val on by rememberUpdatedState(enabled)
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!on()) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var total = Offset.Zero
            var locked = false
            while (true) {
                val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) break
                if (!locked && c.isConsumed) return@awaitEachGesture
                val delta = c.positionChange()
                tracker.addPosition(c.uptimeMillis, c.position)
                if (!locked) {
                    total += delta
                    when {
                        abs(total.x) > slop && abs(total.x) > abs(total.y) * 1.2f -> { locked = true; start() }
                        abs(total.y) > slop -> return@awaitEachGesture
                        else -> continue
                    }
                    c.consume()
                    continue
                }
                c.consume()
                drag(delta.x)
            }
            if (locked) end(tracker.calculateVelocity().x)
        }
    }
}

// --- Chart detail ----------------------------------------------------------------

private class Spec(
    val values: List<Double?>,
    val yMin: Double,
    val yMax: Double,
    val color: (Double) -> Color,
    val bars: Boolean = false,
    val fmt: (Double) -> String,
    val axis: (Double) -> String = { it.roundToInt().toString() },
    val headline: String,
    val sub: String,
    val secondary: List<Double?>? = null,
    /** How the faint second series reads out while scrubbing, e.g. "Feels Like 71°". */
    val secondaryLabel: ((Double) -> String)? = null,
    /** Names for the main and faint lines, shown under the chart. */
    val legend: Pair<String, String>? = null,
    val icons: List<WxIcon?>? = null,
    val arrows: List<Int?>? = null,
    val summaryTitle: String = "Daily Summary",
    val summary: String,
    val about: String,
)

private fun niceStep(range: Double): Double {
    val raw = range / 4
    val mag = Math.pow(10.0, floor(Math.log10(raw.coerceAtLeast(1e-6))))
    return listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= raw }
}

@Composable
private fun ChartDetail(w: Weather, metric: Metric, date: LocalDate, menuButton: @Composable () -> Unit) {
    val u = LocalUnits.current
    val hours = w.hoursOn(date)
    val day = w.dayOf(date)
    val isToday = date == w.now().toLocalDate()
    val now = w.now()
    val nowIdx = if (isToday) now.hour + now.minute / 60f else null
    // Fractional hour under the finger; only the chart's draw phase reads it, so scrubbing just redraws.
    var scrub by remember(metric, date) { mutableStateOf<Float?>(null) }
    val selected by remember(metric, date) { derivedStateOf { scrub?.roundToInt() } }

    val spec: Spec = when (metric) {
        Metric.Conditions, Metric.Feels -> {
            val feels = metric == Metric.Feels
            val v = hours.map { u.tempValue(if (feels) it.feels else it.temp) }
            val other = hours.map { u.tempValue(if (feels) it.temp else it.feels) }
            val lo = floor(min(v.minOrNull() ?: 0.0, other.minOrNull() ?: 0.0) - 2)
            val hi = ceil(max(v.maxOrNull() ?: 0.0, other.maxOrNull() ?: 0.0) + 2)
            val toC: (Double) -> Double = { if (u.s.fahrenheit) (it - 32) * 5 / 9 else it }
            Spec(
                values = v, yMin = lo, yMax = hi, color = { tempColor(toC(it)) },
                fmt = { "${it.roundToInt()}°" }, axis = { "${it.roundToInt()}°" },
                secondary = other,
                secondaryLabel = { if (feels) "Actual ${it.roundToInt()}°" else "Feels Like ${it.roundToInt()}°" },
                legend = if (feels) "Feels Like" to "Actual" else "Temperature" to "Feels Like",
                headline = if (isToday) u.temp(if (feels) w.current.feels else w.current.temp) else u.temp(if (feels) day!!.feelsHigh else day!!.high),
                sub = when {
                    feels -> "Actual: ${u.temp(if (isToday) w.current.temp else day!!.high)}"
                    isToday -> "H:${u.temp(day!!.high)}  L:${u.temp(day.low)}  ·  Feels Like ${u.temp(w.current.feels)}"
                    else -> "H:${u.temp(day!!.high)}  L:${u.temp(day.low)}"
                },
                icons = if (!feels) hours.map { it.condition.icon } else null,
                summary = if (feels) "Feels-like temperatures range from ${u.temp(day!!.feelsLow)} to ${u.temp(day.feelsHigh)}." else Summaries.daily(w, date, u),
                about = if (feels) "The feels-like temperature conveys how warm or cold it feels, combining the effects of wind, humidity and sunlight with the actual temperature."
                else "Temperatures shown are the air temperature about 2 metres above the ground. Icons show the expected sky conditions for each hour.",
            )
        }
        Metric.Uv -> {
            val v = hours.map { it.uv }
            Spec(
                values = v, yMin = 0.0, yMax = max(11.0, ceil(v.maxOrNull() ?: 0.0)), color = { uvColor(it) },
                fmt = { it.roundToInt().toString() },
                headline = (if (isToday) w.current.uv else day!!.uvMax).roundToInt().toString(),
                sub = Summaries.uvLevel(if (isToday) w.current.uv else day!!.uvMax),
                summary = "The UV index peaks at ${day!!.uvMax.roundToInt()} (${Summaries.uvLevel(day.uvMax)}).${if (isToday) " " + Summaries.uv(w, u) else ""}",
                about = "The World Health Organization's UV Index measures ultraviolet radiation. The higher the value, the greater the potential for damage, and the faster harm can occur. It can help you decide when to protect yourself from the sun.",
            )
        }
        Metric.Wind -> {
            val v = hours.map { u.windValue(it.wind) }
            val g = hours.map { u.windValue(it.gust) }
            Spec(
                values = v, yMin = 0.0, yMax = ceil(max(10.0, (g.maxOrNull() ?: 0.0) * 1.15)), color = { Color(0xFF64D2FF) },
                fmt = { "${it.roundToInt()} ${u.windLabel}" },
                headline = if (isToday) u.wind(w.current.wind) else u.wind(day!!.windMax),
                sub = "Gusts: ${if (isToday) u.wind(w.current.gust) else u.wind(day!!.gustMax)}",
                secondary = g, arrows = hours.map { it.windDir },
                secondaryLabel = { "Gusts ${it.roundToInt()} ${u.windLabel}" },
                legend = "Wind" to "Gusts",
                summary = "Wind speeds reach up to ${u.wind(day!!.windMax)}, with gusts to ${u.wind(day.gustMax)}. Prevailing wind from the ${Units.compass(day.windDir)}.",
                about = "Wind speed is the average over a short period at about 10 metres above the ground. Gusts are short, sudden bursts above that average. The arrows show the direction the wind is blowing towards.",
            )
        }
        Metric.Precip -> {
            val v = hours.map { u.precipValue(it.precip) }
            Spec(
                values = v, yMin = 0.0, yMax = max(if (u.s.precip == com.weatherclone.app.data.PrecipUnit.Inches) 0.1 else 2.0, (v.maxOrNull() ?: 0.0) * 1.2),
                color = { RainBlue }, bars = true,
                fmt = { u.precip(if (u.s.precip == com.weatherclone.app.data.PrecipUnit.Inches) it * 25.4 else it) },
                axis = { if (u.s.precip == com.weatherclone.app.data.PrecipUnit.Inches) String.format(Locale.US, "%.2f", it).removePrefix("0") else String.format(Locale.US, "%.1f", it).removeSuffix(".0") },
                headline = u.precip(day!!.precipSum), sub = "Total for the day",
                secondary = hours.map { it.precipChance.toDouble() },
                secondaryLabel = { "Chance ${it.roundToInt()}%" },
                summary = if (day.precipSum < 0.25) "No precipitation expected." else "${u.precip(day.precipSum)} of precipitation expected, with a ${Summaries.roundTo10(day.precipChance)}% chance at its peak.",
                about = "Precipitation amounts are the liquid equivalent expected each hour. The chance of precipitation is the likelihood of measurable precipitation falling during that hour.",
            )
        }
        Metric.Humidity -> {
            val v = hours.map { it.humidity.toDouble() }
            Spec(
                values = v, yMin = 0.0, yMax = 100.0, color = { Color(0xFF5AC8FA) },
                fmt = { "${it.roundToInt()}%" }, axis = { "${it.roundToInt()}%" },
                headline = "${if (isToday) w.current.humidity else v.average().roundToInt()}%",
                sub = "Dew Point: ${u.temp(if (isToday) w.current.dewPoint else hours.map { it.dewPoint }.average())}",
                summary = "Average humidity is ${v.average().roundToInt()}%. The dew point ranges from ${u.temp(hours.minOf { it.dewPoint })} to ${u.temp(hours.maxOf { it.dewPoint })}.",
                about = "Relative humidity is the amount of moisture in the air compared to what the air can hold at that temperature. The dew point is the temperature to which air must cool to become saturated — a higher dew point feels more humid.",
            )
        }
        Metric.Visibility -> {
            val v = hours.map { u.distanceValue(it.visibility) }
            Spec(
                values = v, yMin = 0.0, yMax = ceil(max(10.0, (v.maxOrNull() ?: 0.0) * 1.1)), color = { Color(0xFFBF9BFF) },
                fmt = { "${String.format(Locale.US, "%.1f", it).removeSuffix(".0")} ${u.distanceLabel}" },
                headline = "${u.distanceNumber(if (isToday) w.current.visibility else hours.map { it.visibility }.average())} ${u.distanceLabel}",
                sub = if (isToday) Summaries.visibility(w).removeSuffix(".") else "Average",
                summary = "Visibility ranges from ${u.distanceNumber(hours.minOf { it.visibility })} to ${u.distanceNumber(hours.maxOf { it.visibility })} ${u.distanceLabel}.",
                about = "Visibility is how far away you can clearly see objects like buildings and hills. It is reduced by fog, haze, smoke, rain and snow.",
            )
        }
        Metric.Pressure -> {
            val v = hours.map { u.pressureValue(it.pressure) }
            val pad = u.pressureValue(1016.0) - u.pressureValue(1013.0)
            Spec(
                values = v, yMin = (v.minOrNull() ?: 0.0) - pad, yMax = (v.maxOrNull() ?: 0.0) + pad, color = { Color(0xFF7D7AFF) },
                fmt = {
                    when (u.s.pressure) {
                        com.weatherclone.app.data.PressureUnit.InHg -> String.format(Locale.US, "%.2f", it)
                        com.weatherclone.app.data.PressureUnit.KPa -> String.format(Locale.US, "%.1f", it)
                        else -> it.roundToInt().toString()
                    } + " " + u.pressureLabel
                },
                axis = { if (u.s.pressure == com.weatherclone.app.data.PressureUnit.InHg) String.format(Locale.US, "%.2f", it) else it.roundToInt().toString() },
                headline = "${u.pressureNumber(if (isToday) w.current.pressure else hours.map { it.pressure }.average())} ${u.pressureLabel}",
                sub = if (isToday) Summaries.pressure(w) else "Average",
                summary = "Pressure ranges from ${u.pressureNumber(hours.minOf { it.pressure })} to ${u.pressureNumber(hours.maxOf { it.pressure })} ${u.pressureLabel}.",
                about = "Rapid, significant pressure changes are used to predict changes in the weather. Falling pressure can mean rain or snow is on the way, while rising pressure usually brings improving weather.",
            )
        }
        Metric.Aqi -> {
            val a = w.air
            val map = a?.hourly?.filter { it.first.toLocalDate() == date }?.associate { it.first.hour to it.second.toDouble() } ?: emptyMap()
            val v = List(24) { map[it] }
            Spec(
                values = v, yMin = 0.0, yMax = max(150.0, (v.filterNotNull().maxOrNull() ?: 0.0) * 1.2),
                color = { aqi -> AqiColors[((aqi / 50).toInt()).coerceIn(0, AqiColors.size - 1)] },
                fmt = { it.roundToInt().toString() },
                headline = (if (isToday) a?.aqi else v.filterNotNull().maxOrNull()?.roundToInt())?.toString() ?: "--",
                sub = (if (isToday) a?.aqi else v.filterNotNull().maxOrNull()?.roundToInt())?.let { Summaries.aqiLevel(it) } ?: "No data",
                summary = a?.let { aq ->
                    buildString {
                        append(Summaries.aqi(aq))
                        aq.pm25?.let { append(" PM2.5: ${it.roundToInt()} μg/m³.") }
                        aq.pm10?.let { append(" PM10: ${it.roundToInt()} μg/m³.") }
                        aq.ozone?.let { append(" Ozone: ${it.roundToInt()} μg/m³.") }
                        aq.no2?.let { append(" NO₂: ${it.roundToInt()} μg/m³.") }
                    }
                } ?: "",
                about = "The U.S. Air Quality Index runs from 0 to 500. The higher the value, the greater the level of air pollution and the greater the health concern. Data from the CAMS model via Open-Meteo.",
            )
        }
        else -> return
    }

    val sel = selected
    val selValue = sel?.let { spec.values.getOrNull(it) }
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (selValue != null) spec.fmt(selValue) else spec.headline, fontSize = 30.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Medium, color = Color.White)
                if (metric == Metric.Conditions) {
                    Spacer(Modifier.width(8.dp))
                    val icon = if (sel != null) hours.getOrNull(sel)?.condition?.icon else if (isToday) w.condition.icon else day?.condition?.icon
                    if (icon != null) WeatherIcon(icon, 28.dp)
                }
            }
            Text(
                if (sel != null) {
                    val time = hours.getOrNull(sel)?.let { u.hour(it.time) } ?: ""
                    val extra = spec.secondaryLabel?.let { f -> spec.secondary?.getOrNull(sel)?.let(f) }
                    if (extra != null) "$time  ·  $extra" else time
                } else spec.sub,
                fontSize = 15.sp, color = Secondary, maxLines = 1,
            )
        }
        menuButton()
    }
    Spacer(Modifier.height(12.dp))
    Chart(spec, nowIdx, selected = { scrub }, onSelect = { scrub = it }, modifier = Modifier.fillMaxWidth().height(if (spec.icons != null || spec.arrows != null) 270.dp else 240.dp))
    spec.legend?.takeIf { !spec.bars }?.let { (main, faint) ->
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendSwatch(spec.color(spec.yMax - (spec.yMax - spec.yMin) * 0.3), 1f)
            Text(main, fontSize = 13.sp, color = Secondary)
            Spacer(Modifier.width(16.dp))
            LegendSwatch(spec.color(spec.yMax - (spec.yMax - spec.yMin) * 0.3), 0.4f)
            Text(faint, fontSize = 13.sp, color = Secondary)
        }
    }

    if (metric == Metric.Precip && spec.secondary != null) {
        Spacer(Modifier.height(20.dp))
        Text("Chance of Precipitation", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Chart(
            Spec(values = spec.secondary, yMin = 0.0, yMax = 100.0, color = { RainBlue }, fmt = { "${it.roundToInt()}%" }, axis = { "${it.roundToInt()}%" }, headline = "", sub = "", summary = "", about = ""),
            nowIdx, { null }, {}, Modifier.fillMaxWidth().height(160.dp), interactive = false,
        )
    }

    InfoSection(spec.summaryTitle, spec.summary)
    if (metric == Metric.Conditions) Summaries.comparison(w, date, u)?.let { InfoSection("Daily Comparison", it) }
    InfoSection("About ${metric.title}", spec.about, dim = true)
}

@Composable
private fun LegendSwatch(color: Color, alpha: Float) {
    Box(Modifier.padding(end = 6.dp).size(width = 14.dp, height = 3.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = alpha)))
}

@Composable
private fun InfoSection(title: String, text: String, dim: Boolean = false) {
    if (text.isBlank()) return
    Spacer(Modifier.height(22.dp))
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    Spacer(Modifier.height(8.dp))
    Text(
        text, fontSize = 15.sp, lineHeight = 21.sp, color = if (dim) Secondary else Color.White,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).padding(14.dp),
    )
}

@Composable
private fun Chart(
    spec: Spec,
    nowIdx: Float?,
    selected: () -> Float?,
    onSelect: (Float?) -> Unit,
    modifier: Modifier,
    interactive: Boolean = true,
) {
    val u = LocalUnits.current
    val measurer = rememberTextMeasurer(cacheSize = 48)
    val labelStyle = Type.body.copy(fontSize = 11.sp, color = Secondary)
    val n = spec.values.size.coerceAtLeast(2)
    val currentN by rememberUpdatedState(n)
    val bars by rememberUpdatedState(spec.bars)
    val select by rememberUpdatedState(onSelect)
    Canvas(
        modifier.then(if (!interactive) Modifier else Modifier.pointerInput(Unit) {
            // Fractional hour under x. Bars are centred in their hour, lines sit on the hour mark.
            fun hourAt(x: Float): Float {
                val cw = size.width - 40.dp.toPx()
                val f = x / cw * 24f - if (bars) 0.5f else 0f
                return f.coerceIn(0f, (currentN - 1).toFloat())
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slop = viewConfiguration.touchSlop
                var x = down.position.x
                // Decide between scrubbing and letting the sheet scroll: a sideways drag or a brief
                // hold scrubs, a vertical drag goes to the scroll view untouched.
                val scrubbing = withTimeoutOrNull(140L) {
                    var total = Offset.Zero
                    while (true) {
                        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                        if (!c.pressed) return@withTimeoutOrNull false
                        total += c.positionChange()
                        x = c.position.x
                        if (abs(total.x) > slop && abs(total.x) > abs(total.y)) { c.consume(); return@withTimeoutOrNull true }
                        if (abs(total.y) > slop) return@withTimeoutOrNull false
                    }
                    false
                } ?: true
                if (!scrubbing) return@awaitEachGesture
                select(hourAt(x))
                while (true) {
                    val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed) break
                    c.consume()
                    select(hourAt(c.position.x))
                }
                select(null)
            }
        })
    ) {
        val right = 40.dp.toPx()
        val topPad = if (spec.icons != null || spec.arrows != null) 34.dp.toPx() else 8.dp.toPx()
        val bottom = 22.dp.toPx()
        val cw = size.width - right
        val ch = size.height - topPad - bottom
        val range = (spec.yMax - spec.yMin).takeIf { it > 0 } ?: 1.0
        fun xOf(i: Float) = i / 24f * cw
        fun yOf(v: Double) = topPad + ch - ((v - spec.yMin) / range * ch).toFloat()

        // Grid
        val step = niceStep(range)
        var t = ceil(spec.yMin / step) * step
        while (t <= spec.yMax + 1e-9) {
            val y = yOf(t)
            drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, y), Offset(cw, y), 1f)
            val l = measurer.measure(spec.axis(t), labelStyle)
            drawText(l, topLeft = Offset(cw + 6.dp.toPx(), y - l.size.height / 2))
            t += step
        }
        for (h in 0..24 step 6) {
            val x = xOf(h.toFloat())
            drawLine(Color.White.copy(alpha = 0.18f), Offset(x, topPad), Offset(x, topPad + ch), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 6f)))
            if (h < 24) {
                val label = if (u.use24h) "%02d".format(h) else listOf("12AM", "6AM", "12PM", "6PM")[h / 6]
                val l = measurer.measure(label, labelStyle)
                drawText(l, topLeft = Offset(x + 3.dp.toPx(), topPad + ch + 4.dp.toPx()))
            }
        }

        // Top row: condition icons or wind arrows every 2 hours
        spec.icons?.let { icons ->
            for (i in icons.indices step 2) {
                val ic = icons[i] ?: continue
                val s = 20.dp.toPx()
                val x = xOf(i + 0.5f) - s / 2
                val sc = s / 100f
                val top = 4.dp.toPx()
                withTransform({ translate(x, top); scale(sc, sc, Offset.Zero) }) { drawWx(ic) }
            }
        }
        spec.arrows?.let { arrows ->
            for (i in arrows.indices step 2) {
                val d = arrows[i] ?: continue
                val c = Offset(xOf(i + 0.5f), 16.dp.toPx())
                rotate(d + 180f, c) {
                    val len = 8.dp.toPx()
                    drawLine(Color.White.copy(alpha = 0.8f), Offset(c.x, c.y + len), Offset(c.x, c.y - len), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.8f), Offset(c.x, c.y - len), Offset(c.x - 4.dp.toPx(), c.y - len + 5.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.8f), Offset(c.x, c.y - len), Offset(c.x + 4.dp.toPx(), c.y - len + 5.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                }
            }
        }

        val colors = List(8) { k -> spec.color(spec.yMax - range * k / 7) }
        val brush = Brush.verticalGradient(colors, startY = topPad, endY = topPad + ch)

        if (spec.bars) {
            val bw = cw / 24f
            spec.values.forEachIndexed { i, v ->
                if (v == null || v <= 0) return@forEachIndexed
                val y = yOf(v)
                drawRoundRect(spec.color(v), Offset(xOf(i.toFloat()) + bw * 0.18f, y), Size(bw * 0.64f, topPad + ch - y), CornerRadius(3.dp.toPx()))
            }
        } else {
            fun drawSeries(values: List<Double?>, alpha: Float, fill: Boolean, width: Float) {
                val path = Path(); val area = Path()
                var started = false; var lastX = 0f; var firstX = 0f
                values.forEachIndexed { i, v ->
                    if (v == null) return@forEachIndexed
                    val x = xOf(i.toFloat())
                    val y = yOf(v)
                    if (!started) { path.moveTo(x, y); area.moveTo(x, topPad + ch); area.lineTo(x, y); started = true; firstX = x }
                    else { path.lineTo(x, y); area.lineTo(x, y) }
                    lastX = x
                }
                if (!started) return
                if (fill) {
                    area.lineTo(lastX, topPad + ch); area.lineTo(firstX, topPad + ch); area.close()
                    drawPath(area, Brush.verticalGradient(colors.map { it.copy(alpha = 0.35f * alpha) } + Color.Transparent, startY = topPad, endY = topPad + ch))
                }
                drawPath(path, brush, alpha = alpha, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            spec.secondary?.takeIf { !spec.bars }?.let { drawSeries(it, 0.4f, false, 2.dp.toPx()) }
            drawSeries(spec.values, 1f, true, 3.5f.dp.toPx())
        }

        // Dim the part of today that is already over.
        if (nowIdx != null) {
            val nx = xOf(nowIdx)
            clipRect(0f, topPad - 2, nx, topPad + ch + 2) { drawRect(SheetBg.copy(alpha = 0.45f)) }
            val i0 = floor(nowIdx).toInt().coerceIn(0, n - 1)
            val i1 = (i0 + 1).coerceAtMost(n - 1)
            val v0 = spec.values.getOrNull(i0); val v1 = spec.values.getOrNull(i1)
            if (v0 != null && v1 != null && !spec.bars) {
                val v = v0 + (v1 - v0) * (nowIdx - i0)
                drawCircle(SheetBg, 7.dp.toPx(), Offset(nx, yOf(v)))
                drawCircle(Color.White, 5.dp.toPx(), Offset(nx, yOf(v)))
            }
        }

        val s = selected()
        if (s != null) {
            if (spec.bars) {
                val i = s.roundToInt()
                val x = xOf(i + 0.5f)
                drawLine(Color.White.copy(alpha = 0.7f), Offset(x, topPad), Offset(x, topPad + ch), 1.5f.dp.toPx())
                spec.values.getOrNull(i)?.takeIf { it > 0 }?.let { v ->
                    val bw = cw / 24f
                    val y = yOf(v)
                    drawRoundRect(Color.White, Offset(xOf(i.toFloat()) + bw * 0.18f, y), Size(bw * 0.64f, topPad + ch - y), CornerRadius(3.dp.toPx()))
                }
            } else {
                // The line follows the finger; the dot rides the curve between hourly points.
                val x = xOf(s)
                drawLine(Color.White.copy(alpha = 0.7f), Offset(x, topPad), Offset(x, topPad + ch), 1.5f.dp.toPx())
                fun at(values: List<Double?>): Double? {
                    val i0 = floor(s).toInt().coerceIn(0, values.size - 1)
                    val i1 = (i0 + 1).coerceAtMost(values.size - 1)
                    val a = values.getOrNull(i0) ?: return null
                    val b = values.getOrNull(i1) ?: return a
                    return a + (b - a) * (s - i0)
                }
                spec.secondary?.let { at(it) }?.let { v -> drawCircle(Color.White.copy(alpha = 0.5f), 3.5f.dp.toPx(), Offset(x, yOf(v))) }
                at(spec.values)?.let { v ->
                    drawCircle(SheetBg, 7.dp.toPx(), Offset(x, yOf(v)))
                    drawCircle(Color.White, 5.dp.toPx(), Offset(x, yOf(v)))
                }
            }
        }
    }
}

// --- Sun ---------------------------------------------------------------------

@Composable
private fun SunDetail(w: Weather, date: LocalDate) {
    val u = LocalUnits.current
    val day = w.dayOf(date) ?: return
    val (first, last) = remember(date) { Astro.twilight(date, w.zone, w.place.lat, w.place.lon) }
    val hrs = (day.daylightSeconds / 3600).toInt()
    val mins = ((day.daylightSeconds % 3600) / 60).roundToInt()
    Text("$hrs hr $mins min", fontSize = 30.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Medium, color = Color.White)
    Text("Total Daylight", fontSize = 15.sp, color = Secondary)
    Spacer(Modifier.height(12.dp))
    val isToday = date == w.now().toLocalDate()
    val now = w.now()
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val samples = 144
        val alts = List(samples + 1) { i ->
            val t = date.atStartOfDay(w.zone).toInstant().plusSeconds(i * 600L)
            Astro.sunAltitude(t, w.place.lat, w.place.lon)
        }
        val maxAlt = max(30.0, alts.max()); val minAlt = kotlin.math.min(-30.0, alts.min())
        val horizon = (size.height * (maxAlt / (maxAlt - minAlt))).toFloat()
        fun y(a: Double) = (size.height * ((maxAlt - a) / (maxAlt - minAlt))).toFloat()
        val path = Path()
        alts.forEachIndexed { i, a -> val x = i / samples.toFloat() * size.width; if (i == 0) path.moveTo(x, y(a)) else path.lineTo(x, y(a)) }
        clipRect(bottom = horizon) {
            drawPath(path, Brush.verticalGradient(listOf(SunYellow, Color(0xFFFF9F0A)), 0f, horizon), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        clipRect(top = horizon) { drawPath(path, Color.White.copy(alpha = 0.25f), style = Stroke(3.dp.toPx())) }
        drawLine(Color.White.copy(alpha = 0.4f), Offset(0f, horizon), Offset(size.width, horizon), 1.dp.toPx())
        for (h in 0..24 step 6) {
            val x = h / 24f * size.width
            drawLine(Color.White.copy(alpha = 0.15f), Offset(x, 0f), Offset(x, size.height), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 6f)))
            if (h < 24) {
                val l = measurer.measure(if (u.use24h) "%02d".format(h) else listOf("12AM", "6AM", "12PM", "6PM")[h / 6], Type.body.copy(fontSize = 11.sp, color = Secondary))
                drawText(l, topLeft = Offset(x + 3.dp.toPx(), size.height - l.size.height))
            }
        }
        if (isToday) {
            val f = (now.toLocalTime().toSecondOfDay() / 86400f)
            val a = Astro.sunAltitude(Instant.now(), w.place.lat, w.place.lon)
            val p = Offset(f * size.width, y(a))
            drawCircle(Brush.radialGradient(listOf(SunYellow.copy(alpha = 0.6f), Color.Transparent), p, 18.dp.toPx()), 18.dp.toPx(), p)
            drawCircle(if (a > 0) SunYellow else Color.White.copy(alpha = 0.6f), 6.dp.toPx(), p)
        }
    }
    Spacer(Modifier.height(16.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).padding(horizontal = 14.dp)) {
        listOf(
            "First Light" to first?.let { u.time(it) },
            "Sunrise" to day.sunrise?.let { u.time(it) },
            "Sunset" to day.sunset?.let { u.time(it) },
            "Last Light" to last?.let { u.time(it) },
            "Total Daylight" to "$hrs hr $mins min",
        ).forEachIndexed { i, (k, v) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Hairline))
            Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k, fontSize = 16.sp, color = Color.White, modifier = Modifier.weight(1f))
                Text(v ?: "--", fontSize = 16.sp, color = Secondary)
            }
        }
    }
    InfoSection("About Sunrise and Sunset", "First light and last light are the beginning and end of civil twilight, when the sun is less than 6° below the horizon and there is enough light for most outdoor activities.", dim = true)
}

// --- Moon --------------------------------------------------------------------

@Composable
private fun MoonDetail(w: Weather) {
    val u = LocalUnits.current
    val now = Instant.now()
    val today = w.now().toLocalDate()
    val (rise, set) = remember(today) { Astro.moonTimes(today, w.zone, w.place.lat, w.place.lon) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Astro.phaseName(now), fontSize = 26.sp, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(Modifier.height(18.dp))
        MoonPhase(Astro.moonAge(now) / 29.530588853, 200.dp)
        Spacer(Modifier.height(18.dp))
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).padding(horizontal = 14.dp)) {
        listOf(
            "Illumination" to "${(Astro.illumination(now) * 100).roundToInt()}%",
            "Moonrise" to (rise?.let { u.time(it) } ?: "--"),
            "Moonset" to (set?.let { u.time(it) } ?: "--"),
            "Next Full Moon" to Astro.daysUntilFull(now).let { if (it == 0) "Today" else "$it ${if (it == 1) "Day" else "Days"}" },
            "Next New Moon" to Astro.daysUntilNew(now).let { if (it == 0) "Today" else "$it ${if (it == 1) "Day" else "Days"}" },
        ).forEachIndexed { i, (k, v) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Hairline))
            Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k, fontSize = 16.sp, color = Color.White, modifier = Modifier.weight(1f))
                Text(v, fontSize = 16.sp, color = Secondary)
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    // Upcoming phases for the next two weeks.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (d in 0 until 7) {
            val t = now.plusSeconds(d * 2 * 86400L)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MoonPhase(Astro.moonAge(t) / 29.530588853, 34.dp)
                Spacer(Modifier.height(4.dp))
                Text(today.plusDays(d * 2L).dayOfMonth.toString(), fontSize = 12.sp, color = Secondary)
            }
        }
    }
    InfoSection("About the Moon", "Moon phases are based on the moon's position relative to the earth and sun. Moonrise and moonset times are calculated for this location.", dim = true)
}

// --- Averages ----------------------------------------------------------------

@Composable
private fun AveragesDetail(w: Weather) {
    val u = LocalUnits.current
    val n = w.normals ?: return
    val delta = u.tempDelta(w.today.high - n.todayHigh)
    Text("${Units.signed(delta)}°", fontSize = 30.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Medium, color = Color.White)
    Text(if (delta >= 0) "above average daily high" else "below average daily high", fontSize = 15.sp, color = Secondary)
    Spacer(Modifier.height(16.dp))
    Text("Normal Range by Month", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    Spacer(Modifier.height(8.dp))
    val measurer = rememberTextMeasurer()
    val month = w.now().monthValue - 1
    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
        val lows = n.monthlyLow.map { u.tempValue(it) }
        val highs = n.monthlyHigh.map { u.tempValue(it) }
        val lo = floor((lows.filter { !it.isNaN() }.minOrNull() ?: 0.0) - 3)
        val hi = ceil((highs.filter { !it.isNaN() }.maxOrNull() ?: 0.0) + 3)
        val bottomPad = 20.dp.toPx()
        val ch = size.height - bottomPad
        fun y(v: Double) = (ch - (v - lo) / (hi - lo) * ch).toFloat()
        val bw = size.width / 12f
        val toC: (Double) -> Double = { if (u.s.fahrenheit) (it - 32) * 5 / 9 else it }
        for (m in 0 until 12) {
            if (lows[m].isNaN()) continue
            val x = m * bw + bw / 2
            drawLine(
                Brush.verticalGradient(listOf(tempColor(toC(highs[m])), tempColor(toC(lows[m]))), y(highs[m]), y(lows[m])),
                Offset(x, y(highs[m])), Offset(x, y(lows[m])), bw * 0.45f, StrokeCap.Round,
                alpha = if (m == month) 1f else 0.55f,
            )
            val l = measurer.measure(java.time.Month.of(m + 1).getDisplayName(TextStyle.NARROW, Locale.getDefault()), Type.body.copy(fontSize = 11.sp, color = if (m == month) Color.White else Secondary))
            drawText(l, topLeft = Offset(x - l.size.width / 2, size.height - l.size.height))
        }
        val tx = month * bw + bw / 2
        drawCircle(Color.White, 4.dp.toPx(), Offset(tx, y(u.tempValue(w.today.high))))
    }
    Spacer(Modifier.height(10.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).padding(horizontal = 14.dp)) {
        listOf(
            "Today's High" to u.temp(w.today.high),
            "Average High" to u.temp(n.todayHigh),
            "Today's Low" to u.temp(w.today.low),
            "Average Low" to u.temp(n.todayLow),
        ).forEachIndexed { i, (k, v) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Hairline))
            Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k, fontSize = 16.sp, color = Color.White, modifier = Modifier.weight(1f))
                Text(v, fontSize = 16.sp, color = Secondary)
            }
        }
    }
    InfoSection("About Averages", "Averages are calculated from the last ten years of observed daily highs and lows at this location (Open-Meteo historical weather API).", dim = true)
}

// --- Alerts ------------------------------------------------------------------

@Composable
fun AlertSheet(alert: Alert, onDismiss: () -> Unit) {
    IosSheet(onDismiss, SheetBg) { close ->
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Severe Weather", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    Modifier.align(Alignment.CenterEnd).size(30.dp).clip(CircleShape).background(Color(0xFF3A3A3C)).clickable(onClick = close),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, null, tint = Color(0xFFAEAEB2), modifier = Modifier.size(18.dp)) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
                Text(alert.event, fontSize = 26.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(alert.sender, fontSize = 15.sp, color = Secondary)
                if (alert.headline.isNotBlank()) InfoSection("Summary", alert.headline)
                InfoSection("Details", alert.description.replace(Regex("(?<!\n)\n(?!\n)"), " "))
                if (alert.instruction.isNotBlank()) InfoSection("Precautionary Actions", alert.instruction.replace(Regex("(?<!\n)\n(?!\n)"), " "))
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}
