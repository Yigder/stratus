package com.weatherclone.app.data

import android.content.Context
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class WeatherRepository private constructor(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .cache(Cache(File(context.cacheDir, "http"), 30L * 1024 * 1024))
        .build()

    val client: OkHttpClient get() = http

    private val cacheDir = File(context.filesDir, "weather").apply { mkdirs() }

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code} for $url")
            return res.body.string()
        }
    }

    private fun file(name: String) = File(cacheDir, name)
    private fun readCache(name: String): String? = file(name).takeIf { it.exists() }?.readText()
    private fun writeCache(name: String, data: String) = file(name).writeText(data)
    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    /** Last weather we have on disk for [place], instantly available on launch. */
    suspend fun cached(place: Place): Weather? = withContext(Dispatchers.IO) {
        val id = safe(place.id)
        val fc = readCache("fc_$id.json") ?: return@withContext null
        val ts = file("fc_$id.json").lastModified()
        runCatching {
            Parse.weather(place, fc, readCache("aq_$id.json"), readCache("al_$id.json"), readCache(normalsKey(place)), Instant.ofEpochMilli(ts))
        }.getOrNull()
    }

    suspend fun fetch(place: Place): Weather = withContext(Dispatchers.IO) {
        val id = safe(place.id)
        coroutineScope {
            val fc = async { get(forecastUrl(place.lat, place.lon)) }
            val aq = async { runCatching { get(airUrl(place.lat, place.lon)) }.getOrNull() }
            val al = async { alerts(place) }
            val nm = async { normalsJson(place) }
            val forecast = fc.await()
            writeCache("fc_$id.json", forecast)
            aq.await()?.let { writeCache("aq_$id.json", it) }
            // Keep the last good alert list if the alert feed is temporarily unreachable.
            val alerts = al.await()?.let { Alerts.encode(it) }?.also { writeCache("al_$id.json", it) } ?: readCache("al_$id.json")
            Parse.weather(place, forecast, aq.await() ?: readCache("aq_$id.json"), alerts, nm.await(), Instant.now())
        }
    }

    private val meteoAlarmCache = HashMap<String, Pair<Long, String>>()

    /** Alerts for [p] from whichever government feed covers it; null when the feed failed. */
    private fun alerts(p: Place): List<Alert>? = runCatching {
        when (Alerts.sourceFor(p)) {
            Alerts.Source.NWS -> Alerts.parseNws(get(alertsUrl(p.lat, p.lon), mapOf("Accept" to "application/geo+json")))
            Alerts.Source.ECCC -> Alerts.parseEccc(get(Alerts.ecccUrl(p.lat, p.lon)), p.lat, p.lon)
            Alerts.Source.MeteoAlarm -> {
                val cc = p.countryCode.uppercase(Locale.US)
                val cached = synchronized(meteoAlarmCache) { meteoAlarmCache[cc] }
                val raw = if (cached != null && System.currentTimeMillis() - cached.first < 15 * 60_000) cached.second
                else get(Alerts.meteoAlarmUrl(cc)!!).also { synchronized(meteoAlarmCache) { meteoAlarmCache[cc] = System.currentTimeMillis() to it } }
                Alerts.parseMeteoAlarm(raw, p)
            }
            Alerts.Source.None -> emptyList()
        }
    }.getOrNull()

    private fun normalsKey(p: Place) = String.format(Locale.US, "nm_%.1f_%.1f.json", p.lat, p.lon)

    /** Ten years of daily highs/lows, cached forever per ~10 km grid cell. */
    private fun normalsJson(p: Place): String? {
        readCache(normalsKey(p))?.let { return it }
        val year = LocalDate.now().year
        val url = "https://archive-api.open-meteo.com/v1/archive?latitude=${p.lat}&longitude=${p.lon}" +
            "&start_date=${year - 10}-01-01&end_date=${year - 1}-12-31" +
            "&daily=temperature_2m_max,temperature_2m_min&timezone=auto"
        return runCatching { get(url) }.getOrNull()?.also { writeCache(normalsKey(p), it) }
    }

    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val lang = Locale.getDefault().language.ifBlank { "en" }
        Parse.geocodeResults(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=12&language=$lang&format=json"))
    }

    /** Names a coordinate, trying the platform geocoder first and a free web service second. */
    suspend fun reverseGeocode(lat: Double, lon: Double): Place = withContext(Dispatchers.IO) {
        val base = Place(Place.CURRENT_ID, "My Location", lat = lat, lon = lon, isCurrent = true)
        val viaGeocoder = runCatching { platformReverse(lat, lon) }.getOrNull()
        if (viaGeocoder != null) return@withContext base.copy(
            name = viaGeocoder[0], region = viaGeocoder[1], country = viaGeocoder[2], countryCode = viaGeocoder[3], district = viaGeocoder[4],
        )
        runCatching {
            val r = Parse.reverse(get("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=en"))
            base.copy(name = r[0].ifBlank { r[1] }, region = r[1], country = r[2], countryCode = r[3])
        }.getOrDefault(base)
    }

    private suspend fun platformReverse(lat: Double, lon: Double): List<String>? {
        if (!Geocoder.isPresent()) return null
        val g = Geocoder(context, Locale.getDefault())
        val addr = if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                g.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) { cont.resume(addresses.firstOrNull()) }
                    override fun onError(errorMessage: String?) { cont.resume(null) }
                })
            }
        } else {
            @Suppress("DEPRECATION")
            g.getFromLocation(lat, lon, 1)?.firstOrNull()
        } ?: return null
        val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: return null
        return listOf(city, addr.adminArea ?: "", addr.countryName ?: "", addr.countryCode ?: "", addr.subAdminArea ?: "")
    }

    suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) { get(url) }

    /** RainViewer radar frames: host + (unix time, path) pairs. */
    suspend fun radarFrames(): Pair<String, List<Pair<Long, String>>>? = withContext(Dispatchers.IO) {
        runCatching { Parse.radarFrames(get("https://api.rainviewer.com/public/weather-maps.json")) }.getOrNull()
    }

    companion object {
        @Volatile private var instance: WeatherRepository? = null

        /** One instance per process so the app, widgets and background work share an HTTP cache. */
        fun get(context: Context): WeatherRepository =
            instance ?: synchronized(this) { instance ?: WeatherRepository(context.applicationContext).also { instance = it } }

        const val USER_AGENT ="WeatherClone/1.0 (Android; open-source personal weather app)"

        fun forecastUrl(lat: Double, lon: Double) = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover," +
            "pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,dew_point_2m,visibility,uv_index" +
            "&hourly=temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m,precipitation_probability," +
            "precipitation,weather_code,cloud_cover,visibility,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,is_day,pressure_msl" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min," +
            "sunrise,sunset,daylight_duration,uv_index_max,precipitation_sum,precipitation_probability_max," +
            "wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant" +
            "&minutely_15=precipitation&past_minutely_15=1&forecast_minutely_15=10" +
            "&past_days=1&forecast_days=10&timezone=auto"

        fun airUrl(lat: Double, lon: Double) = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon" +
            "&current=us_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,sulphur_dioxide,carbon_monoxide" +
            "&hourly=us_aqi&past_days=1&forecast_days=5&timezone=auto"

        fun alertsUrl(lat: Double, lon: Double) =
            String.format(Locale.US, "https://api.weather.gov/alerts/active?point=%.4f,%.4f", lat, lon)
    }
}

/** Saved locations and unit settings. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var places: List<Place>
        get() = prefs.getString("places", null)?.let { runCatching { json.decodeFromString(ListSerializer(Place.serializer()), it) }.getOrNull() } ?: emptyList()
        set(v) = prefs.edit().putString("places", json.encodeToString(ListSerializer(Place.serializer()), v)).apply()

    var currentPlace: Place?
        get() = prefs.getString("current", null)?.let { runCatching { json.decodeFromString(Place.serializer(), it) }.getOrNull() }
        set(v) = prefs.edit().putString("current", v?.let { json.encodeToString(Place.serializer(), it) }).apply()

    var settings: Settings
        get() = prefs.getString("settings", null)?.let { runCatching { json.decodeFromString(Settings.serializer(), it) }.getOrNull() } ?: Settings.defaults()
        set(v) = prefs.edit().putString("settings", json.encodeToString(Settings.serializer(), v)).apply()

    var lastPage: Int
        get() = prefs.getInt("page", 0)
        set(v) = prefs.edit().putInt("page", v).apply()

    var askedLocation: Boolean
        get() = prefs.getBoolean("askedLocation", false)
        set(v) = prefs.edit().putBoolean("askedLocation", v).apply()

    // --- Notifications ---------------------------------------------------------------

    var notifySevere: Boolean
        get() = prefs.getBoolean("nSevere", true)
        set(v) = prefs.edit().putBoolean("nSevere", v).apply()

    var notifyRain: Boolean
        get() = prefs.getBoolean("nRain", true)
        set(v) = prefs.edit().putBoolean("nRain", v).apply()

    var notifyDaily: Boolean
        get() = prefs.getBoolean("nDaily", false)
        set(v) = prefs.edit().putBoolean("nDaily", v).apply()

    /** Hour of day (local) for the daily forecast notification. */
    var dailyHour: Int
        get() = prefs.getInt("nDailyHour", 7)
        set(v) = prefs.edit().putInt("nDailyHour", v).apply()

    var notifiedAlerts: Set<String>
        get() = prefs.getStringSet("nAlerts", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("nAlerts", v).apply()

    var lastRainNotice: Long
        get() = prefs.getLong("nRainAt", 0)
        set(v) = prefs.edit().putLong("nRainAt", v).apply()

    var lastDailyNotice: String
        get() = prefs.getString("nDailyDate", "") ?: ""
        set(v) = prefs.edit().putString("nDailyDate", v).apply()

    /** The location widgets, At a Glance and notifications follow: current location, else the first saved city. */
    fun primaryPlace(hasLocation: Boolean): Place? =
        currentPlace?.takeIf { hasLocation } ?: places.firstOrNull() ?: currentPlace
}
