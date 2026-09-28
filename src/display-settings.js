// Profiles affect rendering, never pretend to change the Android output resolution.
export const DISPLAY_PRESETS = {
  phone: {blackLevel:100,contrast:100,saturation:100,artwork:100},
  uconnect: {blackLevel:100,contrast:112,saturation:108,artwork:100}
};
export function resolveDisplayProfile(profile,device) {
  if(['phone','uconnect','custom'].includes(profile))return profile;
  // Ottocast branding can be absent from Android's manufacturer/model fields.
  // Match the measured TRX app window, not an assumed Uconnect panel resolution.
  const measured=Number(device?.widthPixels)>=770&&Number(device?.widthPixels)<=840&&
    Number(device?.heightPixels)>=925&&Number(device?.heightPixels)<=1010&&
    Number(device?.densityDpi)>=190&&Number(device?.densityDpi)<=240;
  return measured||/ottocast|p3[ -]?pro/i.test(`${device?.manufacturer||''} ${device?.model||''}`)?'uconnect':'phone';
}
export function displayVisual(profile,custom) {
  if(profile!=='custom')return DISPLAY_PRESETS[profile]||DISPLAY_PRESETS.phone;
  const fallback=DISPLAY_PRESETS.uconnect,limits={blackLevel:[70,100],contrast:[90,130],saturation:[80,130],artwork:[80,125]};
  return Object.fromEntries(Object.entries(limits).map(([key,[min,max]])=>[key,Number.isFinite(custom?.[key])?Math.max(min,Math.min(max,custom[key])):fallback[key]]));
}
const PALETTES={
  charcoal:['#222a2e','#0e1316','#1a2327','#303b40','#819098'],
  dark:['#10191d','#05090d','#10171c','#212d34','#6a7b82'],
  black:['#070809','#000102','#050708','#11171a','#53636a'],
  carbon:['#0c1012','#020405','#0a1013','#1b262b','#637984']
};
export function displayPalette(surface,day,blackLevel) {
  const keys=['base','deep','panel','raised','line'];
  return Object.fromEntries((PALETTES[surface]||PALETTES.carbon).map((hex,i)=>{
    const lift=(100-blackLevel)*.6+(day?(i===4?22:12):0);
    const rgb=hex.slice(1).match(/../g).map(n=>Math.min(255,Math.round(parseInt(n,16)+lift)));
    return ['--ui-'+keys[i],`rgb(${rgb.join(' ')})`];
  }));
}
