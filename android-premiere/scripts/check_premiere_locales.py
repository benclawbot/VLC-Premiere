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

# Enforce translation coverage for static user-facing labels on Premiere's main
# navigation/presentation surface. Symbols and concatenation prefixes are not labels.
PREMIERE = (ROOT / "android-premiere/native/org/videolan/vlc/premiere/Premiere.java").read_text()
english_labels = set(re.findall(
    r'\\b(?:text|button|heading|label|destination|group)\\s*\\(\\s*"([^"]+)"',
    PREMIERE,
))
translated_labels = set(re.findall(r'KEYS\\.put\\("([^"]+)",', java))
NON_TEXT_LABELS = {
    "VLC", "▶", "↑", "×", "“", "▲",
    "Show more ",  # Followed by a known audioTab; each category is translated.
    "Use an exact film or TV title to improve poster matching. ",
    # The full concatenated literal is translated by premiere_correct_title_hint.
}
missing_labels = sorted(english_labels - translated_labels - NON_TEXT_LABELS)
if missing_labels:
    raise AssertionError(f"Unlocalized static Premiere labels: {missing_labels}")

FORMAT_KEYS = {
    "premiere_current_speed_format": ["%s"],
    "premiere_version_format": ["%s"],
    "premiere_delete_named_media": ["%s"],
}
for language in LANGS:
    for key, formats in FORMAT_KEYS.items():
        text = parsed[language][key]
        placeholders = re.findall(r"%(?:[1-9]\\d*\\$)?[sd]", text)
        normalized = [re.sub(r"%[1-9]\\d*\\$", "%", part) for part in placeholders]
        if normalized != formats:
            raise AssertionError(f"{language}/{key}: expected {formats}, got {placeholders}")

PLURAL_FORMATS = {
    "premiere_video_count": ["%d"],
    "premiere_video_filtered": ["%1$d", "%2$d"],
    "premiere_track_count": ["%d"],
    "premiere_show_tracks": ["%d"],
}
for language in LANGS:
    file = ROOT / "res" / language / "premiere_plurals.xml"
    resource = ET.parse(file).getroot()
    assert resource.tag == "resources", f"Invalid plural resource root: {file}"
    plurals = {}
    for plural in resource:
        if plural.tag != "plurals":
            raise AssertionError(f"Unexpected plural resource in {file}: {plural.tag}")
        name = plural.get("name", "")
        if name in plurals:
            raise AssertionError(f"Duplicate plural name {name} in {file}")
        forms = {}
        for item in plural:
            quantity = item.get("quantity")
            if item.tag != "item" or quantity in forms:
                raise AssertionError(f"Invalid/duplicate quantity in {file}/{name}")
            text = "".join(item.itertext())
            if not text.strip():
                raise AssertionError(f"Empty plural form: {file}/{name}/{quantity}")
            forms[quantity] = re.findall(r"%(?:[1-9]\d*\$)?d", text)
        plurals[name] = forms
    if set(plurals) != set(PLURAL_FORMATS):
        raise AssertionError(f"{language}: plural resource groups differ from baseline")
    for name, expected_format in PLURAL_FORMATS.items():
        forms = plurals[name]
        if set(forms) != {"one", "other"}:
            raise AssertionError(f"{language}/{name}: one and other plural forms required")
        if any(forms[form] != expected_format for form in forms):
            raise AssertionError(f"{language}/{name}: plural format argument mismatch")

print(f"{len(expected)} localized Premiere interface strings and {len(PLURAL_FORMATS)} plural groups validated in EN, FR, DE")
