#!/usr/bin/env python3
"""Audit laid-out Android UIAutomator controls for 48dp touch target regressions.

This is an emulator measurement of visible control bounds, not a substitute for
manual TalkBack, font-scaling, RTL, or hardware-keyboard acceptance.
"""
import re
import xml.etree.ElementTree as ET

BOUNDS = re.compile(r"^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$")


def inspect_xml(xml, density, package="org.videolan.vlc", minimum_dp=48):
    if density <= 0:
        raise ValueError("Display density must be positive")
    root = ET.fromstring(xml)
    problems = []
    audited = 0
    for node in root.iter("node"):
        if (node.get("package") != package
                or node.get("clickable") != "true"
                or node.get("visible-to-user") == "false"):
            continue
        match = BOUNDS.fullmatch(node.get("bounds", ""))
        if not match:
            problems.append({"type": "invalid-bounds", "bounds": node.get("bounds", "")})
            continue
        x1, y1, x2, y2 = map(int, match.groups())
        width_dp = (x2 - x1) * 160 / density
        height_dp = (y2 - y1) * 160 / density
        audited += 1
        if min(width_dp, height_dp) < minimum_dp - 0.75:
            problems.append({
                "type": "undersized-target",
                "text": (node.get("content-desc") or node.get("text") or "")[:100],
                "bounds": node.get("bounds", ""),
                "width_dp": round(width_dp, 1),
                "height_dp": round(height_dp, 1),
            })
    return {"clickable_controls": audited, "density_dpi": density,
            "minimum_dp": minimum_dp, "problems": problems}
