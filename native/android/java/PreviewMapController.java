package com.diaztradeinc.trxlauncher;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.webkit.WebView;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;

/** Places the native, interactive Google map exactly over its CSS layout slot. */
final class PreviewMapController {
    private final Activity activity;
    private final WebView web;
    private MapView view;
    private FrameLayout container;
    private LinearLayout toolbar;
    private GoogleMap map;
    private boolean centered;
    private Double latitude,longitude;
    private String mapMode="standard",lastStyle="";
    private boolean day;
    PreviewMapController(Activity activity,WebView web){this.activity=activity;this.web=web;}
    void update(PluginCall call){
        activity.runOnUiThread(()->{
            try {
                if(!Boolean.TRUE.equals(call.getBoolean("visible",false))){if(container!=null)container.setVisibility(View.GONE);call.resolve();return;}
                FrameLayout root=activity.findViewById(android.R.id.content);
                if(view==null){
                    container=new FrameLayout(activity);
                    view=new MapView(activity);view.setBackgroundColor(Color.rgb(12,22,30));
                    container.addView(view,new FrameLayout.LayoutParams(-1,-1));
                    toolbar=new LinearLayout(activity);toolbar.setOrientation(LinearLayout.VERTICAL);
                    int dp=(int)(activity.getResources().getDisplayMetrics().density*50);
                    FrameLayout.LayoutParams toolParams=new FrameLayout.LayoutParams(dp,-2,android.view.Gravity.RIGHT|android.view.Gravity.TOP);
                    toolParams.topMargin=dp/3;toolParams.rightMargin=dp/6;
                    container.addView(toolbar,toolParams);
                    addControl("▱",()->{mapMode="satellite".equals(mapMode)?"standard":"satellite";apply();});
                    addControl("⌖",()->{if(map!=null&&latitude!=null&&longitude!=null)map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude,longitude),16f));});
                    addControl("3D",()->{if(map!=null){CameraPosition c=map.getCameraPosition();map.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder(c).tilt(c.tilt>15?0:55).build()));}});
                    addControl("+",()->{if(map!=null)map.animateCamera(CameraUpdateFactory.zoomIn());});
                    addControl("−",()->{if(map!=null)map.animateCamera(CameraUpdateFactory.zoomOut());});
                    root.addView(container);view.onCreate(null);view.onStart();view.onResume();
                    view.getMapAsync(g->{map=g;map.getUiSettings().setZoomControlsEnabled(false);map.getUiSettings().setMapToolbarEnabled(false);map.getUiSettings().setCompassEnabled(true);apply();});
                }
                latitude=call.getDouble("latitude");longitude=call.getDouble("longitude");mapMode=call.getString("mapMode","standard");day=Boolean.TRUE.equals(call.getBoolean("dayMode",false));
                toolbar.setVisibility(Boolean.TRUE.equals(call.getBoolean("controls",false))?View.VISIBLE:View.GONE);
                double cssWidth=call.getDouble("viewportWidth",602.0);double scale=web.getWidth()/Math.max(1.0,cssWidth);
                int[] webAt=new int[2],rootAt=new int[2];web.getLocationInWindow(webAt);root.getLocationInWindow(rootAt);
                int width=Math.max(1,(int)Math.round(call.getDouble("width",1.0)*scale));
                int height=Math.max(1,(int)Math.round(call.getDouble("height",1.0)*scale));
                FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(width,height);
                lp.leftMargin=webAt[0]-rootAt[0]+(int)Math.round(call.getDouble("x",0.0)*scale);
                lp.topMargin=webAt[1]-rootAt[1]+(int)Math.round(call.getDouble("y",0.0)*scale);
                container.setLayoutParams(lp);container.setVisibility(View.VISIBLE);container.bringToFront();apply();
                JSObject result=new JSObject();result.put("success",true);call.resolve(result);
            }catch(Exception e){if(container!=null)container.setVisibility(View.GONE);call.reject("Live map could not load",e);}
        });
    }
    private void addControl(String label,Runnable action){
        TextView button=new TextView(activity);button.setText(label);button.setTextColor(Color.WHITE);
        button.setTextSize(21);button.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(18,29,37),Color.rgb(4,8,12)});
        background.setCornerRadius(activity.getResources().getDisplayMetrics().density*11);
        background.setStroke((int)Math.max(1,activity.getResources().getDisplayMetrics().density),Color.rgb(110,132,141));
        button.setBackground(background);
        int side=(int)(activity.getResources().getDisplayMetrics().density*49);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,side);params.bottomMargin=side/9;
        toolbar.addView(button,params);button.setOnClickListener(v->action.run());
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
    void destroy(){if(view!=null){ViewGroup parent=(ViewGroup)container.getParent();if(parent!=null)parent.removeView(container);view.onDestroy();view=null;container=null;toolbar=null;map=null;}}
}
