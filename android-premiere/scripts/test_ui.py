#!/usr/bin/env python3
"""Inspect and exercise only the owned Premiere emulator, using live UI bounds."""
from pathlib import Path
import re, subprocess, sys, time, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
adb=root/'tools/sdk/platform-tools/adb'
def run(*args, binary=False):
    r=subprocess.run([str(adb),'-s','emulator-5580',*args],check=True,capture_output=True,timeout=25)
    return r.stdout if binary else r.stdout.decode()
def hierarchy():
    # Restarting activities and a busy media surface can briefly have no root.
    # Clear the old dump before every attempt; never fall back to stale bounds.
    failure=None
    for attempt in range(4):
        try:
            run('shell','rm','-f','/sdcard/premiere-ui.xml')
            result=run('shell','uiautomator','dump','/sdcard/premiere-ui.xml')
            if 'ERROR' in result: raise RuntimeError(result)
            data=run('shell','cat','/sdcard/premiere-ui.xml')
            tree=ET.fromstring(data)
            (root/'build/ui.xml').write_text(data)
            return tree
        except (RuntimeError,subprocess.CalledProcessError,subprocess.TimeoutExpired,ET.ParseError) as error:
            failure=error
            if attempt<3: time.sleep(1)
    raise RuntimeError('No fresh Android UI hierarchy after four attempts') from failure
command=sys.argv[1]
if command=='tap':
    query=sys.argv[2]
    nodes=[e for e in hierarchy().iter('node') if e.get('resource-id')=='org.videolan.vlc:id/'+query or e.get('text')==query or e.get('content-desc')==query]
    # Native composed controls can expose the label on both the clickable parent
    # and a non-clickable child. Prefer the control, retaining strict ambiguity
    # checks when multiple actual controls share the same label.
    clickable=[e for e in nodes if e.get('clickable')=='true']
    if len(clickable)==1: nodes=clickable
    assert len(nodes)==1,(query,len(nodes))
    node=nodes[0];bounds=list(map(int,re.findall(r'\d+',node.get('bounds'))))
    assert node.get('enabled')=='true'
    run('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2))
elif command=='snapshot':
    name=sys.argv[2]
    r=hierarchy()
    (root/'previews').mkdir(exist_ok=True)
    (root/'previews'/f'{name}.png').write_bytes(run('exec-out','screencap','-p',binary=True))
    (root/'build'/f'ui-{name}.xml').write_text(ET.tostring(r,encoding='unicode'))
    for e in r.iter('node'):
        if e.get('text') or e.get('content-desc'):
            print(e.get('text') or e.get('content-desc'),e.get('resource-id'),e.get('bounds'))
else:raise SystemExit('Use tap ID/TEXT or snapshot NAME')
