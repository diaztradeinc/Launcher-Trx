package com.diaztradeinc.trxlauncher;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/** Opt-in global Back action. Does not inspect other apps or their content. */
public class TrxBackService extends AccessibilityService {
    private static volatile TrxBackService active;
    private static volatile String foregroundPackage="";

    static boolean pressBack() {
        TrxBackService service=active;
        return service!=null && service.performGlobalAction(GLOBAL_ACTION_BACK);
    }
    static boolean ready(){return active!=null;}
    static String foregroundPackage(){return foregroundPackage;}

    @Override protected void onServiceConnected(){super.onServiceConnected();active=this;}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null||event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||event.getPackageName()==null)return;
        String pkg=event.getPackageName().toString();
        if(!pkg.equals(getPackageName())&&!pkg.equals("android")&&!pkg.startsWith("com.android.systemui"))foregroundPackage=pkg;
    }
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){active=null;super.onDestroy();}
}
