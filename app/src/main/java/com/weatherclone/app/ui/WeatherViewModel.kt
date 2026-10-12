package com.weatherclone.app.ui

import android.app.Application
import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weatherclone.app.data.DeviceLocation
import com.weatherclone.app.data.Place
import com.weatherclone.app.data.Settings
import com.weatherclone.app.data.Store
import com.weatherclone.app.data.Weather
import com.weatherclone.app.data.WeatherRepository
import com.weatherclone.app.background.Sync
import com.weatherclone.app.data.NotifPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

class WeatherViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    val repo = WeatherRepository.get(app)

    var settings by mutableStateOf(store.settings)
        private set
    var current by mutableStateOf(store.currentPlace)
        private set
    var locationEnabled by mutableStateOf(DeviceLocation.hasPermission(app))
        private set
    val saved = mutableStateListOf<Place>().apply { addAll(store.places) }
    val weather = mutableStateMapOf<String, Weather>()
    val failed = mutableStateMapOf<String, Boolean>()
    var radar by mutableStateOf<Radar?>(null)
        private set
    private var radarAt: Instant = Instant.EPOCH
    private val jobs = HashMap<String, Job>()

    val places: List<Place>
        get() = listOfNotNull(current?.takeIf { locationEnabled }) + saved

    var lastPage: Int
        get() = store.lastPage
        set(v) { store.lastPage = v }

    val askedLocation get() = store.askedLocation

    init {
        Tiles.client = repo.client
        viewModelScope.launch {
            places.forEach { p -> repo.cached(p)?.let { weather[p.id] = it } }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        store.askedLocation = true
        locationEnabled = granted
        if (granted) refreshLocation()
        if (!granted && saved.isEmpty()) {
            add(Place("geo_5128581", "New York", region = "New York", country = "United States", countryCode = "US", lat = 40.71427, lon = -74.00597))
        }
    }

    fun refreshLocation() {
        if (!DeviceLocation.hasPermission(getApplication())) { locationEnabled = false; return }
        locationEnabled = true
        viewModelScope.launch {
            val loc: Location = DeviceLocation.get(getApplication()) ?: run { current?.let { load(it) }; return@launch }
            val prev = current
            val moved = prev == null || distanceKm(prev.lat, prev.lon, loc.latitude, loc.longitude) > 1.5 || prev.name == "My Location"
            val place = if (moved) repo.reverseGeocode(loc.latitude, loc.longitude) else prev!!
            current = place
            store.currentPlace = place
            load(place, force = moved)
        }
    }

    fun refreshAll(force: Boolean = false) {
        if (locationEnabled) refreshLocation()
        saved.forEach { load(it, force) }
        refreshRadar()
    }

    private fun refreshRadar() {
        if (Duration.between(radarAt, Instant.now()).toMinutes() < 10 && radar != null) return
        viewModelScope.launch {
            repo.radarFrames()?.let { (host, frames) -> radar = Radar(host, frames); radarAt = Instant.now() }
        }
    }

    fun load(p: Place, force: Boolean = false) {
        val have = weather[p.id]
        if (!force && have != null && Duration.between(have.fetchedAt, Instant.now()).toMinutes() < 10 && have.place == p) return
        if (jobs[p.id]?.isActive == true) return
        jobs[p.id] = viewModelScope.launch {
            if (have == null) repo.cached(p)?.let { weather[p.id] = it }
            runCatching { repo.fetch(p) }
                .onSuccess {
                    weather[p.id] = it; failed.remove(p.id)
                    if (p.id == primaryId()) Sync.refreshSurfaces(getApplication())
                }
                .onFailure { failed[p.id] = true }
        }
    }

    fun add(p: Place) {
        if (saved.any { it.id == p.id }) return
        saved += p
        store.places = saved.toList()
        load(p, force = p.id == primaryId())
    }

    fun addWithWeather(p: Place, w: Weather?) {
        if (w != null) weather[p.id] = w
        add(p)
    }

    fun remove(p: Place) {
        saved.removeAll { it.id == p.id }
        store.places = saved.toList()
        weather.remove(p.id)
        viewModelScope.launch { Sync.refreshSurfaces(getApplication()) }
    }

    /** Indices are into [places]. */
    fun move(from: Int, to: Int) {
        val offset = if (current != null && locationEnabled) 1 else 0
        val f = from - offset; val t = to - offset
        if (f !in saved.indices || t !in saved.indices) return
        val item = saved.removeAt(f)
        saved.add(t, item)
        store.places = saved.toList()
    }

    fun updateSettings(s: Settings) {
        settings = s
        store.settings = s
        viewModelScope.launch { Sync.refreshSurfaces(getApplication()) }
    }

    private fun primaryId(): String? = store.primaryPlace(locationEnabled)?.id

    // --- Notifications -------------------------------------------------------------

    var notif by mutableStateOf(NotifPrefs(store.notifySevere, store.notifyRain, store.notifyDaily, store.dailyHour))
        private set

    fun updateNotif(n: NotifPrefs) {
        notif = n
        store.notifySevere = n.severe; store.notifyRain = n.rain; store.notifyDaily = n.daily; store.dailyHour = n.hour
    }

    // --- Map ---------------------------------------------------------------------------

    /** Map model data; kept here so reopening the map reuses what was already loaded. */
    val gridCache = GridCache(repo::fetchText)

    suspend fun search(q: String) = repo.search(q)

    suspend fun preview(p: Place): Weather? = runCatching { repo.fetch(p) }.getOrNull()

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, r)
        return r[0] / 1000.0
    }
}
