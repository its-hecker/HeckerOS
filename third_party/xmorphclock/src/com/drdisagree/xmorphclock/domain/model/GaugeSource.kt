package com.drdisagree.xmorphclock.domain.model

enum class GaugeSource(val key: String) {
    BATTERY("battery"),
    STEPS("steps"),
    WEATHER("weather"),
    HUMIDITY("humidity"),
    UV("uv")
}
