from pathlib import Path
import concurrent.futures as C,urllib.request,hashlib,json,zipfile,os
root=Path(__file__).resolve().parents[1]/"tools"
root.mkdir(exist_ok=True)
items=[('build-tools_r36_linux.zip','https://dl.google.com/android/repository/build-tools_r36_linux.zip','sha1','b0b6376977657e8ad9b969bacf4093601da2c6fb',63737259),('platform-tools_r37.0.1-linux.zip','https://dl.google.com/android/repository/platform-tools_r37.0.1-linux.zip','sha1','477254aa5f903c15cf51001717bdf347fb6b53e0',9054187)]
for abi in ['arm64-v8a','armeabi-v7a','x86','x86_64']:
 name=f'VLC-Android-3.7.1-{abi}.apk';url=f'https://download.videolan.org/pub/videolan/vlc-android/3.7.1/{name}'
 expected=urllib.request.urlopen(url+'.sha256',timeout=60).read().decode().split()[0]
 items.append((name,url,'sha256',expected,None))
def get(item):
 name,url,kind,expected,size=item;p=root/name
 if not p.exists() or hashlib.new(kind,p.read_bytes()).hexdigest()!=expected:
  temp=p.with_suffix(p.suffix+'.partial')
  with urllib.request.urlopen(url,timeout=120) as r,temp.open('wb') as f:
   while chunk:=r.read(1024*1024):f.write(chunk)
  assert hashlib.new(kind,temp.read_bytes()).hexdigest()==expected,name+' checksum mismatch'
  if size:assert temp.stat().st_size==size
  temp.replace(p)
 digest=hashlib.sha256(p.read_bytes()).hexdigest()
 print(name,p.stat().st_size,'checksum verified',flush=True)
 if name.endswith('.zip'):
  dest=root/('build-tools' if name.startswith('build-tools') else 'platform-tools-package');dest.mkdir(exist_ok=True)
  with zipfile.ZipFile(p) as z:
   for i in z.infolist():
    z.extract(i,dest)
    mode=i.external_attr>>16
    if mode:os.chmod(dest/i.filename,mode)
 return {'file':name,'url':url,'size':p.stat().st_size,'sha256':digest,'upstream_checksum':expected,'upstream_checksum_type':kind}
with C.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(get,items))
(root/'dependency-provenance.json').write_text(json.dumps(results,indent=2)+'\n')
