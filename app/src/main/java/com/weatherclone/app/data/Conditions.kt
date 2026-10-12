package com.weatherclone.app.data

enum class WxIcon {
    Sun, Moon, CloudSun, CloudMoon, Cloud, Fog, Haze, Drizzle, Rain, HeavyRain,
    Snow, Sleet, Hail, Thunder, Wind, Sunrise, Sunset
}

/** Which animated sky to draw behind a location. */
enum class Sky { Clear, PartlyCloudy, Cloudy, Fog, Rain, Snow, Storm }

data class Condition(val text: String, val icon: WxIcon, val sky: Sky)

/** Maps WMO weather codes (as returned by Open-Meteo) to Apple-style condition names. */
object Conditions {
    fun of(code: Int, cloud: Int, isDay: Boolean, windKmh: Double, gustKmh: Double): Condition {
        val windy = gustKmh >= 55 || windKmh >= 40
        val breezy = !windy && (gustKmh >= 40 || windKmh >= 28)
        val clearIcon = if (isDay) WxIcon.Sun else WxIcon.Moon
        val partIcon = if (isDay) WxIcon.CloudSun else WxIcon.CloudMoon
        return when (code) {
            0 -> when {
                windy -> Condition("Windy", WxIcon.Wind, Sky.Clear)
                breezy -> Condition("Breezy", WxIcon.Wind, Sky.Clear)
                else -> Condition(if (isDay) "Sunny" else "Clear", clearIcon, Sky.Clear)
            }
            1 -> when {
                windy -> Condition("Windy", WxIcon.Wind, Sky.Clear)
                else -> Condition(if (isDay) "Mostly Sunny" else "Mostly Clear", partIcon, Sky.Clear)
            }
            2 -> when {
                windy -> Condition("Windy", WxIcon.Wind, Sky.PartlyCloudy)
                cloud >= 70 -> Condition("Mostly Cloudy", partIcon, Sky.Cloudy)
                else -> Condition("Partly Cloudy", partIcon, Sky.PartlyCloudy)
            }
            3 -> when {
                windy -> Condition("Windy", WxIcon.Wind, Sky.Cloudy)
                cloud in 1..87 -> Condition("Mostly Cloudy", WxIcon.Cloud, Sky.Cloudy)
                else -> Condition("Cloudy", WxIcon.Cloud, Sky.Cloudy)
            }
            45, 48 -> Condition("Foggy", WxIcon.Fog, Sky.Fog)
            51, 53 -> Condition("Drizzle", WxIcon.Drizzle, Sky.Rain)
            55 -> Condition("Drizzle", WxIcon.Drizzle, Sky.Rain)
            56, 57 -> Condition("Freezing Drizzle", WxIcon.Sleet, Sky.Rain)
            61 -> Condition("Light Rain", WxIcon.Drizzle, Sky.Rain)
            63 -> Condition("Rain", WxIcon.Rain, Sky.Rain)
            65 -> Condition("Heavy Rain", WxIcon.HeavyRain, Sky.Rain)
            66, 67 -> Condition("Freezing Rain", WxIcon.Sleet, Sky.Rain)
            71 -> Condition("Flurries", WxIcon.Snow, Sky.Snow)
            73 -> Condition("Snow", WxIcon.Snow, Sky.Snow)
            75 -> Condition("Heavy Snow", WxIcon.Snow, Sky.Snow)
            77 -> Condition("Flurries", WxIcon.Snow, Sky.Snow)
            80 -> Condition("Showers", WxIcon.Rain, Sky.Rain)
            81 -> Condition("Rain", WxIcon.Rain, Sky.Rain)
            82 -> Condition("Heavy Rain", WxIcon.HeavyRain, Sky.Rain)
            85 -> Condition("Snow Showers", WxIcon.Snow, Sky.Snow)
            86 -> Condition("Heavy Snow", WxIcon.Snow, Sky.Snow)
            95 -> Condition("Thunderstorms", WxIcon.Thunder, Sky.Storm)
            96 -> Condition("Strong Storms", WxIcon.Thunder, Sky.Storm)
            99 -> Condition("Hail", WxIcon.Hail, Sky.Storm)
            else -> Condition(if (isDay) "Sunny" else "Clear", clearIcon, Sky.Clear)
        }
    }

    fun isPrecip(code: Int) = code >= 51
    fun isSnow(code: Int) = code in 71..77 || code in 85..86
}
