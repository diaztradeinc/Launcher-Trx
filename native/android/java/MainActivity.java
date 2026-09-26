package com.diaztradeinc.trxlauncher;

import android.os.Bundle;
import android.content.Intent;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override public void onCreate(Bundle savedInstanceState) {
        registerPlugin(TrxNativePlugin.class);
        super.onCreate(savedInstanceState);
        ObdBridge.start(this);
        MediaBridge.ensureConnected(this);
    }

    @Override public void onResume() {
        super.onResume();
        FloatingRailService.setLauncherVisible(true);
        ObdBridge.start(this);
        MediaBridge.ensureConnected(this);
    }
    @Override public void onPause() {
        FloatingRailService.setLauncherVisible(false);
        super.onPause();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }
}
