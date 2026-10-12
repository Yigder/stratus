package com.weatherclone.app.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.weatherclone.app.MainActivity
import com.weatherclone.app.R
import com.weatherclone.app.data.Store
import com.weatherclone.app.data.Summaries
import com.weatherclone.app.data.Units
import com.weatherclone.app.data.Weather
import com.weatherclone.app.ui.Render

object Notifier {
    const val SEVERE = "severe"
    const val RAIN = "rain"
    const val DAILY = "daily"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(SEVERE, "Severe Weather", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Government-issued weather warnings for your primary location"
            },
            NotificationChannel(RAIN, "Next-Hour Precipitation", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When rain or snow is about to start"
            },
            NotificationChannel(DAILY, "Daily Forecast", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A morning summary of the day's weather"
            },
        ))
    }

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @android.annotation.SuppressLint("MissingPermission")
    fun check(ctx: Context, w: Weather) {
        if (!canPost(ctx)) return
        val store = Store(ctx)
        val u = Units(store.settings, DateFormat.is24HourFormat(ctx))
        val nm = NotificationManagerCompat.from(ctx)
        val city = w.place.name

        // Severe weather: once per alert.
        if (store.notifySevere) {
            val seen = store.notifiedAlerts
            val fresh = w.alerts.filter { it.key !in seen }
            fresh.forEach { a ->
                val n = NotificationCompat.Builder(ctx, SEVERE)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("${a.event} · $city")
                    .setContentText(a.headline.ifBlank { a.sender })
                    .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(a.headline, a.description).filter { it.isNotBlank() }.joinToString("\n\n").take(1200)))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setContentIntent(openApp(ctx))
                    .setAutoCancel(true)
                    .build()
                nm.notify(a.key.hashCode(), n)
            }
            store.notifiedAlerts = (seen.intersect(w.alerts.map { it.key }.toSet())) + fresh.map { it.key }
        }

        // Next-hour precipitation: at most once every two hours.
        if (store.notifyRain) {
            val text = Summaries.nextHour(w)
            val now = System.currentTimeMillis()
            if (text != null && text.contains("starting") && now - store.lastRainNotice > 2 * 3600_000L) {
                val n = NotificationCompat.Builder(ctx, RAIN)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setLargeIcon(Render.icon(w.condition.icon.takeIf { com.weatherclone.app.data.Conditions.isPrecip(w.current.code) } ?: com.weatherclone.app.data.WxIcon.Rain, 128))
                    .setContentTitle(city)
                    .setContentText(text)
                    .setContentIntent(openApp(ctx))
                    .setAutoCancel(true)
                    .setTimeoutAfter(75 * 60_000L)
                    .build()
                nm.notify(RAIN.hashCode(), n)
                store.lastRainNotice = now
            }
        }

        // Daily forecast in the chosen hour.
        if (store.notifyDaily) {
            val local = w.now()
            val today = local.toLocalDate().toString()
            if (local.hour == store.dailyHour && store.lastDailyNotice != today) {
                val d = w.today
                val n = NotificationCompat.Builder(ctx, DAILY)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setLargeIcon(Render.icon(d.condition.icon, 128))
                    .setContentTitle("$city: ${d.condition.text}, H:${u.temp(d.high)} L:${u.temp(d.low)}")
                    .setContentText(Summaries.daily(w, d.date, u))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(Summaries.daily(w, d.date, u)))
                    .setContentIntent(openApp(ctx))
                    .setAutoCancel(true)
                    .build()
                nm.notify(DAILY.hashCode(), n)
                store.lastDailyNotice = today
            }
        }
    }
}
