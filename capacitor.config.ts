import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.diaztradeinc.trxlauncher',
  appName: 'TRX APEX',
  webDir: 'dist',
  android: {
    backgroundColor: '#252a2e',
    allowMixedContent: false,
    webContentsDebuggingEnabled: false,
    captureInput: true,
    backgroundColorTop: '#252a2e',
    backgroundColorBottom: '#252a2e',
    orientation: 'portrait',
    hideSplashScreenOnLoad: true,
    splashScreen: {
      androidScaleType: 'CENTER_CROP',
      androidSplashResourceName: 'splash',
    },
  },
};

export default config;
