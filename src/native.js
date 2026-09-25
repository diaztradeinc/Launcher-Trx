import { Capacitor, registerPlugin } from '@capacitor/core';

const plugin = registerPlugin('TrxNative');
const unavailable = {success:false,error:'This action requires the Android launcher.'};
export const isNative = Capacitor.isNativePlatform();

async function safe(method, args, fallback) {
  if (import.meta.env.DEV && window.__APEX_TEST_BRIDGE__?.[method]) return window.__APEX_TEST_BRIDGE__[method](args || {});
  if (!isNative || !plugin[method]) return fallback;
  try { return await plugin[method](args || {}); }
  catch (error) { console.warn('TRX native bridge:', method, error); return fallback && typeof fallback === 'object' ? {...fallback,error:error?.message || 'Action unavailable'} : fallback; }
}

export const native = {
  mapPreview: (args) => safe('mapPreview', args, {error:'Live map available on Android'}),
  displayInfo: () => safe('getDisplayInfo', {}, null),
  requestPermissionGroup: (group) => safe('requestPermissionGroup', { group }, { granted: false }),
  apps: () => safe('getInstalledApps', {}, { apps: [] }),
  launchApp: (packageName) => safe('launchApp', { packageName }, unavailable),
  appAction: (packageName, action) => safe('appAction', { packageName, action }, unavailable),
  media: () => safe('getMediaState', {}, null),
  mediaCommand: (command, positionMs, index, queueId) => safe('mediaCommand', { command, positionMs, index, queueId }, unavailable),
  spectrum: () => safe('getAudioSpectrum', {}, {available:false,reason:'Audio visualizer requires Android'}),
  visualizerAccess: () => safe('visualizerAccess', {}, {granted:false}),
  stopSpectrum: () => safe('stopAudioSpectrum', {}, {success:true}),
  launchAdjacent: (packageName) => safe('launchAdjacent', {packageName}, unavailable),
  startAppPair: (first,second) => safe('startAppPair', {first,second}, unavailable),
  floatingRail: (enabled, accentColor, surface) => safe('floatingRail', {enabled,accentColor,surface}, unavailable),
  railCapabilities: () => safe('railCapabilities', {}, {overlay:false,back:false}),
  railDestination: () => safe('consumeRailDestination', {}, {page:''}),
  obd: () => safe('getObdState', {}, null),
  reconnectObd: () => safe('reconnectObd', {}, unavailable),
  location: () => safe('getLocation', {}, null),
  searchDestinations: (query) => safe('searchDestinations', { query }, { suggestions: [] }),
  navigate: (destination, latitude, longitude, placeId, theme, accentColor, accentStrength, preferences = {}) => safe('openNavigation', {
    destination, latitude, longitude, placeId, theme, accentColor, accentStrength,
    routingStrategy: preferences.routingStrategy || 'fastest',
    avoidTolls: Boolean(preferences.avoidTolls),
    mapMode: preferences.mapMode || 'standard',
    audioEnabled: preferences.audioEnabled !== false, dayMode: Boolean(preferences.dayMode),
    surface: preferences.surface || 'charcoal'
  }, unavailable),
  settings: (target) => safe('openSystemSettings', { target }, unavailable)
};

export async function currentWeather(location) {
  if (isNative) {
    const result=await safe('getWeather',location || {},null);
    if (Number.isFinite(result?.temperature)) return result;
  }
  if (!Number.isFinite(location?.latitude) || !Number.isFinite(location?.longitude)) return null;
  try {
    const url = 'https://api.open-meteo.com/v1/forecast?latitude=' + location.latitude +
      '&longitude=' + location.longitude + '&current=temperature_2m,weather_code&temperature_unit=fahrenheit';
    const controller=new AbortController();const timeout=setTimeout(()=>controller.abort(),8000);
    let response;try {response=await fetch(url,{signal:controller.signal});}finally{clearTimeout(timeout);}
    if(!response.ok)return null;
    const data = await response.json();
    const code = data?.current?.weather_code;
    const condition = code === 0 ? 'CLEAR' : code <= 3 ? 'PARTLY CLOUDY' : code <= 67 ? 'RAIN' : code <= 77 ? 'SNOW' : 'STORMS';
    if (!Number.isFinite(data?.current?.temperature_2m)) return null;
    return { temperature: Math.round(data.current.temperature_2m), condition };
  } catch { return null; }
}
