import { StrictMode, useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { StatusBar as NativeStatusBar, Style } from '@capacitor/status-bar';
import { Activity, ArrowLeft, Bluetooth, Check, ChevronRight, Gauge, Grid2X2, Heart, Home, MapPin, Music2, Navigation, Pause, Play, Radio, Search, Settings, SkipBack, SkipForward, SlidersHorizontal, Volume2, X, RotateCcw, Briefcase, CloudSun, Phone, PanelLeft } from 'lucide-react';
import { currentWeather, native } from './native';
import './styles.css';
import './apex-instrument.css';
import './surface-styles.css';

const HERO = '/art/trx-alpine.webp';
const ALBUM = '/art/crimson-moon.webp';
const SPLASH = '/art/first-run-splash.webp';
const THEMES = {
  hellfire: { name: 'Hellfire Red', accent: '#f04450', tone: '#76202b' },
  titanium: { name: 'Titanium', accent: '#d2d9dc', tone: '#535d62' },
  baja: { name: 'Baja Sand', accent: '#dcae74', tone: '#705338' },
  arctic: { name: 'Arctic Ice', accent: '#88ccea', tone: '#315e78' },
  night: { name: 'Night Ops', accent: '#a9c0bc', tone: '#344f50' }
};
const SURFACES = {
  charcoal: {name:'Charcoal', color:'#30383d'},
  dark: {name:'Dark', color:'#171d22'},
  black: {name:'All Black', color:'#070809'},
  carbon: {name:'Carbon', color:'#1b2022'}
};
const NAV = [['home','Home',Home],['navigation','Navigation',Navigation],['media','Media',Music2],['performance','Performance',Gauge],['apps','Apps',Grid2X2],['settings','Settings',Settings]];
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
  const [live,setLive]=useState({media:null,obd:null,location:null,weather:read('trx-apex-last-weather',null)});
  const weatherAt=useRef(0);
  const weatherReady=useRef(Boolean(read('trx-apex-last-weather',null)));
  useEffect(()=>{
    let alive=true,timer;
    async function refresh(){
      const [media,obd,location]=await Promise.all([native.media(),native.obd(),native.location()]);
      if(!alive)return;
      setLive(v=>({...v,media,obd,location}));
      if(Date.now()-weatherAt.current>(weatherReady.current?600000:15000)){
        weatherAt.current=Date.now();const weather=await currentWeather(location);
        if(alive && weather){weatherReady.current=true;setLive(v=>({...v,weather}));localStorage.setItem('trx-apex-last-weather',JSON.stringify(weather));}
      }
      if(alive)timer=setTimeout(refresh,1500);
    }
    refresh();return()=>{alive=false;clearTimeout(timer);};
  },[]);
  async function refreshWeather(){weatherAt.current=Date.now();const w=await currentWeather(live.location);if(w){weatherReady.current=true;setLive(v=>({...v,weather:w}));localStorage.setItem('trx-apex-last-weather',JSON.stringify(w));}return Boolean(w);}
  return {...live,refreshWeather};
}
function App(){
  const [active,setActive]=useState('home');
  const [previous,setPrevious]=useState('home');
  const [splitOpen,setSplitOpen]=useState(false);
  const [splitFirst,setSplitFirst]=useState(null);
  const [railEnabled,setRailEnabled]=useStored('trx-apex-floating-rail',true);
  const [railPrompt,setRailPrompt]=useState(()=>localStorage.getItem('trx-apex-commissioned')==='true'&&localStorage.getItem('trx-apex-rail-setup-v526')!=='done');
  const [railCapabilities,setRailCapabilities]=useState({overlay:false,back:false});
  const [commissioned,setCommissioned]=useState(localStorage.getItem('trx-apex-commissioned')==='true');
  const [theme,setTheme]=useState(()=>{const t=legacyString('trx-apex-theme','hellfire');return THEMES[t]?t:'hellfire';});
  const [surface,setSurface]=useState(()=>{const s=legacyString('trx-apex-surface','charcoal');return SURFACES[s]?s:'charcoal';});
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
  useEffect(()=>{for(const [key,val] of Object.entries({'theme':theme,'surface':surface,'accent':accent,'icons':iconScale,'motion':reducedMotion,'display-profile':displayProfile,'mode':displayMode}))localStorage.setItem('trx-apex-'+key,String(val));},[theme,surface,accent,iconScale,reducedMotion,displayProfile,displayMode]);
  useEffect(()=>{
    const resume=async()=>{
      const caps=await native.railCapabilities();
      setRailCapabilities(caps);
      if(railEnabled&&caps?.overlay)await native.floatingRail(true,tone.accent,surface);
      if(railPrompt&&caps?.overlay){setRailEnabled(true);setRailPrompt(false);localStorage.setItem('trx-apex-rail-setup-v526','done');}
      const action=await native.railDestination();
      if(NAV.some(([id])=>id===action?.page))go(action.page);
    };
    resume();addEventListener('focus',resume);
    return()=>removeEventListener('focus',resume);
  },[railEnabled,railPrompt,theme,surface]);
  const day=displayMode==='day'||(displayMode==='auto'&&now.getHours()>=7&&now.getHours()<19);
  const tone=THEMES[theme];
  const style={'--accent':tone.accent,'--tone':tone.tone,'--accent-alpha':Math.max(.3,Math.min(1,accent/100)),'--icon-scale':Math.max(.8,Math.min(1.25,iconScale/100)), '--safe':Math.max(0,calibration.inset || 0)+'px','--offset-x':(calibration.x||0)+'px','--offset-y':(calibration.y||0)+'px', '--art-filter':`contrast(${visual.contrast/100}) saturate(${visual.saturation/100}) brightness(${visual.artwork/100})`,'--black':`rgb(${Math.max(0,100-visual.blackLevel)*.3} ${Math.max(0,100-visual.blackLevel)*.3} ${Math.max(0,100-visual.blackLevel)*.3})`};
  function go(page,shortcut=null){if(page!==active)setPrevious(active);setQuick(shortcut);setActive(page);setSplitOpen(false);}
  function back(){if(splitOpen){setSplitOpen(false);return;}go(previous===active?'home':previous);}
  function route(item){if(!item?.label){notify('Choose a destination first.');return;}act(native.navigate(item.label,item.latitude,item.longitude,item.placeId,theme,tone.accent,accent,{...preferences,dayMode:day,surface}));}
  function finish(){localStorage.setItem('trx-apex-commissioned','true');localStorage.setItem('trx-apex-rail-setup-v526','done');setCommissioned(true);}
  const shared={live,go,notify,act,route,theme,day,preferences};
  return <div className={`apex-shell theme-${theme} surface-${surface} ${day?'day':'night'} profile-${displayProfile} ${reducedMotion?'reduce-motion':''}`} style={style}>
    {!commissioned?<Commissioning finish={finish} act={act}/>:<div className="calibrated-stage">
      <header className="status-bar"><div className="wordmark"><b>TRX</b><em>APEX</em></div><div className="status-right"><Bluetooth className={live.obd?.connected?'connected':''}/><span>{now.toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'})}</span><button aria-label="Refresh weather" onClick={async()=>notify(await live.refreshWeather()?'Weather updated':'Weather requires a current GPS fix and internet.')}><CloudSun/>{live.weather?live.weather.temperature+'°':'—°'}</button>{active==='settings'&&<button aria-label="Close settings" onClick={()=>go('home')}><X/></button>}</div></header>
      <nav className="command-rail" aria-label="Main navigation">{NAV.map(([id,label,Icon])=><button key={id} aria-label={label} aria-current={active===id?'page':undefined} className={active===id?'active':''} onClick={()=>go(id)}><Icon/><span>{label}</span></button>)}</nav>
      <main className="apex-canvas">
        {active==='home'&&<HomePage {...shared}/>}
        {active==='navigation'&&<NavigationPage {...shared} quick={quick} theme={theme} preferences={preferences} setPreferences={setPreferences} day={day}/>}
        {active==='media'&&<MediaPage media={live.media} act={act} notify={notify}/>}
        {active==='performance'&&<PerformancePage obd={live.obd} act={act}/>}
        {active==='apps'&&<AppsPage act={act} notify={notify} quick={quick} openSplit={app=>{setSplitFirst(app||null);setSplitOpen(true);}}/>}
        {active==='settings'&&<SettingsPage {...{theme,setTheme,surface,setSurface,accent,setAccent,iconScale,setIconScale,reducedMotion,setReducedMotion,displayMode,setDisplayMode,displayProfile,setDisplayProfile,calibration,setCalibration,visual,setVisual,preferences,setPreferences,device,viewport,act,notify,railEnabled,setRailEnabled,railCapabilities}} obd={live.obd}/>}
      </main>
      {active!=='home'&&<button className="floating-back" aria-label="Back; hold for Home" onPointerDown={e=>{const button=e.currentTarget;button.dataset.held='';button._hold=setTimeout(()=>{button.dataset.held='yes';go('home');},650);}} onPointerUp={e=>{clearTimeout(e.currentTarget._hold);if(e.currentTarget.dataset.held!=='yes')back();}} onPointerCancel={e=>clearTimeout(e.currentTarget._hold)} onClick={e=>{if(e.detail===0)back();}} onKeyDown={e=>{if(e.key==='Escape'){e.preventDefault();back();}}}><ArrowLeft/></button>}
      {active==='apps'&&splitOpen&&<SplitPicker first={splitFirst} close={()=>setSplitOpen(false)} act={act} notify={notify}/>}
      {railPrompt&&<Modal title="Floating navigation" close={()=>{setRailPrompt(false);localStorage.setItem('trx-apex-rail-setup-v526','done');}}><p>Keep the TRX APEX rail over other Android apps. Android asks you to allow Display over other apps. Back in other apps also needs optional Accessibility access.</p><Action label="Allow floating rail" detail="Opens Android Display over other apps" onClick={()=>act(native.requestPermissionGroup('overlay'))}/><Action label="Set up Back button" detail="Optional Android Accessibility control" onClick={()=>act(native.requestPermissionGroup('back'))}/><Action label="Later" onClick={()=>{setRailPrompt(false);setRailEnabled(false);localStorage.setItem('trx-apex-rail-setup-v526','done');}}/></Modal>}
    </div>}
    {notice&&<div role="status" className="notice">{notice}<button aria-label="Dismiss notice" onClick={()=>setNotice('')}><X/></button></div>}
  </div>;
}
function Commissioning({finish,act}){
  const [step,setStep]=useState(-1);
  const groups=[['location','Location','Weather, nearby map and route origin','Only while you use navigation'],['bluetooth','Bluetooth','Connect your paired OBDLink MX+ adapter','The engine data remains unavailable until the ECU responds'],['media','Media controls','Show and control your active music player','Android may open notification access settings'],['launcher','Default launcher','Open TRX APEX when your Ottocast starts','You can change the default later in Android settings'],['overlay','Floating navigation','Keep the compact TRX APEX rail over Android apps','Android must grant Display over other apps'],['back','Back in other apps','Make the floating rail Back button work inside Android apps','Optional Accessibility access; TRX APEX does not read screen content']];
  const [busy,setBusy]=useState(false),[result,setResult]=useState('');
  function next(){setResult('');if(step>=groups.length-1)finish();else setStep(step+1);}
  return <section className="commissioning" aria-label="First run setup"><div className="commission-art" style={{backgroundImage:`url(${SPLASH})`}} aria-hidden="true"/><div className="commission-identity"><span className="splash-overline">6.2L SUPERCHARGED · TRX COCKPIT</span><h1>TRX <em>APEX</em></h1><span className="splash-streak"/></div><div className="commission-controls"><div className="commission-progress" aria-label="Setup progress">{groups.map((_,i)=><span key={i} className={i<=step?'active':''}/>)}</div>{step<0?<><h2>Your truck. Your cockpit.</h2><p>A clear, connected dashboard made for the Uconnect display.</p><button className="primary" onClick={next}>Begin setup <ChevronRight/></button><button onClick={finish}>Enter without setup</button></>:<><small>STEP {step+1} OF {groups.length}</small><h2>{groups[step][1]}</h2><p>{groups[step][2]}</p><small>{groups[step][3]}</small>{result&&<span role="status" className="permission-result">{result}</span>}<button className="primary" disabled={busy} onClick={async()=>{setBusy(true);const r=await act(native.requestPermissionGroup(groups[step][0]));setBusy(false);if(r?.error){setResult(r.error);return;}if(['launcher','media','overlay','back'].includes(groups[step][0])&&!r?.granted){setResult('Android settings opened. Return here when ready.');return;}setResult(r?.granted?'Access granted.':'Access not granted. You can continue.');}}>Enable {groups[step][1]}</button><button onClick={next}>{step===groups.length-1?'Enter launcher':'Continue'} <ChevronRight/></button></>}</div></section>;
}
function SplitPicker({first,close,act,notify}){
  const [apps,setApps]=useState([]);useEffect(()=>{native.apps().then(r=>setApps(r?.apps||[]));},[]);
  const [left,setLeft]=useState(first);
  return <div className="modal-scrim" onClick={close}><section className="modal split-picker" role="dialog" aria-modal="true" aria-label="Pair two apps" onClick={e=>e.stopPropagation()}><header><h2>Pair two apps</h2><button aria-label="Close dialog" onClick={close}><X/></button></header><p>{left?`${left.name} selected. Choose the second app.`:'Choose the first app, then the second.'} Android and Ottocast decide whether the pair opens side by side. If one opens full screen, use Android Recents → Split screen.</p><div className="split-options">{apps.length?apps.filter(app=>app.packageName!==left?.packageName).map(app=><button key={app.packageName} onClick={async()=>{if(!left){setLeft(app);return;}const r=await act(native.startAppPair(left.packageName,app.packageName));if(r?.success)notify(r.message);close();}}>{app.icon?<img src={app.icon} alt=""/>:<Grid2X2/>}<span>{app.name}</span></button>):<p>No launchable apps found.</p>}</div>{left&&<button onClick={()=>setLeft(null)}>Change first app</button>}</section></div>;
}
function VehicleStatus({obd,action}){return <button className={'vehicle-status '+(obd?.ecuConnected?'live':'waiting')} onClick={action}><Activity/><span><b>{obd?.ecuConnected?'Vehicle data live':obd?.connected?'Adapter connected':'Vehicle link'}</b><small>{obd?.status || 'Pair OBDLink MX+ to connect'}</small></span><ChevronRight/></button>;}
function HomePage({live,go,act,theme,day,preferences}){
  return <section className="page home-page cockpit-home" aria-label="Home page">
    <div className="hero-panel"><img className="hero-art" src={HERO} alt="Red RAM TRX in the mountains"/></div>
    <div className="home-information"><div className="home-map panel"><MapPreview location={live.location} theme={theme} day={day} mapMode={preferences.mapMode}/><button className="map-search" onClick={()=>go('navigation')}><Search/>Search destination <ChevronRight/></button></div>
      <button className={'weather-card panel '+(live.weather?.condition?.toLowerCase().replaceAll(' ','-')||'waiting')} onClick={async()=>{if(!live.location)await act(native.requestPermissionGroup('location'));else await live.refreshWeather();}} aria-label="Refresh weather or enable location"><span className="weather-title"><CloudSun/> WEATHER</span><strong>{live.weather?live.weather.temperature+'°':'—°'}</strong><span>{live.weather?.condition||'Location needed'}</span><small>{live.weather?'Tap to refresh':'Tap to enable location'}</small></button></div>
    <div className="home-launch" aria-label="Quick launch">{[['navigation','Maps',Navigation],['media','Media',Music2],['performance','OBD',Gauge],['apps','Phone',Phone]].map(([id,label,Icon])=><button key={label} onClick={()=>go(id,label==='Phone'?'phone':null)}><Icon/><span>{label}</span></button>)}</div>
    <div className="home-now panel"><button className="home-now-track" onClick={()=>go('media')}><img src={live.media?.artwork||ALBUM} alt=""/><span><small>NOW PLAYING</small><b>{live.media?.hasSession?live.media.title:'Choose a media source'}</b><small>{live.media?.hasSession?live.media.artist:'Open Media to connect'}</small></span></button><div className="home-now-controls"><button aria-label="Previous track" disabled={!live.media?.canPrevious} onClick={()=>act(native.mediaCommand('previous'))}><SkipBack/></button><button className="primary round" aria-label={live.media?.playing?'Pause':'Play'} onClick={()=>live.media?.hasSession?act(native.mediaCommand('toggle')):go('media')}>{live.media?.playing?<Pause/>:<Play/>}</button><button aria-label="Next track" disabled={!live.media?.canNext} onClick={()=>act(native.mediaCommand('next'))}><SkipForward/></button></div></div>
    <VehicleStatus obd={live.obd} action={()=>go('performance')}/>
  </section>;
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
function AudioSpectrum({playing,act}){
  const [enabled,setEnabled]=useState(false),[data,setData]=useState(null),[reason,setReason]=useState('');
  const STYLES=['Bars','Mirror','Wave','Orbit'];
  const [visualStyle,setVisualStyle]=useStored('trx-apex-visualizer-style','Bars');
  useEffect(()=>{let alive=true;async function activate(){const access=await native.visualizerAccess();if(!alive)return;if(access?.granted){setEnabled(true);return;}if(!localStorage.getItem('trx-apex-spectrum-permission-asked')){localStorage.setItem('trx-apex-spectrum-permission-asked','true');const permission=await native.requestPermissionGroup('visualizer');if(alive&&permission?.granted)setEnabled(true);}}activate();return()=>{alive=false;};},[]);
  useEffect(()=>{if(!enabled||!playing){setData(null);native.stopSpectrum();return;}
    let alive=true,pending=false;const poll=async()=>{if(pending)return;pending=true;const r=await native.spectrum();pending=false;if(!alive)return;if(r?.available){setData(r.bands);setReason('');}else{setReason(r?.reason||'Visualization unavailable');setEnabled(false);setData(null);}};
    poll();const timer=setInterval(poll,90);return()=>{alive=false;clearInterval(timer);native.stopSpectrum();};
  },[enabled,playing]);
  const levels=Array.from({length:32},(_,i)=>data?.[i]||0);
  const wave=levels.map((v,i)=>`${i*100/31},${50-Math.min(44,v*43)}`).join(' ');
  return <div className={'audio-spectrum viz-'+visualStyle.toLowerCase()+(data?' live':' idle')} aria-label={data?'Live audio spectrum':'Audio spectrum waiting for playback'}><span className="spectrum-caption"><Activity/> {data?'LIVE AUDIO':'AUDIO VISUALIZER'}</span><button className="spectrum-style" aria-label="Change visualizer style" onClick={()=>setVisualStyle(STYLES[(STYLES.indexOf(visualStyle)+1)%STYLES.length])}>{visualStyle}<ChevronRight/></button><div className="spectrum-stage" aria-hidden="true">{visualStyle==='Wave'?<svg viewBox="0 0 100 100" preserveAspectRatio="none"><polyline points={wave}/><polyline points={levels.map((v,i)=>`${i*100/31},${50+Math.min(44,v*43)}`).join(' ')}/></svg>:<div className="spectrum-bars">{levels.map((v,i)=><i key={i} style={{'--index':i,'--level':`${Math.max(5,Math.round(v*100))}%`,'--energy':Math.max(.17,v)}}/>)}</div>}</div>{!enabled?<button className="spectrum-enable" onClick={async()=>{const r=await act(native.requestPermissionGroup('visualizer'));if(r?.granted){setEnabled(true);setReason('');}else setReason('Audio access was not granted.');}}>{reason||'Enable live audio'}</button>:!playing&&<small className="spectrum-idle">Play audio for live levels</small>}</div>;
}
function MediaPage({media,act,notify}){
  const [sourceOpen,setSourceOpen]=useState(false);const [apps,setApps]=useState([]);const [loading,setLoading]=useState(false);const [selectedSource,setSelectedSource]=useStored('trx-apex-media-source',null);const [pending,setPending]=useState(false);
  useEffect(()=>{if(!sourceOpen)return;let alive=true;setLoading(true);native.apps().then(r=>{if(alive){setApps((r?.apps||[]).filter(a=>/music|spotify|youtube|vlc|tidal|audible|radio|podcast|plex|pandora|soundcloud/i.test(a.name+' '+a.packageName)));setLoading(false);}});return()=>{alive=false;};},[sourceOpen]);
  async function play(){if(media?.hasAccess===false){await act(native.requestPermissionGroup('media'));return;}if(media?.hasSession){act(native.mediaCommand('toggle'));return;}if(selectedSource?.packageName)act(native.launchApp(selectedSource.packageName));else setSourceOpen(true);}
  async function favorite(){if(pending)return;setPending(true);const r=await act(native.mediaCommand('favorite'));if(r?.success)notify('Like request sent to your player.');setPending(false);}
  const source=media?.sourceName || selectedSource?.name || 'Choose source';const duration=media?.durationMs || 0;
  return <section className="page media-page" aria-label="Media page"><div className="media-top"><div className={'record '+(media?.playing?'playing':'')}><img src={media?.artwork || ALBUM} alt="Album artwork"/></div><button className="source-pill" onClick={()=>setSourceOpen(true)}><Radio/>{source}<ChevronRight/></button></div>
    <div className="track-editorial"><h1>{media?.hasSession?media.title:'Ready when you are'}</h1><p>{media?.hasSession?media.artist:'Open a media source to start listening'}</p></div>
    <AudioSpectrum playing={Boolean(media?.playing)} act={act}/>
    <div className="progress-row"><span>{formatTime(media?.positionMs)}</span><input aria-label="Track position" type="range" min="0" max={Math.max(1,duration)} value={Math.min(media?.positionMs||0,duration)} disabled={!media?.canSeek} onChange={e=>act(native.mediaCommand('seek',Number(e.target.value)))}/><span>{formatTime(duration)}</span></div>
    <div className="transport"><button aria-label="Previous track" disabled={!media?.canPrevious} onClick={()=>act(native.mediaCommand('previous'))}><SkipBack/></button><button className="primary round" aria-label={media?.playing?'Pause':'Play'} onClick={play}>{media?.playing?<Pause/>:<Play/>}</button><button aria-label="Next track" disabled={!media?.canNext} onClick={()=>act(native.mediaCommand('next'))}><SkipForward/></button><button aria-label={media?.liked?'Unlike track':'Like track'} aria-pressed={Boolean(media?.liked)} className={media?.liked?'liked':''} disabled={!media?.canFavorite||pending} title={media?.canFavorite?'Like in current player':'Player does not expose a like action'} onClick={favorite}><Heart fill={media?.liked?'currentColor':'none'}/></button></div>
    <label className="volume-row"><Volume2/><span>Media volume</span><input aria-label="Media volume" type="range" value={media?.volumePercent ?? 0} disabled={!media?.volumeAvailable} onChange={e=>act(native.mediaCommand('volume',Number(e.target.value)))}/><b>{media?.volumePercent ?? '—'}%</b></label>
    <div className="queue-panel panel"><div className="queue-heading"><b>Up next</b>{!media?.canFavorite&&<small>Like available in player</small>}</div><div className="queue-list">{media?.queue?.length?media.queue.map((item,i)=><button key={item.id ?? i} disabled={!media.canQueue} onClick={()=>act(native.mediaCommand('queue',0,i,item.id))}><img src={item.artwork || ALBUM} alt=""/><span><b>{item.title}</b><small>{item.artist}</small></span><ChevronRight/></button>):<div className="empty-state"><Music2/><span>{media?.hasSession?'This player has not shared its queue.':'Your player’s queue will appear here.'}</span><button onClick={()=>media?.source?act(native.launchApp(media.source)):setSourceOpen(true)}>Open player</button></div>}</div></div>
    {sourceOpen&&<Modal title="Media sources" close={()=>setSourceOpen(false)}>{loading?<p>Loading installed players…</p>:apps.length?apps.map(app=><button className="source-app" key={app.packageName} onClick={()=>{setSelectedSource({name:app.name,packageName:app.packageName});act(native.launchApp(app.packageName));setSourceOpen(false);}}>{app.icon?<img src={app.icon} alt=""/>:<Radio/>}<b>{app.name}</b><ChevronRight/></button>):<p>No media players found. Open Apps to choose another app.</p>}<Action label="Media control access" detail={media?.hasAccess?'Enabled':'Enable track information and controls'} onClick={()=>act(native.requestPermissionGroup('media'))}/></Modal>}
  </section>;
}
function PerformancePage({obd,act}){
  const [details,setDetails]=useState(false);
  const metrics=[['Boost',obd?.boostPsi,'PSI',1],['Coolant',obd?.coolantF,'°F',0],['Adapter',obd?.batteryV,'V',1],['Intake',obd?.intakeF,'°F',0]];
  return <section className="page performance-page" aria-label="Performance page"><div className="gauge-row"><div><span>RPM</span><strong>{reading(obd?.rpm)}</strong><small>{obd?.ecuConnected?'Engine data':'Awaiting ECU'}</small></div><div><span>Speed</span><strong>{reading(obd?.speedMph)}</strong><small>MPH · OBD</small></div></div><div className="performance-hero"><img className="hero-art" src={HERO} alt="Red RAM TRX"/></div><div className="telemetry-grid">{metrics.map(([label,value,unit,digits])=><div className="panel" key={label}><span>{label}</span><strong>{reading(value,digits)}</strong><small>{unit}</small></div>)}</div><VehicleStatus obd={obd} action={()=>setDetails(true)}/>{details&&<Modal title="OBDLink MX+ diagnostics" close={()=>setDetails(false)}><p>{obd?.status || 'Not connected'}</p><dl className="diagnostics"><dt>Adapter</dt><dd>{obd?.deviceName || 'OBDLink MX+'}</dd><dt>Protocol</dt><dd>{obd?.protocol || '—'}</dd><dt>Live PIDs</dt><dd>{obd?.livePidCount || 0}</dd><dt>Last response</dt><dd>{obd?.ageMs==null?'None':Math.round(obd.ageMs/1000)+'s ago'}</dd><dt>Reconnect attempts</dt><dd>{obd?.reconnectAttempts || 0}</dd><dt>Last connection error</dt><dd>{obd?.lastError || 'None'}</dd><dt>Engine load</dt><dd>{reading(obd?.engineLoad)}%</dd><dt>Throttle</dt><dd>{reading(obd?.throttle)}%</dd><dt>Fuel</dt><dd>{reading(obd?.fuelLevel)}%</dd></dl><p>Gear and transmission temperature require verified RAM-specific data and are not displayed as live readings.</p><Action label="Bluetooth permission" onClick={()=>act(native.requestPermissionGroup('bluetooth'))}/><Action label="Pair adapter" onClick={()=>act(native.settings('bluetooth'))}/><Action label="Reconnect" onClick={()=>act(native.reconnectObd())}/><p>Keep other OBD apps disconnected while APEX uses the adapter.</p><details><summary>Recent adapter responses</summary><pre>{obd?.diagnostics || 'No responses recorded yet.'}</pre></details></Modal>}</section>;
}
function AppsPage({act,notify,quick,openSplit}){
  const [apps,setApps]=useState([]);const [loaded,setLoaded]=useState(false);const [query,setQuery]=useState('');const [editing,setEditing]=useState(false);const [all,setAll]=useState(false);const [context,setContext]=useState(null);
  const [favorites,setFavorites]=useStored('trx-apex-orbit-favorites',[]);
  useEffect(()=>{if(quick==='phone')setQuery('phone');},[quick]);
  useEffect(()=>{let alive=true;native.apps().then(r=>{if(!alive)return;const unique=[...new Map((r?.apps||[]).map(a=>[a.packageName,a])).values()];setApps(unique);setLoaded(true);if(localStorage.getItem('trx-apex-favorites-initialized')!=='true'){if(!favorites.length)setFavorites(unique.filter(a=>/maps|music|phone/i.test(a.name)).slice(0,3).map(a=>a.packageName));localStorage.setItem('trx-apex-favorites-initialized','true');}});return()=>{alive=false;};},[]);
  const selected=favorites.map(id=>apps.find(a=>a.packageName===id)).filter(Boolean).slice(0,6);
  const filtered=useMemo(()=>apps.filter(a=>a.name.toLowerCase().includes(query.toLowerCase())).sort((a,b)=>a.name.localeCompare(b.name)),[apps,query]);
  function toggle(app){if(favorites.includes(app.packageName))setFavorites(favorites.filter(id=>id!==app.packageName));else if(favorites.length<6)setFavorites([...favorites,app.packageName]);else notify('Choose up to six favorites. Remove one first.');}
  const expanded=all||Boolean(query)||editing;
  return <section className={'page apps-page '+(expanded?'expanded':'')} aria-label="Apps page"><div className="search-box"><Search/><input aria-label="Search apps" placeholder="Search apps" value={query} onChange={e=>setQuery(e.target.value)}/>{query&&<button aria-label="Clear app search" onClick={()=>setQuery('')}><X/></button>}<button aria-label="Pair two apps" onClick={()=>openSplit()}><PanelLeft/></button><button aria-label={editing?'Finish editing favorites':'Edit favorites'} className={editing?'active':''} onClick={()=>setEditing(!editing)}>{editing?<Check/>:<SlidersHorizontal/>}</button></div>
    {!expanded&&<div className="app-orbit"><div className="orbit-emblem">TRX<small>Favorites</small></div>{selected.map((app,i)=>{const angle=(i/Math.max(3,selected.length))*2*Math.PI+Math.PI;return <AppButton key={app.packageName} app={app} style={{left:(selected.length===1?50:50+Math.cos(angle)*35)+'%',top:(selected.length===1?72:50+Math.sin(angle)*29)+'%'}} press={()=>act(native.launchApp(app.packageName))} hold={()=>setContext(app)}/>;})}{!selected.length&&<button className="empty-favorites" onClick={()=>setEditing(true)}>Add favorites</button>}</div>}
    <div className="apps-library panel"><div className="library-heading"><b>{editing?'Tap apps to select favorites':'Installed apps'}</b><button onClick={()=>{setAll(!all);setQuery('');}}>{all?'Show favorites':'All apps'}</button></div><div className="apps-grid">{filtered.map(app=><AppButton key={app.packageName} app={app} selected={editing&&favorites.includes(app.packageName)} press={()=>editing?toggle(app):act(native.launchApp(app.packageName))} hold={()=>setContext(app)}/>)}{!filtered.length&&<p>{loaded?'No matching apps.':'Loading installed apps…'}</p>}</div></div>
    {context&&<Modal title={context.name} close={()=>setContext(null)}><Action label="Pair with another app" detail="Select the second app" onClick={()=>{openSplit(context);setContext(null);}}/><Action label="Open for Android split" detail="Then use Android Recents → Split screen to choose the second app" onClick={()=>{const app=context;setContext(null);act(native.launchApp(app.packageName));}}/><Action label={favorites.includes(context.packageName)?'Remove favorite':'Add favorite'} onClick={()=>{toggle(context);setContext(null);}}/><Action label="App information" onClick={()=>{act(native.appAction(context.packageName,'info'));setContext(null);}}/><Action label="Uninstall app" detail="Android will ask you to confirm" onClick={()=>{act(native.appAction(context.packageName,'uninstall'));setContext(null);}}/></Modal>}
  </section>;
}
function AppButton({app,style,press,hold,selected}){
  const timer=useRef();const start=useRef();const cancelled=useRef(false);const held=useRef(false);
  useEffect(()=>()=>clearTimeout(timer.current),[]);
  function down(e){held.current=false;cancelled.current=false;start.current={x:e.clientX,y:e.clientY};timer.current=setTimeout(()=>{held.current=true;hold?.();},650);}
  function move(e){if(start.current&&Math.hypot(e.clientX-start.current.x,e.clientY-start.current.y)>10){cancelled.current=true;clearTimeout(timer.current);}}
  return <button className={'app-tile '+(selected?'selected':'')} style={style} onPointerDown={down} onPointerMove={move} onPointerUp={()=>clearTimeout(timer.current)} onPointerCancel={()=>{cancelled.current=true;clearTimeout(timer.current);}} onClick={()=>{if(!held.current&&!cancelled.current)press();}} onContextMenu={e=>{e.preventDefault();clearTimeout(timer.current);if(!held.current&&!cancelled.current){held.current=true;hold?.();}}}><span className="app-icon">{app.icon?<img src={app.icon} alt=""/>:<Grid2X2/>}</span><b>{app.name}</b>{selected&&<Check className="favorite-check"/>}</button>;
}
function SettingsPage(p){
  const {theme,setTheme,surface,setSurface,accent,setAccent,iconScale,setIconScale,reducedMotion,setReducedMotion,displayMode,setDisplayMode,displayProfile,setDisplayProfile,calibration,setCalibration,visual,setVisual,preferences,setPreferences,device,viewport,act,obd,notify,railEnabled,setRailEnabled,railCapabilities}=p;
  const [sheet,setSheet]=useState(null);
  return <section className="page settings-page" aria-label="Settings page"><div className="studio-hero"><img className="hero-art" src={HERO} alt="Theme preview, red RAM TRX"/></div><div className="theme-panel panel"><span>APPEARANCE · Theme color</span><div className="theme-swatches">{Object.entries(THEMES).map(([id,t])=><button key={id} aria-label={t.name+' theme'} aria-pressed={theme===id} className={theme===id?'selected':''} onClick={()=>setTheme(id)}><i style={{background:`linear-gradient(135deg,${t.accent},${t.tone})`}}/><b>{t.name}</b></button>)}</div></div><div className="surface-panel panel"><span>UI FINISH</span><div className="surface-choices">{Object.entries(SURFACES).map(([id,choice])=><button key={id} aria-label={choice.name+" UI finish"} aria-pressed={surface===id} className={surface===id?"selected":""} onClick={()=>setSurface(id)}><i style={{backgroundColor:choice.color}} className={id}/><b>{choice.name}</b></button>)}</div></div><div className="studio-rows panel"><Action label="Screen fit" detail={`${viewport.width} × ${viewport.height} · ${displayProfile}`} onClick={()=>setSheet('display')}/><Range label="Accent brightness" value={accent} set={setAccent} min={30} max={100} suffix="%"/><Range label="Icon size" value={iconScale} set={setIconScale} min={80} max={125} suffix="%"/><Toggle label="Reduce animation" value={reducedMotion} setValue={setReducedMotion}/><Action label="Vehicle link" detail={obd?.ecuConnected?'Live':obd?.connected?'Adapter connected':'Not connected'} onClick={()=>setSheet('vehicle')}/></div><div className="settings-actions"><button onClick={()=>setSheet('calibration')}><SlidersHorizontal/>Calibrate display</button><button onClick={()=>setSheet('system')}><Settings/>System</button></div>
    {sheet&&<Modal title={{display:'Display preferences',vehicle:'Vehicle connection',calibration:'Screen calibration',visual:'Artwork calibration',system:'System',navigation:'Navigation'}[sheet]} close={()=>setSheet(null)}>
      {sheet==='display'&&<><p>Layout always fits the measured app window. Profiles control calibration behavior.</p><div className="choice-row">{['auto','phone','uconnect','custom'].map(v=><button key={v} className={v===displayProfile?'active':''} onClick={()=>{setDisplayProfile(v);if(v==='phone')setVisual({blackLevel:100,contrast:100,saturation:100,artwork:100});if(v==='uconnect')setVisual({blackLevel:100,contrast:104,saturation:104,artwork:100});}}>{v}</button>)}</div><h3>Lighting</h3><div className="choice-row">{['day','night','auto'].map(v=><button key={v} className={displayMode===v?'active':''} onClick={()=>setDisplayMode(v)}>{v}</button>)}</div><Toggle label="Reduce animation" value={reducedMotion} setValue={setReducedMotion}/><Action label="Artwork calibration" onClick={()=>setSheet('visual')}/></>}
      {sheet==='vehicle'&&<><p>{obd?.status||'Pair your OBDLink MX+ in Android Bluetooth.'}</p><Action label="Bluetooth permission" onClick={()=>act(native.requestPermissionGroup('bluetooth'))}/><Action label="Pair OBDLink MX+" onClick={()=>act(native.settings('bluetooth'))}/><Action label="Reconnect adapter" onClick={()=>act(native.reconnectObd())}/><p>Detailed live PIDs and adapter responses are available on Performance.</p></>}
      {sheet==='system'&&<><Action label="Default launcher" onClick={()=>act(native.requestPermissionGroup('launcher'))}/><Action label="Media control access" onClick={()=>act(native.requestPermissionGroup('media'))}/><Toggle label="Floating rail over Android apps" value={railEnabled&&railCapabilities?.overlay} setValue={async enabled=>{if(enabled){const permission=await native.requestPermissionGroup('overlay');if(!permission?.granted){notify('Allow Display over other apps, then return here.');return;}}const result=await act(native.floatingRail(enabled,THEMES[theme].accent,surface));if(!result?.error)setRailEnabled(enabled);}}/><Action label="Back button in other apps" detail={railCapabilities?.back?'Ready':'Enable optional Accessibility control'} onClick={()=>act(native.requestPermissionGroup('back'))}/><Action label="App permissions" onClick={()=>act(native.settings('app'))}/><Action label="Navigation defaults" onClick={()=>setSheet('navigation')}/><Action label="Preview first-run setup" onClick={()=>{localStorage.removeItem('trx-apex-commissioned');location.reload();}}/><p>TRX APEX · {__APP_VERSION__}</p></>}
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
