package com.weatherclone.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherclone.app.data.Alert
import com.weatherclone.app.data.Astro
import com.weatherclone.app.data.Summaries
import com.weatherclone.app.data.Units
import com.weatherclone.app.data.Weather
import com.weatherclone.app.data.WxIcon
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class Metric(val title: String) {
    Conditions("Conditions"), Uv("UV Index"), Wind("Wind"), Precip("Precipitation"),
    Feels("Feels Like"), Humidity("Humidity"), Visibility("Visibility"), Pressure("Pressure"),
    Aqi("Air Quality"), Sun("Sun"), Moon("Moon"), Averages("Averages"),
}

typealias OpenDetail = (Metric, LocalDate?) -> Unit

// --- Hourly ------------------------------------------------------------------

private sealed interface HourEntry {
    data class H(val hour: com.weatherclone.app.data.Hour, val now: Boolean) : HourEntry
    data class Sun(val time: LocalDateTime, val rise: Boolean) : HourEntry
}

@Composable
fun HourlyCard(w: Weather, open: OpenDetail) {
    val u = LocalUnits.current
    val entries = remember(w, u) {
        val hrs = w.upcomingHours(25)
        val now = w.now()
        val events = w.days.flatMap { d -> listOfNotNull(d.sunrise?.let { HourEntry.Sun(it, true) }, d.sunset?.let { HourEntry.Sun(it, false) }) }
        buildList {
            hrs.forEachIndexed { i, h ->
                add(HourEntry.H(h, i == 0))
                val end = h.time.plusHours(1)
                events.filter { !it.time.isBefore(h.time) && it.time.isBefore(end) && it.time.isAfter(now) }.forEach { add(it) }
            }
        }
    }
    WeatherCard(
        header = {
            Text(
                Summaries.hourly(w, u),
                style = Type.body.copy(fontSize = 15.sp, lineHeight = 20.sp),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
            )
        },
        divider = true,
        contentPadding = false,
        onClick = { open(Metric.Conditions, null) },
    ) {
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
            items(entries) { e ->
                when (e) {
                    is HourEntry.H -> HourCell(
                        label = if (e.now) "Now" else u.hour(e.hour.time),
                        icon = e.hour.condition.icon,
                        chance = e.hour.precipChance.takeIf { it >= 20 && com.weatherclone.app.data.Conditions.isPrecip(e.hour.code) || it >= 30 },
                        value = u.temp(if (e.now) w.current.temp else e.hour.temp),
                    )
                    is HourEntry.Sun -> HourCell(
                        label = u.time(e.time),
                        icon = if (e.rise) WxIcon.Sunrise else WxIcon.Sunset,
                        chance = null,
                        value = if (e.rise) "Sunrise" else "Sunset",
                        smallValue = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun HourCell(label: String, icon: WxIcon, chance: Int?, value: String, smallValue: Boolean = false) {
    Column(
        Modifier.width(if (smallValue) 66.dp else 54.dp).height(118.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.height(46.dp), verticalArrangement = Arrangement.Center) {
            WeatherIcon(icon, 27.dp)
            if (chance != null) Text("${Summaries.roundTo10(chance)}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RainBlue)
        }
        Text(value, fontSize = if (smallValue) 15.sp else 20.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1)
    }
}

// --- 10-day ------------------------------------------------------------------

@Composable
fun TenDayCard(w: Weather, open: OpenDetail) {
    val u = LocalUnits.current
    val days = w.forecastDays()
    val min = days.minOf { it.low }
    val max = days.maxOf { it.high }
    WeatherCard(title = "${days.size}-Day Forecast", icon = Icons.Filled.CalendarMonth, contentPadding = false) {
        days.forEachIndexed { i, d ->
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(0.5.dp).background(Hairline))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(remember { MutableInteractionSource() }, null) { open(Metric.Conditions, d.date) }
                    .padding(horizontal = 16.dp)
                    .height(54.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (i == 0) "Today" else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = Type.row, modifier = Modifier.width(62.dp), maxLines = 1,
                )
                Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    WeatherIcon(d.condition.icon, 26.dp)
                    if (d.precipChance >= 30) Text("${Summaries.roundTo10(d.precipChance)}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RainBlue)
                }
                Text(u.temp(d.low), style = Type.row.copy(color = Secondary), textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
                Spacer(Modifier.width(12.dp))
                TempRangeBar(d.low, d.high, min, max, if (i == 0) w.current.temp else null, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(u.temp(d.high), style = Type.row, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// --- Square cards --------------------------------------------------------------

@Composable
fun UvCard(w: Weather, modifier: Modifier, open: OpenDetail) {
    val u = LocalUnits.current
    val uv = w.current.uv
    WeatherCard(modifier, "UV Index", Icons.Filled.WbSunny, fill = true, onClick = { open(Metric.Uv, null) }) {
        Text(uv.roundToInt().toString(), style = Type.bigValue)
        Text(Summaries.uvLevel(uv), style = Type.subValue)
        Spacer(Modifier.weight(1f))
        GradientDotBar(UvColors, (uv / 11.0).toFloat())
        Spacer(Modifier.height(10.dp))
        Text(Summaries.uv(w, u), style = Type.body, maxLines = 2)
    }
}

@Composable
fun SunCard(w: Weather, modifier: Modifier, open: OpenDetail) {
    val u = LocalUnits.current
    val now = w.now()
    val today = w.today
    val tomorrow = w.days.getOrNull(w.todayIndex() + 1)
    val rise = today.sunrise
    val set = today.sunset
    val showSunset = rise != null && set != null && now.isAfter(rise) && now.isBefore(set)
    val nextEvent = when {
        showSunset -> set
        rise != null && now.isBefore(rise) -> rise
        else -> tomorrow?.sunrise
    }
    val other = if (showSunset) rise else (if (rise != null && now.isBefore(rise)) set else tomorrow?.sunset)
    WeatherCard(modifier, if (showSunset) "Sunset" else "Sunrise", Icons.Filled.WbTwilight, fill = true, onClick = { open(Metric.Sun, null) }) {
        Text(nextEvent?.let { u.time(it) } ?: "--", style = Type.bigValue.copy(fontSize = 30.sp))
        Spacer(Modifier.weight(1f))
        SunCurve(w, Modifier.fillMaxWidth().height(48.dp))
        Spacer(Modifier.weight(1f))
        Text("${if (showSunset) "Sunrise" else "Sunset"}: ${other?.let { u.time(it) } ?: "--"}", style = Type.body)
    }
}

/** Sine-ish day arc with the horizon and the sun's current position. */
@Composable
fun SunCurve(w: Weather, modifier: Modifier) {
    val now = w.now()
    val d = w.today
    Canvas(modifier) {
        val rise = d.sunrise ?: return@Canvas
        val set = d.sunset ?: return@Canvas
        fun frac(t: LocalDateTime) = (t.toLocalTime().toSecondOfDay() / 86400.0)
        val r = frac(rise); val s = frac(set)
        val noon = (r + s) / 2
        val base = cos(2 * PI * (r - noon))
        val amp = 1 - base
        fun elev(x: Double) = (cos(2 * PI * (x - noon)) - base) / amp // 1 at noon, 0 at horizon
        val horizonY = size.height * 0.62f
        val scaleY = horizonY * 0.9f
        val path = Path()
        val steps = 80
        for (i in 0..steps) {
            val x = i / steps.toDouble()
            val y = horizonY - elev(x).toFloat() * scaleY
            if (i == 0) path.moveTo(x.toFloat() * size.width, y) else path.lineTo(x.toFloat() * size.width, y)
        }
        val nowX = frac(now)
        val stroke = Stroke(2.5f * density, cap = StrokeCap.Round)
        clipRect(bottom = horizonY) { drawPath(path, Color.White.copy(alpha = 0.55f), style = stroke) }
        clipRect(top = horizonY) { drawPath(path, Color.White.copy(alpha = 0.18f), style = stroke) }
        drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, horizonY), Offset(size.width, horizonY), 1f * density)
        val sx = (nowX * size.width).toFloat()
        val sy = horizonY - elev(nowX).toFloat() * scaleY
        val up = sy < horizonY
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (up) 0.6f else 0.2f), Color.Transparent), Offset(sx, sy), 12 * density), 12 * density, Offset(sx, sy))
        drawCircle(if (up) Color.White else Color.White.copy(alpha = 0.5f), 4.5f * density, Offset(sx, sy))
    }
}

@Composable
fun PrecipCard(w: Weather, modifier: Modifier, open: OpenDetail) {
    val u = LocalUnits.current
    val (big, unit) = u.precipBig(Summaries.last24hPrecip(w))
    WeatherCard(modifier, "Precipitation", Icons.Filled.WaterDrop, fill = true, onClick = { open(Metric.Precip, null) }) {
        Text(buildAnnotatedString {
            append(big)
            if (unit.isNotEmpty()) withStyle(SpanStyle(fontSize = 20.sp)) { append(" $unit") }
        }, style = Type.bigValue)
        Text("in last 24h", style = Type.subValue)
        Spacer(Modifier.weight(1f))
        Text(Summaries.precipitation(w, u), style = Type.body, maxLines = 3)
    }
}

@Composable
fun FeelsLikeCard(w: Weather, modifier: Modifier, open: OpenDetail) {
    val u = LocalUnits.current
    WeatherCard(modifier, "Feels Like", Icons.Filled.Thermostat, fill = true, onClick = { open(Metric.Feels, null) }) {
        Text(u.temp(w.current.feels), style = Type.bigValue)
        Spacer(Modifier.weight(1f))
        FeelsBar(w)
        Spacer(Modifier.height(10.dp))
        Text(Summaries.feelsLike(w), style = Type.body, maxLines = 3)
    }
}

/** Small comparison of actual vs. feels-like temperature. */
@Composable
private fun FeelsBar(w: Weather) {
    val actual = w.current.temp
    val feels = w.current.feels
    val lo = minOf(actual, feels) - 4
    val hi = maxOf(actual, feels) + 4
    Canvas(Modifier.fillMaxWidth().height(5.dp)) {
        val h = size.height
        drawRoundRect(Color.Black.copy(alpha = 0.22f), cornerRadius = CornerRadius(h / 2))
        fun x(v: Double) = ((v - lo) / (hi - lo) * size.width).toFloat()
        val a = x(minOf(actual, feels)); val b = x(maxOf(actual, feels)).coerceAtLeast(a + h)
        drawRoundRect(Brush.horizontalGradient(tempBrushStops(minOf(actual, feels), maxOf(actual, feels)), a, b), Offset(a, 0f), Size(b - a, h), CornerRadius(h / 2))
        drawCircle(Color.White.copy(alpha = 0.6f), h * 0.6f, Offset(x(actual), h / 2))
        drawCircle(Color.White, h * 0.8f, Offset(x(feels), h / 2))
    }
}

@Composable
fun HumidityCard(w: Weather, modifier: Modifier, open: OpenDetail) {
    val u = LocalUnits.current
    WeatherCard(modifier, "Humidity", Icons.Filled.Opacity, fill = true, onClick = { open(Metric.Humidity, null) }) {
        Text("${w.current.humidity}%", style = Type.bigValue)
        Spacer(Modifier.weight(1f))
        Text(Summaries.humidity(w, u), style = Type.body, maxLines = 3)
    }
}

@Composable
fun VisibilityCard(w: Weather, modifier: Modifier, open: OpenDetail) {
    val u = LocalUnits.current
    WeatherCard(modifier, "Visibility", Icons.Filled.Visibility, fill = true, onClick = { open(Metric.Visibility, null) }) {
        Text(buildAnnotatedString {
            append(u.distanceNumber(w.current.visibility))
            withStyle(SpanStyle(fontSize = 20.sp)) { append(" ${u.distanceLabel}") }
        }, style = Type.bigValue)
        Spacer(Modifier.weight(1f))
        Text(Summaries.visibility(w), style = Type.body, maxLines = 3)
    }
}

@Composable
fun PressureCard(w: Weather, modifier: Modifier, open: OpenDetail) {
    val u = LocalUnits.current
    val trend = Summaries.pressureTrend(w)
    val measurer = rememberTextMeasurer()
    WeatherCard(modifier, "Pressure", Icons.Filled.Speed, fill = true, onClick = { open(Metric.Pressure, null) }) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().aspectRatio(1f, matchHeightConstraintsFirst = true)) {
                val r = size.minDimension / 2 * 0.95f
                val c = center
                val startDeg = 135f
                val sweep = 270f
                val ticks = 48
                val frac = ((w.current.pressure - 960) / (1060 - 960)).coerceIn(0.0, 1.0).toFloat()
                for (i in 0..ticks) {
                    val a = Math.toRadians((startDeg + sweep * i / ticks).toDouble())
                    val f = i / ticks.toFloat()
                    val near = kotlin.math.abs(f - frac) < 0.012f
                    drawLine(
                        Color.White.copy(alpha = if (near) 1f else 0.35f),
                        Offset(c.x + cos(a).toFloat() * r * 0.80f, c.y + sin(a).toFloat() * r * 0.80f),
                        Offset(c.x + cos(a).toFloat() * r * (if (near) 1.02f else 0.98f), c.y + sin(a).toFloat() * r * (if (near) 1.02f else 0.98f)),
                        strokeWidth = (if (near) 3.5f else 1.4f) * density,
                        cap = StrokeCap.Round,
                    )
                }
                val label = measurer.measure(u.pressureNumber(w.current.pressure), Type.subValue.copy(fontSize = 20.sp))
                drawText(label, topLeft = Offset(c.x - label.size.width / 2, c.y - label.size.height / 2 + 4 * density))
                val unit = measurer.measure(u.pressureLabel, Type.body.copy(color = Secondary, fontSize = 12.sp))
                drawText(unit, topLeft = Offset(c.x - unit.size.width / 2, c.y + label.size.height / 2 + 2 * density))
                val ly = c.y - label.size.height / 2 - 12 * density
                val tl = measurer.measure(if (trend > 0) "↑" else if (trend < 0) "↓" else "=", Type.subValue.copy(fontSize = 16.sp, color = Secondary))
                drawText(tl, topLeft = Offset(c.x - tl.size.width / 2, ly - tl.size.height / 2))
                // Low / High labels at the gauge ends.
                val low = measurer.measure("Low", Type.body.copy(fontSize = 11.sp, color = Secondary))
                val high = measurer.measure("High", Type.body.copy(fontSize = 11.sp, color = Secondary))
                drawText(low, topLeft = Offset(c.x - r * 0.62f - low.size.width / 2, c.y + r * 0.72f))
                drawText(high, topLeft = Offset(c.x + r * 0.62f - high.size.width / 2, c.y + r * 0.72f))
            }
        }
    }
}

@Composable
fun AveragesCard(w: Weather, modifier: Modifier, open: OpenDetail) {
    val u = LocalUnits.current
    val n = w.normals ?: return
    val delta = u.tempDelta(w.today.high - n.todayHigh)
    WeatherCard(modifier, "Averages", Icons.AutoMirrored.Filled.ShowChart, fill = true, onClick = { open(Metric.Averages, null) }) {
        Text("${Units.signed(delta)}°", style = Type.bigValue)
        Text(
            when { delta > 0 -> "above average daily high"; delta < 0 -> "below average daily high"; else -> "the average daily high" },
            style = Type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), maxLines = 2,
        )
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth()) {
            Text("Today", style = Type.body.copy(color = Secondary), modifier = Modifier.weight(1f))
            Text("H:${u.temp(w.today.high)}", style = Type.body)
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Average", style = Type.body.copy(color = Secondary), modifier = Modifier.weight(1f))
            Text("H:${u.temp(n.todayHigh)}", style = Type.body)
        }
    }
}

// --- Wide cards ---------------------------------------------------------------

@Composable
fun WindCard(w: Weather, open: OpenDetail) {
    val u = LocalUnits.current
    val c = w.current
    WeatherCard(title = "Wind", icon = Icons.Filled.Air, onClick = { open(Metric.Wind, null) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                WindRow(u.windNumber(c.wind), u.windLabel, "Wind")
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Hairline))
                WindRow(u.windNumber(c.gust), u.windLabel, "Gusts")
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Hairline))
                WindRow("${c.windDir}°", Units.compass(c.windDir), "Direction")
            }
            Spacer(Modifier.width(14.dp))
            Compass(c.windDir, u.windNumber(c.wind), u.windLabel, Modifier.size(136.dp))
        }
    }
}

@Composable
private fun WindRow(value: String, unit: String, label: String) {
    Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(value, style = Type.bigValue.copy(fontSize = 26.sp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(unit.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Secondary, lineHeight = 12.sp)
            Text(label, fontSize = 15.sp, color = Color.White, lineHeight = 17.sp)
        }
    }
}

@Composable
fun Compass(dir: Int, speed: String, unit: String, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = center
        for (i in 0 until 180) {
            val a = Math.toRadians(i * 2.0 - 90)
            val major = i % 15 == 0
            val len = if (major) 0.12f else 0.07f
            drawLine(
                Color.White.copy(alpha = if (major) 0.8f else 0.3f),
                Offset(c.x + cos(a).toFloat() * r * (1 - len), c.y + sin(a).toFloat() * r * (1 - len)),
                Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r),
                strokeWidth = if (major) 1.6f * density else 0.9f * density,
            )
        }
        listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (t, deg) ->
            val m = measurer.measure(t, Type.body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (t == "N") Color.White else Secondary))
            val a = Math.toRadians(deg - 90)
            val rr = r * 0.66f
            drawText(m, topLeft = Offset(c.x + cos(a).toFloat() * rr - m.size.width / 2, c.y + sin(a).toFloat() * rr - m.size.height / 2))
        }
        // Arrow points where the wind is blowing to; the tail circle marks where it comes from.
        rotate(dir.toFloat() + 180f, c) {
            val tip = Offset(c.x, c.y - r * 0.92f)
            val tail = Offset(c.x, c.y + r * 0.92f)
            val gap = r * 0.36f
            drawLine(Color.White, Offset(c.x, c.y - gap), Offset(c.x, tip.y + 8 * density), 3f * density, StrokeCap.Round)
            drawLine(Color.White, Offset(c.x, c.y + gap), Offset(c.x, tail.y - 4 * density), 3f * density, StrokeCap.Round)
            val head = Path().apply {
                moveTo(tip.x, tip.y); lineTo(tip.x - 6 * density, tip.y + 11 * density); lineTo(tip.x + 6 * density, tip.y + 11 * density); close()
            }
            drawPath(head, Color.White)
            drawCircle(Color.White, 4 * density, tail, style = Stroke(2.5f * density))
        }
        val sp = measurer.measure(speed, Type.subValue.copy(fontSize = 20.sp))
        val un = measurer.measure(unit, Type.body.copy(fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold))
        drawText(sp, topLeft = Offset(c.x - sp.size.width / 2, c.y - sp.size.height / 2 - 5 * density))
        drawText(un, topLeft = Offset(c.x - un.size.width / 2, c.y + sp.size.height / 2 - 8 * density))
    }
}

@Composable
fun AqiCard(w: Weather, open: OpenDetail) {
    val a = w.air ?: return
    WeatherCard(title = "Air Quality", icon = Icons.Filled.Grain, onClick = { open(Metric.Aqi, null) }) {
        Text(a.aqi.toString(), style = Type.bigValue)
        Text(Summaries.aqiLevel(a.aqi), style = Type.subValue)
        Spacer(Modifier.height(14.dp))
        GradientDotBar(AqiColors, (a.aqi / 300f))
        Spacer(Modifier.height(12.dp))
        Text(Summaries.aqi(a), style = Type.body)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Hairline))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("See More", style = Type.body.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, null, tint = Secondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun MoonCard(w: Weather, open: OpenDetail) {
    val u = LocalUnits.current
    val now = Instant.now()
    val today = w.now().toLocalDate()
    val (rise, set) = remember(w.place.lat, w.place.lon, today) { Astro.moonTimes(today, w.zone, w.place.lat, w.place.lon) }
    val nextRise = rise ?: remember(today) { Astro.moonTimes(today.plusDays(1), w.zone, w.place.lat, w.place.lon).first }
    val nextSet = set ?: remember(today) { Astro.moonTimes(today.plusDays(1), w.zone, w.place.lat, w.place.lon).second }
    val full = Astro.daysUntilFull(now)
    WeatherCard(title = Astro.phaseName(now), icon = Icons.Filled.Bedtime, onClick = { open(Metric.Moon, null) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MoonRow("Illumination", "${(Astro.illumination(now) * 100).roundToInt()}%")
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Hairline))
                MoonRow("Moonrise", nextRise?.let { u.time(it) } ?: "--")
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Hairline))
                MoonRow("Moonset", nextSet?.let { u.time(it) } ?: "--")
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Hairline))
                MoonRow("Next Full Moon", if (full == 0) "Today" else "$full ${if (full == 1) "Day" else "Days"}")
            }
            Spacer(Modifier.width(18.dp))
            MoonPhase(Astro.moonAge(now) / 29.530588853, 112.dp)
        }
    }
}

@Composable
private fun MoonRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.body.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
        Text(value, style = Type.body.copy(fontSize = 15.sp, color = Secondary))
    }
}

@Composable
fun AlertCard(alert: Alert, onClick: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("EEE h:mma", Locale.US)
    WeatherCard(title = "Severe Weather", icon = Icons.Filled.Warning, onClick = onClick) {
        Text(alert.event, style = Type.subValue)
        Spacer(Modifier.height(4.dp))
        val until = alert.ends?.let { " until ${fmt.format(it.atZone(java.time.ZoneId.systemDefault()))}" } ?: ""
        Text("${alert.sender}: ${alert.event}$until.", style = Type.body)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Hairline))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Learn More", style = Type.body.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, null, tint = Secondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun NextHourCard(w: Weather) {
    val text = Summaries.nextHour(w) ?: return
    val now = w.now()
    // Interpolate the 15-minute series to one bar per minute for the next hour.
    val pts = w.minutes
    val values = remember(w) {
        List(60) { m ->
            val t = now.plusMinutes(m.toLong())
            val i = pts.indexOfLast { !it.time.isAfter(t) }
            if (i < 0 || i >= pts.size - 1) pts.getOrNull(i.coerceAtLeast(0))?.precip ?: 0.0
            else {
                val a = pts[i]; val b = pts[i + 1]
                val f = Duration.between(a.time, t).seconds / 900.0
                a.precip + (b.precip - a.precip) * f
            }
        }
    }
    val peak = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    WeatherCard(title = "Next-Hour Precipitation", icon = Icons.Filled.Umbrella) {
        Text(text, style = Type.subValue)
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val bw = size.width / 60f
            listOf(0.33f, 0.66f).forEach { f ->
                drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, size.height * f), Offset(size.width, size.height * f), 1f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            }
            values.forEachIndexed { i, v ->
                val h = (v / peak).toFloat().coerceIn(0f, 1f) * size.height
                if (h > 0.5f) drawRoundRect(RainBlue, Offset(i * bw + bw * 0.2f, size.height - h), Size(bw * 0.6f, h), CornerRadius(bw * 0.3f))
                else drawCircle(Color.White.copy(alpha = 0.25f), bw * 0.25f, Offset(i * bw + bw / 2, size.height - bw * 0.3f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Now", "10m", "20m", "30m", "40m", "50m").forEach { Text(it, style = Type.body.copy(fontSize = 12.sp, color = Secondary)) }
        }
    }
}
