package com.apex.mapboxlab

import android.app.Application
import com.mapbox.common.MapboxOptions

class LabApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReport.install(this)
        val token = getSharedPreferences("mapbox-setup", MODE_PRIVATE)
            .getString("public-token", getString(R.string.mapbox_public_token)).orEmpty()
        if (PrototypePolicy.publicTokenValid(token)) MapboxOptions.accessToken = token
    }
}
