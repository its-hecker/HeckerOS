package com.drdisagree.xmorphclock.engine.render

internal class IdleFade(target: Float) {

    private var from = target
    private var start = 0L

    var target = target
        private set

    fun value(now: Long): Float {
        val t = ((now - start).toFloat() / DURATION_MS).coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t)
        return from + (target - from) * eased
    }

    fun moveTo(next: Float, now: Long) {
        if (next == target) return
        from = value(now)
        target = next
        start = now
    }

    private companion object {
        const val DURATION_MS = 320f
    }
}
