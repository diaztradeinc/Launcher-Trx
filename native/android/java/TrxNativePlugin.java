package com.diaztradeinc.trxlauncher;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@CapacitorPlugin(
    name = "TrxNative",
    permissions = {
        @Permission(alias = "location", strings = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}),
        @Permission(alias = "bluetooth", strings = {Manifest.permission.BLUETOOTH_CONNECT}),
        @Permission(alias = "notifications", strings = {Manifest.permission.POST_NOTIFICATIONS})
    }
)
public class TrxNativePlugin extends Plugin {
    private PlacesClient placesClient;
    @Override public void load() {
        super.load();
        ObdBridge.start(getContext());
        MediaBridge.ensureConnected(getContext());
        initializePlaces();
    }

    @PluginMethod public void requestPermissionGroup(PluginCall call) {
        String group = call.getString("group", "");
        if ("media".equals(group)) {
            MediaBridge.requestAccess(getContext());
            JSObject result = new JSObject();
            result.put("granted", MediaBridge.hasAccess(getContext()));
            call.resolve(result);
            return;
        }
        if ("launcher".equals(group)) {
            requestHomeRole(call);
            return;
        }
        if ("bluetooth".equals(group) && Build.VERSION.SDK_INT < 31) {
            JSObject result = new JSObject(); result.put("granted", true); call.resolve(result); return;
        }
        requestPermissionForAlias(group, call, "permissionResult");
    }

    @PermissionCallback private void permissionResult(PluginCall call) {
        String group = call.getString("group", "");
        JSObject result = new JSObject();
        result.put("granted", "granted".equals(getPermissionState(group).toString().toLowerCase()));
        call.resolve(result);
    }

    private void requestHomeRole(PluginCall call) {
        Activity activity = getActivity();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager manager = (RoleManager)activity.getSystemService(Context.ROLE_SERVICE);
                if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_HOME) && !manager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    activity.startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_HOME), 701);
                }
            } else {
                activity.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            }
            JSObject result = new JSObject(); result.put("opened", true); call.resolve(result);
        } catch (Throwable error) {
            call.reject("Unable to open default launcher settings", error.getMessage());
        }
    }

    @PluginMethod public void getInstalledApps(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            try {
                PackageManager pm = getContext().getPackageManager();
                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> found = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL);
                Collections.sort(found, Comparator.comparing(a -> a.loadLabel(pm).toString().toLowerCase()));
                JSArray result = new JSArray();
                for (ResolveInfo info : found) {
                    if (info.activityInfo == null || getContext().getPackageName().equals(info.activityInfo.packageName)) continue;
                    JSObject app = new JSObject();
                    app.put("name", info.loadLabel(pm).toString());
                    app.put("packageName", info.activityInfo.packageName);
                    try { app.put("icon", drawableDataUrl(info.loadIcon(pm))); } catch (Throwable ignored) { app.put("icon", ""); }
                    result.put(app);
                }
                JSObject payload = new JSObject(); payload.put("apps", result); call.resolve(payload);
            } catch (Throwable error) { call.reject("Unable to load installed apps", error.getMessage()); }
        });
    }

    private String drawableDataUrl(Drawable source) {
        int size = 96;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        source.setBounds(0, 0, size, size);
        source.draw(canvas);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    @PluginMethod public void launchApp(PluginCall call) {
        String pkg = call.getString("packageName", "");
        try {
            Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) { call.reject("App is not launchable"); return; }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (Throwable error) { call.reject("Unable to launch app", error.getMessage()); }
    }

    @PluginMethod public void appAction(PluginCall call) {
        String pkg = call.getString("packageName", "");
        String action = call.getString("action", "info");
        try {
            Uri uri = Uri.parse("package:" + pkg);
            Intent intent = new Intent("uninstall".equals(action) ? Intent.ACTION_DELETE : Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (Throwable error) { call.reject("Unable to open app action", error.getMessage()); }
    }

    @PluginMethod public void getMediaState(PluginCall call) {
        MediaBridge.refresh(getContext());
        JSObject result = new JSObject();
        result.put("hasAccess", MediaBridge.hasAccess(getContext()));
        result.put("title", MediaBridge.title);
        result.put("artist", MediaBridge.artist);
        result.put("source", MediaBridge.source);
        result.put("playing", MediaBridge.playing);
        result.put("liked", MediaBridge.liked);
        result.put("positionMs", MediaBridge.currentPositionMs());
        result.put("durationMs", MediaBridge.durationMs);
        result.put("artwork", bitmapDataUrl(MediaBridge.artwork));
        JSArray queue = new JSArray();
        for (int i = 0; i < MediaBridge.queueTitles.length; i++) {
            JSObject item = new JSObject();
            item.put("title", MediaBridge.queueTitles[i]);
            item.put("artist", i < MediaBridge.queueArtists.length ? MediaBridge.queueArtists[i] : "");
            item.put("artwork", i < MediaBridge.queueArtwork.length ? bitmapDataUrl(MediaBridge.queueArtwork[i]) : "");
            queue.put(item);
        }
        result.put("queue", queue);
        call.resolve(result);
    }

    private String bitmapDataUrl(Bitmap bitmap) {
        if (bitmap == null) return "";
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 84, out);
            return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable ignored) { return ""; }
    }

    @PluginMethod public void mediaCommand(PluginCall call) {
        String command = call.getString("command", "toggle");
        if ("previous".equals(command)) MediaBridge.previous(getContext());
        else if ("next".equals(command)) MediaBridge.next(getContext());
        else if ("seek".equals(command)) MediaBridge.seekTo(getContext(), call.getLong("positionMs", 0L));
        else if ("queue".equals(command)) MediaBridge.playQueueItem(getContext(), call.getInt("index", 0));
        else if ("favorite".equals(command)) {
            boolean success=MediaBridge.toggleFavorite(getContext());
            JSObject result=new JSObject();result.put("success",success);result.put("liked",MediaBridge.liked);call.resolve(result);return;
        } else MediaBridge.toggle(getContext());
        JSObject result=new JSObject();result.put("success",true);call.resolve(result);
    }

    @PluginMethod public void searchDestinations(PluginCall call) {
        final String query = call.getString("query", "").trim();
        if (query.length() < 3) {
            JSObject payload = new JSObject(); payload.put("suggestions", new JSArray()); call.resolve(payload); return;
        }
        initializePlaces();
        if(placesClient==null){
            JSObject payload=new JSObject();payload.put("suggestions",new JSArray());payload.put("error","GOOGLE PLACES IS NOT CONFIGURED");call.resolve(payload);return;
        }
        FindAutocompletePredictionsRequest request=FindAutocompletePredictionsRequest.builder()
            .setQuery(query).setCountries("US").setRegionCode("US").build();
        placesClient.findAutocompletePredictions(request).addOnSuccessListener(response -> {
            JSArray suggestions = new JSArray();
            for(AutocompletePrediction prediction:response.getAutocompletePredictions()){
                String primary=prediction.getPrimaryText(null).toString();
                String secondary=prediction.getSecondaryText(null).toString();
                JSObject item=new JSObject();item.put("placeId",prediction.getPlaceId());item.put("primary",primary);item.put("secondary",secondary);
                item.put("label",secondary.isEmpty()?primary:primary+", "+secondary);suggestions.put(item);
            }
            JSObject payload = new JSObject(); payload.put("suggestions", suggestions);
            call.resolve(payload);
        }).addOnFailureListener(error -> {
            JSObject payload=new JSObject();payload.put("suggestions",new JSArray());payload.put("error","GOOGLE PLACES UNAVAILABLE · CHECK PLACES API (NEW)");call.resolve(payload);
        });
    }

    private void initializePlaces(){
        if(placesClient!=null)return;
        try{
            ApplicationInfo info=getContext().getPackageManager().getApplicationInfo(getContext().getPackageName(),PackageManager.GET_META_DATA);
            String apiKey=info.metaData==null?"":info.metaData.getString("com.google.android.geo.API_KEY","");
            if(apiKey==null||apiKey.trim().isEmpty())return;
            if(!Places.isInitialized())Places.initializeWithNewPlacesApiEnabled(getContext().getApplicationContext(),apiKey);
            placesClient=Places.createClient(getContext());
        }catch(Throwable ignored){placesClient=null;}
    }

    @PluginMethod public void getObdState(PluginCall call) {
        ObdBridge.start(getContext());
        JSObject result = new JSObject();
        result.put("connected", ObdBridge.connected);
        result.put("ecuConnected", ObdBridge.ecuConnected);
        result.put("status", ObdBridge.status);
        result.put("deviceName", ObdBridge.deviceName);
        result.put("protocol", ObdBridge.protocol);
        result.put("livePidCount", ObdBridge.livePidCount);
        putNumber(result, "rpm", ObdBridge.rpm);
        putNumber(result, "coolantF", ObdBridge.coolantF);
        putNumber(result, "intakeF", ObdBridge.intakeF);
        putNumber(result, "engineLoad", ObdBridge.engineLoad);
        putNumber(result, "batteryV", ObdBridge.batteryV);
        putNumber(result, "speedMph", ObdBridge.obdSpeedMph);
        putNumber(result, "boostPsi", ObdBridge.boostPsi);
        putNumber(result, "transmissionF", ObdBridge.transmissionF);
        putNumber(result, "throttle", ObdBridge.throttle);
        putNumber(result, "fuelLevel", ObdBridge.fuelLevel);
        putNumber(result, "mafGps", ObdBridge.mafGps);
        call.resolve(result);
    }

    private void putNumber(JSObject target, String key, float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) target.put(key, null);
        else target.put(key, value);
    }

    @PluginMethod public void reconnectObd(PluginCall call) {
        ObdBridge.reconnect(getContext());
        call.resolve();
    }

    @PluginMethod public void getLocation(PluginCall call) {
        if (getPermissionState("location") != com.getcapacitor.PermissionState.GRANTED) {
            call.reject("Location permission required"); return;
        }
        try {
            LocationManager manager = (LocationManager)getContext().getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String provider : manager.getProviders(true)) {
                Location item = manager.getLastKnownLocation(provider);
                if (item != null && (best == null || item.getTime() > best.getTime())) best = item;
            }
            if (best == null) { call.reject("Waiting for GPS fix"); return; }
            JSObject result = new JSObject();
            result.put("latitude", best.getLatitude());
            result.put("longitude", best.getLongitude());
            result.put("speedMph", Math.max(0, best.getSpeed() * 2.236936));
            call.resolve(result);
        } catch (Throwable error) { call.reject("Location unavailable", error.getMessage()); }
    }

    @PluginMethod public void openNavigation(PluginCall call) {
        Intent intent = new Intent(getContext(), NavigationActivity.class);
        intent.putExtra("destination", call.getString("destination", ""));
        String placeId=call.getString("placeId");
        if(placeId!=null&&!placeId.trim().isEmpty())intent.putExtra("placeId",placeId);
        intent.putExtra("theme",call.getString("theme","titanium"));
        intent.putExtra("accentColor",call.getString("accentColor","#f28a32"));
        Integer accentStrength=call.getInt("accentStrength");
        if(accentStrength!=null)intent.putExtra("accentStrength",accentStrength);
        intent.putExtra("routingStrategy", call.getString("routingStrategy", "fastest"));
        intent.putExtra("avoidTolls", Boolean.TRUE.equals(call.getBoolean("avoidTolls", false)));
        intent.putExtra("mapMode", call.getString("mapMode", "standard"));
        intent.putExtra("audioEnabled", !Boolean.FALSE.equals(call.getBoolean("audioEnabled", true)));
        Double latitude = call.getDouble("latitude");
        Double longitude = call.getDouble("longitude");
        if (latitude != null && longitude != null) {
            intent.putExtra("latitude", latitude); intent.putExtra("longitude", longitude);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
        call.resolve();
    }

    @PluginMethod public void openSystemSettings(PluginCall call) {
        String target = call.getString("target", "app");
        Intent intent;
        if ("bluetooth".equals(target)) intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        else if ("home".equals(target)) intent = new Intent(Settings.ACTION_HOME_SETTINGS);
        else intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
        call.resolve();
    }
}
