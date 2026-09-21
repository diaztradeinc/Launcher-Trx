import { StrictMode, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  Activity, AppWindow, ArrowLeft, ArrowRight, Bluetooth, Car, ChevronDown, ChevronRight,
  Compass, Gauge, Grid2x2, Heart, Home, Map, MapPin, Menu, Mic, Music2, Navigation,
  Pause, Play, RotateCcw, Search, Settings, Signal, SlidersHorizontal, Smartphone,
  Sparkles, Star, Thermometer, Volume2, Wifi, Wind, Wrench, X, Zap
} from 'lucide-react';
import './styles.css';

const navItems = [
  { id: 'home', label: 'Home', icon: Home },
  { id: 'navigation', label: 'Navigation', icon: Navigation },
  { id: 'media', label: 'Media', icon: Music2 },
  { id: 'performance', label: 'Performance', icon: Gauge },
  { id: 'apps', label: 'Apps', icon: Grid2x2 },
];

const apps = [
  ['Google Maps', 'map', true], ['Waze', 'waze', true], ['Spotify', 'spotify', true], ['YouTube Music', 'youtube', true],
  ['Phone', 'phone', false], ['Messages', 'messages', true], ['Chrome', 'chrome', false], ['Settings', 'settings', false],
  ['SiriusXM', 'sirius', true], ['Weather', 'weather', false], ['Files', 'files', false], ['Camera', 'camera', false],
  ['OBDLink', 'obd', false], ['Gmail', 'gmail', true], ['Play Store', 'play', false], ['Netflix', 'netflix', false],
];

function App() {
  const [active, setActive] = useState('home');
  const [time, setTime] = useState('10:24');
  const [playing, setPlaying] = useState(true);
  const [favorite, setFavorite] = useState(true);
  const [brightness, setBrightness] = useState(70);
  const [theme, setTheme] = useState('TRX Red');
  const [search, setSearch] = useState('');

  useEffect(() => {
    const update = () => setTime(new Intl.DateTimeFormat('en-US', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date()));
    update();
    const interval = window.setInterval(update, 60000);
    return () => window.clearInterval(interval);
  }, []);

  const filteredApps = useMemo(() => apps.filter(([name]) => name.toLowerCase().includes(search.toLowerCase())), [search]);
  const select = (id) => setActive(id);

  return <div className="app-shell">
    <TopBar time={time} />
    <main className="screen-area">
      {active === 'home' && <HomeScreen onNavigate={select} />}
      {active === 'navigation' && <NavigationScreen />}
      {active === 'media' && <MediaScreen playing={playing} setPlaying={setPlaying} favorite={favorite} setFavorite={setFavorite} />}
      {active === 'performance' && <PerformanceScreen />}
      {active === 'apps' && <AppsScreen search={search} setSearch={setSearch} apps={filteredApps} onSettings={() => setActive('settings')} />}
      {active === 'settings' && <SettingsScreen brightness={brightness} setBrightness={setBrightness} theme={theme} setTheme={setTheme} />}
    </main>
    <BottomNav active={active} onNavigate={select} />
  </div>;
}

function TopBar({ time }) {
  return <header className="topbar">
    <div className="weather">72° <Thermometer size={15} /></div>
    <div className="brand"><span>RAM</span><strong>TRX</strong><small>LAUNCHER</small></div>
    <div className="status"><Bluetooth size={15} /><Signal size={16} /><span>{time}</span><span>72°</span></div>
  </header>;
}

function BottomNav({ active, onNavigate }) {
  return <nav className="bottom-nav">{navItems.map(({ id, label, icon: Icon }) => <button key={id} className={active === id ? 'active' : ''} onClick={() => onNavigate(id)}><Icon size={26} strokeWidth={1.8} /><span>{label}</span></button>)}</nav>;
}

function ScreenHeading({ eyebrow, title, action }) {
  return <div className="screen-heading"><div><span className="eyebrow">{eyebrow}</span><h1>{title}</h1></div>{action}</div>;
}

function HomeScreen({ onNavigate }) {
  return <div className="home-screen">
    <section className="hero-card">
      <img src="/trx-hero.webp" alt="Red performance truck on a mountain ridge" />
      <div className="hero-overlay"><span className="eyebrow">BUILT FOR WHAT'S NEXT</span><h1>ANY TERRAIN.<br /><em>A HIGHER STANDARD.</em></h1><button className="red-button" onClick={() => onNavigate('performance')}>ENTER PERFORMANCE <ArrowRight size={18} /></button></div>
    </section>
    <div className="quick-grid">
      <QuickCard icon={Navigation} title="Navigation" subtitle="Ready for route" onClick={() => onNavigate('navigation')} />
      <QuickCard icon={Music2} title="TRX Radio" subtitle="Revolution" onClick={() => onNavigate('media')} />
      <QuickCard icon={Gauge} title="Performance" subtitle="OBDLINK MX+ connected" onClick={() => onNavigate('performance')} />
      <QuickCard icon={AppWindow} title="All Apps" subtitle="16 installed" onClick={() => onNavigate('apps')} />
    </div>
    <section className="home-status"><span className="status-dot" /> OBDLINK MX+ <b>•</b> CONNECTED <span className="divider" /> <Wind size={17} /> 72°F <span className="divider" /> <Wifi size={17} /> 5G</section>
  </div>;
}

function QuickCard({ icon: Icon, title, subtitle, onClick }) {
  return <button className="quick-card" onClick={onClick}><Icon size={30} /><span><strong>{title}</strong><small>{subtitle}</small></span><ChevronRight size={18} /></button>;
}

function NavigationScreen() {
  return <div className="navigation-screen">
    <ScreenHeading eyebrow="ROUTE PLANNER" title="Navigation" action={<button className="icon-button"><Settings size={20} /></button>} />
    <div className="map-toolbar"><div className="search-box"><Search size={21} /><span>Search destination</span><Mic size={20} /></div><button className="toolbar-button"><Map size={18} /> MAP OPTIONS</button></div>
    <section className="map-card"><img src="/trx-map.webp" alt="Navigation map" /><div className="route-callout"><div className="turn-arrow">↱</div><div><strong>0.3 mi</strong><span>Turn right onto Broad St</span></div></div><div className="map-controls"><button><Compass size={21} /></button><button><PlusIcon /></button><button><MinusIcon /></button></div><div className="route-footer"><div><strong>22 min</strong><span>12 mi</span><span>10:46 AM</span></div><button className="red-button">END ROUTE</button></div></section>
  </div>;
}
function PlusIcon() { return <span className="plus-minus">+</span>; }
function MinusIcon() { return <span className="plus-minus">−</span>; }

function MediaScreen({ playing, setPlaying, favorite, setFavorite }) {
  return <div className="media-screen"><ScreenHeading eyebrow="NOW PLAYING" title="TRX Radio" action={<button className="icon-button"><Volume2 size={20} /></button>} />
    <div className="media-main"><div className="album-art"><img src="/trx-radio.webp" alt="TRX Radio album artwork" /><span>TRX RADIO</span></div><div className="track-info"><div className="track-kicker">TRX RADIO <b>•</b> LIVE SESSION</div><h2>REVOLUTION</h2><p>Born for more. Built to go further.</p><div className="player-progress"><span>2:14</span><input type="range" defaultValue="54" aria-label="Track progress" /><span>-1:56</span></div><div className="player-actions"><button><ArrowLeft size={23} /></button><button className="play-button" onClick={() => setPlaying(!playing)}>{playing ? <Pause size={30} /> : <Play size={30} fill="currentColor" />}</button><button><ArrowRight size={23} /></button><button className={favorite ? 'favorite selected' : 'favorite'} onClick={() => setFavorite(!favorite)}><Heart size={20} fill={favorite ? 'currentColor' : 'none'} /></button></div></div></div>
    <div className="equalizer" aria-hidden="true">{Array.from({ length: 54 }, (_, i) => <i key={i} style={{ height: `${18 + ((i * 31) % 74)}%` }} />)}</div><div className="up-next"><span>UP NEXT</span><div><NextTrack title="HIGHER GROUND" /><NextTrack title="WILD ONES" /><NextTrack title="BUILT DIFFERENT" /></div></div>
  </div>;
}
function NextTrack({ title }) { return <button className="next-track"><span className="mini-art" /><b>{title}</b><small>TRX RADIO</small></button>; }

function PerformanceScreen() {
  return <div className="performance-screen"><ScreenHeading eyebrow="OBDLINK MX+ • CONNECTED" title="Performance" action={<button className="icon-button"><RotateCcw size={20} /></button>} /><div className="performance-tabs"><button>GAUGES</button><button className="active">PERFORMANCE</button><button>DIAGNOSTICS</button><button>OBD SETUP</button><button>SETTINGS</button></div><div className="gauges"><GaugeCard label="RPM" value="2,750" unit="RPM" max="7" /><GaugeCard label="BOOST" value="14.2" unit="PSI" max="30" /><GaugeCard label="SPEED" value="68" unit="MPH" max="140" /></div><div className="vehicle-panel"><div className="tire tire-fl"><b>42</b> PSI<small>FL</small></div><div className="tire tire-fr"><b>41</b> PSI<small>FR</small></div><div className="truck-silhouette"><Car size={112} strokeWidth={1} /><span>TRX</span></div><div className="tire tire-rl"><b>40</b> PSI<small>RL</small></div><div className="tire tire-rr"><b>40</b> PSI<small>RR</small></div><div className="engine-stats"><div><Thermometer size={19} /><span>INTAKE AIR<strong>98°F</strong></span></div><div><Activity size={19} /><span>TRANS TEMP<strong>189°F</strong></span></div></div></div><div className="metric-row"><Metric label="0–60 MPH" value="0.00" unit="s" icon={Zap} /><Metric label="ENGINE" value="194" unit="°F" icon={Wrench} /></div><div className="run-actions"><button className="red-button"><Zap size={18} /> ARM RUN</button><button className="secondary-button"><RotateCcw size={18} /> RESET</button></div></div>;
}
function GaugeCard({ label, value, unit, max }) { return <div className="gauge-card"><div className="dial" style={{ '--value': `${Math.min(94, Number(value.replace(',', '.')) / Number(max) * 100)}%` }}><div><span>{label}</span><strong>{value}</strong><small>{unit}</small></div></div></div>; }
function Metric({ label, value, unit, icon: Icon }) { return <div className="metric"><span className="eyebrow">{label}</span><Icon size={20} /><strong>{value}<small>{unit}</small></strong><div className="sparkline" /></div>; }

function AppsScreen({ search, setSearch, apps, onSettings }) {
  return <div className="apps-screen"><ScreenHeading eyebrow="TRX LAUNCHER" title="All Apps" action={<button className="icon-button" onClick={onSettings}><SlidersHorizontal size={20} /></button>} /><div className="apps-toolbar"><div className="search-box"><Search size={20} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search installed apps" /><X size={17} className={search ? 'clear visible' : 'clear'} onClick={() => setSearch('')} /></div><button className="toolbar-button"><Star size={18} /> FAVORITES</button><button className="toolbar-button"><Menu size={18} /> EDIT</button></div><div className="app-grid">{apps.map(([name, kind, isFavorite]) => <AppTile key={name} name={name} kind={kind} favorite={isFavorite} />)}</div><p className="apps-hint">Hold an app for options <b>•</b> Drag to rearrange favorites</p></div>;
}
function AppTile({ name, kind, favorite }) { return <button className="app-tile"><span className={`app-icon ${kind}`}><AppGlyph kind={kind} /></span>{favorite && <Star size={16} className="tile-star" fill="currentColor" />}<strong>{name}</strong></button>; }
function SettingsScreen({ brightness, setBrightness, theme, setTheme }) {
  const themes = [['TRX Red', 'red'], ['Baja Amber', 'amber'], ['Stealth Silver', 'silver'], ['Hydro Blue', 'blue'], ['Custom', 'custom']];
  return <div className="settings-screen"><ScreenHeading eyebrow="LAUNCHER CONFIGURATION" title="Settings" action={<span className="settings-mark">BUILT TO GO FURTHER <b>///</b></span>} /><div className="settings-layout"><aside className="settings-sidebar">{[['Appearance', Sparkles], ['Navigation', Navigation], ['Media', Music2], ['Vehicle & OBD', Car], ['Launcher', Settings], ['System', SlidersHorizontal]].map(([label, Icon], index) => <button className={index === 0 ? 'active' : ''} key={label}><Icon size={23} /><span>{label}</span></button>)}<div className="sidebar-art"><strong>TRX</strong><small>POWER<br />PURPOSE<br />FREEDOM</small></div></aside><section className="settings-content"><h3>THEME</h3><div className="theme-grid">{themes.map(([label, tone]) => <button key={label} className={`theme-card ${tone} ${theme === label ? 'selected' : ''}`} onClick={() => setTheme(label)}><span className="radio">{theme === label && '✓'}</span><strong>{label}</strong><div className="theme-truck">TRX</div></button>)}</div><div className="settings-row"><div className="setting-label"><SunIcon /><strong>DISPLAY MODE</strong></div><div className="segmented"><button className="active">Auto</button><button>Day</button><button>Night</button></div></div><div className="settings-row"><div className="setting-label"><Sparkles size={21} /><strong>BACKGROUND STYLE</strong></div><div className="background-options"><button className="active">Carbon</button><button>Contour</button><button>Mountain</button></div></div><div className="settings-row"><div className="setting-label"><SunIcon /><strong>ACCENT BRIGHTNESS</strong></div><div className="brightness-control"><input type="range" min="20" max="100" value={brightness} onChange={(event) => setBrightness(Number(event.target.value))} /><b>{brightness}%</b></div></div><ToggleRow icon={AppWindow} title="HERO ARTWORK" subtitle="Show featured vehicle artwork on Home screen" enabled /><ToggleRow icon={Gauge} title="REDUCE MOTION" subtitle="Minimize animations and transitions" /><button className="apply-theme">APPLY THEME</button><div className="preview-heading"><h3>LIVE PREVIEW</h3><span>Shows how your theme will look on the Home screen</span></div><div className="live-preview"><span>72°</span><strong>RAM <em>TRX</em> LAUNCHER</strong><div className="preview-copy">ANY TERRAIN<br />A HIGHER STANDARD</div><div className="preview-buttons"><button>Navigation</button><button>Media</button><button>Vehicle</button></div></div><div className="settings-footer"><span>TRX Launcher 4.2.3</span><button>DEFAULT LAUNCHER</button><button>PERMISSIONS</button><button>CHECK FOR UPDATES</button></div></section></div></div>;
}
function ToggleRow({ icon: Icon, title, subtitle, enabled = false }) { const [on, setOn] = useState(enabled); return <div className="toggle-row"><Icon size={23} /><span><strong>{title}</strong><small>{subtitle}</small></span><button className={on ? 'toggle on' : 'toggle'} onClick={() => setOn(!on)}><i /></button></div>; }
function SunIcon() { return <span className="sun-icon">☼</span>; }

function AppGlyph({ kind }) { const glyphs = { map: <MapPin />, waze: <Navigation />, spotify: <Volume2 />, youtube: <Play fill="currentColor" />, phone: <Smartphone />, messages: <span>•••</span>, chrome: <Compass />, settings: <Settings />, sirius: <Signal />, weather: <Wind />, files: <AppWindow />, camera: <span className="camera-lens" />, obd: <Car />, gmail: <span>M</span>, play: <Play fill="currentColor" />, netflix: <span>N</span> }; return glyphs[kind] || <AppWindow />; }

export default App;

createRoot(document.getElementById('root')).render(<StrictMode><App /></StrictMode>);
