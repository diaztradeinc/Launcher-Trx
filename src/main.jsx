import { StrictMode, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  Activity, Aperture, Bluetooth, Check, ChevronRight, CloudSun, Gauge, Grid2X2,
  Heart, Home, Layers3, LocateFixed, Map, MapPin, Mic, Moon, Music2, Navigation,
  Pause, Play, Radio, Search, Settings, ShieldCheck, Signal, SkipBack, SkipForward,
  SlidersHorizontal, Sparkles, Sun, Volume2, Wifi
} from 'lucide-react';
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
    <StatusBar now={now} />
    <CommandRail active={active} onNavigate={setActive} />
    <main className="apex-canvas" key={active}>
      {active === 'home' && <HomePage onNavigate={setActive} now={now} />}
      {active === 'navigation' && <NavigationPage />}
      {active === 'media' && <MediaPage />}
      {active === 'performance' && <PerformancePage />}
      {active === 'apps' && <AppsPage onOpenSettings={function () { setActive('settings'); }} />}
      {active === 'settings' && <SettingsPage theme={theme} setTheme={setTheme} accent={accent} setAccent={setAccent} iconScale={iconScale} setIconScale={setIconScale} reducedMotion={reducedMotion} setReducedMotion={setReducedMotion} displayMode={displayMode} setDisplayMode={setDisplayMode} />}
    </main>
  </div>;
}

function Commissioning({ onComplete }) {
  const [step, setStep] = useState(0);
  const nodes = [['Position', MapPin], ['Vehicle link', Bluetooth], ['Media', Music2], ['Launcher', Home]];

  async function request() {
    const current = nodes[step][0];
    try {
      if (current === 'Position' && navigator.geolocation) {
        await new Promise(function (resolve) {
          navigator.geolocation.getCurrentPosition(resolve, resolve, { timeout: 3500 });
        });
      }
      if (current === 'Media' && 'Notification' in window && Notification.permission === 'default') {
        await Notification.requestPermission();
      }
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

function StatusBar({ now }) {
  const time = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  return <header className="status-bar">
    <div className="coordinates">40.3573° N <span>74.6702° W</span></div>
    <div className="apex-wordmark"><b>TRX</b><span>APEX</span></div>
    <div className="system-status"><Bluetooth /><Wifi /><Signal /><span>{time}</span><b>72°</b></div>
  </header>;
}

function CommandRail({ active, onNavigate }) {
  return <nav className="command-rail" aria-label="Main navigation">
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

function HomePage({ onNavigate, now }) {
  const [quickOpen, setQuickOpen] = useState(false);
  const glyphs = ['🗺️', '●', '☎', '▶', 'MX'];
  return <section className="page home-page">
    <div className="home-hero">
      <img src="/trx-hero.webp" alt="Red RAM TRX in mountain terrain" />
      <div className="terrain-lines" />
      <div className="solar-arc"><Sun /><span>SUNRISE 6:12</span><i /><span>SUNSET 7:28</span></div>
      <div className="hero-time"><strong>{now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</strong><span>MON · APR 14</span></div>
      <div className="hero-weather"><CloudSun /><b>72°F</b><span>MOAB, UT<br />CLEAR · ELEV 4,025 FT</span></div>
      <div className="expedition-copy"><span>EXPEDITION 01</span><h2>Adventure<br />awaits.</h2></div>
      <div className="context-ribbon">
        <button className="media-context" onClick={function () { onNavigate('media'); }}><img src="/trx-radio.webp" alt="Album artwork" /><span><small>NOW PLAYING</small><b>Kickstart My Heart</b><em>Mötley Crüe</em></span><Pause /></button>
        <button className="route-context" onClick={function () { onNavigate('navigation'); }}><Navigation /><span><small>NEXT MANEUVER</small><b>0.8 mi · Turn right</b><em>ETA 10:36 · 6.4 mi</em></span></button>
        <button className="vehicle-context" onClick={function () { onNavigate('performance'); }}><Activity /><span><small>VEHICLE LINK</small><b>OBDLINK MX+</b><em className="live">● LIVE</em></span></button>
      </div>
      <div className={'quick-orbit' + (quickOpen ? ' open' : '')}>
        <button className="quick-trigger" onClick={function () { setQuickOpen(!quickOpen); }}><Sparkles /></button>
        {glyphs.map(function (glyph, i) {
          return <button key={glyph + i} style={{ '--i': i }} onClick={function () { onNavigate(i === 4 ? 'performance' : i < 1 ? 'navigation' : i === 1 ? 'media' : 'apps'); }}>{glyph}</button>;
        })}
      </div>
      <div className="connection-strip"><i /> GPS LOCKED <span /> OBDLINK MX+ <b>LIVE</b> <span /> ALL SYSTEMS GO</div>
    </div>
  </section>;
}

function NavigationPage() {
  const [layer, setLayer] = useState('terrain');
  const [routing, setRouting] = useState(true);
  return <section className="page navigation-page">
    <div className="map-stage">
      <img src="/trx-map.webp" alt="Dimensional terrain route" />
      <div className="map-shade" /><div className="route-ribbon" /><div className="route-arrow">➤</div>
      <div className="nav-command"><Search /><input placeholder="Search destination or command" /><Mic /></div>
      <div className="maneuver"><span>NEXT TURN</span><strong>0.8<small>mi</small></strong><p>Turn right onto<br /><b>Darlington Dr</b></p></div>
      <div className="lane-guidance"><i>↑</i><i className="active">↗</i><i>↑</i><span>KEEP RIGHT</span></div>
      <div className="arrival"><span>ARRIVAL</span><strong>10:36</strong><small>12 min · 6.4 mi</small></div>
      <div className="speed"><strong>65</strong><span>MPH</span><small>SPEED<br />LIMIT<br /><b>70</b></small></div>
      <div className="thumb-arc">
        <button onClick={function () { setRouting(!routing); }} className={routing ? 'active' : ''}><LocateFixed /><span>Recenter</span></button>
        <button onClick={function () { setLayer(layer === 'terrain' ? 'satellite' : 'terrain'); }}><Layers3 /><span>{layer}</span></button>
        <button><Map /><span>Overview</span></button><button><Volume2 /><span>Audio</span></button>
      </div>
      <div className="road-label">N PRIMA RD</div>
      <div className="nav-caption">TERRAIN FLOW <span>GOOGLE NAVIGATION SDK</span></div>
    </div>
  </section>;
}

function MediaPage() {
  const [playing, setPlaying] = useState(true);
  const [liked, setLiked] = useState(true);
  const [progress, setProgress] = useState(34);
  const queue = ['Def Leppard', "Guns N' Roses", 'AC/DC', 'Foo Fighters'];
  return <section className="page media-page">
    <PageTag index="04" title="Sonic" subtitle="Spatial media environment" right={<button className="source-pill"><Radio /> SPOTIFY <ChevronRight /></button>} />
    <div className="sonic-stage">
      <div className="wave-field">{Array.from({ length: 64 }, function (_, i) { return <i key={i} style={{ '--h': (18 + Math.abs(Math.sin(i * .61)) * 70) + '%', '--d': (i * -36) + 'ms' }} />; })}</div>
      <div className={'record' + (playing ? ' spinning' : '')}><img src="/trx-radio.webp" alt="Kickstart My Heart album artwork" /><div className="record-hole" /></div>
      <div className="track-editorial"><span>NOW PLAYING · ROCK LIVES ON</span><h2>Kickstart<br />My Heart</h2><p>Mötley Crüe</p></div>
      <div className="transport-arc">
        <button><SkipBack /></button><button className="transport-main" onClick={function () { setPlaying(!playing); }}>{playing ? <Pause /> : <Play />}</button><button><SkipForward /></button>
        <button className={liked ? 'liked' : ''} onClick={function () { setLiked(!liked); }}><Heart fill={liked ? 'currentColor' : 'none'} /></button>
      </div>
      <div className="progress-line"><span>1:24</span><input type="range" value={progress} onChange={function (e) { setProgress(e.target.value); }} /><span>4:43</span></div>
      <div className="up-next-curve"><span>UP NEXT</span>{queue.map(function (name, i) { return <button key={name} style={{ '--i': i }}><img src="/trx-radio.webp" alt="" /><b>{name}</b></button>; })}</div>
      <div className="audio-output"><Volume2 /><span>UCONNECT 12</span><b>18</b></div>
    </div>
  </section>;
}

function PerformancePage() {
  const [rpm, setRpm] = useState(4200);
  const telemetry = [
    ['Boost', '18.5', 'PSI', 'supercharger'], ['Coolant', '194', '°F', 'coolant'],
    ['Oil', '210', '°F', 'oil'], ['Trans', '190', '°F', 'trans'],
    ['Voltage', '14.1', 'V', 'voltage'], ['Throttle', '68', '%', 'throttle']
  ];
  return <section className="page performance-page">
    <PageTag index="05" title="Dynamics" subtitle="Live vehicle intelligence" right={<div className="mx-live"><i /> OBDLINK MX+ <b>LIVE</b></div>} />
    <div className="dynamics-stage">
      <div className="rpm-readout"><strong>{rpm.toLocaleString()}</strong><span>RPM</span><b>M4</b><small>TOW / HAUL OFF</small></div>
      <div className="tach-arc"><div className="tach-fill" style={{ '--rpm': (rpm / 70) + '%' }} />{[1,2,3,4,5,6,7].map(function (n) { return <i key={n} style={{ '--i': n }}>{n}</i>; })}</div>
      <div className="xray-truck"><img src="/trx-hero.webp" alt="RAM TRX vehicle telemetry model" /><div className="thermal engine" /><div className="thermal rear" /></div>
      <svg className="callout-lines" viewBox="0 0 900 460" preserveAspectRatio="none"><path d="M450 190 L230 95 L96 95"/><path d="M525 218 L720 105 L850 105"/><path d="M397 250 L210 340 L80 340"/><path d="M615 280 L760 340 L868 340"/></svg>
      <div className="telemetry-grid">{telemetry.map(function (item) { return <div className={'telemetry ' + item[3]} key={item[0]}><span>{item[0]}</span><strong>{item[1]}<small>{item[2]}</small></strong></div>; })}</div>
      <div className="power-surface"><span>LIVE POWER CURVE</span><svg viewBox="0 0 500 160" preserveAspectRatio="none"><path className="gridline" d="M0 130H500M0 90H500M0 50H500"/><path className="hp" d="M0 140 C100 135 125 95 205 88 S330 25 500 35"/><path className="torque" d="M0 145 C90 125 125 58 220 50 S365 62 500 77"/></svg><div><b>HP 702</b><b>TQ 650</b></div></div>
      <button className="zero-sixty"><span>0–60 MPH</span><strong>3.4<small>s</small></strong><em>DRAG TIMER</em></button>
      <input className="rpm-control" type="range" min="900" max="6500" value={rpm} onChange={function (e) { setRpm(Number(e.target.value)); }} aria-label="Demo RPM" />
    </div>
  </section>;
}

function AppsPage({ onOpenSettings }) {
  const [query, setQuery] = useState('');
  const filtered = useMemo(function () {
    return APP_LIST.filter(function (item) { return item[0].toLowerCase().includes(query.toLowerCase()); });
  }, [query]);
  return <section className="page apps-page">
    <PageTag index="06" title="Orbit" subtitle="Applications in motion" right={<button className="edit-apps" onClick={onOpenSettings}><SlidersHorizontal /> CUSTOMIZE</button>} />
    <div className="app-search"><Search /><input value={query} onChange={function (e) { setQuery(e.target.value); }} placeholder="Search apps, settings, vehicle…" /><Sparkles /></div>
    {!query && <div className="app-orbit"><div className="orbit-emblem">TRX<small>FAVORITES</small></div>{APP_LIST.slice(0, 8).map(function (item, i) { return <button key={item[0]} style={{ '--i': i }}><AppDisc name={item[0]} glyph={item[1]} /></button>; })}</div>}
    <div className={'app-flow' + (query ? ' searching' : '')}>{filtered.map(function (item) { return <button key={item[0]}><AppDisc name={item[0]} glyph={item[1]} /></button>; })}</div>
    <div className="alphabet">{'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('').map(function (letter) { return <span key={letter}>{letter}</span>; })}</div>
    <div className="app-mode"><button className="active">RECENT</button><button>ALL APPS</button></div>
  </section>;
}

function AppDisc({ name, glyph }) {
  return <><span className={'disc disc-' + name.toLowerCase().replaceAll(' ', '-')}>{glyph}</span><b>{name}</b></>;
}

function SettingsPage(props) {
  const { theme, setTheme, accent, setAccent, iconScale, setIconScale, reducedMotion, setReducedMotion, displayMode, setDisplayMode } = props;
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
      {section === 'navigation' && <><Action label="DEFAULT MAP PROVIDER" detail="Google Navigation SDK" /><Toggle label="3D TERRAIN" detail="Elevation-aware route rendering" value={true} setValue={function () {}} /></>}
      {section === 'vehicle' && <><Action label="OBDLINK MX+" detail="Connected · Bluetooth · 42 PIDs" /><Action label="SENSOR DASHBOARD" detail="Choose live telemetry channels" /></>}
      {section === 'system' && <><Action label="DEFAULT LAUNCHER" detail="TRX APEX is active" /><Action label="BACKUP & RESTORE" detail="Themes, shortcuts and settings" /></>}
    </div>
  </section>;
}

function RangeControl({ label, value, onChange, min, max, suffix }) {
  return <label className="range-control"><span>{label}</span><input type="range" min={min} max={max} value={value} onChange={function (e) { onChange(Number(e.target.value)); }} /><b>{value}{suffix}</b></label>;
}
function Toggle({ label, detail, value, setValue }) {
  return <button className="drawer-row" onClick={function () { setValue(!value); }}><span><b>{label}</b><small>{detail}</small></span><i className={'switch' + (value ? ' on' : '')}><em /></i></button>;
}
function Action({ label, detail }) {
  return <button className="drawer-row"><span><b>{label}</b><small>{detail}</small></span><ChevronRight /></button>;
}

createRoot(document.getElementById('root')).render(<StrictMode><App /></StrictMode>);
