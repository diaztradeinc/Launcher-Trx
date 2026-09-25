package com.diaztradeinc.trxlauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingRailService extends Service {
    private static final String CHANNEL = "trx_apex_rail";
    private WindowManager manager;
    private View rail;
    private boolean expanded = true;

    @Override public void onCreate() {
        super.onCreate();
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
        if (rail == null) showRail();
        return START_STICKY;
    }

    private void showRail() {
        manager = (WindowManager)getSystemService(WINDOW_SERVICE);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(4),dp(6),dp(4),dp(6));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xf50b0f13,0xf5222b31});
        background.setCornerRadii(new float[]{0,0,dp(16),dp(16),dp(16),dp(16),0,0});
        background.setStroke(dp(1),0xff6a747b);
        column.setBackground(background);
        TextView handle = button("❯","Expand or collapse rail",column);
        LinearLayout destinations = new LinearLayout(this);
        destinations.setOrientation(LinearLayout.VERTICAL);
        column.addView(destinations);
        button("⌂","Home",destinations).setOnClickListener(v -> open("home"));
        button("➤","Navigation",destinations).setOnClickListener(v -> open("navigation"));
        button("♫","Media",destinations).setOnClickListener(v -> open("media"));
        button("▦","Apps",destinations).setOnClickListener(v -> open("apps"));
        button("×","Close floating rail",destinations).setOnClickListener(v -> stopSelf());
        handle.setOnClickListener(v -> {
            expanded=!expanded;
            destinations.setVisibility(expanded?View.VISIBLE:View.GONE);
            handle.setText(expanded?"❮":"❯");
            manager.updateViewLayout(rail,rail.getLayoutParams());
        });
        rail = column;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(dp(49),WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.LEFT | Gravity.CENTER_VERTICAL;
        manager.addView(rail,lp);
    }

    private TextView button(String icon,String description,LinearLayout container) {
        TextView view = new TextView(this);
        view.setText(icon);view.setContentDescription(description);view.setTextColor(Color.WHITE);
        view.setTextSize(26);view.setGravity(Gravity.CENTER);
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff37434a,0xff111a20});
        bg.setCornerRadius(dp(12));bg.setStroke(dp(1),0xff788891);
        view.setBackground(bg);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(41),dp(46));
        params.bottomMargin=dp(6);container.addView(view,params);
        return view;
    }

    private void open(String page) {
        Intent intent=new Intent(this,MainActivity.class);
        intent.putExtra("apexPage",page);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onDestroy() {
        if(manager!=null&&rail!=null){try{manager.removeView(rail);}catch(RuntimeException ignored){}rail=null;}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
