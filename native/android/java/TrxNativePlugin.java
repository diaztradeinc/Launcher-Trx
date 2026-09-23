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

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@CapacitorPlugin(
    name = "TrxNative",
    permissions = {
        @Permission(alias = "location", strings = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}),
        @Permission(alias = "bluetooth", strings = {Manifest.permission.BLUETOOTH_CONNECT}),
        @Permission(alias = "notifications", strings = {Manifest.permission.POST_NOTIFICATIONS})
    }
)
public class TrxNativePlugin extends Plugin {
    @Override public void load() {
        super.load();
        ObdBridge.start(getContext());
        MediaBridge.ensureConnected(getContext());
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
            call.reject("Unable to open default launcher settings", error);
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
            } catch (Throwable error) { call.reject("Unable to load installed apps", error); }
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
        } catch (Throwable error) { call.reject("Unable to launch app", error); }
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
        } catch (Throwable error) { call.reject("Unable to open app action", error); }
    }

    @PluginMethod public void getMediaState(PluginCall call) {
        MediaBridge.refresh(getContext());
        JSObject result = new JSObject();
        result.put("hasAccess", MediaBridge.hasAccess(getContext()));
        result.put("title", MediaBridge.title);
        result.put("artist", MediaBridge.artist);
        result.put("source", MediaBridge.source);
        result.put("playing", MediaBridge.playing);
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
        else MediaBridge.toggle(getContext());
        call.resolve();
    }

    @PluginMethod public void getObdState(PluginCall call) {
        ObdBridge.start(getContext());
        JSObject result = new JSObject();
        result.put("connected", ObdBridge.connected);
        result.put("status", ObdBridge.status);
        result.put("deviceName", ObdBridge.deviceName);
        putNumber(result, "rpm", ObdBridge.rpm);
        putNumber(result, "coolantF", ObdBridge.coolantF);
        putNumber(result, "intakeF", ObdBridge.intakeF);
        putNumber(result, "engineLoad", ObdBridge.engineLoad);
        putNumber(result, "batteryV", ObdBridge.batteryV);
        putNumber(result, "speedMph", ObdBridge.obdSpeedMph);
        putNumber(result, "boostPsi", ObdBridge.boostPsi);
        putNumber(result, "transmissionF", ObdBridge.transmissionF);
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
        } catch (Throwable error) { call.reject("Location unavailable", error); }
    }

    @PluginMethod public void openNavigation(PluginCall call) {
        Intent intent = new Intent(getContext(), NavigationActivity.class);
        intent.putExtra("destination", call.getString("destination", ""));
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
