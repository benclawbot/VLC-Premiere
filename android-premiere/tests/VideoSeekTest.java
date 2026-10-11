package org.videolan.vlc.premiere;

/** Run with javac/java; does not depend on Android classes. */
public final class VideoSeekTest {
  private static int count;

  private static void check(long expected, long current, long duration, long delta) {
    long actual = VideoSeek.target(current, duration, delta);
    if (actual != expected) {
      throw new AssertionError("seek target " + actual + " instead of " + expected);
    }
    count++;
  }

  public static void main(String[] args) {
    check(300000L, 180000L, 600000L, 120000L); // right double tap: +2 minutes
    check(60000L, 180000L, 600000L, -120000L); // left double tap: -2 minutes
    check(0L, 45000L, 600000L, -120000L); // rewind cannot pass beginning
    check(600000L, 590000L, 600000L, 120000L); // forward cannot pass end
    check(120000L, 0L, -1L, 120000L); // unknown duration remains seekable
    check(0L, -3000L, 600000L, -120000L); // invalid playback time is bounded
    check(600000L, Long.MAX_VALUE - 10L, 600000L, 120000L); // overflow is bounded
    check(0L, 500L, Long.MAX_VALUE, Long.MIN_VALUE); // underflow is bounded
    check(20000L, 10000L, 600000L, 10000L); // existing transport buttons
    System.out.println(count + " video seek assertions passed");
  }
}
