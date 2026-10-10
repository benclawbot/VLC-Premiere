#!/usr/bin/env python3
"""Restore density split resources with exact original IDs, retaining custom UI edits."""
from pathlib import Path
import hashlib, json, os, shutil, subprocess, sys
import xml.etree.ElementTree as ET
sys.path.insert(0, str(Path(__file__).resolve().parent))
from toolchain import apktool_jar, java_tool

root = Path(__file__).resolve().parents[2]
project = root/'android-premiere'
work = Path(sys.argv[1])
reference = project/'tools/official-res'
apk = project/'tools/VLC-Android-3.7.1-x86_64.apk'
assert hashlib.sha256(apk.read_bytes()).hexdigest() == '4646633ede40c4784c5584087cffc5c60cc45b16966ae0b36cedd784d7c4a11e'
if not reference.exists():
    subprocess.run([str(java_tool('java')),'-jar',str(apktool_jar()),
        'd','-s','-o',str(reference),str(apk)], check=True)
ref = reference/'res'
pubfile = work/'res/values/public.xml'
pub = ET.parse(pubfile).getroot(); upstream = ET.parse(ref/'values/public.xml').getroot()
current = {(i.get('type'),i.get('name')):i.get('id') for i in pub}
official = {(i.get('type'),i.get('name')):i.get('id') for i in upstream}
assert all(current[k] == official[k] for k in current.keys() & official.keys()), 'Resource ID mismatch with official release'
missing = official.keys()-current.keys()
assert len(missing) == 106 and all(kind=='drawable' for kind,name in missing)
ids=set(current.values())
for entry in upstream:
    if (entry.get('type'),entry.get('name')) in missing:
        assert entry.get('id') not in ids
        pub.append(entry)
ET.indent(pub,space='    ')
ET.ElementTree(pub).write(pubfile,encoding='utf-8',xml_declaration=True)
added=[]
for folder in ref.glob('drawable*'):
    for f in folder.iterdir():
        name=f.name.split('.')[0]
        if ('drawable',name) in missing:
            dest=work/'res'/folder.name/f.name
            dest.parent.mkdir(exist_ok=True)
            shutil.copy2(f,dest);added.append(str(dest.relative_to(work)))
# Resource shrink/decode changed missing file references to @null. Restore only
# null references that the exact matching release resolves to drawable resources.
repaired=[]
for f in (work/'res').rglob('*.xml'):
    other=ref/f.relative_to(work/'res')
    if not other.exists() or f==pubfile:continue
    data=f.read_text()
    if '@null' not in data:continue
    tree=ET.fromstring(data); source=ET.parse(other).getroot()
    # Layout binding graphs must remain untouched. Restore attributes by matching
    # existing nodes only when both serialized resource trees have the same graph.
    nodes=list(tree.iter()); originals=list(source.iter())
    if len(nodes)!=len(originals) or any(a.tag!=b.tag for a,b in zip(nodes,originals)):continue
    changes=0
    for a,b in zip(nodes,originals):
        for key,val in list(a.attrib.items()):
            target=b.get(key,'')
            if val=='@null' and target.startswith(('@drawable/','@mipmap/')):
                a.set(key,target);changes+=1
        if a.text and a.text.strip()=='@null' and b.text and b.text.strip().startswith(('@drawable/','@mipmap/')):
            a.text=b.text;changes+=1
    if changes:
        ET.register_namespace('android','http://schemas.android.com/apk/res/android')
        ET.register_namespace('app','http://schemas.android.com/apk/res-auto')
        ET.indent(tree,space='    ')
        ET.ElementTree(tree).write(f,encoding='utf-8',xml_declaration=True)
        repaired.append({'path':str(f.relative_to(work)),'references':changes})
(project/'build/density-restoration.json').write_text(json.dumps({'missing_resource_names':sorted(name for kind,name in missing),'added_files':added,'repaired_files':repaired,'existing_id_changes':0},indent=2)+'\n')
print(f'Restored {len(missing)} density resource names, {len(added)} files and {sum(i["references"] for i in repaired)} null references; original IDs unchanged')
