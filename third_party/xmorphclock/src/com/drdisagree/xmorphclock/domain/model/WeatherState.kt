package com.drdisagree.xmorphclock.domain.model

data class WeatherState(
    val settings: WeatherSettings = WeatherSettings(),
    val snapshot: WeatherSnapshot? = null,
    val status: WeatherStatus = WeatherStatus.IDLE
)
