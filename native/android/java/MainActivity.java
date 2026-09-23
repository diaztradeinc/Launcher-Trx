package com.diaztradeinc.trxlauncher;

import android.os.Bundle;
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
        ObdBridge.start(this);
        MediaBridge.ensureConnected(this);
    }
}
