package com.apex.navlab

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.GoogleMap
import com.google.android.libraries.navigation.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

/** Google owns the entire map viewport, maneuver header, attribution and ETA. */
class GoogleNavigationActivity : AppCompatActivity() {
    private var navView: NavigationView? = null
    private var navigator: Navigator? = null
    private var map: GoogleMap? = null
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var searchRow: LinearLayout
    private lateinit var end: TextView
    private val lookup = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var initializing = false
    private var startupError: String? = null
    private var active = false
    private var night = true
    private var voice = true
    private var satellite = false
    private val red = 0xffe2162c.toInt()
    private val prefs by lazy { getSharedPreferences("google-navigation", MODE_PRIVATE) }
    override fun attachBaseContext(base: android.content.Context) {
        val m=base.resources.displayMetrics
        val config=android.content.res.Configuration(base.resources.configuration)
        config.densityDpi=(160f * minOf(m.widthPixels/602f,m.heightPixels/726f)).toInt().coerceAtLeast(120)
        config.fontScale=1f
        super.attachBaseContext(base.createConfigurationContext(config))
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK; window.navigationBarColor = Color.BLACK
        night = prefs.getBoolean("night",true); voice = prefs.getBoolean("voice",true)
        val frame = LinearLayout(this).apply { orientation=1;setBackgroundColor(0xff080b0f.toInt()) }
        setContentView(frame)
        frame.setOnApplyWindowInsetsListener { v,i -> v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom);i }
        val header = row()
        header.addView(label("TRX",22,true), LinearLayout.LayoutParams(0,dp(48),1f))
        header.addView(label("APEX",22,true).apply { setTextColor(red) },LinearLayout.LayoutParams(0,dp(48),1f))
        header.addView(label("NAV LAB 0.4.0",11),LinearLayout.LayoutParams(0,dp(48),1f))
        frame.addView(header)
        val body=row();frame.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        val dock=LinearLayout(this).apply { orientation=1 }
        body.addView(dock,LinearLayout.LayoutParams(dp(58),-1))
        listOf("⌂\nHome","➤\nNav","♫\nMedia","◴\nPerf","▦\nApps","⚙\nSetup").forEachIndexed { index,title ->
            val b=label(title,11).apply { gravity=Gravity.CENTER;setTextColor(if(index==1)red else Color.LTGRAY);setOnClickListener { when(index) {1->recenter();5->settings();else->openLauncher(listOf("home","navigation","media","performance","apps")[index])} } }
            val slot=FrameLayout(this);slot.addView(b,FrameLayout.LayoutParams(-1,-1))
            if(index==1)slot.addView(View(this).apply { setBackgroundColor(red) },FrameLayout.LayoutParams(dp(3),-1))
            dock.addView(slot,LinearLayout.LayoutParams(-1,0,1f))
        }
        val content=LinearLayout(this).apply { orientation=1 };body.addView(content,LinearLayout.LayoutParams(0,-1,1f))
        searchRow=row();search=EditText(this).apply { hint="Destination or address";setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY);setTextSize(14f);setSingleLine();imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH;setOnEditorActionListener { _,_,_->findDestination();true } }
        searchRow.addView(search,LinearLayout.LayoutParams(0,dp(52),1f));searchRow.addView(button("Find") {findDestination()});content.addView(searchRow)
        status=label("Enable location to begin",12).apply { tag="google-status";setPadding(dp(8),dp(6),dp(8),dp(6));setOnClickListener {initialize()} };content.addView(status)
        val viewport=FrameLayout(this);content.addView(viewport,LinearLayout.LayoutParams(-1,0,1f))
        try { navView=NavigationView(this);viewport.addView(navView,FrameLayout.LayoutParams(-1,-1));navView!!.onCreate(state) }
        catch(e:Exception) { startupError="Map could not start: ${e.javaClass.simpleName}";status.text=startupError;navView=null }
        val tools=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false }
        val controls=row();tools.addView(controls)
        controls.addView(button("Recenter") {recenter()})
        controls.addView(button("Layers") {satellite=!satellite;map?.mapType=if(satellite)GoogleMap.MAP_TYPE_HYBRID else GoogleMap.MAP_TYPE_NORMAL})
        controls.addView(button("Saved") {showSaved()})
        controls.addView(button("Overview") {navView?.showRouteOverview()})
        end=button("End") {endRoute()};controls.addView(end);content.addView(tools)
        initialize()
    }
    private fun initialize() {
        if(navView==null || initializing || navigator!=null)return
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            startupError="Precise location required · tap here to retry";status.text=startupError
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),73);return
        }
        startupError=null;status.text="Connecting to Google Navigation…";initializing=true
        NavigationApi.getNavigator(this,object:NavigationApi.NavigatorListener {
            override fun onNavigatorReady(n:Navigator) {
                if(isFinishing || isDestroyed)return
                initializing=false;startupError=null;navigator=n
                navView?.apply {
                    setNavigationUiEnabled(true);setHeaderEnabled(true);setEtaCardEnabled(true);setRecenterButtonEnabled(true)
                    setSpeedometerEnabled(true);setSpeedLimitIconEnabled(true)
                    setForceNightMode(if(night)ForceNightMode.FORCE_NIGHT else ForceNightMode.FORCE_DAY)
                    setStylingOptions(StylingOptions().primaryNightModeThemeColor(0xff11161c.toInt()).secondaryNightModeThemeColor(0xff39131a.toInt()).headerLargeManeuverIconColor(red).headerGuidanceRecommendedLaneColor(red))
                    getMapAsync { m->map=m;m.setBuildingsEnabled(true);m.isTrafficEnabled=true;recenter() }
                }
                applyVoice();status.text="Google Navigation ready · enter a destination"
            }
            override fun onError(code:Int) {initializing=false;startupError=when(code) {
                NavigationApi.ErrorCode.NOT_AUTHORIZED->"Google authorization failed (1). Setup → Google connection details identifies this build. Check the matching Google Cloud key, Android restrictions, enabled SDKs and billing; tap here after correcting them."
                NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED->"Google terms not accepted · tap to retry"
                NavigationApi.ErrorCode.LOCATION_PERMISSION_MISSING->"Precise location required · tap to retry"
                else->"Google Navigation error $code · tap to retry"
            };status.text=startupError }
        })
    }
    @Suppress("DEPRECATION") private fun findDestination() {
        val query=search.text.toString().trim();if(query.isEmpty())return
        if(navigator==null){status.visibility=View.VISIBLE;status.text=startupError ?: "Connecting to Google Navigation…";return}
        val request=++generation;status.text="Finding destinations…"
        lookup.execute {
            try {
                val found=Geocoder(this,Locale.getDefault()).getFromLocationName(query,5).orEmpty()
                runOnUiThread {
                    if(isFinishing || isDestroyed || request!=generation)return@runOnUiThread
                    if(found.isEmpty()){status.text="No matching address. Try street, city and state.";return@runOnUiThread}
                    status.text="Select the correct destination"
                    AlertDialog.Builder(this).setTitle("Choose destination").setItems(found.map { it.getAddressLine(0) ?: query }.toTypedArray()) { _,index ->
                        val a=found[index];confirmPlace(a.getAddressLine(0) ?: query,a.latitude,a.longitude)
                    }.setNegativeButton("Cancel",null).show()
                }
            } catch(e:Exception) {runOnUiThread {if(!isDestroyed && request==generation)status.text="Address search unavailable. Check connection and retry."} }
        }
    }
    private fun confirmPlace(title:String,lat:Double,lng:Double) {
        AlertDialog.Builder(this).setTitle(title).setMessage("Start Google turn-by-turn guidance to this destination?")
            .setPositiveButton("Navigate") {_,_->route(title,lat,lng)}.setNeutralButton("Save") {_,_->
                val places=saved();if((0 until places.length()).none {places.getJSONObject(it).optString("title")==title})places.put(JSONObject().put("title",title).put("lat",lat).put("lng",lng))
                prefs.edit().putString("places",places.toString()).apply();status.text="Destination saved"
            }.setNegativeButton("Cancel",null).show()
    }
    private fun saved():JSONArray=try {JSONArray(prefs.getString("places","[]"))}catch(e:Exception){JSONArray()}
    private fun showSaved() {
        val places=saved();if(places.length()==0){status.text="No saved destinations. Find an address, then choose Save.";return}
        AlertDialog.Builder(this).setTitle("Saved destinations").setItems(Array(places.length()){places.getJSONObject(it).getString("title")}) {_,i->val p=places.getJSONObject(i);confirmPlace(p.getString("title"),p.getDouble("lat"),p.getDouble("lng"))}.setNegativeButton("Close",null).show()
    }
    private fun route(title:String,lat:Double,lng:Double) {
        val n=navigator ?: return;val request=++generation;status.text="Calculating route…"
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken,0)
        n.setDestination(Waypoint.builder().setLatLng(lat,lng).setTitle(title).build()).setOnResultListener {result->runOnUiThread {
            if(isDestroyed || isFinishing || request!=generation)return@runOnUiThread
            if(result==Navigator.RouteStatus.OK){n.startGuidance();active=true;searchRow.visibility=View.GONE;status.visibility=View.GONE;recenter()}
            else status.text="Route unavailable: $result"
        } }
    }
    private fun endRoute() {generation++;navigator?.stopGuidance();navigator?.clearDestinations();active=false;searchRow.visibility=View.VISIBLE;status.visibility=View.VISIBLE;status.text=startupError ?: if(navigator!=null) "Route ended · choose a destination" else "Google Navigation is still connecting"}
    private fun recenter(){map?.followMyLocation(GoogleMap.CameraPerspective.TILTED)}
    private fun applyVoice(){navigator?.setAudioGuidanceSettings(AudioGuidanceSettings.builder().setGuidanceMode(if(voice)AudioGuidanceSettings.GuidanceMode.VOICE_ALERTS_AND_GUIDANCE else AudioGuidanceSettings.GuidanceMode.SILENT).build())}
    private fun settings() {
        AlertDialog.Builder(this).setTitle("APEX Navigation").setItems(arrayOf(if(night)"Use day map" else "Use night map",if(voice)"Mute voice guidance" else "Enable voice guidance","Open 3D simulation","Google connection details","Google 3D preview")) {_,i->when(i) {
            0->{night=!night;prefs.edit().putBoolean("night",night).apply();navView?.setForceNightMode(if(night)ForceNightMode.FORCE_NIGHT else ForceNightMode.FORCE_DAY)}
            1->{voice=!voice;prefs.edit().putBoolean("voice",voice).apply();applyVoice()}
            2->{if(active)Toast.makeText(this,"End guidance before opening the simulation",Toast.LENGTH_LONG).show() else startActivity(Intent(this,MainActivity::class.java))}
            4->{
                if(active)Toast.makeText(this,"End guidance before opening the 3D preview",Toast.LENGTH_LONG).show()
                else {
                    val center=map?.cameraPosition?.target
                    startActivity(Intent(this,Google3DPreviewActivity::class.java)
                        .putExtra("latitude",center?.latitude ?: 40.333)
                        .putExtra("longitude",center?.longitude ?: -74.593))
                }
            }
            3->AlertDialog.Builder(this).setTitle("Google authorization").setMessage("Navigation SDK for Android and billing must be enabled. Authorize Android package:\ncom.diaztradeinc.trxnavprototype\n\nSigning SHA-1:\n03:04:AF:48:B6:70:72:BE:31:3F:17:C2:D8:F7:17:6D:6C:78:D3:BC\n\nBuild credential fingerprint (SHA-256 prefix): ${credentialFingerprint()}\n\nCompare this with the CI credential check to confirm the installed APK uses the current repository secret. This identifier is not the API key. A successful build does not prove Google authorization.").setPositiveButton("Close",null).show()
        } }.setNegativeButton("Close",null).show()
    }
    @Suppress("DEPRECATION")
    private fun credentialFingerprint():String {
        val key=packageManager.getApplicationInfo(packageName,PackageManager.GET_META_DATA)
            .metaData?.getString("com.google.android.geo.API_KEY").orEmpty().trim()
        if(key.isEmpty())return "MISSING"
        return java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
    private fun openLauncher(page:String) {
        try {startActivity(Intent().setClassName("com.diaztradeinc.trxlauncher","com.diaztradeinc.trxlauncher.MainActivity").putExtra("apexPage",page).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))}
        catch(e:Exception){Toast.makeText(this,"Install TRX APEX Launcher to open this page",Toast.LENGTH_LONG).show()}
    }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun row()=LinearLayout(this).apply {orientation=0;gravity=Gravity.CENTER_VERTICAL}
    private fun label(s:String,size:Int,bold:Boolean=false)=TextView(this).apply {text=s;setTextColor(Color.WHITE);textSize=size.toFloat();gravity=Gravity.CENTER_VERTICAL;setPadding(dp(6),0,dp(6),0);if(bold)setTypeface(null,Typeface.BOLD_ITALIC)}
    private fun button(s:String,action:()->Unit)=label(s,12).apply {gravity=Gravity.CENTER;minWidth=dp(58);minimumHeight=dp(48);background=GradientDrawable().apply {setColor(0xff141a22.toInt());setStroke(dp(1),0xff353b44.toInt());cornerRadius=dp(7).toFloat()};setOnClickListener {action()}}
    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==73 && g.firstOrNull()==PackageManager.PERMISSION_GRANTED)initialize()}
    override fun onStart(){super.onStart();navView?.onStart()}
    override fun onResume(){super.onResume();navView?.onResume()}
    override fun onPause(){navView?.onPause();super.onPause()}
    override fun onStop(){navView?.onStop();super.onStop()}
    override fun onSaveInstanceState(out:Bundle){navView?.onSaveInstanceState(out);super.onSaveInstanceState(out)}
    override fun onDestroy(){generation++;lookup.shutdownNow();if(isFinishing){navigator?.stopGuidance();navigator?.clearDestinations()};navView?.onDestroy();super.onDestroy()}
    override fun onTrimMemory(level:Int){super.onTrimMemory(level);navView?.onTrimMemory(level)}
}
