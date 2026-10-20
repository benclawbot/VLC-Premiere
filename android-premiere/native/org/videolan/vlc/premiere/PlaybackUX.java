package org.videolan.vlc.premiere;

/** Pure media UX rules; no Android dependencies, safe for JVM regression tests. */
final class PlaybackUX {
  private PlaybackUX() {}

  /** Match media by stable identity rather than Java wrapper object identity. */
  static int indexOf(String identity, Object[] queue,
      java.util.function.Function<Object, String> keyOf) {
    if (identity == null || identity.isEmpty() || queue == null) return -1;
    for (int i = 0; i < queue.length; i++)
      if (identity.equals(keyOf.apply(queue[i]))) return i;
    return -1;
  }

  static boolean matchesVideoFilter(String filter, long position, long duration, long seen) {
    boolean completed = seen > 0 || (duration > 0
        && position >= Math.ceil(duration * .95));
    boolean inProgress = position > 5000 && !completed;
    if ("unwatched".equals(filter)) return !completed && !inProgress;
    if ("in-progress".equals(filter)) return inProgress;
    if ("completed".equals(filter)) return completed;
    return true;
  }

  /** libVLC states: 1 opening, 2 buffering, 7 error; paused is intentionally silent. */
  static String videoStatus(int state) {
    if (state == 1) return "Opening video…";
    if (state == 2) return "Buffering…";
    if (state == 7) return "Playback failed";
    return "";
  }

  static int nextRepeatMode(int current) {
    return current == 0 ? 2 : current == 2 ? 1 : 0;
  }

  static String repeatLabel(int mode) {
    return mode == 1 ? "One" : mode == 2 ? "All" : "Off";
  }
}
