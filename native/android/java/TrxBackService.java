package com.diaztradeinc.trxlauncher;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.provider.Settings;

/** Opt-in global Back action. Does not inspect other apps or their content. */
public class TrxBackService extends AccessibilityService {
    private static volatile TrxBackService active;
    private static volatile String foregroundPackage="";

    private static volatile String lastResult="Not tested";
    static String status(Context context){
        if(active!=null)return "Connected · "+lastResult;
        String enabled=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if(enabled!=null)for(String entry:enabled.split(":")){ComponentName name=ComponentName.unflattenFromString(entry);if(new ComponentName(context,TrxBackService.class).equals(name))return "Enabled, but service disconnected · toggle TRX APEX Back off and on";}
        return "Enable TRX APEX Back in Accessibility";
    }
    static boolean pressBack() {
        TrxBackService service=active;
        if(service==null){lastResult="Service disconnected";return false;}
        try{boolean result=service.performGlobalAction(GLOBAL_ACTION_BACK);lastResult=result?"Last Back accepted by Android":"Last Back rejected by Android";return result;}catch(RuntimeException error){lastResult="Back connection unavailable";return false;}
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
    @Override public boolean onUnbind(Intent intent){if(active==this)active=null;return super.onUnbind(intent);}
    @Override public void onDestroy(){if(active==this)active=null;super.onDestroy();}
}
