package org.videolan.vlc.premiere;

import java.util.concurrent.atomic.AtomicBoolean;

/** Prevent duplicate VLC rescans while allowing a deferred scan after library initialization. */
final class LaunchScanGate {
  private final AtomicBoolean requested = new AtomicBoolean();

  boolean claim(boolean enabled, boolean libraryReady) {
    return enabled && libraryReady && requested.compareAndSet(false, true);
  }
}
