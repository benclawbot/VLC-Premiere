package org.videolan.vlc.premiere;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/** Migrate cached artwork into durable, app-private storage without losing prior images. */
final class ArtworkStorage {
  private static final Pattern MANAGED = Pattern.compile(
      "(?:media|query)-[0-9a-f]{64}\\.(?:json|miss)|entity-[0-9a-f]{64}\\.jpg(?:\\.age)?");

  private ArtworkStorage() {}

  static void migrate(File oldCache, File privateFiles) throws IOException {
    if (!privateFiles.isDirectory() && !privateFiles.mkdirs()) {
      throw new IOException("Could not create private artwork folder");
    }
    if (!oldCache.isDirectory()) return;

    File[] entries = oldCache.listFiles();
    if (entries == null) throw new IOException("Could not list existing artwork");
    for (File source : entries) {
      Path from = source.toPath();
      if (!MANAGED.matcher(source.getName()).matches()
          || Files.isSymbolicLink(from) || !Files.isRegularFile(from)) continue;
      File destination = new File(privateFiles, source.getName());
      if (destination.exists()) continue; // Newer private artwork always wins.
      Path to = destination.toPath();
      try {
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException moveFailed) {
        // Allow migration if the two app-private folders are on different volumes.
        Path pending = new File(privateFiles, source.getName() + ".migrating").toPath();
        try {
          Files.copy(from, pending, StandardCopyOption.REPLACE_EXISTING);
          try {
            Files.move(pending, to, StandardCopyOption.ATOMIC_MOVE);
          } catch (IOException unsupported) {
            Files.move(pending, to);
          }
          Files.deleteIfExists(from);
        } finally {
          Files.deleteIfExists(pending);
        }
      }
    }
  }
}
