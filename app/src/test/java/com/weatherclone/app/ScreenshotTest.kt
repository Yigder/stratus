package com.weatherclone.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.weatherclone.app.data.Parse
import com.weatherclone.app.data.Place
import com.weatherclone.app.data.Settings
import com.weatherclone.app.data.Units
import com.weatherclone.app.data.Weather
import com.weatherclone.app.ui.DetailContent
import com.weatherclone.app.ui.ListScreen
import com.weatherclone.app.ui.LocalAnimate
import com.weatherclone.app.ui.LocalScene
import com.weatherclone.app.ui.LocalUnits
import com.weatherclone.app.ui.LocationPage
import com.weatherclone.app.ui.Metric
import com.weatherclone.app.ui.Scene
import com.weatherclone.app.ui.drawSky
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.util.Locale

/** Renders key screens to PNGs in build/screens so the UI can be reviewed without a device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi", application = android.app.Application::class)
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun res(name: String) = File("src/test/resources/$name").takeIf { it.exists() }?.readText()

    private val place = Place("current", "New York", region = "New York", country = "United States", countryCode = "US", lat = 40.7143, lon = -74.006, isCurrent = true)

    private fun weather(): Weather = Parse.weather(place, res("forecast.json")!!, res("air.json"), res("alerts.json"), res("archive.json"), Instant.now())

    private fun shot(name: String) {
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        MaterialTheme(colorScheme = darkColorScheme(), typography = com.weatherclone.app.ui.AppTypography) {
            CompositionLocalProvider(LocalAnimate provides false, LocalUnits provides Units(Settings.defaults(Locale.US), false)) {
                Box(Modifier.fillMaxSize().background(Color.Black)) { content() }
            }
        }
    }

    @Test
    fun page() {
        val w = weather()
        rule.mainClock.autoAdvance = false
        rule.setContent { Themed { LocationPage(place, w, false, null, 80.dp, {}, topInset = 40.dp) } }
        rule.mainClock.advanceTimeBy(500)
        shot("page_top")
        rule.onRoot().performTouchInput { swipeUp(startY = centerY + 300, endY = centerY - 100, durationMillis = 600) }
        rule.mainClock.advanceTimeBy(2000)
        shot("page_scroll1")
        rule.onRoot().performTouchInput { swipeUp(startY = bottom - 100, endY = top + 100, durationMillis = 400) }
        rule.mainClock.advanceTimeBy(3000)
        shot("page_scroll2")
        rule.onRoot().performTouchInput { swipeUp(startY = bottom - 100, endY = top + 100, durationMillis = 400) }
        rule.mainClock.advanceTimeBy(3000)
        shot("page_scroll3")
        rule.onRoot().performTouchInput { swipeUp(startY = bottom - 100, endY = top + 100, durationMillis = 400) }
        rule.mainClock.advanceTimeBy(3000)
        shot("page_scroll4")
    }

    private fun scene(name: String, code: Int, day: Boolean) {
        val base = weather()
        val w = base.copy(current = base.current.copy(code = code, isDay = day, cloud = if (code >= 3) 100 else 0))
        rule.mainClock.autoAdvance = false
        rule.setContent { Themed { LocationPage(place.copy(isCurrent = false), w, false, null, 80.dp, {}, topInset = 40.dp) } }
        rule.mainClock.advanceTimeBy(500)
        shot("scene_$name")
    }

    @Test
    fun skies() {
        var current by androidx.compose.runtime.mutableStateOf(com.weatherclone.app.ui.Scene(com.weatherclone.app.data.Sky.Clear, false, false) to 5f)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { with(this) { drawSky(current.first, current.second) } }
        }
        listOf(
            Triple("clear", com.weatherclone.app.ui.Scene(com.weatherclone.app.data.Sky.Clear, false, false), 5f),
            Triple("partly", com.weatherclone.app.ui.Scene(com.weatherclone.app.data.Sky.PartlyCloudy, false, false), 5f),
            Triple("cloudy", com.weatherclone.app.ui.Scene(com.weatherclone.app.data.Sky.Cloudy, false, false), 5f),
            Triple("sunset", com.weatherclone.app.ui.Scene(com.weatherclone.app.data.Sky.Clear, false, true), 5f),
            Triple("night", com.weatherclone.app.ui.Scene(com.weatherclone.app.data.Sky.Clear, true, false), 8.4f),
            Triple("rain", com.weatherclone.app.ui.Scene(com.weatherclone.app.data.Sky.Rain, false, false), 5f),
            Triple("snow", com.weatherclone.app.ui.Scene(com.weatherclone.app.data.Sky.Snow, false, false), 5f),
            Triple("fog", com.weatherclone.app.ui.Scene(com.weatherclone.app.data.Sky.Fog, false, false), 5f),
            Triple("storm", com.weatherclone.app.ui.Scene(com.weatherclone.app.data.Sky.Storm, false, false), 4.03f),
        ).forEach { (name, scene, t) ->
            current = scene to t
            rule.mainClock.advanceTimeBy(100)
            shot("sky_$name")
        }
    }

    @Test fun sceneClear() = scene("clear", 0, true)
    @Test fun sceneNight() = scene("night_clear", 0, false)
    @Test fun sceneRain() = scene("rain", 63, true)
    @Test fun sceneSnow() = scene("snow_night", 73, false)
    @Test fun sceneStorm() = scene("storm", 95, true)

    @Test
    fun detail() {
        val w = weather()
        rule.setContent { Themed { CompositionLocalProvider(LocalScene provides Scene.of(w)) { Box(Modifier.background(Color(0xFF1C1C1E))) { DetailContent(w, Metric.Conditions, null) {} } } } }
        shot("detail_conditions")
    }

    @Test
    fun detailConditionsChance() {
        val w = weather()
        rule.setContent { Themed { CompositionLocalProvider(LocalScene provides Scene.of(w)) { Box(Modifier.background(Color(0xFF1C1C1E))) { DetailContent(w, Metric.Conditions, null) {} } } } }
        rule.onRoot().performTouchInput { swipeUp(startY = bottom - 100, endY = centerY, durationMillis = 400) }
        shot("detail_conditions_chance")
    }

    @Test
    fun detailConditionsScrub() {
        val w = weather()
        rule.setContent { Themed { CompositionLocalProvider(LocalScene provides Scene.of(w)) { Box(Modifier.background(Color(0xFF1C1C1E))) { DetailContent(w, Metric.Conditions, null) {} } } } }
        // Scrub the temperature chart; the chance chart below keeps its own (empty) selection.
        rule.onRoot().performTouchInput { down(Offset(width * 0.3f, top + 1150f)); moveTo(Offset(width * 0.55f, top + 1150f)) }
        shot("detail_conditions_scrub")
        rule.onRoot().performTouchInput { up() }
    }

    private fun detailOf(metric: Metric, name: String) {
        val w = weather()
        rule.setContent { Themed { Box(Modifier.background(Color(0xFF1C1C1E))) { DetailContent(w, metric, null) {} } } }
        shot("detail_$name")
    }

    @Test fun detailFeels() = detailOf(Metric.Feels, "feels")
    @Test fun detailWind() = detailOf(Metric.Wind, "wind")
    @Test fun detailPrecip() = detailOf(Metric.Precip, "precip")

    @Test
    fun detailSunScrub() {
        val w = weather()
        rule.setContent { Themed { Box(Modifier.background(Color(0xFF1C1C1E))) { DetailContent(w, Metric.Sun, null) {} } } }
        shot("detail_sun")
        // Press on the curve, then drag sideways to scrub to mid-afternoon, and hold.
        rule.onRoot().performTouchInput { down(Offset(width * 0.45f, top + 1000f)); moveTo(Offset(width * 0.6f, top + 1000f)) }
        shot("detail_sun_scrub")
        rule.onRoot().performTouchInput { up() }
    }

    @Test
    fun detailUv() {
        val w = weather()
        rule.setContent { Themed { Box(Modifier.background(Color(0xFF1C1C1E))) { DetailContent(w, Metric.Uv, null) {} } } }
        shot("detail_uv")
    }

    @Test
    fun list() {
        val w = weather()
        val other = Place("geo_1", "Cupertino", region = "California", country = "United States", countryCode = "US", lat = 37.3, lon = -122.0)
        val w2 = w.copy(place = other, current = w.current.copy(code = 0, isDay = false, temp = 14.0))
        rule.setContent {
            Themed {
                ListScreen(
                    places = listOf(place, other), weather = mapOf(place.id to w, other.id to w2),
                    settings = Settings.defaults(Locale.US), locationEnabled = true,
                    onOpen = {}, onRemove = {}, onMove = { _, _ -> }, onSettings = {}, onEnableLocation = {},
                    search = { emptyList() }, onPreview = {},
                )
            }
        }
        shot("list")
    }
}
