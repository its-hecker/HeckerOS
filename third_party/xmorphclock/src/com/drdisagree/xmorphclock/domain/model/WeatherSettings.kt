package com.drdisagree.xmorphclock.domain.model

data class WeatherSettings(
    val provider: WeatherProvider = WeatherProvider.OPEN_METEO,
    val apiKey: String = "",
    val metric: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val intervalHours: Int = 1,
    val locationName: String = ""
) {
    val hasCustomLocation: Boolean
        get() = latitude != null && longitude != null
}
