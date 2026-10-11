package org.videolan.vlc.premiere;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.xmlpull.v1.XmlPullParser;

/** Native Premiere presentation. VLC retains its application, library and player. */
public final class Premiere {
  static String afterSetup = "";
  private static final Map<Activity, UI> live = new WeakHashMap<>();
  private static final ExecutorService images = Executors.newFixedThreadPool(2);
  private static final ExecutorService mediaQueries = Executors.newSingleThreadExecutor();
  private static final Map<Class<?>, Method[]> methodCache = new ConcurrentHashMap<>();
  private static final android.util.LruCache<String, Bitmap> cache =
      new android.util.LruCache<String, Bitmap>(16 * 1024 * 1024) {
        protected int sizeOf(String k, Bitmap b) {
          return b.getAllocationByteCount();
        }
      };

  static boolean auxiliary(Activity a) {
    return Arrays.asList(
            "AuthorsActivity",
            "LibrariesActivity",
            "FeedbackActivity",
            "DebugLogActivity",
            "EqualizerSettingsActivity",
            "PinCodeActivity",
            "OTPCodeActivity",
            "RemoteAccessShareActivity")
        .contains(a.getClass().getSimpleName());
  }

  public static void boot(Application app) {
    app.registerActivityLifecycleCallbacks(
        new Application.ActivityLifecycleCallbacks() {
          public void onActivityCreated(Activity a, Bundle b) {
            if (a.getClass().getName().equals("org.videolan.vlc.gui.MainActivity")
                || a.getClass().getName().equals("org.videolan.vlc.gui.video.VideoPlayerActivity")
                || a.getClass()
                    .getName()
                    .equals("org.videolan.vlc.gui.onboarding.OnboardingActivity")
                || a.getClass().getName().contains("RemoteAccessOnboardingActivity")
                || a.getClass()
                    .getName()
                    .equals("org.videolan.vlc.gui.preferences.PreferencesActivity")
                || auxiliary(a)) {
              a.getWindow()
                  .getDecorView()
                  .post(
                      () -> {
                        UI u =
                            a.getClass().getName().contains("VideoPlayerActivity")
                                ? new VideoUI(a)
                                : a.getClass().getName().contains("OnboardingActivity")
                                    ? new SetupUI(a)
                                    : a.getClass()
                                            .getName()
                                            .equals(
                                                "org.videolan.vlc.gui.preferences.PreferencesActivity")
                                        ? new PreferencesUI(a)
                                        : auxiliary(a) ? new AuxUI(a) : new UI(a);
                        live.put(a, u);
                        u.install(b);
                        invoke(clazz("org.videolan.vlc.premiere.PremiereBackCallback"), "register", a);
                      });
            }
          }

          public void onActivityResumed(Activity a) {
            UI u = live.get(a);
            if (u != null) u.refresh();
          }

          public void onActivitySaveInstanceState(Activity a, Bundle b) {
            UI u = live.get(a);
            if (u != null) {
              b.putString("premiere_screen", u.screen);
              b.putString("premiere_xml", u.xml);
            }
          }

          public void onActivityDestroyed(Activity a) {
            UI u = live.remove(a);
            if (u != null) {
              u.handler.removeCallbacksAndMessages(null);
              if (u.networkBrowser != null) u.networkBrowser.close();
              u.unwatchLibrary();
            }
          }

          public void onActivityPaused(Activity a) {}

          public void onActivityStarted(Activity a) {}

          public void onActivityStopped(Activity a) {}
        });
  }

  static void navigate(Activity from, String screen) {
    for (Map.Entry<Activity, UI> entry : new ArrayList<>(live.entrySet()))
      if (entry.getKey().getClass().getName().equals("org.videolan.vlc.gui.MainActivity")) {
        UI main = entry.getValue();
        main.history.clear();
        main.screen = screen;
        main.resetScrollOnRender = true;
        main.refresh();
        from.finish();
        return;
      }
  }

  public static void result(Activity a, int request, int code, Intent data) {
    if (request == 774) {
      UI ui = live.get(a);
      if (ui != null) {
        Runnable action = ui.protectedAction;
        ui.protectedAction = null;
        if (code == Activity.RESULT_OK && action != null) {
          ui.authorized = true;
          try {
            action.run();
          } finally {
            ui.authorized = false;
          }
        }
      }
      return;
    }
    if (request == 773) {
      UI ui = live.get(a);
      if (code == Activity.RESULT_OK && ui != null) {
        ui.history.push(new State(ui));
        ui.screen = ui.pendingScreen;
        ui.resetScrollOnRender = true;
        ui.render();
      }
      return;
    }
    if (code != Activity.RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    if (request == 771) invoke(service(), "addSubtitleTrack", uri, true);
    else if (request == 772)
      invoke(singleton("org.videolan.vlc.media.MediaUtils", "INSTANCE"), "openUri", a, uri);
  }

  public static boolean back(Activity a) {
    UI u = live.get(a);
    return u != null && u.back();
  }

  static Object invoke(Object target, String name, Object... args) {
    if (target == null) return null;
    Class<?> cls = target instanceof Class ? (Class<?>) target : target.getClass();
    Method[] methods = methodCache.get(cls);
    if (methods == null) {
      methods = cls.getMethods();
      Method[] existing = methodCache.putIfAbsent(cls, methods);
      if (existing != null) methods = existing;
    }
    for (Method m : methods)
      if (m.getName().equals(name) && m.getParameterTypes().length == args.length) {
        Class<?>[] types = m.getParameterTypes();
        boolean match = true;
        for (int i = 0; i < types.length; i++)
          if (args[i] != null && !box(types[i]).isInstance(args[i])) match = false;
        if (!match) continue;
        try {
          return m.invoke(target instanceof Class ? null : target, args);
        } catch (Exception e) {
          android.util.Log.e("Premiere", name, e);
          return null;
        }
      }
    return null;
  }

  static Class<?> box(Class<?> c) {
    if (c == boolean.class) return Boolean.class;
    if (c == int.class) return Integer.class;
    if (c == long.class) return Long.class;
    if (c == float.class) return Float.class;
    if (c == double.class) return Double.class;
    return c;
  }

  static Object singleton(String cls, String field) {
    try {
      return Class.forName(cls).getField(field).get(null);
    } catch (Exception e) {
      return null;
    }
  }

  static Class<?> clazz(String name) {
    try {
      return Class.forName(name);
    } catch (Exception e) {
      return null;
    }
  }

  static Object library() {
    return invoke(clazz("org.videolan.medialibrary.interfaces.Medialibrary"), "getInstance");
  }

  static Object service() {
    return invoke(singleton("org.videolan.vlc.PlaybackService", "Companion"), "getInstance");
  }

  static void seekTo(long position) {
    Object playback = service();
    if (playback == null) return;
    long length = number(invoke(playback, "getLength"));
    long destination = Math.max(0, length > 0 ? Math.min(position, length) : position);
    invoke(playback, "setTime", destination, false);
    // VLC's original video controller updates its progress model after a seek.
    // A paused decoder may not emit a time event until playback resumes.
    Object player = invoke(invoke(playback, "getPlaylistManager"), "getPlayer");
    invoke(player, "updateProgress", destination, length);
  }

  static void update(TextView view, String value) {
    if (view != null && !value.contentEquals(view.getText())) view.setText(value);
  }

  static String str(Object x) {
    return x == null ? "" : String.valueOf(x);
  }

  static String title(Object x) {
    return str(invoke(x, "getTitle"));
  }

  static long number(Object x) {
    return x instanceof Number ? ((Number) x).longValue() : 0;
  }

  static Object[] array(Object x) {
    if (x instanceof List) return ((List<?>) x).toArray();
    if (x instanceof Object[]) return (Object[]) x;
    return new Object[0];
  }

  static Object[] tracks(Object x) {
    Object a = invoke(x, "getTracks");
    if (a == null) a = invoke(x, "getTracks", true, false);
    return array(a);
  }

  static String time(long ms) {
    long s = Math.max(0, ms / 1000);
    return s >= 3600
        ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
  }

  static final class State {
    String screen, xml, networkLocation;
    Object item;
    File folder;

    State(UI u) {
      screen = u.screen;
      xml = u.xml;
      item = u.item;
      folder = u.folder;
      networkLocation = u.networkLocation;
    }
  }

  static class UI {
    final Activity a;
    Object mediaCallback;
    final SharedPreferences prefs;
    final Handler handler = new Handler(Looper.getMainLooper());
    final ArrayDeque<State> history = new ArrayDeque<>();
    FrameLayout root;
    LinearLayout base, body;
    View old;
    String screen = "video", xml = "preferences_ui", audioTab = "Artists";
    Object item;
    File folder;
    final Set<Object> selected = new LinkedHashSet<>();
    final Map<String, Boolean> folderScanOverrides = new HashMap<>();
    Object[] videos = {}, audios = {}, recentVideos = {}, videoHistory = {};
    final Map<String, Object[]> audioCategories = new HashMap<>();
    final Map<Object, Object[]> detailTracks = new IdentityHashMap<>();
    final Set<Object> detailQueries = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    final Set<String> categoryQueries = new HashSet<>();
    int videoLimit = 80, audioLimit = 80, categoryLimit = 40;
    int count = -1;
    String[] lastVideoSnapshot = new String[0], lastAudioSnapshot = new String[0];
    boolean hasLibrarySnapshot = false, resetScrollOnRender = false;
    ScrollView currentScroll;
    String renderedScreen = "";
    Runnable libraryRefresh;
    boolean mediaQueryPending = false, mediaQueryDirty = false, mediaQueryForceRender = false;
    int bg, panel, ink, muted, line;
    final int orange = Color.rgb(244, 107, 50);
    SeekBar seek;
    TextView elapsed, total, playButton, miniToggle;
    String miniMediaKey = "";
    volatile int searchGeneration = 0;
    boolean seeking = false;
    String currentTitle = "";
    NetworkBrowser networkBrowser;
    String networkLocation = "";
    String pendingScreen = "";
    Runnable protectedAction;
    boolean authorized = false;
    final Set<String> integerPreferences = new HashSet<>();

    UI(Activity a) {
      this.a = a;
      prefs = a.getSharedPreferences(a.getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }

    int dp(float x) {
      return (int) (x * a.getResources().getDisplayMetrics().density + .5f);
    }

    LinearLayout column() {
      LinearLayout l = new LinearLayout(a);
      l.setOrientation(LinearLayout.VERTICAL);
      return l;
    }

    LinearLayout row() {
      LinearLayout l = new LinearLayout(a);
      l.setOrientation(LinearLayout.HORIZONTAL);
      l.setGravity(Gravity.CENTER_VERTICAL);
      return l;
    }

    GradientDrawable surface(int color, int radius, boolean border) {
      GradientDrawable d = new GradientDrawable();
      d.setColor(color);
      d.setCornerRadius(dp(radius));
      if (border) d.setStroke(dp(1), line);
      return d;
    }

    TextView text(String s, int size, int color) {
      TextView t = new TextView(a);
      t.setText(s);
      t.setTextSize(size);
      t.setTextColor(color);
      t.setFontFeatureSettings("kern");
      t.setIncludeFontPadding(false);
      return t;
    }

    TextView label(String s) {
      TextView t = text(s.toUpperCase(Locale.ROOT), 10, muted);
      t.setLetterSpacing(.12f);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
      p.topMargin = dp(16);
      p.bottomMargin = dp(8);
      t.setLayoutParams(p);
      return t;
    }

    void gap(LinearLayout l, int n) {
      View v = new View(a);
      l.addView(v, new LinearLayout.LayoutParams(1, dp(n)));
    }

    void install(Bundle b) {
      ViewGroup content = a.findViewById(android.R.id.content);
      if (content.getChildCount() > 0) {
        old = content.getChildAt(0);
        old.setVisibility(View.GONE);
      }
      root = new FrameLayout(a);
      root.setOnApplyWindowInsetsListener(
          (v, insets) -> {
            v.setPadding(
                insets.getSystemWindowInsetLeft(),
                0,
                insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom());
            return insets;
          });
      content.addView(root, new ViewGroup.LayoutParams(-1, -1));
      fitWindow(root);
      if (b != null) {
        screen = b.getString("premiere_screen", "video");
        xml = b.getString("premiere_xml", "preferences_ui");
      }
      if (!afterSetup.isEmpty()) {
        String route = afterSetup;
        afterSetup = "";
        screen = route.equals("pick-file") ? "browse" : route;
        if (route.equals("pick-file"))
          handler.postDelayed(
              () ->
                  a.startActivityForResult(
                      new Intent(Intent.ACTION_OPEN_DOCUMENT)
                          .setType("*/*")
                          .addCategory(Intent.CATEGORY_OPENABLE),
                      772),
              600);
      }
      render();
      refresh();
      tick();
      watchLibrary();
    }

    void watchLibrary() {
      try {
        Class<?> type = clazz("org.videolan.medialibrary.interfaces.Medialibrary$MediaCb");
        if (type == null) return;
        java.lang.ref.WeakReference<UI> weak = new java.lang.ref.WeakReference<>(this);
        mediaCallback = java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
          UI ui = weak.get();
          if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
          if (method.getName().equals("equals")) return proxy == (args == null ? null : args[0]);
          if (method.getName().equals("toString")) return "Premiere library observer";
          if (ui != null && (method.getName().startsWith("onMedia") || method.getName().equals("onMediaConvertedToExternal"))) {
            UI current = weak.get();
            if (current != null) current.scheduleLibraryRefresh(weak);
          }
          return null;
        });
        invoke(library(), "addMediaCb", mediaCallback);
      } catch (Exception ignored) {}
    }

    void scheduleLibraryRefresh(java.lang.ref.WeakReference<UI> weak) {
      handler.post(() -> {
        if (a.isDestroyed() || a.isFinishing()) return;
        if (libraryRefresh != null) handler.removeCallbacks(libraryRefresh);
        libraryRefresh = () -> {
          UI current = weak.get();
          if (current != null && !current.a.isDestroyed() && !current.a.isFinishing()) current.refresh(false);
        };
        handler.postDelayed(libraryRefresh, 350);
      });
    }

    String[] libraryKeys(Object[] items) {
      String[] keys = new String[items.length];
      for (int i = 0; i < items.length; i++) {
        Object media = items[i];
        String uriText = str(invoke(media, "getUri"));
        String fileState = "";
        try {
          Uri uri = Uri.parse(uriText);
          if (uri.getScheme() == null || "file".equals(uri.getScheme())) {
            File file = new File(uri.getPath() == null ? uriText : uri.getPath());
            fileState = ":" + file.length() + ":" + file.lastModified();
          }
        } catch (Exception ignored) {}
        keys[i] = ArtworkMatch.itemIdentity(uriText, str(invoke(media, "getId")),
            str(invoke(media, "getTitle")), str(invoke(media, "getDate")), number(invoke(media, "getLength")))
            + ":" + str(invoke(media, "getFileName"))
            + ":" + str(invoke(media, "getAlbumArtistName"))
            + ":" + str(invoke(media, "getArtistName"))
            + ":" + str(invoke(media, "getAlbumName")) + fileState;
      }
      return keys;
    }

    void unwatchLibrary() {
      if (mediaCallback != null) invoke(library(), "removeMediaCb", mediaCallback);
      mediaCallback = null;
      if (libraryRefresh != null) handler.removeCallbacks(libraryRefresh);
      libraryRefresh = null;
    }

    static void fitWindow(View v) {
      v.getViewTreeObserver()
          .addOnGlobalLayoutListener(
              () -> {
                android.graphics.Rect visible = new android.graphics.Rect();
                v.getWindowVisibleDisplayFrame(visible);
                int[] location = new int[2];
                v.getLocationOnScreen(location);
                int top = Math.max(0, visible.top - location[1]),
                    bottom = Math.max(0, location[1] + v.getHeight() - visible.bottom);
                if (v.getPaddingBottom() != bottom || v.getPaddingTop() != top)
                  v.setPadding(0, top, 0, bottom);
              });
      v.requestApplyInsets();
    }

    void palette() {
      String theme = str(prefs.getAll().get("app_theme"));
      boolean light =
          theme.equals("1")
              || (theme.equals("-1")
                  && (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                      != Configuration.UI_MODE_NIGHT_YES);
      bg = Color.parseColor(light ? "#F4EFE7" : "#171615");
      panel = Color.parseColor(light ? "#FFFDF9" : "#211E1B");
      ink = Color.parseColor(light ? "#29241F" : "#E7DDCC");
      muted = Color.parseColor(light ? "#766D64" : "#AAA196");
      line = Color.parseColor(light ? "#E3DACE" : "#3A342F");
      a.getWindow().setStatusBarColor(bg);
      a.getWindow().setNavigationBarColor(bg);
      a.getWindow()
          .getDecorView()
          .setSystemUiVisibility(light ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0);
    }

    void refresh() { refresh(true); }

    void refresh(boolean forceRender) {
      if (a.isDestroyed()) return;
      if (mediaQueryPending) { mediaQueryDirty=true; mediaQueryForceRender |= forceRender; return; }
      mediaQueryPending = true;
      mediaQueries.execute(
          () -> {
            Object lib = library();
            if (!Boolean.TRUE.equals(invoke(lib, "isInitiated"))) {
              handler.post(() -> {
                mediaQueryPending = false;
                if (!a.isDestroyed() && !a.isFinishing())
                  handler.postDelayed(this::refresh, 250);
              });
              return;
            }
            Object[] newVideos = array(invoke(lib, "getVideos"));
            Object[] newAudios = array(invoke(lib, "getAudio"));
            Object[] newRecent = array(invoke(lib, "getRecentVideos"));
            Object[] newHistory = array(invoke(lib, "history", 0));
            String[] videoKeys = libraryKeys(newVideos), audioKeys = libraryKeys(newAudios);
            int newCount = (int) number(invoke(lib, "getVideoCount"))
                + (int) number(invoke(lib, "getAudioCount"));
            handler.post(
                () -> {
                  mediaQueryPending = false;
                  if (a.isDestroyed() || a.isFinishing()) return;
                  boolean renderRequested = forceRender || mediaQueryForceRender;
                  mediaQueryForceRender = false;
                  boolean changed = !hasLibrarySnapshot
                      || !ArtworkMatch.sameSnapshot(lastVideoSnapshot, videoKeys)
                      || !ArtworkMatch.sameSnapshot(lastAudioSnapshot, audioKeys);
                  videos = newVideos;
                  audios = newAudios;
                  recentVideos = newRecent;
                  videoHistory = newHistory;
                  count = newCount;
                  lastVideoSnapshot = videoKeys;
                  lastAudioSnapshot = audioKeys;
                  hasLibrarySnapshot = true;
                  if (changed) {
                    Artwork.invalidateMemory();
                    Artwork.revisit();
                    audioCategories.clear();
                    detailTracks.clear();
                  }
                  if (changed || renderRequested) {
                    Artwork.sweep(a.getApplicationContext(), newVideos, newAudios);
                    render();
                  }
                  if(mediaQueryDirty){mediaQueryDirty=false;refresh(false);}
                });
          });
    }

    void loadAudioCategory(String category) {
      if (category.equals("Tracks") || audioCategories.containsKey(category)
          || !categoryQueries.add(category)) return;
      mediaQueries.execute(() -> {
        Object lib = library();
        Object[] result = category.equals("Artists")
            ? array(invoke(lib, "getArtists", true, true, false))
            : category.equals("Albums")
                ? array(invoke(lib, "getAlbums", true, false))
                : array(invoke(lib, "getGenres", true, false));
        handler.post(() -> {
          categoryQueries.remove(category);
          if (a.isDestroyed() || a.isFinishing()) return;
          audioCategories.put(category, result);
          if (screen.equals("audio") && audioTab.equals(category)) render();
        });
      });
    }

    void tick() {
      handler.postDelayed(
          new Runnable() {
            public void run() {
              if (a.isDestroyed() || a.isFinishing()) return;
              Object s = service();
              if (screen.equals("audio-player") && s != null) {
                String name = title(invoke(s, "getCurrentMediaWrapper"));
                if (!name.equals(currentTitle)) {
                  render();
                } else if (seek != null && !seeking) {
                  long position = number(invoke(s, "getTime"));
                  seek.setProgress((int) position);
                  update(elapsed, time(position));
                  update(playButton, Boolean.TRUE.equals(invoke(s, "isPlaying")) ? "Ⅱ" : "▶");
                }
              }
              if (!screen.equals("audio-player") && !screen.equals("queue") && !screen.equals("bookmarks")) {
                Object current = invoke(s, "getCurrentMediaWrapper");
                String key = current != null && number(invoke(current, "getType")) != 0
                    ? str(invoke(current, "getUri")) : "";
                if (!key.equals(miniMediaKey)) render();
                else if (miniToggle != null) {
                  boolean playing = Boolean.TRUE.equals(invoke(s, "isPlaying"));
                  update(miniToggle, playing ? "Ⅱ" : "▶");
                  miniToggle.setContentDescription(playing ? "Pause audio" : "Play audio");
                }
              }
              handler.postDelayed(this, 1200);
            }
          },
          1200);
    }

    boolean authorize(Runnable action) {
      if (authorized
          || !Boolean.TRUE.equals(
              invoke(singleton("org.videolan.tools.Settings", "INSTANCE"), "getSafeMode")))
        return true;
      protectedAction = action;
      Object intent =
          invoke(
              singleton("org.videolan.vlc.gui.PinCodeActivity", "Companion"),
              "getIntent",
              a,
              singleton("org.videolan.vlc.gui.PinCodeReason", "CHECK"));
      if (intent instanceof Intent) a.startActivityForResult((Intent) intent, 774);
      return false;
    }

    void go(String next) {
      if (next.equals("playlist-edit") && !authorize(() -> go(next))) return;
      if (next.equals("settings") && prefs.getBoolean("restrict_settings", false)) {
        pendingScreen = next;
        Object reason = singleton("org.videolan.vlc.gui.PinCodeReason", "CHECK");
        Object intent =
            invoke(
                singleton("org.videolan.vlc.gui.PinCodeActivity", "Companion"),
                "getIntent",
                a,
                reason);
        if (intent instanceof Intent) a.startActivityForResult((Intent) intent, 773);
        return;
      }
      history.push(new State(this));
      screen = next;
      resetScrollOnRender = true;
      render();
    }

    void goNetwork(String location) {
      history.push(new State(this));
      networkLocation = location;
      screen = "network-share";
      resetScrollOnRender = true;
      render();
    }

    void goItem(String next, Object selected) {
      history.push(new State(this));
      item = selected;
      screen = next;
      resetScrollOnRender = true;
      render();
    }

    boolean back() {
      if (history.isEmpty()) return false;
      State s = history.pop();
      screen = s.screen;
      xml = s.xml;
      item = s.item;
      folder = s.folder;
      networkLocation = s.networkLocation;
      resetScrollOnRender = true;
      render();
      return true;
    }

    String navActive() {
      if (screen.startsWith("audio")
          || screen.equals("album")
          || screen.equals("artist")
          || screen.equals("genre")) return "Audio";
      if (screen.equals("browse")
          || screen.equals("folder")
          || screen.startsWith("network")
          || screen.equals("stream")) return "Browse";
      if (screen.startsWith("playlist")) return "Playlists";
      if (screen.equals("video")
          || screen.equals("videos")
          || screen.equals("info")
          || screen.equals("groups")
          || screen.equals("group")
          || screen.equals("search")
          || screen.equals("series") || screen.equals("series-season")) return "Video";
      return "More";
    }

    void render() {
      palette();
      seek = null;
      final int previousScroll = currentScroll == null ? 0 : currentScroll.getScrollY();
      final boolean preserveScroll = !resetScrollOnRender && screen.equals(renderedScreen);
      renderedScreen = screen;
      resetScrollOnRender = false;
      root.removeAllViews();
      root.setBackgroundColor(bg);
      base = column();
      root.addView(base, new FrameLayout.LayoutParams(-1, -1));
      ScrollView sc = new ScrollView(a);
      currentScroll = sc;
      sc.setFillViewport(true);
      sc.setClipToPadding(false);
      body = column();
      body.setPadding(dp(20), dp(10), dp(20), dp(18));
      sc.addView(body, new ScrollView.LayoutParams(-1, -2));
      base.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
      if (preserveScroll && previousScroll > 0) sc.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
        public void onGlobalLayout() {
          sc.getViewTreeObserver().removeOnGlobalLayoutListener(this);
          if (currentScroll == sc) sc.scrollTo(0, previousScroll);
        }
      });
      switch (screen) {
        case "selection":
          selection();
          break;
        case "video":
          libraryPage();
          break;
        case "series":
          seriesList();
          break;
        case "series-season":
          seriesSeason();
          break;
        case "videos":
          Object[] listed = filteredVideos();
          heading("All videos", listed.length + (listed.length == videos.length
              ? " videos · On this device" : " of " + videos.length + " videos · Filtered"));
          if (prefs.getBoolean("premiere_video_list", false)) {
            for (int i=0; i<Math.min(videoLimit,listed.length); i++) {
              Object v=listed[i]; mediaRow(body,v,()->play(v),true);
            }
            if(listed.length>videoLimit) body.addView(button("Show more videos",()->{videoLimit+=80;render();},false));
            break;
          }
          LinearLayout posterGrid = new LinearLayout(a);
          posterGrid.setOrientation(LinearLayout.VERTICAL);
          int posterHeight = Math.max(dp(128), (int)(((a.getResources().getDisplayMetrics().widthPixels - dp(40)) / 2f - dp(10)) * 1.5f));
          for (int i = 0; i < Math.min(videoLimit, listed.length); i += 2) {
            LinearLayout posterRow = row();
            posterCard(posterRow, listed[i], posterHeight);
            if (i + 1 < Math.min(videoLimit, listed.length)) posterCard(posterRow, listed[i + 1], posterHeight);
            else posterRow.addView(new View(a),new LinearLayout.LayoutParams(0,1,1));
            posterGrid.addView(posterRow);
          }
          body.addView(posterGrid);
          if (listed.length > videoLimit)
            body.addView(button("Show more videos", () -> { videoLimit += 80; render(); }, false));
          break;
        case "audio":
          music();
          break;
        case "artist":
        case "album":
        case "genre":
          musicDetail();
          break;
        case "browse":
          browse();
          break;
        case "folder":
          folderPage();
          break;
        case "network":
        case "network-share":
          network();
          break;
        case "stream":
          stream();
          break;
        case "playlists":
          playlists();
          break;
        case "playlist":
          playlist();
          break;
        case "playlist-edit":
          playlistEdit();
          break;
        case "more":
          more();
          break;
        case "settings":
          settings();
          break;
        case "artwork-settings":
          artworkSettings();
          break;
        case "preferences":
          preferences();
          break;
        case "settings-search":
          settingsSearch();
          break;
        case "info":
          information();
          break;
        case "search":
          search();
          break;
        case "history":
          historyPage();
          break;
        case "audio-player":
          audioPlayer();
          break;
        case "queue":
          queue();
          break;
        case "bookmarks":
          bookmarks();
          break;
        case "widgets":
          widgets();
          break;
        case "about":
          about();
          break;
        case "groups":
          groups();
          break;
        case "group":
          groupPage();
          break;
        case "equalizer":
          equalizer();
          break;
        case "permissions":
          permissions();
          break;
        default:
          heading("VLC", "");
      }
      if (!screen.equals("audio-player") && !screen.equals("queue") && !screen.equals("bookmarks")) {
        miniDock();
        nav();
      }
    }

    void masthead() {
      LinearLayout h = row();
      boolean top = Arrays.asList("video", "audio", "browse", "playlists", "more").contains(screen);
      h.addView(
          icon(
              top ? "cone" : "back",
              top ? "VLC" : "Back",
              () -> {
                if (!top) back();
              }),
          new LinearLayout.LayoutParams(dp(48), dp(48)));
      TextView brand = text(top ? "VLC  PREMIERE" : "VLC", 12, ink);
      brand.setLetterSpacing(.1f);
      h.addView(brand, new LinearLayout.LayoutParams(0, dp(32), 1));
      brand.setGravity(Gravity.CENTER_VERTICAL);
      h.addView(
          icon(
              "search",
              screen.equals("settings") || screen.equals("preferences")
                  ? "Search settings"
                  : "Search media",
              () ->
                  go(
                      screen.equals("settings") || screen.equals("preferences")
                          ? "settings-search"
                          : "search")),
          new LinearLayout.LayoutParams(dp(48), dp(48)));
      h.addView(
          icon(
              screen.startsWith("playlist") ? "plus" : "more",
              screen.startsWith("playlist") ? "Create playlist" : "Display options",
              () -> {
                if (screen.startsWith("playlist")) createPlaylist(null);
                else display();
              }),
          new LinearLayout.LayoutParams(dp(48), dp(48)));
      body.addView(h);
      gap(body, 10);
    }

    void heading(String name, String sub) {
      masthead();
      TextView t = text(name, 27, ink);
      t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
      body.addView(t);
      if (!sub.isEmpty()) {
        gap(body, 7);
        body.addView(text(sub, 12, muted));
      }
      gap(body, 15);
    }

    View icon(String type, String desc, Runnable action) {
      Icon v = new Icon(a, type, desc.equals("VLC") ? orange : ink);
      v.setContentDescription(desc);
      v.setMinimumHeight(dp(48));
      v.setMinimumWidth(dp(48));
      v.setFocusable(true);
      v.setOnClickListener(x -> action.run());
      return v;
    }

    TextView button(String label, Runnable action, boolean primary) {
      TextView b = text(label, 14, primary ? Color.rgb(23, 22, 21) : ink);
      b.setGravity(Gravity.CENTER);
      b.setMinHeight(dp(48));
      b.setPadding(dp(12), dp(12), dp(12), dp(12));
      b.setBackground(surface(primary ? orange : panel, 6, !primary));
      b.setOnClickListener(v -> action.run());
      b.setFocusable(true);
      b.setAccessibilityDelegate(
          new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                View host, android.view.accessibility.AccessibilityNodeInfo info) {
              super.onInitializeAccessibilityNodeInfo(host, info);
              info.setClassName("android.widget.Button");
            }
          });
      b.setContentDescription(label);
      return b;
    }

    void nav() {
      LinearLayout n = row();
      n.setBackgroundColor(panel);
      n.setPadding(dp(2), dp(8), dp(2), dp(6));
      String[] names = {"Video", "Audio", "Browse", "Playlists", "More"},
          icons = {"video", "audio", "folder", "list", "more"},
          dest = {"video", "audio", "browse", "playlists", "more"};
      for (int i = 0; i < 5; i++) {
        final String d = dest[i];
        boolean active = names[i].equals(navActive());
        LinearLayout cell = column();
        cell.setGravity(Gravity.CENTER);
        Icon ic = new Icon(a, icons[i], active ? orange : muted);
        cell.addView(ic, new LinearLayout.LayoutParams(dp(22), dp(24)));
        gap(cell, 5);
        TextView label = text(names[i], 10, active ? orange : muted);
        label.setGravity(Gravity.CENTER);
        cell.addView(label);
        cell.setContentDescription("Navigate to " + names[i]);
        cell.setFocusable(true);
        cell.setOnClickListener(
            v -> {
              if (this instanceof PreferencesUI) {
                navigate(a, d);
                return;
              }
              history.clear();
              screen = d;
              resetScrollOnRender = true;
              render();
              refresh();
            });
        n.addView(cell, new LinearLayout.LayoutParams(0, dp(52), 1));
      }
      View border = new View(a);
      border.setBackgroundColor(line);
      base.addView(border, new LinearLayout.LayoutParams(-1, dp(1)));
      base.addView(n, new LinearLayout.LayoutParams(-1, dp(68)));
    }

    void libraryPage() {
      heading("Your library", count < 0 ? "Loading videos…"
          : videos.length + " videos · On this device");
      TextView search = button("⌕  Find something to watch", () -> go("search"), false);
      search.setTextColor(muted);
      search.setTextSize(12);
      search.setGravity(Gravity.CENTER_VERTICAL);
      search.setMinHeight(dp(40));
      search.setPadding(dp(12), dp(8), dp(12), dp(8));
      body.addView(search);
      if (count < 0) {
        body.addView(text("Loading your library…", 14, muted));
        return;
      }
      if (videos.length == 0) {
        gap(body, 62);
        body.addView(text("Your next film starts here.", 24, ink));
        gap(body, 14);
        body.addView(text("Choose a folder to find the videos already on your device.", 14, muted));
        gap(body, 24);
        body.addView(
            button(
                "Choose folders",
                () -> {
                  folder = Environment.getExternalStorageDirectory();
                  go("folder");
                },
                true));
        return;
      }
      Object featured = null;
      for (Object v : videoHistory)
        if (number(invoke(v, "getType")) == 0
            && !isWatched(v)
            && number(invoke(v, "getTime")) > 0
            && !isWatched(v)
            && number(invoke(v, "getTime")) < number(invoke(v, "getLength")) - 5000) {
          featured = v;
          break;
        }
      if (featured == null)
        for (Object v : videos)
          if (number(invoke(v, "getTime")) > 0
              && number(invoke(v, "getTime")) < number(invoke(v, "getLength")) - 5000) {
            featured = v;
            break;
          }
      if (featured == null) {
        Object[] recent = recentVideos;
        featured = recent.length > 0 ? recent[0] : videos[0];
      }
      long position = number(invoke(featured, "getTime"));
      body.addView(label(position > 0 ? "Continue watching" : "From your library"));
      hero(featured);
      continueWatchingShelf(featured);
      gap(body, 18);
      LinearLayout shelfHeading = row();
      shelfHeading.addView(
          text("Recently added", 14, ink), new LinearLayout.LayoutParams(0, -2, 1));
      TextView all = text("See all", 11, orange);
      all.setMinHeight(dp(28));
      all.setGravity(Gravity.CENTER);
      all.setOnClickListener(v -> go("videos"));
      shelfHeading.addView(all);
      body.addView(shelfHeading);
      gap(body, 10);
      HorizontalScrollView scroll = new HorizontalScrollView(a);
      scroll.setHorizontalScrollBarEnabled(false);
      LinearLayout shelf = row();
      Object[] recent = recentVideos;
      if (recent.length == 0) recent = videos;
      for (int i = 0; i < Math.min(12, recent.length); i++) {
        Object v = recent[i];
        LinearLayout card = column();
        card.addView(art(v, 159), new LinearLayout.LayoutParams(-1, dp(159)));
        gap(card, 8);
        TextView name = text(ArtworkMatch.videoTitle(title(v)), 11, ink);
        name.setMaxLines(2);
        card.addView(name);
        gap(card, 5);
        card.addView(text(time(number(invoke(v, "getLength"))), 10, muted));
        card.setOnClickListener(x -> play(v));
        card.setOnLongClickListener(
            x -> {
              actions(v);
              return true;
            });
        card.setContentDescription("Play " + title(v));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(106), -2);
        p.rightMargin = dp(10);
        shelf.addView(card, p);
      }
      scroll.addView(shelf);
      body.addView(scroll);
      if (!seriesGroups().isEmpty()) {
        gap(body, 20);
        body.addView(button("TV series · Browse seasons", () -> go("series"), false));
      }
      gap(body, 28);
      TextView note = text("Your files. Your cinema.", 11, muted);
      note.setGravity(Gravity.CENTER);
      body.addView(note);
    }

    void continueWatchingShelf(Object featured) {
      String featuredUri = str(invoke(featured, "getUri"));
      LinkedHashMap<String, Object> unfinished = new LinkedHashMap<>();
      for (Object[] library : new Object[][] {videoHistory, videos}) {
        for (Object media : library) {
          if (number(invoke(media, "getType")) != 0 || isWatched(media)) continue;
          long position = number(invoke(media, "getTime"));
          long length = number(invoke(media, "getLength"));
          String uri = str(invoke(media, "getUri"));
          if (uri.equals(featuredUri) || uri.isEmpty() || position < 5000
              || length <= 0 || position >= length - 5000) continue;
          unfinished.putIfAbsent(uri, media);
          if (unfinished.size() == 8) break;
        }
        if (unfinished.size() == 8) break;
      }
      if (unfinished.isEmpty()) return;
      gap(body, 20);
      body.addView(label("Keep watching"));
      HorizontalScrollView horizontal = new HorizontalScrollView(a);
      horizontal.setHorizontalScrollBarEnabled(false);
      LinearLayout row = row();
      for (Object media : unfinished.values()) {
        LinearLayout card = column();
        card.addView(art(media, 114), new LinearLayout.LayoutParams(-1, dp(114)));
        TextView name = text(title(media), 12, ink);
        name.setMaxLines(2);
        card.addView(name);
        gap(card, 4);
        long remaining = Math.max(0, number(invoke(media, "getLength"))
            - number(invoke(media, "getTime")));
        card.addView(text(time(remaining) + " left", 11, muted));
        card.setContentDescription("Resume " + title(media));
        card.setFocusable(true);
        card.setOnClickListener(v -> play(media));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(126), -2);
        params.rightMargin = dp(10);
        row.addView(card, params);
      }
      horizontal.addView(row);
      body.addView(horizontal);
    }

    void posterCard(LinearLayout parent, Object media, int posterHeight) {
      LinearLayout card = column();
      card.setPadding(dp(5), dp(8), dp(5), dp(8));
      card.addView(art(media, posterHeight), new LinearLayout.LayoutParams(-1, posterHeight));
      gap(card, 8);
      TextView name = text(ArtworkMatch.videoTitle(title(media)), 13, ink); name.setMaxLines(2); card.addView(name);
      gap(card, 4);
      String date = str(invoke(media, "getDate"));
      if(date.isEmpty())date=ArtworkMatch.year(str(invoke(media,"getFileName")));
      java.util.regex.Matcher y = java.util.regex.Pattern.compile("(?:19|20)\\d{2}").matcher(date);
      card.addView(text(y.find() ? y.group() : format(media), 10, muted));
      LinearLayout posterActions = row();
      TextView play = text("▶  Play", 11, orange);
      play.setContentDescription("Play " + title(media));
      play.setOnClickListener(v -> play(media));
      posterActions.addView(play, new LinearLayout.LayoutParams(0, dp(38), 1));
      posterActions.addView(icon("more", "Actions for " + title(media), () -> actions(media)), new LinearLayout.LayoutParams(dp(48), dp(48)));
      card.addView(posterActions);
      card.setOnClickListener(v -> play(media));
      card.setOnLongClickListener(v -> { actions(media); return true; });
      card.setContentDescription("Play " + title(media));
      parent.addView(card, new LinearLayout.LayoutParams(0, -2, 1));
    }

    void hero(Object media) {
      FrameLayout f = new FrameLayout(a);
      f.setBackground(surface(panel, 8, false));
      f.setClipToOutline(true);
      f.addView(art(media, 200), new FrameLayout.LayoutParams(-1, -1));
      GradientDrawable shade =
          new GradientDrawable(
              GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x00171615, 0xE6171615});
      View sh = new View(a);
      sh.setBackground(shade);
      f.addView(sh, new FrameLayout.LayoutParams(-1, -1));
      LinearLayout copy = column();
      copy.setPadding(dp(16), 0, dp(70), dp(20));
      long length = number(invoke(media, "getLength")), position = number(invoke(media, "getTime"));
      copy.addView(
          text(
              position > 0 ? time(length - position) + " LEFT" : time(length) + " TOTAL",
              10,
              Color.rgb(231, 221, 204)));
      gap(copy, 6);
      TextView title = text(title(media), 22, Color.rgb(231, 221, 204));
      title.setMaxLines(2);
      copy.addView(title);
      gap(copy, 6);
      copy.addView(text(format(media), 11, Color.rgb(231, 221, 204)));
      FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
      f.addView(copy, cp);
      TextView p = button("▶", () -> play(media), true);
      p.setTextSize(20);
      p.setMinHeight(0);
      p.setPadding(0, 0, 0, 0);
      p.setBackground(surface(orange, 40, false));
      FrameLayout.LayoutParams pp =
          new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.RIGHT | Gravity.BOTTOM);
      pp.rightMargin = dp(16);
      pp.bottomMargin = dp(22);
      f.addView(p, pp);
      if (position > 0 && length > 0) {
        View progress = new View(a);
        progress.setBackgroundColor(orange);
        f.addView(
            progress,
            new FrameLayout.LayoutParams(
                Math.max(
                    dp(2),
                    (int)
                        (dp(
                                a.getResources().getDisplayMetrics().widthPixels
                                        / a.getResources().getDisplayMetrics().density
                                    - 40)
                            * (double) position
                            / length)),
                dp(3),
                Gravity.BOTTOM));
      }
      f.setOnClickListener(v -> play(media));
      f.setOnLongClickListener(
          v -> {
            actions(media);
            return true;
          });
      body.addView(f, new LinearLayout.LayoutParams(-1, dp(200)));
    }

    String format(Object media) {
      String name = str(invoke(media, "getFileName"));
      int dot = name.lastIndexOf('.');
      String type = dot >= 0 ? name.substring(dot + 1).toUpperCase(Locale.ROOT) : "VIDEO";
      long w = number(invoke(media, "getWidth")), h = number(invoke(media, "getHeight"));
      return type + (w > 0 ? " · " + w + " × " + h : "");
    }

    ImageView art(Object media, int h) {
      ImageView iv = new ImageView(a);
      iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
      iv.setBackground(surface(panel, 6, false));
      iv.setClipToOutline(true);
      iv.setContentDescription("Artwork for " + title(media));
      String fingerprint = ArtworkMatch.itemIdentity(str(invoke(media, "getUri")), str(invoke(media, "getId")),
          str(invoke(media, "getTitle")), str(invoke(media, "getDate")), number(invoke(media, "getLength")));
      Artwork.resolve(a.getApplicationContext(), media, fingerprint, iv);
      return iv;
    }

    void mediaRow(LinearLayout parent, Object media, Runnable click, boolean menu) {
      LinearLayout r = row();
      r.setPadding(0, dp(10), 0, dp(10));
      r.addView(art(media, 58), new LinearLayout.LayoutParams(dp(64), dp(58)));
      LinearLayout copy = column();
      copy.setPadding(dp(14), 0, dp(8), 0);
      TextView name = text(title(media), 14, ink);
      name.setMaxLines(2);
      copy.addView(name);
      gap(copy, 6);
      String artist = str(invoke(media, "getArtistName"));
      copy.addView(
          text(
              artist.isEmpty()
                  ? format(media) + " · " + time(number(invoke(media, "getLength")))
                  : artist + " · " + time(number(invoke(media, "getLength"))),
              11,
              muted));
      r.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
      if (menu)
        r.addView(
            icon("more", "Actions for " + title(media), () -> actions(media)),
            new LinearLayout.LayoutParams(dp(38), dp(48)));
      r.setOnClickListener(v -> click.run());
      r.setOnLongClickListener(
          v -> {
            actions(media);
            return true;
          });
      r.setFocusable(true);
      r.setContentDescription("Play " + title(media));
      parent.addView(r);
    }

    void play(Object media) {
      // Use VLC's non-blocking playback path. openMedia() routes through a
      // suspend dialog callback and can leave Premiere's row tap without a
      // service load when there is no pending playback to resume.
      invoke(singleton("org.videolan.vlc.media.MediaUtils", "INSTANCE"), "openMediaNoUi", a, media);
      if (number(invoke(media, "getType")) != 0) {
        history.push(new State(this));
        screen = "audio-player";
        handler.postDelayed(this::render, 450);
      }
    }

    void playList(Object[] media, boolean shuffle) {
      if (media.length == 0) return;
      invoke(
          singleton("org.videolan.vlc.media.MediaUtils", "INSTANCE"),
          "openList",
          a,
          Arrays.asList(media),
          0,
          shuffle);
      if (number(invoke(media[0], "getType")) != 0) {
        go("audio-player");
        handler.postDelayed(this::render, 450);
      }
    }

    void music() {
      Object[] content = audioTab.equals("Tracks") ? audios : audioCategories.get(audioTab);
      if (content == null) {
        loadAudioCategory(audioTab);
        content = new Object[0];
      }
      boolean loading = !audioTab.equals("Tracks") && !audioCategories.containsKey(audioTab);
      heading(
          "Your music",
          audioTab.equals("Tracks") ? audios.length + " tracks · On this device"
              : audios.length + " tracks · " + content.length + " " + audioTab.toLowerCase(Locale.ROOT));
      LinearLayout tabs = row();
      for (String name : new String[] {"Artists", "Albums", "Tracks", "Genres"}) {
        TextView t = text(name, 12, name.equals(audioTab) ? orange : muted);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(12), 0, dp(12));
        t.setOnClickListener(
            v -> {
              audioTab = name;
              categoryLimit = 40;
              resetScrollOnRender = true;
              render();
            });
        tabs.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
      }
      body.addView(tabs);
      gap(body, 20);
      if (audioTab.equals("Tracks")) {
        paired(
            body,
            "▶ Play all",
            () -> playList(audios, false),
            "Shuffle",
            () -> playList(audios, true));
        gap(body, 12);
        for (int i = 0; i < Math.min(audioLimit, audios.length); i++) {
          Object track = audios[i];
          mediaRow(body, track, () -> play(track), true);
        }
        if (audios.length > audioLimit)
          body.addView(button("Show more tracks", () -> { audioLimit += 80; render(); }, false));
      } else {
        for (int i = 0; i < Math.min(categoryLimit, content.length); i += 2) {
          LinearLayout r = row();
          r.setGravity(Gravity.TOP);
          for (int j = i; j < Math.min(i + 2, Math.min(categoryLimit, content.length)); j++) {
            Object entry = content[j];
            LinearLayout cell = column();
            int coverSide=(int)((a.getResources().getDisplayMetrics().widthPixels-dp(60))/2f);
            cell.addView(art(entry, 154), new LinearLayout.LayoutParams(-1, coverSide));
            gap(cell, 9);
            cell.addView(text(title(entry), 14, ink));
            gap(cell, 6);
            cell.addView(text(number(invoke(entry, "getTracksCount")) + " tracks", 11, muted));
            String next =
                audioTab.equals("Artists")
                    ? "artist"
                    : audioTab.equals("Albums") ? "album" : "genre";
            cell.setOnClickListener(v -> goItem(next, entry));
            cell.setContentDescription(title(entry));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
            p.rightMargin = dp(10);
            r.addView(cell, p);
          }
          if (r.getChildCount() == 1)
            r.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
          body.addView(r);
          gap(body, 22);
        }
        if (content.length > categoryLimit)
          body.addView(button("Show more " + audioTab.toLowerCase(Locale.ROOT),
              () -> { categoryLimit += 40; render(); }, false));
      }
      if (loading) body.addView(text("Loading music…", 14, muted));
      else if (content.length == 0) {
        empty("Your music starts here.", "Choose a music folder or open an audio file.");
        if (count >= 0 && audios.length == 0)
          body.addView(
              button(
                  "Choose music folder",
                  () -> {
                    File musicFolder=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                    folder=musicFolder.isDirectory()?musicFolder:Environment.getExternalStorageDirectory();
                    go("folder");
                  },
                  true));
        else if(audios.length>0 && !audioTab.equals("Tracks"))
          body.addView(button("Show " + audios.length + " tracks",()->{audioTab="Tracks";resetScrollOnRender=true;render();},true));
      }
    }

    void musicDetail() {
      Object selectedItem = item;
      Object[] ts = detailTracks.get(selectedItem);
      if (ts == null) {
        heading(title(selectedItem), "Loading tracks…");
        if (detailQueries.add(selectedItem)) {
          mediaQueries.execute(() -> {
            Object[] found = tracks(selectedItem);
            handler.post(() -> {
              detailQueries.remove(selectedItem);
              if (a.isDestroyed() || a.isFinishing()) return;
              detailTracks.put(selectedItem, found);
              if (item == selectedItem && (screen.equals("artist") || screen.equals("album")
                  || screen.equals("genre"))) render();
            });
          });
        }
        return;
      }
      heading(title(item), ts.length + " tracks · On this device");
      body.addView(art(item, 250), new LinearLayout.LayoutParams(-1, dp(250)));
      gap(body, 20);
      paired(body, "▶ Play all", () -> playList(ts, false), "Shuffle", () -> playList(ts, true));
      gap(body, 16);
      for (Object v : ts) mediaRow(body, v, () -> play(v), true);
    }

    void miniDock() {
      Object s = service(), current = invoke(s, "getCurrentMediaWrapper");
      miniMediaKey = current == null || number(invoke(current, "getType")) == 0
          ? "" : str(invoke(current, "getUri"));
      miniToggle = null;
      if (miniMediaKey.isEmpty()) return;
      LinearLayout dock = row();
      dock.setGravity(Gravity.CENTER_VERTICAL);
      dock.setPadding(dp(12), dp(7), dp(12), dp(7));
      dock.setBackground(surface(panel, 6, true));
      dock.addView(art(current, 42), new LinearLayout.LayoutParams(dp(42), dp(42)));
      LinearLayout copy = column();
      copy.setPadding(dp(12), 0, dp(4), 0);
      TextView name = text(title(current), 13, ink);
      name.setSingleLine(true);
      name.setEllipsize(android.text.TextUtils.TruncateAt.END);
      copy.addView(name);
      TextView artist = text(str(invoke(current, "getArtistName")), 11, muted);
      artist.setSingleLine(true);
      artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
      copy.addView(artist);
      dock.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
      miniToggle = button(Boolean.TRUE.equals(invoke(s, "isPlaying")) ? "Ⅱ" : "▶", () -> {
        Object playback = service();
        boolean playing = Boolean.TRUE.equals(invoke(playback, "isPlaying"));
        invoke(playback, playing ? "pause" : "play");
        if (miniToggle != null) {
          miniToggle.setText(playing ? "▶" : "Ⅱ");
          miniToggle.setContentDescription(playing ? "Play audio" : "Pause audio");
        }
      }, false);
      miniToggle.setContentDescription(Boolean.TRUE.equals(invoke(s, "isPlaying")) ? "Pause audio" : "Play audio");
      dock.addView(miniToggle, new LinearLayout.LayoutParams(dp(48), dp(48)));
      dock.setOnClickListener(v -> go("audio-player"));
      dock.setContentDescription("Now playing " + title(current) + ". Open player");
      dock.setFocusable(true);
      base.addView(dock, new LinearLayout.LayoutParams(-1, -2));
    }

    LinearLayout group(String name) {
      body.addView(label(name));
      LinearLayout g = column();
      g.setPadding(dp(12), 0, dp(12), 0);
      g.setBackground(surface(panel, 8, true));
      body.addView(g);
      return g;
    }

    void destination(LinearLayout parent, String name, String sub, String icon, Runnable click) {
      LinearLayout r = row();
      r.setPadding(0, dp(14), 0, dp(14));
      if (icon != null) {
        Icon i = new Icon(a, icon, Color.rgb(184, 139, 95));
        i.setBackground(surface(panel, 6, false));
        r.addView(i, new LinearLayout.LayoutParams(dp(34), dp(40)));
      }
      LinearLayout copy = column();
      copy.setPadding(icon == null ? 0 : dp(12), 0, dp(12), 0);
      copy.addView(text(name, 14, ink));
      if (!sub.isEmpty()) {
        gap(copy, 6);
        TextView s = text(sub, 11, muted);
        s.setMaxLines(2);
        copy.addView(s);
      }
      r.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
      r.addView(new Icon(a, "next", muted), new LinearLayout.LayoutParams(dp(18), dp(22)));
      r.setOnClickListener(v -> click.run());
      r.setContentDescription(name);
      r.setFocusable(true);
      if (parent.getChildCount() > 0) {
        View b = new View(a);
        b.setBackgroundColor(line);
        parent.addView(b, new LinearLayout.LayoutParams(-1, dp(1)));
      }
      parent.addView(r);
    }

    void browse() {
      heading("Browse", "A place for every file");
      File storage = Environment.getExternalStorageDirectory();
      StatFs stat = new StatFs(storage.getPath());
      LinearLayout device = group("On this device");
      destination(
          device,
          "Internal storage",
          android.text.format.Formatter.formatShortFileSize(a, stat.getAvailableBytes())
              + " free of "
              + android.text.format.Formatter.formatShortFileSize(a, stat.getTotalBytes()),
          "folder",
          () -> openFolder(storage));
      for (File external : a.getExternalFilesDirs(null))
        if (external != null && !external.getAbsolutePath().startsWith(storage.getAbsolutePath()))
          destination(device, "SD card", "External storage", "folder", () -> openFolder(external));
      LinearLayout network = group("Network");
      destination(
          network, "Local network", "Discover servers & shares", "network", () -> go("network"));
      destination(network, "Favorites", "Saved locations", "bookmark", () -> favorites());
      LinearLayout quick = group("Quick access");
      destination(
          quick,
          "Movies",
          videos.length + " videos",
          "video",
          () ->
              openFolder(
                  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)));
      destination(
          quick,
          "Music",
          audios.length + " tracks",
          "audio",
          () ->
              openFolder(
                  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)));
      destination(
          quick,
          "Downloads",
          "Files on this device",
          "folder",
          () ->
              openFolder(
                  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)));
    }

    void openFolder(File f) {
      history.push(new State(this));
      folder = f;
      screen = "folder";
      resetScrollOnRender = true;
      render();
    }

    String normalizedFolderPath(String path) {
      Uri uri = Uri.parse(path);
      String value = uri.getScheme() == null ? path : uri.getPath();
      return value == null ? path : new File(value).getAbsolutePath();
    }

    boolean[] scanState(File directory) {
      String path = directory.getAbsolutePath();
      boolean included = false;
      boolean partial = false;
      boolean inherited = false;
      boolean exact = false;
      for (Object root : array(invoke(library(), "getFoldersList"))) {
        if (root == null) continue;
        String rootPath = normalizedFolderPath(String.valueOf(root));
        if (path.equals(rootPath)) {
          exact = true;
          included = true;
        } else if (path.startsWith(rootPath + File.separator)) {
          included = true;
          inherited = true;
        } else if (rootPath.startsWith(path + File.separator)) {
          partial = true;
        }
      }
      Boolean override = folderScanOverrides.get(path);
      if (override != null) {
        if (override == included) folderScanOverrides.remove(path);
        else included = override;
      }
      return new boolean[] {included, partial, inherited, exact};
    }

    void folderEntry(LinearLayout parent, File directory) {
      boolean[] state = scanState(directory);
      String status =
          state[0]
              ? state[2] ? "Included by a parent folder" : "In your library"
              : state[1] ? "Some subfolders included" : "Not in your library";
      LinearLayout entry = row();
      entry.setPadding(0, dp(10), 0, dp(10));
      Icon folderIcon = new Icon(a, "folder", orange);
      folderIcon.setBackground(surface(panel, 6, false));
      entry.addView(folderIcon, new LinearLayout.LayoutParams(dp(34), dp(40)));
      LinearLayout copy = column();
      copy.setPadding(dp(12), 0, dp(6), 0);
      copy.addView(text(directory.getName(), 14, ink));
      gap(copy, 5);
      copy.addView(text(status, 11, state[0] ? orange : muted));
      entry.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
      CheckBox include = new CheckBox(a);
      include.setButtonTintList(ColorStateList.valueOf(orange));
      include.setContentDescription(
          (state[0] ? "Included in scan: " : "Include in scan: ") + directory.getName());
      include.setChecked(state[0]);
      include.setEnabled(!state[2]);
      include.setOnCheckedChangeListener(
          (button, checked) -> {
            String path = directory.getAbsolutePath();
            String mrl = Uri.fromFile(directory).toString();
            folderScanOverrides.put(path, checked);
            Object utils = singleton("org.videolan.vlc.gui.helpers.MedialibraryUtils", "INSTANCE");
            if (checked) {
              invoke(utils, "addDir", mrl, a.getApplicationContext());
              toast("Added “" + directory.getName() + "” to the scan");
            } else {
              invoke(utils, "removeDir", mrl);
              toast("Removed “" + directory.getName() + "” from the scan");
            }
            handler.postDelayed(
                () -> {
                  if (!a.isDestroyed() && screen.equals("folder")) render();
                },
                900);
          });
      entry.addView(include, new LinearLayout.LayoutParams(dp(48), dp(48)));
      entry.setOnClickListener(v -> openFolder(directory));
      entry.setContentDescription(directory.getName() + ", " + status + ". Open folder.");
      entry.setFocusable(true);
      if (parent.getChildCount() > 0) {
        View divider = new View(a);
        divider.setBackgroundColor(line);
        parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
      }
      parent.addView(entry);
    }

    void folderPage() {
      if (folder == null) folder = Environment.getExternalStorageDirectory();
      String folderName = folder.getName();
      heading(
          folderName.isEmpty() || folderName.equals("0") ? "Internal storage" : folderName,
          folder.getAbsolutePath());
      File[] fs = folder.listFiles();
      if (fs == null) {
        empty(
            "Access to this folder is restricted.", "Choose a folder with Android’s file picker.");
        body.addView(
            button(
                "Choose a file",
                () ->
                    a.startActivityForResult(
                        new Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .setType("*/*")
                            .addCategory(Intent.CATEGORY_OPENABLE),
                        772),
                true));
        return;
      }
      Arrays.sort(
          fs,
          (x, y) ->
              x.isDirectory() == y.isDirectory()
                  ? x.getName().compareToIgnoreCase(y.getName())
                  : (x.isDirectory() ? -1 : 1));
      LinearLayout g = group("Files");
      for (File f : fs) {
        if (f.getName().startsWith(".") && !prefs.getBoolean("browser_show_hidden_files", false))
          continue;
        if (f.isDirectory()) {
          folderEntry(g, f);
          continue;
        }
        destination(
            g,
            f.getName(),
            f.isDirectory()
                ? "Folder"
                : android.text.format.Formatter.formatShortFileSize(a, f.length()),
            f.isDirectory() ? "folder" : "video",
            () -> {
              if (f.isDirectory()) openFolder(f);
              else
                invoke(
                    singleton("org.videolan.vlc.media.MediaUtils", "INSTANCE"),
                    "openUri",
                    a,
                    Uri.fromFile(f));
            });
      }
      gap(body, 20);
      boolean[] currentState = scanState(folder);
      String scanLabel =
          currentState[2]
              ? "Included by a parent folder"
              : currentState[3] ? "Remove this folder from the scan" : "Scan this folder";
      TextView scanButton =
          button(
              scanLabel,
              () -> {
                Object utils =
                    singleton("org.videolan.vlc.gui.helpers.MedialibraryUtils", "INSTANCE");
                String path = folder.getAbsolutePath();
                String mrl = Uri.fromFile(folder).toString();
                if (currentState[3]) {
                  folderScanOverrides.put(path, false);
                  invoke(utils, "removeDir", mrl);
                  toast("Removed folder from the scan");
                } else {
                  folderScanOverrides.put(path, true);
                  invoke(utils, "addDir", mrl, a.getApplicationContext());
                  toast("Scanning folder");
                }
                handler.postDelayed(
                    () -> {
                      if (!a.isDestroyed() && screen.equals("folder")) render();
                    },
                    900);
              },
              true);
      if (currentState[2]) {
        scanButton.setEnabled(false);
        scanButton.setAlpha(.55f);
      }
      body.addView(scanButton);
    }

    void network() {
      heading(
          networkLocation.isEmpty()
              ? "Local network"
              : Uri.decode(Uri.parse(networkLocation).getLastPathSegment()),
          networkLocation.isEmpty() ? "Browse devices on your Wi-Fi" : networkLocation);
      if (networkBrowser == null || !networkBrowser.location.equals(networkLocation)) {
        if (networkBrowser != null) networkBrowser.close();
        networkBrowser = new NetworkBrowser(this, networkLocation);
        networkBrowser.start();
      }
      LinearLayout discovered =
          group(networkLocation.isEmpty() ? "On your network" : "Files & folders");
      for (NetworkBrowser.Entry entry : new ArrayList<>(networkBrowser.entries))
        destination(
            discovered,
            entry.title,
            entry.folder ? "Folder" : str(entry.uri),
            entry.folder ? "folder" : "video",
            () -> {
              if (entry.folder) {
                goNetwork(str(entry.uri));
              } else openUri(str(entry.uri));
            });
      if (networkBrowser.entries.isEmpty())
        discovered.addView(
            text(
                networkBrowser.finished
                    ? "No media locations found."
                    : "Looking for media locations…",
                13,
                muted));
      if (networkLocation.isEmpty()) {
        LinearLayout g = group("Saved servers");
        String saved = prefs.getString("premiere_servers", "");
        for (String address : saved.split("\n"))
          if (!address.isEmpty())
            destination(
                g,
                address,
                "Network location",
                "network",
                () -> {
                  goNetwork(address);
                });
        gap(body, 24);
        body.addView(button("+ Add server", this::serverForm, true));
      }
      gap(body, 14);
      body.addView(
          button(
              "Refresh",
              () -> {
                if (networkBrowser != null) networkBrowser.close();
                networkBrowser = null;
                render();
              },
              false));
    }

    void serverForm() {
      LinearLayout form = column();
      EditText host = field(form, "Server address", "smb://");
      form.addView(text("Use an SMB, FTP, SFTP, NFS or HTTP address.", 12, muted));
      Dialog d = sheet("Connect to server", form);
      gap(form, 20);
      form.addView(
          button(
              "Connect",
              () -> {
                String url = host.getText().toString().trim();
                if (url.isEmpty()) return;
                prefs
                    .edit()
                    .putString(
                        "premiere_servers", prefs.getString("premiere_servers", "") + url + "\n")
                    .apply();
                d.dismiss();
                goNetwork(url);
              },
              true));
    }

    void favorites() {
      LinearLayout form = column();
      String saved = prefs.getString("premiere_servers", "");
      if (saved.isEmpty()) form.addView(text("No saved locations yet.", 14, muted));
      for (String url : saved.split("\n"))
        if (!url.isEmpty())
          destination(
              form,
              url,
              "Saved server",
              "network",
              () -> {
                goNetwork(url);
              });
      sheet("Favorites", form);
    }

    void openUri(String url) {
      invoke(
          singleton("org.videolan.vlc.media.MediaUtils", "INSTANCE"), "openUri", a, Uri.parse(url));
    }

    void stream() {
      heading("Open a stream", "Play an address directly");
      EditText input = field(body, "Network address", "");
      gap(body, 16);
      body.addView(
          button(
              "Open stream",
              () -> {
                String url = input.getText().toString().trim();
                if (url.isEmpty()) return;
                prefs
                    .edit()
                    .putString(
                        "premiere_streams", url + "\n" + prefs.getString("premiere_streams", ""))
                    .apply();
                openUri(url);
              },
              true));
      LinearLayout recent = group("Recent streams");
      Set<String> seen =
          new LinkedHashSet<>(Arrays.asList(prefs.getString("premiere_streams", "").split("\n")));
      for (String url : seen)
        if (!url.isEmpty())
          destination(recent, url, "Previously opened", "network", () -> openUri(url));
    }

    Object[] allPlaylists() {
      Object type = singleton("org.videolan.medialibrary.interfaces.media.Playlist$Type", "All");
      return array(invoke(library(), "getPlaylists", type, false));
    }

    void playlists() {
      heading("Your playlists", "Collections worth returning to");
      Object[] lists = allPlaylists();
      for (Object list : lists) {
        Object[] ts = tracks(list);
        LinearLayout r = row();
        r.setPadding(dp(12), dp(16), dp(12), dp(16));
        r.setBackground(surface(panel, 8, true));
        FrameLayout stack = new FrameLayout(a);
        if (ts.length > 0) {
          ImageView back = art(ts[0], 82);
          back.setRotation(-9);
          FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(58), dp(80));
          bp.leftMargin = dp(8);
          bp.topMargin = dp(6);
          stack.addView(back, bp);
          ImageView front = art(ts[Math.min(1, ts.length - 1)], 82);
          front.setRotation(7);
          FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(dp(58), dp(80));
          fp.leftMargin = dp(26);
          fp.topMargin = dp(4);
          stack.addView(front, fp);
        } else
          stack.addView(new Icon(a, "list", muted), new FrameLayout.LayoutParams(dp(62), dp(80)));
        r.addView(stack, new LinearLayout.LayoutParams(dp(102), dp(94)));
        LinearLayout copy = column();
        copy.addView(text(title(list), 17, ink));
        gap(copy, 10);
        copy.addView(text(number(invoke(list, "getTracksCount")) + " items", 12, muted));
        r.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(new Icon(a, "next", muted), new LinearLayout.LayoutParams(dp(18), dp(24)));
        r.setOnClickListener(v -> goItem("playlist", list));
        r.setContentDescription(title(list));
        body.addView(r);
        gap(body, 18);
      }
      if (lists.length == 0)
        empty(
            "Make a little collection.",
            "Films for the weekend. Music for the road. Keep your favorites together.");
      body.addView(button("+ Create playlist", () -> createPlaylist(null), true));
    }

    void createPlaylist(Object media) {
      if (!authorize(() -> createPlaylist(media))) return;
      LinearLayout content = column();
      content.addView(text("Give this collection a name.", 14, muted));
      EditText name = field(content, "Playlist name", "");
      CheckBox include = new CheckBox(a);
      include.setText("Include current queue");
      include.setTextColor(ink);
      if (number(invoke(service(), "getMediaListSize")) > 0) content.addView(include);
      Dialog d = sheet("Create playlist", content);
      gap(content, 20);
      content.addView(
          button(
              "Save playlist",
              () -> {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) {
                  name.setError("Enter a playlist name");
                  return;
                }
                Object list = invoke(library(), "createPlaylist", n, true, false);
                if (list == null) {
                  toast("Playlist could not be created");
                  return;
                }
                if (media != null) invoke(list, "append", number(invoke(media, "getId")));
                if (include.isChecked())
                  for (Object v :
                      array(invoke(invoke(service(), "getPlaylistManager"), "getMediaList")))
                    invoke(list, "append", number(invoke(v, "getId")));
                d.dismiss();
                screen = "playlists";
                resetScrollOnRender = true;
                refresh();
              },
              true));
    }

    void playlist() {
      Object[] ts = tracks(item);
      heading(title(item), ts.length + " items · Your collection");
      if (ts.length > 0) {
        LinearLayout art = row();
        for (int i = 0; i < Math.min(3, ts.length); i++) {
          LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(138), 1);
          p.rightMargin = dp(6);
          art.addView(art(ts[i], 138), p);
        }
        body.addView(art);
        gap(body, 22);
      }
      paired(body, "▶ Play all", () -> playList(ts, false), "Shuffle", () -> playList(ts, true));
      gap(body, 20);
      for (Object v : ts) mediaRow(body, v, () -> play(v), true);
      gap(body, 18);
      body.addView(button("Edit playlist", () -> go("playlist-edit"), false));
    }

    void playlistEdit() {
      Object[] ts = tracks(item);
      heading(title(item), "Reorder this collection · " + ts.length + " items");
      for (int i = 0; i < ts.length; i++) {
        final int pos = i;
        LinearLayout r = row();
        TextView name = text(title(ts[i]), 14, ink);
        r.addView(name, new LinearLayout.LayoutParams(0, dp(64), 1));
        name.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(
            button(
                "↑",
                () -> {
                  if (pos > 0) {
                    invoke(item, "move", pos, pos - 1);
                    render();
                  }
                },
                false),
            new LinearLayout.LayoutParams(dp(44), dp(44)));
        r.addView(
            button(
                "×",
                () -> {
                  invoke(item, "remove", pos);
                  render();
                },
                false),
            new LinearLayout.LayoutParams(dp(44), dp(44)));
        body.addView(r);
      }
      gap(body, 20);
      body.addView(button("+ Add media", () -> addMedia(item), true));
      gap(body, 12);
      body.addView(button("Done", this::back, false));
    }

    void addMedia(Object list) {
      LinearLayout options = column();
      Dialog d = sheet("Add media", options);
      for (Object v : concat(videos, audios))
        destination(
            options,
            title(v),
            time(number(invoke(v, "getLength"))),
            "plus",
            () -> {
              d.dismiss();
              appendToPlaylist(list, v);
            });
    }

    void appendToPlaylist(Object list, Object media) {
      if (!authorize(() -> appendToPlaylist(list, media))) return;
      boolean duplicate = false;
      for (Object v : tracks(list))
        if (number(invoke(v, "getId")) == number(invoke(media, "getId"))) duplicate = true;
      if (duplicate) {
        LinearLayout c = column();
        c.addView(text("“" + title(media) + "” is already in this playlist.", 15, ink));
        Dialog d = sheet("Already in this playlist", c);
        gap(c, 18);
        paired(
            c,
            "Skip duplicate",
            d::dismiss,
            "Add anyway",
            () -> {
              invoke(list, "append", number(invoke(media, "getId")));
              d.dismiss();
              render();
            });
      } else {
        invoke(list, "append", number(invoke(media, "getId")));
        toast("Added to " + title(list));
        render();
      }
    }

    Object[] concat(Object[] a, Object[] b) {
      Object[] r = Arrays.copyOf(a, a.length + b.length);
      System.arraycopy(b, 0, r, a.length, b.length);
      return r;
    }

    void addToPlaylist(Object media) {
      LinearLayout content = column();
      Dialog d = sheet("Add to playlist", content);
      for (Object list : allPlaylists())
        destination(
            content,
            title(list),
            number(invoke(list, "getTracksCount")) + " items",
            "list",
            () -> {
              d.dismiss();
              appendToPlaylist(list, media);
            });
      gap(content, 16);
      content.addView(
          button(
              "+ Create a new playlist",
              () -> {
                d.dismiss();
                createPlaylist(media);
              },
              true));
    }

    void more() {
      heading("More", "Everything else, close at hand");
      paired(body, "Settings", () -> go("settings"), "▲ About VLC", () -> go("about"));
      LinearLayout media = group("Your media");
      destination(
          media, "Playback history", "Pick up where you left off", "video", () -> go("history"));
      destination(media, "Streams", "Open a network address", "network", () -> go("stream"));
      destination(media, "Bookmarks", "Saved moments", "bookmark", () -> go("bookmarks"));
      LinearLayout tools = group("Tools");
      destination(tools, "Equalizer", "Find your sound", "audio", () -> go("equalizer"));
      destination(
          tools,
          "Remote access",
          "Connect a browser",
          "network",
          () -> pref("preferences_remote_access"));
    }

    void paired(LinearLayout parent, String first, Runnable a1, String second, Runnable a2) {
      LinearLayout pair = row();
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
      p.rightMargin = dp(8);
      pair.addView(button(first, a1, true), p);
      pair.addView(button(second, a2, false), new LinearLayout.LayoutParams(0, -2, 1));
      parent.addView(pair);
    }

    void settings() {
      heading("Settings", "Make VLC yours");
      LinearLayout library = group("Media library");
      destination(
          library,
          "Folders",
          "Choose what VLC scans",
          null,
          () -> openFolder(Environment.getExternalStorageDirectory()));
      toggle(library, "Auto rescan", "Refresh when opening VLC", "auto_rescan", true);
      LinearLayout personalize = group("Personalize");
      destination(
          personalize,
          "Interface",
          "Theme, language & library",
          null,
          () -> pref("preferences_ui"));
      destination(
          personalize, "Video", "Playback & controls", null, () -> pref("preferences_video"));
      destination(
          personalize, "Audio", "Sound & headset behavior", null, () -> pref("preferences_audio"));
      destination(
          personalize,
          "Subtitles",
          "Appearance & language",
          null,
          () -> pref("preferences_subtitles"));
      destination(personalize, "Artwork", "Movie posters and album covers", null, () -> go("artwork-settings"));
      LinearLayout more = group("More");
      String[][] sections = {
        {"Casting", "Devices & conversion", "preferences_casting"},
        {"Remote access", "Pair a browser", "preferences_remote_access"},
        {"Parental controls", "PIN & restrictions", "preferences_parental_control"},
        {"Android Auto", "Interface & player controls", "preferences_android_auto"},
        {"Widgets", "Appearance & controls", "preferences_widgets"},
        {"Advanced", "Network & data tools", "preferences_adv"},
        {"Optional features", "Experimental options", "preferences_optional"}
      };
      for (String[] s : sections)
        destination(
            more,
            s[0],
            s[1],
            null,
            () -> {
              if (s[2].equals("preferences_widgets")) go("widgets");
              else pref(s[2]);
            });
      destination(more, "Permissions", "Review device access", null, () -> go("permissions"));
    }

    void artworkSettings() {
      heading("Artwork", "Personalize your library");
      body.addView(text(Artwork.status(a), 12, muted));
      gap(body, 12);
      group("Movie and TV posters");
      body.addView(text("Add a TMDB read token to look up posters. The token stays in private app storage and is hidden while you type.", 13, muted));
      gap(body, 12);
      EditText token = new EditText(a);
      token.setSingleLine(true);
      token.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
      token.setHint("TMDB read token");
      token.setTextColor(ink);
      body.addView(token, new LinearLayout.LayoutParams(-1, dp(52)));
      if (new File(a.getNoBackupFilesDir(), "premiere_tmdb_token").isFile()) {
        TextView enabled = text("Movie and TV poster lookups are enabled.", 12, orange);
        body.addView(enabled);
      }
      gap(body, 10);
      body.addView(button("Save and enable posters", () -> {
        String secret = token.getText().toString().trim();
        if (secret.isEmpty()) { token.setError("Enter a read token"); return; }
        try {
          token.setEnabled(false);
          java.lang.ref.WeakReference<Activity> weakActivity = new java.lang.ref.WeakReference<>(a);
          java.lang.ref.WeakReference<EditText> weakToken = new java.lang.ref.WeakReference<>(token);
          Handler mainHandler = new Handler(Looper.getMainLooper());
          Artwork.validate(secret, valid -> mainHandler.post(() -> {
            Activity activity = weakActivity.get(); EditText input = weakToken.get();
            if (activity == null || activity.isDestroyed() || input == null) return;
            input.setEnabled(true);
            if (!valid) { input.setError("Token could not be validated"); Toast.makeText(activity, "TMDB token validation failed", Toast.LENGTH_SHORT).show(); return; }
            try {
              Artwork.saveToken(activity, secret);
              prefs.edit().putBoolean("premiere_video_artwork", true).apply();
              input.setText(""); Artwork.invalidateMemory(); Artwork.clearMisses(activity, () -> {if(!activity.isDestroyed())refresh();}); Toast.makeText(activity, "Artwork retrieval enabled", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { Toast.makeText(activity, "Could not save artwork setting", Toast.LENGTH_SHORT).show(); }
          }));
        } catch (Exception e) { toast("Could not save artwork setting"); }
      }, true));
      gap(body, 10);
      Switch videoSwitch = new Switch(a);
      videoSwitch.setText("Look up online movie posters");
      videoSwitch.setTextColor(ink);
      videoSwitch.setChecked(prefs.getBoolean("premiere_video_artwork", true));
      videoSwitch.setOnCheckedChangeListener((button, checked) -> {
        prefs.edit().putBoolean("premiere_video_artwork", checked).apply();
        Artwork.invalidateMemory();
        Artwork.revisit();
        if(checked)refresh();
      });
      body.addView(videoSwitch);
      gap(body, 10);
      body.addView(button("Clear artwork cache", () -> {
        Artwork.clearCache(a, () -> {if(!a.isDestroyed()){toast("Artwork cache cleared");refresh();}});
      }, false));
      gap(body, 20);
      group("Music covers");
      body.addView(text("Covers use tags and filenames with fuzzy matching, including numbered tracks. MusicBrainz and Deezer supply album covers. YouTube thumbnails provide matching song and live-set images. An identified artist's release can supply a representative cover when no closer match is available. Embedded covers are used first.", 13, muted));
      Switch musicSwitch = new Switch(a);
      musicSwitch.setText("Look up online album covers");
      musicSwitch.setTextColor(ink);
      musicSwitch.setChecked(prefs.getBoolean("premiere_music_artwork", true));
      musicSwitch.setOnCheckedChangeListener((button, checked) -> {prefs.edit().putBoolean("premiere_music_artwork", checked).apply();Artwork.revisit();if(checked)refresh();});
      body.addView(musicSwitch);
      gap(body, 20);
      body.addView(button("Retry missing artwork", () -> { Artwork.invalidateMemory(); Artwork.clearMisses(a, () -> {if(!a.isDestroyed()){toast("Artwork retry queued");refresh();}}); }, false));
    }

    void pref(String name) {
      try {
        a.startActivity(
            new Intent(a, Class.forName("org.videolan.vlc.gui.preferences.PreferencesActivity"))
                .putExtra("premiere_preference_section", name));
      } catch (Exception e) {
        android.util.Log.e("Premiere", "Preferences launch", e);
      }
    }

    String resourceString(XmlResourceParser p, String attr, String fallback) {
      int id = p.getAttributeResourceValue("http://schemas.android.com/apk/res/android", attr, 0);
      if (id != 0)
        try {
          return a.getString(id);
        } catch (Exception e) {
        }
      String v = p.getAttributeValue("http://schemas.android.com/apk/res/android", attr);
      if (v == null) {
        id = p.getAttributeResourceValue("http://schemas.android.com/apk/res-auto", attr, 0);
        if (id != 0)
          try {
            return a.getString(id);
          } catch (Exception e) {
          }
        v = p.getAttributeValue("http://schemas.android.com/apk/res-auto", attr);
      }
      return v == null ? fallback : v;
    }

    boolean defaultBoolean(XmlResourceParser p) {
      int id =
          p.getAttributeResourceValue(
              "http://schemas.android.com/apk/res/android", "defaultValue", 0);
      if (id != 0)
        try {
          return a.getResources().getBoolean(id);
        } catch (Exception e) {
        }
      return p.getAttributeBooleanValue(
          "http://schemas.android.com/apk/res/android", "defaultValue", false);
    }

    void preferences() {
      int id = a.getResources().getIdentifier(xml, "xml", a.getPackageName());
      if (id == 0) {
        heading("Settings", "");
        return;
      }
      try {
        XmlResourceParser p = a.getResources().getXml(id);
        while (p.getEventType() != XmlPullParser.START_TAG
            && p.next() != XmlPullParser.END_DOCUMENT) {}
        String page = xml.replace("preferences_", "").replace('_', ' ');
        heading(
            page.substring(0, 1).toUpperCase(Locale.ROOT) + page.substring(1), "VLC preferences");
        LinearLayout current = group("Preferences");
        while (p.next() != XmlPullParser.END_DOCUMENT) {
          if (p.getEventType() != XmlPullParser.START_TAG) continue;
          String type = p.getName(),
              key = resourceString(p, "key", ""),
              name = resourceString(p, "title", ""),
              sub = resourceString(p, "summary", "");
          if (type.endsWith("PreferenceCategory")) {
            current = group(name);
            continue;
          }
          if (name.isEmpty() || key.equals("tv_ui")) continue;
          if (type.endsWith("CheckBoxPreference")
              || type.endsWith("SwitchPreference")
              || type.endsWith("SwitchPreferenceCompat")) {
            toggle(current, name, sub, key, defaultBoolean(p));
          } else if (type.endsWith("ListPreference")) {
            int
                entries =
                    p.getAttributeResourceValue(
                        "http://schemas.android.com/apk/res/android", "entries", 0),
                values =
                    p.getAttributeResourceValue(
                        "http://schemas.android.com/apk/res/android", "entryValues", 0);
            String def = resourceString(p, "defaultValue", "");
            String val = str(prefs.getAll().containsKey(key) ? prefs.getAll().get(key) : def);
            String[]
                labels = entries == 0 ? new String[0] : a.getResources().getStringArray(entries),
                vs = values == 0 ? new String[0] : a.getResources().getStringArray(values);
            String summary = val;
            for (int n = 0; n < Math.min(labels.length, vs.length); n++)
              if (vs[n].equals(val)) summary = labels[n];
            String shown = summary;
            destination(current, name, shown, null, () -> choosePreference(key, name, labels, vs));
          } else if (type.endsWith("SeekBarPreference")) {
            int max =
                p.getAttributeIntValue("http://schemas.android.com/apk/res/android", "max", 100);
            int min = p.getAttributeIntValue("http://schemas.android.com/apk/res-auto", "min", 0);
            int def =
                p.getAttributeIntValue(
                    "http://schemas.android.com/apk/res/android", "defaultValue", min);
            TextView caption = text(name, 14, ink);
            current.addView(caption);
            SeekBar bar = new SeekBar(a);
            bar.setMax(max - min);
            bar.setProgress(
                (int) number((prefs.getAll().containsKey(key) ? prefs.getAll().get(key) : def))
                    - min);
            final int low = min;
            bar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                  public void onProgressChanged(SeekBar s, int v, boolean user) {
                    if (user) prefs.edit().putInt(key, v + low).apply();
                  }

                  public void onStartTrackingTouch(SeekBar s) {}

                  public void onStopTrackingTouch(SeekBar s) {}
                });
            current.addView(bar);
          } else if (type.endsWith("EditTextPreference")
              || type.endsWith("NumberPickerPreference")) {
            if (type.endsWith("NumberPickerPreference")) integerPreferences.add(key);
            destination(
                current, name, str(prefs.getAll().get(key)), null, () -> editPreference(key, name));
          } else {
            destination(
                current,
                name,
                sub.equals("%s") ? "" : sub,
                null,
                () -> preferenceAction(key, name));
          }
        }
        p.close();
      } catch (Exception e) {
        android.util.Log.e("Premiere", "Preference rendering", e);
        body.addView(text("This setting could not be opened.", 14, muted));
      }
    }

    void toggle(LinearLayout parent, String name, String sub, String key, boolean def) {
      LinearLayout r = row();
      r.setPadding(0, dp(14), 0, dp(14));
      LinearLayout copy = column();
      copy.addView(text(name, 14, ink));
      if (!sub.isEmpty()) {
        gap(copy, 6);
        TextView desc = text(sub, 11, muted);
        desc.setMaxLines(3);
        copy.addView(desc);
      }
      r.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
      Switch sw = new Switch(a);
      Object val = prefs.getAll().get(key);
      sw.setChecked(val instanceof Boolean ? (Boolean) val : def);
      sw.setContentDescription(name);
      sw.setThumbTintList(android.content.res.ColorStateList.valueOf(orange));
      sw.setOnCheckedChangeListener(
          (b, on) -> {
            prefs.edit().putBoolean(key, on).apply();
            if (key.equals("enable_remote_access"))
              invoke(
                  clazz("org.videolan.resources.util.ExtensionsKt"),
                  on ? "startRemoteAccess" : "stopRemoteAccess",
                  a);
          });
      r.addView(sw);
      r.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
      if (parent.getChildCount() > 0) {
        View divider = new View(a);
        divider.setBackgroundColor(line);
        parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
      }
      parent.addView(r);
    }

    void choosePreference(String key, String name, String[] labels, String[] values) {
      if (key.equals("set_locale")) {
        editPreference(key, name);
        return;
      }
      if (labels.length == 0) {
        editPreference(key, name);
        return;
      }
      LinearLayout options = column();
      Dialog d = sheet(name, options);
      for (int i = 0; i < Math.min(labels.length, values.length); i++) {
        final String val = values[i];
        destination(
            options,
            labels[i],
            val.equals(str(prefs.getAll().get(key))) ? "Selected" : "",
            null,
            () -> {
              prefs.edit().putString(key, val).apply();
              d.dismiss();
              render();
            });
      }
    }

    void editPreference(String key, String name) {
      LinearLayout form = column();
      EditText e = field(form, name, str(prefs.getAll().get(key)));
      Dialog d = sheet(name, form);
      gap(form, 18);
      form.addView(
          button(
              "Save",
              () -> {
                try {
                  if (integerPreferences.contains(key)
                      || prefs.getAll().get(key) instanceof Integer)
                    prefs.edit().putInt(key, Integer.parseInt(e.getText().toString())).apply();
                  else prefs.edit().putString(key, e.getText().toString()).apply();
                  d.dismiss();
                  render();
                } catch (NumberFormatException ex) {
                  e.setError("Enter a number");
                }
              },
              true));
    }

    void preferenceAction(String key, String name) {
      String mapped =
          key.contains("video_controls")
              ? "preferences_video_controls"
              : key.contains("audio_controls")
                  ? "preferences_audio_controls"
                  : key.contains("subtitle")
                      ? "preferences_subtitles"
                      : key.contains("optional") ? "preferences_optional" : "";
      if (!mapped.isEmpty()) {
        pref(mapped);
        return;
      }
      if (key.equals("equalizer")) {
        go("equalizer");
        return;
      }
      if (key.equals("directories")) {
        openFolder(Environment.getExternalStorageDirectory());
        return;
      }
      if (key.equals("remote_access_status")) {
        a.startActivity(
            new Intent("vlc.remoteaccess.share").setClassName(a, "org.videolan.vlc.StartActivity"));
        return;
      }
      if (key.equals("remote_access_info")) {
        legacy(
            "org.videolan.vlc.remoteaccessserver.gui.remoteaccess.onboarding.RemoteAccessOnboardingActivity");
        return;
      }
      if (key.equals("modify_pin_code")) {
        Object reason =
            singleton(
                "org.videolan.vlc.gui.PinCodeReason",
                prefs.contains("safe_mode_pin") ? "MODIFY" : "FIRST_CREATION");
        Object intent =
            invoke(
                singleton("org.videolan.vlc.gui.PinCodeActivity", "Companion"),
                "getIntent",
                a,
                reason);
        if (intent instanceof Intent) a.startActivity((Intent) intent);
        return;
      }
      if (key.equals("debug_logs")) {
        legacy("org.videolan.vlc.gui.DebugLogActivity");
        return;
      }
      if (key.equals("quit_app")) {
        invoke(service(), "stop");
        a.finish();
        return;
      }
      if (key.equals("clear_history")) {
        LinearLayout c = column();
        c.addView(text("Clear recently played media? Your files stay on this device.", 14, muted));
        Dialog d = sheet("Clear playback history", c);
        gap(c, 18);
        c.addView(
            button(
                "Clear history",
                () -> {
                  invoke(library(), "clearHistory", 0);
                  d.dismiss();
                },
                true));
        return;
      }
      originalPreferences(name);
    }

    void originalPreferences(String title) {
      try {
        Intent intent =
            new Intent(a, Class.forName("org.videolan.vlc.gui.preferences.PreferencesActivity"));
        int id = a.getResources().getIdentifier(xml, "xml", a.getPackageName());
        intent.putExtra("premiere_preference_section", xml);
        intent.putExtra("premiere_preference_action", title);
        a.startActivity(intent);
      } catch (Exception e) {
        toast("Open this setting from VLC preferences");
      }
    }

    void information() {
      heading("Media information", "");
      body.addView(art(item, 220), new LinearLayout.LayoutParams(-1, dp(220)));
      gap(body, 20);
      body.addView(text(title(item), 26, ink));
      gap(body, 12);
      body.addView(text(format(item) + " · " + time(number(invoke(item, "getLength"))), 12, muted));
      LinearLayout stats = group("Video");
      destination(
          stats,
          "Resolution",
          number(invoke(item, "getWidth")) + " × " + number(invoke(item, "getHeight")),
          null,
          () -> {});
      destination(stats, "Duration", time(number(invoke(item, "getLength"))), null, () -> {});
      LinearLayout file = group("File");
      String uri = str(invoke(item, "getUri"));
      destination(file, str(invoke(item, "getFileName")), uri, null, () -> {});
      gap(body, 24);
      body.addView(button("▶ Play media", () -> play(item), true));
    }

    void settingsSearch() {
      heading("Search settings", "Find a preference");
      EditText input = field(body, "Search preferences", "");
      LinearLayout results = column();
      body.addView(results);
      input.addTextChangedListener(
          new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            public void afterTextChanged(Editable e) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {
              results.removeAllViews();
              String query = s.toString().trim().toLowerCase(Locale.ROOT);
              if (query.isEmpty()) return;
              for (String file :
                  new String[] {
                    "preferences_ui",
                    "preferences_video",
                    "preferences_video_controls",
                    "preferences_audio",
                    "preferences_audio_controls",
                    "preferences_subtitles",
                    "preferences_casting",
                    "preferences_remote_access",
                    "preferences_adv",
                    "preferences_android_auto",
                    "preferences_parental_control"
                  })
                try {
                  XmlResourceParser p =
                      a.getResources()
                          .getXml(a.getResources().getIdentifier(file, "xml", a.getPackageName()));
                  while (p.next() != XmlPullParser.END_DOCUMENT)
                    if (p.getEventType() == XmlPullParser.START_TAG) {
                      String name = resourceString(p, "title", ""),
                          description = resourceString(p, "summary", "");
                      if ((name + " " + description).toLowerCase(Locale.ROOT).contains(query))
                        destination(
                            results,
                            name,
                            file.replace("preferences_", "").replace('_', ' '),
                            null,
                            () -> pref(file));
                    }
                  p.close();
                } catch (Exception e) {
                }
            }
          });
    }

    void search() {
      heading("Search", "Films and music in your library");
      EditText query = field(body, "Search your media", "");
      LinearLayout results = column();
      body.addView(results);
      query.addTextChangedListener(new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void afterTextChanged(Editable e) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {
          int generation = ++searchGeneration;
          String q = s.toString().trim().toLowerCase(Locale.ROOT);
          results.removeAllViews();
          if (q.isEmpty()) return;
          results.addView(text("Searching…", 12, muted));
          handler.postDelayed(() -> {
            if (generation != searchGeneration || !screen.equals("search")) return;
            Object[] source = concat(videos, audios);
            mediaQueries.execute(() -> {
              ArrayList<Object> first = new ArrayList<>();
              ArrayList<Object> rest = new ArrayList<>();
              int matches = 0;
              for (Object media : source) {
                if (generation != searchGeneration) return;
                String name = title(media).toLowerCase(Locale.ROOT);
                String searchable = name + " "
                    + str(invoke(media, "getArtistName")).toLowerCase(Locale.ROOT)
                    + " " + str(invoke(media, "getAlbumName")).toLowerCase(Locale.ROOT);
                if (searchable.contains(q)) {
                  matches++;
                  if (first.size() + rest.size() < 60) {
                    if (name.startsWith(q)) first.add(media);
                    else rest.add(media);
                  }
                }
              }
              int resultCount = matches;
              handler.post(() -> {
                if (generation != searchGeneration || !screen.equals("search")
                    || results.getParent() == null) return;
                results.removeAllViews();
                results.addView(label(resultCount + " matching items"
                    + (resultCount > 60 ? " · Showing the first 60" : "")));
                if (resultCount == 0) results.addView(text("No matching media found.", 14, muted));
                for (Object media : first) mediaRow(results, media, () -> play(media), true);
                for (Object media : rest) mediaRow(results, media, () -> play(media), true);
              });
            });
          }, 250);
        }
      });
    }

    void historyPage() {
      heading("History", "Recently played");
      Object[] played = array(invoke(library(), "history", 0));
      for (Object v : played) mediaRow(body, v, () -> play(v), true);
      if (played.length == 0)
        empty("Your history starts with a play.", "Recently played media will appear here.");
    }

    void audioPlayer() {
      Object s = service(), media = invoke(s, "getCurrentMediaWrapper");
      if (media == null) {
        heading("Now playing", "");
        empty("Choose something to listen to.", "Your music is waiting in the library.");
        body.addView(button("Your music", () -> go("audio"), true));
        return;
      }
      currentTitle = title(media);
      heading("Now playing", str(invoke(media, "getAlbumName")));
      int width =
          (int)
                  (a.getResources().getDisplayMetrics().widthPixels
                      / a.getResources().getDisplayMetrics().density)
              - 40;
      int cover = Math.min(width, 250);
      LinearLayout.LayoutParams squareCover=new LinearLayout.LayoutParams(dp(cover),dp(cover));
      squareCover.gravity=Gravity.CENTER_HORIZONTAL;
      body.addView(art(media, cover), squareCover);
      gap(body, 20);
      TextView title = text(title(media), 26, ink);
      title.setGravity(Gravity.CENTER);
      body.addView(title);
      gap(body, 8);
      TextView artist = text(str(invoke(media, "getArtistName")), 14, muted);
      artist.setGravity(Gravity.CENTER);
      body.addView(artist);
      gap(body, 20);
      seek = new SeekBar(a);
      seek.setMax((int) Math.min(Integer.MAX_VALUE, number(invoke(s, "getLength"))));
      seek.setProgress((int) number(invoke(s, "getTime")));
      seek.setProgressTintList(android.content.res.ColorStateList.valueOf(orange));
      seek.setThumbTintList(android.content.res.ColorStateList.valueOf(orange));
      seek.setContentDescription("Track position");
      seek.setOnSeekBarChangeListener(
          new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int value, boolean user) {
              if (user) elapsed.setText(time(value));
            }

            public void onStartTrackingTouch(SeekBar b) {
              seeking = true;
            }

            public void onStopTrackingTouch(SeekBar b) {
              seekTo((long) b.getProgress());
              seeking = false;
            }
          });
      body.addView(seek);
      LinearLayout times = row();
      elapsed = text(time(number(invoke(s, "getTime"))), 11, muted);
      total = text(time(number(invoke(s, "getLength"))), 11, muted);
      times.addView(elapsed, new LinearLayout.LayoutParams(0, -2, 1));
      times.addView(total);
      body.addView(times);
      gap(body, 20);
      LinearLayout transport = row();
      transport.setGravity(Gravity.CENTER);
      transport.addView(
          icon("shuffle", Boolean.TRUE.equals(invoke(s, "isShuffling")) ? "Shuffle on" : "Shuffle off", () -> {
            invoke(service(), "shuffle");
            toast(Boolean.TRUE.equals(invoke(service(), "isShuffling")) ? "Shuffle on" : "Shuffle off");
            render();
          }),
          new LinearLayout.LayoutParams(dp(46), dp(52)));
      transport.addView(
          icon("back", "Previous track", () -> invoke(service(), "previous", true)),
          new LinearLayout.LayoutParams(dp(48), dp(52)));
      playButton =
          button(
              Boolean.TRUE.equals(invoke(s, "isPlaying")) ? "Ⅱ" : "▶",
              () -> {
                Object svc = service();
                invoke(svc, Boolean.TRUE.equals(invoke(svc, "isPlaying")) ? "pause" : "play");
              },
              true);
      playButton.setTextSize(25);
      playButton.setBackground(surface(orange, 40, false));
      transport.addView(playButton, new LinearLayout.LayoutParams(dp(58), dp(58)));
      transport.addView(
          icon("next", "Next track", () -> invoke(service(), "next", true)),
          new LinearLayout.LayoutParams(dp(48), dp(52)));
      transport.addView(
          icon("list", "Open queue", () -> go("queue")),
          new LinearLayout.LayoutParams(dp(46), dp(52)));
      body.addView(transport);
      gap(body, 16);
      LinearLayout tools = row();
      for (String name : new String[] {"Equalizer", "Sleep timer", "Bookmarks"}) {
        TextView t = text(name, 11, orange);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(44));
        t.setOnClickListener(
            v -> {
              if (name.equals("Equalizer")) go("equalizer");
              else if (name.equals("Bookmarks")) go("bookmarks");
              else sleepTimer();
            });
        tools.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
      }
      body.addView(tools);
      gap(body, 12);
      paired(body, "Repeat: " + repeatLabel(), this::cycleRepeat,
          "Playback speed", this::audioSpeed);
      gap(body, 10);
      paired(body, "−15 seconds", () -> {
        seekTo(VideoSeek.target(number(invoke(service(), "getTime")),
            number(invoke(service(), "getLength")), -15000L));
      }, "+30 seconds", () -> {
        Object playback = service();
        seekTo(VideoSeek.target(number(invoke(playback, "getTime")),
            number(invoke(playback, "getLength")), 30000L));
      });
      gap(body, 10);
      body.addView(button("Chapters", this::audioChapters, false));
    }

    String repeatLabel() {
      Object r = invoke(service(), "getRepeatType");
      int mode = r instanceof Number ? ((Number) r).intValue() : 0;
      return PlaybackUX.repeatLabel(mode);
    }

    void cycleRepeat() {
      Object s = service();
      Object r = invoke(s, "getRepeatType");
      if (!(r instanceof Number)) {
        toast("Repeat control unavailable");
        return;
      }
      int mode = ((Number) r).intValue();
      invoke(s, "setRepeatType", PlaybackUX.nextRepeatMode(mode));
      handler.postDelayed(this::render, 100);
    }

    void audioSpeed() {
      LinearLayout content = column();
      Dialog dialog = sheet("Playback speed", content);
      Object current = invoke(service(), "getRate");
      content.addView(text("Current speed · " + (current == null ? "1" : current) + "×", 15, ink));
      for (float speed : new float[] {.5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f})
        destination(content, speed + "×", speed == 1f ? "Normal speed" : "", null, () -> {
          invoke(service(), "setRate", speed, true);
          dialog.dismiss();
          toast("Playback speed · " + speed + "×");
        });
    }

    void audioChapters() {
      LinearLayout content = column();
      Dialog dialog = sheet("Chapters", content);
      Object[] chapters = array(invoke(service(), "getChapters", -1));
      if (chapters.length == 0) {
        content.addView(text("No chapters available for this track.", 14, muted));
        return;
      }
      for (int i = 0; i < chapters.length; i++) {
        final int index = i;
        Object chapter = chapters[i];
        String name = str(invoke(chapter, "getName"));
        if (name.isEmpty()) {
          try { name = str(chapter.getClass().getField("name").get(chapter)); }
          catch (Exception ignored) {}
        }
        destination(content, name.isEmpty() ? "Chapter " + (i + 1) : name,
            "Jump to chapter", null, () -> {
              invoke(service(), "setChapterIdx", index);
              dialog.dismiss();
            });
      }
    }

    void queue() {
      Object manager = invoke(service(), "getPlaylistManager");
      Object[] queue = array(invoke(manager, "getMediaList"));
      heading("Up next", queue.length + " tracks");
      for (int i = 0; i < queue.length; i++) {
        final int pos = i;
        Object media = queue[i];
        LinearLayout entry = column();
        mediaRow(
            entry,
            media,
            () -> {
              invoke(service(), "playIndex", pos);
              go("audio-player");
            },
            false);
        View.OnDragListener drop = (target, event) -> {
          if (event.getAction() == DragEvent.ACTION_DROP) {
            try {
              ClipData data = event.getClipData();
              if (data == null || data.getItemCount() == 0) return false;
              int from = Integer.parseInt(data.getItemAt(0).coerceToText(a).toString());
              if (from < 0 || from >= queue.length || from == pos) return true;
              invoke(manager, "moveItem", from, pos);
              render();
            } catch (NumberFormatException ignored) {
              toast("Could not reorder queue");
            }
          }
          return true;
        };
        entry.setOnDragListener(drop);
        entry.getChildAt(0).setOnDragListener(drop);
        body.addView(entry);
        LinearLayout actions = row();
        actions.addView(
            button(
                "Move up",
                () -> {
                  if (pos > 0) {
                    invoke(manager, "moveItem", pos, pos - 1);
                    render();
                  }
                },
                false),
            new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(button("Move down", () -> {
          if (pos < queue.length - 1) {
            invoke(manager, "moveItem", pos, pos + 1);
            render();
          }
        }, false), new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(
            button(
                "Remove",
                () -> {
                  invoke(manager, "remove", pos);
                  render();
                },
                false),
            new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView drag = button("Drag", () -> toast("Long-press Drag, then drop on a track"), false);
        drag.setOnLongClickListener(v -> {
          ClipData data = ClipData.newPlainText("premiere.queue.index", String.valueOf(pos));
          v.startDragAndDrop(data, new View.DragShadowBuilder(v), null, 0);
          return true;
        });
        drag.setContentDescription("Drag to reorder " + title(media));
        drag.setTextSize(11);
        actions.addView(drag, new LinearLayout.LayoutParams(0, dp(48), 1));
        body.addView(actions);
        gap(body, 8);
      }
    }

    void bookmarks() {
      Object media = invoke(service(), "getCurrentMediaWrapper");
      heading("Bookmarks", title(media));
      Object[] bs = array(invoke(media, "getBookmarks"));
      for (Object bookmark : bs) {
        long t = number(invoke(bookmark, "getTime"));
        destination(
            body,
            title(bookmark).isEmpty() ? "Saved at " + time(t) : title(bookmark),
            time(t),
            "bookmark",
            () -> seekTo(t));
      }
      if (media != null) {
        gap(body, 20);
        body.addView(
            button(
                "+ Add bookmark",
                () -> {
                  Object b = invoke(media, "addBookmark", number(invoke(service(), "getTime")));
                  if (b != null)
                    invoke(b, "setName", "Moment at " + time(number(invoke(service(), "getTime"))));
                  toast(b == null ? "Bookmark could not be saved" : "Bookmark saved");
                  render();
                },
                true));
      } else empty("Save a moment.", "Start playback to add a bookmark.");
    }

    void sleepTimer() {
      LinearLayout content = column();
      Dialog d = sheet("Sleep timer", content);
      for (int min : new int[] {15, 30, 45, 60})
        destination(
            content,
            min + " min",
            "Stop playback after this duration",
            null,
            () -> {
              Calendar end = Calendar.getInstance();
              end.add(Calendar.MINUTE, min);
              invoke(service(), "setSleepTimer", end);
              d.dismiss();
              toast("Sleep timer set");
            });
      destination(
          content,
          "Cancel timer",
          "Keep playing",
          null,
          () -> {
            invoke(service(), "setSleepTimer", (Object) null);
            d.dismiss();
          });
    }

    void equalizerDialog() {
      LinearLayout content = column();
      equalizerControls(content);
      sheet("Equalizer", content);
    }

    void equalizerControls(LinearLayout content) {
      LinearLayout controls = column();
      content.addView(controls);
      new EqualizerUI(this, controls).start();
    }

    void equalizer() {
      heading("Equalizer", "Shape the sound");
      equalizerControls(body);
    }

    void about() {
      heading("About VLC", "");
      TextView logo = text("▲", 64, orange);
      logo.setGravity(Gravity.CENTER);
      body.addView(logo);
      gap(body, 16);
      TextView name = text("VLC", 34, ink);
      name.setGravity(Gravity.CENTER);
      body.addView(name);
      gap(body, 10);
      TextView tagline = text("Media player, for everyone.", 14, muted);
      tagline.setGravity(Gravity.CENTER);
      body.addView(tagline);
      try {
        ImageView tmdbLogo = new ImageView(a);
        InputStream logoStream = a.getAssets().open("premiere/tmdb-logo.png");
        tmdbLogo.setImageBitmap(BitmapFactory.decodeStream(logoStream)); logoStream.close();
        tmdbLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoSize = new LinearLayout.LayoutParams(dp(58), dp(42)); logoSize.gravity = Gravity.CENTER; logoSize.topMargin = dp(14);
        body.addView(tmdbLogo, logoSize);
      } catch (Exception ignored) {}
      gap(body, 12);
      String version = "";
      try {
        version = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
      } catch (Exception e) {
      }
      TextView ver = text("Version " + version + " · Android", 12, muted);
      ver.setGravity(Gravity.CENTER);
      body.addView(ver);
      LinearLayout project = group("The project");
      destination(
          project,
          "VideoLAN",
          "A worldwide open source community",
          null,
          () ->
              a.startActivity(
                  new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.videolan.org/"))));
      destination(
          project,
          "Contributors",
          "The people behind VLC",
          null,
          () -> legacy("org.videolan.vlc.gui.AuthorsActivity"));
      destination(
          project,
          "Open source licenses",
          "Libraries & acknowledgments",
          null,
          () -> legacy("org.videolan.vlc.gui.LibrariesActivity"));
      LinearLayout providers = group("Artwork credits");
      TextView tmdb = text("This product uses the TMDB API but is not endorsed or certified by TMDB.", 12, muted);
      providers.addView(tmdb);
      destination(providers, "TMDB", "Movie and television artwork", null, () -> a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.themoviedb.org/"))));
      destination(providers, "MusicBrainz", "Album metadata", null, () -> a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://musicbrainz.org/"))));
      destination(providers, "Cover Art Archive", "Album artwork source", null, () -> a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://coverartarchive.org/"))));
      destination(providers, "Deezer", "Fallback album metadata and artwork", null, () -> a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.deezer.com/"))));
      destination(providers, "YouTube", "Thumbnail images from public video search", null, () -> a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/"))));
      destination(
          project,
          "Feedback & support",
          "Share your experience",
          null,
          () -> legacy("org.videolan.vlc.gui.FeedbackActivity"));
    }

    void widgets() {
      heading("Widgets", "Keep playback close");
      android.appwidget.AppWidgetManager manager =
          android.appwidget.AppWidgetManager.getInstance(a);
      ComponentName provider =
          new ComponentName(
              a.getPackageName(), "org.videolan.vlc.widget.MiniPlayerAppWidgetProvider");
      int[] ids = manager.getAppWidgetIds(provider);
      LinearLayout g = group("On your home screen");
      for (int id : ids)
        destination(
            g,
            "VLC widget",
            "Widget " + id,
            "audio",
            () ->
                a.startActivity(
                    new Intent()
                        .setClassName(a, "org.videolan.vlc.widget.MiniPlayerConfigureActivity")
                        .putExtra("appWidgetId", id)));
      if (ids.length == 0) {
        g.addView(
            text("Add a VLC widget to pause, skip and resume without opening the app.", 14, muted));
        gap(body, 24);
      }
      body.addView(
          button(
              "Add a VLC widget",
              () -> {
                if (android.os.Build.VERSION.SDK_INT >= 26
                    && manager.isRequestPinAppWidgetSupported())
                  manager.requestPinAppWidget(provider, null, null);
                else toast("Touch and hold your home screen, then choose Widgets → VLC");
              },
              true));
    }

    void permissions() {
      heading("Permissions", "Review device access");
      LinearLayout access = group("Device access");
      destination(
          access,
          "Videos & music",
          "Android media permissions",
          null,
          () ->
              a.startActivity(
                  new Intent(
                      android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                      Uri.parse("package:" + a.getPackageName()))));
      destination(
          access,
          "External storage",
          "Choose files and folders",
          null,
          () ->
              a.startActivity(
                  new Intent(
                      android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                      Uri.parse("package:" + a.getPackageName()))));
      destination(
          access,
          "Notifications",
          "Background playback controls",
          null,
          () ->
              a.startActivity(
                  new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                      .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, a.getPackageName())));
    }

    void groups() {
      heading("Collections", "Your videos, brought together");
      Object[] groups = array(invoke(library(), "getVideoGroups", 0, false, true, false, 0, 0));
      for (Object group : groups)
        destination(
            body,
            title(group),
            number(invoke(group, "getTracksCount")) + " videos",
            "video",
            () -> goItem("group", group));
      if (groups.length == 0)
        empty("Bring your videos together.", "Create collections using media actions.");
    }

    void groupPage() {
      heading(title(item), "Your video collection");
      Object[] media = tracks(item);
      paired(
          body, "▶ Play all", () -> playList(media, false), "Shuffle", () -> playList(media, true));
      for (Object v : media) mediaRow(body, v, () -> play(v), true);
    }

    void selection() {
      heading(selected.size() + " selected", "Choose videos for a collection");
      paired(
          body,
          "Play selected",
          () -> playList(selected.toArray(), false),
          "Create collection",
          () -> groupDialog(selected.toArray()));
      gap(body, 18);
      for (Object media : videos) {
        LinearLayout r = row();
        CheckBox cb = new CheckBox(a);
        cb.setButtonTintList(android.content.res.ColorStateList.valueOf(orange));
        cb.setChecked(selected.contains(media));
        cb.setContentDescription("Select " + title(media));
        cb.setOnCheckedChangeListener(
            (v, on) -> {
              if (on) selected.add(media);
              else selected.remove(media);
            });
        r.addView(cb);
        r.addView(art(media, 56), new LinearLayout.LayoutParams(dp(68), dp(56)));
        TextView t = text(title(media), 14, ink);
        t.setPadding(dp(14), dp(18), 0, dp(18));
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        r.setOnClickListener(v -> cb.setChecked(!cb.isChecked()));
        body.addView(r);
        gap(body, 10);
      }
    }

    void groupDialog(Object[] media) {
      LinearLayout content = column();
      EditText name = field(content, "Collection name", "");
      Dialog d = sheet("Create collection", content);
      gap(content, 18);
      content.addView(
          button(
              "Save collection",
              () -> {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) {
                  name.setError("Enter a collection name");
                  return;
                }
                Object group = invoke(library(), "createVideoGroup", n);
                if (group == null) {
                  toast("Collection could not be created");
                  return;
                }
                for (Object v : media) invoke(group, "add", number(invoke(v, "getId")));
                d.dismiss();
                goItem("group", group);
              },
              true));
    }

    void rename(Object media) {
      LinearLayout c = column();
      String filename = str(invoke(media, "getFileName"));
      EditText input = field(c, "File name", filename);
      Dialog d = sheet("Rename file", c);
      gap(c, 18);
      c.addView(
          button(
              "Rename",
              () -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty() || name.contains("/")) {
                  input.setError("Enter a valid file name");
                  return;
                }
                Uri uri = (Uri) invoke(media, "getUri");
                if (uri == null || !"file".equals(uri.getScheme())) {
                  toast("This location requires Android’s file manager");
                  return;
                }
                File from = new File(uri.getPath()), to = new File(from.getParentFile(), name);
                if (to.exists()) {
                  input.setError("A file with this name already exists");
                  return;
                }
                if (from.renameTo(to)) {
                  invoke(library(), "reload");
                  d.dismiss();
                  toast("File renamed");
                  refresh();
                } else input.setError("VLC could not rename this file");
              },
              true));
    }

    void delete(Object media) {
      LinearLayout c = column();
      c.addView(text("Delete “" + title(media) + "” from this device?", 18, ink));
      gap(c, 12);
      c.addView(text("The file will be permanently removed.", 13, muted));
      Dialog d = sheet("Delete media", c);
      gap(c, 18);
      c.addView(
          button(
              "Delete file",
              () -> {
                Object result =
                    invoke(
                        singleton("org.videolan.vlc.util.FileUtils", "INSTANCE"),
                        "deleteFile",
                        invoke(media, "getUri"));
                if (Boolean.TRUE.equals(result)) {
                  invoke(library(), "reload");
                  d.dismiss();
                  toast("File deleted");
                  refresh();
                } else toast("VLC could not delete this file");
              },
              true));
      gap(c, 10);
      c.addView(button("Cancel", d::dismiss, false));
    }

    void share(Object media) {
      Uri uri = (Uri) invoke(media, "getUri");
      if (uri == null) return;
      Uri safe =
          (Uri) invoke(singleton("org.videolan.vlc.util.FileUtils", "INSTANCE"), "getUri", uri);
      if (safe == null) safe = uri;
      try {
        a.startActivity(
            Intent.createChooser(
                new Intent(Intent.ACTION_SEND)
                    .setType(number(invoke(media, "getType")) == 0 ? "video/*" : "audio/*")
                    .putExtra(Intent.EXTRA_STREAM, safe)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Share media"));
      } catch (Exception e) {
        toast("This file cannot be shared from its current location");
      }
    }

    void actions(Object media) {
      LinearLayout options = column();
      Dialog d = sheet("Media actions", options);
      options.addView(text(title(media), 20, ink));
      gap(options, 14);
      destination(
          options,
          "Play from beginning",
          format(media),
          "play",
          () -> {
            d.dismiss();
            invoke(media, "setTime", 0L);
            play(media);
            handler.postDelayed(() -> {
              Object current = invoke(service(), "getCurrentMediaWrapper");
              if (str(invoke(media, "getUri")).equals(str(invoke(current, "getUri")))) seekTo(0L);
            }, 800);
          });
      if (number(invoke(media, "getType")) == 0) {
        destination(options, isWatched(media) ? "Mark as unwatched" : "Mark as watched",
            "Update Continue watching and library filters", "video", () -> {
              d.dismiss(); setWatched(media, !isWatched(media));
            });
        destination(options, "Correct poster match", "Search artwork with a different title",
            "video", () -> { d.dismiss(); correctPoster(media); });
      }
      destination(
          options,
          "Play next",
          "Add after current item",
          "list",
          () -> {
            invoke(
                singleton("org.videolan.vlc.media.MediaUtils", "INSTANCE"), "insertNext", a, media);
            d.dismiss();
          });
      destination(
          options,
          "Add to playlist",
          "",
          "plus",
          () -> {
            d.dismiss();
            addToPlaylist(media);
          });
      destination(
          options,
          "Media information",
          "",
          "video",
          () -> {
            d.dismiss();
            goItem("info", media);
          });
      destination(
          options,
          "Share",
          "Send this media file",
          "network",
          () -> {
            d.dismiss();
            share(media);
          });
      destination(
          options,
          "Add to collection",
          "Group related videos",
          "video",
          () -> {
            d.dismiss();
            groupDialog(new Object[] {media});
          });
      if (!prefs.getBoolean("safe_mode", false)) {
        destination(
            options,
            "Rename",
            "Change the file name",
            null,
            () -> {
              d.dismiss();
              rename(media);
            });
        destination(
            options,
            "Delete",
            "Remove the file from this device",
            null,
            () -> {
              d.dismiss();
              delete(media);
            });
      }
    }

    String watchedKey(Object media) {
      return "premiere_watched_" + str(invoke(media, "getUri"));
    }

    boolean isWatched(Object media) {
      String key = watchedKey(media);
      if (prefs.contains(key)) return prefs.getBoolean(key, false);
      long length = number(invoke(media, "getLength"));
      return number(invoke(media, "getSeen")) > 0
          || (length > 0 && number(invoke(media, "getTime")) >= length * .95);
    }

    void setWatched(Object media, boolean watched) {
      prefs.edit().putBoolean(watchedKey(media), watched).apply();
      toast(watched ? "Marked as watched" : "Marked as unwatched");
      refresh();
    }

    void correctPoster(Object media) {
      String key = "premiere_artwork_override_" + str(invoke(media, "getUri"));
      LinearLayout content = column();
      content.addView(text("Use an exact film or TV title to improve poster matching. "
          + "Requires online artwork and a TMDB token.", 13, muted));
      EditText input = field(content, "Movie or series title",
          prefs.getString(key, ""));
      Dialog dialog = sheet("Correct poster match", content);
      gap(content, 16);
      content.addView(button("Search with this title", () -> {
        String title = input.getText().toString().trim();
        if (title.isEmpty()) { input.setError("Enter a movie or TV title"); return; }
        prefs.edit().putString(key, title).apply();
        Artwork.invalidateMemory();
        Artwork.clearMisses(a, () -> { if (!a.isDestroyed()) refresh(); });
        dialog.dismiss();
        toast("Poster search updated");
      }, true));
      content.addView(button("Restore automatic matching", () -> {
        prefs.edit().remove(key).apply();
        Artwork.invalidateMemory();
        Artwork.clearMisses(a, () -> { if (!a.isDestroyed()) refresh(); });
        dialog.dismiss();
      }, false));
    }

    Map<String, List<Object>> seriesGroups() {
      Map<String, List<Object>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      for (Object media : videos) {
        SeriesUX.Episode episode =
            SeriesUX.from(str(invoke(media, "getFileName")));
        if (episode == null) continue;
        String key = episode.group();
        List<Object> entries = result.get(key);
        if (entries == null) {
          entries = new ArrayList<>();
          result.put(key, entries);
        }
        entries.add(media);
      }
      for (List<Object> tracks : result.values()) {
        tracks.sort((left, right) -> {
          SeriesUX.Episode a = SeriesUX.from(str(invoke(left, "getFileName")));
          SeriesUX.Episode b = SeriesUX.from(str(invoke(right, "getFileName")));
          return Integer.compare(a == null ? 0 : a.episode, b == null ? 0 : b.episode);
        });
      }
      return result;
    }

    void seriesList() {
      Map<String, List<Object>> groups = seriesGroups();
      heading("TV series", groups.size() + " seasons · On this device");
      for (Map.Entry<String, List<Object>> group : groups.entrySet()) {
        String name = group.getKey();
        destination(body, name, group.getValue().size() + " episodes",
            "video", () -> goItem("series-season", name));
      }
      if (groups.isEmpty()) empty("No TV episodes detected.",
          "Files named Show.S01E01 or similar will be grouped automatically.");
    }

    void seriesSeason() {
      String group = str(item);
      List<Object> episodes = seriesGroups().get(group);
      if (episodes == null) {
        heading(group, "This season is no longer available.");
        return;
      }
      heading(group, episodes.size() + " episodes");
      Object[] media = episodes.toArray();
      body.addView(button("▶ Play season in order", () -> playList(media, false), true));
      gap(body, 16);
      for (Object episode : episodes) mediaRow(body, episode, () -> play(episode), true);
    }

    Object[] filteredVideos() {
      ArrayList<Object> result = new ArrayList<>();
      String filter = prefs.getString("premiere_video_filter", "all");
      for (Object media : videos) {
        long time = number(invoke(media, "getTime"));
        long length = number(invoke(media, "getLength"));
        String stateKey = watchedKey(media);
        if (prefs.contains(stateKey) && prefs.getBoolean(stateKey, false)) {
          if (!PlaybackUX.matchesVideoFilter(filter, time, length, 1)) continue;
        } else if (prefs.contains(stateKey)) {
          if (!PlaybackUX.matchesVideoFilter(filter, 0, length, 0)) continue;
        } else if (!PlaybackUX.matchesVideoFilter(filter, time, length,
            number(invoke(media, "getSeen")))) continue;
        result.add(media);
      }
      String sort = prefs.getString("premiere_video_sort", "recent");
      if (sort.equals("name"))
        result.sort((a, b) -> title(a).compareToIgnoreCase(title(b)));
      else if (sort.equals("duration"))
        result.sort((a, b) -> Long.compare(number(invoke(a, "getLength")),
            number(invoke(b, "getLength"))));
      else if (sort.equals("longest"))
        result.sort((a, b) -> Long.compare(number(invoke(b, "getLength")),
            number(invoke(a, "getLength"))));
      return result.toArray();
    }

    void videoSort(String sort) {
      prefs.edit().putString("premiere_video_sort", sort).apply();
      videoLimit = 80;
      go("videos");
    }

    void videoFilter(String filter) {
      prefs.edit().putString("premiere_video_filter", filter).apply();
      videoLimit = 80;
      go("videos");
    }

    void display() {
      LinearLayout options = column();
      Dialog d = sheet("Display & sort", options);
      destination(options, prefs.getBoolean("premiere_video_list",false)?"Use poster grid":"Use compact list",
          "Choose how videos are displayed", "video", ()->{
            prefs.edit().putBoolean("premiere_video_list",!prefs.getBoolean("premiere_video_list",false)).apply();
            d.dismiss();go("videos");
          });
      destination(
          options,
          "All videos",
          "Posters and media details",
          "list",
          () -> {
            d.dismiss();
            go("videos");
          });
      destination(options, "TV series", "Automatically grouped seasons",
          "video", () -> { d.dismiss(); go("series"); });
      destination(
          options,
          "Collections",
          "Related videos together",
          "video",
          () -> {
            d.dismiss();
            go("groups");
          });
      destination(
          options,
          "Select media",
          "Play, collect or share multiple videos",
          "list",
          () -> {
            selected.clear();
            d.dismiss();
            go("selection");
          });
      String selectedSort = prefs.getString("premiere_video_sort", "recent");
      String[] sorts = {"recent", "name", "duration", "longest"};
      String[] sortNames = {"Recently added", "Title A–Z", "Shortest first", "Longest first"};
      for (int i = 0; i < sorts.length; i++) {
        final String value = sorts[i];
        destination(options, "Sort · " + sortNames[i],
            value.equals(selectedSort) ? "Selected" : "", "list", () -> {
              d.dismiss();
              videoSort(value);
            });
      }
      String selectedFilter = prefs.getString("premiere_video_filter", "all");
      String[] filters = {"all", "unwatched", "in-progress", "completed"};
      String[] filterNames = {"All videos", "Unwatched", "Continue watching", "Completed"};
      for (int i = 0; i < filters.length; i++) {
        final String value = filters[i];
        destination(options, "Filter · " + filterNames[i],
            value.equals(selectedFilter) ? "Selected" : "", "video", () -> {
              d.dismiss();
              videoFilter(value);
            });
      }
      destination(
          options,
          "Refresh library",
          "Scan for new media",
          "folder",
          () -> {
            invoke(library(), "reload");
            d.dismiss();
            toast("Refreshing your library");
          });
    }

    EditText field(LinearLayout parent, String label, String value) {
      gap(parent, 14);
      parent.addView(text(label, 12, muted));
      gap(parent, 8);
      EditText input = new EditText(a);
      input.setText(value);
      input.setTextColor(ink);
      input.setHintTextColor(muted);
      input.setTextSize(14);
      input.setSingleLine(true);
      input.setPadding(dp(12), dp(12), dp(12), dp(12));
      input.setBackground(surface(panel, 6, true));
      input.setContentDescription(label);
      parent.addView(input, new LinearLayout.LayoutParams(-1, dp(48)));
      return input;
    }

    Dialog sheet(String title, LinearLayout content) {
      Dialog d = new Dialog(a);
      LinearLayout shell = column();
      shell.setPadding(dp(20), dp(18), dp(20), dp(24));
      shell.setBackground(surface(panel, 18, true));
      LinearLayout head = row();
      head.addView(text(title, 22, ink), new LinearLayout.LayoutParams(0, -2, 1));
      head.addView(
          icon("close", "Close " + title, d::dismiss),
          new LinearLayout.LayoutParams(dp(40), dp(36)));
      shell.addView(head);
      gap(shell, 18);
      ScrollView sc =
          new ScrollView(a) {
            @Override
            protected void onMeasure(int width, int height) {
              int cap = a.getResources().getDisplayMetrics().heightPixels - dp(210);
              super.onMeasure(
                  width, View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST));
            }
          };
      sc.addView(content);
      shell.addView(sc, new LinearLayout.LayoutParams(-1, -2));
      d.setContentView(shell);
      Window w = d.getWindow();
      if (w != null) {
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setGravity(Gravity.BOTTOM);
        w.setLayout(-1, -2);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
      }
      d.show();
      if (w != null) w.setLayout(-1, -2);
      shell
          .getViewTreeObserver()
          .addOnGlobalLayoutListener(
              () -> {
                Rect visible = new Rect();
                shell.getWindowVisibleDisplayFrame(visible);
                int[] pos = new int[2];
                shell.getLocationOnScreen(pos);
                int bottom = dp(24) + Math.max(0, pos[1] + shell.getHeight() - visible.bottom);
                if (shell.getPaddingBottom() != bottom)
                  shell.setPadding(dp(20), dp(18), dp(20), bottom);
              });
      return d;
    }

    void empty(String title, String copy) {
      gap(body, 36);
      body.addView(text(title, 24, ink));
      gap(body, 14);
      TextView t = text(copy, 14, muted);
      t.setLineSpacing(dp(4), 1);
      body.addView(t);
      gap(body, 28);
    }

    void toast(String message) {
      Toast.makeText(a, message, Toast.LENGTH_SHORT).show();
    }

    void legacy(String name) {
      try {
        a.startActivity(new Intent(a, Class.forName(name)));
      } catch (Exception e) {
        toast("This feature is unavailable");
      }
    }
  }

  static final class Icon extends View {
    final String kind;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final int color;

    Icon(Context c, String kind, int color) {
      super(c);
      this.kind = kind;
      this.color = color;
    }

    protected void onDraw(Canvas c) {
      super.onDraw(c);
      c.save();
      float size = Math.min(getWidth(), getHeight()) * .65f;
      c.translate((getWidth() - size) / 2, (getHeight() - size) / 2);
      c.scale(size / 24, size / 24);
      p.setColor(color);
      p.setStrokeWidth(1.6f);
      p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeJoin(Paint.Join.ROUND);
      p.setStyle(Paint.Style.STROKE);
      Path path = new Path();
      switch (kind) {
        case "cone":
          p.setStyle(Paint.Style.FILL);
          path.moveTo(12, 2);
          path.lineTo(23, 22);
          path.lineTo(1, 22);
          path.close();
          c.drawPath(path, p);
          break;
        case "search":
          c.drawCircle(10, 10, 6, p);
          c.drawLine(15, 15, 21, 21, p);
          break;
        case "video":
          c.drawRoundRect(2, 4, 22, 20, 2, 2, p);
          path.moveTo(10, 8);
          path.lineTo(16, 12);
          path.lineTo(10, 16);
          path.close();
          c.drawPath(path, p);
          break;
        case "play":
          path.moveTo(8, 4);
          path.lineTo(20, 12);
          path.lineTo(8, 20);
          path.close();
          c.drawPath(path, p);
          break;
        case "audio":
          c.drawLine(9, 18, 9, 5, p);
          c.drawLine(9, 5, 20, 3, p);
          c.drawLine(20, 3, 20, 16, p);
          c.drawLine(9, 9, 20, 7, p);
          c.drawOval(3, 16, 9, 21, p);
          c.drawOval(14, 14, 20, 19, p);
          break;
        case "folder":
          path.moveTo(2, 6);
          path.lineTo(10, 6);
          path.lineTo(12, 9);
          path.lineTo(22, 9);
          path.lineTo(22, 21);
          path.lineTo(2, 21);
          path.close();
          c.drawPath(path, p);
          break;
        case "list":
          for (int y : new int[] {6, 12, 18}) {
            c.drawLine(7, y, 22, y, p);
            c.drawPoint(2, y, p);
          }
          break;
        case "back":
        case "next":
          boolean back = kind.equals("back");
          path.moveTo(back ? 15 : 9, 5);
          path.lineTo(back ? 8 : 16, 12);
          path.lineTo(back ? 15 : 9, 19);
          c.drawPath(path, p);
          break;
        case "plus":
          c.drawLine(12, 4, 12, 20, p);
          c.drawLine(4, 12, 20, 12, p);
          break;
        case "close":
          c.drawLine(5, 5, 19, 19, p);
          c.drawLine(5, 19, 19, 5, p);
          break;
        case "lock":
          c.drawRoundRect(5, 10, 19, 22, 2, 2, p);
          c.drawArc(8, 2, 16, 15, 180, 180, false, p);
          break;
        case "bookmark":
          path.moveTo(6, 3);
          path.lineTo(18, 3);
          path.lineTo(18, 22);
          path.lineTo(12, 17);
          path.lineTo(6, 22);
          path.close();
          c.drawPath(path, p);
          break;
        case "cast":
          c.drawLine(3, 8, 3, 3, p);
          c.drawLine(3, 3, 22, 3, p);
          c.drawLine(22, 3, 22, 18, p);
          c.drawLine(22, 18, 11, 18, p);
          c.drawArc(-4, 8, 16, 28, -90, 90, false, p);
          c.drawArc(-1, 13, 9, 23, -90, 90, false, p);
          c.drawCircle(3, 20, 1, p);
          break;
        case "network":
          c.drawRect(8, 2, 16, 8, p);
          c.drawLine(12, 8, 12, 14, p);
          c.drawLine(4, 14, 20, 14, p);
          c.drawLine(4, 14, 4, 17, p);
          c.drawLine(20, 14, 20, 17, p);
          c.drawRect(1, 17, 7, 22, p);
          c.drawRect(17, 17, 23, 22, p);
          break;
        case "shuffle":
          c.drawLine(3, 5, 21, 19, p);
          c.drawLine(3, 19, 21, 5, p);
          c.drawLine(17, 5, 21, 5, p);
          c.drawLine(21, 5, 21, 9, p);
          c.drawLine(17, 19, 21, 19, p);
          c.drawLine(21, 19, 21, 15, p);
          break;
        default:
          p.setStyle(Paint.Style.FILL);
          for (int x : new int[] {4, 12, 20}) c.drawCircle(x, 12, 1.4f, p);
      }
      c.restore();
    }
  }
}
