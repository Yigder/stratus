package com.weatherclone.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import com.weatherclone.app.data.Sky
import com.weatherclone.app.data.Weather
import java.time.Duration
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** False in screenshot tests so the UI can settle. */
val LocalAnimate = compositionLocalOf { true }

@Immutable
data class Scene(val sky: Sky, val night: Boolean, val twilight: Boolean) {
    val colors: List<Color> get() = when {
        twilight && (sky == Sky.Clear || sky == Sky.PartlyCloudy) -> listOf(Color(0xFF2B4C7E), Color(0xFF6C6A92), Color(0xFFDB9871))
        twilight && sky == Sky.Cloudy -> listOf(Color(0xFF3A4660), Color(0xFF6E6F85), Color(0xFFA88C86))
        else -> when (sky) {
            Sky.Clear -> if (night) listOf(Color(0xFF070B1F), Color(0xFF141E3D), Color(0xFF26355C)) else listOf(Color(0xFF2969C2), Color(0xFF4A8FDB), Color(0xFF79B6EC))
            Sky.PartlyCloudy -> if (night) listOf(Color(0xFF0F1629), Color(0xFF1E2942), Color(0xFF34425E)) else listOf(Color(0xFF3672B8), Color(0xFF5A90C9), Color(0xFF8DB4D9))
            Sky.Cloudy -> if (night) listOf(Color(0xFF1A2029), Color(0xFF283039), Color(0xFF3B4450)) else listOf(Color(0xFF536779), Color(0xFF6F8294), Color(0xFF93A3B3))
            Sky.Fog -> if (night) listOf(Color(0xFF262B32), Color(0xFF363C44), Color(0xFF4C535C)) else listOf(Color(0xFF828C96), Color(0xFF9AA2AA), Color(0xFFB4BAC0))
            Sky.Rain -> if (night) listOf(Color(0xFF12171F), Color(0xFF1E252F), Color(0xFF303844)) else listOf(Color(0xFF465464), Color(0xFF5A6877), Color(0xFF727F8D))
            Sky.Snow -> if (night) listOf(Color(0xFF222A36), Color(0xFF323B4A), Color(0xFF4A5566)) else listOf(Color(0xFF7A8DA1), Color(0xFF94A5B7), Color(0xFFB6C3D0))
            Sky.Storm -> if (night) listOf(Color(0xFF0C0F15), Color(0xFF181C24), Color(0xFF282D37)) else listOf(Color(0xFF2B313C), Color(0xFF3B424E), Color(0xFF515865))
        }
    }

    /** Translucent card fill that reads like Apple's blurred material over this sky. */
    val card: Color get() = when {
        night -> Color.White.copy(alpha = 0.07f)
        sky == Sky.Clear || sky == Sky.PartlyCloudy || twilight -> Color(0xFF0A2A5A).copy(alpha = 0.22f)
        else -> Color.Black.copy(alpha = 0.13f)
    }

    val listCard: Brush get() = Brush.verticalGradient(colors)

    companion object {
        fun of(w: Weather?): Scene {
            if (w == null) return Scene(Sky.Clear, night = false, twilight = false)
            val now = w.now()
            val today = w.today
            val tw = listOfNotNull(today.sunrise, today.sunset).any { abs(Duration.between(it, now).toMinutes()) <= 35 }
            return Scene(w.condition.sky, night = !w.current.isDay, twilight = tw)
        }
    }
}

@Composable
fun rememberFrameTime(): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    if (LocalAnimate.current) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { t.floatValue = (it - start) / 1_000_000_000f }
        }
    }
    return t
}

private fun hash(i: Int, salt: Float = 0f): Float {
    val x = sin(i * 12.9898f + salt * 78.233f) * 43758.547f
    return x - floor(x)
}

@Composable
fun SkyBackground(scene: Scene, modifier: Modifier = Modifier) {
    val time = rememberFrameTime()
    Canvas(modifier.fillMaxSize()) { drawSky(scene, time.value) }
}

/** Gradient plus weather effects at time [t] seconds (also used to paint widget backgrounds). */
fun DrawScope.drawSky(scene: Scene, t: Float) {
    drawRect(Brush.verticalGradient(scene.colors))
    val night = scene.night
    when (scene.sky) {
        Sky.Clear -> if (night) { stars(t, 150); shootingStar(t) } else sun(t, scene.twilight, 1f)
        Sky.PartlyCloudy -> {
            if (night) stars(t, 80) else sun(t, scene.twilight, 0.8f)
            clouds(t, perLayer = 2, alpha = if (night) 0.10f else 0.30f, tint = Color.White, top = 0.06f, bottom = 0.42f)
        }
        Sky.Cloudy -> clouds(t, perLayer = 4, alpha = if (night) 0.09f else 0.20f, tint = if (night) Color(0xFFB0B8C4) else Color.White, top = -0.04f, bottom = 0.6f)
        Sky.Fog -> fog(t)
        Sky.Rain -> {
            clouds(t, perLayer = 3, alpha = if (night) 0.07f else 0.13f, tint = Color(0xFFC9D0DA), top = -0.08f, bottom = 0.3f)
            rain(t, 1f)
        }
        Sky.Snow -> {
            clouds(t, perLayer = 3, alpha = if (night) 0.07f else 0.14f, tint = Color.White, top = -0.08f, bottom = 0.3f)
            snow(t)
        }
        Sky.Storm -> {
            clouds(t, perLayer = 3, alpha = 0.10f, tint = Color(0xFFAEB6C2), top = -0.1f, bottom = 0.32f)
            rain(t, 1.35f)
            lightning(t)
        }
    }
}

// --- Sun -----------------------------------------------------------------------

/**
 * A glowing sun in the top-left corner with slowly turning, shimmering rays and a faint lens
 * flare, like Apple's clear-day sky. At sunrise and sunset it sits low and warm instead.
 */
private fun DrawScope.sun(t: Float, twilight: Boolean, strength: Float) {
    val w = size.width
    val c = if (twilight) Offset(w * 0.24f, size.height * 0.92f) else Offset(w * 0.18f, w * 0.05f)
    val warm = if (twilight) Color(0xFFFFC488) else Color(0xFFFFFDF2)

    val hr = w * 1.2f
    drawCircle(
        Brush.radialGradient(
            0f to warm.copy(alpha = 0.40f * strength),
            0.16f to warm.copy(alpha = 0.20f * strength),
            0.45f to warm.copy(alpha = 0.06f * strength),
            1f to Color.Transparent,
            center = c, radius = hr,
        ),
        hr, c,
    )

    // Rays: thin wedges fading outwards, each breathing at its own pace while the set turns slowly.
    val rays = 18
    val path = Path()
    for (i in 0 until rays) {
        val deg = i * (360f / rays) + hash(i, 51f) * 14f + t * 1.4f
        val half = (0.9f + hash(i, 52f) * 2.4f) * (PI / 180f).toFloat()
        val len = w * (0.75f + hash(i, 53f) * 0.85f)
        val shimmer = 0.5f + 0.5f * sin(t * (0.45f + hash(i, 54f) * 0.9f) + i * 2.1f)
        val alpha = (0.04f + 0.10f * shimmer) * strength
        val a = deg * (PI / 180f).toFloat()
        path.reset()
        path.moveTo(c.x, c.y)
        path.lineTo(c.x + cos(a - half) * len, c.y + sin(a - half) * len)
        path.lineTo(c.x + cos(a + half) * len, c.y + sin(a + half) * len)
        path.close()
        drawPath(
            path,
            Brush.radialGradient(0f to warm.copy(alpha = alpha), 0.55f to warm.copy(alpha = alpha * 0.35f), 1f to Color.Transparent, center = c, radius = len),
        )
    }

    val cr = w * 0.15f * (1f + 0.035f * sin(t * 1.1f))
    drawCircle(
        Brush.radialGradient(
            0f to Color.White,
            0.3f to Color.White.copy(alpha = 0.8f * strength),
            0.65f to warm.copy(alpha = 0.25f * strength),
            1f to Color.Transparent,
            center = c, radius = cr,
        ),
        cr, c,
    )

    if (twilight) return
    // Lens flare ghosts strung along the line from the sun through the screen.
    val target = Offset(w * 0.8f, size.height * 0.62f)
    val dir = target - c
    val sway = 0.012f * sin(t * 0.35f)
    val ghosts = listOf(
        Triple(0.30f, 0.045f, Color.White.copy(alpha = 0.08f)),
        Triple(0.46f, 0.022f, Color(0xFFB8F0FF).copy(alpha = 0.12f)),
        Triple(0.58f, 0.085f, Color(0xFFCFE6FF).copy(alpha = 0.06f)),
        Triple(0.80f, 0.032f, Color(0xFFFFE7B8).copy(alpha = 0.10f)),
        Triple(1.00f, 0.13f, Color.White.copy(alpha = 0.04f)),
    )
    for ((f, r, col) in ghosts) {
        val p = c + dir * (f + sway * (1f + f))
        val rr = w * r
        drawCircle(
            Brush.radialGradient(0f to col.copy(alpha = col.alpha * strength), 0.75f to col.copy(alpha = col.alpha * 0.7f * strength), 1f to Color.Transparent, center = p, radius = rr),
            rr, p,
        )
    }
}

// --- Night ---------------------------------------------------------------------

private fun DrawScope.stars(t: Float, count: Int) {
    for (i in 0 until count) {
        val x = hash(i) * size.width
        val y = hash(i, 1f) * hash(i, 4f) * size.height * 0.65f
        val bright = hash(i, 3f).let { it * it * it }
        val tw = 0.55f + 0.45f * sin(t * (0.5f + hash(i, 2f) * 1.8f) + i)
        val fade = 1f - y / (size.height * 0.75f)
        val a = ((0.25f + 0.75f * bright) * tw * fade).coerceIn(0f, 1f)
        val r = (0.5f + bright * 1.4f) * density
        val p = Offset(x, y)
        drawCircle(Color.White.copy(alpha = a), r, p)
        if (bright > 0.75f) {
            // The brightest few get a soft glow and a faint glint.
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = a * 0.35f), Color.Transparent), p, r * 5), r * 5, p)
            val g = r * 4.5f
            drawLine(Color.White.copy(alpha = a * 0.35f), Offset(x - g, y), Offset(x + g, y), 0.6f * density)
            drawLine(Color.White.copy(alpha = a * 0.35f), Offset(x, y - g), Offset(x, y + g), 0.6f * density)
        }
    }
}

/** A brief streak across the upper sky every ~13 seconds. */
private fun DrawScope.shootingStar(t: Float) {
    val period = 13f
    val tt = t + 5f
    val cycle = floor(tt / period).toInt()
    val phase = tt - cycle * period
    val dur = 0.9f
    if (phase > dur) return
    val p = phase / dur
    val start = Offset((0.35f + hash(cycle, 61f) * 0.6f) * size.width, (0.04f + hash(cycle, 62f) * 0.22f) * size.height)
    val ang = ((150f + hash(cycle, 63f) * 30f) * PI / 180f).toFloat()
    val d = Offset(cos(ang), sin(ang))
    val travel = size.width * 0.45f
    val head = start + d * (travel * p)
    val tail = head - d * (size.width * 0.2f * sin(PI.toFloat() * p))
    val a = sin(PI.toFloat() * p)
    drawLine(
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.9f * a), Color.Transparent), head, tail),
        head, tail, 1.6f * density, StrokeCap.Round,
    )
}

// --- Clouds --------------------------------------------------------------------

// A cumulus outline as (x, y, radius) puffs in units of the cloud's scale.
private val Puffs = floatArrayOf(
    -0.95f, 0.18f, 0.42f,
    -0.50f, -0.04f, 0.58f,
    0.05f, -0.28f, 0.72f,
    0.62f, -0.08f, 0.60f,
    1.05f, 0.20f, 0.42f,
    -0.25f, 0.28f, 0.52f,
    0.38f, 0.30f, 0.54f,
)

private fun DrawScope.cloud(cx: Float, cy: Float, s: Float, alpha: Float, tint: Color) {
    for (k in 0 until Puffs.size / 3) {
        val c = Offset(cx + Puffs[k * 3] * s, cy + Puffs[k * 3 + 1] * s)
        val r = Puffs[k * 3 + 2] * s
        drawCircle(
            Brush.radialGradient(0f to tint.copy(alpha = alpha), 0.55f to tint.copy(alpha = alpha * 0.7f), 1f to tint.copy(alpha = 0f), center = c, radius = r),
            r, c,
        )
    }
}

/** Three depth layers of drifting clouds; nearer ones are bigger, brighter and faster. */
private fun DrawScope.clouds(t: Float, perLayer: Int, alpha: Float, tint: Color, top: Float, bottom: Float) {
    val layers = 3
    val margin = 0.7f
    val span = 1f + 2 * margin
    for (l in 0 until layers) {
        val depth = (l + 1f) / layers
        for (j in 0 until perLayer) {
            val i = l * 31 + j
            val speed = (0.004f + 0.011f * depth) * (0.8f + 0.4f * hash(i, 5f))
            val fx = ((hash(i, 6f) * span + j * span / perLayer + t * speed) % span) - margin
            val fy = top + hash(i, 7f) * (bottom - top)
            val s = size.width * (0.14f + 0.16f * depth) * (0.8f + 0.5f * hash(i, 8f))
            cloud(fx * size.width, fy * size.height, s, alpha * (0.55f + 0.45f * depth), tint)
        }
    }
}

// --- Precipitation -------------------------------------------------------------

private fun DrawScope.rain(t: Float, intensity: Float) {
    // (count, length dp, width dp, alpha, screens per second)
    val layers = listOf(
        floatArrayOf(90f, 12f, 0.9f, 0.14f, 1.1f),
        floatArrayOf(60f, 20f, 1.3f, 0.24f, 1.6f),
        floatArrayOf(22f, 34f, 2.0f, 0.34f, 2.3f),
    )
    layers.forEachIndexed { l, (countF, lenDp, wDp, a, speed) ->
        val count = (countF * intensity).toInt()
        val len = lenDp * density
        for (k in 0 until count) {
            val i = l * 1000 + k
            val sp = speed * (0.85f + hash(i, 11f) * 0.3f)
            val y = ((hash(i, 12f) + t * sp) % 1f) * (size.height + len * 2) - len
            val x = hash(i, 13f) * size.width * 1.12f - y * 0.06f
            drawLine(
                Color(0xFFDDE8F5).copy(alpha = a * (0.7f + hash(i, 15f) * 0.6f)),
                Offset(x, y), Offset(x - len * 0.06f, y + len),
                strokeWidth = wDp * density, cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.snow(t: Float) {
    // (count, radius dp, alpha, screens per second, sway dp)
    val layers = listOf(
        floatArrayOf(80f, 1.1f, 0.55f, 0.035f, 8f),
        floatArrayOf(45f, 2.0f, 0.75f, 0.06f, 14f),
        floatArrayOf(14f, 4.2f, 0.65f, 0.10f, 22f),
    )
    layers.forEachIndexed { l, (countF, rDp, a, speed, swayDp) ->
        for (k in 0 until countF.toInt()) {
            val i = l * 1000 + k
            val sp = speed * (0.8f + hash(i, 21f) * 0.4f)
            val y = ((hash(i, 22f) + t * sp) % 1f) * (size.height + 40 * density) - 20 * density
            val x = hash(i, 23f) * size.width + sin(t * (0.5f + hash(i, 26f) * 0.6f) + i) * swayDp * density
            val r = rDp * (0.7f + hash(i, 24f) * 0.6f) * density
            val alpha = a * (0.7f + hash(i, 25f) * 0.3f)
            val p = Offset(x, y)
            if (l == 2) {
                // Near flakes are soft and out of focus.
                drawCircle(Brush.radialGradient(0f to Color.White.copy(alpha = alpha), 0.5f to Color.White.copy(alpha = alpha * 0.6f), 1f to Color.Transparent, center = p, radius = r * 1.6f), r * 1.6f, p)
            } else {
                drawCircle(Color.White.copy(alpha = alpha), r, p)
            }
        }
    }
}

// --- Fog and storms ------------------------------------------------------------

private fun DrawScope.fog(t: Float) {
    for (i in 0 until 8) {
        val y = (0.08f + i * 0.12f) * size.height
        val dir = if (i % 2 == 0) 1f else -1f
        val x = (((hash(i, 31f) + dir * t * 0.008f * (1 + i % 3)) % 1f + 1f) % 1f * 1.8f - 0.4f) * size.width
        val w = size.width * 1.6f
        val h = size.height * (0.14f + hash(i, 32f) * 0.08f)
        // A circular gradient squashed vertically, so each band fades out on every side.
        scale(1f, h / w, Offset(x, y)) {
            drawCircle(
                Brush.radialGradient(0f to Color.White.copy(alpha = 0.18f), 0.5f to Color.White.copy(alpha = 0.08f), 1f to Color.Transparent, center = Offset(x, y), radius = w / 2),
                w / 2, Offset(x, y),
            )
        }
    }
    drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.White.copy(alpha = 0.14f)))
}

/** Every ~7 seconds: a flickering flash, usually with a forked bolt. */
private fun DrawScope.lightning(t: Float) {
    val period = 7f
    val tt = t + 3f
    val cycle = floor(tt / period).toInt()
    val phase = tt - cycle * period
    val a = when {
        phase < 0.07f -> 1f
        phase in 0.12f..0.18f -> 0.6f
        phase in 0.24f..0.29f -> 0.3f
        else -> return
    }
    drawRect(Color.White.copy(alpha = 0.2f * a))
    if (hash(cycle, 41f) > 0.8f) return

    fun bolt(start: Offset, steps: Int, seed: Int, lean: Float): List<Offset> {
        val pts = mutableListOf(start)
        var x = start.x; var y = start.y
        for (k in 0 until steps) {
            y += size.height * 0.04f
            x += (hash(seed * 37 + k, 43f) - 0.5f + lean) * size.width * 0.09f
            pts += Offset(x, y)
        }
        return pts
    }
    fun pathOf(pts: List<Offset>) = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
    val mainPts = bolt(Offset((0.15f + 0.7f * hash(cycle, 42f)) * size.width, 0f), 12, cycle, 0f)
    val main = pathOf(mainPts)
    val branch = pathOf(bolt(mainPts[4], 5, cycle + 99, if (hash(cycle, 44f) > 0.5f) 0.35f else -0.35f))
    for ((path, scale) in listOf(main to 1f, branch to 0.6f)) {
        drawPath(path, Color(0xFFCFD8FF).copy(alpha = 0.22f * a * scale), style = Stroke(9f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Color.White.copy(alpha = a * scale), style = Stroke(1.8f * density * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
