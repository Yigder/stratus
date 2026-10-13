package com.weatherclone.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val SOURCE_URL = "https://github.com/Yigder/stratus"

/** Something Stratus ships with or draws on; [text] names its license text in assets/licenses/. */
private class Credit(val name: String, val by: String, val license: String, val text: String?, val url: String)

private val libraries = listOf(
    Credit("AndroidX: Compose, Material 3, Activity, Lifecycle, Glance, WorkManager", "The Android Open Source Project", "Apache 2.0", "apache-2.0", "https://developer.android.com/jetpack/androidx"),
    Credit("Kotlin, kotlinx.coroutines, kotlinx.serialization, Compose runtime", "JetBrains", "Apache 2.0", "apache-2.0", "https://kotlinlang.org"),
    Credit("OkHttp, Okio", "Square", "Apache 2.0", "apache-2.0", "https://square.github.io/okhttp"),
    Credit("Haze", "Chris Banes", "Apache 2.0", "apache-2.0", "https://github.com/chrisbanes/haze"),
    Credit("Smartspacer Plugin SDK", "Kieron Quinn", "Apache 2.0", "apache-2.0", "https://github.com/KieronQuinn/Smartspacer"),
    Credit("Guava ListenableFuture, JSpecify", "Google and others", "Apache 2.0", "apache-2.0", "https://github.com/google/guava"),
    Credit("Inter", "The Inter Project Authors", "SIL Open Font License 1.1", "ofl", "https://rsms.me/inter"),
)

private val dataSources = listOf(
    Credit("Open-Meteo", "Forecast, air quality, averages, city search", "CC BY 4.0", null, "https://open-meteo.com"),
    Credit("RainViewer", "Radar", "Free API", null, "https://www.rainviewer.com"),
    Credit("Esri", "Map tiles · Esri, HERE, Garmin, OpenStreetMap contributors", "Esri terms", null, "https://www.esri.com"),
    Credit("National Weather Service", "US forecast and alerts", "Public domain", null, "https://www.weather.gov"),
    Credit("Environment and Climate Change Canada", "Alerts in Canada", "Open Government Licence – Canada", null, "https://weather.gc.ca"),
    Credit("MeteoAlarm", "Alerts in Europe", "MeteoAlarm terms", null, "https://meteoalarm.org"),
)

private val Panel = Color(0xFF2C2C2E)
private val Blue = Color(0xFF0A84FF)

/** ⋯ → About Stratus: version, source code, Stratus's license, data sources and open-source licenses. */
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    IosSheet(onDismiss, Color(0xFF1C1C1E)) { close -> AboutContent(close) }
}

@Composable
fun AboutContent(close: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val version = remember(context) { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("About", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.align(Alignment.Center))
            Box(
                Modifier.align(Alignment.CenterEnd).size(30.dp).clip(CircleShape).background(Color(0xFF3A3A3C)).clickable(onClick = close),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, null, tint = Color(0xFFAEAEB2), modifier = Modifier.size(18.dp)) }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text("Stratus", fontSize = 34.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 4.dp))
            Text("Version $version", fontSize = 15.sp, color = Secondary, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            Note("Stratus is free software under the GNU General Public License, version 3 or later. You can use, study, share and change it; copies and changed versions have to stay under the same license, with their source. It's an independent project, not affiliated with Apple or Google.")
            Group {
                LinkRow("Source Code", SOURCE_URL.removePrefix("https://")) { uri.openUri(SOURCE_URL) }
                Divider()
                LicenseRow("GNU GPL 3.0", "Stratus's license", "gpl-3.0")
            }
            Header("WEATHER DATA")
            Group {
                dataSources.forEachIndexed { i, d ->
                    if (i > 0) Divider()
                    LinkRow(d.name, "${d.by} · ${d.license}") { uri.openUri(d.url) }
                }
            }
            Header("BUILT WITH")
            Note("Tap one to read its license.")
            Group {
                libraries.forEachIndexed { i, l ->
                    if (i > 0) Divider()
                    LicenseRow(l.name, "${l.license} · ${l.by}", l.text!!, l.url)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Header(text: String) =
    Text(text, color = Color(0xFF8E8E93), fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 6.dp))

@Composable
private fun Note(text: String) =
    Text(text, color = Color(0xFF8E8E93), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp))

@Composable
private fun Group(content: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Panel)) { content() }

@Composable
private fun Divider() = HorizontalDivider(color = Color(0xFF3A3A3C), thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))

@Composable
private fun TextRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp)
            Text(subtitle, color = Color(0xFF8E8E93), fontSize = 13.sp)
        }
        Icon(icon, null, tint = Color(0xFF8E8E93), modifier = Modifier.padding(start = 8.dp).size(20.dp))
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) = TextRow(title, subtitle, Icons.AutoMirrored.Filled.OpenInNew, onClick)

/** A library or license that opens in place to show its full license text. */
@Composable
private fun LicenseRow(title: String, subtitle: String, text: String, url: String? = null) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    TextRow(title, subtitle, if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown) { open = !open }
    AnimatedVisibility(open) {
        Column {
            if (url != null) {
                Divider()
                LinkRow("Project Page", url.removePrefix("https://")) { uri.openUri(url) }
            }
            Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) { LicenseText(text) }
        }
    }
}

/** License files are wrapped at ~80 columns; join each paragraph's lines so they wrap to the screen instead. */
private fun reflow(text: String): String =
    text.split(Regex("\\n\\s*\\n")).joinToString("\n\n") { para -> para.lines().joinToString(" ") { it.trim() }.trim() }.trim()

@Composable
private fun LicenseText(name: String) {
    val context = LocalContext.current
    val text by produceState<String?>(null, name) {
        value = withContext(Dispatchers.IO) {
            runCatching { reflow(context.assets.open("licenses/$name.txt").use { it.readBytes().decodeToString() }) }.getOrNull()
        }
    }
    Text(text ?: "", color = Color(0xFF8E8E93), fontSize = 12.sp, lineHeight = 16.sp)
}
