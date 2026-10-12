package com.weatherclone.app.glance

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceAction
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Icon
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.TapAction
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Text
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerComplicationProvider
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import com.kieronquinn.app.smartspacer.sdk.utils.ComplicationTemplate
import com.kieronquinn.app.smartspacer.sdk.utils.TargetTemplate
import com.weatherclone.app.MainActivity
import com.weatherclone.app.R
import com.weatherclone.app.background.Sync
import com.weatherclone.app.data.DeviceLocation
import com.weatherclone.app.data.Store
import com.weatherclone.app.data.Summaries
import com.weatherclone.app.data.Units
import com.weatherclone.app.data.Weather
import com.weatherclone.app.data.WeatherRepository
import com.weatherclone.app.data.WxIcon
import com.weatherclone.app.ui.Render
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import android.graphics.drawable.Icon as AndroidIcon

/*
 * Pixel's At a Glance has no public API for third-party weather. Smartspacer (a free app by
 * Kieron Quinn) replaces At a Glance on Pixel and accepts plugins; these two providers are that
 * plugin. Without Smartspacer installed they are simply never called.
 */

/** Cached weather only — Smartspacer calls in on a binder thread, so never hit the network here. */
private fun cachedPrimary(ctx: Context): Weather? {
    val store = Store(ctx)
    val place = store.primaryPlace(DeviceLocation.hasPermission(ctx)) ?: return null
    val w = runBlocking { WeatherRepository.get(ctx).cached(place) }
    if (w == null || Duration.between(w.fetchedAt, Instant.now()).toMinutes() > 30) Sync.refreshSoon(ctx)
    return w
}

private fun openApp(ctx: Context) = TapAction(
    intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
)

/** The "☁ 58°" chip next to the date. */
class WeatherComplication : SmartspacerComplicationProvider() {
    override fun getSmartspaceActions(smartspacerId: String): List<SmartspaceAction> {
        val ctx = provideContext()
        val w = cachedPrimary(ctx) ?: return emptyList()
        val u = Units(Store(ctx).settings, DateFormat.is24HourFormat(ctx))
        return listOf(
            ComplicationTemplate.Basic(
                id = "stratus_weather",
                icon = Icon(AndroidIcon.createWithBitmap(Render.icon(w.condition.icon, 96)), w.condition.text, shouldTint = false),
                content = Text(u.temp(w.current.temp)),
                onClick = openApp(ctx),
            ).create()
        )
    }

    override fun getConfig(smartspacerId: String?) = Config(
        label = "Stratus Weather",
        description = "Current temperature and conditions for your location",
        icon = AndroidIcon.createWithResource(provideContext(), R.drawable.ic_notification),
    )
}

/** A full At a Glance card for severe alerts and imminent rain, like Google's weather. */
class WeatherTarget : SmartspacerTargetProvider() {
    override fun getSmartspaceTargets(smartspacerId: String): List<SmartspaceTarget> {
        val ctx = provideContext()
        val w = cachedPrimary(ctx) ?: return emptyList()
        val component = ComponentName(ctx, MainActivity::class.java)
        val alert = w.alerts.firstOrNull()
        val (id, title, subtitle, icon) = when {
            alert != null -> Quad("stratus_alert", alert.event, "${w.place.name} · ${alert.sender}", AndroidIcon.createWithResource(ctx, R.drawable.ic_alert))
            else -> {
                val rain = Summaries.nextHour(w) ?: return emptyList()
                Quad("stratus_rain", rain, w.place.name, AndroidIcon.createWithBitmap(Render.icon(WxIcon.Rain, 96)))
            }
        }
        return listOf(
            TargetTemplate.Basic(
                id = id,
                componentName = component,
                title = Text(title),
                subtitle = Text(subtitle),
                icon = Icon(icon, shouldTint = alert != null),
                onClick = openApp(ctx),
            ).create()
        )
    }

    override fun onDismiss(smartspacerId: String, targetId: String): Boolean = false

    override fun getConfig(smartspacerId: String?) = Config(
        label = "Stratus Alerts",
        description = "Severe weather warnings and next-hour precipitation",
        icon = AndroidIcon.createWithResource(provideContext(), R.drawable.ic_alert),
    )

    private data class Quad(val a: String, val b: String, val c: String, val d: AndroidIcon)
}
