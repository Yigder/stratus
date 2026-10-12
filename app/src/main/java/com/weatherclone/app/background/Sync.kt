package com.weatherclone.app.background

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerComplicationProvider
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import com.weatherclone.app.data.DeviceLocation
import com.weatherclone.app.data.Store
import com.weatherclone.app.data.Weather
import com.weatherclone.app.data.WeatherRepository
import com.weatherclone.app.glance.GlanceWidget
import com.weatherclone.app.glance.LargeWidget
import com.weatherclone.app.glance.MediumWidget
import com.weatherclone.app.glance.SmallWidget
import com.weatherclone.app.glance.WeatherComplication
import com.weatherclone.app.glance.WeatherTarget
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Keeps widgets, the At a Glance (Smartspacer) entries and notifications up to date. */
object Sync {
    private const val PERIODIC = "stratus-refresh"
    private const val NOW = "stratus-refresh-now"

    /** Weather for the primary place; fetches when [fetch] or when the cache is older than 20 minutes. */
    suspend fun primary(ctx: Context, fetch: Boolean): Weather? {
        val store = Store(ctx)
        val place = store.primaryPlace(DeviceLocation.hasPermission(ctx)) ?: return null
        val repo = WeatherRepository.get(ctx)
        val cached = repo.cached(place)
        val stale = cached == null || Duration.between(cached.fetchedAt, Instant.now()).toMinutes() >= 20
        if (!fetch && !stale) return cached
        return runCatching { repo.fetch(place) }.getOrNull() ?: cached
    }

    /** Redraw every surface that shows the primary place. */
    suspend fun refreshSurfaces(ctx: Context) {
        runCatching {
            SmallWidget().updateAll(ctx)
            MediumWidget().updateAll(ctx)
            LargeWidget().updateAll(ctx)
            GlanceWidget().updateAll(ctx)
        }
        runCatching { SmartspacerComplicationProvider.notifyChange(ctx, WeatherComplication::class.java) }
        runCatching { SmartspacerTargetProvider.notifyChange(ctx, WeatherTarget::class.java) }
    }

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** One refresh as soon as possible (e.g. after a widget is added or settings change). */
    fun refreshSoon(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
    }
}

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val w = Sync.primary(applicationContext, fetch = true)
        Sync.refreshSurfaces(applicationContext)
        if (w != null) runCatching { Notifier.check(applicationContext, w) }
        return Result.success()
    }
}
