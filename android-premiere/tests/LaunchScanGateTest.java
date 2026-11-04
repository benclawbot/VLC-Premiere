package org.videolan.vlc.premiere;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class LaunchScanGateTest {
  private static int assertions;

  private static void require(boolean result, String message) {
    assertions++;
    if (!result) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    LaunchScanGate gate = new LaunchScanGate();
    require(!gate.claim(false, false), "disabled and uninitialized cannot scan");
    require(!gate.claim(false, true), "disabled preference cannot scan");
    require(!gate.claim(true, false), "wait until medialibrary is initialized");
    require(gate.claim(true, true), "first ready launch schedules scan");
    require(!gate.claim(true, true), "resuming playback cannot schedule again");
    require(!gate.claim(false, true), "turning the preference off never scans");
    require(!gate.claim(true, true), "turning it back on does not duplicate process scan");

    final int callers = 16;
    LaunchScanGate concurrent = new LaunchScanGate();
    AtomicInteger accepted = new AtomicInteger();
    CountDownLatch ready = new CountDownLatch(callers);
    CountDownLatch start = new CountDownLatch(1);
    Thread[] threads = new Thread[callers];
    for (int i = 0; i < callers; i++) {
      threads[i] = new Thread(() -> {
        ready.countDown();
        try {
          start.await();
          if (concurrent.claim(true, true)) accepted.incrementAndGet();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      });
      threads[i].start();
    }
    ready.await();
    start.countDown();
    for (Thread thread : threads) thread.join();
    require(accepted.get() == 1, "concurrent callbacks schedule only one scan");
    System.out.println(assertions + " launch scan gate assertions passed");
  }
}
