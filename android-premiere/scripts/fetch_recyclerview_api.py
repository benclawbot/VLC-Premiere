#!/usr/bin/env python3
"""Download checksum-pinned AndroidX compile-only API jars for VLC's bundled runtime.

These jars never enter Premiere's DEX or replace VLC's own AndroidX libraries.
"""
from pathlib import Path
from io import BytesIO
import hashlib
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
TOOLS.mkdir(exist_ok=True, parents=True)

DEPS = [
    ("androidx/recyclerview/recyclerview", "recyclerview", "1.3.2",
     "005cf51510493a24fa48baae1a455a5258515391351a71717dd33cba95211966"),
    ("androidx/core/core", "core", "1.12.0",
     "42ffa7ca47d7ba8fe1d874c57ef9c7111bc41a2b0c0c21518a39e07d222ded8b"),
]

for module, name, version, expected_sha in DEPS:
    aar = TOOLS / f"{name}-{version}.aar"
    api = TOOLS / f"{name}-{version}.jar"
    if aar.exists():
        archive = aar.read_bytes()
    else:
        url = f"https://dl.google.com/dl/android/maven2/{module}/{version}/{name}-{version}.aar"
        with urllib.request.urlopen(url, timeout=90) as remote:
            archive = remote.read()
    if hashlib.sha256(archive).hexdigest() != expected_sha:
        raise SystemExit(f"{name} {version} dependency checksum mismatch")
    if not aar.exists():
        aar.write_bytes(archive)
    with zipfile.ZipFile(BytesIO(archive)) as opened:
        jar = opened.read("classes.jar")
    if not api.exists() or api.read_bytes() != jar:
        api.write_bytes(jar)
    print(f"Verified {name} {version} compile-only API: {hashlib.sha256(jar).hexdigest()}")
