package com.diaztradeinc.trxlauncher;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.webkit.WebView;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;

/** Places the native, interactive Google map exactly over its CSS layout slot. */
final class PreviewMapController {
    private final Activity activity;
    private final WebView web;
    private MapView view;
    private GoogleMap map;
    private boolean centered;
    private Double latitude,longitude;
    private String mapMode="standard",lastStyle="";
    private boolean day;
    PreviewMapController(Activity activity,WebView web){this.activity=activity;this.web=web;}
    void update(PluginCall call){
        activity.runOnUiThread(()->{
            try {
                if(!Boolean.TRUE.equals(call.getBoolean("visible",false))){if(view!=null)view.setVisibility(View.GONE);call.resolve();return;}
                FrameLayout root=activity.findViewById(android.R.id.content);
                if(view==null){view=new MapView(activity);view.setBackgroundColor(Color.rgb(12,22,30));view.onCreate(null);root.addView(view);view.onStart();view.onResume();view.getMapAsync(g->{map=g;map.getUiSettings().setZoomControlsEnabled(false);map.getUiSettings().setMapToolbarEnabled(false);map.getUiSettings().setCompassEnabled(true);apply();});}
                latitude=call.getDouble("latitude");longitude=call.getDouble("longitude");mapMode=call.getString("mapMode","standard");day=Boolean.TRUE.equals(call.getBoolean("dayMode",false));
                double cssWidth=call.getDouble("viewportWidth",602.0);double scale=web.getWidth()/Math.max(1.0,cssWidth);
                int[] webAt=new int[2],rootAt=new int[2];web.getLocationInWindow(webAt);root.getLocationInWindow(rootAt);
                int width=Math.max(1,(int)Math.round(call.getDouble("width",1.0)*scale));
                int height=Math.max(1,(int)Math.round(call.getDouble("height",1.0)*scale));
                FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(width,height);
                lp.leftMargin=webAt[0]-rootAt[0]+(int)Math.round(call.getDouble("x",0.0)*scale);
                lp.topMargin=webAt[1]-rootAt[1]+(int)Math.round(call.getDouble("y",0.0)*scale);
                view.setLayoutParams(lp);view.setVisibility(View.VISIBLE);view.bringToFront();apply();
                JSObject result=new JSObject();result.put("success",true);call.resolve(result);
            }catch(Exception e){if(view!=null)view.setVisibility(View.GONE);call.reject("Live map could not load",e);}
        });
    }
    private void apply(){
        if(map==null)return;
        map.setMapType("satellite".equals(mapMode)?GoogleMap.MAP_TYPE_HYBRID:GoogleMap.MAP_TYPE_NORMAL);
        String style=day?"day":"night";
        if(!style.equals(lastStyle)){ApexMapStyle.apply(activity,map,day);lastStyle=style;}
        map.setBuildingsEnabled(true);
        try{map.setMyLocationEnabled(true);}catch(SecurityException ignored){}
        if(!centered&&latitude!=null&&longitude!=null){map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude,longitude),14f));centered=true;}
    }
    void resume(){if(view!=null){view.onStart();view.onResume();}}
    void pause(){if(view!=null){view.onPause();view.onStop();}}
    void destroy(){if(view!=null){ViewGroup parent=(ViewGroup)view.getParent();if(parent!=null)parent.removeView(view);view.onDestroy();view=null;map=null;}}
}
