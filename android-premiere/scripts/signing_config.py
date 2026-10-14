"""Fail-closed selection of CI development versus pinned production APK signing."""
from pathlib import Path
import os
import re


def select_signing(root, env=None):
    if env is None:
        env = os.environ
    fields = ("PREMIERE_RELEASE_KEYSTORE", "PREMIERE_RELEASE_PASSWORD_FILE",
              "PREMIERE_RELEASE_ALIAS", "PREMIERE_RELEASE_CERT_SHA256")
    provided = [bool(env.get(field)) for field in fields]
    if any(provided):
        if not all(provided):
            raise ValueError("Release signing requires all four PREMIERE_RELEASE_* settings")
        key = Path(env[fields[0]]).expanduser().resolve()
        password = Path(env[fields[1]]).expanduser().resolve()
        if not key.is_file() or not password.is_file():
            raise ValueError("Release keystore/password file is missing")
        fingerprint = env[fields[3]].strip().lower().replace(":", "")
        if not re.fullmatch(r"[0-9a-f]{64}", fingerprint):
            raise ValueError("Expected release certificate SHA-256 must be 64 hex digits")
        return {"mode": "release", "key": key, "password": password,
                "alias": env[fields[2]], "fingerprint": fingerprint}
    root = Path(root)
    return {"mode": "development", "key": root / "signing/premiere-development.p12",
            "password": root / "signing/password.txt",
            "alias": "premiere", "fingerprint": None}


def verify_certificate(output, config):
    import re
    match = re.search(r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", output)
    if not match:
        raise ValueError("Could not read the signed APK's certificate fingerprint")
    fingerprint = match.group(1).lower().replace(":", "")
    if config["fingerprint"] is not None and fingerprint != config["fingerprint"]:
        raise ValueError("Release APK is signed with an unexpected certificate")
    return fingerprint
