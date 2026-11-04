from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from verify_release_upgrade import verify_upgrade


OLD_CERT = "ab" * 32
OLD_SIGNATURE = "Signer #1 certificate SHA-256 digest: " + OLD_CERT
OLD_APK = "package: name='org.videolan.vlc' versionCode='13070109' versionName='3.7.1'"
NEW_APK = "package: name='org.videolan.vlc' versionCode='13070110' versionName='3.7.1'"
FILES = [
    {"file": f"VLC-Premiere-{variant}.apk", "signing_mode": "release",
     "certificate_sha256": OLD_CERT}
    for variant in ("arm64", "Universal")
]


class UpgradeCompatibilityTests(unittest.TestCase):
    def check(self, artifacts=None, signature=OLD_SIGNATURE, old=OLD_APK,
              arm64=NEW_APK, universal=NEW_APK):
        return verify_upgrade(
            FILES if artifacts is None else artifacts,
            signature, old, arm64, universal,
        )

    def test_update_retains_identity_and_monotonic_version(self):
        self.assertEqual(self.check(), (OLD_CERT, 13070109))

    def test_rejects_certificate_rotation(self):
        with self.assertRaisesRegex(ValueError, "certificate does not match"):
            self.check(artifacts=[
                dict(item, certificate_sha256="cd" * 32) for item in FILES
            ])

    def test_rejects_ephemeral_development_key(self):
        with self.assertRaisesRegex(ValueError, "development-signed"):
            self.check(artifacts=[
                dict(item, signing_mode="development") for item in FILES
            ])

    def test_rejects_downgrade_and_equal_version(self):
        with self.assertRaisesRegex(ValueError, "versionCode"):
            self.check(arm64=OLD_APK)
        with self.assertRaisesRegex(ValueError, "versionCode"):
            self.check(universal=OLD_APK)

    def test_rejects_changed_package(self):
        with self.assertRaisesRegex(ValueError, "package"):
            self.check(universal=NEW_APK.replace("org.videolan.vlc", "example.new"))

    def test_rejects_wrong_or_incomplete_variants(self):
        with self.assertRaisesRegex(ValueError, "two signed APK"):
            self.check(artifacts=FILES[:1])

    def test_rejects_invalid_cert_report(self):
        with self.assertRaisesRegex(ValueError, "certificate"):
            self.check(signature="invalid")

    def test_rejects_missing_badging(self):
        with self.assertRaisesRegex(ValueError, "package identity"):
            self.check(old="invalid")


if __name__ == "__main__":
    unittest.main()
