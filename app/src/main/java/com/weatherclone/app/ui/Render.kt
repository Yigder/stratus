package com.weatherclone.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.weatherclone.app.data.WxIcon

/** Draws Compose graphics into plain bitmaps for widgets, notifications and At a Glance. */
object Render {
    private fun bitmap(w: Int, h: Int, density: Float = 1f, block: DrawScope.() -> Unit): Bitmap {
        val img = ImageBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1))
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat()), block)
        return img.asAndroidBitmap()
    }

    fun icon(icon: WxIcon, px: Int): Bitmap = bitmap(px, px) {
        val s = px / 100f
        scale(s, s, Offset.Zero) { drawWx(icon) }
    }

    fun sky(scene: Scene, w: Int, h: Int, density: Float): Bitmap = bitmap(w, h, density) { drawSky(scene, 0f) }

    /** 10-day style temperature range bar. */
    fun rangeBar(low: Double, high: Double, min: Double, max: Double, w: Int, h: Int): Bitmap = bitmap(w, h) {
        val r = CornerRadius(h / 2f)
        drawRoundRect(Color.Black.copy(alpha = 0.22f), cornerRadius = r)
        val span = (max - min).coerceAtLeast(1.0)
        val x0 = ((low - min) / span * w).toFloat()
        val x1 = ((high - min) / span * w).toFloat().coerceAtLeast(x0 + h)
        drawRoundRect(
            Brush.horizontalGradient(tempBrushStops(low, high), startX = x0, endX = x1),
            topLeft = Offset(x0, 0f), size = Size(x1 - x0, h.toFloat()), cornerRadius = r,
        )
    }

    /** Small location arrow used next to the city name. */
    fun arrow(px: Int): Bitmap = bitmap(px, px) { locationArrow(Color.White) }
}
