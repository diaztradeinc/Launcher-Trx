import { StrictMode, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { StatusBar as NativeStatusBar, Style } from '@capacitor/status-bar';
import {
  Activity, Aperture, Bluetooth, Check, ChevronRight, CloudSun, Gauge, Grid2X2,
  Heart, Home, Layers3, LocateFixed, Map, MapPin, Mic, Moon, Music2, Navigation,
  Pause, Play, Radio, Search, Settings, ShieldCheck, Signal, SkipBack, SkipForward,
  SlidersHorizontal, Sparkles, Sun, Volume2, Wifi
} from 'lucide-react';
import { currentWeather, native } from './native';
import './styles.css';

const NAV = [
  ['home', 'Home', Home], ['navigation', 'Navigate', Navigation],
  ['media', 'Media', Music2], ['performance', 'Dynamics', Gauge],
  ['apps', 'Apps', Grid2X2], ['settings', 'Studio', Settings]
];

const THEMES = {
  titanium: { name: 'Titanium Ember', accent: '#f28a32', signal: '#82e6e1' },
  hellfire: { name: 'Hellfire Red', accent: '#f12d31', signal: '#ffab9b' },
  baja: { name: 'Baja Sand', accent: '#d5aa63', signal: '#8be1cb' },
  arctic: { name: 'Arctic Signal', accent: '#49bdf2', signal: '#b7efff' },
  night: { name: 'Night Ops', accent: '#b9c1c5', signal: '#79efb1' }
};

const APP_LIST = [
  ['Maps', '🗺️'], ['Spotify', '●'], ['Phone', '☎'], ['Waze', '◉'],
  ['YouTube', '▶'], ['OBDLink', 'MX'], ['Chrome', '◎'], ['VLC', '▲'],
  ['Settings', '⚙'], ['Camera', '◍'], ['Messages', '•••'], ['Files', '▰'],
  ['Weather', '☀'], ['Play Store', '▷'], ['Netflix', 'N']
];

function readStoredJson(key, fallback) {
  try { const value = JSON.parse(localStorage.getItem(key)); return value ?? fallback; }
  catch (_) { return fallback; }
}

function useVehicleData() {
  const [media, setMedia] = useState(null);
  const [obd, setObd] = useState(null);
  const [location, setLocation] = useState(null);
  const [weather, setWeather] = useState(null);
  useEffect(function () {
    let alive = true;
    async function refresh() {
      const values = await Promise.all([native.media(), native.obd(), native.location()]);
      if (!alive) return;
      if (values[0]) setMedia(values[0]);
      if (values[1]) setObd(values[1]);
      if (values[2]) {
        setLocation(values[2]);
        const nextWeather = await currentWeather(values[2]);
        if (alive && nextWeather) setWeather(nextWeather);
      }
    }
    refresh();
    const timer = setInterval(refresh, 1800);
    return function () { alive = false; clearInterval(timer); };
  }, []);
  return { media, obd, location, weather };
}

function App() {
  const [active, setActive] = useState('home');
  const [commissioned, setCommissioned] = useState(function () {
    return localStorage.getItem('trx-apex-commissioned') === 'true';
  });
  const [theme, setTheme] = useState(localStorage.getItem('trx-apex-theme') || 'titanium');
  const [accent, setAccent] = useState(Number(localStorage.getItem('trx-apex-accent') || 82));
  const [iconScale, setIconScale] = useState(Number(localStorage.getItem('trx-apex-icons') || 100));
  const [reducedMotion, setReducedMotion] = useState(localStorage.getItem('trx-apex-motion') === 'true');
  const [displayMode, setDisplayMode] = useState('auto');
  const [now, setNow] = useState(new Date());
  const live = useVehicleData();

  useEffect(function () {
    NativeStatusBar.setOverlaysWebView({ overlay: false }).catch(function () {});
    NativeStatusBar.setStyle({ style: Style.Dark }).catch(function () {});
    NativeStatusBar.hide().catch(function () {});
  }, []);

  useEffect(function () {
    const timer = setInterval(function () { setNow(new Date()); }, 30000);
    return function () { clearInterval(timer); };
  }, []);

  useEffect(function () {
    localStorage.setItem('trx-apex-theme', theme);
    localStorage.setItem('trx-apex-accent', String(accent));
    localStorage.setItem('trx-apex-icons', String(iconScale));
    localStorage.setItem('trx-apex-motion', String(reducedMotion));
  }, [theme, accent, iconScale, reducedMotion]);

  const style = {
    '--accent': THEMES[theme].accent,
    '--signal': THEMES[theme].signal,
    '--accent-strength': accent / 100,
    '--icon-scale': iconScale / 100
  };

  function finishCommissioning() {
    localStorage.setItem('trx-apex-commissioned', 'true');
    setCommissioned(true);
  }

  if (!commissioned) return <Commissioning onComplete={finishCommissioning} />;

  return <div className={'apex-shell theme-' + theme + ' mode-' + displayMode + (reducedMotion ? ' reduce-motion' : '')} style={style}>
    <StatusBar now={now} live={live} />
    <CommandRail active={active} onNavigate={setActive} />
    <main className="apex-canvas" key={active}>
      {active === 'home' && <HomePage onNavigate={setActive} now={now} live={live} />}
      {active === 'navigation' && <NavigationPage live={live} theme={theme} accent={accent} />}
      {active === 'media' && <MediaPage media={live.media} />}
      {active === 'performance' && <PerformancePage obd={live.obd} />}
      {active === 'apps' && <AppsPage onOpenSettings={function () { setActive('settings'); }} />}
      {active === 'settings' && <SettingsPage theme={theme} setTheme={setTheme} accent={accent} setAccent={setAccent} iconScale={iconScale} setIconScale={setIconScale} reducedMotion={reducedMotion} setReducedMotion={setReducedMotion} displayMode={displayMode} setDisplayMode={setDisplayMode} obd={live.obd} />}
    </main>
  </div>;
}

function Commissioning({ onComplete }) {
  const [step, setStep] = useState(0);
  const nodes = [['Position', MapPin], ['Vehicle link', Bluetooth], ['Media', Music2], ['Launcher', Home]];

  async function request() {
    const current = nodes[step][0];
    try {
      const groups = { Position: 'location', 'Vehicle link': 'bluetooth', Media: 'media', Launcher: 'launcher' };
      await native.requestPermissionGroup(groups[current]);
    } catch {
      // Native Android permission state remains authoritative.
    }
    if (step < nodes.length - 1) setStep(step + 1);
    else onComplete();
  }

  return <div className="commissioning">
    <div className="commission-grid" />
    <div className="wire-truck"><img src="/trx-hero.webp" alt="Red RAM TRX" /><div className="scanline" /></div>
    <div className="commission-copy"><span>TRX COMMAND SYSTEM</span><h1>APEX</h1><p>VEHICLE INTERFACE · SYSTEM COMMISSIONING</p></div>
    <div className="orbit-system">
      <div className="orbit-core"><strong>{step + 1}</strong><span>OF 4</span></div>
      {nodes.map(function (item, index) {
        const label = item[0], Icon = item[1];
        return <button key={label} className={'orbit-node n' + index + (index < step ? ' done' : '') + (index === step ? ' current' : '')} onClick={function () { if (index === step) request(); }}>
          {index < step ? <Check /> : <Icon />}<span>{label}</span>
        </button>;
      })}
    </div>
    <button className="initialize" onClick={request}>{step === nodes.length - 1 ? 'INITIALIZE APEX' : 'AUTHORIZE ' + nodes[step][0].toUpperCase()}<ChevronRight /></button>
    <button className="setup-skip" onClick={onComplete}>Finish later</button>
  </div>;
}

function StatusBar({ now, live }) {
  const time = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  const latitude = live.location?.latitude?.toFixed(4) || '40.3573';
  const longitude = live.location?.longitude?.toFixed(4) || '74.6702';
  return <header className="status-bar">
    <div className="coordinates">{latitude}° N <span>{longitude}° W</span></div>
    <div className="apex-wordmark"><b>TRX</b><span>APEX</span></div>
    <div className="system-status"><Bluetooth /><Wifi /><Signal /><span>{time}</span><b>{live.weather?.temperature ?? 72}°</b></div>
  </header>;
}

function CommandRail({ active, onNavigate }) {
  return <nav className={'command-rail ' + active + '-active'} aria-label="Main navigation">
    <div className="ram-mark">RAM</div><div className="rail-line" />
    {NAV.map(function (item) {
      const id = item[0], label = item[1], Icon = item[2];
      return <button key={id} className={active === id ? 'active' : ''} onClick={function () { onNavigate(id); }} aria-label={label}>
        <Icon /><span>{label}</span>
      </button>;
    })}
    <div className="rail-pulse" />
  </nav>;
}

function PageTag({ index, title, subtitle, right }) {
  return <div className="page-tag"><div><span>{index}</span><h1>{title}</h1><small>{subtitle}</small></div>{right}</div>;
}

function HomePage({ onNavigate, now, live }) {
  const [quickOpen, setQuickOpen] = useState(false);
  const glyphs = ['🗺️', '●', '☎', '▶', 'MX'];
  return <section className="page home-page">
    <div className="home-hero">
      <img src="/trx-hero.webp" alt="Red RAM TRX in mountain terrain" />
      <div className="terrain-lines" />
      <div className="solar-arc"><Sun /><span>SUNRISE 6:12</span><i /><span>SUNSET 7:28</span></div>
      <div className="hero-time"><strong>{now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</strong><span>{now.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' }).toUpperCase()}</span></div>
      <div className="hero-weather"><CloudSun /><b>{live.weather?.temperature ?? 72}°F</b><span>LIVE LOCATION<br />{live.weather?.condition || 'WAITING FOR WEATHER'}</span></div>
      <div className="expedition-copy"><span>EXPEDITION 01</span><h2>Adventure<br />awaits.</h2></div>
      <div className="context-ribbon">
        <button className="media-context" onClick={function () { onNavigate('media'); }}><img src={live.media?.artwork || '/trx-radio.webp'} alt="Album artwork" /><span><small>NOW PLAYING</small><b>{live.media?.title || 'No active media'}</b><em>{live.media?.artist || 'Open a media app'}</em></span>{live.media?.playing ? <Pause /> : <Play />}</button>
        <button className="route-context" onClick={function () { onNavigate('navigation'); }}><Navigation /><span><small>NEXT MANEUVER</small><b>0.8 mi · Turn right</b><em>ETA 10:36 · 6.4 mi</em></span></button>
        <button className="vehicle-context" onClick={function () { onNavigate('performance'); }}><Activity /><span><small>VEHICLE LINK</small><b>{live.obd?.deviceName || 'OBDLINK MX+'}</b><em className="live">● {live.obd?.connected ? 'LIVE' : 'STANDBY'}</em></span></button>
      </div>
      <div className={'quick-orbit' + (quickOpen ? ' open' : '')}>
        <button className="quick-trigger" onClick={function () { setQuickOpen(!quickOpen); }}><Sparkles /></button>
        {glyphs.map(function (glyph, i) {
          return <button key={glyph + i} style={{ '--i': i }} onClick={function () { onNavigate(i === 4 ? 'performance' : i < 1 ? 'navigation' : i === 1 ? 'media' : 'apps'); }}>{glyph}</button>;
        })}
      </div>
      <div className="connection-strip"><i /> {live.location ? 'GPS LOCKED' : 'GPS WAITING'} <span /> OBDLINK MX+ <b>{live.obd?.connected ? 'LIVE' : 'CONNECTING'}</b> <span /> APEX NATIVE</div>
    </div>
  </section>;
}

function NavigationPage({ live, theme, accent }) {
  const [layer, setLayer] = useState('terrain');
  const [routing, setRouting] = useState(true);
  const [destination, setDestination] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [searchError, setSearchError] = useState('');
  const [selected, setSelected] = useState(null);
  useEffect(function () {
    const query = destination.trim();
    if (selected?.label === destination || query.length < 3) { setSuggestions([]); setSearchError(''); return; }
    let current = true;
    const timer = setTimeout(function () {
      native.searchDestinations(query).then(function (result) { if (current) { setSuggestions(result?.suggestions || []); setSearchError(result?.error || ''); } });
    }, 280);
    return function () { current = false; clearTimeout(timer); };
  }, [destination, selected]);
  function startRoute() {
    const match = selected?.label === destination ? selected : null;
    setSuggestions([]); setSearchError('');
    native.navigate(destination, match?.latitude, match?.longitude, match?.placeId, theme, THEMES[theme].accent, accent);
  }
  function selectSuggestion(item) { setDestination(item.label); setSelected(item); setSuggestions([]); setSearchError(''); }
  return <section className="page navigation-page">
    <div className="map-stage">
      <img src="/trx-map.webp" alt="Dimensional terrain route" />
      <div className="map-shade" /><div className="route-ribbon" /><div className="route-arrow">➤</div>
      <div className="nav-command"><Search /><input value={destination} onChange={function (e) { setDestination(e.target.value); setSelected(null); }} onKeyDown={function (e) { if (e.key === 'Enter') startRoute(); }} placeholder="Search destination or command" /><button onClick={startRoute} aria-label="Start navigation"><Navigation /></button><Mic />
        {(suggestions.length > 0 || searchError) && <div className="nav-suggestions">{searchError && <div className="places-error">{searchError}</div>}{suggestions.map(function (item, index) { return <button key={(item.placeId || item.label) + index} onClick={function () { selectSuggestion(item); }}><MapPin /><span><b>{item.primary || item.label}</b><small>{item.secondary}</small></span><ChevronRight /></button>; })}<div className="places-credit"><span>Google</span> Places</div></div>}
      </div>
      <div className="maneuver"><span>NEXT TURN</span><strong>0.8<small>mi</small></strong><p>Turn right onto<br /><b>Darlington Dr</b></p></div>
      <div className="lane-guidance"><i>↑</i><i className="active">↗</i><i>↑</i><span>KEEP RIGHT</span></div>
      <div className="arrival"><span>ARRIVAL</span><strong>10:36</strong><small>12 min · 6.4 mi</small></div>
      <div className="speed"><strong>{Math.round(live.location?.speedMph || live.obd?.speedMph || 0)}</strong><span>MPH</span><small>SPEED<br />LIMIT<br /><b>--</b></small></div>
      <div className="thumb-arc">
        <button onClick={startRoute} className={routing ? 'active' : ''}><LocateFixed /><span>Navigate</span></button>
        <button onClick={function () { setLayer(layer === 'terrain' ? 'satellite' : 'terrain'); }}><Layers3 /><span>{layer}</span></button>
        <button><Map /><span>Overview</span></button><button><Volume2 /><span>Audio</span></button>
      </div>
      <div className="road-label">N PRIMA RD</div>
      <div className="nav-caption">TERRAIN FLOW <span>GOOGLE NAVIGATION SDK</span></div>
    </div>
  </section>;
}

function MediaPage({ media }) {
  const playing = media?.playing ?? false;
  const [liked, setLiked] = useState(media?.liked ?? false);
  const [sourceOpen, setSourceOpen] = useState(false);
  const [mediaApps, setMediaApps] = useState([]);
  const [selectedSource, setSelectedSource] = useState(function () { return readStoredJson('trx-apex-media-source', null); });
  const duration = Math.max(1, media?.durationMs || 1);
  const progress = Math.min(100, Math.round((media?.positionMs || 0) * 100 / duration));
  const queue = media?.queue?.length ? media.queue : [{ title: 'Queue unavailable' }];
  const source = selectedSource?.name || (media?.source ? media.source.split('.').pop().toUpperCase() : 'MEDIA');
  useEffect(function () {
    if (!sourceOpen || mediaApps.length) return;
    native.apps().then(function (result) {
      const apps = result?.apps || [];
      const likely = apps.filter(function (app) {
        return /(spotify|music|youtube|vlc|pandora|tidal|amazon|iheartradio|sirius|soundcloud|poweramp|audible|podcast|plex|radio)/i.test(app.name + ' ' + app.packageName);
      });
      setMediaApps(likely.length ? likely : apps);
    });
  }, [sourceOpen, mediaApps.length]);
  useEffect(function () { if (typeof media?.liked === 'boolean') setLiked(media.liked); }, [media?.liked, media?.title]);
  async function toggleLike() {
    const result = await native.mediaCommand('favorite');
    if (result?.success) setLiked(result.liked);
  }
  function chooseSource(app) {
    const saved = { packageName: app.packageName, name: app.name };
    setSelectedSource(saved);
    localStorage.setItem('trx-apex-media-source', JSON.stringify(saved));
    native.launchApp(app.packageName);
    setSourceOpen(false);
  }
  function handlePlay() {
    if (media?.hasAccess === false) { native.requestPermissionGroup('media'); return; }
    if (!playing && selectedSource?.packageName) { native.launchApp(selectedSource.packageName); return; }
    native.mediaCommand('toggle');
  }
  function formatMs(value) { const seconds = Math.floor((value || 0) / 1000); return Math.floor(seconds / 60) + ':' + String(seconds % 60).padStart(2, '0'); }
  return <section className="page media-page">
    <PageTag index="04" title="Sonic" subtitle="Spatial media environment" right={<button className="source-pill" onClick={function () { setSourceOpen(true); }}><Radio /> {source} <ChevronRight /></button>} />
    {sourceOpen && <div className="source-scrim" onClick={function () { setSourceOpen(false); }}>
      <div className="source-drawer" onClick={function (event) { event.stopPropagation(); }}>
        <div className="source-drawer-head"><span><small>AUDIO ROUTING</small><b>CHOOSE SOURCE</b></span><button onClick={function () { setSourceOpen(false); }}>×</button></div>
        <div className="source-apps">{mediaApps.length ? mediaApps.map(function (app) { return <button key={app.packageName} onClick={function () { chooseSource(app); }}>{app.icon ? <img src={app.icon} alt="" /> : <Radio />}<span><b>{app.name}</b><small>OPEN PLAYER</small></span><ChevronRight /></button>; }) : <div className="source-loading">SCANNING INSTALLED MEDIA APPS…</div>}</div>
        <button className="source-access" onClick={function () { native.requestPermissionGroup('media'); }}><ShieldCheck /><span><b>MEDIA CONTROL ACCESS</b><small>{media?.hasAccess ? 'ENABLED · LIVE SESSION CONTROL' : 'ENABLE TRACK INFO AND CONTROLS'}</small></span><ChevronRight /></button>
      </div>
    </div>}
    <div className="sonic-stage">
      <div className="wave-field">{Array.from({ length: 64 }, function (_, i) { return <i key={i} style={{ '--h': (18 + Math.abs(Math.sin(i * .61)) * 70) + '%', '--d': (i * -36) + 'ms' }} />; })}</div>
      <div className={'record' + (playing ? ' spinning' : '')}><img src={media?.artwork || '/trx-radio.webp'} alt="Current album artwork" /><div className="record-hole" /></div>
      <div className="track-editorial"><span>{media?.hasAccess === false ? 'MEDIA ACCESS REQUIRED' : 'NOW PLAYING · LIVE SESSION'}</span><h2>{media?.title || 'NO ACTIVE'}<br />{media?.title ? '' : 'MEDIA'}</h2><p>{media?.artist || 'Start music to begin'}</p></div>
      <div className="transport-arc">
        <button onClick={function () { native.mediaCommand('previous'); }}><SkipBack /></button><button className="transport-main" onClick={handlePlay}>{playing ? <Pause /> : <Play />}</button><button onClick={function () { native.mediaCommand('next'); }}><SkipForward /></button>
        <button className={liked ? 'liked' : ''} onClick={toggleLike} aria-label={liked ? 'Remove from favorites' : 'Add to favorites'}><Heart fill={liked ? 'currentColor' : 'none'} /></button>
      </div>
      <div className="progress-line"><span>{formatMs(media?.positionMs)}</span><input type="range" value={progress} onChange={function (e) { native.mediaCommand('seek', Math.round(Number(e.target.value) * duration / 100)); }} /><span>{formatMs(media?.durationMs)}</span></div>
      <div className="up-next-curve"><span>UP NEXT</span>{queue.slice(0,3).map(function (item, i) { const art = item.artwork || (media?.artwork && (!item.artist || item.artist.toLowerCase() === (media?.artist || '').toLowerCase()) ? media.artwork : ''); return <button key={(item.title || '') + i} onClick={function () { if (media?.queue?.length) native.mediaCommand('queue', 0, i); }}>{art ? <img src={art} alt="" /> : <i className="queue-placeholder"><Music2 /></i>}<span><b>{item.title}</b><small>{item.artist || 'UPCOMING TRACK'}</small></span><ChevronRight /></button>; })}</div>
      <div className="audio-output"><Volume2 /><span>UCONNECT 12</span><b>18</b></div>
    </div>
  </section>;
}

function PerformancePage({ obd }) {
  const rpm = Math.round(obd?.rpm || 0);
  function reading(value, fallback, digits) { return value == null ? fallback : Number(value).toFixed(digits || 0); }
  const telemetry = [
    ['Boost', reading(obd?.boostPsi, '--', 1), 'PSI', 'supercharger'], ['Coolant', reading(obd?.coolantF, '--'), '°F', 'coolant'],
    ['Intake', reading(obd?.intakeF, '--'), '°F', 'oil'], ['Trans', reading(obd?.transmissionF, 'N/A'), '°F', 'trans'],
    ['Voltage', reading(obd?.batteryV, '--', 1), 'V', 'voltage'], ['Load', reading(obd?.engineLoad, '--'), '%', 'throttle']
  ];
  return <section className="page performance-page">
    <PageTag index="05" title="Dynamics" subtitle="Live vehicle intelligence" right={<button className="mx-live" onClick={function () { native.reconnectObd(); }}><i /> {obd?.deviceName || 'OBDLINK MX+'} <b>{obd?.connected ? 'LIVE' : 'CONNECT'}</b></button>} />
    <div className="dynamics-stage">
      <div className="rpm-readout"><strong>{rpm.toLocaleString()}</strong><span>RPM</span><div className="gear-readout"><b>M4</b><small>TOW / HAUL OFF</small></div></div>
      <div className="tach-arc"><div className="tach-fill" style={{ '--rpm': (rpm / 70) + '%' }} />{[1,2,3,4,5,6,7].map(function (n) { return <i key={n} style={{ '--i': n }}>{n}</i>; })}</div>
      <div className="xray-truck"><img src="/trx-hero.webp" alt="RAM TRX vehicle telemetry model" /><div className="thermal engine" /><div className="thermal rear" /></div>
      <svg className="callout-lines" viewBox="0 0 900 460" preserveAspectRatio="none"><path d="M450 190 L230 95 L96 95"/><path d="M525 218 L720 105 L850 105"/><path d="M397 250 L210 340 L80 340"/><path d="M615 280 L760 340 L868 340"/></svg>
      <div className="telemetry-grid">{telemetry.map(function (item) { return <div className={'telemetry ' + item[3]} key={item[0]}><span>{item[0]}</span><strong>{item[1]}<small>{item[2]}</small></strong></div>; })}</div>
      <div className="power-surface"><span>LIVE POWER CURVE</span><svg viewBox="0 0 500 160" preserveAspectRatio="none"><path className="gridline" d="M0 130H500M0 90H500M0 50H500"/><path className="hp" d="M0 140 C100 135 125 95 205 88 S330 25 500 35"/><path className="torque" d="M0 145 C90 125 125 58 220 50 S365 62 500 77"/></svg><div><b>HP 702</b><b>TQ 650</b></div></div>
      <button className="zero-sixty"><span>0–60 MPH</span><strong>3.4<small>s</small></strong><em>DRAG TIMER</em></button>
      <div className="rpm-control">{obd?.status || 'PAIR OBDLINK MX+'}</div>
    </div>
  </section>;
}

function AppsPage() {
  const [query, setQuery] = useState('');
  const [installed, setInstalled] = useState([]);
  const [editing, setEditing] = useState(false);
  const [favoritePackages, setFavoritePackages] = useState(function () { return readStoredJson('trx-apex-orbit-favorites', []); });
  useEffect(function () {
    native.apps().then(function (result) { if (result?.apps?.length) setInstalled(result.apps); });
  }, []);
  const source = installed.length ? installed : APP_LIST.map(function (item) { return { name: item[0], glyph: item[1], packageName: '' }; });
  useEffect(function () {
    if (!installed.length || favoritePackages.length) return;
    const defaults = installed.slice(0, 8).map(function (app) { return app.packageName; });
    setFavoritePackages(defaults);
    localStorage.setItem('trx-apex-orbit-favorites', JSON.stringify(defaults));
  }, [installed, favoritePackages.length]);
  const favorites = favoritePackages.map(function (packageName) { return source.find(function (app) { return app.packageName === packageName; }); }).filter(Boolean).slice(0, 8);
  function toggleFavorite(app) {
    if (!app.packageName) return;
    setFavoritePackages(function (current) {
      const exists = current.includes(app.packageName);
      const next = exists ? current.filter(function (item) { return item !== app.packageName; }) : current.length < 8 ? [...current, app.packageName] : [...current.slice(1), app.packageName];
      localStorage.setItem('trx-apex-orbit-favorites', JSON.stringify(next));
      return next;
    });
  }
  const filtered = useMemo(function () {
    return source.filter(function (item) { return item.name.toLowerCase().includes(query.toLowerCase()); });
  }, [query, installed]);
  return <section className="page apps-page">
    <PageTag index="06" title="Orbit" subtitle={editing ? 'Tap apps below to add · tap orbit to remove' : 'Applications in motion'} right={<button className={'edit-apps' + (editing ? ' active' : '')} onClick={function () { setEditing(!editing); }}><SlidersHorizontal /> {editing ? 'DONE' : 'CUSTOMIZE'}</button>} />
    <div className="app-search"><Search /><input value={query} onChange={function (e) { setQuery(e.target.value); }} placeholder="Search apps, settings, vehicle…" /><Sparkles /></div>
    {!query && <div className={'app-orbit' + (editing ? ' editing' : '')}><div className="orbit-emblem">TRX<small>{editing ? (favorites.length + ' / 8 SELECTED') : 'FAVORITES'}</small></div>{(favorites.length ? favorites : source.slice(0, 8)).map(function (item, i) { return <AppButton key={item.packageName || item.name} app={item} style={{ '--i': i }} editing={editing} onPress={editing ? function () { toggleFavorite(item); } : null} />; })}</div>}
    <div className={'app-flow' + (query ? ' searching' : '') + (editing ? ' editing' : '')}>{filtered.map(function (item) { return <AppButton key={item.packageName || item.name} app={item} editing={editing && favoritePackages.includes(item.packageName)} onPress={editing ? function () { toggleFavorite(item); } : null} />; })}</div>
    <div className="alphabet">{'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('').map(function (letter) { return <span key={letter}>{letter}</span>; })}</div>
    <div className="app-mode"><button className="active">RECENT</button><button>ALL APPS</button></div>
  </section>;
}

function AppButton({ app, style, onPress, editing }) {
  let held = false;
  let timer;
  function down() {
    held = false;
    timer = setTimeout(function () { held = true; if (!onPress && app.packageName) native.appAction(app.packageName, 'info'); }, 650);
  }
  function up() {
    clearTimeout(timer);
    if (!held && onPress) onPress();
    else if (!held && app.packageName) native.launchApp(app.packageName);
  }
  return <button className={editing ? 'favorite-selected' : ''} style={style} onPointerDown={down} onPointerUp={up} onPointerCancel={function () { clearTimeout(timer); }}><AppDisc app={app} />{editing && <i className="favorite-mark">✓</i>}</button>;
}

function AppDisc({ app }) {
  const className = 'disc disc-' + app.name.toLowerCase().replaceAll(' ', '-');
  return <>{app.icon ? <span className={className}><img src={app.icon} alt="" /></span> : <span className={className}>{app.glyph || '◈'}</span>}<b>{app.name}</b></>;
}

function SettingsPage(props) {
  const { theme, setTheme, accent, setAccent, iconScale, setIconScale, reducedMotion, setReducedMotion, displayMode, setDisplayMode, obd } = props;
  const [section, setSection] = useState('appearance');
  const sections = [['appearance', 'Drive & Display', Aperture], ['navigation', 'Navigation', Navigation], ['vehicle', 'Vehicle Link', Bluetooth], ['system', 'System', Settings]];
  return <section className="page settings-page">
    <PageTag index="07" title="Studio" subtitle="TRX APEX customization" right={<div className="studio-status"><ShieldCheck /> SETTINGS SAVED</div>} />
    <div className="studio-preview"><div className="preview-rail" /><img src="/trx-hero.webp" alt="Live launcher preview" /><strong>10:24</strong><span>{THEMES[theme].name.toUpperCase()}</span><div className="preview-stack"><i /><i /><i /></div></div>
    <div className="theme-materials"><span>THEME MATERIAL</span><div>{Object.entries(THEMES).map(function (entry) {
      const id = entry[0], item = entry[1];
      return <button key={id} className={theme === id ? 'selected' : ''} onClick={function () { setTheme(id); }}><i style={{ '--swatch': item.accent }} /><b>{item.name}</b></button>;
    })}</div></div>
    <div className="studio-controls">
      <RangeControl label="ACCENT INTENSITY" value={accent} onChange={setAccent} min={30} max={100} suffix="%" />
      <RangeControl label="ICON SCALE" value={iconScale} onChange={setIconScale} min={80} max={125} suffix="%" />
      <div className="mode-control"><span>DISPLAY MODE</span>{['day', 'night', 'auto'].map(function (mode) { return <button key={mode} className={displayMode === mode ? 'active' : ''} onClick={function () { setDisplayMode(mode); }}>{mode === 'day' ? <Sun /> : mode === 'night' ? <Moon /> : <Sparkles />}{mode}</button>; })}</div>
    </div>
    <div className="studio-sections">{sections.map(function (item) {
      const id = item[0], label = item[1], Icon = item[2];
      const detail = id === 'appearance' ? 'Theme · Motion · Calibration' : id === 'navigation' ? 'Google · Guidance · Offline' : id === 'vehicle' ? 'OBDLink MX+ · Diagnostics' : 'Startup · Backup · About';
      return <button key={id} onClick={function () { setSection(id); }} className={section === id ? 'active' : ''}><Icon /><span>{label}</span><small>{detail}</small><ChevronRight /></button>;
    })}</div>
    <div className="setting-drawer">
      {section === 'appearance' && <><Toggle label="REDUCE MOTION" detail="Minimize transitions while driving" value={reducedMotion} setValue={setReducedMotion} /><Action label="SCREEN CALIBRATION" detail="1080 × 1440 · 4:3 portrait" /></>}
      {section === 'navigation' && <><Action label="GOOGLE NAVIGATION SDK" detail="Open native turn-by-turn navigation" onClick={function () { native.navigate(''); }} /><Toggle label="3D TERRAIN" detail="Elevation-aware route rendering" value={true} setValue={function () {}} /></>}
      {section === 'vehicle' && <><Action label="OBDLINK MX+" detail={obd?.status || 'Pair adapter in Android Bluetooth'} onClick={function () { native.settings('bluetooth'); }} /><Action label="RECONNECT VEHICLE LINK" detail="Restart read-only OBD telemetry" onClick={function () { native.reconnectObd(); }} /></>}
      {section === 'system' && <><Action label="DEFAULT LAUNCHER" detail="Choose TRX APEX as Android Home" onClick={function () { native.requestPermissionGroup('launcher'); }} /><Action label="APP PERMISSIONS" detail="Location · Bluetooth · Media" onClick={function () { native.settings('app'); }} /></>}
    </div>
  </section>;
}

function RangeControl({ label, value, onChange, min, max, suffix }) {
  return <label className="range-control"><span>{label}</span><input type="range" min={min} max={max} value={value} onChange={function (e) { onChange(Number(e.target.value)); }} /><b>{value}{suffix}</b></label>;
}
function Toggle({ label, detail, value, setValue }) {
  return <button className="drawer-row" onClick={function () { setValue(!value); }}><span><b>{label}</b><small>{detail}</small></span><i className={'switch' + (value ? ' on' : '')}><em /></i></button>;
}
function Action({ label, detail, onClick }) {
  return <button className="drawer-row" onClick={onClick}><span><b>{label}</b><small>{detail}</small></span><ChevronRight /></button>;
}

createRoot(document.getElementById('root')).render(<StrictMode><App /></StrictMode>);
