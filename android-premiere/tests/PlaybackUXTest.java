package org.videolan.vlc.premiere;

/** Executable without an Android emulator. */
public final class PlaybackUXTest {
  private static int count;
  private static void check(boolean ok, String description) {
    if (!ok) throw new AssertionError(description);
    count++;
  }
  public static void main(String[] args) {
    check(PlaybackUX.matchesVideoFilter("all", 0, 600000, 0), "all");
    check(PlaybackUX.matchesVideoFilter("unwatched", 0, 600000, 0), "unwatched");
    check(!PlaybackUX.matchesVideoFilter("unwatched", 120000, 600000, 0), "exclude progress");
    check(PlaybackUX.matchesVideoFilter("in-progress", 120000, 600000, 0), "progress");
    check(!PlaybackUX.matchesVideoFilter("in-progress", 590000, 600000, 0), "near end");
    check(PlaybackUX.matchesVideoFilter("completed", 590000, 600000, 0), "completed by progress");
    check(PlaybackUX.matchesVideoFilter("completed", 0, 600000, 1), "completed by seen");
    check(!PlaybackUX.matchesVideoFilter("in-progress", 0, 600000, 1), "seen excluded");
    check(PlaybackUX.matchesVideoFilter("unwatched", 0, -1, 0), "unknown length");
    check(PlaybackUX.matchesVideoFilter("in-progress", 6000, -1, 0), "unknown length resume");
    check("Opening video…".equals(PlaybackUX.videoStatus(1)), "opening state");
    check("Buffering…".equals(PlaybackUX.videoStatus(2)), "buffering state");
    check("Playback failed".equals(PlaybackUX.videoStatus(7)), "error state");
    check("".equals(PlaybackUX.videoStatus(3)), "playing state");
    check("".equals(PlaybackUX.videoStatus(4)), "paused not loading");
    check("".equals(PlaybackUX.videoStatus(-1)), "unknown not loading");
    check(PlaybackUX.nextRepeatMode(0) == 2, "repeat all");
    check(PlaybackUX.nextRepeatMode(2) == 1, "repeat one");
    check(PlaybackUX.nextRepeatMode(1) == 0, "repeat off");
    check("One".equals(PlaybackUX.repeatLabel(1)), "label one");
    check("All".equals(PlaybackUX.repeatLabel(2)), "label all");
    check("Off".equals(PlaybackUX.repeatLabel(0)), "label off");
    System.out.println(count + " playback UX assertions passed");
  }
}
