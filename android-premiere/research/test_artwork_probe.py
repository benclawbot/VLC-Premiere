import importlib.util
import unittest
import tempfile
from unittest.mock import patch
from pathlib import Path

spec = importlib.util.spec_from_file_location("artwork_probe", Path(__file__).with_name("artwork_probe.py"))
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class CandidateTests(unittest.TestCase):
    def test_supplied_video_names(self):
        mary = probe.normalize("Project.Hail.Mary.2026.IMAX.1080p.WEBRip.x264.AAC5.1-[YTS.BZ].mp4")
        self.assertEqual((mary["title"], mary["year"], mary["kind"]), ("Project Hail Mary", 2026, "video"))
        lanterns = probe.normalize("Lanterns.2026.S01E08.1080p.WEB.H264-ETHEL.mkv")
        self.assertEqual((lanterns["title"], lanterns["year"], lanterns["season"], lanterns["episode"]), ("Lanterns", 2026, 1, 8))

    def test_dotted_title_wrong_year_and_episode(self):
        self.assertEqual(probe.normalize("The.Good.The.Bad.and.The.Ugly.1966.1080p.mkv")["title"], "The Good The Bad and The Ugly")
        query = probe.normalize("Example.Movie.2024.mkv")
        self.assertEqual(probe.score(query, {"title": "Example Movie", "year": 1998}), 0)
        self.assertEqual(probe.normalize("Show.S02E03.mkv")["episode"], 3)

    def test_id3_artist_album_fields_and_metadata_first(self):
        music = probe.normalize("01.Track.Name.flac", title="Song Name", artist="The Artist", album="Great Album", date="2001-05-01")
        self.assertEqual(music["title"], "Song Name")
        self.assertEqual(music["artist"], "The Artist")
        self.assertEqual(music["year"], 2001)
        self.assertEqual(probe.lookup(music)["title"], "Great Album")
        self.assertIsNone(probe.lookup(probe.normalize("Song.mp3", title="Song Name")))
        fallback = probe.normalize("Great.Album.flac", artist="The Artist", album="Great Album")
        self.assertEqual((fallback["kind"], fallback["title"], fallback["artist"]), ("music", "Great Album", "The Artist"))

    def test_ambiguous_match_and_unicode(self):
        self.assertEqual(probe.normalize("1984.mkv")["title"], "1984")
        self.assertEqual(probe.normalize("Spider-Man.mp4")["title"], "Spider-Man")
        self.assertEqual(probe.normalize("2001.A.Space.Odyssey.1968.mkv")["title"], "2001 A Space Odyssey")
        query = {"title": "Amélie", "year": 2001}
        self.assertEqual(probe.select(query, [{"title": "Amelie", "year": 2001}])["title"], "Amelie")
        self.assertIsNone(probe.select(query, [{"title": "Amelie", "year": 2001}, {"title": "Amélie", "year": 2001}]))

    def test_storage_add_change_remove(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(probe.subprocess, "run", side_effect=OSError("fixture")):
            root = Path(directory)
            self.assertEqual(probe.scan(root), [])
            first = root / "Example.2024.mp4"
            first.write_bytes(b"fixture")
            initial = probe.scan(root)
            self.assertEqual(len(initial), 1)
            first.write_bytes(b"changed fixture")
            changed = probe.scan(root)
            self.assertNotEqual(initial[0]["cache_id"], changed[0]["cache_id"])
            second = root / "Another.2023.mkv"
            second.write_bytes(b"fixture")
            self.assertEqual(len(probe.scan(root)), 2)
            first.unlink()
            self.assertEqual(probe.scan(root)[0]["path"], str(second))


if __name__ == "__main__":
    unittest.main()
