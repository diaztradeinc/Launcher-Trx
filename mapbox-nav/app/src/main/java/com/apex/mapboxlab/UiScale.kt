package com.apex.mapboxlab

import android.content.Context
import android.content.res.Configuration
import kotlin.math.min
import kotlin.math.roundToInt

/** Widget-only sizing context: accommodates Ottocast's unusually high logical density.
 * Does not rescale the map, change system density, or stretch bitmap artwork.
 */
object UiScale {
    fun context(base: Context): Context {
        val metrics = base.resources.displayMetrics
        val scale = min(metrics.widthPixels / 420f, metrics.heightPixels / 720f).coerceAtLeast(.5f)
        val config = Configuration(base.resources.configuration)
        config.densityDpi = (160 * scale).roundToInt()
        // Keep the Activity in the ContextWrapper chain for attribution dialogs and SDK plugins.
        return android.view.ContextThemeWrapper(base, R.style.AppTheme).apply {
            applyOverrideConfiguration(config)
        }
    }
}
