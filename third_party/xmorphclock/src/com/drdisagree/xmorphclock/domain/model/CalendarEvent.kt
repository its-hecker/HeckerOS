package com.drdisagree.xmorphclock.domain.model

data class CalendarEvent(
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String
)
