package org.videolan.vlc.premiere;

/** Calculates a bounded relative video seek without overflowing long timestamps. */
final class VideoSeek {
  private VideoSeek() {}

  static long target(long current, long duration, long delta) {
    long position = Math.max(0L, current);
    if (delta >= 0) {
      position = position > Long.MAX_VALUE - delta ? Long.MAX_VALUE : position + delta;
    } else {
      position = delta < -position ? 0L : position + delta;
    }
    return duration > 0 ? Math.min(position, duration) : position;
  }
}
