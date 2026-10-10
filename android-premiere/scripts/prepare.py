#!/usr/bin/env python3
"""Create an isolated apktool project with the decoded six-dex tree flattened."""
from pathlib import Path
import hashlib, shutil, subprocess, sys, tempfile
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from toolchain import apktool_jar, java_tool
WORK = ROOT / 'android-premiere' / 'work'
if WORK.exists():
    shutil.rmtree(WORK)
WORK.mkdir(parents=True)
for name in ('AndroidManifest.xml', 'apktool.yml', 'res', 'assets', 'unknown'):
    src = ROOT / name
    dst = WORK / name
    if src.is_dir(): shutil.copytree(src, dst)
    elif src.exists(): shutil.copy2(src, dst)
smali_root = ROOT / 'smali'
if not all((smali_root / dex).is_dir() for dex in ('smali', 'smali_classes2', 'smali_classes3', 'smali_classes4', 'smali_classes5', 'smali_classes6')):
    apk = ROOT / 'android-premiere' / 'tools' / 'VLC-Android-3.7.1-arm64-v8a.apk'
    expected = 'bea9ea0cdecfd8f461ef305d2afe5d1ac1459cfb9156acce2f615788a486de74'
    if not apk.is_file() or hashlib.sha256(apk.read_bytes()).hexdigest() != expected:
        raise SystemExit(f'Missing decoded smali tree and verified baseline APK: {apk}. Run fetch_dependencies.py first.')
    cache = ROOT / 'android-premiere' / 'baseline' / 'original-arm64-decoded'
    if not all((cache / dex).is_dir() for dex in ('smali', 'smali_classes2', 'smali_classes3', 'smali_classes4', 'smali_classes5', 'smali_classes6')):
        cache.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='vlc-decode-', dir=cache.parent) as temp:
            decoded = Path(temp) / 'decoded'
            subprocess.run([str(java_tool('java')), '-jar', str(apktool_jar()), 'd', '-f', '-o', str(decoded), str(apk)], check=True)
            if not all((decoded / dex).is_dir() for dex in ('smali', 'smali_classes2', 'smali_classes3', 'smali_classes4', 'smali_classes5', 'smali_classes6')):
                raise SystemExit('The verified arm64 APK did not decode into the required six dex trees.')
            if cache.exists():
                raise SystemExit(f'Incomplete decode cache exists at {cache}; inspect it before removing it.')
            decoded.rename(cache)
    smali_root = cache
for dex in ('smali', 'smali_classes2', 'smali_classes3', 'smali_classes4', 'smali_classes5', 'smali_classes6'):
    src = smali_root / dex
    if not src.is_dir():
        raise SystemExit(f'Missing decoded dex tree: {src}')
    shutil.copytree(src, WORK / dex)
print(f'Prepared {WORK}')
