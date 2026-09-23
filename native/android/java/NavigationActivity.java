package com.diaztradeinc.trxlauncher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.libraries.navigation.ListenableResultFuture;
import com.google.android.libraries.navigation.NavigationApi;
import com.google.android.libraries.navigation.NavigationView;
import com.google.android.libraries.navigation.Navigator;
import com.google.android.libraries.navigation.RoutingOptions;
import com.google.android.libraries.navigation.Waypoint;

import java.util.List;
import java.util.Locale;

public class NavigationActivity extends Activity {
    private NavigationView navigationView;
    private Navigator navigator;
    private EditText destination;
    private TextView status;
    private Bundle savedState;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        savedState = state;
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().getInsetsController().hide(WindowInsets.Type.statusBars());
            getWindow().getInsetsController().setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        buildUi();
        navigationView.onCreate(state);
        NavigationApi.getNavigator(this, new NavigationApi.NavigatorListener() {
            @Override public void onNavigatorReady(Navigator ready) {
                navigator = ready;
                navigationView.setNavigationUiEnabled(true);
                navigationView.setHeaderEnabled(true);
                navigationView.setEtaCardEnabled(true);
                navigationView.setRecenterButtonEnabled(true);
                navigationView.setSpeedometerEnabled(true);
                navigationView.setSpeedLimitIconEnabled(true);
                status.setText("NAVIGATION READY");
                String requested = getIntent().getStringExtra("destination");
                if (requested != null && !requested.trim().isEmpty()) {
                    destination.setText(requested);
                    routeTo(requested);
                }
            }
            @Override public void onError(int code) {
                status.setText(code == NavigationApi.ErrorCode.NOT_AUTHORIZED ? "GOOGLE NAVIGATION KEY NOT AUTHORIZED" : "NAVIGATION ERROR " + code);
            }
        });
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff050605);
        navigationView = new NavigationView(this);
        root.addView(navigationView, new FrameLayout.LayoutParams(-1, -1));

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
        Button go = actionButton("GO"); go.setOnClickListener(v -> routeTo(destination.getText().toString()));
        search.addView(go, new LinearLayout.LayoutParams(dp(74), dp(46)));
        FrameLayout.LayoutParams searchLp = new FrameLayout.LayoutParams(-1, dp(58));
        searchLp.leftMargin = dp(24); searchLp.rightMargin = dp(24); searchLp.topMargin = dp(26);
        root.addView(search, searchLp);

        Button close = actionButton("EXIT");
        close.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dp(82), dp(46), Gravity.BOTTOM | Gravity.RIGHT);
        closeLp.rightMargin = dp(22); closeLp.bottomMargin = dp(24);
        root.addView(close, closeLp);

        status = new TextView(this);
        status.setText("INITIALIZING GOOGLE NAVIGATION…"); status.setTextColor(0xfff4f0e8); status.setTextSize(12);
        status.setGravity(Gravity.CENTER); status.setBackgroundColor(0xdd050605);
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(-1, dp(34), Gravity.BOTTOM);
        root.addView(status, statusLp);
        setContentView(root);
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        button.setText(label); button.setTextColor(Color.WHITE); button.setTextSize(12);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xffc71f28); bg.setCornerRadius(dp(23));
        button.setBackground(bg); return button;
    }

    private void routeTo(String value) {
        final String query = value == null ? "" : value.trim();
        if (query.isEmpty()) { Toast.makeText(this, "Enter a destination", Toast.LENGTH_SHORT).show(); return; }
        if (navigator == null) { status.setText("NAVIGATION IS STILL INITIALIZING"); return; }
        status.setText("FINDING " + query.toUpperCase(Locale.US) + "…");
        new Thread(() -> {
            try {
                List<Address> matches = new Geocoder(this, Locale.US).getFromLocationName(query, 1);
                if (matches == null || matches.isEmpty()) throw new IllegalArgumentException("Destination not found");
                Address found = matches.get(0);
                Waypoint waypoint = new Waypoint.Builder().setLatLng(found.getLatitude(), found.getLongitude()).setTitle(query).build();
                RoutingOptions options = new RoutingOptions();
                options.travelMode(RoutingOptions.TravelMode.DRIVING);
                runOnUiThread(() -> {
                    ListenableResultFuture<Navigator.RouteStatus> result = navigator.setDestination(waypoint, options);
                    result.setOnResultListener(routeStatus -> {
                        if (routeStatus == Navigator.RouteStatus.OK) {
                            navigator.startGuidance(); status.setVisibility(View.GONE); destination.clearFocus();
                        } else status.setText("ROUTE UNAVAILABLE · " + routeStatus);
                    });
                });
            } catch (Throwable error) { runOnUiThread(() -> status.setText("DESTINATION NOT FOUND")); }
        }, "apex-route").start();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onStart(){super.onStart();navigationView.onStart();}
    @Override protected void onResume(){super.onResume();navigationView.onResume();}
    @Override protected void onPause(){navigationView.onPause();super.onPause();}
    @Override protected void onStop(){navigationView.onStop();super.onStop();}
    @Override protected void onDestroy(){navigationView.onDestroy();super.onDestroy();}
    @Override public void onLowMemory(){super.onLowMemory();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);navigationView.onSaveInstanceState(out);}
}
