#!/usr/bin/env python3
"""Strict Android emulator install/launch/media-URI smoke with retained evidence.

Does not claim to test casting hardware, manual TalkBack or every codec.
"""
from pathlib import Path
import json
import re
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build" / "smoke"
OUT.mkdir(parents=True, exist_ok=True)
PACKAGE = "org.videolan.vlc"
report = {"checks": [], "device": {}, "limitations": [
    "Emulator test only; no Chromecast/remote device connected",
    "Does not replace manual Android 16, TalkBack, RTL and large-catalogue validation",
]}


def cmd(*args, timeout=45, check=True):
    done = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    if check and done.returncode:
        raise RuntimeError(f"ADB failed ({done.returncode}): {' '.join(args)}\n{done.stderr}")
    return done.stdout.strip()


def shell(*args, timeout=45, check=True):
    return cmd("shell", *args, timeout=timeout, check=check)


def record(label, passed, detail=""):
    report["checks"].append({"name": label, "passed": bool(passed), "detail": str(detail)[:1000]})
    if not passed:
        raise AssertionError(label + ": " + str(detail))
    print("PASS", label, flush=True)


def screenshot(label):
    result = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=30)
    if result.returncode == 0 and result.stdout.startswith(b"\x89PNG"):
        (OUT / (label + ".png")).write_bytes(result.stdout)


def process_alive():
    return bool(shell("pidof", PACKAGE, check=False))


def launch_view(mime, uri):
    return shell("am", "start", "-a", "android.intent.action.VIEW",
        "-t", mime, "-d", uri, "-p", PACKAGE, timeout=30)


try:
    report["device"]["sdk"] = shell("getprop", "ro.build.version.sdk")
    report["device"]["abi"] = shell("getprop", "ro.product.cpu.abi")
    cmd("logcat", "-c")
    apk = ROOT / "dist" / "VLC-Premiere-Universal.apk"
    record("universal APK available", apk.is_file() and apk.stat().st_size > 1000000, apk)
    install = cmd("install", "-r", str(apk), timeout=150)
    record("Android package installed", "Success" in install, install)
    record("package path registered", PACKAGE in shell("pm", "path", PACKAGE), PACKAGE)

    # Permissions are scoped to the owned emulator's fixture directories.
    shell("pm", "grant", PACKAGE, "android.permission.READ_EXTERNAL_STORAGE", check=False)
    shell("appops", "set", PACKAGE, "MANAGE_EXTERNAL_STORAGE", "allow", check=False)

    shell("monkey", "-p", PACKAGE, "-c", "android.intent.category.LAUNCHER", "1", timeout=60)
    time.sleep(9)
    screenshot("launch")
    record("Premiere activity process survives startup", process_alive())
    (OUT / "activity-startup.txt").write_text(shell("dumpsys", "activity", "activities", timeout=90))

    # Exercise VLC's exported VIEW handler with real independently generated media.
    shell("mkdir", "-p", "/sdcard/Music", "/sdcard/Movies")
    cmd("push", str(OUT / "premiere-smoke.wav"), "/sdcard/Music/premiere-smoke.wav", timeout=75)
    cmd("push", str(OUT / "premiere-smoke.mp4"), "/sdcard/Movies/premiere-smoke.mp4", timeout=75)

    audio = launch_view("audio/wav", "file:///sdcard/Music/premiere-smoke.wav")
    record("WAV VIEW intent accepted", "Error" not in audio, audio)
    time.sleep(4)
    screenshot("audio-view")
    session_audio = shell("dumpsys", "media_session", timeout=90)
    (OUT / "session-audio.txt").write_text(session_audio)
    record("audio launch survives decoder startup", process_alive())

    video = launch_view("video/mp4", "file:///sdcard/Movies/premiere-smoke.mp4")
    record("MP4 VIEW intent accepted", "Error" not in video, video)
    time.sleep(3)
    screenshot("video-view")
    (OUT / "session-video.txt").write_text(shell("dumpsys", "media_session", timeout=90))
    record("video launch survives decoder startup", process_alive())

    # Surface recreation and background/foreground lifecycle smoke.
    shell("settings", "put", "system", "accelerometer_rotation", "0")
    shell("settings", "put", "system", "user_rotation", "1")
    time.sleep(2)
    screenshot("video-landscape")
    record("landscape transition preserves process", process_alive())
    shell("settings", "put", "system", "user_rotation", "0")
    shell("input", "keyevent", "KEYCODE_HOME")
    time.sleep(2)
    shell("monkey", "-p", PACKAGE, "-c", "android.intent.category.LAUNCHER", "1", timeout=60)
    time.sleep(2)
    record("background and foreground preserve process", process_alive())
    screenshot("return-home")
finally:
    shell("settings", "put", "system", "accelerometer_rotation", "1", check=False)
    try:
        log = cmd("logcat", "-d", "-v", "threadtime", timeout=120)
        (OUT / "logcat.txt").write_text(log)
        # FATAL EXCEPTION blocks that identify VLC's process are release blockers.
        fatal = re.search(r"FATAL EXCEPTION:[^\n]*(?:\n(?!FATAL EXCEPTION).*){0,5}\n.*Process: org\.videolan\.vlc", log)
        report["fatal_vlc_exception"] = bool(fatal)
        if fatal:
            report["checks"].append({"name": "No VLC fatal exception", "passed": False,
                                     "detail": fatal.group(0)[:1000]})
        else:
            report["checks"].append({"name": "No VLC fatal exception", "passed": True})
    except Exception as error:
        report["logcat_capture_error"] = repr(error)
    (OUT / "report.json").write_text(json.dumps(report, indent=2) + "\n")

if any(not item["passed"] for item in report["checks"]):
    raise SystemExit("Android emulator smoke reported failures: see build/smoke/report.json")
print("Android emulator smoke checks passed; see retained evidence", flush=True)
