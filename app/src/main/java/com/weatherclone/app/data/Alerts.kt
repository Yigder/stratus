package com.weatherclone.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.text.Normalizer
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

/**
 * Severe-weather alerts from free government feeds:
 *  - United States: National Weather Service (point query)
 *  - Canada: Environment and Climate Change Canada (polygons)
 *  - Europe: MeteoAlarm, 38 countries (matched by polygon when given, otherwise by area name)
 */
object Alerts {
    private val json = Json { ignoreUnknownKeys = true }

    enum class Source { NWS, ECCC, MeteoAlarm, None }

    val meteoAlarmSlugs = mapOf(
        "AT" to "austria", "BE" to "belgium", "BA" to "bosnia-herzegovina", "BG" to "bulgaria", "HR" to "croatia",
        "CY" to "cyprus", "CZ" to "czechia", "DK" to "denmark", "EE" to "estonia", "FI" to "finland", "FR" to "france",
        "DE" to "germany", "GR" to "greece", "HU" to "hungary", "IS" to "iceland", "IE" to "ireland", "IL" to "israel",
        "IT" to "italy", "LV" to "latvia", "LT" to "lithuania", "LU" to "luxembourg", "MT" to "malta", "MD" to "moldova",
        "ME" to "montenegro", "NL" to "netherlands", "MK" to "republic-of-north-macedonia", "NO" to "norway",
        "PL" to "poland", "PT" to "portugal", "RO" to "romania", "RS" to "serbia", "SK" to "slovakia", "SI" to "slovenia",
        "ES" to "spain", "SE" to "sweden", "CH" to "switzerland", "UA" to "ukraine", "GB" to "united-kingdom",
    )

    fun sourceFor(p: Place): Source {
        val cc = p.countryCode.uppercase(Locale.US)
        return when {
            cc == "US" || cc in setOf("PR", "GU", "VI", "AS", "MP") -> Source.NWS
            cc == "CA" -> Source.ECCC
            cc in meteoAlarmSlugs -> Source.MeteoAlarm
            cc.isBlank() && p.lat in 24.0..50.0 && p.lon in -125.0..-66.0 -> Source.NWS
            else -> Source.None
        }
    }

    // --- NWS -------------------------------------------------------------------

    fun parseNws(raw: String): List<Alert> {
        val root = json.parseToJsonElement(raw).jsonObject
        val features = root["features"] as? JsonArray ?: return emptyList()
        return features.mapNotNull { f ->
            val p = (f as? JsonObject)?.get("properties") as? JsonObject ?: return@mapNotNull null
            Alert(
                event = p.str("event") ?: "Weather Alert",
                headline = p.str("headline") ?: "",
                description = p.str("description") ?: "",
                instruction = p.str("instruction") ?: "",
                severity = p.str("severity") ?: "",
                sender = p.str("senderName") ?: "National Weather Service",
                endsEpoch = (p.str("ends") ?: p.str("expires"))?.let(::epoch),
            )
        }.distinctBy { it.event }
    }

    // --- Environment Canada ----------------------------------------------------

    fun ecccUrl(lat: Double, lon: Double) = String.format(
        Locale.US,
        "https://api.weather.gc.ca/collections/weather-alerts/items?f=json&lang=en&limit=50&bbox=%.4f,%.4f,%.4f,%.4f",
        lon - 0.02, lat - 0.02, lon + 0.02, lat + 0.02,
    )

    fun parseEccc(raw: String, lat: Double, lon: Double): List<Alert> {
        val root = json.parseToJsonElement(raw).jsonObject
        val features = root["features"] as? JsonArray ?: return emptyList()
        val now = Instant.now().toEpochMilli()
        return features.mapNotNull { f ->
            val o = f as? JsonObject ?: return@mapNotNull null
            val p = o["properties"] as? JsonObject ?: return@mapNotNull null
            if (p.str("status_en") == "ended") return@mapNotNull null
            val ends = p.str("event_end_datetime")?.let(::epoch) ?: p.str("expiration_datetime")?.let(::epoch)
            if (ends != null && ends < now) return@mapNotNull null
            val geom = o["geometry"] as? JsonObject
            if (geom != null && !geoJsonContains(geom, lat, lon)) return@mapNotNull null
            val name = p.str("alert_short_name_en") ?: p.str("alert_name_en") ?: "Weather Alert"
            Alert(
                event = name.replaceFirstChar { it.uppercase() },
                headline = p.str("feature_name_en") ?: "",
                description = p.str("alert_text_en") ?: "",
                instruction = "",
                severity = p.str("risk_colour_en") ?: "",
                sender = "Environment Canada",
                endsEpoch = ends,
            )
        }.distinctBy { it.event }
    }

    // --- MeteoAlarm -------------------------------------------------------------

    fun meteoAlarmUrl(countryCode: String) =
        meteoAlarmSlugs[countryCode.uppercase(Locale.US)]?.let { "https://feeds.meteoalarm.org/api/v1/warnings/feeds-$it" }

    fun parseMeteoAlarm(raw: String, p: Place): List<Alert> {
        val root = json.parseToJsonElement(raw).jsonObject
        val warnings = root["warnings"] as? JsonArray ?: return emptyList()
        val now = Instant.now().toEpochMilli()
        val names = listOf(p.name, p.district, p.region).map(::norm).filter { it.length >= 3 }.distinct()
        val lang = Locale.getDefault().language
        val out = mutableListOf<Alert>()
        for (w in warnings) {
            val alert = (w as? JsonObject)?.get("alert") as? JsonObject ?: continue
            val infos = (alert["info"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: continue
            val info = infos.firstOrNull { it.str("language")?.startsWith(lang) == true }
                ?: infos.firstOrNull { it.str("language")?.startsWith("en") == true }
                ?: infos.firstOrNull() ?: continue
            val ends = info.str("expires")?.let(::epoch)
            if (ends != null && ends < now) continue
            val areas = (info["area"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
            val hit = areas.any { a ->
                val poly = (a["polygon"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    ?: listOfNotNull(a.str("polygon"))
                if (poly.isNotEmpty() && poly.any { capPolygonContains(it, p.lat, p.lon) }) return@any true
                val desc = norm(a.str("areaDesc") ?: return@any false)
                names.any { n -> desc == n || desc.split(' ').windowed(n.split(' ').size).any { it.joinToString(" ") == n } }
            }
            if (!hit) continue
            val event = info.str("event")?.replaceFirstChar { it.uppercase() } ?: "Weather Warning"
            out += Alert(
                event = info.str("headline")?.takeIf { it.length <= 48 } ?: event,
                headline = info.str("headline") ?: "",
                description = info.str("description") ?: "",
                instruction = info.str("instruction") ?: "",
                severity = info.str("severity") ?: "",
                sender = info.str("senderName") ?: "MeteoAlarm",
                endsEpoch = ends,
            )
        }
        return out.distinctBy { it.event }
    }

    // --- helpers -----------------------------------------------------------------

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

    private fun epoch(s: String): Long? = runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()

    private val noise = Regex(
        "\\b(landkreis|kreis|stadt|kreisfreie|city of|county|province of|provincia di|provincia de|provincia|province|" +
            "departement|department|region|regione|district|municipality|gemeinde|comune|bezirk|and|und|et|y)\\b"
    )

    fun norm(s: String): String {
        val ascii = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return ascii.replace(Regex("[^a-z0-9 ]"), " ").replace(noise, " ").replace(Regex("\\s+"), " ").trim()
    }

    /** CAP polygon: "lat,lon lat,lon …". */
    private fun capPolygonContains(poly: String, lat: Double, lon: Double): Boolean {
        val pts = poly.trim().split(Regex("\\s+")).mapNotNull { pair ->
            val (a, b) = pair.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
            val la = a.toDoubleOrNull() ?: return@mapNotNull null
            val lo = b.toDoubleOrNull() ?: return@mapNotNull null
            lo to la
        }
        return pts.size >= 3 && ringContains(pts, lon, lat)
    }

    private fun geoJsonContains(geom: JsonObject, lat: Double, lon: Double): Boolean {
        val type = geom.str("type")
        val coords = geom["coordinates"] as? JsonArray ?: return true
        fun ring(a: JsonArray) = a.mapNotNull { pt ->
            val xy = pt as? JsonArray ?: return@mapNotNull null
            val x = (xy.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val y = (xy.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            x to y
        }
        fun polygon(a: JsonArray): Boolean {
            val outer = (a.getOrNull(0) as? JsonArray)?.let(::ring) ?: return false
            if (!ringContains(outer, lon, lat)) return false
            return a.drop(1).none { h -> (h as? JsonArray)?.let { ringContains(ring(it), lon, lat) } == true }
        }
        return when (type) {
            "Polygon" -> polygon(coords)
            "MultiPolygon" -> coords.any { (it as? JsonArray)?.let(::polygon) == true }
            else -> true
        }
    }

    /** Ray casting; points are (x=lon, y=lat). */
    private fun ringContains(pts: List<Pair<Double, Double>>, x: Double, y: Double): Boolean {
        var inside = false
        var j = pts.size - 1
        for (i in pts.indices) {
            val (xi, yi) = pts[i]
            val (xj, yj) = pts[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    fun encode(list: List<Alert>): String = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Alert.serializer()), list)
    fun decode(raw: String): List<Alert> = runCatching {
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Alert.serializer()), raw)
    }.getOrDefault(emptyList())
}
