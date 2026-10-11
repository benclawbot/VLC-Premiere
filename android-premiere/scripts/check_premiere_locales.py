#!/usr/bin/env python3
"""Fail CI when Premiere-owned translations diverge across supported locales."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
LANGS = ("values", "values-fr", "values-de")
PREFIX = "premiere_"
parsed = {}
for language in LANGS:
    path = ROOT / "res" / language / "premiere_strings.xml"
    root = ET.parse(path).getroot()
    assert root.tag == "resources", f"Invalid resources root: {path}"
    entries = {}
    for item in root:
        if item.tag != "string":
            raise AssertionError(f"Unexpected element in {path}: {item.tag}")
        name = item.attrib.get("name", "")
        if not name.startswith(PREFIX) or name in entries:
            raise AssertionError(f"Duplicate/invalid string {name} in {path}")
        text = "".join(item.itertext())
        if not text.strip():
            raise AssertionError(f"Empty translation {name} in {path}")
        entries[name] = text
    parsed[language] = entries

expected = set(parsed["values"])
for language in LANGS[1:]:
    missing, extra = expected - set(parsed[language]), set(parsed[language]) - expected
    if missing or extra:
        raise AssertionError(f"{language}: missing={sorted(missing)}, extra={sorted(extra)}")

java = (ROOT / "android-premiere/native/org/videolan/vlc/premiere/PremiereLocale.java").read_text()
mapped = re.findall(r'KEYS\.put\(".*?", "(premiere_[a-z0-9_]+)"\);', java)
if len(mapped) != len(expected) or set(mapped) != expected:
    raise AssertionError("PremiereLocale and res/values do not contain identical keys")

assert len(expected) >= 100, "Accidental major loss of localized interface coverage"
print(f"{len(expected)} localized Premiere interface strings validated in EN, FR, DE")
