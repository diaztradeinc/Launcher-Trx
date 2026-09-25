package com.diaztradeinc.trxlauncher;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/** Opt-in global Back action. Does not inspect other apps or their content. */
public class TrxBackService extends AccessibilityService {
    private static volatile TrxBackService active;

    static boolean pressBack() {
        TrxBackService service=active;
        return service!=null && service.performGlobalAction(GLOBAL_ACTION_BACK);
    }
    static boolean ready(){return active!=null;}

    @Override protected void onServiceConnected(){super.onServiceConnected();active=this;}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){active=null;super.onDestroy();}
}
