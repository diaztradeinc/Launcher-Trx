import { Capacitor, registerPlugin } from '@capacitor/core';

const plugin = registerPlugin('TrxNative');
export const isNative = Capacitor.isNativePlatform();

async function safe(method, args, fallback) {
  if (!isNative || !plugin[method]) return fallback;
  try { return await plugin[method](args || {}); }
  catch (error) { console.warn('TRX native bridge:', method, error); return fallback; }
}

export const native = {
  requestPermissionGroup: (group) => safe('requestPermissionGroup', { group }, { granted: false }),
  apps: () => safe('getInstalledApps', {}, { apps: [] }),
  launchApp: (packageName) => safe('launchApp', { packageName }, null),
  appAction: (packageName, action) => safe('appAction', { packageName, action }, null),
  media: () => safe('getMediaState', {}, null),
  mediaCommand: (command, positionMs, index) => safe('mediaCommand', { command, positionMs, index }, null),
  obd: () => safe('getObdState', {}, null),
  reconnectObd: () => safe('reconnectObd', {}, null),
  location: () => safe('getLocation', {}, null),
  searchDestinations: (query) => safe('searchDestinations', { query }, { suggestions: [] }),
  navigate: (destination, latitude, longitude, placeId, theme, accentColor, accentStrength, preferences = {}) => safe('openNavigation', {
    destination, latitude, longitude, placeId, theme, accentColor, accentStrength,
    routingStrategy: preferences.routingStrategy || 'fastest',
    avoidTolls: Boolean(preferences.avoidTolls),
    mapMode: preferences.mapMode || 'standard',
    audioEnabled: preferences.audioEnabled !== false
  }, null),
  settings: (target) => safe('openSystemSettings', { target }, null)
};

export async function currentWeather(location) {
  if (!location?.latitude || !location?.longitude) return null;
  try {
    const url = 'https://api.open-meteo.com/v1/forecast?latitude=' + location.latitude +
      '&longitude=' + location.longitude + '&current=temperature_2m,weather_code&temperature_unit=fahrenheit';
    const response = await fetch(url);
    const data = await response.json();
    const code = data?.current?.weather_code;
    const condition = code === 0 ? 'CLEAR' : code <= 3 ? 'PARTLY CLOUDY' : code <= 67 ? 'RAIN' : code <= 77 ? 'SNOW' : 'STORMS';
    return { temperature: Math.round(data.current.temperature_2m), condition };
  } catch { return null; }
}
