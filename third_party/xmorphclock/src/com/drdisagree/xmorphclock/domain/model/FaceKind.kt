package com.drdisagree.xmorphclock.domain.model

enum class FaceKind(val styleBase: Int, val mineBase: Int, val assetDir: String) {
    CLOCK(2000, 6000, "clock_faces"),
    WEATHER(3000, 7000, "weather_faces"),
    WIDGET(4000, 8000, "widget_faces");

    fun owns(style: Int): Boolean = style in styleBase until styleBase + RANGE || isMine(style)

    fun isMine(style: Int): Boolean = style in mineBase until mineBase + RANGE

    companion object {
        const val RANGE = 1000

        fun of(style: Int): FaceKind? = entries.firstOrNull { it.owns(style) }
    }
}
