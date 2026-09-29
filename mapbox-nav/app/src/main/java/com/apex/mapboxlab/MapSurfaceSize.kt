package com.apex.mapboxlab

import kotlin.math.ceil

/** Keep at least two logical pixels even during collapsed/inset transition frames. */
object MapSurfaceSize {
    fun safe(width: Int, height: Int, pixelRatio: Float): Pair<Int, Int> {
        require(pixelRatio.isFinite() && pixelRatio > 0f)
        val minimum = ceil(2.0 * pixelRatio).toInt().coerceAtLeast(2)
        return width.coerceAtLeast(minimum) to height.coerceAtLeast(minimum)
    }
}
