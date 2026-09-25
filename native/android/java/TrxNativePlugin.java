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
import android.media.AudioManager;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import android.util.DisplayMetrics;

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
        @Permission(alias = "notifications", strings = {Manifest.permission.POST_NOTIFICATIONS}),
        @Permission(alias = "visualizer", strings = {Manifest.permission.RECORD_AUDIO})
    }
)
public class TrxNativePlugin extends Plugin {
    private Visualizer spectrum;
    private PlacesClient placesClient;
    private PreviewMapController previewMap;
    private LocationManager locationManager;
    private boolean locationUpdates;
    private final android.location.LocationListener locationListener=new android.location.LocationListener(){
        @Override public void onLocationChanged(Location location){}
        @Override public void onProviderEnabled(String provider){}
        @Override public void onProviderDisabled(String provider){}
        @Override public void onStatusChanged(String provider,int status,android.os.Bundle extras){}
    };
    private void ensureLocationUpdates(){
        if(locationUpdates)return;
        try {
            locationManager=(LocationManager)getContext().getSystemService(Context.LOCATION_SERVICE);
            for(String provider:locationManager.getProviders(true))if(!LocationManager.PASSIVE_PROVIDER.equals(provider)){
                locationManager.requestLocationUpdates(provider,5000L,2f,locationListener,android.os.Looper.getMainLooper());
                locationUpdates=true;
            }
        }catch(SecurityException ignored){}catch(RuntimeException ignored){}
    }
    @PluginMethod public void mapPreview(PluginCall call){
        if(previewMap==null)previewMap=new PreviewMapController(getActivity(),getBridge().getWebView());
        previewMap.update(call);
    }
    @Override protected void handleOnResume(){super.handleOnResume();if(previewMap!=null)previewMap.resume();}
    @Override protected void handleOnPause(){stopSpectrum();if(previewMap!=null)previewMap.pause();super.handleOnPause();}
    @Override protected void handleOnDestroy(){stopSpectrum();if(previewMap!=null)previewMap.destroy();if(locationManager!=null)locationManager.removeUpdates(locationListener);super.handleOnDestroy();}

    private synchronized void stopSpectrum(){
        if(spectrum!=null){try{spectrum.setEnabled(false);}catch(Throwable ignored){}try{spectrum.release();}catch(Throwable ignored){}spectrum=null;}
    }
    @PluginMethod public void stopAudioSpectrum(PluginCall call){stopSpectrum();call.resolve();}
    @PluginMethod public void visualizerAccess(PluginCall call){
        JSObject result=new JSObject();
        result.put("granted",Build.VERSION.SDK_INT<23 || getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED);
        call.resolve(result);
    }
    @PluginMethod public synchronized void getAudioSpectrum(PluginCall call){
        JSObject result=new JSObject();
        if(Build.VERSION.SDK_INT>=23 && getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            result.put("available",false);result.put("reason","Enable audio visualization to show live levels.");call.resolve(result);return;
        }
        try {
            if(spectrum==null){spectrum=new Visualizer(0);spectrum.setCaptureSize(Visualizer.getCaptureSizeRange()[0]);spectrum.setEnabled(true);}
            byte[] fft=new byte[spectrum.getCaptureSize()];
            if(spectrum.getFft(fft)!=Visualizer.SUCCESS)throw new IllegalStateException("Audio output is not available");
            JSArray bands=new JSArray();int bins=Math.max(1,fft.length/2-1);
            for(int i=0;i<32;i++){
                int from=1+(int)(Math.pow(i/32.0,1.6)*bins);int to=Math.max(from+1,1+(int)(Math.pow((i+1)/32.0,1.6)*bins));
                float peak=0;for(int b=from;b<Math.min(to,bins);b++){int re=fft[b*2],im=fft[b*2+1];peak=Math.max(peak,(float)Math.sqrt(re*re+im*im));}
                bands.put(Math.min(1.0,Math.max(0.0,peak/110.0)));
            }
            result.put("available",true);result.put("bands",bands);call.resolve(result);
        }catch(Throwable error){stopSpectrum();result.put("available",false);result.put("reason","Audio output visualization is unavailable on this device.");call.resolve(result);}
    }

    @PluginMethod public void getDisplayInfo(PluginCall call) {
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        android.content.res.Configuration configuration = getContext().getResources().getConfiguration();
        JSObject result = new JSObject();
        result.put("manufacturer", Build.MANUFACTURER);
        result.put("model", Build.MODEL);
        result.put("widthPixels", metrics.widthPixels);
        result.put("heightPixels", metrics.heightPixels);
        result.put("densityDpi", metrics.densityDpi);
        result.put("widthDp", configuration.screenWidthDp);
        result.put("heightDp", configuration.screenHeightDp);
        DisplayMetrics fullDisplay = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getRealMetrics(fullDisplay);
        result.put("fullWidthPixels", fullDisplay.widthPixels);
        result.put("fullHeightPixels", fullDisplay.heightPixels);
        call.resolve(result);
    }
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
        if ("overlay".equals(group)) {
            try {
                if (!Settings.canDrawOverlays(getContext())) {
                    Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getContext().getPackageName()));
                    getActivity().startActivity(permission);
                }
                JSObject result = new JSObject();
                result.put("granted", Settings.canDrawOverlays(getContext()));
                result.put("opened", true);
                call.resolve(result);
            } catch (Throwable error) { call.reject("Unable to open overlay permission",error.getMessage()); }
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

    @PluginMethod public void launchAdjacent(PluginCall call){
        String pkg=call.getString("packageName","");
        try{
            Intent intent=getContext().getPackageManager().getLaunchIntentForPackage(pkg);
            if(intent==null){call.reject("App is not launchable");return;}
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
            getActivity().startActivity(intent);
            JSObject result=new JSObject();result.put("success",true);
            result.put("message","Requested a split beside TRX APEX; Android may open the app full screen.");call.resolve(result);
        }catch(Throwable error){call.reject("Unable to request split screen",error.getMessage());}
    }

    @PluginMethod public void startAppPair(PluginCall call) {
        String first=call.getString("first", ""), second=call.getString("second", "");
        if(first.equals(second)){call.reject("Choose two different apps");return;}
        PackageManager pm=getContext().getPackageManager();
        Intent left=pm.getLaunchIntentForPackage(first),right=pm.getLaunchIntentForPackage(second);
        if(left==null||right==null){call.reject("Both apps must be launchable");return;}
        try {
            left.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            right.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
            getActivity().startActivities(new Intent[]{left,right});
            JSObject result=new JSObject();result.put("success",true);
            result.put("message","Requested two-app split. If Ottocast opens one app full screen, use Android Recents → Split screen to select its partner.");
            call.resolve(result);
        }catch(Throwable error){call.reject("Unable to request two-app split",error.getMessage());}
    }

    @PluginMethod public void floatingRail(PluginCall call) {
        boolean enable=Boolean.TRUE.equals(call.getBoolean("enabled",false));
        JSObject result=new JSObject();
        boolean granted=Settings.canDrawOverlays(getContext());
        result.put("granted",granted);
        if(enable&&!granted){result.put("error","Allow Display over other apps in Android settings first.");call.resolve(result);return;}
        try {
            Intent intent=new Intent(getContext(),FloatingRailService.class);
            if(enable){ if(Build.VERSION.SDK_INT>=26)getContext().startForegroundService(intent);else getContext().startService(intent); }
            else getContext().stopService(intent);
            result.put("enabled",enable);call.resolve(result);
        }catch(Throwable error){call.reject("Unable to update floating rail",error.getMessage());}
    }

    @PluginMethod public void consumeRailDestination(PluginCall call) {
        Intent intent=getActivity().getIntent();
        String page=intent.getStringExtra("apexPage");
        intent.removeExtra("apexPage");
        JSObject result=new JSObject();result.put("page",page==null?"":page);call.resolve(result);
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
        result.put("hasSession", MediaBridge.hasSession());
        result.put("canFavorite", MediaBridge.canFavorite());
        result.put("canPrevious", MediaBridge.supports(android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS));
        result.put("canNext", MediaBridge.supports(android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT));
        result.put("canSeek", MediaBridge.supports(android.media.session.PlaybackState.ACTION_SEEK_TO));
        result.put("canQueue", MediaBridge.supports(android.media.session.PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM));
        try { result.put("sourceName",getContext().getPackageManager().getApplicationLabel(getContext().getPackageManager().getApplicationInfo(MediaBridge.source,0)).toString()); }
        catch(Exception e){result.put("sourceName",MediaBridge.source);}
        AudioManager audio=(AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
        result.put("volumeAvailable",audio!=null&&!audio.isVolumeFixed());
        if(audio!=null)result.put("volumePercent",Math.round(100f*audio.getStreamVolume(AudioManager.STREAM_MUSIC)/Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))));
        result.put("title", MediaBridge.title);
        result.put("artist", MediaBridge.artist);
        result.put("source", MediaBridge.source);
        result.put("playing", MediaBridge.playing);
        result.put("liked", MediaBridge.liked);
        result.put("positionMs", MediaBridge.currentPositionMs());
        result.put("durationMs", MediaBridge.durationMs);
        result.put("artwork", bitmapDataUrl(MediaBridge.artwork));
        JSArray queue = new JSArray();
        MediaBridge.QueueSnapshot snapshot=MediaBridge.queueSnapshot();
        for (int i = 0; i < snapshot.titles.length; i++) {
            JSObject item = new JSObject();
            item.put("id", i < snapshot.ids.length ? Long.toString(snapshot.ids[i]) : "");
            item.put("title", snapshot.titles[i]);
            item.put("artist", i < snapshot.artists.length ? snapshot.artists[i] : "");
            item.put("artwork", i < snapshot.artwork.length ? bitmapDataUrl(snapshot.artwork[i]) : "");
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
        getActivity().runOnUiThread(() -> {
            String command=call.getString("command", "toggle");
            boolean success=false;
            try {
                MediaBridge.refresh(getContext());
                if("favorite".equals(command))success=MediaBridge.toggleFavorite(getContext());
                else if("queue".equals(command))success=MediaBridge.playQueueItem(getContext(),call.getString("queueId"));
                else if("volume".equals(command)){
                    AudioManager audio=(AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
                    if(audio!=null&&!audio.isVolumeFixed()){
                        int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        int value=Math.max(0,Math.min(100,call.getInt("positionMs",50)));
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.round(max*value/100f),0);success=true;
                    }
                } else if("previous".equals(command)&&MediaBridge.supports(android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS)){MediaBridge.previous(getContext());success=true;}
                else if("next".equals(command)&&MediaBridge.supports(android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT)){MediaBridge.next(getContext());success=true;}
                else if("seek".equals(command)&&MediaBridge.supports(android.media.session.PlaybackState.ACTION_SEEK_TO)){MediaBridge.seekTo(getContext(),call.getLong("positionMs",0L));success=true;}
                else if("toggle".equals(command)){
                    long action=MediaBridge.playing?android.media.session.PlaybackState.ACTION_PAUSE:android.media.session.PlaybackState.ACTION_PLAY;
                    if(MediaBridge.supports(action)||MediaBridge.supports(android.media.session.PlaybackState.ACTION_PLAY_PAUSE)){MediaBridge.toggle(getContext());success=true;}
                }
                JSObject result=new JSObject();result.put("success",success);result.put("liked",MediaBridge.liked);
                if(!success)result.put("message","This player does not expose this action. Open the player to use it.");
                call.resolve(result);
            } catch(Exception e){call.reject("Media action unavailable",e);}
        });
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
        result.put("ageMs",ObdBridge.lastUpdate==0?null:android.os.SystemClock.elapsedRealtime()-ObdBridge.lastUpdate);
        result.put("diagnostics",ObdBridge.diagnostics());
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
            ensureLocationUpdates();
            LocationManager manager = (LocationManager)getContext().getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String provider : manager.getProviders(true)) {
                Location item = manager.getLastKnownLocation(provider);
                if (item != null && (best == null || item.getTime() > best.getTime())) best = item;
            }
            if (best == null || android.os.SystemClock.elapsedRealtimeNanos()-best.getElapsedRealtimeNanos()>120000000000L) { call.reject("Waiting for GPS fix"); return; }
            JSObject result = new JSObject();
            result.put("latitude", best.getLatitude());
            result.put("longitude", best.getLongitude());
            result.put("speedMph", Math.max(0, best.getSpeed() * 2.236936));
            call.resolve(result);
        } catch (Throwable error) { call.reject("Location unavailable", error.getMessage()); }
    }

    @PluginMethod public void getWeather(PluginCall call) {
        Double lat=call.getDouble("latitude"),lon=call.getDouble("longitude");
        if(lat==null||lon==null){
            if(getPermissionState("location")!=com.getcapacitor.PermissionState.GRANTED){call.reject("Location permission required");return;}
            try {
                LocationManager manager=(LocationManager)getContext().getSystemService(Context.LOCATION_SERVICE);
                Location best=null;
                for(String provider:manager.getProviders(true)){
                    Location item=manager.getLastKnownLocation(provider);
                    if(item!=null&&(best==null||item.getTime()>best.getTime()))best=item;
                }
                if(best!=null&&System.currentTimeMillis()-best.getTime()<1800000L){lat=best.getLatitude();lon=best.getLongitude();}
            }catch(SecurityException ignored){}
        }
        if(lat==null||lon==null||Math.abs(lat)>90||Math.abs(lon)>180){call.reject("Location unavailable");return;}
        final double weatherLat=lat,weatherLon=lon;
        new Thread(() -> {
            java.net.HttpURLConnection connection=null;
            try {
                String endpoint=String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&temperature_unit=fahrenheit",weatherLat,weatherLon);
                connection=(java.net.HttpURLConnection)new java.net.URL(endpoint).openConnection();
                connection.setConnectTimeout(7000);connection.setReadTimeout(7000);
                if(connection.getResponseCode()!=200)throw new IllegalStateException("Weather service unavailable");
                byte[] body;
                try(java.io.InputStream stream=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[4096];int count;
                    while((count=stream.read(buffer))!=-1)output.write(buffer,0,count);
                    body=output.toByteArray();
                }
                org.json.JSONObject current=new org.json.JSONObject(new String(body,java.nio.charset.StandardCharsets.UTF_8)).getJSONObject("current");
                int code=current.getInt("weather_code");
                JSObject result=new JSObject();result.put("temperature",Math.round(current.getDouble("temperature_2m")));
                result.put("condition",code==0?"CLEAR":code<=3?"PARTLY CLOUDY":code<=67?"RAIN":code<=77?"SNOW":"STORMS");
                call.resolve(result);
            }catch(Throwable error){call.reject("Weather service unavailable",error.getMessage());}
            finally{if(connection!=null)connection.disconnect();}
        }).start();
    }

    @PluginMethod public void openNavigation(PluginCall call) {
        Intent intent = new Intent(getContext(), NavigationActivity.class);
        intent.putExtra("destination", call.getString("destination", ""));
        String placeId=call.getString("placeId");
        if(placeId!=null&&!placeId.trim().isEmpty())intent.putExtra("placeId",placeId);
        intent.putExtra("theme",call.getString("theme","titanium"));
        intent.putExtra("surface",call.getString("surface","charcoal"));
        intent.putExtra("accentColor",call.getString("accentColor","#f28a32"));
        Integer accentStrength=call.getInt("accentStrength");
        if(accentStrength!=null)intent.putExtra("accentStrength",accentStrength);
        intent.putExtra("routingStrategy", call.getString("routingStrategy", "fastest"));
        intent.putExtra("avoidTolls", Boolean.TRUE.equals(call.getBoolean("avoidTolls", false)));
        intent.putExtra("mapMode", call.getString("mapMode", "standard"));
        intent.putExtra("dayMode",Boolean.TRUE.equals(call.getBoolean("dayMode",false)));
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
