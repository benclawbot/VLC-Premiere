"""Offline regression tests for production issue gate and acceptance flow."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from release_gate import ReleaseBlocked, require_no_open_issues


class ReleaseGateTest(unittest.TestCase):
    def run_gate(self, payload, approved=True, token="fake-token"):
        return require_no_open_issues(token, fetch_json=lambda _: payload,
                                     verify_token=lambda owner: approved)

    def test_complete_empty_owner_search_passes(self):
        self.assertTrue(self.run_gate({"total_count": 0, "incomplete_results": False}))

    def test_open_issues_block(self):
        with self.assertRaisesRegex(ReleaseBlocked, "4 issues"):
            self.run_gate({"total_count": 4, "incomplete_results": False})

    def test_missing_token_blocks(self):
        with self.assertRaises(ReleaseBlocked):
            self.run_gate({"total_count": 0, "incomplete_results": False}, token="")

    def test_unverified_or_partial_token_blocks(self):
        with self.assertRaisesRegex(ReleaseBlocked, "classic full repo"):
            self.run_gate({"total_count": 0, "incomplete_results": False}, approved=False)

    def test_malformed_search_blocks(self):
        for payload in ({}, {"total_count": 0}, {"total_count": 0, "incomplete_results": True},
                        {"total_count": "0", "incomplete_results": False}):
            with self.subTest(payload=payload), self.assertRaises(ReleaseBlocked):
                self.run_gate(payload)

    def test_network_failure_blocks(self):
        def failed(_):
            raise OSError("network")
        with self.assertRaises(ReleaseBlocked):
            require_no_open_issues("fake-token", fetch_json=failed, verify_token=lambda _: True)

    def test_publication_stage_only_enforces_gate(self):
        source = (Path(__file__).resolve().parents[1] / "scripts" / "sign.py").read_text()
        self.assertIn("stage == 'publication'", source)
        self.assertIn("require_no_open_issues(os.environ.get('PREMIERE_RELEASE_GITHUB_TOKEN'))", source)
        self.assertLess(source.index("require_no_open_issues("), source.index("def run("))

    def test_owner_search_is_scoped(self):
        def check(url):
            self.assertIn("user%3Abenclawbot", url)
            self.assertIn("is%3Aissue", url)
            return {"total_count": 0, "incomplete_results": False}
        self.assertTrue(require_no_open_issues("fake-token", fetch_json=check,
                                               verify_token=lambda _: True))


if __name__ == "__main__":
    unittest.main()
