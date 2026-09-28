package com.apex.navlab

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.location.LocationCompat
import androidx.core.location.altitude.AltitudeConverterCompat
import com.google.android.gms.maps3d.*
import com.google.android.gms.maps3d.model.*
import com.google.android.libraries.navigation.*
import com.google.android.libraries.mapsplatform.turnbyturn.model.NavInfo
import com.google.android.libraries.mapsplatform.turnbyturn.model.NavState
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Shares Google's current Navigator. Back never stops guidance; End explicitly does. */
class Google3DLiveActivity : Activity(), OnMap3DViewReadyCallback {
    private var view: Map3DView? = null
    private var map: GoogleMap3D? = null
    private var navigator: Navigator? = null
    private var provider: RoadSnappedLocationProvider? = null
    private var truck: Model? = null
    private var routeLine: Polyline? = null
    private var routePoints = emptyList<LatLngAltitude>()
    private val handler = Handler(Looper.getMainLooper())
    private val altitudeWorker = Executors.newSingleThreadExecutor()
    private val converting = AtomicBoolean(false)
    private val motion = LiveMotion()
    private var resumed = false
    private var attached = false
    private var loadingNavigator = false
    private var ready = false
    private var rendererFailed = false
    private var following = true
    private var overhead = false
    private var lastInfo: NavInfo? = null
    private var infoTime = 0L
    private var lastHudTime = 0L
    private var rawAltitude: Location? = null
    private var mslFix: Location? = null
    private var displayedHeight: Double? = null
    private var lastDrawn: LiveMotion.Pose? = null
    private var routeRefreshAt = 0L
    private var feedRegistered = false
    private var problem: String? = null
    private var errorDetails = ""
    private lateinit var status: TextView
    private lateinit var maneuver: TextView
    private lateinit var trip: TextView
    private lateinit var turnImage: ImageView
    private lateinit var laneImage: ImageView
    private lateinit var cameraButton: TextView
    private lateinit var followButton: TextView

    private val routeChanged = Navigator.RouteChangedListener { handler.post { if(attached) refreshRoute() } }
    private val rerouting = Navigator.ReroutingListener { handler.post {
        if(attached) { lastInfo=null;clearRoute();maneuver.text="Rerouting…";clearImages();trip.text="Finding a new route…" }
    } }
    private val locationListener = object : RoadSnappedLocationProvider.LocationListener {
        override fun onLocationChanged(location: Location) { val copy=Location(location);handler.post { acceptFix(copy) } }
        override fun onRawLocationUpdate(location: Location) {
            val copy=Location(location)
            handler.post { if(attached && copy.hasAltitude()) rawAltitude=copy }
        }
    }

    override fun attachBaseContext(base: Context) {
        val m=base.resources.displayMetrics
        val config=Configuration(base.resources.configuration)
        config.densityDpi=(160f*minOf(m.widthPixels/602f,m.heightPixels/726f)).toInt().coerceAtLeast(120)
        config.fontScale=1f
        super.attachBaseContext(base.createConfigurationContext(config))
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK
        val root=LinearLayout(this).apply { orientation=1;setBackgroundColor(0xff080b0f.toInt()) }
        setContentView(root)
        root.setOnApplyWindowInsetsListener { v,i->v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom);i }
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(text("TRX APEX  ·  LIVE 3D",18).apply { setTextColor(0xffed2037.toInt()) },LinearLayout.LayoutParams(0,dp(50),1f))
        header.addView(button("Back to map") { finish() });root.addView(header)
        val card=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;background=panel() }
        turnImage=ImageView(this).apply { contentDescription="Next maneuver";scaleType=ImageView.ScaleType.FIT_CENTER;visibility=View.GONE }
        card.addView(turnImage,LinearLayout.LayoutParams(dp(64),dp(64)))
        maneuver=text("Connecting to active guidance…",20).apply { tag="live-maneuver" }
        card.addView(maneuver,LinearLayout.LayoutParams(0,-2,1f));root.addView(card)
        laneImage=ImageView(this).apply { contentDescription="Google recommended lanes";scaleType=ImageView.ScaleType.FIT_CENTER;visibility=View.GONE }
        root.addView(laneImage,LinearLayout.LayoutParams(-1,dp(38)))
        status=text("Loading Google 3D terrain…",12).apply { tag="live-status";setOnClickListener {
            if(errorDetails.isNotEmpty()) android.app.AlertDialog.Builder(this@Google3DLiveActivity).setTitle("Live 3D details").setMessage(errorDetails).setPositiveButton("Close",null).show()
        } }
        root.addView(status)
        val viewport=FrameLayout(this).apply { tag="live-viewport" }
        root.addView(viewport,LinearLayout.LayoutParams(-1,0,1f))
        trip=text("Waiting for Google ETA…",20).apply { tag="live-eta";background=panel() };root.addView(trip)
        val controls=LinearLayout(this)
        cameraButton=button("Above") { overhead=!overhead;cameraButton.text=if(overhead)"Chase" else "Above";following=true;lastDrawn=null }
        followButton=button("Explore") { following=!following;followButton.text=if(following)"Explore" else "Recenter";lastDrawn=null }
        listOf(cameraButton,followButton,button("End") {
            navigator?.stopGuidance();navigator?.clearDestinations();clearRoute();lastInfo=null;finish()
        }).forEach { controls.addView(it,LinearLayout.LayoutParams(0,dp(52),1f)) }
        root.addView(controls)
        try {
            val candidate=Map3DView(this,Google3DConfig.create(intent.getDoubleExtra("latitude",40.333),intent.getDoubleExtra("longitude",-74.593)))
            candidate.onCreate(state);view=candidate;viewport.addView(candidate,FrameLayout.LayoutParams(-1,-1));candidate.getMap3DViewAsync(this)
        } catch(e:Exception) { fail(e) } catch(e:LinkageError) { fail(e) }
        handler.postDelayed({ if(!ready && !rendererFailed && !isDestroyed) problem="3D terrain is still loading · Back to map remains available" },30000)
    }
    override fun onMap3DViewReady(googleMap3D: GoogleMap3D) { runOnUiThread {
        if(isDestroyed || isFinishing)return@runOnUiThread
        map=googleMap3D
        googleMap3D.setOnMapReadyListener { handler.post {
            if(!isDestroyed) { ready=true;problem=null;lastDrawn=null;drawRoute() }
        } }
    } }
    override fun onError(error: Exception) { runOnUiThread { fail(error) } }

    private fun connect() {
        if(!resumed || attached || loadingNavigator)return
        val n=navigator
        if(n!=null) { attachFeed(n);return }
        loadingNavigator=true
        NavigationApi.getNavigator(application,object:NavigationApi.NavigatorListener {
            override fun onNavigatorReady(value: Navigator) { runOnUiThread { loadingNavigator=false;navigator=value;if(resumed && !isDestroyed)attachFeed(value) } }
            override fun onError(code:Int) { runOnUiThread { loadingNavigator=false;problem="Navigation unavailable ($code) · return to map" } }
        })
    }
    private fun attachFeed(n: Navigator) {
        if(attached || !resumed)return
        if(!n.isGuidanceRunning) { problem="No active route · return to map and choose a destination";maneuver.text="Guidance is not running";return }
        try {
            attached=true;lastInfo=null;infoTime=0L;problem=null
            LiveNavFeed.receiver={ if(attached && resumed)acceptInfo(it) }
            feedRegistered=n.registerServiceForNavUpdates(packageName,LiveNavFeedService::class.java.name,
                NavigationUpdatesOptions.builder().setNumNextStepsToPreview(1)
                    .setGeneratedStepImagesType(NavigationUpdatesOptions.GeneratedStepImagesType.BITMAP)
                    .setDisplayMetrics(resources.displayMetrics).build())
            if(!feedRegistered)problem="Guidance feed unavailable · use Back to map"
            n.addRouteChangedListener(routeChanged);n.addReroutingListener(rerouting)
            provider=NavigationApi.getRoadSnappedLocationProvider(application)
            provider?.addLocationListener(locationListener)
            refreshRoute()
        } catch(e:Exception) { detachFeed();problem="Live feed unavailable · return to map";record(e) }
    }
    private fun detachFeed() {
        attached=false
        LiveNavFeed.receiver=null
        provider?.removeLocationListener(locationListener);provider=null
        navigator?.removeRouteChangedListener(routeChanged);navigator?.removeReroutingListener(rerouting)
        if(feedRegistered)navigator?.unregisterServiceForNavUpdates()
        feedRegistered=false;lastInfo=null;infoTime=0
    }
    private fun acceptInfo(info: NavInfo) {
        val previous=lastInfo?.navState
        lastInfo=info;infoTime=SystemClock.elapsedRealtime()
        when(info.navState) {
            NavState.ENROUTE -> if(info.routeChanged || previous!=NavState.ENROUTE || routePoints.isEmpty())refreshRoute()
            else -> clearRoute()
        }
        renderHud(infoTime)
    }
    private fun refreshRoute() {
        if(!attached || navigator?.isGuidanceRunning!=true)return
        try {
            routePoints=navigator?.routeSegments.orEmpty().flatMap { it.latLngs.orEmpty() }
                .map { LatLngAltitude(it.latitude,it.longitude,0.5) }
            routeRefreshAt=SystemClock.elapsedRealtime();drawRoute()
        } catch(e:Exception) { clearRoute();problem="Route overlay unavailable · use Back to map";record(e) }
    }
    private fun drawRoute() {
        if(!ready || rendererFailed)return
        try {
            routeLine?.remove();routeLine=null
            if(routePoints.size>=2)routeLine=map?.addPolyline(PolylineOptions().apply {
                id="apex-live-route";path=routePoints;altitudeMode=AltitudeMode.RELATIVE_TO_MESH
                strokeColor=0xff168dff.toInt();strokeWidth=9.0;outerColor=0xffb0e8ff.toInt();outerWidth=0.25
                drawsOccludedSegments=false
            })
        } catch(e:Exception) { problem="Route overlay unavailable · use Back to map";record(e) }
    }
    private fun clearRoute() { routePoints=emptyList();routeLine?.remove();routeLine=null }

    private fun acceptFix(location: Location) {
        if(!attached || !resumed)return
        val now=SystemClock.elapsedRealtime()
        if(!location.hasAccuracy() || location.accuracy>75f)return
        val old=motion.at(now)
        val bearing=if(location.hasBearing() && (!location.hasSpeed() || location.speed>0.8f)) location.bearing.toDouble() else old?.bearing ?: 0.0
        if(!motion.offer(LiveMotion.Pose(location.latitude,location.longitude,bearing),location.elapsedRealtimeNanos/1000000,now))return
        val elevation=Location(location)
        val raw=rawAltitude
        if(!elevation.hasAltitude() && raw!=null && kotlin.math.abs(raw.elapsedRealtimeNanos-location.elapsedRealtimeNanos)<3000000000L && raw.distanceTo(location)<50f) {
            elevation.altitude=raw.altitude
            if(raw.hasVerticalAccuracy())elevation.verticalAccuracyMeters=raw.verticalAccuracyMeters
        }
        if(elevation.hasAltitude() && converting.compareAndSet(false,true)) {
            altitudeWorker.execute {
                try {
                    AltitudeConverterCompat.addMslAltitudeToLocation(applicationContext,elevation)
                    handler.post { if(!isDestroyed && attached && LocationCompat.hasMslAltitude(elevation)) { mslFix=elevation;lastDrawn=null } }
                } catch(e:Exception) { /* Keep a broad view when elevation cannot be resolved. */ }
                finally { converting.set(false) }
            }
        }
    }
    private val frameTick=object:Runnable {
        override fun run() {
            if(!resumed || isDestroyed)return
            val now=SystemClock.elapsedRealtime()
            if(now-lastHudTime>=500) { renderHud(now);lastHudTime=now }
            if(attached && lastInfo?.navState==NavState.ENROUTE && now-infoTime<8000 && now-routeRefreshAt>10000)refreshRoute()
            if(ready && !rendererFailed && attached && navigator?.isGuidanceRunning==true && lastInfo?.navState!=NavState.STOPPED && motion.fresh(now)) {
                motion.at(now)?.let { pose->try { renderPose(pose,now) } catch(e:Exception) { fail(e) } }
            }
            handler.postDelayed(this,50) // Bound camera/model work to 20 Hz on Ottocast.
        }
    }
    private fun renderPose(pose: LiveMotion.Pose,now:Long) {
        if(pose==lastDrawn)return
        lastDrawn=pose
        if(truck==null)truck=map?.addModel(ModelOptions().apply {
            id="apex-live-truck";url="https://raw.githubusercontent.com/diaztradeinc/Launcher-Trx/prototype/v0.1-native-3d/prototype-nav/models/apex-truck-v02.glb"
            position=LatLngAltitude(pose.latitude,pose.longitude,0.0);altitudeMode=AltitudeMode.RELATIVE_TO_MESH
            scale=Vector3D(1.0,1.0,1.0);orientation=Orientation(pose.bearing,0.0,0.0)
        })
        truck?.position=LatLngAltitude(pose.latitude,pose.longitude,0.0)
        truck?.orientation=Orientation(pose.bearing,0.0,0.0)
        if(!following)return
        val elevation=mslFix
        val usable=elevation!=null && now-elevation.elapsedRealtimeNanos/1000000 in 0..8000 &&
            LiveMotion.distance(pose,LiveMotion.Pose(elevation.latitude,elevation.longitude,0.0))<100.0
        if(!usable) return // No invented sea-level target that might put the camera underground.
        val altitude=LocationCompat.getMslAltitudeMeters(elevation!!)
        if(!altitude.isFinite())return
        displayedHeight=displayedHeight?.let { it+(altitude-it)*0.2 } ?: altitude
        val accurate=LocationCompat.hasMslAltitudeAccuracy(elevation) && LocationCompat.getMslAltitudeAccuracyMeters(elevation)<=15f
        // MSL height is not the photogrammetry surface: leave generous clearance, especially when uncertain.
        map?.setCamera(Camera(LatLngAltitude(pose.latitude,pose.longitude,displayedHeight!!+5.0),pose.bearing,
            if(overhead)0.0 else if(accurate)60.0 else 40.0,0.0,if(overhead)250.0 else if(accurate)110.0 else 500.0))
    }
    private fun renderHud(now:Long) {
        if(rendererFailed)return
        val info=lastInfo
        val state=info?.navState
        val fresh=now-infoTime in 0..8000 && info!=null
        val gps=motion.fresh(now)
        val running=navigator?.isGuidanceRunning==true
        if(!running || !fresh || !gps || state!=NavState.ENROUTE) {
            maneuver.text=when {
                navigator!=null && !running || (state==NavState.STOPPED && fresh) -> "Guidance ended · return to map"
                state==NavState.REROUTING && fresh -> "Rerouting…"
                !gps -> "Waiting for a fresh GPS fix…"
                else -> "Waiting for live guidance…"
            }
            clearImages();trip.text=if(!running || state==NavState.STOPPED)"Choose a destination on the map" else "ETA updating…"
            if(!fresh || !running)clearRoute()
        } else {
            val step=info!!.currentStep
            maneuver.text="${distance(info.distanceToCurrentStepMeters)}\n${step?.fullInstructionText?.takeIf { it.isNotBlank() } ?: step?.fullRoadName ?: "Follow Google guidance"}"
            turnImage.setImageBitmap(step?.maneuverBitmap);turnImage.visibility=if(step?.maneuverBitmap!=null)View.VISIBLE else View.GONE
            laneImage.setImageBitmap(step?.lanesBitmap);laneImage.visibility=if(step?.lanesBitmap!=null)View.VISIBLE else View.GONE
            val seconds=info.timeToFinalDestinationSeconds
            trip.text=if(seconds!=null) "${(seconds+59)/60} min   ·   ${distance(info.distanceToFinalDestinationMeters)}   ·   ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis()+seconds.toLong()*1000))}" else "ETA updating…"
        }
        val elevation=mslFix
        val heightFresh=elevation!=null && now-elevation.elapsedRealtimeNanos/1000000 in 0..8000
        status.text=problem ?: when {
            !ready -> "Loading Google 3D terrain…"
            !gps -> "Position paused · waiting for GPS"
            !heightFresh -> "Live position · waiting for elevation to follow camera"
            !following -> "Exploring map · tap Recenter to follow"
            else -> "LIVE 3D · ${if(overhead)"overhead" else "chase"} · terrain alignment under test"
        }
    }
    private fun distance(meters:Int?):String = when {
        meters==null -> "—"
        meters<161 -> "${(meters*3.28084).toInt()} ft"
        else -> String.format(Locale.US,"%.1f mi",meters/1609.344)
    }
    private fun clearImages() { turnImage.setImageDrawable(null);laneImage.setImageDrawable(null);turnImage.visibility=View.GONE;laneImage.visibility=View.GONE }
    private fun record(error:Throwable) {
        errorDetails=generateSequence(error) { it.cause }.take(3).joinToString("\n") { "${it.javaClass.simpleName}: ${it.message.orEmpty().take(400)}" }.replace(Regex("AIza[\\w-]+"),"[REDACTED]")
        android.util.Log.w("ApexLiveNav",errorDetails)
    }
    private fun fail(error:Throwable) { if(isDestroyed)return;record(error);rendererFailed=true;clearImages();maneuver.text="Return to Google map";trip.text="Voice guidance remains active";status.text="3D unavailable · Back to map keeps guidance running. Tap for details." }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun panel()=GradientDrawable().apply { setColor(0xff11161c.toInt());setStroke(dp(1),0xff3a4048.toInt());cornerRadius=dp(10).toFloat() }
    private fun text(value:String,size:Int)=TextView(this).apply { text=value;textSize=size.toFloat();setTextColor(Color.WHITE);setPadding(dp(12),dp(8),dp(12),dp(8)) }
    private fun button(value:String,action:()->Unit)=text(value,14).apply { gravity=Gravity.CENTER;minimumHeight=dp(48);background=panel();setOnClickListener { action() } }
    override fun onResume() { super.onResume();resumed=true;view?.onResume();lastDrawn=null;connect();handler.post(frameTick) }
    override fun onPause() { resumed=false;handler.removeCallbacks(frameTick);detachFeed();view?.onPause();super.onPause() }
    override fun onSaveInstanceState(state:Bundle) { view?.onSaveInstanceState(state);super.onSaveInstanceState(state) }
    override fun onLowMemory() { view?.onLowMemory();super.onLowMemory() }
    override fun onDestroy() {
        detachFeed();handler.removeCallbacksAndMessages(null);altitudeWorker.shutdownNow()
        clearRoute();truck?.remove();truck=null;map?.setOnMapReadyListener(null);view?.onDestroy();view=null;map=null
        super.onDestroy()
    }
}
