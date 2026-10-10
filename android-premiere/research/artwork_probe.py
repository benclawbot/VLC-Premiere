#!/usr/bin/env python3
"""Local-only artwork matching probe. No network requests are made."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import unicodedata
from difflib import SequenceMatcher
from pathlib import Path

VIDEO_EXTS = {".mp4", ".mkv", ".avi", ".mov", ".m4v", ".webm"}
NOISE = re.compile(r"\b(?:\d{3,4}p|\d{3,4}x\d{3,4}|4k|8k|uhd|hdr10?|dv|web[- .]?dl|webrip|bluray|brrip|hdtv|h\.?26[45]|x26[45]|av1|aac(?:\d(?:\.\d)?)?|dts(?:[- .]?hd)?|ddp?\d(?:\.\d)?|proper|repack|remux|imax)\b", re.I)
YEAR = re.compile(r"(?<!\d)(19\d{2}|20\d{2})(?!\d)")
EPISODE = re.compile(r"(?i)\bS(\d{1,2})[ ._-]*E(\d{1,3})\b")
GROUP = re.compile(r"\s*[-[]\s*[A-Z0-9.]{2,}\s*[])]?\s*$", re.I)


def normalize(name: str, title: str = "", artist: str = "", album: str = "", date: str = "") -> dict:
    raw = Path(name).stem
    episode = EPISODE.search(raw)
    # A title such as "1984" or "2001 A Space Odyssey" is not a release year.
    year_match = next((m for m in reversed(list(YEAR.finditer(raw))) if raw[:m.start()].strip(" ._-")), None)
    year = int(year_match.group(1)) if year_match else None
    if episode:
        raw = raw[:episode.start()]
    if year_match:
        raw = raw[:year_match.start()]
    # A trailing hyphen can be part of the title (Spider-Man). Only infer a
    # release-group suffix when other release/encoding markers are present.
    if NOISE.search(raw):
        raw = GROUP.sub("", raw)
    raw = NOISE.sub(" ", raw).replace(".", " ").replace("_", " ")
    raw = re.sub(r"\[[^]]*\]|\([^)]*\)", " ", raw)
    filename_title = " ".join(raw.split()).strip(" -_[]()")
    # Tags/stream metadata take precedence. Filename only supplies missing metadata.
    is_music = bool(artist or album or re.search(r"(?i)\.(?:mp3|flac|m4a|ogg|opus|wav)$", name))
    chosen = title.strip() or (album.strip() if is_music and album else filename_title)
    track_artist = artist.strip()
    if date:
        m = YEAR.search(date)
        year = int(m.group(1)) if m else year
    return {"kind": "music" if is_music else ("episode" if episode else "video"),
            "title": chosen, "album": album.strip(), "artist": track_artist, "year": year,
            "season": int(episode.group(1)) if episode else None,
            "episode": int(episode.group(2)) if episode else None}


def tokens(s: str) -> set[str]:
    folded = unicodedata.normalize("NFKD", s.casefold())
    return set(re.findall(r"[^\W_]+", "".join(c for c in folded if not unicodedata.combining(c))))


def lookup(metadata: dict) -> dict | None:
    """Use album + artist for a cover lookup; never substitute a track title."""
    if metadata.get("kind") == "music":
        if not metadata.get("album") or not metadata.get("artist"):
            return None
        return {"kind": "music", "title": metadata["album"], "artist": metadata["artist"], "year": metadata.get("year")}
    return {k: metadata.get(k) for k in ("kind", "title", "year", "season", "episode")}


def score(query: dict, candidate: dict) -> float:
    """Conservative deterministic score. Return zero for known year conflicts."""
    if query.get("kind") != "music" and query.get("year") and candidate.get("year") and query["year"] != candidate["year"]:
        return 0.0
    qt, ct = tokens(query.get("title", "")), tokens(candidate.get("title", ""))
    jaccard = len(qt & ct) / max(1, len(qt | ct))
    title_score = 0.8 * jaccard + 0.2 * SequenceMatcher(None, " ".join(sorted(qt)), " ".join(sorted(ct))).ratio()
    if query.get("kind") == "music":
        qa, ca = tokens(query.get("artist", "")), tokens(candidate.get("artist", ""))
        if not qa or not ca:
            return 0.0
        title_score = 0.65 * title_score + 0.35 * len(qa & ca) / max(1, len(qa | ca))
    return title_score


def select(query: dict, candidates: list[dict]) -> dict | None:
    ranked = sorted(((score(query, c), c) for c in candidates), key=lambda row: row[0], reverse=True)
    if not ranked or ranked[0][0] < 0.90:
        return None
    if len(ranked) > 1 and ranked[0][0] - ranked[1][0] < 0.12:
        return None
    return ranked[0][1]


def identity(path: Path, metadata: dict) -> str:
    st = path.stat()
    # Path/size/mtime + parsed metadata changes invalidate this local cache identity.
    raw = json.dumps([str(path.resolve()), st.st_size, st.st_mtime_ns, metadata], sort_keys=True)
    return hashlib.sha256(raw.encode()).hexdigest()


def inspect(path: Path) -> dict:
    cmd = ["ffprobe", "-v", "error", "-show_entries", "format_tags=title,artist,album,date,album_artist:stream_tags=title,artist,album,date", "-of", "json", str(path)]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=15, check=True)
        obj = json.loads(result.stdout)
    except (OSError, subprocess.SubprocessError, json.JSONDecodeError):
        obj = {}
    tags = {}
    for stream in obj.get("streams", []):
        tags.update({k.casefold(): v for k, v in stream.get("tags", {}).items()})
    tags.update({k.casefold(): v for k, v in obj.get("format", {}).get("tags", {}).items()})
    md = normalize(path.name, tags.get("title", ""), tags.get("album_artist", "") or tags.get("artist", ""), tags.get("album", ""), tags.get("date", ""))
    return {"path": str(path), "metadata": md, "lookup": lookup(md), "cache_id": identity(path, md)}


def scan(directory: Path) -> list[dict]:
    return [inspect(path) for path in sorted(p for p in directory.rglob("*") if p.is_file() and p.suffix.casefold() in VIDEO_EXTS | {".mp3", ".flac", ".m4a", ".ogg", ".opus", ".wav"})]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path, help="directory to scan locally")
    args = parser.parse_args()
    if not args.directory.is_dir():
        parser.error("directory does not exist")
    for record in scan(args.directory):
        print(json.dumps(record, ensure_ascii=False))


if __name__ == "__main__":
    main()
