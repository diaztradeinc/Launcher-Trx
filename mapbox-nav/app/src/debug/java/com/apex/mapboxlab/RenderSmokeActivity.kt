package com.apex.mapboxlab

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.bindgen.Value
import com.mapbox.common.MapboxOptions
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.plugin.LocationPuck3D
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider

/** Offline native-renderer smoke check, reachable only by an explicit debug intent.
 * Synthetic single position and blank background; never used for navigation.
 */
class RenderSmokeActivity : AppCompatActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        MapboxOptions.accessToken = getString(R.string.mapbox_public_token).ifBlank {
            getSharedPreferences("mapbox-setup", MODE_PRIVATE).getString("public-token", "").orEmpty()
        }
        val context = UiScale.context(this)
        val map = MapView(context, MapInitOptions(context, mapOptions = MapOptions.Builder().pixelRatio(1f).build()))
        val root = FrameLayout(this)
        root.addView(map)
        val message = TextView(context).apply { text = "OFFLINE RENDER CHECK · NOT NAVIGATION"; setTextColor(Color.WHITE); setBackgroundColor(Color.BLACK) }
        root.addView(message, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        val point = Point.fromLngLat(-122.33517,47.6080)
        map.mapboxMap.setCamera(CameraOptions.Builder().center(point).zoom(19.0).pitch(55.0).build())
        val provider = NavigationLocationProvider()
        map.mapboxMap.loadStyle("""{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#303642"}}]}""") {
            map.location.setLocationProvider(provider)
            if (!intent.getBooleanExtra("noModel", false)) {
                map.location.locationPuck = LocationPuck3D(modelUri="asset://apex-truck.glb", modelScale=listOf(2.2f,2.2f,2.2f), modelRotation=listOf(0f,0f,180f))
            }
            map.location.enabled = true
            provider.changePosition(Location.Builder().longitude(point.longitude()).latitude(point.latitude()).timestamp(System.currentTimeMillis()).bearing(0.0).build())
            message.text = "OFFLINE STYLE READY · ${if(intent.getBooleanExtra("noModel",false)) "NO MODEL" else "INDEXED TRUCK"}"
        }
    }
}
