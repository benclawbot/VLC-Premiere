package org.videolan.vlc.premiere;

public final class SeriesUXTest {
  private static int count;
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
    count++;
  }
  public static void main(String[] args) {
    SeriesUX.Episode a = SeriesUX.from("The.Last.Show.S01E02.1080p.mkv");
    check(a != null, "season parse");
    check("The Last Show".equals(a.series), "normalized name");
    check(a.season == 1 && a.episode == 2, "correct season/episode");
    check("The Last Show · Season 1".equals(a.group()), "season label");
    check("The Last Show · S01E02".equals(a.displayTitle()), "recent episodes have an ID");
    SeriesUX.Episode b = SeriesUX.from("My Show - s12e105 - Title.mp4");
    check(b != null && b.season == 12 && b.episode == 105, "three digit episode");
    check("My Show · S12E105".equals(b.displayTitle()), "three digit episode label");
    check(SeriesUX.from("Film.2026.1080p.mkv") == null, "movie not episode");
    check(SeriesUX.from("S02E01.mp4") == null, "missing series");
    check(SeriesUX.from("My.Show.S01E00.mkv") == null, "invalid episode");
    check(SeriesUX.from(null) == null, "null safe");
    check(SeriesUX.from("Music.mp3") == null, "music not episode");
    System.out.println(count + " TV grouping assertions passed");
  }
}
