#!/usr/bin/env python3
"""Restore verified, release-matched native payloads and make the base standalone."""
from pathlib import Path
import hashlib, json, sys, zipfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
work = Path(sys.argv[1])
tools = root/'android-premiere/tools'
checksums = {
    'arm64-v8a': 'bea9ea0cdecfd8f461ef305d2afe5d1ac1459cfb9156acce2f615788a486de74',
    'armeabi-v7a': 'a6a7f940e4bd190c9a70f3f0dc97c1c402c10200af20edac7a1d452b24abd8a0',
    'x86': '5cf145afecd72782771d5dc5e3c98c1f76e5967d8cba20d289340e6638bc47c0',
    'x86_64': '4646633ede40c4784c5584087cffc5c60cc45b16966ae0b36cedd784d7c4a11e',
}
report = []
for abi, expected in checksums.items():
    name = f'VLC-Android-3.7.1-{abi}.apk'
    apk = tools/name
    assert hashlib.sha256(apk.read_bytes()).hexdigest() == expected, name+' checksum mismatch'
    with zipfile.ZipFile(apk) as z:
        entries = [i for i in z.infolist() if i.filename.startswith('lib/'+abi+'/') and i.filename.endswith('.so')]
        assert len(entries) == 4, (name, entries)
        for entry in entries:
            dest = work/entry.filename
            dest.parent.mkdir(parents=True, exist_ok=True)
            data = z.read(entry)
            assert data[:4] == b'\x7fELF'
            dest.write_bytes(data)
            report.append({'path':entry.filename,'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data),
                'source':f'https://download.videolan.org/pub/videolan/vlc-android/3.7.1/{name}', 'apk_sha256':expected})

# The missing ABI splits are now materialized. Density resources already compiled in base.
A = '{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android', A[1:-1])
manifest = work/'AndroidManifest.xml'
r = ET.parse(manifest).getroot()
for attr in ('requiredSplitTypes','splitTypes'): r.attrib.pop(A+attr,None)
app = r.find('application')
for meta in list(app):
    if meta.tag == 'meta-data' and meta.get(A+'name') in ['com.android.vending.splits.required','com.android.vending.splits']:
        app.remove(meta)
    elif meta.tag == 'meta-data' and meta.get(A+'name') == 'com.google.android.gms.car.notification.SmallIcon' and meta.get(A+'resource') == '@null':
        # Decompiled @null becomes resource ID 0, rejected by PackageParser.
        meta.set(A+'resource','@drawable/ic_auto_notif_audio')
for attr in ('banner','logo'):
    if app.get(A+attr) == '@null': app.attrib.pop(A+attr)
app.set(A+'extractNativeLibs','false')
ET.indent(r, space='    ')
ET.ElementTree(r).write(manifest, encoding='utf-8', xml_declaration=True)
yml = work/'apktool.yml'
yml.write_text(yml.read_text()+'- so\n')
(root/'android-premiere/build/native-provenance.json').write_text(json.dumps(report,indent=2)+'\n')
print('Restored 16 native libraries across 4 ABIs; removed split-install requirements')
