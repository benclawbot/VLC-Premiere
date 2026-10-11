#!/usr/bin/env python3
"""Fetch immutable, checksum-pinned VLC and Android build dependencies."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.request import Request, urlopen
import hashlib
import json
import os
import zipfile

BASE = Path(__file__).resolve().parents[1]
TOOLS = BASE / "tools"
TOOLS.mkdir(exist_ok=True)
MANIFEST = json.loads((BASE / "DEPENDENCIES.json").read_text())


def digest(path):
    sha = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            sha.update(chunk)
    return sha.hexdigest()


def download(entry):
    name, expected = entry["file"], entry["sha256"].lower()
    if not expected or len(expected) != 64 or any(x not in "0123456789abcdef" for x in expected):
        raise ValueError("Invalid pinned SHA256 for " + name)
    url = entry["url"]
    if not url.startswith("https://"):
        raise ValueError("Only HTTPS dependencies are permitted: " + name)
    target = TOOLS / name
    if not target.is_file() or digest(target) != expected:
        pending = TOOLS / (name + ".partial")
        try:
            with urlopen(Request(url, headers={"User-Agent": "VLC-Premiere-CI/1.0"}), timeout=120) as response, pending.open("wb") as file:
                for chunk in iter(lambda: response.read(1024 * 1024), b""):
                    file.write(chunk)
            if digest(pending) != expected:
                raise ValueError("Pinned checksum mismatch: " + name)
            if int(entry.get("size") or 0) and pending.stat().st_size != int(entry["size"]):
                raise ValueError("Pinned file size mismatch: " + name)
            os.replace(pending, target)
        finally:
            pending.unlink(missing_ok=True)
    assert digest(target) == expected, name + " SHA256 did not match pinned manifest"

    if name.endswith(".zip"):
        destination = TOOLS / ("build-tools" if name.startswith("build-tools") else "platform-tools-package")
        destination.mkdir(exist_ok=True)
        with zipfile.ZipFile(target) as archive:
            for info in archive.infolist():
                relative = Path(info.filename)
                if relative.is_absolute() or ".." in relative.parts:
                    raise ValueError("Unsafe dependency ZIP path " + info.filename)
                resolved = (destination / relative).resolve()
                if not resolved.is_relative_to(destination.resolve()):
                    raise ValueError("Dependency ZIP path traversal")
                archive.extract(info, destination)
                mode = info.external_attr >> 16
                if mode and not info.is_dir():
                    os.chmod(resolved, mode & 0o777)
    print(name, target.stat().st_size, "bytes · pinned SHA256 verified", flush=True)
    return {"file": name, "sha256": expected, "bytes": target.stat().st_size, "url": url}


if not MANIFEST:
    raise SystemExit("Missing pinned dependencies")
with ThreadPoolExecutor(max_workers=3) as pool:
    report = list(pool.map(download, MANIFEST))
(TOOLS / "dependency-provenance.json").write_text(json.dumps(report, indent=2) + "\n")
print("All pinned build dependencies verified", flush=True)
