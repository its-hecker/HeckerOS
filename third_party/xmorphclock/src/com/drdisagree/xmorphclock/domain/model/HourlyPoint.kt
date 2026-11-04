package com.drdisagree.xmorphclock.domain.model

data class HourlyPoint(
    val time: Long,
    val temperature: Int,
    val conditionCode: Int,
    val isDay: Boolean
)
