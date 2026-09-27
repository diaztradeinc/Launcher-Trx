package com.diaztradeinc.trxlauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingRailService extends Service {
    private static final String CHANNEL = "trx_apex_rail";
    private static volatile FloatingRailService instance;
    private static volatile boolean launcherVisible;
    private WindowManager manager;
    private View rail;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean expanded=false;
    private boolean backPending=false;
    private final Runnable retract=() -> setExpanded(false);

    private void setExpanded(boolean value){
        handler.removeCallbacks(retract);
        expanded=value;
        if(manager!=null&&rail!=null){manager.removeView(rail);rail=null;}
        showRail();
        rail.setVisibility(launcherVisible?View.GONE:View.VISIBLE);
        if(value)handler.postDelayed(retract,5000);
    }
    private int accentColor = 0xfff04450;
    private int surfaceColor = 0xff1b2426;
    private int buttonHeightDp = 44;

    @Override public void onCreate() {
        super.onCreate();
        instance=this;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager notifications = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            notifications.createNotificationChannel(new NotificationChannel(CHANNEL,"TRX APEX floating rail",NotificationManager.IMPORTANCE_LOW));
        }
        Intent home = new Intent(this,MainActivity.class).putExtra("apexPage","home").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(this,0,home,PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("TRX APEX rail is on")
            .setContentText("Tap to open TRX APEX; disable in launcher settings")
            .setContentIntent(open).setOngoing(true).build();
        startForeground(524,notification);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
        int newAccent=accentColor,newSurface=surfaceColor;
        try{if(intent!=null)newAccent=Color.parseColor(intent.getStringExtra("accentColor"));}catch(Throwable ignored){}
        if(intent!=null){String surface=intent.getStringExtra("surface");
            if("black".equals(surface))newSurface=0xff060809;
            else if("dark".equals(surface))newSurface=0xff111a20;
            else if("charcoal".equals(surface))newSurface=0xff30383d;
            else newSurface=0xff1b2426;
        }
        if(rail!=null&&(newAccent!=accentColor||newSurface!=surfaceColor)){
            manager.removeView(rail);rail=null;
        }
        accentColor=newAccent;surfaceColor=newSurface;
        if(backPending)return START_STICKY;
        if (rail == null) showRail();
        rail.setVisibility(launcherVisible?View.GONE:View.VISIBLE);
        return START_STICKY;
    }

    public static void setLauncherVisible(boolean visible){
        launcherVisible=visible;
        FloatingRailService service=instance;
        if(service!=null&&service.rail!=null)service.rail.setVisibility(visible?View.GONE:View.VISIBLE);
    }

    private void showRail() {
        manager = (WindowManager)getSystemService(WINDOW_SERVICE);
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(dp(expanded?8:3),dp(4),dp(expanded?8:3),dp(4));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{surfaceColor,0xf804080a});
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1),expanded?0xff536168:accentColor);
        dock.setElevation(dp(8));
        dock.setBackground(background);
        if(expanded){
        button("⌂","Home",dock).setOnClickListener(v -> open("home",false));
        TextView split=button("◫","Split screen",dock);
        split.setTextColor(accentColor);
        split.setOnClickListener(v -> open("apps",true));
        button("←","Back",dock).setOnClickListener(v -> {
            dispatchSystemBack();
        });
        }else{
            TextView handle=button("⌃","",dock);
            handle.setContentDescription("Expand Home, Split screen and Back controls");
            handle.setOnClickListener(v -> setExpanded(true));
        }
        rail = dock;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(expanded?WindowManager.LayoutParams.MATCH_PARENT:dp(48),dp(52),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.BOTTOM | (expanded?Gravity.CENTER_HORIZONTAL:Gravity.RIGHT);
        lp.y=expanded?0:dp(96);
        manager.addView(rail,lp);
    }

    private TextView button(String icon,String description,LinearLayout container) {
        TextView view = new TextView(this);
        view.setText(description);
        RailIconDrawable glyph=new RailIconDrawable(icon,accentColor);glyph.setBounds(0,0,dp(22),dp(22));
        view.setCompoundDrawables(null,glyph,null,null);view.setCompoundDrawablePadding(dp(2));view.setContentDescription(description);view.setTextColor(Color.WHITE);
        view.setTextSize(11);view.setGravity(Gravity.CENTER);view.setSingleLine(true);
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{surfaceColor,0xff060a0c});
        bg.setCornerRadius(dp(9));bg.setStroke(dp(1),0x785d7077);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf((accentColor & 0x00ffffff)|0x44000000),bg,null));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(buttonHeightDp),1);
        params.leftMargin=dp(3);params.rightMargin=dp(3);
        container.addView(view,params);
        return view;
    }

    private void dispatchSystemBack(){
        if(backPending)return;
        backPending=true;
        handler.removeCallbacks(retract);
        // Detach the touched overlay before asking Android to target the app beneath it.
        if(manager!=null&&rail!=null){manager.removeViewImmediate(rail);rail=null;}
        expanded=false;
        handler.postDelayed(() -> {
            boolean handled=TrxBackService.pressBack();
            if(!handled)Toast.makeText(this,TrxBackService.status(this),Toast.LENGTH_LONG).show();
            // Never retry automatically: one tap must produce at most one system Back.
            handler.postDelayed(() -> {backPending=false;if(rail==null&&Settings.canDrawOverlays(this)){showRail();rail.setVisibility(launcherVisible?View.GONE:View.VISIBLE);}},220);
        },120);
    }

    private void open(String page,boolean split) {
        setExpanded(false);
        Intent intent=new Intent(this,MainActivity.class);
        intent.putExtra("apexPage",page);
        if(split)intent.putExtra("apexSplitFirst",TrxBackService.foregroundPackage());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if(manager!=null&&rail!=null){try{manager.removeView(rail);}catch(RuntimeException ignored){}rail=null;}
        if(instance==this)instance=null;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
