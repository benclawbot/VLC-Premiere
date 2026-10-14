from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from signing_config import select_signing, verify_certificate


class SigningConfigTest(unittest.TestCase):
    def test_ci_uses_development_certificate_without_release_inputs(self):
        config = select_signing(Path("/tmp/premiere"), {})
        self.assertEqual(config["mode"], "development")
        self.assertEqual(config["alias"], "premiere")

    def test_partial_production_secrets_fail_closed(self):
        with self.assertRaises(ValueError):
            select_signing("/tmp", {"PREMIERE_RELEASE_KEYSTORE": "/tmp/a.p12"})

    def test_production_secret_and_fingerprint_must_match(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            key, password = path / "key.p12", path / "password.txt"
            key.write_bytes(b"test fixture")
            password.write_text("test password")
            expected = "ab" * 32
            config = select_signing(path, {
                "PREMIERE_RELEASE_KEYSTORE": str(key),
                "PREMIERE_RELEASE_PASSWORD_FILE": str(password),
                "PREMIERE_RELEASE_ALIAS": "production",
                "PREMIERE_RELEASE_CERT_SHA256": expected,
            })
            self.assertEqual(config["mode"], "release")
            self.assertEqual(config["alias"], "production")
            self.assertEqual(verify_certificate(
                "Signer #1 certificate SHA-256 digest: " + expected, config), expected)
            with self.assertRaises(ValueError):
                verify_certificate("Signer #1 certificate SHA-256 digest: " + "cd" * 32, config)

    def test_invalid_fingerprint_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "key").touch()
            (path / "password").touch()
            with self.assertRaises(ValueError):
                select_signing(path, {
                    "PREMIERE_RELEASE_KEYSTORE": str(path / "key"),
                    "PREMIERE_RELEASE_PASSWORD_FILE": str(path / "password"),
                    "PREMIERE_RELEASE_ALIAS": "production",
                    "PREMIERE_RELEASE_CERT_SHA256": "not a sha256",
                })


if __name__ == "__main__":
    unittest.main()
