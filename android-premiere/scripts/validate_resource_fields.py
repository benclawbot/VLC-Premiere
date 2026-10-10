#!/usr/bin/env python3
"""Catch resource-field linkage errors that apktool's assembler cannot detect."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
work = root / 'android-premiere/work'
descriptor = 'Lorg/videolan/vlc/R$id;'
r_file = work / 'smali_classes6/org/videolan/vlc/R$id.smali'
fields = set(re.findall(r'^\.field[^\n]*\s([\w$]+):I(?:\s|=|$)', r_file.read_text(), re.M))
errors = []
checked = 0
for folder in work.glob('smali*'):
    for path in folder.rglob('*.smali'):
        text = path.read_text()
        names = set(re.findall(re.escape(descriptor) + r'->([\w$]+):I', text))
        checked += len(names)
        for name in sorted(names - fields):
            errors.append(f'{path.relative_to(work)}: undefined {descriptor}->{name}:I')
if errors:
    print('\n'.join(errors))
    raise SystemExit(1)
print(f'VLC resource-field linkage verified: {checked} references, {len(fields)} defined IDs')
