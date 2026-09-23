package com.diaztradeinc.trxlauncher;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.GoogleMap.CameraPerspective;
import com.google.android.libraries.navigation.AudioGuidanceSettings;
import com.google.android.libraries.navigation.ListenableResultFuture;
import com.google.android.libraries.navigation.NavigationApi;
import com.google.android.libraries.navigation.Navigator;
import com.google.android.libraries.navigation.RoutingOptions;
import com.google.android.libraries.navigation.SupportNavigationFragment;
import com.google.android.libraries.navigation.Waypoint;

import java.util.List;
import java.util.Locale;

public class NavigationActivity extends AppCompatActivity {
    private static final int LOCATION_REQUEST = 731;
    private SupportNavigationFragment navigationFragment;
    private Navigator navigator;
    private EditText destination;
    private TextView status;
    private boolean initializing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
            if (android.os.Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) {
                getWindow().getInsetsController().hide(WindowInsets.Type.statusBars());
                getWindow().getInsetsController().setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
            buildUi();
            ensureLocationAndInitialize();
        } catch (Throwable error) { Log.e("TRX-NAV","Navigation startup failed",error); showFatal("NAV START ERROR · "+error.getClass().getSimpleName()); }
    }

    private void buildUi() {
        setContentView(R.layout.activity_navigation);
        FrameLayout root=findViewById(R.id.navigation_root);
        navigationFragment=(SupportNavigationFragment)getSupportFragmentManager().findFragmentById(R.id.navigation_fragment);
        if(navigationFragment==null)throw new IllegalStateException("Navigation fragment unavailable");

        LinearLayout search = new LinearLayout(this);
        search.setOrientation(LinearLayout.HORIZONTAL);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(18), 0, dp(8), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xee090a09); bg.setCornerRadius(dp(30)); bg.setStroke(dp(1), 0x88f12d31);
        search.setBackground(bg);
        destination = new EditText(this);
        destination.setSingleLine(true); destination.setHint("Search destination"); destination.setHintTextColor(0xff777771);
        destination.setTextColor(Color.WHITE); destination.setTextSize(17); destination.setBackgroundColor(Color.TRANSPARENT);
        search.addView(destination, new LinearLayout.LayoutParams(0, dp(58), 1));
        Button go = actionButton("GO"); go.setOnClickListener(v -> routeToInput());
        search.addView(go, new LinearLayout.LayoutParams(dp(74), dp(46)));
        FrameLayout.LayoutParams searchLp = new FrameLayout.LayoutParams(-1, dp(58));
        searchLp.leftMargin = dp(24); searchLp.rightMargin = dp(24); searchLp.topMargin = dp(26);
        root.addView(search, searchLp);

        Button close = actionButton("EXIT"); close.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dp(82), dp(46), Gravity.BOTTOM | Gravity.RIGHT);
        closeLp.rightMargin = dp(22); closeLp.bottomMargin = dp(24); root.addView(close, closeLp);

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
                        navigationFragment.setNavigationUiEnabled(true);
                        navigationFragment.setHeaderEnabled(true);
                        navigationFragment.setEtaCardEnabled(true);
                        navigationFragment.setRecenterButtonEnabled(true);
                        navigationFragment.setSpeedometerEnabled(true);
                        navigationFragment.setSpeedLimitIconEnabled(true);
                        navigationFragment.getMapAsync(map -> map.followMyLocation(CameraPerspective.TILTED));
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
                }else status.setText("ROUTE UNAVAILABLE · "+routeStatus);
            }catch(Throwable error){Log.e("TRX-NAV","Guidance start failed",error);status.setText("GUIDANCE COULD NOT START");}
        }));
    }

    private Button actionButton(String label) {
        Button button = new Button(this); button.setText(label); button.setTextColor(Color.WHITE); button.setTextSize(12);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xffc71f28); bg.setCornerRadius(dp(23)); button.setBackground(bg); return button;
    }

    private void showFatal(String message) { if (status != null) status.setText(message); else Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) initializeNavigator();
        else if (requestCode == LOCATION_REQUEST) showFatal("LOCATION PERMISSION REQUIRED");
    }
}
