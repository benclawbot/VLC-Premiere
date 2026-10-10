package org.videolan.vlc.premiere;

import static org.videolan.vlc.premiere.Premiere.*;

import android.net.Uri;
import java.lang.reflect.*;
import java.util.*;

/** Uses VLC's SMB/FTP/NFS discovery and browse engine, including its login dialogs. */
final class NetworkBrowser {
  static final class Entry {
    final String title;
    final Uri uri;
    final boolean folder;

    Entry(Object media) {
      uri = (Uri) invoke(media, "getUri");
      String name = str(invoke(media, "getMeta", 0));
      title =
          name.isEmpty() ? (uri == null ? "Media" : Uri.decode(uri.getLastPathSegment())) : name;
      folder = number(invoke(media, "getType")) == 2;
    }
  }

  final Premiere.UI ui;
  final List<Entry> entries = new ArrayList<>();
  Object browser;
  boolean finished = false;
  final String location;

  NetworkBrowser(Premiere.UI ui, String address) {
    this.ui = ui;
    location = address;
  }

  void start() {
    try {
      Class<?> listener = clazz("org.videolan.libvlc.util.MediaBrowser$EventListener");
      Object callback =
          Proxy.newProxyInstance(
              listener.getClassLoader(),
              new Class<?>[] {listener},
              (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("onMediaAdded")) {
                  Entry entry = new Entry(args[1]);
                  ui.handler.post(
                      () -> {
                        entries.add(entry);
                        if (ui.screen.equals("network") || ui.screen.equals("network-share"))
                          ui.render();
                      });
                } else if (name.equals("onBrowseEnd"))
                  ui.handler.post(
                      () -> {
                        finished = true;
                        if (ui.screen.startsWith("network")) ui.render();
                      });
                return null;
              });
      Object engine =
          invoke(singleton("org.videolan.resources.VLCInstance", "INSTANCE"), "init", ui.a);
      Class<?> type = clazz("org.videolan.libvlc.util.MediaBrowser");
      browser =
          type.getConstructor(
                  clazz("org.videolan.libvlc.interfaces.ILibVLC"),
                  listener,
                  android.os.Handler.class)
              .newInstance(engine, callback, ui.handler);
      if (location.isEmpty()) invoke(browser, "discoverNetworkShares");
      else invoke(browser, "browse", location, 0);
    } catch (Exception e) {
      android.util.Log.e("Premiere", "Network browser", e);
      finished = true;
    }
  }

  void close() {
    invoke(browser, "release");
    browser = null;
  }
}
