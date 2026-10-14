#!/usr/bin/env python3
"""Reject Premiere APKs missing the RecyclerView runtime supplied by VLC.

Only classes1–6 come from VLC; classes7 contains Premiere's own UI. The
compile-only AndroidX Maven jars must never be included in classes7.
"""
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[1]
required = (
    "RecyclerView;",
    "RecyclerView$Adapter;",
    "LinearLayoutManager;",
    "GridLayoutManager;",
    "ListAdapter;",
    "DiffUtil;",
)
prefix = b"Landroidx/recyclerview/widget/"
for variant in ("arm64", "Universal"):
    apk = root / "dist" / f"VLC-Premiere-{variant}.apk"
    with zipfile.ZipFile(apk) as package:
        names = [name for name in package.namelist()
                 if name.startswith("classes") and name.endswith(".dex")
                 and name != "classes7.dex"]
        if len(names) < 6:
            raise AssertionError(f"{apk}: missing expected bundled VLC DEX files")
        contents = b"".join(package.read(name) for name in names)
        missing = [name for name in required if prefix + name.encode() not in contents]
        if missing:
            raise AssertionError(f"{apk}: VLC's AndroidX runtime is missing: {missing}")
    print(f"{variant}: bundled VLC RecyclerView, ListAdapter, GridLayoutManager and DiffUtil verified")
