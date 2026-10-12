package com.weatherclone.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherclone.app.data.Alert
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.weatherclone.app.data.Place
import com.weatherclone.app.data.Weather
import java.time.LocalDate
import kotlin.math.max

@Composable
fun LocationPage(
    place: Place,
    weather: Weather?,
    error: Boolean,
    radar: Radar?,
    bottomInset: Dp,
    onOpenMap: (Weather) -> Unit,
    topInset: Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
) {
    val scene = Scene.of(weather)
    var detail by remember { mutableStateOf<Pair<Metric, LocalDate?>?>(null) }
    var alert by remember { mutableStateOf<Alert?>(null) }
    val open: OpenDetail = { m, d -> detail = m to d }

    val haze = rememberHazeState()
    CompositionLocalProvider(LocalScene provides scene) {
        Box(Modifier.fillMaxSize()) {
            SkyBackground(scene, Modifier.hazeSource(haze))

            val density = LocalDensity.current
            val scroll = rememberScrollState()
            var expandedPx by remember { mutableIntStateOf(0) }
            val nameBlock = if (place.isCurrent) 60.dp else 44.dp
            val collapsedPx = with(density) { (topInset + 34.dp + nameBlock + 30.dp).toPx() }
            val collapsedState = rememberUpdatedState(collapsedPx)
            val collapse = remember {
                CardCollapse { max(collapsedState.value, expandedPx - scroll.value.toFloat()) }
            }

            CompositionLocalProvider(LocalCollapse provides collapse, LocalHaze provides haze) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { collapse.container = it }
                        .verticalScroll(scroll)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(Modifier.height((with(density) { expandedPx.toDp() } - 12.dp).coerceAtLeast(0.dp)))
                    if (weather != null) Modules(weather, radar, open, onAlert = { alert = it }, onOpenMap = { onOpenMap(weather) })
                    else Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        if (error) Text("Weather unavailable.\nCheck your connection.", color = Secondary, textAlign = TextAlign.Center)
                        else CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f), strokeWidth = 2.dp)
                    }
                    Spacer(Modifier.height(bottomInset + 24.dp))
                }
            }

            Header(
                place, weather, topInset, nameBlock,
                scrollPx = { scroll.value.toFloat() },
                expandedPx = { expandedPx.toFloat() },
                collapsedPx = collapsedPx,
                modifier = Modifier.onSizeChanged { expandedPx = it.height },
            )
        }
    }

    if (weather != null) {
        detail?.let { (m, d) -> CompositionLocalProvider(LocalScene provides scene) { DetailSheet(weather, m, d) { detail = null } } }
        alert?.let { AlertSheet(it) { alert = null } }
    }
}

@Composable
private fun Modules(w: Weather, radar: Radar?, open: OpenDetail, onAlert: (Alert) -> Unit, onOpenMap: () -> Unit) {
    w.alerts.forEach { a -> AlertCard(a) { onAlert(a) } }
    NextHourCard(w)
    HourlyCard(w, open)
    TenDayCard(w, open)
    MapCard(w, radar, onOpenMap)
    AqiCard(w, open)
    CardRow({ UvCard(w, it, open) }, { SunCard(w, it, open) })
    WindCard(w, open)
    CardRow({ PrecipCard(w, it, open) }, { FeelsLikeCard(w, it, open) })
    CardRow({ HumidityCard(w, it, open) }, { VisibilityCard(w, it, open) })
    if (w.normals != null) CardRow({ PressureCard(w, it, open) }, { AveragesCard(w, it, open) })
    else CardRow({ PressureCard(w, it, open) }, { Spacer(it) })
    MoonCard(w, open)
    Footer(w)
}

@Composable
private fun Footer(w: Weather) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Weather for ${w.place.name}", color = Secondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Weather data by Open-Meteo.com (CC BY 4.0) · Alerts by the National Weather Service · Radar by RainViewer",
            color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Header(
    place: Place,
    w: Weather?,
    topInset: Dp,
    nameBlock: Dp,
    scrollPx: () -> Float,
    expandedPx: () -> Float,
    collapsedPx: Float,
    modifier: Modifier,
) {
    val u = LocalUnits.current
    val range ={ (expandedPx() - collapsedPx).coerceAtLeast(1f) }
    fun fade(start: Float, end: Float) = { (1f - ((scrollPx() / range()) - start) / (end - start)).coerceIn(0f, 1f) }

    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                // Clip the header to its current (shrinking) height so lines disappear under the cards.
                val h = max(collapsedPx, expandedPx() - scrollPx())
                clip = true
                shape = object : androidx.compose.ui.graphics.Shape {
                    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
                        androidx.compose.ui.graphics.Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, h))
                }
            }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(PaddingValues(top = topInset + 34.dp, bottom = 34.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Name block stays pinned.
            Column(Modifier.height(nameBlock), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (place.isCurrent) "My Location" else place.name,
                    fontSize = 34.sp, fontWeight = FontWeight.Normal, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp),
                    style = androidx.compose.ui.text.TextStyle(fontFamily = InterDisplay, shadow = textShadow),
                )
                if (place.isCurrent) Text(
                    place.name.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    style = androidx.compose.ui.text.TextStyle(fontFamily = Inter, shadow = textShadow, letterSpacing = 0.4.sp),
                )
            }
            Box(contentAlignment = Alignment.TopCenter) {
                // Compact line that fades in once collapsed.
                Text(
                    if (w != null) "${u.temp(w.current.temp)} | ${w.condition.text}" else "",
                    fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Secondary,
                    modifier = Modifier.graphicsLayer {
                        alpha = ((scrollPx() / range() - 0.75f) / 0.25f).coerceIn(0f, 1f)
                    },
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val tempFade = fade(0.25f, 0.7f)
                    Text(
                        if (w != null) "${u.tempInt(w.current.temp)}°" else "--°",
                        fontSize = 102.sp, fontWeight = FontWeight.Thin, color = Color.White,
                        style = androidx.compose.ui.text.TextStyle(fontFamily = InterDisplay, shadow = textShadow, lineHeight = 108.sp, letterSpacing = (-2).sp),
                        modifier = Modifier
                            .padding(start = 26.dp)
                            .graphicsLayer {
                                alpha = tempFade()
                                translationY = -scrollPx() * 0.25f
                            },
                    )
                    val condFade = fade(0.1f, 0.45f)
                    Text(
                        w?.condition?.text ?: "",
                        fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White,
                        style = androidx.compose.ui.text.TextStyle(fontFamily = InterDisplay, shadow = textShadow),
                        modifier = Modifier.graphicsLayer { alpha = condFade(); translationY = -scrollPx() * 0.35f },
                    )
                    val hlFade = fade(0f, 0.3f)
                    Text(
                        if (w != null) "H:${u.temp(w.today.high)}  L:${u.temp(w.today.low)}" else "",
                        fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White,
                        style = androidx.compose.ui.text.TextStyle(fontFamily = InterDisplay, shadow = textShadow),
                        modifier = Modifier.graphicsLayer { alpha = hlFade(); translationY = -scrollPx() * 0.45f },
                    )
                }
            }
        }
    }
}

private val textShadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.18f), androidx.compose.ui.geometry.Offset(0f, 1f), 6f)
