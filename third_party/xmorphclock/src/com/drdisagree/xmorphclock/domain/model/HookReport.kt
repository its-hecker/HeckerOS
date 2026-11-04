package com.drdisagree.xmorphclock.domain.model

data class HookReport(
    val timestamp: Long,
    val framework: String,
    val systemUiVersion: String,
    val buildId: String,
    val adapter: String?,
    val signals: List<HookSignal>,
    val clockFace: String?,
    val errors: List<String>,
    val largeFrame: ClockFrame? = null,
    val smallFrame: ClockFrame? = null,
    val screenAspect: Float? = null,
    val moduleUpdatedAt: Long = 0L,
    val moduleLoadedAt: Long = 0L,
    val uptimeMs: Long = 0L,
    val probes: List<String> = emptyList(),
    val counters: Map<String, Int> = emptyMap(),
    val state: Map<String, String> = emptyMap(),
    val logs: List<String> = emptyList()
) {
    val isHealthy: Boolean
        get() = adapter != null && signals.all { it.source != null } && errors.isEmpty()
}
