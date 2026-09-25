const {spawn}=require('node:child_process');
const fs=require('node:fs');
const path=require('node:path');
const {chromium}=require('playwright');
const base=path.resolve(__dirname,'..');
const output=process.env.APEX_QA_OUTPUT||path.join(base,'qa-output');
fs.mkdirSync(output,{recursive:true});
async function main(){
 const server=spawn(process.execPath,['node_modules/vite/bin/vite.js','--host','127.0.0.1','--port','5181','--strictPort'],{cwd:base,stdio:'pipe'});
 let browser;
 try{
  await new Promise((resolve,reject)=>{server.stdout.on('data',d=>{if(d.toString().includes('Local:'))resolve();});server.stderr.on('data',d=>process.stderr.write(d));server.on('exit',c=>reject(Error('Vite stopped '+c)));setTimeout(()=>reject(Error('Vite startup timeout')),10000).unref();});
  const args=process.env.APEX_CHROMIUM_ARGS_MODULE ? (await import(process.env.APEX_CHROMIUM_ARGS_MODULE)).default.args.filter(a=>a!=='--single-process') : [];
  browser=await chromium.launch({executablePath:process.env.APEX_CHROMIUM||undefined,args,headless:true});
  const errors=[];const results=[];
  for(const size of [{width:602,height:726},{width:480,height:800},{width:390,height:680},{width:800,height:600}]){
   const p=await browser.newPage({viewport:size,deviceScaleFactor:1.33});p.on('pageerror',e=>errors.push(e.message));
   await p.route('https://api.open-meteo.com/**',r=>r.fulfill({json:{current:{temperature_2m:61,weather_code:2}}}));
   await p.addInitScript(()=>{
    localStorage.setItem('trx-apex-commissioned','true');if(!localStorage.getItem('trx-apex-theme'))localStorage.setItem('trx-apex-theme','hellfire');localStorage.setItem('trx-apex-display-profile','phone');
    const icon='data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64"><rect rx="14" width="64" height="64" fill="#6e8a9f"/><circle cx="32" cy="32" r="17" fill="#fff"/></svg>');
    const apps=['Maps','YouTube Music','Phone','Waze','Spotify','Chrome','Settings','OBDLink','A very long application name that must wrap'].map((name,i)=>({name,packageName:'test.app'+i,icon}));
    const media={hasAccess:true,hasSession:true,title:'Fall For Your Type (Official Video)',artist:'Jamie Foxx',source:'test.app1',sourceName:'YouTube Music',playing:true,liked:false,canFavorite:true,canPrevious:true,canNext:true,canSeek:true,canQueue:true,volumeAvailable:true,volumePercent:44,durationMs:280000,positionMs:154000,artwork:'/art/crimson-moon.webp',queue:[{id:'11',title:'After Hours — a long title that must never overlap controls',artist:'APEX Radio',artwork:'/art/crimson-moon.webp'},{id:'12',title:'Open Road',artist:'APEX Radio'}]};
    window.__calls=[];window.__media=media;
    const record=(method,args)=>{window.__calls.push({method,args});return {success:true};};
    window.__APEX_TEST_BRIDGE__={getMediaState:()=>({...media}),getObdState:()=>({connected:true,ecuConnected:false,deviceName:'OBDLink MX+',status:'ADAPTER CONNECTED · ECU NO DATA',livePidCount:0,protocol:'ISO 15765-4 CAN',batteryV:12.4,diagnostics:'010C → NO DATA >'}),getLocation:()=>({latitude:40.32,longitude:-74.59}),getDisplayInfo:()=>({widthPixels:800,heightPixels:965,densityDpi:600,manufacturer:'Ottocast',model:'P3 Pro'}),getInstalledApps:()=>({apps}),mapPreview:a=>record('mapPreview',a),searchDestinations:a=>({suggestions:[{label:'Test destination, New Jersey',primary:'Test destination',secondary:'A longer street address in New Jersey',placeId:'test-place'}]}),openNavigation:a=>record('openNavigation',a),launchApp:a=>record('launchApp',a),startAppPair:a=>record('startAppPair',a),visualizerAccess:()=>({granted:false}),getAudioSpectrum:()=>({available:true,bands:Array(32).fill(.55)}),stopAudioSpectrum:()=>({success:true}),appAction:a=>record('appAction',a),reconnectObd:a=>record('reconnectObd',a),requestPermissionGroup:a=>({...record('requestPermissionGroup',a),granted:true}),openSystemSettings:a=>record('openSystemSettings',a),mediaCommand:a=>{record('mediaCommand',a);if(a.command==='favorite')media.liked=!media.liked;if(a.command==='toggle')media.playing=!media.playing;return {success:true,liked:media.liked};}};
   });
   await p.goto('http://127.0.0.1:5181');await p.evaluate(()=>document.fonts.ready);
   for(const label of ['Home','Navigation','Media','Performance','Apps','Settings']){
    await p.getByRole('navigation').getByRole('button',{name:label,exact:true}).click();await p.waitForTimeout(500);
    const geometry=await p.evaluate(()=>{const rect=e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height,right:r.right,bottom:r.bottom};};const page=document.querySelector('.page');const children=[...page.children].filter(e=>!e.classList.contains('modal-scrim'));const outside=children.filter(e=>{const r=rect(e),p=rect(page);return r.x<p.x-1||r.y<p.y-1||r.right>p.right+1||r.bottom>p.bottom+1;}).map(e=>e.className);const overlaps=[];for(let i=0;i<children.length;i++)for(let j=i+1;j<children.length;j++){const a=rect(children[i]),b=rect(children[j]);if(Math.min(a.right,b.right)-Math.max(a.x,b.x)>1&&Math.min(a.bottom,b.bottom)-Math.max(a.y,b.y)>1)overlaps.push([children[i].className,children[j].className]);}return {outside,overlaps};});
    results.push({viewport:size,page:label,...geometry});
    if(size.width===602)await p.screenshot({animations:'disabled',path:path.join(output,label.toLowerCase()+'.png')});
   }
   if(size.width===602){
    await p.getByRole('button',{name:'Baja Sand theme',exact:true}).click();if(await p.locator('.apex-shell').evaluate(e=>getComputedStyle(e).getPropertyValue('--accent').trim())!=='#d9b078')throw Error('Theme not applied');
    for(const [name,id] of [['Hellfire Red','hellfire'],['Titanium','titanium'],['Arctic Ice','arctic'],['Night Ops','night'],['Baja Sand','baja']]){
     await p.getByRole('button',{name:name+' theme',exact:true}).click();
     if(!await p.locator('.theme-'+id).count())throw Error('Theme failed '+id);
    }
    for(const [name,id] of [['Dark','dark'],['All Black','black'],['Carbon','carbon'],['Charcoal','charcoal']]){
     await p.getByRole('button',{name:name+' UI finish',exact:true}).click();
     if(!await p.locator('.surface-'+id).count())throw Error('UI finish failed '+id);
    }
    await p.getByRole('button',{name:'Carbon UI finish',exact:true}).click();
    await p.getByRole('slider',{name:'Icon size',exact:true}).fill('125');
    await p.getByRole('button',{name:'Calibrate display',exact:true}).click();await p.getByRole('slider',{name:'Safe edge',exact:true}).fill('12');await p.getByRole('button',{name:'Reset geometry',exact:true}).click();await p.getByRole('button',{name:'Close dialog',exact:true}).click();
    await p.getByRole('navigation').getByRole('button',{name:'Media',exact:true}).click();await p.getByRole('button',{name:'Like track',exact:true}).click();await p.getByRole('button',{name:'Pause',exact:true}).click();await p.getByRole('button',{name:'Next track',exact:true}).click();await p.getByRole('slider',{name:'Media volume',exact:true}).fill('70');
    await p.getByRole('navigation').getByRole('button',{name:'Navigation',exact:true}).click();await p.getByRole('textbox',{name:'Destination',exact:true}).fill('test destination');await p.getByRole('button',{name:/Test destination A longer/}).click();await p.getByRole('button',{name:'Start route',exact:true}).click();
    const calls=await p.evaluate(()=>window.__calls);for(const cmd of ['favorite','toggle','next','volume'])if(!calls.some(c=>c.method==='mediaCommand'&&c.args.command===cmd))throw Error('Missing '+cmd);if(!calls.some(c=>c.method==='openNavigation'&&c.args.placeId==='test-place'&&c.args.theme==='baja'&&c.args.surface==='carbon'))throw Error('Route/theme bridge mismatch');
    await p.reload();if(!await p.locator('.theme-baja.surface-carbon').count())throw Error('Theme or UI finish persistence failed');
    await p.getByRole('navigation').getByRole('button',{name:'Media',exact:true}).click();
    await p.evaluate(()=>{window.__media.canFavorite=false;});await p.waitForTimeout(1700);
    if(!await p.getByRole('button',{name:/Like track|Unlike track/}).isDisabled())throw Error('Unsupported Like must disable');
    await p.getByRole('navigation').getByRole('button',{name:'Performance',exact:true}).click();
    await p.getByRole('button',{name:/Adapter connected/}).click();await p.getByRole('button',{name:'Reconnect',exact:true}).click();await p.getByRole('button',{name:'Close dialog',exact:true}).click();
    if(!await p.evaluate(()=>window.__calls.some(c=>c.method==='reconnectObd')))throw Error('Reconnect not dispatched');
    await p.evaluate(()=>{localStorage.setItem('trx-apex-orbit-favorites',JSON.stringify(['test.app0','test.app1','test.app2','test.app3','test.app4','test.app5']));});await p.reload();
    await p.getByRole('navigation').getByRole('button',{name:'Apps',exact:true}).click();
    const collisions=await p.locator('.app-orbit .app-tile').evaluateAll(items=>{const r=items.map(e=>e.getBoundingClientRect());return r.some((a,i)=>r.some((b,j)=>j>i&&Math.min(a.right,b.right)-Math.max(a.left,b.left)>1&&Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top)>1));});
    if(collisions)throw Error('Six favorites overlap at maximum icon size');
    await p.getByRole('textbox',{name:'Search apps',exact:true}).fill('Spotify');await p.locator('.apps-grid').getByRole('button',{name:'Spotify',exact:true}).click();
    if(!await p.evaluate(()=>window.__calls.some(c=>c.method==='launchApp'&&c.args.packageName==='test.app4')))throw Error('App launch failed');
    await p.evaluate(()=>localStorage.setItem('trx-apex-orbit-favorites','[]'));await p.reload();await p.getByRole('navigation').getByRole('button',{name:'Apps',exact:true}).click();
    if(!await p.getByRole('button',{name:'Add favorites',exact:true}).count())throw Error('Empty favorites repopulated');
    await p.getByRole('button',{name:'Pair two apps',exact:true}).click();
    await p.getByRole('dialog',{name:'Pair two apps'}).getByRole('button',{name:'Phone',exact:true}).click();
    await p.getByRole('dialog',{name:'Pair two apps'}).getByRole('button',{name:'Maps',exact:true}).click();
    if(!await p.evaluate(()=>window.__calls.some(c=>c.method==='startAppPair'&&c.args.first==='test.app2'&&c.args.second==='test.app0')))throw Error('Two-app split request was not dispatched');
    await p.getByRole('navigation').getByRole('button',{name:'Media',exact:true}).click();
    await p.waitForTimeout(150);
    if(!await p.evaluate(()=>window.__calls.some(c=>c.method==='requestPermissionGroup'&&c.args.group==='visualizer')))throw Error('Optional audio permission was not requested');
    for(const name of ['Mirror','Wave','Orbit','Bars']){
     await p.getByRole('button',{name:'Change visualizer style',exact:true}).click();
     if(!await p.locator('.viz-'+name.toLowerCase()).count())throw Error('Visualizer style failed '+name);
    }

   }
   await p.close();
  }
  fs.writeFileSync(path.join(output,'layout-results.json'),JSON.stringify({errors,results},null,2));
  console.log(JSON.stringify({errors,issues:results.filter(r=>r.outside.length||r.overlaps.length),layouts:results.length}));
  if(errors.length||results.some(r=>r.outside.length||r.overlaps.length))process.exitCode=1;
 }finally{if(browser)await browser.close();server.kill();}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
