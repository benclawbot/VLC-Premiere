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
    # VIEW resolves to VLC's transient StartActivity router. Android's -W
    # waits for that component to draw, but it immediately delegates and
    # finishes. Require downstream media processing instead.
    output = shell("am", "start", "-a", "android.intent.action.VIEW",
                   "-t", mime, "-d", uri, "-p", PACKAGE, timeout=30)
    if "Error" in output or "Starting: Intent" not in output:
        raise RuntimeError("Android rejected VIEW intent: " + output)
    return output


def assert_media_opened(kind, expected):
    # A cold emulator can launch VideoPlayerActivity before libVLC opens the
    # URI. Wait for actual media-engine evidence, with a bounded deadline.
    deadline = time.monotonic() + 30
    session = ""
    logs = ""
    evidence = False
    alive = False
    while True:
        session = shell("dumpsys", "media_session", timeout=60)
        logs = cmd("logcat", "-d", "-v", "threadtime", timeout=120)
        evidence = expected in session or (
            ("Creating an input for '" + expected + "'") in logs)
        alive = process_alive()
        if (evidence and alive) or time.monotonic() >= deadline:
            break
        time.sleep(2)
    (OUT / ("session-" + kind + ".txt")).write_text(session)
    (OUT / ("logcat-" + kind + ".txt")).write_text(logs)
    record(kind + " reached VLC media engine", evidence, expected)
    record(kind + " media process alive", alive)


def foreground_activity(state):
    # Android 16 dumpsys uses topResumedActivity, not mResumedActivity.
    for marker in ("topResumedActivity=", "mResumedActivity:",
                   "ResumedActivity:", "mFocusedApp="):
        hit = re.search(r"(?m)^\s*" + re.escape(marker) + r"\s*([^\n]+)", state)
        if hit:
            return hit.group(1)
    raise RuntimeError("Unable to determine foreground activity from dumpsys")


def complete_first_run():
    # A second test run may leave the theme step visible; explicitly finish
    # the real onboarding before asserting media launch behavior.
    import xml.etree.ElementTree as ET
    state = shell("dumpsys", "activity", "activities", timeout=45)
    if "OnboardingActivity" not in foreground_activity(state):
        return
    (OUT / "first-run-before.txt").write_text(state)
    # Android 14 can report OnboardingActivity before its Skip button has
    # laid out. Retry actual UI hierarchy evidence rather than tapping blind.
    matches = []
    for attempt in range(12):
        shell("uiautomator", "dump", "/sdcard/premiere-onboarding.xml", timeout=45)
        xml = shell("cat", "/sdcard/premiere-onboarding.xml", timeout=30)
        (OUT / "first-run-ui.xml").write_text(xml)
        nodes = list(ET.fromstring(xml).iter("node"))
        matches = [n for n in nodes if n.get("text") == "Skip" or n.get("content-desc") == "Skip"]
        if len(matches) == 1:
            break
        # Overloaded CI emulators may display a system Pixel Launcher ANR over
        # the still-running VLC onboarding activity. Recover only this exact
        # infrastructure dialog; a VLC ANR must remain an acceptance failure.
        launcher_anr = any(n.get("text") == "Pixel Launcher isn't responding" for n in nodes)
        if launcher_anr:
            waits = [n for n in nodes if n.get("resource-id") == "android:id/aerr_wait"
                     and n.get("text") == "Wait"]
            if len(waits) != 1:
                raise RuntimeError("Pixel Launcher ANR without a single Wait action")
            bounds = list(map(int, re.findall(r"\d+", waits[0].get("bounds", ""))))
            if len(bounds) != 4:
                raise RuntimeError("Pixel Launcher ANR Wait action has invalid bounds")
            screenshot("pixel-launcher-anr-" + str(attempt))
            report.setdefault("infrastructure_recoveries", []).append(
                {"kind": "pixel-launcher-anr", "attempt": attempt})
            shell("input", "tap", str((bounds[0] + bounds[2]) // 2),
                  str((bounds[1] + bounds[3]) // 2))
            time.sleep(2)
            continue
        if len(matches) > 1:
            raise RuntimeError("Ambiguous Premiere Skip controls: " + str(len(matches)))
        if attempt < 11:
            time.sleep(2)
    if len(matches) != 1:
        screenshot("onboarding-skip-missing")
        raise RuntimeError("Expected a single Premiere Skip control after bounded retries; found " + str(len(matches)))
    bounds = list(map(int, re.findall(r"\d+", matches[0].get("bounds", ""))))
    if len(bounds) != 4:
        raise RuntimeError("Onboarding Skip control has invalid bounds")
    shell("input", "tap", str((bounds[0]+bounds[2])//2), str((bounds[1]+bounds[3])//2))
    for _ in range(15):
        time.sleep(2)
        state = shell("dumpsys", "activity", "activities", timeout=45)
        resumed = foreground_activity(state)
        if "MainActivity" in resumed and PACKAGE in resumed:
            (OUT / "first-run-after.txt").write_text(state)
            record("onboarding completed before intent playback", True)
            return
    (OUT / "first-run-after.txt").write_text(state)
    raise RuntimeError("Onboarding did not navigate to MainActivity after Skip: " + foreground_activity(state))

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
    complete_first_run()
    screenshot("after-onboarding")

    # Measure actual visible interactive control bounds after Android layout.
    # Preserve the UI hierarchy and the report for diagnosing any 48dp failure.
    from a11y_targets import inspect_xml
    shell("uiautomator", "dump", "/sdcard/premiere-target-audit.xml", timeout=60)
    hierarchy = shell("cat", "/sdcard/premiere-target-audit.xml")
    (OUT / "a11y-targets.xml").write_text(hierarchy)
    density_output = shell("wm", "density")
    density_match = re.findall(r"(?:Override|Physical) density:\s*(\d+)", density_output)
    if not density_match:
        raise RuntimeError("Could not determine emulator display density")
    a11y = inspect_xml(hierarchy, int(density_match[-1]))
    (OUT / "a11y-targets.json").write_text(json.dumps(a11y, indent=2) + "\n")
    record("visible Premiere controls are at least 48dp after layout",
           not a11y["problems"] and a11y["clickable_controls"] > 0,
           a11y["problems"][:12] or a11y["clickable_controls"])

    # Re-measure accessible touch targets after Android relays a 150% font-scale
    # configuration change. Verify actual laid-out bounds, not minHeight APIs.
    initial_font_scale = shell("settings", "get", "system", "font_scale", check=False).strip()
    shell("settings", "put", "system", "font_scale", "1.5")
    try:
        shell("am", "force-stop", PACKAGE)
        shell("monkey", "-p", PACKAGE, "-c", "android.intent.category.LAUNCHER", "1", timeout=60)
        time.sleep(4)
        screenshot("font-scale-150-percent")
        shell("uiautomator", "dump", "/sdcard/premiere-a11y-large-font.xml", timeout=60)
        font_xml = shell("cat", "/sdcard/premiere-a11y-large-font.xml")
        (OUT / "a11y-large-font.xml").write_text(font_xml)
        large_font = inspect_xml(font_xml, int(density_match[-1]))
        (OUT / "a11y-large-font.json").write_text(json.dumps(large_font, indent=2) + "\n")
        record("150% font scale preserves 48dp visible Premiere controls",
               large_font["clickable_controls"] > 0 and not large_font["problems"],
               large_font["problems"][:12] or large_font["clickable_controls"])
        record("150% font scale activity survives relaunch", process_alive())
    finally:
        shell("settings", "put", "system", "font_scale",
              initial_font_scale if re.fullmatch(r"\d+(?:\.\d+)?", initial_font_scale) else "1.0",
              check=False)
        shell("am", "force-stop", PACKAGE, check=False)
        shell("monkey", "-p", PACKAGE, "-c", "android.intent.category.LAUNCHER", "1",
              timeout=60, check=False)
        time.sleep(2)

    # Exercise VLC's exported VIEW handler with real independently generated media.
    shell("mkdir", "-p", "/sdcard/Music", "/sdcard/Movies")
    cmd("push", str(OUT / "premiere-smoke.wav"), "/sdcard/Music/premiere-smoke.wav", timeout=75)
    cmd("push", str(OUT / "premiere-smoke.mp4"), "/sdcard/Movies/premiere-smoke.mp4", timeout=75)

    audio = launch_view("audio/wav", "file:///sdcard/Music/premiere-smoke.wav")
    record("WAV VIEW intent accepted", "Error" not in audio, audio)
    time.sleep(4)
    screenshot("audio-view")
    assert_media_opened("audio", "premiere-smoke.wav")

    # A media intent delivered while the short WAV is still playing can
    # open VideoPlayerActivity against the existing audio playlist. Isolate
    # codec verification by ending the audio playback session first; keep
    # the completed first-run permissions and preferences.
    shell("am", "force-stop", PACKAGE)
    # Android 13 may report the still-exiting VLC process just after
    # force-stop returns. Require observed termination within a deadline.
    audio_deadline = time.monotonic() + 12
    while process_alive() and time.monotonic() < audio_deadline:
        time.sleep(0.5)
    record("audio playback session terminated before video", not process_alive())
    cmd("logcat", "-c")

    video = launch_view("video/mp4", "file:///sdcard/Movies/premiere-smoke.mp4")
    record("MP4 VIEW intent accepted", "Error" not in video, video)
    time.sleep(3)
    screenshot("video-view")
    assert_media_opened("video", "premiere-smoke.mp4")

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
