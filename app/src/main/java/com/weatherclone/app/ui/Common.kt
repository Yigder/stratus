package com.weatherclone.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.weatherclone.app.data.Settings
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import com.weatherclone.app.data.Units

val LocalUnits = compositionLocalOf { Units(Settings.defaults(), false) }
val LocalScene = compositionLocalOf { Scene(com.weatherclone.app.data.Sky.Clear, night = false, twilight = false) }

val Secondary = Color.White.copy(alpha = 0.6f)
val Hairline = Color.White.copy(alpha = 0.22f)

object Type {
    val cardHeader = TextStyle(fontFamily = Inter, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Secondary, letterSpacing = 0.2.sp)
    val bigValue = TextStyle(fontFamily = InterDisplay, fontSize = 32.sp, fontWeight = FontWeight.Medium, color = Color.White)
    val subValue = TextStyle(fontFamily = Inter, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    val body = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Normal, color = Color.White, lineHeight = 18.sp)
    val row = TextStyle(fontFamily = Inter, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White)
}

/**
 * Lets cards slide under the collapsed header the way Apple's do: the header sticks at the
 * cut-off line while the body scrolls away beneath it.
 */
class CardCollapse(val cutoff: () -> Float) {
    var container: LayoutCoordinates? = null
}

val LocalCollapse = compositionLocalOf<CardCollapse?> { null }

/** The animated sky that cards blur, like Apple's frosted material. */
val LocalHaze = compositionLocalOf<HazeState?> { null }

@Composable
private fun Modifier.cardMaterial(scene: Scene): Modifier {
    val haze = LocalHaze.current ?: return background(scene.card)
    val style = remember(scene) {
        HazeBlurStyle {
            blurRadius(26.dp)
            noiseFactor(0f)
            colorEffects(listOf(HazeColorEffect.tint(scene.card)))
        }
    }
    return hazeBlur(input = HazeInput.Backdrop(haze), style = style)
}

private class TopCutShape(private val top: Float, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val t = top.coerceIn(0f, size.height)
        return Outline.Rounded(RoundRect(0f, t, size.width, size.height, CornerRadius(radius)))
    }
}

private class CutRect(private val top: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, top.coerceIn(0f, size.height), size.width, size.height)) })
}

@Composable
fun WeatherCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    header: (@Composable () -> Unit)? = null,
    divider: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: Boolean = true,
    fill: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val collapse = LocalCollapse.current
    val scene = LocalScene.current
    val coordsRef = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val headerH = remember { mutableFloatStateOf(0f) }
    val cardH = remember { mutableFloatStateOf(0f) }
    val radius = with(androidx.compose.ui.platform.LocalDensity.current) { 15.dp.toPx() }

    // Written on every placement so the layer blocks below re-run when layout (not just scroll) moves the card.
    val placed = remember { mutableFloatStateOf(0f) }

    fun hidden(): Float {
        @Suppress("UNUSED_VARIABLE") val tick = placed.floatValue
        val c = collapse ?: return 0f
        val me = coordsRef[0] ?: return 0f
        val container = c.container ?: return 0f
        if (!me.isAttached || !container.isAttached) return 0f
        val top = container.localPositionOf(me, Offset.Zero).y
        return (c.cutoff() - top).coerceAtLeast(0f)
    }

    Box(
        modifier
            .onGloballyPositioned {
                coordsRef[0] = it
                placed.floatValue = collapse?.container?.takeIf { c -> c.isAttached }?.localPositionOf(it, Offset.Zero)?.y ?: 0f
            }
            .onSizeChanged { cardH.floatValue = it.height.toFloat() }
            .graphicsLayer {
                val hid = hidden()
                val remaining = cardH.floatValue - hid
                alpha = if (hid <= 0f) 1f else (remaining / headerH.floatValue.coerceAtLeast(1f)).coerceIn(0f, 1f)
                clip = true
                shape = TopCutShape(hid, radius)
            }
            .cardMaterial(scene)
            .then(
                if (onClick != null) Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClick) else Modifier
            )
    ) {
        Column(if (fill) Modifier.fillMaxSize() else Modifier) {
            Box(
                Modifier
                    .zIndex(1f)
                    .onSizeChanged { headerH.floatValue = it.height.toFloat() }
                    .graphicsLayer {
                        translationY = hidden().coerceAtMost((cardH.floatValue - headerH.floatValue).coerceAtLeast(0f))
                    }
            ) {
                Column {
                    when {
                        header != null -> header()
                        title != null -> CardHeader(title, icon)
                    }
                    if (divider) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(0.5.dp).background(Hairline))
                }
            }
            Column(
                (if (fill) Modifier.weight(1f).fillMaxWidth() else Modifier)
                    .graphicsLayer {
                        clip = true
                        shape = CutRect(hidden())
                    }
                    .then(if (contentPadding) Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp) else Modifier),
                content = content,
            )
        }
    }
}

@Composable
fun CardHeader(title: String, icon: ImageVector?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Secondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(title.uppercase(), style = Type.cardHeader)
    }
}

/** Two cards side by side, equal square size. */
@Composable
fun CardRow(left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        left(Modifier.weight(1f).height(SQUARE))
        right(Modifier.weight(1f).height(SQUARE))
    }
}

val SQUARE = 172.dp

// --- Colour scales -------------------------------------------------------

private val tempStops = listOf(
    -20.0 to Color(0xFF8E7CF0),
    -8.0 to Color(0xFF4A8CF7),
    2.0 to Color(0xFF4DB6F5),
    10.0 to Color(0xFF5AC8C0),
    16.0 to Color(0xFFA5D65A),
    22.0 to Color(0xFFF3CB45),
    28.0 to Color(0xFFF59A3B),
    35.0 to Color(0xFFEB5B3E),
    42.0 to Color(0xFFD9363E),
)

fun tempColor(c: Double): Color {
    if (c <= tempStops.first().first) return tempStops.first().second
    if (c >= tempStops.last().first) return tempStops.last().second
    val i = tempStops.indexOfFirst { it.first > c }
    val (t0, c0) = tempStops[i - 1]
    val (t1, c1) = tempStops[i]
    return lerp(c0, c1, ((c - t0) / (t1 - t0)).toFloat())
}

fun tempBrushStops(lo: Double, hi: Double): List<Color> {
    if (hi - lo < 0.1) return listOf(tempColor(lo), tempColor(hi))
    return List(6) { tempColor(lo + (hi - lo) * it / 5.0) }
}

val UvColors = listOf(Color(0xFF34C759), Color(0xFFFFD60A), Color(0xFFFF9F0A), Color(0xFFFF453A), Color(0xFFBF5AF2))
val AqiColors = listOf(Color(0xFF34C759), Color(0xFFFFD60A), Color(0xFFFF9F0A), Color(0xFFFF453A), Color(0xFFBF5AF2), Color(0xFF8E2C48))

fun uvColor(uv: Double): Color = when {
    uv < 3 -> UvColors[0]; uv < 6 -> UvColors[1]; uv < 8 -> UvColors[2]; uv < 11 -> UvColors[3]; else -> UvColors[4]
}

/** Gradient track with a white position dot (UV, AQI cards). */
@Composable
fun GradientDotBar(colors: List<Color>, fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(5.dp)) {
        val h = size.height
        drawRoundRect(Brush.horizontalGradient(colors), cornerRadius = CornerRadius(h / 2))
        val x = (fraction.coerceIn(0f, 1f) * size.width).coerceIn(h / 2, size.width - h / 2)
        drawCircle(Color.Black.copy(alpha = 0.35f), h * 0.95f, Offset(x, h / 2))
        drawCircle(Color.White, h * 0.7f, Offset(x, h / 2))
    }
}

/** The 10-day temperature range bar. */
@Composable
fun TempRangeBar(low: Double, high: Double, min: Double, max: Double, current: Double?, modifier: Modifier = Modifier) {
    val track = Color.Black.copy(alpha = 0.22f)
    Canvas(modifier.height(5.dp)) {
        val h = size.height
        drawRoundRect(track, cornerRadius = CornerRadius(h / 2))
        val span = (max - min).coerceAtLeast(1.0)
        val x0 = ((low - min) / span * size.width).toFloat()
        val x1 = ((high - min) / span * size.width).toFloat().coerceAtLeast(x0 + h)
        drawRoundRect(
            Brush.horizontalGradient(tempBrushStops(low, high), startX = x0, endX = x1),
            topLeft = Offset(x0, 0f), size = Size(x1 - x0, h), cornerRadius = CornerRadius(h / 2),
        )
        if (current != null) {
            val cx = ((current - min) / span * size.width).toFloat().coerceIn(h / 2, size.width - h / 2)
            drawCircle(track.copy(alpha = 0.6f), h * 1.05f, Offset(cx, h / 2), style = Stroke(h * 0.5f))
            drawCircle(Color.White, h * 0.8f, Offset(cx, h / 2))
        }
    }
}
