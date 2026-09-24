import { StrictMode, useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { StatusBar as NativeStatusBar, Style } from '@capacitor/status-bar';
import { Activity, Bluetooth, Check, ChevronRight, Gauge, Grid2X2, Heart, Home, Layers3, MapPin, Music2, Navigation, Pause, Play, Radio, Search, Settings, ShieldCheck, SkipBack, SkipForward, SlidersHorizontal, Volume2, X, RotateCcw, Briefcase, CloudSun } from 'lucide-react';
import { currentWeather, native } from './native';
import './styles.css';

const HERO = '/art/trx-alpine.webp';
const ALBUM = '/art/crimson-moon.webp';
const THEMES = {
  hellfire: { name: 'Hellfire Red', accent: '#ed3344', tone: '#791724' },
  titanium: { name: 'Titanium', accent: '#c4cbd3', tone: '#434b57' },
  baja: { name: 'Baja Sand', accent: '#d9b078', tone: '#695031' },
  arctic: { name: 'Arctic Ice', accent: '#8fceee', tone: '#294f6a' },
  night: { name: 'Night Ops', accent: '#a9b5b7', tone: '#263438' }
};
const NAV = [['home','Home',Home],['navigation','Navigation',Navigation],['media','Sonic',Music2],['performance','Dynamics',Gauge],['apps','Orbit',Grid2X2],['settings','Studio',Settings]];
const DEFAULT_NAV = {routingStrategy:'fastest',avoidTolls:false,mapMode:'standard',audioEnabled:true};
function read(key, fallback) { try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; } }
function useStored(key, fallback) {
  const [value,setValue] = useState(() => read(key,fallback));
  useEffect(() => { localStorage.setItem(key,JSON.stringify(value)); },[key,value]);
  return [value,setValue];
}
function legacyString(key, fallback) { const v=localStorage.getItem(key); try { return JSON.parse(v) || fallback; } catch { return v || fallback; } }
function reading(value, digits=0) { return Number.isFinite(value) ? value.toFixed(digits) : '—'; }
function formatTime(ms) { const s=Math.floor((ms || 0)/1000);return Math.floor(s/60)+':'+String(s%60).padStart(2,'0'); }
function useLive() {
  const [live,setLive]=useState({media:null,obd:null,location:null,weather:null});
  const weatherAt=useRef(0);
  useEffect(()=>{
    let alive=true,timer;
    async function refresh(){
      const [media,obd,location]=await Promise.all([native.media(),native.obd(),native.location()]);
      if(!alive)return;
      setLive(v=>({...v,media,obd,location}));
      if(location && Date.now()-weatherAt.current>600000){
        weatherAt.current=Date.now();const weather=await currentWeather(location);
        if(alive && weather)setLive(v=>({...v,weather}));
      }
      if(alive)timer=setTimeout(refresh,1500);
    }
    refresh();return()=>{alive=false;clearTimeout(timer);};
  },[]);
  async function refreshWeather(){weatherAt.current=0;const w=await currentWeather(live.location);if(w)setLive(v=>({...v,weather:w}));return Boolean(w);}
  return {...live,refreshWeather};
}
function App(){
  const [active,setActive]=useState('home');
  const [commissioned,setCommissioned]=useState(localStorage.getItem('trx-apex-commissioned')==='true');
  const [theme,setTheme]=useState(()=>{const t=legacyString('trx-apex-theme','hellfire');return THEMES[t]?t:'hellfire';});
  const [accent,setAccent]=useState(Number(localStorage.getItem('trx-apex-accent')) || 82);
  const [iconScale,setIconScale]=useState(Number(localStorage.getItem('trx-apex-icons')) || 100);
  const [reducedMotion,setReducedMotion]=useState(localStorage.getItem('trx-apex-motion')==='true');
  const [displayMode,setDisplayMode]=useState(()=>legacyString('trx-apex-mode','auto'));
  const [displayProfile,setDisplayProfile]=useState(()=>legacyString('trx-apex-display-profile','auto'));
  const [calibration,setCalibration]=useStored('trx-apex-screen-calibration',{x:0,y:0,inset:0});
  const [visual,setVisual]=useStored('trx-apex-visual-calibration',{blackLevel:100,contrast:104,saturation:104,artwork:100});
  const [preferences,setPreferences]=useStored('trx-apex-nav-preferences',DEFAULT_NAV);
  const [device,setDevice]=useState(null);
  const [now,setNow]=useState(new Date());
  const [notice,setNotice]=useState('');
  const [quick,setQuick]=useState(null);
  const [viewport,setViewport]=useState({width:innerWidth,height:innerHeight,dpr:devicePixelRatio});
  const live=useLive();
  const noticeTimer=useRef();
  function notify(message){setNotice(message);clearTimeout(noticeTimer.current);noticeTimer.current=setTimeout(()=>setNotice(''),5500);}
  async function act(promise,message){const r=await promise;if(r?.error || r?.success===false)notify(r?.error || r?.message || 'This action is not available.');else if(message)notify(message);return r;}
  useEffect(()=>{native.displayInfo().then(setDevice);NativeStatusBar.setOverlaysWebView({overlay:false}).catch(()=>{});NativeStatusBar.setStyle({style:Style.Dark}).catch(()=>{});NativeStatusBar.hide().catch(()=>{});},[]);
  useEffect(()=>{const id=setInterval(()=>setNow(new Date()),30000);const resize=()=>setViewport({width:innerWidth,height:innerHeight,dpr:devicePixelRatio});addEventListener('resize',resize);return()=>{clearInterval(id);removeEventListener('resize',resize);clearTimeout(noticeTimer.current);};},[]);
  useEffect(()=>{for(const [key,val] of Object.entries({'theme':theme,'accent':accent,'icons':iconScale,'motion':reducedMotion,'display-profile':displayProfile,'mode':displayMode}))localStorage.setItem('trx-apex-'+key,String(val));},[theme,accent,iconScale,reducedMotion,displayProfile,displayMode]);
  const day=displayMode==='day'||(displayMode==='auto'&&now.getHours()>=7&&now.getHours()<19);
  const tone=THEMES[theme];
  const style={'--accent':tone.accent,'--tone':tone.tone,'--accent-alpha':Math.max(.3,Math.min(1,accent/100)),'--icon-scale':Math.max(.8,Math.min(1.25,iconScale/100)), '--safe':Math.max(0,calibration.inset || 0)+'px','--offset-x':(calibration.x||0)+'px','--offset-y':(calibration.y||0)+'px', '--art-filter':`contrast(${visual.contrast/100}) saturate(${visual.saturation/100}) brightness(${visual.artwork/100})`,'--black':`rgb(${Math.max(0,100-visual.blackLevel)*.3} ${Math.max(0,100-visual.blackLevel)*.3} ${Math.max(0,100-visual.blackLevel)*.3})`};
  function go(page,shortcut=null){setQuick(shortcut);setActive(page);}
  function route(item){if(!item?.label){notify('Choose a destination first.');return;}act(native.navigate(item.label,item.latitude,item.longitude,item.placeId,theme,tone.accent,accent,{...preferences,dayMode:day}));}
  function finish(){localStorage.setItem('trx-apex-commissioned','true');setCommissioned(true);}
  const shared={live,go,notify,act,route};
  return <div className={`apex-shell theme-${theme} ${day?'day':'night'} profile-${displayProfile} ${reducedMotion?'reduce-motion':''}`} style={style}>
    {!commissioned?<Commissioning finish={finish} act={act}/>:<div className="calibrated-stage">
      <header className="status-bar"><div className="wordmark"><b>TRX</b><em>APEX</em></div><div className="status-right"><Bluetooth className={live.obd?.connected?'connected':''}/><span>{now.toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'})}</span><button aria-label="Refresh weather" onClick={async()=>notify(await live.refreshWeather()?'Weather updated':'Weather requires a current GPS fix and internet.')}><CloudSun/>{live.weather?live.weather.temperature+'°':'—°'}</button></div></header>
      <nav className="command-rail" aria-label="Main navigation">{NAV.map(([id,label,Icon])=><button key={id} aria-label={label} aria-current={active===id?'page':undefined} className={active===id?'active':''} onClick={()=>go(id)}><Icon/><span>{label}</span></button>)}</nav>
      <main className="apex-canvas">
        {active==='home'&&<HomePage {...shared}/>}
        {active==='navigation'&&<NavigationPage {...shared} quick={quick} theme={theme} preferences={preferences} setPreferences={setPreferences} day={day}/>}
        {active==='media'&&<MediaPage media={live.media} act={act} notify={notify}/>}
        {active==='performance'&&<PerformancePage obd={live.obd} act={act}/>}
        {active==='apps'&&<AppsPage act={act} notify={notify}/>}
        {active==='settings'&&<SettingsPage {...{theme,setTheme,accent,setAccent,iconScale,setIconScale,reducedMotion,setReducedMotion,displayMode,setDisplayMode,displayProfile,setDisplayProfile,calibration,setCalibration,visual,setVisual,preferences,setPreferences,device,viewport,act,notify}} obd={live.obd}/>}
      </main>
    </div>}
    {notice&&<div role="status" className="notice">{notice}<button aria-label="Dismiss notice" onClick={()=>setNotice('')}><X/></button></div>}
  </div>;
}
function Commissioning({finish,act}){
  const [step,setStep]=useState(0);const groups=[['location','Location'],['bluetooth','Bluetooth'],['media','Media controls'],['launcher','Default launcher']];
  return <section className="commissioning"><img src={HERO} alt="Red RAM TRX"/><h1>TRX <em>APEX</em></h1><p>Connect your cockpit</p><p>{step+1} of 4 · {groups[step][1]}</p><button className="primary" onClick={async()=>{await act(native.requestPermissionGroup(groups[step][0]));if(step<3)setStep(step+1);else finish();}}>Set up {groups[step][1]}<ChevronRight/></button><button onClick={finish}>Finish later</button></section>;
}
function VehicleStatus({obd,action}){return <button className={'vehicle-status '+(obd?.ecuConnected?'live':'waiting')} onClick={action}><Activity/><span><b>{obd?.ecuConnected?'Vehicle data live':obd?.connected?'Adapter connected':'Vehicle link'}</b><small>{obd?.status || 'Pair OBDLink MX+ to connect'}</small></span><ChevronRight/></button>;}
function HomePage({live,go,act,route}){
  const home=read('trx-apex-nav-favorites',{}).home;
  return <section className="page home-page" aria-label="Home page"><div className="hero-panel"><img className="hero-art" src={HERO} alt="Red RAM TRX in the mountains"/></div><div className="home-deck">
    <button className="drive-card" onClick={()=>home?route(home):go('navigation','home')}><div><Navigation/><span><b>Drive Home</b><small>{home?.label || 'Choose your home address'}</small></span><ChevronRight/></div><img src={HERO} alt=""/></button>
    <div className="home-media panel"><button className="track-link" onClick={()=>go('media')}><img src={live.media?.artwork || ALBUM} alt=""/><span><small>Now playing</small><b>{live.media?.source?live.media.title:'No active media'}</b><small>{live.media?.source?live.media.artist:'Choose a source'}</small></span></button><div className="mini-transport"><button aria-label="Previous track" disabled={!live.media?.canPrevious} onClick={()=>act(native.mediaCommand('previous'))}><SkipBack/></button><button className="primary round" aria-label={live.media?.playing?'Pause':'Play'} onClick={()=>live.media?.hasSession?act(native.mediaCommand('toggle')):go('media')}>{live.media?.playing?<Pause/>:<Play/>}</button><button aria-label="Next track" disabled={!live.media?.canNext} onClick={()=>act(native.mediaCommand('next'))}><SkipForward/></button></div></div>
  </div><div className="quick-row">{[['home','Home',Home],['work','Work',Briefcase],['apps','Apps',Grid2X2]].map(([id,label,Icon])=><button key={id} onClick={()=>go(id==='apps'?'apps':'navigation',id==='apps'?null:id)}><Icon/>{label}</button>)}</div><VehicleStatus obd={live.obd} action={()=>go('performance')}/></section>;
}
function MapPreview({location,theme,mapMode,day,hidden}){
  const ref=useRef();const [error,setError]=useState('Loading live map…');
  useEffect(()=>{
    let alive=true;
    function sync(){if(!ref.current)return;const b=ref.current.getBoundingClientRect();native.mapPreview({visible:!hidden,x:b.x,y:b.y,width:b.width,height:b.height,viewportWidth:innerWidth,latitude:location?.latitude,longitude:location?.longitude,theme,accentColor:THEMES[theme].accent,mapMode,dayMode:day}).then(r=>{if(alive)setError(r?.error || '');});}
    sync();const obs=new ResizeObserver(sync);obs.observe(ref.current);addEventListener('resize',sync);
    return()=>{alive=false;obs.disconnect();removeEventListener('resize',sync);native.mapPreview({visible:false});};
  },[Boolean(location),theme,mapMode,day,hidden]);
  return <div ref={ref} className="map-preview" aria-label="Live map preview"><MapPin/><span>{error || 'Live Google map'}</span>{!location&&<small>Waiting for location</small>}</div>;
}
function NavigationPage({live,quick,theme,preferences,setPreferences,route,act,notify,day}){
  const [destination,setDestination]=useState('');const [selected,setSelected]=useState(null);const [suggestions,setSuggestions]=useState([]);const [error,setError]=useState('');
  const [favorites,setFavorites]=useStored('trx-apex-nav-favorites',{});const [recents,setRecents]=useStored('trx-apex-nav-recents',[]);const [sheet,setSheet]=useState(null);const [saveAs,setSaveAs]=useState(null);const [focused,setFocused]=useState(false);
  const input=useRef();
  function select(item){setDestination(item.label);setSelected(item);setSuggestions([]);setError('');setFocused(false);input.current?.blur();if(saveAs){setFavorites(v=>({...v,[saveAs]:item}));setSaveAs(null);notify('Destination saved.');}}
  function shortcut(key){if(key==='gas'){setDestination('Gas stations');setSelected(null);setFocused(true);input.current?.focus();return;}if(key==='favorites'){setSheet('favorites');return;}if(favorites[key])select(favorites[key]);else {setSaveAs(key);setDestination('');setSelected(null);setFocused(true);input.current?.focus();}}
  useEffect(()=>{if(quick)shortcut(quick);},[]);
  useEffect(()=>{if(destination.trim().length<3 || selected?.label===destination){setSuggestions([]);return;}let alive=true;const id=setTimeout(async()=>{const r=await native.searchDestinations(destination);if(alive){setSuggestions(r?.suggestions || []);setError(r?.error || '');}},300);return()=>{alive=false;clearTimeout(id);};},[destination,selected]);
  function start(){const item=selected || {label:destination.trim(),primary:destination.trim()};if(!item.label)return;setRecents([item,...recents.filter(r=>r.label!==item.label)].slice(0,8));route(item);}
  const pref=(key,val)=>setPreferences(v=>({...v,[key]:val}));
  return <section className="page navigation-page" aria-label="Navigation page"><div className="search-box"><Search/><input ref={input} aria-label="Destination" value={destination} onFocus={()=>setFocused(true)} onChange={e=>{setDestination(e.target.value);setSelected(null);setError('');}} onKeyDown={e=>{if(e.key==='Enter')start();}} placeholder={saveAs?'Search '+saveAs+' address':'Search destination'}/>{destination&&<button aria-label="Clear destination" onClick={()=>{setDestination('');setSelected(null);setSuggestions([]);}}><X/></button>}<button aria-label="Route preferences" onClick={()=>setSheet('preferences')}><SlidersHorizontal/></button></div>
    <div className="map-stage"><MapPreview location={live.location} {...{theme,day}} mapMode={preferences.mapMode} hidden={Boolean(sheet)||focused}/>{focused&&<div className="suggestions"><div className="suggestions-head"><span>{saveAs?'Save '+saveAs+' destination':'Search results'}</span><button aria-label="Close search" onClick={()=>{setFocused(false);input.current?.blur();}}><X/></button></div>{error&&<p role="alert">{error}</p>}{suggestions.map((item,i)=><button key={item.placeId || i} onClick={()=>select(item)}><MapPin/><span><b>{item.primary || item.label}</b><small>{item.secondary}</small></span><ChevronRight/></button>)}{!suggestions.length&&!error&&<p>{destination.length<3?'Enter at least three letters.':'Searching places…'}</p>}<small className="attribution">Google Places</small></div>}</div>
    <div className="quick-row">{[['home','Home',Home],['work','Work',Briefcase],['favorites','Favorites',Heart]].map(([id,label,Icon])=><button key={id} onClick={()=>shortcut(id)}><Icon/>{label}</button>)}</div>
    <div className="selected-destination panel"><MapPin/><span><b>{selected?.primary || destination || 'Choose destination'}</b><small>{selected?.secondary || 'Route and arrival time calculated by Google'}</small></span>{destination&&<button aria-label="Save destination" onClick={()=>setSheet('save')}><Heart/></button>}</div>
    <button className="primary route-start" disabled={!destination.trim()} onClick={start}><Navigation/>Start route</button>
    {sheet&&<Modal title={sheet==='preferences'?'Route preferences':sheet==='save'?'Save destination':'Saved destinations'} close={()=>setSheet(null)}>
      {sheet==='preferences'&&<><Toggle label="Avoid tolls" value={preferences.avoidTolls} setValue={v=>pref('avoidTolls',v)}/><Toggle label="Voice guidance" value={preferences.audioEnabled} setValue={v=>pref('audioEnabled',v)}/><Toggle label="Satellite map" value={preferences.mapMode==='satellite'} setValue={v=>pref('mapMode',v?'satellite':'standard')}/><Toggle label="Prefer shorter routes" value={preferences.routingStrategy==='shortest'} setValue={v=>pref('routingStrategy',v?'shortest':'fastest')}/><Action label="Gas nearby" onClick={()=>{setSheet(null);shortcut('gas');}}/></>}
      {sheet==='save'&&['home','work'].map(key=><Action key={key} label={'Save as '+key} onClick={()=>{setFavorites(v=>({...v,[key]:selected || {label:destination,primary:destination}}));setSheet(null);notify('Saved as '+key);}}/>)}
      {sheet==='favorites'&&<>{['home','work'].map(key=><div className="saved-row" key={key}><Action label={key} detail={favorites[key]?.label || 'Not set'} onClick={()=>{setSheet(null);shortcut(key);}}/><button aria-label={'Change '+key} onClick={()=>{setSheet(null);setSaveAs(key);setDestination('');setSelected(null);setFocused(true);input.current?.focus();}}><SlidersHorizontal/></button></div>)}<h3>Recent</h3>{recents.length?recents.map((item,i)=><Action key={i} label={item.primary || item.label} detail={item.secondary} onClick={()=>{select(item);setSheet(null);}}/>):<p>No recent destinations.</p>}</>}
    </Modal>}
  </section>;
}
function MediaPage({media,act,notify}){
  const [sourceOpen,setSourceOpen]=useState(false);const [apps,setApps]=useState([]);const [loading,setLoading]=useState(false);const [selectedSource,setSelectedSource]=useStored('trx-apex-media-source',null);const [pending,setPending]=useState(false);
  useEffect(()=>{if(!sourceOpen)return;let alive=true;setLoading(true);native.apps().then(r=>{if(alive){setApps((r?.apps||[]).filter(a=>/music|spotify|youtube|vlc|tidal|audible|radio|podcast|plex|pandora|soundcloud/i.test(a.name+' '+a.packageName)));setLoading(false);}});return()=>{alive=false;};},[sourceOpen]);
  async function play(){if(media?.hasAccess===false){await act(native.requestPermissionGroup('media'));return;}if(media?.hasSession){act(native.mediaCommand('toggle'));return;}if(selectedSource?.packageName)act(native.launchApp(selectedSource.packageName));else setSourceOpen(true);}
  async function favorite(){if(pending)return;setPending(true);const r=await act(native.mediaCommand('favorite'));if(r?.success)notify('Like request sent to your player.');setPending(false);}
  const source=media?.sourceName || selectedSource?.name || 'Choose source';const duration=media?.durationMs || 0;
  return <section className="page media-page" aria-label="Sonic page"><div className="media-top"><div className={'record '+(media?.playing?'playing':'')}><img src={media?.artwork || ALBUM} alt="Album artwork"/></div><button className="source-pill" onClick={()=>setSourceOpen(true)}><Radio/>{source}<ChevronRight/></button></div>
    <div className="track-editorial"><h1>{media?.hasSession?media.title:'Ready when you are'}</h1><p>{media?.hasSession?media.artist:'Open a media source to start listening'}</p></div>
    <div className="progress-row"><span>{formatTime(media?.positionMs)}</span><input aria-label="Track position" type="range" min="0" max={Math.max(1,duration)} value={Math.min(media?.positionMs||0,duration)} disabled={!media?.canSeek} onChange={e=>act(native.mediaCommand('seek',Number(e.target.value)))}/><span>{formatTime(duration)}</span></div>
    <div className="transport"><button aria-label="Previous track" disabled={!media?.canPrevious} onClick={()=>act(native.mediaCommand('previous'))}><SkipBack/></button><button className="primary round" aria-label={media?.playing?'Pause':'Play'} onClick={play}>{media?.playing?<Pause/>:<Play/>}</button><button aria-label="Next track" disabled={!media?.canNext} onClick={()=>act(native.mediaCommand('next'))}><SkipForward/></button><button aria-label={media?.liked?'Unlike track':'Like track'} aria-pressed={Boolean(media?.liked)} className={media?.liked?'liked':''} disabled={!media?.canFavorite||pending} title={media?.canFavorite?'Like in current player':'Player does not expose a like action'} onClick={favorite}><Heart fill={media?.liked?'currentColor':'none'}/></button></div>
    <label className="volume-row"><Volume2/><span>Media volume</span><input aria-label="Media volume" type="range" value={media?.volumePercent ?? 0} disabled={!media?.volumeAvailable} onChange={e=>act(native.mediaCommand('volume',Number(e.target.value)))}/><b>{media?.volumePercent ?? '—'}%</b></label>
    <div className="queue-panel panel"><div className="queue-heading"><b>Up next</b>{!media?.canFavorite&&<small>Like available in player</small>}</div><div className="queue-list">{media?.queue?.length?media.queue.map((item,i)=><button key={item.id ?? i} disabled={!media.canQueue} onClick={()=>act(native.mediaCommand('queue',0,i,item.id))}><img src={item.artwork || ALBUM} alt=""/><span><b>{item.title}</b><small>{item.artist}</small></span><ChevronRight/></button>):<div className="empty-state"><Music2/><span>{media?.hasSession?'This player has not shared its queue.':'Your player’s queue will appear here.'}</span><button onClick={()=>media?.source?act(native.launchApp(media.source)):setSourceOpen(true)}>Open player</button></div>}</div></div>
    {sourceOpen&&<Modal title="Media sources" close={()=>setSourceOpen(false)}>{loading?<p>Loading installed players…</p>:apps.length?apps.map(app=><button className="source-app" key={app.packageName} onClick={()=>{setSelectedSource({name:app.name,packageName:app.packageName});act(native.launchApp(app.packageName));setSourceOpen(false);}}>{app.icon?<img src={app.icon} alt=""/>:<Radio/>}<b>{app.name}</b><ChevronRight/></button>):<p>No media players found. Open Orbit to choose another app.</p>}<Action label="Media control access" detail={media?.hasAccess?'Enabled':'Enable track information and controls'} onClick={()=>act(native.requestPermissionGroup('media'))}/></Modal>}
  </section>;
}
function PerformancePage({obd,act}){
  const [details,setDetails]=useState(false);
  const metrics=[['Boost',obd?.boostPsi,'PSI',1],['Coolant',obd?.coolantF,'°F',0],['Battery',obd?.batteryV,'V',1],['Intake',obd?.intakeF,'°F',0]];
  return <section className="page performance-page" aria-label="Dynamics page"><div className="gauge-row"><div><span>RPM</span><strong>{reading(obd?.rpm)}</strong><small>{obd?.ecuConnected?'Engine data':'Awaiting ECU'}</small></div><div><span>Speed</span><strong>{reading(obd?.speedMph)}</strong><small>MPH · OBD</small></div></div><div className="performance-hero"><img className="hero-art" src={HERO} alt="Red RAM TRX"/></div><div className="telemetry-grid">{metrics.map(([label,value,unit,digits])=><div className="panel" key={label}><span>{label}</span><strong>{reading(value,digits)}</strong><small>{unit}</small></div>)}</div><VehicleStatus obd={obd} action={()=>setDetails(true)}/>{details&&<Modal title="OBDLink MX+ diagnostics" close={()=>setDetails(false)}><p>{obd?.status || 'Not connected'}</p><dl className="diagnostics"><dt>Adapter</dt><dd>{obd?.deviceName || 'OBDLink MX+'}</dd><dt>Protocol</dt><dd>{obd?.protocol || '—'}</dd><dt>Live PIDs</dt><dd>{obd?.livePidCount || 0}</dd><dt>Last response</dt><dd>{obd?.ageMs==null?'None':Math.round(obd.ageMs/1000)+'s ago'}</dd><dt>Engine load</dt><dd>{reading(obd?.engineLoad)}%</dd><dt>Throttle</dt><dd>{reading(obd?.throttle)}%</dd><dt>Fuel</dt><dd>{reading(obd?.fuelLevel)}%</dd></dl><p>Gear and transmission temperature require verified RAM-specific data and are not displayed as live readings.</p><Action label="Bluetooth permission" onClick={()=>act(native.requestPermissionGroup('bluetooth'))}/><Action label="Pair adapter" onClick={()=>act(native.settings('bluetooth'))}/><Action label="Reconnect" onClick={()=>act(native.reconnectObd())}/><p>Keep other OBD apps disconnected while APEX uses the adapter.</p><details><summary>Recent adapter responses</summary><pre>{obd?.diagnostics || 'No responses recorded yet.'}</pre></details></Modal>}</section>;
}
function AppsPage({act,notify}){
  const [apps,setApps]=useState([]);const [loaded,setLoaded]=useState(false);const [query,setQuery]=useState('');const [editing,setEditing]=useState(false);const [all,setAll]=useState(false);const [context,setContext]=useState(null);
  const [favorites,setFavorites]=useStored('trx-apex-orbit-favorites',[]);
  useEffect(()=>{let alive=true;native.apps().then(r=>{if(!alive)return;const unique=[...new Map((r?.apps||[]).map(a=>[a.packageName,a])).values()];setApps(unique);setLoaded(true);if(localStorage.getItem('trx-apex-favorites-initialized')!=='true'){if(!favorites.length)setFavorites(unique.filter(a=>/maps|music|phone/i.test(a.name)).slice(0,3).map(a=>a.packageName));localStorage.setItem('trx-apex-favorites-initialized','true');}});return()=>{alive=false;};},[]);
  const selected=favorites.map(id=>apps.find(a=>a.packageName===id)).filter(Boolean).slice(0,6);
  const filtered=useMemo(()=>apps.filter(a=>a.name.toLowerCase().includes(query.toLowerCase())).sort((a,b)=>a.name.localeCompare(b.name)),[apps,query]);
  function toggle(app){if(favorites.includes(app.packageName))setFavorites(favorites.filter(id=>id!==app.packageName));else if(favorites.length<6)setFavorites([...favorites,app.packageName]);else notify('Choose up to six favorites. Remove one first.');}
  const expanded=all||Boolean(query)||editing;
  return <section className={'page apps-page '+(expanded?'expanded':'')} aria-label="Orbit page"><div className="search-box"><Search/><input aria-label="Search apps" placeholder="Search apps" value={query} onChange={e=>setQuery(e.target.value)}/>{query&&<button aria-label="Clear app search" onClick={()=>setQuery('')}><X/></button>}<button aria-label={editing?'Finish editing favorites':'Edit favorites'} className={editing?'active':''} onClick={()=>setEditing(!editing)}>{editing?<Check/>:<SlidersHorizontal/>}</button></div>
    {!expanded&&<div className="app-orbit"><div className="orbit-emblem">TRX<small>Favorites</small></div>{selected.map((app,i)=>{const angle=(i/Math.max(3,selected.length))*2*Math.PI+Math.PI;return <AppButton key={app.packageName} app={app} style={{left:(selected.length===1?50:50+Math.cos(angle)*35)+'%',top:(selected.length===1?72:50+Math.sin(angle)*29)+'%'}} press={()=>act(native.launchApp(app.packageName))} hold={()=>setContext(app)}/>;})}{!selected.length&&<button className="empty-favorites" onClick={()=>setEditing(true)}>Add favorites</button>}</div>}
    <div className="apps-library panel"><div className="library-heading"><b>{editing?'Tap apps to select favorites':'Installed apps'}</b><button onClick={()=>{setAll(!all);setQuery('');}}>{all?'Show favorites':'All apps'}</button></div><div className="apps-grid">{filtered.map(app=><AppButton key={app.packageName} app={app} selected={editing&&favorites.includes(app.packageName)} press={()=>editing?toggle(app):act(native.launchApp(app.packageName))} hold={()=>setContext(app)}/>)}{!filtered.length&&<p>{loaded?'No matching apps.':'Loading installed apps…'}</p>}</div></div>
    {context&&<Modal title={context.name} close={()=>setContext(null)}><Action label={favorites.includes(context.packageName)?'Remove favorite':'Add favorite'} onClick={()=>{toggle(context);setContext(null);}}/><Action label="App information" onClick={()=>{act(native.appAction(context.packageName,'info'));setContext(null);}}/><Action label="Uninstall app" detail="Android will ask you to confirm" onClick={()=>{act(native.appAction(context.packageName,'uninstall'));setContext(null);}}/></Modal>}
  </section>;
}
function AppButton({app,style,press,hold,selected}){
  const timer=useRef();const start=useRef();const cancelled=useRef(false);const held=useRef(false);
  useEffect(()=>()=>clearTimeout(timer.current),[]);
  function down(e){held.current=false;cancelled.current=false;start.current={x:e.clientX,y:e.clientY};timer.current=setTimeout(()=>{held.current=true;hold?.();},650);}
  function move(e){if(start.current&&Math.hypot(e.clientX-start.current.x,e.clientY-start.current.y)>10){cancelled.current=true;clearTimeout(timer.current);}}
  return <button className={'app-tile '+(selected?'selected':'')} style={style} onPointerDown={down} onPointerMove={move} onPointerUp={()=>clearTimeout(timer.current)} onPointerCancel={()=>{cancelled.current=true;clearTimeout(timer.current);}} onClick={()=>{if(!held.current&&!cancelled.current)press();}} onContextMenu={e=>e.preventDefault()}><span className="app-icon">{app.icon?<img src={app.icon} alt=""/>:<Grid2X2/>}</span><b>{app.name}</b>{selected&&<Check className="favorite-check"/>}</button>;
}
function SettingsPage(p){
  const {theme,setTheme,accent,setAccent,iconScale,setIconScale,reducedMotion,setReducedMotion,displayMode,setDisplayMode,displayProfile,setDisplayProfile,calibration,setCalibration,visual,setVisual,preferences,setPreferences,device,viewport,act,obd}=p;
  const [sheet,setSheet]=useState(null);
  return <section className="page settings-page" aria-label="Studio page"><div className="studio-hero"><img className="hero-art" src={HERO} alt="Theme preview, red RAM TRX"/></div><div className="theme-panel panel"><span>Theme color</span><div className="theme-swatches">{Object.entries(THEMES).map(([id,t])=><button key={id} aria-label={t.name+' theme'} aria-pressed={theme===id} className={theme===id?'selected':''} onClick={()=>setTheme(id)}><i style={{background:`linear-gradient(135deg,${t.accent},${t.tone})`}}/><b>{t.name}</b></button>)}</div></div><div className="studio-rows panel"><Action label="Display profile" detail={displayProfile} onClick={()=>setSheet('display')}/><Range label="Accent brightness" value={accent} set={setAccent} min={30} max={100} suffix="%"/><Range label="Icon size" value={iconScale} set={setIconScale} min={80} max={125} suffix="%"/><Action label="Vehicle link" detail={obd?.ecuConnected?'Live':obd?.connected?'Adapter connected':'Not connected'} onClick={()=>setSheet('vehicle')}/></div><div className="settings-actions"><button onClick={()=>setSheet('calibration')}><SlidersHorizontal/>Screen calibration</button><button onClick={()=>setSheet('system')}><Settings/>System</button></div>
    {sheet&&<Modal title={{display:'Display preferences',vehicle:'Vehicle connection',calibration:'Screen calibration',visual:'Artwork calibration',system:'System',navigation:'Navigation'}[sheet]} close={()=>setSheet(null)}>
      {sheet==='display'&&<><p>Layout always fits the measured app window. Profiles control calibration behavior.</p><div className="choice-row">{['auto','phone','uconnect','custom'].map(v=><button key={v} className={v===displayProfile?'active':''} onClick={()=>{setDisplayProfile(v);if(v==='phone')setVisual({blackLevel:100,contrast:100,saturation:100,artwork:100});if(v==='uconnect')setVisual({blackLevel:100,contrast:104,saturation:104,artwork:100});}}>{v}</button>)}</div><h3>Lighting</h3><div className="choice-row">{['day','night','auto'].map(v=><button key={v} className={displayMode===v?'active':''} onClick={()=>setDisplayMode(v)}>{v}</button>)}</div><Toggle label="Reduce animation" value={reducedMotion} setValue={setReducedMotion}/><Action label="Artwork calibration" onClick={()=>setSheet('visual')}/></>}
      {sheet==='vehicle'&&<><p>{obd?.status||'Pair your OBDLink MX+ in Android Bluetooth.'}</p><Action label="Bluetooth permission" onClick={()=>act(native.requestPermissionGroup('bluetooth'))}/><Action label="Pair OBDLink MX+" onClick={()=>act(native.settings('bluetooth'))}/><Action label="Reconnect adapter" onClick={()=>act(native.reconnectObd())}/><p>Detailed live PIDs and adapter responses are available on Dynamics.</p></>}
      {sheet==='system'&&<><Action label="Default launcher" onClick={()=>act(native.requestPermissionGroup('launcher'))}/><Action label="Media control access" onClick={()=>act(native.requestPermissionGroup('media'))}/><Action label="App permissions" onClick={()=>act(native.settings('app'))}/><Action label="Navigation defaults" onClick={()=>setSheet('navigation')}/><p>TRX APEX · {__APP_VERSION__}</p></>}
      {sheet==='navigation'&&<><Toggle label="Voice guidance" value={preferences.audioEnabled} setValue={v=>setPreferences({...preferences,audioEnabled:v})}/><Toggle label="Avoid tolls" value={preferences.avoidTolls} setValue={v=>setPreferences({...preferences,avoidTolls:v})}/><Toggle label="Satellite map" value={preferences.mapMode==='satellite'} setValue={v=>setPreferences({...preferences,mapMode:v?'satellite':'standard'})}/></>}
      {sheet==='calibration'&&<><div className="calibration-target"><b>{viewport.width} × {viewport.height}</b><span>CSS pixels · DPR {viewport.dpr.toFixed(2)}</span><small>{device?`${device.widthPixels} × ${device.heightPixels} Android window · ${device.densityDpi} DPI`:'Device metrics unavailable in browser'}</small></div><Range label="Horizontal offset" value={calibration.x||0} set={v=>setCalibration({...calibration,x:v})} min={-40} max={40} suffix=" px"/><Range label="Vertical offset" value={calibration.y||0} set={v=>setCalibration({...calibration,y:v})} min={-40} max={40} suffix=" px"/><Range label="Safe edge" value={calibration.inset||0} set={v=>setCalibration({...calibration,inset:v})} min={0} max={30} suffix=" px"/><button className="primary" onClick={()=>setCalibration({x:0,y:0,inset:0})}><RotateCcw/>Reset geometry</button></>}
      {sheet==='visual'&&<><img className="visual-preview hero-art" src={HERO} alt="Artwork calibration preview"/>{[['Black level','blackLevel',70,100],['Contrast','contrast',90,130],['Color','saturation',80,130],['Artwork brightness','artwork',80,125]].map(([label,key,min,max])=><Range key={key} label={label} value={visual[key]} set={v=>{setVisual({...visual,[key]:v});setDisplayProfile('custom');}} min={min} max={max} suffix="%"/>)}<button onClick={()=>setVisual({blackLevel:100,contrast:104,saturation:104,artwork:100})}>Reset artwork calibration</button></>}
    </Modal>}
  </section>;
}
function Modal({title,close,children}){useEffect(()=>{const key=e=>{if(e.key==='Escape')close();};addEventListener('keydown',key);return()=>removeEventListener('keydown',key);},[close]);return <div className="modal-scrim" onClick={close}><section className="modal" role="dialog" aria-modal="true" aria-label={title} onClick={e=>e.stopPropagation()}><header><h2>{title}</h2><button aria-label="Close dialog" onClick={close}><X/></button></header><div className="modal-body">{children}</div></section></div>;}
function Action({label,detail,onClick}){return <button className="action-row" onClick={onClick}><span><b>{label}</b>{detail&&<small>{detail}</small>}</span><ChevronRight/></button>;}
function Range({label,value,set,min,max,suffix}){return <label className="range-row"><span>{label}</span><input aria-label={label} type="range" min={min} max={max} value={value} onChange={e=>set(Number(e.target.value))}/><b>{value}{suffix}</b></label>;}
function Toggle({label,value,setValue}){return <button className="action-row" role="switch" aria-checked={Boolean(value)} onClick={()=>setValue(!value)}><b>{label}</b><span className={'switch '+(value?'on':'')}/></button>;}
createRoot(document.getElementById('root')).render(<StrictMode><App/></StrictMode>);
