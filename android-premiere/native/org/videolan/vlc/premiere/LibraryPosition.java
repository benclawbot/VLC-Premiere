package org.videolan.vlc.premiere;

import java.util.function.IntFunction;

/** Preserve a visible media identity when asynchronous library updates reorder rows. */
final class LibraryPosition {
  private LibraryPosition() {}

  /** Find the nearest occurrence of the anchored row, falling back to its prior index.
   * Avoid materializing a second full-library key array for 10k+ media items.
   */
  static int restore(int previous, int count, String anchor, IntFunction<String> keyAt) {
    if (count <= 0) return 0;
    int baseline = Math.max(0, Math.min(previous, count - 1));
    if (anchor == null || anchor.isEmpty()) return baseline;
    if (anchor.equals(keyAt.apply(baseline))) return baseline;
    for (int distance = 1; distance < count; distance++) {
      int earlier = baseline - distance;
      if (earlier >= 0 && anchor.equals(keyAt.apply(earlier))) return earlier;
      int later = baseline + distance;
      if (later < count && anchor.equals(keyAt.apply(later))) return later;
      if (earlier < 0 && later >= count) break;
    }
    return baseline;
  }
}
