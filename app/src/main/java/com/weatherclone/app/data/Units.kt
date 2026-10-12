package com.weatherclone.app.data

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class WindUnit(val label: String) { Mph("mph"), Kmh("km/h"), Ms("m/s"), Knots("kn"), Beaufort("Beaufort") }
enum class PrecipUnit(val label: String) { Inches("Inches (in)"), Millimeters("Millimeters (mm)") }
enum class PressureUnit(val label: String) { InHg("Inches (inHg)"), Mb("Millibars (mbar)"), KPa("Kilopascals (kPa)"), MmHg("Millimeters (mmHg)"), HPa("Hectopascals (hPa)") }
enum class DistanceUnit(val label: String) { Miles("Miles (mi)"), Kilometers("Kilometers (km)") }

@Serializable
data class Settings(
    val fahrenheit: Boolean,
    val wind: WindUnit,
    val precip: PrecipUnit,
    val pressure: PressureUnit,
    val distance: DistanceUnit,
) {
    companion object {
        fun defaults(locale: Locale = Locale.getDefault()): Settings {
            val imperial = locale.country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")
            return Settings(
                fahrenheit = imperial,
                wind = if (imperial || locale.country == "GB") WindUnit.Mph else WindUnit.Kmh,
                precip = if (imperial) PrecipUnit.Inches else PrecipUnit.Millimeters,
                pressure = if (imperial) PressureUnit.InHg else PressureUnit.HPa,
                distance = if (imperial || locale.country == "GB") DistanceUnit.Miles else DistanceUnit.Kilometers,
            )
        }
    }
}

/** Converts metric values to display strings in the user's units. */
class Units(val s: Settings, val use24h: Boolean) {
    fun tempValue(c: Double): Double = if (s.fahrenheit) c * 9 / 5 + 32 else c
    fun tempInt(c: Double): Int = tempValue(c).roundToInt()
    fun temp(c: Double): String = "${tempInt(c)}°"
    /** Temperature difference (e.g. "+3°"), no offset applied for Fahrenheit. */
    fun tempDelta(dc: Double): Int = (if (s.fahrenheit) dc * 9 / 5 else dc).roundToInt()

    fun windValue(kmh: Double): Double = when (s.wind) {
        WindUnit.Mph -> kmh * 0.621371
        WindUnit.Kmh -> kmh
        WindUnit.Ms -> kmh / 3.6
        WindUnit.Knots -> kmh * 0.539957
        WindUnit.Beaufort -> beaufort(kmh).toDouble()
    }
    fun windNumber(kmh: Double): String = windValue(kmh).roundToInt().toString()
    val windLabel: String get() = if (s.wind == WindUnit.Beaufort) "Bft" else s.wind.label
    fun wind(kmh: Double): String = "${windNumber(kmh)} $windLabel"

    fun precipValue(mm: Double): Double = if (s.precip == PrecipUnit.Inches) mm / 25.4 else mm
    fun precip(mm: Double): String = if (s.precip == PrecipUnit.Inches) {
        val v = mm / 25.4
        when {
            v <= 0.004 -> "0\""
            v < 1 -> String.format(Locale.US, "%.2f\"", v)
            else -> String.format(Locale.US, "%.1f\"", v)
        }
    } else {
        when {
            mm < 0.5 -> "0 mm"
            mm < 10 -> String.format(Locale.US, "%.1f mm", mm).replace(".0 ", " ")
            else -> "${mm.roundToInt()} mm"
        }
    }
    /** Big number for the precipitation card ("0\"" or "3" + "mm"). */
    fun precipBig(mm: Double): Pair<String, String> = if (s.precip == PrecipUnit.Inches) precip(mm) to "" else {
        val v = if (mm < 10) String.format(Locale.US, "%.1f", mm).removeSuffix(".0") else mm.roundToInt().toString()
        (if (mm < 0.05) "0" else v) to "mm"
    }

    fun pressureValue(hpa: Double): Double = when (s.pressure) {
        PressureUnit.InHg -> hpa * 0.0295300
        PressureUnit.Mb, PressureUnit.HPa -> hpa
        PressureUnit.KPa -> hpa / 10
        PressureUnit.MmHg -> hpa * 0.750062
    }
    fun pressureNumber(hpa: Double): String = when (s.pressure) {
        PressureUnit.InHg -> String.format(Locale.US, "%.2f", pressureValue(hpa))
        PressureUnit.KPa -> String.format(Locale.US, "%.1f", pressureValue(hpa))
        else -> pressureValue(hpa).roundToInt().toString()
    }
    val pressureLabel: String get() = when (s.pressure) {
        PressureUnit.InHg -> "inHg"; PressureUnit.Mb -> "mbar"; PressureUnit.KPa -> "kPa"
        PressureUnit.MmHg -> "mmHg"; PressureUnit.HPa -> "hPa"
    }

    fun distanceValue(m: Double): Double = if (s.distance == DistanceUnit.Miles) m / 1609.344 else m / 1000
    fun distanceNumber(m: Double): String {
        val v = distanceValue(m)
        return if (v >= 10) v.roundToInt().toString() else String.format(Locale.US, "%.1f", v).removeSuffix(".0")
    }
    val distanceLabel: String get() = if (s.distance == DistanceUnit.Miles) "mi" else "km"

    private val hourFmt = DateTimeFormatter.ofPattern(if (use24h) "HH" else "ha", Locale.US)
    private val timeFmt = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mma", Locale.US)

    /** "1PM" / "13". */
    fun hour(t: LocalDateTime): String = hourFmt.format(t)
    /** "7:42PM" / "19:42". */
    fun time(t: LocalDateTime): String = timeFmt.format(t)

    companion object {
        fun beaufort(kmh: Double): Int {
            val limits = doubleArrayOf(1.0, 6.0, 12.0, 20.0, 29.0, 39.0, 50.0, 62.0, 75.0, 89.0, 103.0, 118.0)
            return limits.indexOfFirst { kmh < it }.let { if (it < 0) 12 else it }
        }

        fun compass(deg: Int): String {
            val names = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
            return names[(((deg % 360) + 360) % 360 / 22.5 + 0.5).toInt() % 16]
        }

        fun signed(v: Int): String = if (v > 0) "+$v" else if (v < 0) "−${abs(v)}" else "0"
    }
}
