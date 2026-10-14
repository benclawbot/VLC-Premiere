#!/usr/bin/env python3
"""Download checksum-pinned AndroidX RecyclerView 1.3.2 *compile-only* API.

The VLC 3.7.1 APK already packages the AndroidX RecyclerView runtime.
Never inject this jar into classes7.dex or overwrite the bundled AndroidX code.
"""
from pathlib import Path
from io import BytesIO
import hashlib
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
TOOLS.mkdir(exist_ok=True, parents=True)
AAR = TOOLS / "recyclerview-1.3.2.aar"
API = TOOLS / "recyclerview-1.3.2.jar"
SHA256 = "005cf51510493a24fa48baae1a455a5258515391351a71717dd33cba95211966"
URL = ("https://dl.google.com/dl/android/maven2/androidx/recyclerview/"
       "recyclerview/1.3.2/recyclerview-1.3.2.aar")

def verified(data):
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise SystemExit("AndroidX RecyclerView dependency checksum mismatch")
    return data

if AAR.exists():
    archive = verified(AAR.read_bytes())
else:
    with urllib.request.urlopen(URL, timeout=90) as remote:
        archive = verified(remote.read())
    AAR.write_bytes(archive)

with zipfile.ZipFile(BytesIO(archive)) as opened:
    jar = opened.read("classes.jar")
if not API.exists() or API.read_bytes() != jar:
    API.write_bytes(jar)
print("Verified RecyclerView 1.3.2 compile-only API:", hashlib.sha256(jar).hexdigest())
