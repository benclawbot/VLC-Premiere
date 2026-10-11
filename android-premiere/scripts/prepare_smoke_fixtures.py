#!/usr/bin/env python3
"""Deterministic, locally generated audio/video fixtures for CI emulator use."""
from pathlib import Path
import math
import shutil
import struct
import subprocess
import wave

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build" / "smoke"
OUT.mkdir(parents=True, exist_ok=True)

wav = OUT / "premiere-smoke.wav"
rate = 22050
with wave.open(str(wav), "wb") as file:
    file.setnchannels(1)
    file.setsampwidth(2)
    file.setframerate(rate)
    for start in range(0, rate * 5, rate):
        payload = bytearray()
        for sample in range(start, min(start + rate, rate * 5)):
            value = int(8000 * math.sin(2 * math.pi * 440 * sample / rate))
            payload += struct.pack("<h", value)
        file.writeframes(payload)

ffmpeg = shutil.which("ffmpeg")
if not ffmpeg:
    raise SystemExit("FFmpeg is required for the reproducible MP4 smoke fixture")
mp4 = OUT / "premiere-smoke.mp4"
subprocess.run([
    ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
    "-f", "lavfi", "-i", "testsrc2=size=320x180:rate=12",
    "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=16000",
    "-t", "6", "-c:v", "libx264", "-preset", "ultrafast",
    "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", str(mp4)
], check=True, timeout=90)
assert wav.stat().st_size > 150000 and mp4.stat().st_size > 10000
print("Generated owned WAV and MP4 fixtures outside APK", flush=True)
