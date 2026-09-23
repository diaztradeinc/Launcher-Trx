package com.diaztradeinc.trxlauncher;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.GoogleMap.CameraPerspective;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.libraries.navigation.AudioGuidanceSettings;
import com.google.android.libraries.navigation.ListenableResultFuture;
import com.google.android.libraries.navigation.NavigationApi;
import com.google.android.libraries.navigation.Navigator;
import com.google.android.libraries.navigation.RoutingOptions;
import com.google.android.libraries.navigation.NavigationView;
import com.google.android.libraries.navigation.Waypoint;

import java.util.List;
import java.util.Locale;

public class NavigationActivity extends AppCompatActivity {
    private static final int LOCATION_REQUEST = 731;
    private NavigationView navigationView;
    private Navigator navigator;
    private EditText destination;
    private TextView status;
    private LinearLayout searchBar;
    private LinearLayout driveControls;
    private boolean initializing;
    private boolean satelliteMode;
    private boolean audioEnabled = true;
    private String startupStage = "ACTIVITY WINDOW";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
            startupStage = "NAVIGATION VIEW CONSTRUCTION";
            buildUi(state);
            startupStage = "NAVIGATION VIEW ONCREATE";
            navigationView.onCreate(state);
            startupStage = "NAVIGATOR INITIALIZATION";
            ensureLocationAndInitialize();
        } catch (Throwable error) { Log.e("TRX-NAV","Navigation startup failed at "+startupStage,error); showFatal(startupMessage(error)); }
    }

    private void buildUi(Bundle state) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff050605);
        navigationView = new NavigationView(this);
        root.addView(navigationView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(18), 0, dp(8), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xee090a09); bg.setCornerRadius(dp(30)); bg.setStroke(dp(1), 0x88f12d31);
        searchBar.setBackground(bg);
        destination = new EditText(this);
        destination.setSingleLine(true); destination.setHint("Search destination"); destination.setHintTextColor(0xff777771);
        destination.setTextColor(Color.WHITE); destination.setTextSize(17); destination.setBackgroundColor(Color.TRANSPARENT);
        searchBar.addView(destination, new LinearLayout.LayoutParams(0, dp(58), 1));
        Button go = actionButton("GO"); go.setOnClickListener(v -> routeToInput());
        searchBar.addView(go, new LinearLayout.LayoutParams(dp(74), dp(46)));
        FrameLayout.LayoutParams searchLp = new FrameLayout.LayoutParams(-1, dp(58));
        searchLp.leftMargin = dp(24); searchLp.rightMargin = dp(24); searchLp.topMargin = dp(26);
        root.addView(searchBar, searchLp);

        driveControls = new LinearLayout(this);
        driveControls.setOrientation(LinearLayout.VERTICAL);
        driveControls.setGravity(Gravity.CENTER);
        driveControls.setPadding(dp(10),dp(12),dp(10),dp(12));
        GradientDrawable railBg = new GradientDrawable();
        railBg.setColor(0xe80a0b0a); railBg.setCornerRadius(dp(48)); railBg.setStroke(dp(1),0x665f625e);
        driveControls.setBackground(railBg);
        Button recenter = railButton("◎\nRECENTER");
        recenter.setOnClickListener(v -> navigationView.getMapAsync(map -> map.followMyLocation(CameraPerspective.TILTED)));
        Button satellite = railButton("◇\nSATELLITE");
        satellite.setOnClickListener(v -> navigationView.getMapAsync(map -> {
            satelliteMode = !satelliteMode;
            map.setMapType(satelliteMode ? GoogleMap.MAP_TYPE_HYBRID : GoogleMap.MAP_TYPE_NORMAL);
            satellite.setText(satelliteMode ? "◇\nSTANDARD" : "◇\nSATELLITE");
        }));
        Button overview = railButton("▱\nOVERVIEW"); overview.setOnClickListener(v -> navigationView.showRouteOverview());
        Button audio = railButton("◖))\nAUDIO");
        audio.setOnClickListener(v -> {
            audioEnabled = !audioEnabled;
            AudioGuidanceSettings setting = AudioGuidanceSettings.builder().setGuidanceMode(audioEnabled ? AudioGuidanceSettings.GuidanceMode.VOICE_ALERTS_AND_GUIDANCE : AudioGuidanceSettings.GuidanceMode.SILENT).build();
            if (navigator != null) navigator.setAudioGuidanceSettings(setting);
            audio.setText(audioEnabled ? "◖))\nAUDIO" : "◖×\nMUTED");
        });
        Button exit = railButton("×\nEXIT"); exit.setOnClickListener(v -> finish());
        driveControls.addView(recenter); driveControls.addView(satellite); driveControls.addView(overview); driveControls.addView(audio); driveControls.addView(exit);
        driveControls.setVisibility(View.GONE);
        FrameLayout.LayoutParams railLp = new FrameLayout.LayoutParams(dp(102), -2, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        railLp.rightMargin=dp(18);root.addView(driveControls,railLp);

        status = new TextView(this);
        status.setText("INITIALIZING GOOGLE NAVIGATION…"); status.setTextColor(0xfff4f0e8); status.setTextSize(12);
        status.setGravity(Gravity.CENTER); status.setBackgroundColor(0xdd050605);
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(-1, dp(34), Gravity.BOTTOM); root.addView(status, statusLp);

        String requested = getIntent().getStringExtra("destination");
        if (requested != null) destination.setText(requested);
    }

    private void ensureLocationAndInitialize() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            status.setText("LOCATION PERMISSION REQUIRED");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
            return;
        }
        initializeNavigator();
    }

    private void initializeNavigator() {
        if (initializing || navigator != null) return;
        initializing = true;
        try {
            NavigationApi.getNavigator(this, new NavigationApi.NavigatorListener() {
                @Override public void onNavigatorReady(Navigator ready) {
                    try {
                        navigator = ready; initializing = false;
                        navigationView.setNavigationUiEnabled(true);
                        navigationView.setHeaderEnabled(true);
                        navigationView.setEtaCardEnabled(true);
                        navigationView.setRecenterButtonEnabled(true);
                        navigationView.setSpeedometerEnabled(true);
                        navigationView.setSpeedLimitIconEnabled(true);
                        navigationView.getMapAsync(map -> map.followMyLocation(CameraPerspective.TILTED));
                        status.setText("NAVIGATION READY");
                        if (!destination.getText().toString().trim().isEmpty()) routeToInput();
                    } catch (Throwable error) { showFatal("NAVIGATION DISPLAY ERROR"); }
                }
                @Override public void onError(int code) {
                    initializing = false;
                    if (code == NavigationApi.ErrorCode.NOT_AUTHORIZED) showFatal("GOOGLE NAVIGATION KEY NOT AUTHORIZED");
                    else if (code == NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED) showFatal("ACCEPT GOOGLE NAVIGATION TERMS TO CONTINUE");
                    else if (code == NavigationApi.ErrorCode.LOCATION_PERMISSION_MISSING) showFatal("LOCATION PERMISSION REQUIRED");
                    else showFatal("NAVIGATION ERROR " + code);
                }
            });
        } catch (Throwable error) { initializing = false; showFatal("NAVIGATION INITIALIZATION FAILED"); }
    }

    private void routeToInput() {
        String query = destination.getText().toString().trim();
        if (query.isEmpty()) { Toast.makeText(this, "Enter a destination", Toast.LENGTH_SHORT).show(); return; }
        if (navigator == null) { status.setText("NAVIGATION IS STILL INITIALIZING"); return; }
        String placeId=getIntent().getStringExtra("placeId");
        if(placeId!=null&&!placeId.trim().isEmpty()){
            getIntent().removeExtra("placeId");routeToPlaceId(query,placeId);return;
        }
        if (getIntent().hasExtra("latitude") && getIntent().hasExtra("longitude")) {
            routeToCoordinates(query, getIntent().getDoubleExtra("latitude", 0), getIntent().getDoubleExtra("longitude", 0));
            getIntent().removeExtra("latitude"); getIntent().removeExtra("longitude"); return;
        }
        status.setText("FINDING " + query.toUpperCase(Locale.US) + "…");
        new Thread(() -> {
            try {
                List<Address> matches = new Geocoder(this, Locale.US).getFromLocationName(query, 1);
                if (matches == null || matches.isEmpty()) throw new IllegalArgumentException("Destination not found");
                Address found = matches.get(0);
                runOnUiThread(() -> routeToCoordinates(query, found.getLatitude(), found.getLongitude()));
            } catch (Throwable error) { runOnUiThread(() -> status.setText("DESTINATION NOT FOUND · TRY A SUGGESTION")); }
        }, "apex-route-lookup").start();
    }

    private void routeToPlaceId(String title,String placeId){
        try{
            Waypoint waypoint=Waypoint.builder().setPlaceIdString(placeId).setTitle(title).build();
            calculateRoute(waypoint);
        }catch(Throwable error){Log.e("TRX-NAV","Place route failed",error);status.setText("SELECTED PLACE COULD NOT BE ROUTED");}
    }

    private void routeToCoordinates(String title, double latitude, double longitude) {
        try {
            Waypoint waypoint = new Waypoint.Builder().setLatLng(latitude, longitude).setTitle(title).build();
            calculateRoute(waypoint);
        } catch (Throwable error) { status.setText("ROUTE COULD NOT BE CREATED"); }
    }

    private void calculateRoute(Waypoint waypoint){
        status.setVisibility(android.view.View.VISIBLE);status.setText("CALCULATING ROUTE…");
        RoutingOptions options=new RoutingOptions();options.travelMode(RoutingOptions.TravelMode.DRIVING);
        ListenableResultFuture<Navigator.RouteStatus> pending=navigator.setDestination(waypoint,options);
        pending.setOnResultListener(routeStatus -> runOnUiThread(() -> {
            try{
                if(routeStatus==Navigator.RouteStatus.OK){
                    AudioGuidanceSettings audio=AudioGuidanceSettings.builder().setGuidanceMode(AudioGuidanceSettings.GuidanceMode.VOICE_ALERTS_AND_GUIDANCE).build();
                    navigator.setAudioGuidanceSettings(audio);navigator.startGuidance();status.setVisibility(android.view.View.GONE);destination.clearFocus();
                    searchBar.setVisibility(View.GONE);driveControls.setVisibility(View.VISIBLE);
                    InputMethodManager keyboard=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    if(keyboard!=null)keyboard.hideSoftInputFromWindow(destination.getWindowToken(),0);
                }else status.setText("ROUTE UNAVAILABLE · "+routeStatus);
            }catch(Throwable error){Log.e("TRX-NAV","Guidance start failed",error);status.setText("GUIDANCE COULD NOT START");}
        }));
    }

    private Button actionButton(String label) {
        Button button = new Button(this); button.setText(label); button.setTextColor(Color.WHITE); button.setTextSize(12);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xffc71f28); bg.setCornerRadius(dp(23)); button.setBackground(bg); return button;
    }

    private Button railButton(String label) {
        Button button=new Button(this);button.setText(label);button.setTextColor(0xfff2eee6);button.setTextSize(9);button.setGravity(Gravity.CENTER);button.setAllCaps(false);
        button.setPadding(0,0,0,0);GradientDrawable bg=new GradientDrawable();bg.setShape(GradientDrawable.OVAL);bg.setColor(0xff111210);bg.setStroke(dp(1),0x886f716c);button.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(76),dp(76));lp.topMargin=dp(5);lp.bottomMargin=dp(5);button.setLayoutParams(lp);return button;
    }

    private String startupMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String detail = root.getMessage();
        if (detail == null || detail.trim().isEmpty()) detail = root.getClass().getSimpleName();
        StackTraceElement[] trace = root.getStackTrace();
        String where = trace.length == 0 ? "" : " · " + trace[0].getClassName().replace("com.google.android.", "g.") + ":" + trace[0].getLineNumber();
        return "NAV FAILURE · " + startupStage + "\n" + root.getClass().getSimpleName() + " · " + detail + where;
    }
    private void showFatal(String message) {
        if (status != null) { status.setVisibility(android.view.View.VISIBLE); status.setText(message); return; }
        TextView report = new TextView(this);
        report.setText(message + "\n\nGoogle Play services and graphics compatibility are checked before API authorization.");
        report.setTextColor(Color.WHITE); report.setTextSize(15); report.setGravity(Gravity.CENTER); report.setPadding(dp(28),dp(28),dp(28),dp(28));
        report.setBackgroundColor(0xff151615); setContentView(report);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) initializeNavigator();
        else if (requestCode == LOCATION_REQUEST) showFatal("LOCATION PERMISSION REQUIRED");
    }

    @Override protected void onStart() { super.onStart(); if (navigationView != null) navigationView.onStart(); }
    @Override protected void onResume() { super.onResume(); if (navigationView != null) navigationView.onResume(); }
    @Override protected void onPause() { if (navigationView != null) navigationView.onPause(); super.onPause(); }
    @Override protected void onStop() { if (navigationView != null) navigationView.onStop(); super.onStop(); }
    @Override protected void onDestroy() { if (navigationView != null) navigationView.onDestroy(); super.onDestroy(); }
    @Override public void onTrimMemory(int level) { super.onTrimMemory(level); if (navigationView != null) navigationView.onTrimMemory(level); }
    @Override public void onConfigurationChanged(@NonNull Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (navigationView != null) navigationView.onConfigurationChanged(configuration);
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        if (navigationView != null) navigationView.onSaveInstanceState(state);
        super.onSaveInstanceState(state);
    }
}
