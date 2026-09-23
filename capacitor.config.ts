import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.diaztradeinc.trxlauncher',
  appName: 'TRX APEX',
  webDir: 'dist',
  android: {
    backgroundColor: '#07090a',
  allowMixedContent: false,
  webContentsDebuggingEnabled: false,
  captureInput: true,
  backgroundColorTop: '#07090a',
    backgroundColorBottom: '#07090a',
  orientation: 'portrait',
  hideSplashScreenOnLoad: true,
  splashScreen: {
      androidScaleType: 'CENTER_CROP',
      androidSplashResourceName: 'splash',
    },
  },
};

export default config;
