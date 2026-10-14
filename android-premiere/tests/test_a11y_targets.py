"""Pure Python bounds and package-scoping regressions for the on-device audit."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from a11y_targets import inspect_xml


def tree(*children):
    return "<hierarchy>" + "".join(children) + "</hierarchy>"


def control(bounds, package="org.videolan.vlc", visible="true"):
    return '<node package="' + package + '" clickable="true" visible-to-user="' + visible + '" bounds="' + bounds + '" text="Play" />'


class TouchTargetTest(unittest.TestCase):
    def test_48_dp_targets_at_high_density(self):
        result = inspect_xml(tree(control("[0,0][144,144]")), 480)
        self.assertEqual(result["clickable_controls"], 1)
        self.assertEqual(result["problems"], [])

    def test_small_target_fails_after_layout(self):
        result = inspect_xml(tree(control("[0,0][90,144]")), 480)
        self.assertEqual(len(result["problems"]), 1)
        self.assertEqual(result["problems"][0]["type"], "undersized-target")

    def test_external_and_hidden_controls_are_ignored(self):
        xml = tree(control("[0,0][10,10]", "com.android.settings"),
                   control("[0,0][10,10]", visible="false"))
        result = inspect_xml(xml, 480)
        self.assertEqual(result["clickable_controls"], 0)

    def test_missing_or_invalid_bounds_cannot_pass(self):
        result = inspect_xml(tree(control("")), 480)
        self.assertEqual(result["problems"][0]["type"], "invalid-bounds")

    def test_rejects_invalid_density(self):
        with self.assertRaises(ValueError):
            inspect_xml(tree(control("[0,0][144,144]")), 0)


if __name__ == "__main__":
    unittest.main()
