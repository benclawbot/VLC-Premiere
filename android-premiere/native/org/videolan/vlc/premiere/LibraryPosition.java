package org.videolan.vlc.premiere;

import java.util.function.IntFunction;

/** Preserve a visible media identity when asynchronous library updates reorder rows. */
final class LibraryPosition {
  private LibraryPosition() {}

  /** Stable per-cell focus destinations survive recycled view recreation. */
  static final String FOCUS_CARD = "premiere-media-card";
  static final String FOCUS_ACTIONS = "premiere-media-actions";
  static final String FOCUS_PLAY = "premiere-media-play";

  static String focusTarget(Object tag) {
    if (FOCUS_ACTIONS.equals(tag)) return FOCUS_ACTIONS;
    if (FOCUS_PLAY.equals(tag)) return FOCUS_PLAY;
    return FOCUS_CARD;
  }

  /** Deterministic 64-bit FNV-1a key for RecyclerView stable adapter IDs. */
  static long stableId(String value) {
    long hash = 0xcbf29ce484222325L;
    for (int i = 0; i < value.length(); i++) {
      hash ^= value.charAt(i);
      hash *= 0x100000001b3L;
    }
    return hash;
  }

  /** Find the same media identity or report its deletion; do not transfer focus to another item.
   * Examine nearby rows first without materializing an extra key array for large libraries.
   */
  static int find(int previous, int count, String anchor, IntFunction<String> keyAt) {
    if (count <= 0 || anchor == null || anchor.isEmpty()) return -1;
    int baseline = Math.max(0, Math.min(previous, count - 1));
    if (anchor.equals(keyAt.apply(baseline))) return baseline;
    for (int distance = 1; distance < count; distance++) {
      int earlier = baseline - distance;
      if (earlier >= 0 && anchor.equals(keyAt.apply(earlier))) return earlier;
      int later = baseline + distance;
      if (later < count && anchor.equals(keyAt.apply(later))) return later;
      if (earlier < 0 && later >= count) break;
    }
    return -1;
  }

  /** Keep the viewport at a valid row even when the anchored media was deleted. */
  static int restore(int previous, int count, String anchor, IntFunction<String> keyAt) {
    if (count <= 0) return 0;
    int matched = find(previous, count, anchor, keyAt);
    return matched >= 0 ? matched : Math.max(0, Math.min(previous, count - 1));
  }
}
