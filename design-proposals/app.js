const designs = [
 {id:'cinema',name:'Cinema',inspired:'Infuse',url:'https://firecore.com/infuse',tag:'An evening at the movies.',description:'Artwork takes the lead. A generous resume card, cinematic posters and warm amber make choosing a film feel like part of the experience.',colors:['#171717','#F46B32','#E7DDCC'],tradeoff:'Best for a film library with artwork. Less information on each screen.'},
 {id:'quiet',name:'Quiet',inspired:'IINA',url:'https://www.iina.io/',tag:'Just you and your video.',description:'A bright, file-first library with generous space, restrained orange and clear typography. Playback controls sit in a quiet, separate dock.',colors:['#F8F6F1','#B84E24','#23221F'],tradeoff:'Best for everyday local files. Less of a home-cinema atmosphere.'},
 {id:'collection',name:'Collection',inspired:'Plex',url:'https://support.plex.tv/articles/200392126-using-the-library-view/',tag:'Your own small cinema club.',description:'An inky navy library arranged into collections and shelves. Mint navigation and plum surfaces create a distinct identity for personal media.',colors:['#141C2B','#80E1C0','#AB91C7'],tradeoff:'Best for a large, organized library. Collection grouping adds a browsing step.'},
 {id:'studio',name:'Studio',inspired:'DaVinci Resolve',url:'https://www.blackmagicdesign.com/products/davinciresolve/edit',tag:'Every detail, within reach.',description:'A precise graphite interface: filenames, technical metadata and direct playback adjustments. Teal marks selection; orange marks the playhead.',colors:['#191B1E','#55C7BC','#ED875D'],tradeoff:'Best for playback enthusiasts. Denser and more technical than the other directions.'}
];
const media = [
 {title:'The Long Way Home',file:'the_long_way_home.mkv',image:'golden-hour.jpg',duration:'1h 42m',resolution:'4K',type:'Adventure'},
 {title:'Above the Clouds',file:'above_the_clouds.mp4',image:'alpine.jpg',duration:'48m',resolution:'1080p',type:'Documentary'},
 {title:'Blue Between',file:'blue_between.mkv',image:'coast.jpg',duration:'1h 16m',resolution:'4K',type:'Short film'},
 {title:'A Quiet Universe',file:'a_quiet_universe.mp4',image:'night-sky.jpg',duration:'52m',resolution:'1080p',type:'Documentary'},
 {title:'Where the Road Ends',file:'where_the_road_ends.mp4',image:'desert.jpg',duration:'1h 33m',resolution:'1080p',type:'Adventure'},
 {title:'The Green Distance',file:'the_green_distance.mkv',image:'forest.jpg',duration:'39m',resolution:'4K',type:'Short film'}
];
const paths={video:'<rect x="3" y="5" width="13" height="14" rx="2"/><path d="m16 10 5-3v10l-5-3"/>',audio:'<path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>',browse:'<path d="M3 6h7l2 2h9v11H3zM3 9h18"/>',playlists:'<path d="M4 6h12M4 11h12M4 16h7m6-1 4 2.5-4 2.5z"/>',more:'<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',search:'<circle cx="10" cy="10" r="6"/><path d="m15 15 6 6"/>',play:'<path d="m8 5 11 7-11 7z" fill="currentColor" stroke="none"/>',pause:'<path d="M7 5h4v14H7zM15 5h4v14h-4z" fill="currentColor" stroke="none"/>',arrow:'<path d="m9 5 7 7-7 7"/>',back:'<path d="m15 5-7 7 7 7"/>',cast:'<rect x="3" y="4" width="18" height="14" rx="2"/><path d="M3 21v-1m0-4a5 5 0 0 1 5 5M3 12a9 9 0 0 1 9 9"/>',cc:'<rect x="2" y="5" width="20" height="14" rx="3"/><path d="M10 10a2 2 0 1 0 0 4m7-4a2 2 0 1 0 0 4"/>',plus:'<path d="M12 4v16M4 12h16"/>',sliders:'<path d="M3 6h18M3 12h18M3 18h18M7 3v6m10 0v6M9 15v6"/>',check:'<path d="m4 12 5 5L20 6"/>',expand:'<path d="M4 9V4h5m6 0h5v5m0 6v5h-5M9 20H4v-5"/>'};
const icon=n=>`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[n]||paths.more}</svg>`;
const image=(m,cls='')=>`<img class="${cls}" src="assets/${m.image}" alt="${m.title} · sample artwork">`;
const playMedia=(m,content,cls='')=>`<button class="media-open ${cls}" data-media="${m.title}" aria-label="Preview ${m.title}" data-search="${(m.title+' '+m.file+' '+m.type).toLowerCase()}">${content}</button>`;
const row=(m,studio=false)=>playMedia(m,`${image(m)}<span class="row-copy"><strong>${studio?m.file:m.title}</strong><small>${studio?m.resolution+' · HEVC · '+m.duration:m.type+' · '+m.duration}</small></span><span class="row-end">${studio?m.resolution:icon('play')}</span>`,'file-row');
const poster=m=>playMedia(m,`${image(m)}<span class="poster-caption"><strong>${m.title}</strong><small>${m.duration}</small></span>`,'poster-tile');
const section=(title,action='')=>`<div class="section-label"><h4>${title}</h4>${action?`<button class="feedback-button" aria-label="${action}" data-feedback="${action}">${icon('arrow')}</button>`:''}</div>`;
const search=d=>`<label class="search-field">${icon('search')}<input type="search" aria-label="Search ${d.name} library" placeholder="Search your videos"></label>`;
const nav=()=>`<nav class="app-nav" aria-label="VLC navigation">${['Video','Audio','Browse','Playlists','More'].map((n,i)=>`<button class="nav-item ${i===0?'selected':''}" aria-label="${n}" aria-pressed="${i===0}" data-nav="${n}">${icon(n.toLowerCase())}<span>${n}</span></button>`).join('')}</nav>`;
const status=()=>'<div class="statusbar"><span>9:41</span><span class="status-signals">▂▄▆ &nbsp; ◔ &nbsp; ▰</span></div>';
function library(d){
 const head=`<div class="app-heading"><div class="app-brand"><span class="mini-cone">▲</span> VLC</div><button class="feedback-button" aria-label="Cast video" data-feedback="Cast · choose a device in the full app">${icon('cast')}</button></div>`;
 let body='';
 if(d.id==='cinema')body=`${head}<h3 class="app-title">Your next<br>great escape.</h3>${search(d)}${playMedia(media[0],`${image(media[0])}<span class="hero-shade"></span><span class="hero-caption"><small>CONTINUE WATCHING</small><strong>The Long Way Home</strong><span>42 min left <b>${icon('play')}</b></span><i class="progress-line"></i></span>`,'cinema-hero')}${section('Recently added','Recently added · all six sample videos')}<div class="poster-shelf cinema-posters">${media.slice(1,4).map(poster).join('')}</div><p class="library-footnote">6 videos · on this device</p>`;
 if(d.id==='quiet')body=`${head}<div class="quiet-title"><h3 class="app-title">Library</h3><span>6 videos</span></div>${search(d)}<div class="quiet-section-title">PICK UP WHERE YOU LEFT OFF</div>${playMedia(media[0],`${image(media[0])}<span class="quiet-resume-copy"><strong>The Long Way Home</strong><small>38:14 of 1:42:18</small></span><span class="quiet-play">${icon('play')}</span>`,'quiet-resume')}${section('All videos')}<div class="file-list">${media.slice(1,5).map(m=>row(m)).join('')}</div><button class="open-file feedback-button" data-feedback="Open a file · this review uses six sample videos">${icon('plus')} Open a file</button>`;
 if(d.id==='collection')body=`${head}<h3 class="app-title">The collection<span>Good stories. Yours to keep.</span></h3>${search(d)}<div class="collection-tabs"><button class="category selected" data-category="All" aria-pressed="true">All</button><button class="category" data-category="Documentary" aria-pressed="false">Docs</button><button class="category" data-category="Short film" aria-pressed="false">Shorts</button></div>${section('Continue watching')}${playMedia(media[0],`${image(media[0])}<span><strong>The Long Way Home</strong><small>42 min left</small><i class="progress-line"></i></span>${icon('play')}`,'collection-resume')}${section('Recently added')}<div class="poster-shelf collection-posters">${media.slice(1,4).map(poster).join('')}</div>${section('Your collections')}<div class="collection-folders"><button class="feedback-button" data-feedback="Weekend escapes · The Long Way Home and Where the Road Ends">${icon('browse')}<strong>Weekend escapes</strong><small>2 videos</small></button><button class="feedback-button" data-feedback="Nature notes · Above the Clouds and A Quiet Universe">${icon('browse')}<strong>Nature notes</strong><small>2 videos</small></button></div>`;
 if(d.id==='studio')body=`${head}<div class="studio-title"><h3 class="app-title">Media bin</h3><span>LOCAL / 06</span></div>${search(d)}${playMedia(media[0],`${image(media[0])}<span class="monitor-label">00:38:14</span><span class="monitor-play">${icon('play')}</span>`,'studio-monitor')}<div class="technical-line"><span>3840 × 2160</span><span>HEVC</span><span>23.976 fps</span></div><div class="studio-table-head"><span>FILE / FORMAT</span><span>RES.</span></div><div class="file-list studio-files">${media.slice(0,4).map(m=>row(m,true)).join('')}</div><div class="studio-quick"><span>Playback defaults</span><button class="feedback-button" aria-label="Playback defaults" data-feedback="Defaults · fit to screen, stereo, normal speed">${icon('sliders')}</button></div><div class="technical-line studio-defaults"><span>FIT</span><span>STEREO</span><span>1.00×</span></div>`;
 return `<div class="library-screen">${status()}<div class="app-body">${body}<p class="empty-state" hidden>No matching videos.</p></div>${nav()}<div class="device-feedback" role="status"></div></div>`;
}
function player(d){return `<div class="playback-screen"><div class="video-surface">${image(media[0],'video-still')}<div class="video-gradient"></div><div class="player-heading"><button class="back-library" aria-label="Back to library">${icon('back')}</button><span class="playing-name">The Long Way Home<small>${d.id==='studio'?'the_long_way_home.mkv · 3840 × 2160':'On this device · 4K UHD'}</small></span><button class="feedback-button" aria-label="Cast playback" data-feedback="Cast · available in the full app">${icon('cast')}</button></div><button class="play-toggle" aria-label="Play simulated video">${icon('play')}</button><div class="subtitle-line" hidden>Some roads are worth taking slowly.</div>${d.id==='studio'?'<div class="monitor-details"><span>HEVC / 23.976 fps</span><span>BT.2020 · STEREO</span></div>':''}</div><div class="playback-dock"><div class="seek-line"><span class="elapsed">38:14</span><input class="seek" type="range" min="0" max="6138" value="2294" aria-label="Playback position for ${d.name}"><span class="total-time">1:42:18</span></div><div class="transport-row"><div class="transport-group"><button class="skip" data-skip="-10" aria-label="Rewind ten seconds">−10</button><button class="dock-play" aria-label="Play simulated video">${icon('play')}</button><button class="skip" data-skip="10" aria-label="Forward ten seconds">+10</button></div><div class="transport-group"><button class="cc-toggle" aria-label="Toggle subtitles" aria-pressed="false">${icon('cc')}</button><button class="speed-toggle" aria-label="Change playback speed">1×</button><button class="feedback-button" data-feedback="Audio track · English, stereo" aria-label="Audio track">${icon('audio')}</button></div></div></div><div class="device-feedback" role="status"></div></div>`;}
function renderCard(d,i){return `<article class="concept-card" data-concept="${d.id}"><div class="concept-top"><div><span class="concept-number">0${i+1} / 04</span><h2>${d.name}</h2><span class="concept-reference">Inspired by ${d.inspired}</span></div><button class="focus-concept" aria-label="Focus ${d.name}" aria-pressed="false">${icon('expand')}</button></div><div class="device-wrap"><div class="device ${d.id}" aria-label="${d.name} design proposal">${library(d)}${player(d)}</div></div><div class="concept-notes"><p class="concept-tag">${d.tag}</p><p class="concept-description">${d.description}</p><div class="palette">${d.colors.map(c=>`<span style="background:${c}" title="${c}"></span>`).join('')}<span class="palette-values">${d.colors.join(' / ')}</span></div><p class="tradeoff">${d.tradeoff}</p><a class="reference" href="${d.url}" target="_blank" rel="noreferrer">View inspiration ${icon('arrow')}</a></div></article>`;}
document.querySelector('#gallery').innerHTML=designs.map(renderCard).join('');
let focused=null;
function setView(view){document.body.dataset.view=view;document.querySelectorAll('.screen-toggle').forEach(b=>{let selected=b.dataset.screen===view;b.classList.toggle('active',selected);b.setAttribute('aria-pressed',String(selected));});}
function setFocus(id){focused=id;document.body.classList.toggle('focus-mode',!!id);document.querySelectorAll('.concept-card').forEach(c=>{c.hidden=!!id&&c.dataset.concept!==id;c.querySelector('.focus-concept').setAttribute('aria-pressed',String(c.dataset.concept===id));});document.querySelector('#show-all').hidden=!id;}
const toastTimers=new WeakMap();
function feedback(device,message){const box=device.querySelectorAll('.device-feedback')[document.body.dataset.view==='playback'?1:0];clearTimeout(toastTimers.get(box));box.textContent=message;box.classList.add('visible');toastTimers.set(box,setTimeout(()=>box.classList.remove('visible'),3500));}
const format=n=>`${Math.floor(n/60)}:${String(n%60).padStart(2,'0')}`;
function updateTime(device){const seek=device.querySelector('.seek');device.querySelector('.elapsed').textContent=format(Number(seek.value));}
function togglePlay(device){const playing=device.dataset.playing!=='true';device.dataset.playing=String(playing);device.querySelectorAll('.play-toggle,.dock-play').forEach(b=>{b.innerHTML=icon(playing?'pause':'play');b.setAttribute('aria-label',`${playing?'Pause':'Play'} simulated video`);});}
document.querySelectorAll('.screen-toggle').forEach(b=>b.addEventListener('click',()=>setView(b.dataset.screen)));
document.querySelector('#show-all').addEventListener('click',()=>setFocus(null));
document.querySelectorAll('.focus-concept').forEach(b=>b.addEventListener('click',()=>setFocus(focused===b.closest('article').dataset.concept?null:b.closest('article').dataset.concept)));
document.querySelectorAll('.device').forEach(device=>{
 let category='All';
 const filter=()=>{const query=device.querySelector('input[type=search]').value.trim().toLowerCase();let count=0;device.querySelectorAll('.media-open').forEach(b=>{const m=media.find(m=>m.title===b.dataset.media);const visible=b.dataset.search.includes(query)&&(category==='All'||m.type===category);b.hidden=!visible;if(visible)count++;});device.querySelector('.empty-state').hidden=count>0;};
 device.querySelector('input[type=search]').addEventListener('input',filter);
 device.querySelectorAll('.category').forEach(b=>b.addEventListener('click',()=>{category=b.dataset.category;device.querySelectorAll('.category').forEach(other=>{other.classList.toggle('selected',other===b);other.setAttribute('aria-pressed',String(other===b));});filter();}));
 device.querySelectorAll('.media-open').forEach(b=>b.addEventListener('click',()=>{const m=media.find(m=>m.title===b.dataset.media);device.querySelector('.video-still').src=`assets/${m.image}`;device.querySelector('.video-still').alt=`${m.title} · sample artwork`;const durations=[6138,2886,4602,3151,5589,2352];const duration=durations[media.indexOf(m)];const seek=device.querySelector('.seek');seek.max=duration;seek.value=media.indexOf(m)===0?2294:0;device.querySelector('.total-time').textContent=duration>=3600?`${Math.floor(duration/3600)}:${format(duration%3600).padStart(5,'0')}`:format(duration);updateTime(device);device.querySelector('.playing-name').innerHTML=`${m.title}<small>${m.file} · ${m.resolution}</small>`;setFocus(device.closest('article').dataset.concept);setView('playback');}));
 device.querySelectorAll('.nav-item').forEach(b=>b.addEventListener('click',()=>{device.querySelectorAll('.nav-item').forEach(other=>{other.classList.toggle('selected',other===b);other.setAttribute('aria-pressed',String(other===b));});feedback(device,b.dataset.nav==='Video'?'Video library':`${b.dataset.nav} · navigation preview. Sample video library remains visible.`);}));
 device.querySelectorAll('.feedback-button').forEach(b=>b.addEventListener('click',()=>feedback(device,b.dataset.feedback)));
 device.querySelector('.back-library').addEventListener('click',()=>setView('library'));
 device.querySelectorAll('.play-toggle,.dock-play').forEach(b=>b.addEventListener('click',()=>togglePlay(device)));
 device.querySelector('.seek').addEventListener('input',()=>updateTime(device));
 device.querySelectorAll('.skip').forEach(b=>b.addEventListener('click',()=>{const seek=device.querySelector('.seek');seek.value=Math.max(0,Math.min(Number(seek.max),Number(seek.value)+Number(b.dataset.skip)));updateTime(device);}));
 device.querySelector('.cc-toggle').addEventListener('click',event=>{const b=event.currentTarget;const on=b.getAttribute('aria-pressed')!=='true';b.setAttribute('aria-pressed',String(on));device.querySelector('.subtitle-line').hidden=!on;});
 device.querySelector('.speed-toggle').addEventListener('click',event=>{const b=event.currentTarget;const speeds=['1×','1.25×','1.5×','2×','0.75×'];b.textContent=speeds[(speeds.indexOf(b.textContent)+1)%speeds.length];});
});
setView('library');

// Export the reviewed HTML as a self-contained design board. Artwork is embedded.
document.querySelector('#save-board').addEventListener('click', async event => {
 const button=event.currentTarget;
 button.disabled=true;button.textContent='Saving…';
 try {
  const clone=document.body.cloneNode(true);
  clone.classList.add('export-root');
  clone.querySelectorAll('script,#save-board,#download-board').forEach(e=>e.remove());
  const dataURL=blob=>new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result);reader.onerror=reject;reader.readAsDataURL(blob);});
  await Promise.all([...clone.querySelectorAll('img')].map(async img=>{img.src=await dataURL(await (await fetch(img.src)).blob());img.removeAttribute('loading');}));
  const css=(await (await fetch('styles.css')).text()).replace(/(?<![-\w.])body\b/g,'.export-root');
  const width=document.documentElement.clientWidth;
  const height=document.documentElement.scrollHeight;
  const wrapper=document.createElement('div');
  wrapper.setAttribute('xmlns','http://www.w3.org/1999/xhtml');
  const style=document.createElement('style');style.textContent=css;
  wrapper.append(style,clone);
  const markup=new XMLSerializer().serializeToString(wrapper);
  const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"><foreignObject width="100%" height="100%">${markup}</foreignObject></svg>`;
  const picture=new Image();
  await new Promise((resolve,reject)=>{picture.onload=resolve;picture.onerror=reject;picture.src='data:image/svg+xml;charset=utf-8,'+encodeURIComponent(svg);});
  const canvas=document.createElement('canvas');canvas.width=width*2;canvas.height=height*2;
  const context=canvas.getContext('2d');context.scale(2,2);context.drawImage(picture,0,0);
  const png=await new Promise(resolve=>canvas.toBlob(resolve,'image/png'));
  if(!png)throw new Error('Image export unavailable');
  const link=document.querySelector('#download-board');
  if(link.dataset.url)URL.revokeObjectURL(link.dataset.url);
  link.href=URL.createObjectURL(png);link.dataset.url=link.href;link.hidden=false;
  link.download=`vlc-${document.body.dataset.view}-${focused||'four-styles'}.png`;
  button.textContent='Image ready';
 } catch(error){button.textContent='Retry export';console.error('Design export:',error);}
 button.disabled=false;
});
