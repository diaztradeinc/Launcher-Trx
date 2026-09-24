package com.diaztradeinc.trxlauncher;
import android.content.Context;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.MapStyleOptions;
final class ApexMapStyle {
    static void apply(Context context,GoogleMap map,boolean day){
        // Roads and attribution remain SDK content. Never use a raster mockup as a map.
        map.setMapStyle(MapStyleOptions.loadRawResourceStyle(context,day?R.raw.apex_navigation_day:R.raw.apex_navigation_style));
    }
}
