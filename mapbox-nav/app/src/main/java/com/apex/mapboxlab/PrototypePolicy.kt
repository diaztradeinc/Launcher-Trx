package com.apex.mapboxlab

/** Pure rules shared by the UI and tests. Never promote replay fixes into live guidance. */
object PrototypePolicy {
    const val FRESH_FIX_MS = 8_000L
    fun publicTokenValid(value: String): Boolean =
        value.startsWith("pk.") && value.length > 30 && value.none { it.isWhitespace() }

    fun freshFix(nowMs: Long, fixMs: Long): Boolean =
        fixMs > 0 && nowMs - fixMs in 0..FRESH_FIX_MS

    fun formatDistance(meters: Double): String =
        if (meters < 160.9344) "${(meters * 3.28084 / 10).toInt().coerceAtLeast(1) * 10} ft"
        else String.format(java.util.Locale.US, "%.1f mi", meters / 1609.344)
}

/** Invalidates asynchronous route/search responses after end, replacement, or destruction. */
class RequestEpoch {
    private var epoch = 0
    @Synchronized fun next(): Int = ++epoch
    @Synchronized fun current(value: Int): Boolean = value == epoch
}
