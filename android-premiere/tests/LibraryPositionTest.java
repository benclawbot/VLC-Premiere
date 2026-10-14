package org.videolan.vlc.premiere;

public final class LibraryPositionTest {
  private static int checks;
  private static void require(boolean yes, String reason) {
    checks++;
    if (!yes) throw new AssertionError(reason);
  }

  public static void main(String[] args) {
    String[] records = {"zero", "one", "two", "three"};
    require(LibraryPosition.restore(2, records.length, "two", i -> records[i]) == 2,
        "stable list retains anchor");
    String[] added = {"new", "zero", "one", "two", "three"};
    require(LibraryPosition.restore(2, added.length, "two", i -> added[i]) == 3,
        "insertion before viewport retains item");
    String[] reordered = {"three", "two", "one", "zero"};
    require(LibraryPosition.restore(2, reordered.length, "two", i -> reordered[i]) == 1,
        "sort changes preserve item");
    String[] removed = {"zero", "one", "three"};
    require(LibraryPosition.restore(2, removed.length, "two", i -> removed[i]) == 2,
        "removed anchor falls back safely");
    require(LibraryPosition.restore(999, removed.length, null, i -> removed[i]) == 2,
        "out of range positions clamp");
    require(LibraryPosition.restore(22, 0, "two", i -> { throw new AssertionError("no media"); }) == 0,
        "empty library doesn't probe rows");
    String[] duplicates = {"x", "a", "x", "b", "x"};
    require(LibraryPosition.restore(3, duplicates.length, "x", i -> duplicates[i]) == 2,
        "duplicates prefer nearest previous row");
    require(LibraryPosition.restore(0, 20000, "item19999",
        i -> "item" + i) == 19999,
        "large libraries can restore an anchor beyond the visible range");
    // Media callbacks can reorder thousands of rows while one row has keyboard focus.
    require(LibraryPosition.restore(500, 10000, "video-9090",
        i -> "video-" + i) == 9090,
        "10k video rows retain the focused media identity across a reorder");
    require(LibraryPosition.restore(19999, 20000, "track-3",
        i -> "track-" + i) == 3,
        "20k audio tracks retain the focused identity near the start");
    require(LibraryPosition.restore(19999, 20000, "missing",
        i -> "track-" + i) == 19999,
        "deleted focused media falls back to a valid row");
    System.out.println(checks + " library scroll restoration assertions passed");
  }
}
