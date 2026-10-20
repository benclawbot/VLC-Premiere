package org.videolan.vlc.premiere;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Filename-based TV season detection, separate from cached artwork metadata. */
final class SeriesUX {
  private static final Pattern EPISODE = Pattern.compile(
      "^(.*?)[ ._-]+[Ss](\\d{1,2})[ ._-]*[Ee](\\d{1,3})(?:\\D|$)");

  static final class Episode {
    final String series;
    final int season;
    final int episode;
    Episode(String series, int season, int episode) {
      this.series = series;
      this.season = season;
      this.episode = episode;
    }
    String group() { return series + " · Season " + season; }
    String episodeCode() {
      return String.format(java.util.Locale.ROOT, "S%02dE%02d", season, episode);
    }
    String displayTitle() { return series + " · " + episodeCode(); }
  }

  static Episode from(String filename) {
    if (filename == null) return null;
    Matcher match = EPISODE.matcher(filename);
    if (!match.find()) return null;
    String name = match.group(1).replaceAll("[._-]+", " ").trim()
        .replaceAll("\\s+", " ");
    if (name.isEmpty()) return null;
    try {
      int season = Integer.parseInt(match.group(2));
      int episode = Integer.parseInt(match.group(3));
      if (season > 99 || episode <= 0) return null;
      return new Episode(name, season, episode);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }
}
