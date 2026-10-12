package com.weatherclone.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.weatherclone.app.data.WxIcon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

val SunYellow = Color(0xFFFFD60A)
val RainBlue = Color(0xFF4CC9F0)
val CloudWhite = Color(0xFFFFFFFF)
val MoonWhite = Color(0xFFF2F2F7)

/** Multicolor weather glyphs, drawn on a 100×100 grid. */
@Composable
fun WeatherIcon(icon: WxIcon, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val s = this.size.minDimension / 100f
        withTransform({
            translate((this@Canvas.size.width - 100 * s) / 2, (this@Canvas.size.height - 100 * s) / 2)
            scale(s, s, Offset.Zero)
        }) { drawWx(icon) }
    }
}

private fun cloudShape(): Path {
    val base = Path().apply { addRoundRect(RoundRect(Rect(9f, 52f, 91f, 77f), CornerRadius(12.5f))) }
    val puffs = listOf(Triple(30f, 56f, 15.5f), Triple(56f, 43f, 25f), Triple(78f, 59f, 13f))
    var p = base
    for ((x, y, r) in puffs) {
        val c = Path().apply { addOval(Rect(Offset(x, y), r)) }
        p = Path().apply { op(p, c, PathOperation.Union) }
    }
    return p
}

private val CLOUD = cloudShape()

private fun DrawScope.cloud(dx: Float, dy: Float, scale: Float, gap: Boolean = false, color: Color = CloudWhite) {
    withTransform({ translate(dx, dy); scale(scale, scale, Offset.Zero) }) {
        if (gap) drawPath(CLOUD, Color.Black, style = Stroke(width = 12f / scale), blendMode = BlendMode.Clear)
        drawPath(CLOUD, color)
    }
}

private fun DrawScope.sun(cx: Float, cy: Float, r: Float, rayIn: Float, rayOut: Float, rayW: Float, skip: Set<Int> = emptySet()) {
    drawCircle(SunYellow, r, Offset(cx, cy))
    for (i in 0 until 8) {
        if (i in skip) continue
        val a = i * PI / 4
        drawLine(
            SunYellow,
            Offset(cx + cos(a).toFloat() * rayIn, cy + sin(a).toFloat() * rayIn),
            Offset(cx + cos(a).toFloat() * rayOut, cy + sin(a).toFloat() * rayOut),
            strokeWidth = rayW, cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.moon(cx: Float, cy: Float, r: Float) {
    val disc = Path().apply { addOval(Rect(Offset(cx, cy), r)) }
    val bite = Path().apply { addOval(Rect(Offset(cx + r * 0.55f, cy - r * 0.38f), r * 0.86f)) }
    drawPath(Path().apply { op(disc, bite, PathOperation.Difference) }, MoonWhite)
}

private fun DrawScope.star(cx: Float, cy: Float, r: Float) {
    val p = Path().apply {
        moveTo(cx, cy - r)
        quadraticTo(cx, cy, cx + r, cy)
        quadraticTo(cx, cy, cx, cy + r)
        quadraticTo(cx, cy, cx - r, cy)
        quadraticTo(cx, cy, cx, cy - r)
        close()
    }
    drawPath(p, MoonWhite)
}

private fun DrawScope.drop(x: Float, y: Float, len: Float, w: Float = 6.5f) =
    drawLine(RainBlue, Offset(x, y), Offset(x - len * 0.25f, y + len), strokeWidth = w, cap = StrokeCap.Round)

private fun DrawScope.flake(cx: Float, cy: Float, r: Float) {
    for (i in 0 until 3) {
        val a = i * PI / 3 + PI / 2
        val dx = cos(a).toFloat() * r
        val dy = sin(a).toFloat() * r
        drawLine(CloudWhite, Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy), strokeWidth = 3.6f, cap = StrokeCap.Round)
    }
}

/** Cloud placed in the upper part of the grid for precipitation icons. */
private fun DrawScope.topCloud() = cloud(5f, -9f, 0.9f)

fun DrawScope.drawWx(icon: WxIcon) {
    when (icon) {
        WxIcon.Sun -> sun(50f, 50f, 20f, 30f, 42f, 7f)
        WxIcon.Moon -> {
            moon(44f, 54f, 32f)
            star(78f, 22f, 9f)
            star(88f, 46f, 5.5f)
        }
        WxIcon.CloudSun -> {
            sun(64f, 34f, 15f, 22f, 30f, 5.5f)
            cloud(-2f, 12f, 0.84f, gap = true)
        }
        WxIcon.CloudMoon -> {
            moon(64f, 34f, 20f)
            cloud(-2f, 12f, 0.84f, gap = true)
        }
        WxIcon.Cloud -> cloud(0f, 4f, 1f)
        WxIcon.Fog -> {
            topCloud()
            val c = Color(0xFFD1D1D6)
            drawLine(c, Offset(18f, 72f), Offset(82f, 72f), 6f, StrokeCap.Round)
            drawLine(c, Offset(28f, 85f), Offset(72f, 85f), 6f, StrokeCap.Round)
        }
        WxIcon.Haze -> {
            sun(50f, 38f, 15f, 22f, 30f, 5.5f, skip = setOf(1, 2, 3))
            val c = Color(0xFFD1D1D6)
            drawLine(c, Offset(14f, 66f), Offset(86f, 66f), 6f, StrokeCap.Round)
            drawLine(c, Offset(22f, 79f), Offset(78f, 79f), 6f, StrokeCap.Round)
            drawLine(c, Offset(30f, 92f), Offset(70f, 92f), 6f, StrokeCap.Round)
        }
        WxIcon.Drizzle -> {
            topCloud()
            listOf(32f, 50f, 68f).forEach { drop(it, 70f, 9f) }
            listOf(41f, 59f).forEach { drop(it, 84f, 9f) }
        }
        WxIcon.Rain -> {
            topCloud()
            listOf(36f, 54f, 72f).forEach { drop(it, 68f, 20f) }
        }
        WxIcon.HeavyRain -> {
            topCloud()
            listOf(28f, 44f, 60f, 76f).forEach { drop(it, 66f, 26f, 6f) }
        }
        WxIcon.Snow -> {
            topCloud()
            flake(33f, 74f, 7f); flake(67f, 74f, 7f); flake(50f, 88f, 7f)
        }
        WxIcon.Sleet -> {
            topCloud()
            drop(34f, 68f, 16f); flake(52f, 80f, 7f); drop(72f, 68f, 16f)
        }
        WxIcon.Hail -> {
            topCloud()
            listOf(Offset(33f, 74f), Offset(67f, 74f), Offset(50f, 88f)).forEach {
                drawCircle(CloudWhite, 5.5f, it)
            }
        }
        WxIcon.Thunder -> {
            topCloud()
            val bolt = Path().apply {
                moveTo(56f, 56f); lineTo(40f, 80f); lineTo(50f, 80f); lineTo(43f, 99f)
                lineTo(65f, 72f); lineTo(54f, 72f); lineTo(62f, 56f); close()
            }
            drawPath(bolt, SunYellow)
            drop(28f, 68f, 14f); drop(80f, 68f, 14f)
        }
        WxIcon.Wind -> {
            val w = Stroke(width = 6.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(Path().apply { moveTo(10f, 40f); lineTo(60f, 40f); cubicTo(76f, 40f, 76f, 18f, 61f, 18f); cubicTo(54f, 18f, 51f, 24f, 52f, 28f) }, CloudWhite, style = w)
            drawPath(Path().apply { moveTo(10f, 56f); lineTo(76f, 56f); cubicTo(94f, 56f, 94f, 82f, 77f, 82f); cubicTo(70f, 82f, 66f, 76f, 67f, 72f) }, CloudWhite, style = w)
            drawPath(Path().apply { moveTo(10f, 70f); lineTo(46f, 70f) }, CloudWhite, style = w)
        }
        WxIcon.Sunrise, WxIcon.Sunset -> {
            clipRect(0f, 0f, 100f, 72f) { drawCircle(SunYellow, 19f, Offset(50f, 72f)) }
            listOf(-150.0, -120.0, -60.0, -30.0, -180.0, 0.0).forEach { deg ->
                val a = deg * PI / 180
                drawLine(
                    SunYellow,
                    Offset(50f + cos(a).toFloat() * 27f, 72f + sin(a).toFloat() * 27f),
                    Offset(50f + cos(a).toFloat() * 36f, 72f + sin(a).toFloat() * 36f),
                    strokeWidth = 5.5f, cap = StrokeCap.Round,
                )
            }
            drawLine(CloudWhite, Offset(8f, 80f), Offset(92f, 80f), 6f, StrokeCap.Round)
            val up = icon == WxIcon.Sunrise
            val top = 10f; val bottom = 38f
            drawLine(CloudWhite, Offset(50f, top), Offset(50f, bottom), 5.5f, StrokeCap.Round)
            val tipY = if (up) top else bottom
            val dir = if (up) 1f else -1f
            drawLine(CloudWhite, Offset(50f, tipY), Offset(42f, tipY + 8f * dir), 5.5f, StrokeCap.Round)
            drawLine(CloudWhite, Offset(50f, tipY), Offset(58f, tipY + 8f * dir), 5.5f, StrokeCap.Round)
        }
    }
}

/** Moon disc lit according to [phase] (0 = new, 0.5 = full). */
@Composable
fun MoonPhase(phase: Double, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val c = center
        drawCircle(Color(0xFF3A3A3C), r, c)
        val p = ((phase % 1) + 1) % 1
        val waxing = p < 0.5
        val k = cos(2 * PI * p).toFloat() // 1 new … -1 full
        val half = Path().apply {
            val rect = Rect(c, r)
            addArc(rect, if (waxing) -90f else 90f, 180f)
            close()
        }
        val rx = abs(k) * r
        val ellipse = Path().apply { addOval(Rect(Offset(c.x - rx, c.y - r), Size(rx * 2, r * 2))) }
        val lit = Path().apply {
            op(half, ellipse, if (k > 0) PathOperation.Difference else PathOperation.Union)
        }
        drawPath(lit, Color(0xFFE8E8ED))
    }
}

/** Little location arrow used in page dots and headers. */
fun DrawScope.locationArrow(color: Color) {
    val w = size.width; val h = size.height
    val p = Path().apply {
        moveTo(w * 0.92f, h * 0.08f)
        lineTo(w * 0.08f, h * 0.45f)
        lineTo(w * 0.48f, h * 0.52f)
        lineTo(w * 0.55f, h * 0.92f)
        close()
    }
    drawPath(p, color)
}
