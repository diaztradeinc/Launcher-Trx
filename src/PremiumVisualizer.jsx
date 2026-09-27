import {useEffect,useRef,useState} from 'react';

export const VISUAL_STYLES=[['Filament','Crimson Filament'],['Ribbon','Liquid Ribbon'],['Obsidian','Obsidian Pulse'],['Silk','Spectral Silk']];

// One resolution-aware canvas replaces hundreds of animated DOM elements.
export function PremiumVisualizer({bands,styleName}){
 const canvas=useRef(null),target=useRef([]);
 useEffect(()=>{target.current=bands||[];},[bands]);
 useEffect(()=>{
  const el=canvas.current,ctx=el.getContext('2d');if(!ctx)return;
  let frame,last=0,width=0,height=0,box=null,accent='#f04450',dirty=true;const smooth=new Float32Array(32);
  const reduced=window.matchMedia('(prefers-reduced-motion: reduce)');
  const measure=()=>{dirty=true;const r=el.getBoundingClientRect(),record=el.closest('.media-top')?.querySelector('.record')?.getBoundingClientRect();width=r.width;height=r.height;const dpr=Math.min(window.devicePixelRatio||1,2);el.width=Math.round(width*dpr);el.height=Math.round(height*dpr);ctx.setTransform(dpr,0,0,dpr,0,0);box=record?{x:record.left-r.left-6,y:record.top-r.top-6,w:record.width+12,h:record.height+12}:null;accent=getComputedStyle(el).getPropertyValue('--accent').trim()||'#f04450';};
  const observer=new ResizeObserver(measure);observer.observe(el);const record=el.closest('.media-top')?.querySelector('.record');if(record)observer.observe(record);
  const themeObserver=new MutationObserver(measure);const shell=el.closest('.apex-shell');if(shell)themeObserver.observe(shell,{attributes:true,attributeFilter:['class','style']});
  const value=(t)=>{const f=Math.max(0,Math.min(31,t*31)),i=Math.floor(f);return smooth[i]*(1-f+i)+(smooth[Math.min(31,i+1)]||0)*(f-i);};
  const color=t=>styleName==='Silk'?`hsl(${190+t*145} 95% 65%)`:accent;
  function stroke(x,y,dx,dy,t,maxLength,centered){const energy=value(t),len=1+energy*maxLength;ctx.strokeStyle=styleName==='Obsidian'?'#d2dce0':color(t);ctx.lineWidth=styleName==='Obsidian'?1.5:1;ctx.lineCap='round';ctx.setLineDash(styleName==='Obsidian'?[1.2,2.8]:[]);ctx.beginPath();ctx.moveTo(x-dx*(centered?len:0),y-dy*(centered?len:0));ctx.lineTo(x+dx*len,y+dy*len);ctx.stroke();ctx.setLineDash([]);if(energy>.18){ctx.fillStyle=color(t);ctx.fillRect(x+dx*len-.7,y+dy*len-.7,1.4,1.4);}}
  function edge(x,y,dx,dy,nx,ny,length,maxLength,centered=false){
   if(styleName==='Ribbon'){
    for(let layer=0;layer<3;layer++){ctx.beginPath();ctx.lineWidth=layer===0?1.5:.7;ctx.strokeStyle=layer===0?accent:layer===1?'#e8dce0':accent;ctx.globalAlpha=layer===0?.9:.45;for(let i=0;i<=80;i++){const t=i/80,amp=value(t)*maxLength*Math.sin(t*Math.PI*8+layer*1.5);const px=x+dx*t*length+nx*amp,py=y+dy*t*length+ny*amp;if(i===0)ctx.moveTo(px,py);else ctx.lineTo(px,py);}ctx.stroke();}ctx.globalAlpha=1;return;
   }
   const count=Math.max(12,Math.floor(length/3));for(let i=0;i<=count;i++){const t=i/count;stroke(x+dx*t*length,y+dy*t*length,nx,ny,t,maxLength,centered);}
   if(styleName==='Silk'){ctx.lineWidth=.8;for(const sign of [-1,1]){ctx.beginPath();for(let i=0;i<=80;i++){const t=i/80,amp=value(t)*maxLength*sign,px=x+dx*t*length+nx*amp,py=y+dy*t*length+ny*amp;if(!i)ctx.moveTo(px,py);else ctx.lineTo(px,py);}const gradient=ctx.createLinearGradient(x,y,x+dx*length||.01,y+dy*length||.01);gradient.addColorStop(0,'#25d9ff');gradient.addColorStop(.5,'#9b73ff');gradient.addColorStop(1,'#ff438e');ctx.strokeStyle=gradient;ctx.stroke();}}
  }
  function draw(now){frame=requestAnimationFrame(draw);if(document.hidden||now-last<33)return;last=now;if(!box||!width)return;const noMotion=reduced.matches||shell?.classList.contains('reduce-motion');let changed=dirty;for(let i=0;i<32;i++){const v=Math.max(0,Math.min(1,Number(target.current[i])||0));if(Math.abs(v-smooth[i])>.001){smooth[i]+=(v-smooth[i])*(noMotion?1:.28);changed=true;}}if(!changed)return;dirty=false;ctx.clearRect(0,0,width,height);const bass=(smooth[0]+smooth[1]+smooth[2]+smooth[3])/4;ctx.globalAlpha=.85;const b=box,reach=Math.max(2,Math.min(b.y-12,(height-b.y-b.h)-12,28));
   // Side waveforms and the square share the same FFT samples and palette.
   edge(12,b.y+b.h/2,1,0,0,1,Math.max(1,b.x-24),Math.min(height*.18,46),true);
   edge(b.x+b.w+12,b.y+b.h/2,1,0,0,1,Math.max(1,width-b.x-b.w-24),Math.min(height*.18,46),true);
   edge(b.x,b.y,1,0,0,-1,b.w,reach);edge(b.x,b.y+b.h,1,0,0,1,b.w,reach);
   edge(b.x,b.y,0,1,-1,0,b.h,reach);edge(b.x+b.w,b.y,0,1,1,0,b.h,reach);
   ctx.globalAlpha=.65+(noMotion?0:bass*.35);ctx.strokeStyle=styleName==='Silk'?'#ba78fa':accent;ctx.lineWidth=1.1;ctx.shadowColor=ctx.strokeStyle;ctx.shadowBlur=noMotion?0:3+bass*10;ctx.beginPath();ctx.roundRect(b.x,b.y,b.w,b.h,9);ctx.stroke();ctx.shadowBlur=0;ctx.globalAlpha=1;
  }
  measure();frame=requestAnimationFrame(draw);return()=>{cancelAnimationFrame(frame);observer.disconnect();themeObserver.disconnect();};
 },[styleName]);
 return <canvas ref={canvas} className="premium-spectrum" aria-hidden="true"/>;
}

export function TrackTitle({title,artist}){
 const viewport=useRef(null),text=useRef(null);const [overflow,setOverflow]=useState(0);
 useEffect(()=>{let alive=true;const measure=()=>{if(alive)setOverflow(Math.max(0,(text.current?.scrollWidth||0)-(viewport.current?.clientWidth||0)));};const observer=new ResizeObserver(measure);if(viewport.current)observer.observe(viewport.current);if(text.current)observer.observe(text.current);document.fonts.ready.then(measure);measure();return()=>{alive=false;observer.disconnect();};},[title,artist]);
 return <div className="track-editorial premium-title" key={title+'|'+artist}><h1 ref={viewport} className={overflow>2?'title-overflow':''} style={{'--title-travel':`-${overflow}px`,'--title-duration':`${Math.max(8,overflow/22+5)}s`}}><span ref={text}>{title}</span></h1><p>{artist}</p></div>;
}

export function StylePreview({name}){return <svg viewBox="0 0 48 22" aria-hidden="true" className={'style-preview preview-'+name.toLowerCase()}>{name==='Ribbon'?<><path d="M1 11 Q7 0 13 11 T25 11 T37 11 T47 11"/><path opacity=".5" d="M1 14 Q7 4 13 14 T25 14 T37 14 T47 14"/></>:Array.from({length:13},(_,i)=>{const h=3+Math.sin(i/12*Math.PI)*15;return <path key={i} stroke={name==='Silk'?`hsl(${190+i*12} 95% 65%)`:undefined} strokeDasharray={name==='Obsidian'?'1 2':undefined} d={`M${3+i*3.5} ${11-h/2}v${h}`}/>;})}</svg>;}
