package com.diaztradeinc.trxlauncher;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.widget.Toast;

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

    static boolean automaticPairAvailable(){
        TrxBackService service=active;
        try{return service!=null&&service.supportsSplitAction();}catch(RuntimeException ignored){return false;}
    }
    private boolean supportsSplitAction(){
        if(Build.VERSION.SDK_INT<30)return true;
        for(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction action:getSystemActions())
            if(action.getId()==GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)return true;
        return false;
    }
    private String appLabel(String pkg){
        try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}
        catch(Exception ignored){return pkg;}
    }
    private void openOttocastPairPicker(){
        // Ottocast's navigation bar owns the long-press Back split-screen action.
        // Leave the first selected app in front so its picker docks that app,
        // rather than accidentally splitting APEX or a Recents preview.
        String message=appLabel(pairFirst)+" is ready. Hold Ottocast Back, then choose "+appLabel(pairSecond)+".";
        Toast.makeText(this,message,Toast.LENGTH_LONG).show();
        finishPair("Finish in Ottocast: "+message,false);
    }
    interface PairResult { void complete(String message,boolean error); }
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String pairFirst,pairSecond;
    private PairResult pairResult;
    private int pairStage;
    private boolean manualPair;
    private final Runnable pairTimeout=() -> finishPair("Pair setup timed out. Open the first app, hold Ottocast Back, and choose the second app.",true);

    static void startPair(String first,String second,boolean manual,boolean alreadySplit,PairResult result){
        TrxBackService service=active;
        if(service==null){result.complete("TRX APEX Back service must be connected to pair apps. Check Accessibility in Settings.",true);return;}
        if(service.pairResult!=null){result.complete("An app pair is already opening.",true);return;}
        if(first.equals(second)||first.equals(service.getPackageName())||second.equals(service.getPackageName())){
            result.complete("Choose two different external apps.",true);return;
        }
        if(alreadySplit){
            result.complete("Close the current split, then choose the pair again. This keeps TRX APEX out of the selected pair.",true);return;
        }
        service.pairFirst=first;service.pairSecond=second;service.pairResult=result;service.pairStage=0;
        service.handler.postDelayed(service.pairTimeout,20000);
        service.manualPair=manual;
        // Seed the second task too: the old fallback only ever opened the first app.
        service.handler.post(manual?service::prepareSecond:service::launchFirst);
    }
    private void prepareSecond(){
        if(pairResult==null)return;
        try{
            Intent intent=getPackageManager().getLaunchIntentForPackage(pairSecond);
            if(intent==null){finishPair("The second app is no longer available.",true);return;}
            pairStage=4;intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }catch(RuntimeException error){finishPair("Android could not prepare the second app.",true);}
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
        if(manualPair){openOttocastPairPicker();return;}
        if(!supportsSplitAction()||!performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)){
            manualPair=true;prepareSecond();return;
        }
        handler.postDelayed(() -> {
            if(pairResult==null||pairStage!=2)return;
            try{
                Intent intent=getPackageManager().getLaunchIntentForPackage(pairSecond);
                if(intent==null){finishPair("The second app is no longer available.",true);return;}
                pairStage=3;
                // The first app is already docked by the OS. An adjacent flag from our
                // service has no activity anchor and can reintroduce the launcher task.
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(intent);
            }catch(RuntimeException error){finishPair("Android could not open the second app.",true);}
        },650);
    }
    private void finishPair(String message,boolean error){
        if(pairResult!=null)getSharedPreferences("launcher",Context.MODE_PRIVATE).edit().putString("last_pair_status",message).apply();
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
        if(pairResult!=null&&pairStage==4&&pkg.equals(pairSecond)){
            pairStage=5;handler.postDelayed(this::launchFirst,350);
        }else if(pairResult!=null&&pairStage==1&&pkg.equals(pairFirst)){
            pairStage=2;handler.postDelayed(this::splitFirst,250);
        }else if(pairResult!=null&&pairStage==3&&pkg.equals(pairSecond)){
            finishPair("Opened "+appLabel(pairFirst)+" and "+appLabel(pairSecond)+". Split placement is not verified.",false);
        }
    }
    @Override public void onInterrupt(){}
    @Override public boolean onUnbind(Intent intent){finishPair("Back service disconnected during pairing.",true);if(active==this)active=null;return super.onUnbind(intent);}
    @Override public void onDestroy(){finishPair("Back service stopped during pairing.",true);if(active==this)active=null;super.onDestroy();}
}
