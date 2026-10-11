package org.videolan.vlc.premiere;

/** Pure media UX rules; no Android dependencies, safe for JVM regression tests. */
final class PlaybackUX {
  private PlaybackUX() {}

  static boolean matchesVideoFilter(String filter, long position, long duration, long seen) {
    boolean completed = seen > 0 || (duration > 0
        && position >= Math.ceil(duration * .95));
    boolean inProgress = position > 5000 && !completed;
    if ("unwatched".equals(filter)) return !completed && !inProgress;
    if ("in-progress".equals(filter)) return inProgress;
    if ("completed".equals(filter)) return completed;
    return true;
  }

  static int nextRepeatMode(int current) {
    return current == 0 ? 2 : current == 2 ? 1 : 0;
  }

  static String repeatLabel(int mode) {
    return mode == 1 ? "One" : mode == 2 ? "All" : "Off";
  }
}
