package org.videolan.vlc.premiere;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ArtworkStorageTest {
  private static int checks;
  private static void require(boolean yes, String why) {
    checks++;
    if (!yes) throw new AssertionError(why);
  }

  public static void main(String[] args) throws Exception {
    Path root = Files.createTempDirectory("premiere-artwork-test");
    File legacy = root.resolve("cache/premiere-art").toFile();
    File durable = root.resolve("files/premiere-art").toFile();
    require(legacy.mkdirs(), "fixture cache created");
    String hash = "a".repeat(64);
    File cover = new File(legacy, "entity-" + hash + ".jpg");
    File record = new File(legacy, "media-" + hash + ".json");
    File age = new File(legacy, "entity-" + hash + ".jpg.age");
    Files.write(cover.toPath(), "previous-cover".getBytes(StandardCharsets.UTF_8));
    Files.write(record.toPath(), "previous-metadata".getBytes(StandardCharsets.UTF_8));
    Files.write(age.toPath(), "previous-age".getBytes(StandardCharsets.UTF_8));
    Files.write(new File(legacy, "unsafe.txt").toPath(), "must-stay".getBytes(StandardCharsets.UTF_8));

    ArtworkStorage.migrate(legacy, durable);
    require(new File(durable, cover.getName()).isFile(), "album cover migrated");
    require(new File(durable, record.getName()).isFile(), "artwork metadata migrated");
    require(new File(durable, age.getName()).isFile(), "cache age migrated");
    require(new File(legacy, "unsafe.txt").isFile(), "unmanaged files untouched");
    require(!cover.exists(), "old asset moved, not duplicated");
    require("previous-cover".equals(Files.readString(new File(durable, cover.getName()).toPath())),
        "cached image bytes preserved");

    Files.write(cover.toPath(), "stale-cover".getBytes(StandardCharsets.UTF_8));
    ArtworkStorage.migrate(legacy, durable);
    require("previous-cover".equals(Files.readString(new File(durable, cover.getName()).toPath())),
        "existing durable artwork not overwritten");
    require(cover.isFile(), "stale legacy image left untouched");
    ArtworkStorage.migrate(root.resolve("missing").toFile(), durable);
    require(new File(durable, record.getName()).isFile(), "missing old cache does not clear artwork");

    System.out.println(checks + " artwork storage assertions passed");
  }
}
