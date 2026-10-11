#!/usr/bin/env python3
"""Exercise the real Android 13 onboarding notification permission path."""
from pathlib import Path
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build" / "notification-smoke"
OUT.mkdir(parents=True, exist_ok=True)
PACKAGE = "org.videolan.vlc"
results = []

def adb(*args, timeout=40, check=True):
    p = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    if check and p.returncode:
        raise RuntimeError(f"adb {' '.join(args)} failed: {p.stderr.decode(errors='replace')}")
    return p.stdout.decode(errors="replace")

def shell(*args, **kw):
    return adb("shell", *args, **kw)

def hierarchy():
    shell("uiautomator", "dump", "/sdcard/premiere-notification.xml", timeout=60)
    data = shell("cat", "/sdcard/premiere-notification.xml")
    (OUT / "hierarchy.xml").write_text(data)
    return ET.fromstring(data)

def save(label):
    (OUT / (label + ".txt")).write_text(shell("dumpsys", "activity", "activities", timeout=60, check=False))
    p = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=35)
    if p.stdout.startswith(b"\x89PNG"):
        (OUT / (label + ".png")).write_bytes(p.stdout)

def tap_label(label):
    found = [node for node in hierarchy().iter("node")
             if node.get("text") == label or node.get("content-desc") == label]
    found = [node for node in found if len(re.findall(r"\d+", node.get("bounds", ""))) == 4]
    if len(found) != 1:
        raise RuntimeError(f"Expected control {label!r}, found {len(found)}")
    a,b,c,d=map(int,re.findall(r"\d+",found[0].get("bounds","")))
    shell("input","tap",str((a+c)//2),str((b+d)//2))

def status(name, okay, extra=""):
    results.append(dict(check=name, passed=bool(okay), detail=extra))
    if not okay:
        raise RuntimeError(name + ": " + extra)
    print("PASS",name,flush=True)

try:
    status("API 33+",int(shell("getprop","ro.build.version.sdk").strip())>=33)
    status("install", "Success" in adb("install","-r",str(ROOT/"dist/VLC-Premiere-Universal.apk"),timeout=160))
    adb("logcat","-c")
    shell("monkey","-p",PACKAGE,"-c","android.intent.category.LAUNCHER","1",timeout=60)
    time.sleep(7)
    save("initial")
    # The original onboarding owns the state machine. We use its real buttons
    # instead of writing a preference to bypass notification permission.
    for index in range(16):
        root=hierarchy()
        buttons=[node.get("text","") for node in root.iter("node")]
        if "Allow notifications" in buttons:
            save("before-notification")
            tap_label("Allow notifications")
            time.sleep(3)
            root=hierarchy()
            nodes=[(n.get("text",""),n.get("resource-id","")) for n in root.iter("node")]
            (OUT/"permission-dialog.json").write_text(json.dumps(nodes,indent=2))
            allow=[n for n in root.iter("node") if n.get("resource-id","").endswith("permission_allow_button")]
            if allow:
                a,b,c,d=map(int,re.findall(r"\d+",allow[0].get("bounds","")))
                shell("input","tap",str((a+c)//2),str((b+d)//2))
                time.sleep(4)
            save("after-notification")
            status("notification permission onboarding survives",bool(shell("pidof",PACKAGE,check=False).strip()))
            granted=shell("dumpsys","package",PACKAGE,timeout=60,check=False)
            (OUT/"package-permissions.txt").write_text(granted)
            status("notifications permission granted","android.permission.POST_NOTIFICATIONS: granted=true" in granted)
            break
        if "Skip" in buttons and index==15:
            raise RuntimeError("Could not reach notification onboarding; last screen had " + repr(buttons[:35]))
        # Android 16 may open its dedicated All files access Settings screen.
        # This is an app-op settings toggle, not a runtime Allow button.
        # Exercise the real toggle, verify its state, and return to onboarding.
        if "Allow access to manage all files" in buttons:
            save("all-files-access-before")
            switches = [n for n in root.iter("node")
                        if n.get("checkable") == "true"
                        and n.get("clickable") == "true"]
            if len(switches) != 1:
                raise RuntimeError("Expected one All files access switch, got " + str(len(switches)))
            if switches[0].get("checked") != "true":
                a,b,c,d = map(int,re.findall(r"\\d+",switches[0].get("bounds","")))
                shell("input","tap",str((a+c)//2),str((b+d)//2))
                time.sleep(1)
                checked = [n for n in hierarchy().iter("node")
                           if n.get("checkable") == "true"]
                status("all-files access switch activated",
                       len(checked) == 1 and checked[0].get("checked") == "true")
            shell("input","keyevent","KEYCODE_BACK")
            time.sleep(2)
            save("all-files-access-return")
            continue
        # Use actual call-to-action on each onboarding stage, and grant runtime
        # Android media permission when its system dialog appears.
        candidates=["Get started","Allow media access","Continue","Find my media",
                    "Next","Scan","Start watching"]
        matching=[text for text in candidates if text in buttons]
        if matching:
            tap_label(matching[0])
        else:
            dialog=[n for n in root.iter("node") if n.get("resource-id","").endswith("permission_allow_button")]
            if dialog:
                a,b,c,d=map(int,re.findall(r"\d+",dialog[0].get("bounds","")))
                shell("input","tap",str((a+c)//2),str((b+d)//2))
            else:
                raise RuntimeError("Unrecognized onboarding state: "+repr(buttons[:60]))
        time.sleep(3)
    else:
        raise RuntimeError("Notification step not reached")
finally:
    (OUT/"logcat.txt").write_text(adb("logcat","-d","-v","threadtime",timeout=120,check=False))
    (OUT/"results.json").write_text(json.dumps(results,indent=2)+"\n")
