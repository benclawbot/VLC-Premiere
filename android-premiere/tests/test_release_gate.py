"""Production-release gate must fail closed; network is mocked."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from release_gate import ReleaseBlocked, require_no_open_issues


class ReleaseGateTest(unittest.TestCase):
    def test_green_only_when_owner_wide_search_is_complete_and_empty(self):
        self.assertTrue(require_no_open_issues("fake-token", fetch_json=lambda _: {
            "total_count": 0, "incomplete_results": False, "items": [],
        }))

    def test_open_issue_blocks_even_with_otherwise_complete_response(self):
        with self.assertRaisesRegex(ReleaseBlocked, "4 issues"):
            require_no_open_issues("fake-token", fetch_json=lambda _: {
                "total_count": 4, "incomplete_results": False, "items": [],
            })

    def test_missing_token_fails_closed(self):
        with self.assertRaisesRegex(ReleaseBlocked, "required"):
            require_no_open_issues("", fetch_json=lambda _: {
                "total_count": 0, "incomplete_results": False,
            })

    def test_unknown_or_incomplete_response_fails_closed(self):
        for payload in ({}, {"total_count": 0}, {"total_count": 0, "incomplete_results": True},
                        {"total_count": "0", "incomplete_results": False}):
            with self.subTest(payload=payload), self.assertRaises(ReleaseBlocked):
                require_no_open_issues("fake-token", fetch_json=lambda _: payload)

    def test_network_errors_never_open_release_gate(self):
        def offline(url):
            raise OSError("unreachable")
        with self.assertRaisesRegex(ReleaseBlocked, "Cannot verify"):
            require_no_open_issues("fake-token", fetch_json=offline)

    def test_owner_filter_is_part_of_query(self):
        def check(url):
            self.assertIn("user%3Abenclawbot", url)
            self.assertIn("is%3Aissue", url)
            return {"total_count": 0, "incomplete_results": False}
        require_no_open_issues("fake-token", fetch_json=check)


if __name__ == "__main__":
    unittest.main()
