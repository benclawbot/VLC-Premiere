#!/usr/bin/env python3
"""XML well-formedness and binding signature checks for the isolated work tree."""
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'android-premiere/work'
ORIG=ROOT
A='{http://schemas.android.com/apk/res/android}'
changed=[str(f.relative_to(WORK/'res')) for f in (WORK/'res').glob('layout*/*.xml') if (ORIG/'res'/f.relative_to(WORK/'res')).exists() and f.read_bytes()!=(ORIG/'res'/f.relative_to(WORK/'res')).read_bytes()]
structural_layouts={'layout/video_grid.xml'}
def signature(path):
    root=ET.parse(path).getroot()
    # Check every view's class and parent/order as well as IDs/tags. Generated binding
    # implementations can depend on untagged nodes and includes too.
    return [(e.tag,e.get(A+'id'),e.get(A+'tag'),e.get('layout'),len(e)) for e in root.iter()]
failed=[]
xml_count=0
for folder in ('res',):
    for f in (WORK/folder).rglob('*.xml'):
        xml_count+=1
        try: ET.parse(f)
        except Exception as e: failed.append(f'{f.relative_to(WORK)}: {e}')
for rel in changed:
    a,b=signature(ORIG/'res'/rel),signature(WORK/'res'/rel)
    if rel not in structural_layouts:
        if a!=b: failed.append(f'{rel}: data-binding id/tag signature differs')
        else: print(f'Binding signature unchanged: {rel} ({len(a)} binding/id nodes)')
    else:
        # This screen intentionally adds a native composition around the existing
        # adapter. Preserve every original view identity and binding tag in order.
        identity=lambda rows:[(row[0],row[1],row[2],row[3]) for row in rows]
        old_ids=identity(a); new_ids=identity(b); cursor=0
        for item in new_ids:
            if cursor<len(old_ids) and item==old_ids[cursor]: cursor+=1
        if cursor!=len(old_ids): failed.append(f'{rel}: existing binding view identities/order changed')
        else: print(f'Binding signature preserved with approved native additions: {rel} ({len(a)} original nodes, {len(b)-len(a)} added)')
if failed:
    print('\n'.join(failed)); raise SystemExit(1)
print(f'XML parse check passed: {xml_count} resource XML files parsed')
