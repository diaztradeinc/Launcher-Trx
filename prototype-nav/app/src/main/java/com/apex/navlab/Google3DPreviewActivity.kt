package com.apex.navlab

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.maps3d.GoogleMap3D
import com.google.android.gms.maps3d.Map3DView
import com.google.android.gms.maps3d.OnMap3DViewReadyCallback
import com.google.android.gms.maps3d.model.AltitudeMode
import com.google.android.gms.maps3d.model.Camera
import com.google.android.gms.maps3d.model.LatLngAltitude
import com.google.android.gms.maps3d.model.Model
import com.google.android.gms.maps3d.model.ModelOptions
import com.google.android.gms.maps3d.model.Orientation
import com.google.android.gms.maps3d.model.Vector3D

/** Geographic renderer evaluation; does not start, simulate or own navigation. */
class Google3DPreviewActivity : Activity(), OnMap3DViewReadyCallback {
    private var view: Map3DView? = null
    private var map: GoogleMap3D? = null
    private var truck: Model? = null
    private var anchor: LatLngAltitude? = null
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var sceneReady = false
    private var failed = false
    private var errorDetails = ""
    private var heading = 0.0
    private var overhead = false
    private var wide = false
    private var resumed = false
    private lateinit var controls: List<Button>
    private val initialLatitude by lazy { intent.getDoubleExtra("latitude",40.333).coerceIn(-85.0,85.0) }
    private val initialLongitude by lazy { intent.getDoubleExtra("longitude",-74.593).coerceIn(-180.0,180.0) }

    override fun attachBaseContext(base: Context) {
        val metrics=base.resources.displayMetrics
        val config=Configuration(base.resources.configuration)
        config.densityDpi=(160f*minOf(metrics.widthPixels/602f,metrics.heightPixels/726f)).toInt().coerceAtLeast(120)
        config.fontScale=1f
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor=Color.BLACK
        window.navigationBarColor=Color.BLACK
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(0xff080b0f.toInt()) }
        setContentView(root)
        root.setOnApplyWindowInsetsListener { v,i ->
            v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom);i
        }
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(text("TRX APEX · 3D PREVIEW",18),LinearLayout.LayoutParams(0,dp(52),1f))
        header.addView(button("Back to map") { finish() })
        root.addView(header)
        status=text("Loading Google 3D…",13).apply { tag="3d-status"
            setOnClickListener {
                if(errorDetails.isNotEmpty()) android.app.AlertDialog.Builder(this@Google3DPreviewActivity)
                    .setTitle("Google 3D details").setMessage(errorDetails).setPositiveButton("Close",null).show()
            }
        }
        root.addView(status)
        root.addView(text("Renderer preview only · tap a road to place the truck",12))
        val viewport=android.widget.FrameLayout(this).apply { tag="3d-viewport" }
        root.addView(viewport,LinearLayout.LayoutParams(-1,0,1f))
        val row=LinearLayout(this)
        val chase=button("Chase / Above") { overhead=!overhead;recenter() }
        val orbit=button("Rotate 45°") { heading=(heading+45.0)%360.0;updateTruck();recenter() }
        val zoom=button("Near / Wide") { wide=!wide;recenter() }
        val reset=button("Recenter") { recenter() }
        controls=listOf(chase,orbit,zoom,reset)
        controls.forEach { it.isEnabled=false;row.addView(it,LinearLayout.LayoutParams(0,dp(54),1f)) }
        root.addView(row)
        try {
            val config=Google3DConfig.create(initialLatitude,initialLongitude)
            val candidate=Map3DView(this,config)
            candidate.onCreate(state)
            view=candidate
            viewport.addView(candidate,android.widget.FrameLayout.LayoutParams(-1,-1))
            candidate.getMap3DViewAsync(this)
            handler.postDelayed({
                if(!sceneReady && !failed && !isDestroyed) {
                    status.text="3D is still loading. Check connection and Maps 3D SDK access in Google Cloud. Back to map remains available."
                }
            },30000)
        } catch(e:Exception) { recordError(e);showError(e.javaClass.simpleName) }
          catch(e:LinkageError) { recordError(e);showError(e.javaClass.simpleName,false) }
    }

    override fun onMap3DViewReady(googleMap3D: GoogleMap3D) {
        runOnUiThread {
            if(isDestroyed || isFinishing)return@runOnUiThread
            map=googleMap3D
            googleMap3D.setOnMapReadyListener {
                runOnUiThread {
                    if(!isDestroyed && !failed) {
                        sceneReady=true
                        if(anchor==null)status.text="Google 3D ready · tap a road to place the truck"
                    }
                }
            }
            googleMap3D.setMap3DClickListener { position,_ ->
                runOnUiThread {
                    if(!isDestroyed && !isFinishing && resumed && !failed) placeTruck(position)
                }
            }
            // Broad initial view; the close camera uses the actual clicked mesh altitude.
            googleMap3D.setCamera(Camera(LatLngAltitude(initialLatitude,initialLongitude,0.0),0.0,45.0,0.0,1800.0))
        }
    }

    private fun placeTruck(position: LatLngAltitude) {
        anchor=position
        try {
            if(truck==null) {
                truck=map?.addModel(ModelOptions().apply {
                    id="apex-preview-truck"
                    this.position=LatLngAltitude(position.latitude,position.longitude,0.0)
                    url="https://raw.githubusercontent.com/diaztradeinc/Launcher-Trx/prototype/v0.1-native-3d/prototype-nav/models/apex-truck-v02.glb"
                    altitudeMode=AltitudeMode.RELATIVE_TO_MESH
                    scale=Vector3D(1.0,1.0,1.0)
                    orientation=Orientation(heading,0.0,0.0)
                })
            }
            updateTruck()
            controls.forEach { it.isEnabled=true }
            status.text="Original truck preview · drag map or use camera controls"
            recenter()
        } catch(e:Exception) { showError("Truck placement unavailable") }
    }

    private fun updateTruck() {
        val point=anchor ?: return
        truck?.position=LatLngAltitude(point.latitude,point.longitude,0.0)
        truck?.orientation=Orientation(heading,0.0,0.0)
    }

    private fun recenter() {
        val point=anchor ?: return
        map?.setCamera(Camera(LatLngAltitude(point.latitude,point.longitude,point.altitude+1.5),
            heading,if(overhead)0.0 else 72.0,0.0,if(wide)85.0 else 22.0))
    }

    override fun onError(error: Exception) { recordError(error);runOnUiThread { showError(error.javaClass.simpleName) } }
    private fun recordError(error:Throwable) {
        errorDetails=generateSequence(error) { it.cause }.take(3)
            .joinToString("\n") { "${it.javaClass.simpleName}: ${it.message.orEmpty().take(600)}" }
            .replace(Regex("AIza[\\w-]+"),"[REDACTED_GOOGLE_KEY]")
        android.util.Log.e("Apex3D",error.stackTraceToString().replace(Regex("AIza[\\w-]+"),"[REDACTED_GOOGLE_KEY]"))
    }
    private fun showError(reason:String,cloudHint:Boolean=true) {
        if(isDestroyed || isFinishing)return
        failed=true
        status.text=if(cloudHint) "Google 3D unavailable ($reason). Check connection and Maps 3D SDK access, or return to the working map."
            else "Google 3D unavailable ($reason). This build has a renderer compatibility error. Return to the working map."
        status.append(" Tap this message for details.")
        controls.forEach { it.isEnabled=false }
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun text(value:String,size:Int)=TextView(this).apply {
        text=value;textSize=size.toFloat();setTextColor(Color.WHITE);setPadding(dp(10),dp(8),dp(10),dp(8))
    }
    private fun button(value:String,action:()->Unit)=Button(this).apply {
        text=value;textSize=12f;isAllCaps=false;minHeight=dp(48);setTextColor(Color.WHITE)
        backgroundTintList=android.content.res.ColorStateList.valueOf(0xff222831.toInt())
        setOnClickListener { action() }
    }
    override fun onResume() { super.onResume();resumed=true;view?.onResume() }
    override fun onPause() { resumed=false;view?.onPause();super.onPause() }
    override fun onSaveInstanceState(state:Bundle) { view?.onSaveInstanceState(state);super.onSaveInstanceState(state) }
    override fun onLowMemory() { view?.onLowMemory();super.onLowMemory() }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        truck?.remove();truck=null
        map?.setMap3DClickListener(null);map?.setOnMapReadyListener(null)
        view?.onDestroy();view=null;map=null
        super.onDestroy()
    }
}
