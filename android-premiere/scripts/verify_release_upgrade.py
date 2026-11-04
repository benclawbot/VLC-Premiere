#!/usr/bin/env python3
"""Fail closed before publishing an APK that cannot update the latest published version."""
import argparse
import json
import re
from pathlib import Path

_EXPECTED_PACKAGE = "org.videolan.vlc"


def certificate_digest(output):
    match = re.search(
        r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", output
    )
    if not match:
        raise ValueError("Cannot read the previous release's signing certificate")
    digest = match.group(1).replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", digest):
        raise ValueError("Invalid previous certificate SHA-256")
    return digest


def badging(output):
    match = re.search(
        r"^package:\s+name='([^']+)'\s+versionCode='(\d+)'",
        output, re.MULTILINE
    )
    if not match:
        raise ValueError("Cannot read APK package identity and versionCode")
    return match.group(1), int(match.group(2))


def verify_upgrade(artifacts, old_signature, old_badging, new_arm64_badging,
                   new_universal_badging):
    prior_cert = certificate_digest(old_signature)
    prior_package, prior_version = badging(old_badging)
    if prior_package != _EXPECTED_PACKAGE:
        raise ValueError("Previous release is not VLC Première's package")
    if not isinstance(artifacts, list) or len(artifacts) != 2:
        raise ValueError("Expected exactly two signed APK variants")

    files = {item["file"]: item for item in artifacts}
    if set(files) != {"VLC-Premiere-arm64.apk", "VLC-Premiere-Universal.apk"}:
        raise ValueError("Missing or unexpected APK variant")
    for filename, output in (
        ("VLC-Premiere-arm64.apk", new_arm64_badging),
        ("VLC-Premiere-Universal.apk", new_universal_badging),
    ):
        entry = files[filename]
        if entry.get("signing_mode") != "release":
            raise ValueError("Refusing to publish a development-signed APK")
        if entry.get("certificate_sha256", "").lower().replace(":", "") != prior_cert:
            raise ValueError(
                "Signing certificate does not match the latest published APK; "
                "in-place updates would fail and private TMDB/artwork data could be lost "
                "if a user uninstalled. Use the original release keystore."
            )
        package, version = badging(output)
        if package != prior_package:
            raise ValueError("APK package name changed; app data would not be retained")
        if version <= prior_version:
            raise ValueError(
                "APK versionCode must exceed the latest published versionCode "
                f"({version} <= {prior_version})"
            )
    return prior_cert, prior_version


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifacts", type=Path, required=True)
    parser.add_argument("--old-certificate", type=Path, required=True)
    parser.add_argument("--old-badging", type=Path, required=True)
    parser.add_argument("--new-arm64-badging", type=Path, required=True)
    parser.add_argument("--new-universal-badging", type=Path, required=True)
    args = parser.parse_args()
    cert, old_version = verify_upgrade(
        json.loads(args.artifacts.read_text()),
        args.old_certificate.read_text(),
        args.old_badging.read_text(),
        args.new_arm64_badging.read_text(),
        args.new_universal_badging.read_text(),
    )
    print(f"Upgrade-compatible production certificate {cert}; previous versionCode {old_version}")


if __name__ == "__main__":
    main()
