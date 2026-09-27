package com.diaztradeinc.trxlauncher;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;

/** Opt-in global Back action. Does not inspect other apps or their content. */
public class TrxBackService extends AccessibilityService {
    private static volatile TrxBackService active;
    private static volatile String foregroundPackage="";
    private String currentWindowPackage="";

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

    interface PairResult { void complete(String message,boolean error); }
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String pairFirst,pairSecond;
    private PairResult pairResult;
    private int pairStage;
    private final Runnable pairTimeout=() -> finishPair("Android did not finish opening the selected pair. Use Recents → Split screen on this device.",true);

    static void startPair(String first,String second,boolean alreadySplit,PairResult result){
        TrxBackService service=active;
        if(service==null){result.complete("TRX APEX Back service must be connected to pair apps. Check Accessibility in Settings.",true);return;}
        if(service.pairResult!=null){result.complete("An app pair is already opening.",true);return;}
        service.pairFirst=first;service.pairSecond=second;service.pairResult=result;service.pairStage=0;
        service.handler.postDelayed(service.pairTimeout,10000);
        // Leave a launcher-owned split before anchoring the new split to the first app.
        if(alreadySplit&&!service.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)){
            service.finishPair("Android could not leave the current split. Close it before opening the selected pair.",true);return;
        }
        service.handler.postDelayed(service::launchFirst,alreadySplit?650:0);
    }
    private void launchFirst(){
        if(pairResult==null)return;
        try{
            Intent intent=getPackageManager().getLaunchIntentForPackage(pairFirst);
            if(intent==null){finishPair("The first app is no longer available.",true);return;}
            pairStage=1;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }catch(RuntimeException error){finishPair("Android could not open the first app.",true);}
    }
    private void splitFirst(){
        if(pairResult==null||pairStage!=2)return;
        // The first selected app has reported its own foreground window. Never split the launcher.
        if(!pairFirst.equals(currentWindowPackage)){finishPair("Pairing stopped because the foreground app changed.",true);return;}
        if(!performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)){
            finishPair("This device declined the split-screen action. Use Recents → Split screen to choose the second app.",true);return;
        }
        handler.postDelayed(() -> {
            if(pairResult==null||pairStage!=2)return;
            try{
                Intent intent=getPackageManager().getLaunchIntentForPackage(pairSecond);
                if(intent==null){finishPair("The second app is no longer available.",true);return;}
                pairStage=3;
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
                startActivity(intent);
            }catch(RuntimeException error){finishPair("Android could not open the second app.",true);}
        },650);
    }
    private void finishPair(String message,boolean error){
        PairResult result=pairResult;pairResult=null;pairStage=0;pairFirst=null;pairSecond=null;
        handler.removeCallbacksAndMessages(null);
        if(result!=null)result.complete(message,error);
    }

    @Override protected void onServiceConnected(){super.onServiceConnected();active=this;}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null||event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||event.getPackageName()==null)return;
        String pkg=event.getPackageName().toString();
        if(!pkg.equals("android")&&!pkg.startsWith("com.android.systemui")){
            currentWindowPackage=pkg;
            if(!pkg.equals(getPackageName()))foregroundPackage=pkg;
        }
        if(pairResult!=null&&pairStage==1&&pkg.equals(pairFirst)){
            pairStage=2;handler.postDelayed(this::splitFirst,250);
        }else if(pairResult!=null&&pairStage==3&&pkg.equals(pairSecond)){
            finishPair("Both selected apps opened; Android controls whether they can remain side by side.",false);
        }
    }
    @Override public void onInterrupt(){}
    @Override public boolean onUnbind(Intent intent){finishPair("Back service disconnected during pairing.",true);if(active==this)active=null;return super.onUnbind(intent);}
    @Override public void onDestroy(){finishPair("Back service stopped during pairing.",true);if(active==this)active=null;super.onDestroy();}
}
