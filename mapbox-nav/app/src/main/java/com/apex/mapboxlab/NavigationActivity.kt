package com.apex.mapboxlab

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.bindgen.Value
import com.mapbox.common.MapboxOptions
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.plugin.LocationPuck3D
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.*
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.core.trip.session.*
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.*
import com.mapbox.navigation.ui.maps.route.line.model.*
import com.mapbox.navigation.ui.maps.route.arrow.api.*
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
class NavigationActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private lateinit var frame: FrameLayout
    private lateinit var guidance: TextView
    private lateinit var trip: TextView
    private lateinit var status: TextView
    private lateinit var title: TextView
    private lateinit var camera: NavigationCamera
    private lateinit var viewport: MapboxNavigationViewportDataSource
    private lateinit var lineApi: MapboxRouteLineApi
    private lateinit var lineView: MapboxRouteLineView
    private lateinit var arrowView: MapboxRouteArrowView
    private val arrowApi = MapboxRouteArrowApi()
    private val locationProvider = NavigationLocationProvider()
    private var replayObserver: ReplayProgressObserver? = null
    private var attachedNav: MapboxNavigation? = null
    private var active = false
    private var styleReady = false
    private var observingNavigation = false
    private var night = true
    private var buildings = true
    private var voice = true
    private var speechReady = false
    private var rawFixTime = 0L
    private var currentLocation: Location? = null
    private var simulation = true
    private var hasCentered = false
    private var permissionRequested = false
    private var pendingDestination: Point? = null
    private val requests = RequestEpoch()
    private val renderRequests = RequestEpoch()
    private val searchRequests = RequestEpoch()
    private var searchClient: PlaceSearch? = null
    private var speech: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    private val origin = Point.fromLngLat(-122.33517, 47.6080)
    private val demoDestination = Point.fromLngLat(-122.3385, 47.6205)
    private val positioned = mutableListOf<Pair<View, FloatArray>>()
    private val textSizes = mutableMapOf<TextView, Float>()
    private val fresh: Boolean get() = simulation || PrototypePolicy.freshFix(System.currentTimeMillis(), rawFixTime)
    private var layoutWidth = 420f
    private var layoutHeight = 720f
    private val staleCheck = object : Runnable {
        override fun run() {
            if (!simulation && !fresh) {
                status.text = "Waiting for fresh GPS · guidance paused"
                guidance.text = "Location unavailable\nKeep the map open to reconnect"
                speech?.stop()
            }
            handler.postDelayed(this, 1_000)
        }
    }

    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: Location) {
            rawFixTime = rawLocation.timestamp
        }
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            if (!fresh) return
            val location = locationMatcherResult.enhancedLocation
            currentLocation = location
            locationProvider.changePosition(location, locationMatcherResult.keyPoints)
            viewport.onLocationChanged(location)
            evaluateViewport()
            if (!hasCentered && styleReady) {
                hasCentered = true
                camera.requestNavigationCameraToFollowing()
            }
            status.text = if (simulation) "SIMULATION · NOT LIVE GPS" else "LIVE GPS · ${if (active) "Guidance active" else "Choose a destination"}"
            if (!active) guidance.text = "Where to?\nFind an address or hold a point on the map"
        }
    }

    private val progressObserver = RouteProgressObserver { progress ->
        if (active && fresh) {
            viewport.onRouteProgressChanged(progress)
            evaluateViewport()
            val leg = progress.currentLegProgress
            val next = leg?.upcomingStep?.maneuver()
            val meters = leg?.currentStepProgress?.distanceRemaining?.toDouble() ?: 0.0
            guidance.text = "${PrototypePolicy.formatDistance(meters)}  ${turnSymbol(next?.modifier())}\n${next?.instruction() ?: "Continue along the route"}"
            val eta = SimpleDateFormat("h:mm a", Locale.US).format(Date(System.currentTimeMillis() + (progress.durationRemaining * 1000).toLong()))
            trip.text = "$eta arrival   ·   ${kotlin.math.ceil(progress.durationRemaining / 60).toInt()} min   ·   ${PrototypePolicy.formatDistance(progress.distanceRemaining.toDouble())}"
            map.mapboxMap.style?.let { arrowView.renderManeuverUpdate(it, arrowApi.addUpcomingManeuverArrow(progress)) }
            if (progress.currentState == com.mapbox.navigation.base.trip.model.RouteProgressState.COMPLETE) {
                endRoute()
                guidance.text = "You have arrived\nChoose another destination when ready"
            }
        }
    }

    private val voiceObserver = VoiceInstructionsObserver { instruction ->
        if (active && fresh && voice && speechReady) {
            speech?.speak(instruction.announcement(), TextToSpeech.QUEUE_FLUSH, null, "maneuver")
        }
    }

    private val routesObserver = RoutesObserver { update ->
        val renderEpoch = renderRequests.next()
        if (update.navigationRoutes.isNotEmpty()) {
            lineApi.setNavigationRoutes(update.navigationRoutes) { draw ->
                if (!isDestroyed && active && renderRequests.current(renderEpoch)) map.mapboxMap.style?.let { lineView.renderRouteDrawData(it, draw) }
            }
            viewport.onRouteChanged(update.navigationRoutes.first())
            evaluateViewport()
        } else {
            map.mapboxMap.style?.let { style ->
                lineApi.clearRouteLine { if (!isDestroyed && renderRequests.current(renderEpoch)) lineView.renderClearRouteLineValue(style, it) }
                arrowView.render(style, arrowApi.clearArrows())
            }
            viewport.clearRouteData()
            evaluateViewport()
        }
    }

    private val navigation: MapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                CrashReport.stage("Attaching navigation observers")
                attachedNav = mapboxNavigation
                if (styleReady) attachNavigationObservers(mapboxNavigation)
            }
            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                requests.next()
                observingNavigation = false
                mapboxNavigation.unregisterRoutesObserver(routesObserver)
                mapboxNavigation.unregisterLocationObserver(locationObserver)
                mapboxNavigation.unregisterRouteProgressObserver(progressObserver)
                mapboxNavigation.unregisterVoiceInstructionsObserver(voiceObserver)
                replayObserver?.let { mapboxNavigation.unregisterRouteProgressObserver(it) }
                replayObserver = null
                mapboxNavigation.mapboxReplayer.stop()
                mapboxNavigation.stopTripSession()
                attachedNav = null
            }
        },
        onInitialize = {
            CrashReport.stage("Creating navigation engine")
            if (!MapboxNavigationApp.isSetup()) MapboxNavigationApp.setup(NavigationOptions.Builder(applicationContext).build())
        }
    )

    private fun attachNavigationObservers(mapboxNavigation: MapboxNavigation) {
        if (observingNavigation || !styleReady || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
        observingNavigation = true
                mapboxNavigation.registerRoutesObserver(routesObserver)
                mapboxNavigation.registerLocationObserver(locationObserver)
                mapboxNavigation.registerRouteProgressObserver(progressObserver)
                mapboxNavigation.registerVoiceInstructionsObserver(voiceObserver)
                if (simulation) {
                    replayObserver = ReplayProgressObserver(mapboxNavigation.mapboxReplayer).also {
                        mapboxNavigation.registerRouteProgressObserver(it)
                    }
                }
                startSession()
    }

    private fun evaluateViewport() {
        // Native camera calculations need the style and a measured map surface.
        if (styleReady && map.width > 1 && map.height > 1) viewport.evaluate()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val token = getSharedPreferences("mapbox-setup", MODE_PRIVATE).getString("public-token", "").orEmpty()
        if (!PrototypePolicy.publicTokenValid(token)) { finish(); return }
        MapboxOptions.accessToken = token
        simulation = intent.getBooleanExtra("simulation", true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        searchClient = PlaceSearch(token)
        CrashReport.stage("Creating map and HUD")
        buildUi()
        CrashReport.stage("Creating camera and route layers")
        viewport = MapboxNavigationViewportDataSource(map.mapboxMap)
        viewport.options.followingFrameOptions.defaultPitch = 55.0
        viewport.options.followingFrameOptions.minZoom = 16.0
        viewport.options.followingFrameOptions.maxZoom = 18.0
        camera = NavigationCamera(map.mapboxMap, map.camera, viewport)
        map.camera.addCameraAnimationsLifecycleListener(NavigationBasicGesturesHandler(camera))
        lineApi = MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build())
        lineView = MapboxRouteLineView(MapboxRouteLineViewOptions.Builder(UiScale.context(this))
            .routeLineColorResources(RouteLineColorResources.Builder()
                .routeDefaultColor(Color.rgb(41, 211, 255))
                .routeCasingColor(Color.rgb(9, 71, 117)).build())
            .build())
        arrowView = MapboxRouteArrowView(RouteArrowOptions.Builder(UiScale.context(this)).build())
        map.location.apply {
            setLocationProvider(locationProvider)
            puckBearingEnabled = true
            enabled = true
        }
        map.mapboxMap.setCamera(CameraOptions.Builder().center(origin).zoom(16.5).pitch(55.0).build())
        map.mapboxMap.subscribeMapLoadingError {
            runOnUiThread { if (!isDestroyed) status.text = "Map load error · check token, connection and account" }
        }
        CrashReport.stage("Loading Mapbox Standard")
        map.mapboxMap.loadStyle(Style.STANDARD) { style ->
            CrashReport.stage("Standard loaded; configuring style")
            applyStyle()
            CrashReport.stage("Initializing route layers")
            lineView.initializeLayers(style)
            if (getSharedPreferences("mapbox-setup", MODE_PRIVATE).getBoolean("truck-model", false)) {
            CrashReport.stage("Loading truck model")
            map.location.locationPuck = LocationPuck3D(
                modelUri = "asset://apex-truck.glb",
                modelScale = listOf(18f, 18f, 18f),
                modelEmissiveStrength = 0f,
                modelRotation = listOf(0f, 0f, 180f)
            )
            }

            styleReady = true
            evaluateViewport()
            attachedNav?.let { attachNavigationObservers(it) }
            status.text = if (simulation) "SIMULATION · Tap Demo to start" else "Map ready · waiting for GPS"
        }
        map.gestures.addOnMapLongClickListener { point ->
            confirmDestination(point, "Selected map point")
            true
        }
        speech = TextToSpeech(this) { result ->
            speechReady = result == TextToSpeech.SUCCESS
            if (speechReady) {
                val available = speech?.setLanguage(Locale.US)
                speechReady = available != TextToSpeech.LANG_MISSING_DATA && available != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        // requireMapboxNavigation attaches at CREATED, after view initialisation.
    }

    @SuppressLint("MissingPermission")
    private fun startSession() {
        val nav = attachedNav ?: return
        if (!styleReady || !observingNavigation) return
        CrashReport.stage(if (simulation) "Starting replay session" else "Starting live session")
        if (simulation) {
            nav.startReplayTripSession(withForegroundService = false)
            if (!active) {
                nav.mapboxReplayer.clearEvents()
                nav.mapboxReplayer.pushEvents(listOf(ReplayRouteMapper.mapToUpdateLocation(Date().time.toDouble(), origin)))
                nav.mapboxReplayer.playFirstLocation()
            }
            nav.mapboxReplayer.play()
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            rawFixTime = 0
            nav.startTripSession(withForegroundService = false)
        } else {
            status.text = "Precise location permission is required for live guidance"
            if (!permissionRequested) {
                permissionRequested = true
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 71)
            }
        }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 71 && styleReady) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startSession()
            else AlertDialog.Builder(UiScale.context(this)).setTitle("Precise location is off")
                .setMessage("Live navigation needs precise location. You can return to setup and use simulation without GPS.")
                .setPositiveButton("Setup") { _, _ -> finish() }.setNegativeButton("Stay", null).show()
        }
    }

    private fun confirmDestination(point: Point, label: String) {
        if (attachedNav == null || !styleReady) { toast("Wait for the map to finish loading"); return }
        AlertDialog.Builder(UiScale.context(this)).setTitle(label)
            .setMessage(if (simulation) "Simulate a route from the Seattle demo origin?" else "Navigate from your current GPS position?")
            .setPositiveButton(if (simulation) "Simulate" else "Navigate") { _, _ -> requestRoute(point) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun requestRoute(destination: Point) {
        val nav = attachedNav ?: return
        if (!styleReady) { toast("Map is still loading"); return }
        if (!simulation && (!fresh || currentLocation == null)) { toast("Waiting for a fresh GPS position"); return }
        val start = if (simulation) origin else currentLocation!!.let { Point.fromLngLat(it.longitude, it.latitude) }
        endRoute()
        val epoch = requests.next()
        pendingDestination = destination
        status.text = "Finding route…"
        nav.requestRoutes(RouteOptions.builder().applyDefaultNavigationOptions()
            .applyLanguageAndVoiceUnitOptions(this).coordinatesList(listOf(start, destination)).build(),
            object : NavigationRouterCallback {
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                    if (requests.current(epoch)) status.text = "Route request canceled · choose a destination"
                }
                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    if (requests.current(epoch)) status.text = "Route unavailable · check connection, token and destination"
                }
                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                    if (!requests.current(epoch) || isDestroyed || attachedNav == null) return
                    if (routes.isEmpty()) { status.text = "No drivable route found"; return }
                    if (!simulation && !fresh) { status.text = "GPS became stale · choose destination again after reconnecting"; return }
                    active = true
                    nav.setNavigationRoutes(routes)
                    guidance.text = "Starting guidance…"
                    if (simulation) {
                        val events = ReplayRouteMapper().mapDirectionsRouteGeometry(routes.first().directionsRoute)
                        if (events.isEmpty()) { endRoute(); status.text = "Route has no replay geometry"; return }
                        nav.mapboxReplayer.stop()
                        nav.mapboxReplayer.clearEvents()
                        nav.mapboxReplayer.pushEvents(events)
                        nav.mapboxReplayer.seekTo(events.first())
                        nav.mapboxReplayer.play()
                    }
                    camera.requestNavigationCameraToFollowing()
                }
            })
    }

    private fun endRoute() {
        requests.next()
        renderRequests.next()
        searchRequests.next()
        searchClient?.cancel()
        pendingDestination = null
        active = false
        attachedNav?.setNavigationRoutes(emptyList())
        if (simulation) { attachedNav?.mapboxReplayer?.stop(); attachedNav?.mapboxReplayer?.clearEvents() }
        speech?.stop()
        guidance.text = "Where to?\nFind an address or hold a point on the map"
        trip.text = "No active route"
        status.text = if (simulation) "SIMULATION · Tap Demo to start" else "LIVE GPS · Choose a destination"
    }

    private fun applyStyle() {
        map.mapboxMap.setStyleImportConfigProperty("basemap", "lightPreset", Value(if (night) "night" else "day"))
        map.mapboxMap.setStyleImportConfigProperty("basemap", "show3dObjects", Value(buildings))
    }

    private fun search() {
        val input = EditText(UiScale.context(this)).apply { hint = "Address or city"; setSingleLine(true) }
        AlertDialog.Builder(UiScale.context(this)).setTitle("Find an address").setView(input)
            .setPositiveButton("Find") { _, _ ->
                val query = input.text.toString().trim()
                if (query.length < 3) { toast("Enter at least three characters"); return@setPositiveButton }
                status.text = "Searching addresses…"
                val searchEpoch = searchRequests.next()
                searchClient?.search(query) { result ->
                    runOnUiThread {
                        if (!searchRequests.current(searchEpoch) || isDestroyed || isFinishing || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@runOnUiThread
                        result.fold(onSuccess = { places ->
                            status.text = if (simulation) "SIMULATION · Choose a result" else "LIVE GPS · Choose a result"
                            if (places.isEmpty()) toast("No addresses found")
                            else AlertDialog.Builder(UiScale.context(this)).setTitle("Choose destination")
                                .setItems(places.map { it.label }.toTypedArray()) { _, index -> confirmDestination(places[index].point, places[index].label) }
                                .setNegativeButton("Cancel", null).show()
                        }, onFailure = { status.text = "Address search unavailable · check connection and token" })
                    }
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun settings() {
        val metrics = resources.displayMetrics
        AlertDialog.Builder(UiScale.context(this)).setTitle("Mapbox Lab 0.1.1")
            .setItems(arrayOf(if (night) "Switch to day" else "Switch to night", if (buildings) "Hide 3D scenery" else "Show 3D scenery", "Display and prototype details", "Return to token / mode setup")) { _, index ->
                when (index) {
                    0 -> { night = !night; applyStyle() }
                    1 -> { buildings = !buildings; applyStyle() }
                    2 -> AlertDialog.Builder(UiScale.context(this)).setTitle("Device details")
                        .setMessage("Viewport: ${map.width} × ${map.height} px\nAndroid density: ${metrics.densityDpi} dpi\nMapbox Standard · Navigation SDK 3.31.1\n\nNative map and pixel-scaled controls. Original low-poly pickup proxy. Buildings depend on provider coverage. No 3D Lanes private-preview data. Guidance pauses in the background. Device render performance remains unverified.")
                        .setPositiveButton("OK", null).show()
                    3 -> finish()
                }
            }.setNegativeButton("Close", null).show()
    }

    private fun buildUi() {
        frame = FrameLayout(this).apply { setBackgroundColor(Color.rgb(12, 17, 23)) }
        setContentView(frame)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        val metrics = resources.displayMetrics
        val mapPixelRatio = min(metrics.widthPixels / 420f, metrics.heightPixels / 720f).coerceAtLeast(.5f)
        val mapContext = UiScale.context(this)
        map = MapView(mapContext, MapInitOptions(mapContext, mapOptions = MapOptions.Builder().pixelRatio(mapPixelRatio).build()))
        map.setMaximumFps(30)
        // Leave the attribution area inside the map unobscured above the bottom UI.
        place(map, 0f, 45f, 420f, 545f)
        title = label("TRX APEX  ·  MAPBOX", 0f, 0f, 420f, 43f, 19f)
        guidance = label("Loading 3D map…", 12f, 57f, 328f, 87f, 21f)
        status = label(if (simulation) "SIMULATION · NOT LIVE GPS" else "LIVE GPS · Connecting", 12f, 151f, 328f, 29f, 11f)
        button("◎", "Recenter", 352f, 59f, 56f, 52f) { if (styleReady) camera.requestNavigationCameraToFollowing() }
        button("☼", "Day or night map", 352f, 121f, 56f, 52f) { night = !night; applyStyle() }
        var voiceButton: TextView? = null
        voiceButton = button("Voice", "Toggle voice guidance", 352f, 183f, 56f, 52f) {
            voice = !voice; voiceButton?.text = if (voice) "Voice" else "Muted"
            if (!voice) speech?.stop()
            if (voice && !speechReady) toast("English speech engine is not ready on this device")
        }
        trip = label("No active route", 12f, 602f, 308f, 47f, 14f)
        button("End", "End navigation", 330f, 602f, 78f, 47f, true) { endRoute() }
        button("Find", "Find address", 12f, 659f, 92f, 49f) { search() }
        button(if (simulation) "Demo" else "GPS", "Start demo or recenter GPS", 113f, 659f, 92f, 49f) {
            if (simulation) requestRoute(demoDestination) else if (fresh && styleReady) camera.requestNavigationCameraToFollowing() else { permissionRequested = false; startSession() }
        }
        button("Overview", "Route overview", 214f, 659f, 92f, 49f) { if (styleReady) camera.requestNavigationCameraToOverview() }
        button("Setup", "Map settings", 315f, 659f, 93f, 49f) { settings() }
        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layoutUi() }
    }

    private fun place(view: View, x: Float, y: Float, width: Float, height: Float) {
        positioned.add(view to floatArrayOf(x, y, width, height))
        frame.addView(view, FrameLayout.LayoutParams(1, 1))
    }
    private fun label(text: String, x: Float, y: Float, w: Float, h: Float, size: Float): TextView {
        val view = TextView(this).apply {
            this.text = text; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = GradientDrawable().apply { setColor(Color.argb(244, 16, 22, 29)); cornerRadius = 14f }
            maxLines = 3
        }
        textSizes[view] = size
        place(view, x, y, w, h)
        return view
    }
    private fun button(text: String, description: String, x: Float, y: Float, w: Float, h: Float, red: Boolean = false, click: () -> Unit): TextView =
        label(text, x, y, w, h, if (text.length < 3) 24f else 14f).apply {
            gravity = Gravity.CENTER; isClickable = true; isFocusable = true; contentDescription = description
            if (red) background = GradientDrawable().apply { setColor(Color.rgb(195, 28, 55)); cornerRadius = 14f }
            setOnClickListener { click() }
        }
    private fun layoutUi() {
        val w = (frame.width - frame.paddingLeft - frame.paddingRight).toFloat()
        val h = (frame.height - frame.paddingTop - frame.paddingBottom).toFloat()
        if (w <= 0 || h <= 0) return
        val sx = w / layoutWidth
        val sy = h / layoutHeight
        val textScale = min(sx, sy)
        positioned.forEach { (view, p) ->
            val lp = view.layoutParams as FrameLayout.LayoutParams
            val vw = (p[2] * sx).toInt(); val vh = (p[3] * sy).toInt()
            val vx = (p[0] * sx).toInt(); val vy = (p[1] * sy).toInt()
            if (lp.width != vw || lp.height != vh || lp.leftMargin != vx || lp.topMargin != vy) {
                lp.width = vw; lp.height = vh; lp.leftMargin = vx; lp.topMargin = vy; view.layoutParams = lp
            }
            if (view is TextView) {
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizes.getValue(view) * textScale)
                view.setPadding((10 * textScale).toInt(), 0, (10 * textScale).toInt(), 0)
            }
        }
        if (::viewport.isInitialized) {
            viewport.followingPadding = EdgeInsets(map.height * .35, map.width * .05, map.height * .08, map.width * .18)
            viewport.overviewPadding = EdgeInsets(map.height * .28, map.width * .08, map.height * .10, map.width * .18)
            evaluateViewport()
        }
    }
    private fun turnSymbol(modifier: String?): String = when (modifier) {
        "right", "sharp right", "slight right" -> "↱"
        "left", "sharp left", "slight left" -> "↰"
        "uturn" -> "↶"
        else -> "↑"
    }
    private fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
    override fun onResume() { super.onResume(); if (::map.isInitialized) handler.post(staleCheck) }
    override fun onPause() { handler.removeCallbacks(staleCheck); speech?.stop(); searchRequests.next(); searchClient?.cancel(); super.onPause() }
    override fun onDestroy() {
        requests.next()
        renderRequests.next()
        searchClient?.close()
        speech?.shutdown()
        if (::lineApi.isInitialized) lineApi.cancel()
        if (::lineView.isInitialized) lineView.cancel()
        super.onDestroy()
    }
}
