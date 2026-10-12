package com.weatherclone.app.glance

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.weatherclone.app.MainActivity
import com.weatherclone.app.background.Sync
import com.weatherclone.app.data.Store
import com.weatherclone.app.data.Summaries
import com.weatherclone.app.data.Units
import com.weatherclone.app.data.Weather
import com.weatherclone.app.data.WxIcon
import com.weatherclone.app.ui.Render
import com.weatherclone.app.ui.Scene
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/** Everything a widget needs, prepared off the UI thread. */
private class WidgetData(
    val w: Weather?,
    val u: Units,
    val bg: Bitmap?,
    val icons: Map<WxIcon, Bitmap>,
    val arrow: Bitmap,
    val bars: List<Bitmap>,
) {
    fun icon(i: WxIcon) = ImageProvider(icons.getValue(i))

    companion object {
        suspend fun load(ctx: Context, bgW: Int, bgH: Int, withBars: Boolean = false): WidgetData {
            val w = Sync.primary(ctx, fetch = false)
            val u = Units(Store(ctx).settings, DateFormat.is24HourFormat(ctx))
            val density = ctx.resources.displayMetrics.density
            val needed = buildSet {
                w?.let { add(it.condition.icon); it.upcomingHours(7).forEach { h -> add(h.condition.icon) }; it.forecastDays().take(6).forEach { d -> add(d.condition.icon) } }
            }
            val days = w?.forecastDays()?.take(6).orEmpty()
            val bars = if (withBars && days.isNotEmpty()) {
                val min = days.minOf { it.low }; val max = days.maxOf { it.high }
                days.map { Render.rangeBar(it.low, it.high, min, max, 260, 12) }
            } else emptyList()
            return WidgetData(
                w, u,
                if (bgW > 0) Render.sky(Scene.of(w), bgW, bgH, density / 1.5f) else null,
                needed.associateWith { Render.icon(it, 96) },
                Render.arrow(32),
                bars,
            )
        }
    }
}

private val white = ColorProvider(Color.White)
private val dim = ColorProvider(Color.White.copy(alpha = 0.75f))
private fun style(size: Int, weight: FontWeight = FontWeight.Medium, color: ColorProvider = white, align: TextAlign = TextAlign.Start) =
    TextStyle(color = color, fontSize = size.sp, fontWeight = weight, textAlign = align)

@Composable
private fun Card(d: WidgetData, content: @Composable () -> Unit) {
    val base = GlanceModifier.fillMaxSize().cornerRadius(22.dp).clickable(actionStartActivity<MainActivity>())
    Box(
        if (d.bg != null) base.background(ImageProvider(d.bg), ContentScale.FillBounds) else base.background(Color(0xFF2969C2)),
    ) { content() }
}

@Composable
private fun Empty() {
    Box(GlanceModifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
        Text("Open Stratus to load the weather", style = style(13, align = TextAlign.Center))
    }
}

@Composable
private fun CityLine(d: WidgetData, w: Weather, size: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(w.place.name, style = style(size, FontWeight.Bold), maxLines = 1)
        if (w.place.isCurrent) {
            Spacer(GlanceModifier.width(4.dp))
            Image(ImageProvider(d.arrow), null, GlanceModifier.size((size - 4).dp))
        }
    }
}

// --- Small ------------------------------------------------------------------------

class SmallWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WidgetData.load(context, 340, 340)
        provideContent { Card(d) { val w = d.w; if (w == null) Empty() else SmallContent(d, w) } }
    }
}

@Composable
private fun SmallContent(d: WidgetData, w: Weather) {
    Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
        CityLine(d, w, 15)
        Text(d.u.temp(w.current.temp), style = style(40, FontWeight.Normal))
        Spacer(GlanceModifier.defaultWeight())
        Image(d.icon(w.condition.icon), null, GlanceModifier.size(20.dp))
        Spacer(GlanceModifier.height(2.dp))
        Text(w.condition.text, style = style(13, FontWeight.Bold), maxLines = 1)
        Text("H:${d.u.temp(w.today.high)} L:${d.u.temp(w.today.low)}", style = style(13, FontWeight.Bold), maxLines = 1)
    }
}

// --- Medium -----------------------------------------------------------------------

class MediumWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WidgetData.load(context, 720, 340)
        provideContent { Card(d) { val w = d.w; if (w == null) Empty() else Column(GlanceModifier.fillMaxSize().padding(14.dp)) { TopRow(d, w); Spacer(GlanceModifier.defaultWeight()); HourRow(d, w) } } }
    }
}

@Composable
private fun TopRow(d: WidgetData, w: Weather) {
    Row(GlanceModifier.fillMaxWidth()) {
        Column(GlanceModifier.defaultWeight()) {
            CityLine(d, w, 15)
            Text(d.u.temp(w.current.temp), style = style(40, FontWeight.Normal))
        }
        Column(horizontalAlignment = Alignment.End) {
            Image(d.icon(w.condition.icon), null, GlanceModifier.size(20.dp))
            Spacer(GlanceModifier.height(2.dp))
            Text(w.condition.text, style = style(13, FontWeight.Bold, align = TextAlign.End), maxLines = 1)
            Text("H:${d.u.temp(w.today.high)} L:${d.u.temp(w.today.low)}", style = style(13, FontWeight.Bold, align = TextAlign.End), maxLines = 1)
        }
    }
}

@Composable
private fun HourRow(d: WidgetData, w: Weather) {
    Row(GlanceModifier.fillMaxWidth()) {
        w.upcomingHours(6).forEachIndexed { i, h ->
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (i == 0) "Now" else d.u.hour(h.time), style = style(12, FontWeight.Bold, dim, TextAlign.Center), maxLines = 1)
                Spacer(GlanceModifier.height(4.dp))
                Image(d.icon(h.condition.icon), null, GlanceModifier.size(22.dp))
                Spacer(GlanceModifier.height(4.dp))
                Text(d.u.temp(if (i == 0) w.current.temp else h.temp), style = style(14, FontWeight.Bold, align = TextAlign.Center), maxLines = 1)
            }
        }
    }
}

// --- Large ------------------------------------------------------------------------

class LargeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WidgetData.load(context, 720, 760, withBars = true)
        provideContent {
            Card(d) {
                val w = d.w
                if (w == null) Empty() else Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
                    TopRow(d, w)
                    Spacer(GlanceModifier.height(10.dp))
                    HourRow(d, w)
                    Spacer(GlanceModifier.height(8.dp))
                    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.25f))) {}
                    Spacer(GlanceModifier.height(4.dp))
                    // Glance keeps at most 10 children per Column, so the day rows get their own.
                    Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    w.forecastDays().take(6).forEachIndexed { i, day ->
                        Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (i == 0) "Today" else day.date.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault()),
                                style = style(14, FontWeight.Bold), modifier = GlanceModifier.width(52.dp), maxLines = 1,
                            )
                            Image(d.icon(day.condition.icon), null, GlanceModifier.size(20.dp))
                            Text(d.u.temp(day.low), style = style(14, FontWeight.Bold, dim, TextAlign.End), modifier = GlanceModifier.width(40.dp))
                            Spacer(GlanceModifier.width(8.dp))
                            d.bars.getOrNull(i)?.let { Image(ImageProvider(it), null, GlanceModifier.defaultWeight().height(5.dp), contentScale = ContentScale.FillBounds) }
                            Spacer(GlanceModifier.width(8.dp))
                            Text(d.u.temp(day.high), style = style(14, FontWeight.Bold, align = TextAlign.End), modifier = GlanceModifier.width(36.dp))
                        }
                    }
                    }
                }
            }
        }
    }
}

// --- At a Glance style -------------------------------------------------------------

/** Transparent date + weather line, styled after Pixel's At a Glance. */
class GlanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WidgetData.load(context, 0, 0)
        provideContent {
            val w = d.w
            Column(GlanceModifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp).clickable(actionStartActivity<MainActivity>()), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val date = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).format(w?.now() ?: java.time.LocalDateTime.now())
                    Text(date, style = style(22, FontWeight.Normal), maxLines = 1)
                    if (w != null) {
                        Spacer(GlanceModifier.width(12.dp))
                        Image(d.icon(w.condition.icon), null, GlanceModifier.size(24.dp))
                        Spacer(GlanceModifier.width(6.dp))
                        Text(d.u.temp(w.current.temp), style = style(22, FontWeight.Normal), maxLines = 1)
                    }
                }
                if (w != null) {
                    val line = w.alerts.firstOrNull()?.let { "⚠ ${it.event}" }
                        ?: Summaries.nextHour(w)
                        ?: "${w.condition.text} · H:${d.u.temp(w.today.high)} L:${d.u.temp(w.today.low)}"
                    Text(line, style = style(15, FontWeight.Medium), maxLines = 1)
                }
            }
        }
    }
}

class SmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); Sync.schedule(context); Sync.refreshSoon(context) }
}
class MediumWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MediumWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); Sync.schedule(context); Sync.refreshSoon(context) }
}
class LargeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LargeWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); Sync.schedule(context); Sync.refreshSoon(context) }
}
class GlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GlanceWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); Sync.schedule(context); Sync.refreshSoon(context) }
}
