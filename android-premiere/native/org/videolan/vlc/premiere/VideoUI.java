package org.videolan.vlc.premiere;

import static org.videolan.vlc.premiere.Premiere.*;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Premiere controls over VLC's original SurfaceView and PlaybackService. */
final class VideoUI extends Premiere.UI {
  LinearLayout top, bottom;
  TextView titleView, center, clock, sizeLabel;
  boolean visible = true, locked = false;
  boolean defaultScaleApplied;
  String lastScaledMedia = "";
  float downX, downY, initialBrightness;
  float initialSpan, initialScale;
  int initialVolume;
  long initialTime;
  boolean seekingGesture, scalingGesture, fadeScheduled;
  final GestureDetector tapDetector;
  AudioManagerHolder volume;
  final Runnable fade = () -> show(false);

  VideoUI(Activity a) {
    super(a);
    screen = "video-player";
    tapDetector =
        new GestureDetector(
            a,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onDown(MotionEvent event) {
                return true;
              }

              @Override
              public boolean onSingleTapConfirmed(MotionEvent event) {
                if (!locked) show(!visible);
                return true;
              }

              @Override
              public boolean onDoubleTap(MotionEvent event) {
                if (locked) return true;
                long delta = event.getX() < root.getWidth() / 2f ? -120000L : 120000L;
                jump(delta);
                indicator(delta < 0 ? "−2:00" : "+2:00");
                return true;
              }
            });
  }

  @Override
  void install(Bundle b) {
    ViewGroup content = a.findViewById(android.R.id.content);
    root = new FrameLayout(a);
    content.addView(root, new ViewGroup.LayoutParams(-1, -1));
    fitWindow(root);
    volume = new AudioManagerHolder(a);
    render();
    root.post(this::fillVideo);
    tickVideo();
  }

  View nativeView(String id) {
    int r = a.getResources().getIdentifier(id, "id", a.getPackageName());
    return r == 0 ? null : a.findViewById(r);
  }

  void suppress() {
    for (String id :
        new String[] {
          "player_ui_container",
          "hud_background",
          "hud_right_background",
          "video_playlist_container"
        }) {
      View v = nativeView(id);
      if (v != null) v.setVisibility(View.GONE);
    }
  }

  @Override
  void refresh() {
    if (root != null) render();
  }

  @Override
  boolean back() {
    if (locked) {
      locked = false;
      render();
      return true;
    }
    return false;
  }

  @Override
  void render() {
    palette();
    ink = Color.rgb(231, 221, 204);
    muted = Color.rgb(170, 161, 150);
    panel = Color.rgb(33, 30, 27);
    line = Color.rgb(58, 52, 47);
    root.removeAllViews();
    root.setBackgroundColor(Color.TRANSPARENT);
    suppress();
    root.setOnTouchListener((v, event) -> gesture(event));
    if (locked) {
      TextView unlock =
          button(
              "Unlock controls",
              () -> {
                locked = false;
                invoke(a, "setLocked", false);
                render();
              },
              false);
      FrameLayout.LayoutParams p =
          new FrameLayout.LayoutParams(dp(150), dp(44), Gravity.RIGHT | Gravity.BOTTOM);
      p.rightMargin = dp(20);
      p.bottomMargin = dp(24);
      root.addView(unlock, p);
      return;
    }
    top = row();
    top.setPadding(dp(16), dp(16), dp(16), dp(40));
    top.setBackground(
        new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xe6171615, 0x00171615}));
    top.addView(
        icon("back", "Back to library", a::finish), new LinearLayout.LayoutParams(dp(42), dp(48)));
    LinearLayout copy = column();
    titleView = text(title(invoke(service(), "getCurrentMediaWrapper")), 17, ink);
    titleView.setMaxLines(1);
    copy.addView(titleView);
    gap(copy, 6);
    copy.addView(text("VLC · PREMIERE", 10, muted));
    top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
    top.addView(
        icon("cast", "Cast to a device", this::cast),
        new LinearLayout.LayoutParams(dp(44), dp(48)));
    top.addView(
        icon("more", "Playback options", this::tools),
        new LinearLayout.LayoutParams(dp(44), dp(48)));
    root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
    LinearLayout transport = row();
    transport.setGravity(Gravity.CENTER);
    TextView rewind = button("−10", () -> jump(-10000), false),
        forward = button("+10", () -> jump(10000), false);
    rewind.setBackgroundColor(Color.TRANSPARENT);
    forward.setBackgroundColor(Color.TRANSPARENT);
    transport.addView(rewind, new LinearLayout.LayoutParams(dp(68), dp(64)));
    center =
        button(
            Boolean.TRUE.equals(invoke(service(), "isPlaying")) ? "Ⅱ" : "▶",
            () -> {
              Object s = service();
              invoke(s, Boolean.TRUE.equals(invoke(s, "isPlaying")) ? "pause" : "play");
              show(true);
            },
            true);
    center.setTextSize(27);
    center.setBackground(surface(orange, 40, false));
    transport.addView(center, new LinearLayout.LayoutParams(dp(60), dp(60)));
    transport.addView(forward, new LinearLayout.LayoutParams(dp(68), dp(64)));
    transport.setTag("transport");
    root.addView(transport, new FrameLayout.LayoutParams(-2, dp(70), Gravity.CENTER));
    bottom = column();
    bottom.setPadding(dp(20), dp(36), dp(20), dp(20));
    bottom.setBackground(
        new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x00171615, 0xf5171615}));
    seek = new SeekBar(a);
    seek.setMax((int) Math.min(Integer.MAX_VALUE, number(invoke(service(), "getLength"))));
    seek.setProgress((int) number(invoke(service(), "getTime")));
    seek.setProgressTintList(android.content.res.ColorStateList.valueOf(orange));
    seek.setThumbTintList(android.content.res.ColorStateList.valueOf(orange));
    seek.setContentDescription("Video position");
    seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar b, int p, boolean u) {
            if (u) clock.setText(time(p) + " / " + time(number(invoke(service(), "getLength"))));
          }

          public void onStartTrackingTouch(SeekBar b) {
            seeking = true;
            handler.removeCallbacks(fade);
          }

          public void onStopTrackingTouch(SeekBar b) {
            seekTo((long) b.getProgress());
            seeking = false;
            show(true);
          }
        });
    bottom.addView(seek, new LinearLayout.LayoutParams(-1, dp(36)));
    LinearLayout timeline = row();
    clock =
        text(
            time(number(invoke(service(), "getTime")))
                + " / "
                + time(number(invoke(service(), "getLength"))),
            11,
            ink);
    timeline.addView(clock, new LinearLayout.LayoutParams(0, -2, 1));
    sizeLabel = text(prefs.getString("premiere_video_size", "Best fit"), 11, muted);
    TextView size = sizeLabel;
    size.setOnClickListener(v -> sizes());
    size.setPadding(dp(8), dp(8), 0, dp(8));
    timeline.addView(size);
    bottom.addView(timeline);
    gap(bottom, 8);
    LinearLayout controls = row();
    control(controls, "Audio & subtitles", this::tracksSheet);
    control(controls, "Speed", this::speed);
    controls.addView(
        icon("bookmark", "Video bookmarks", this::bookmarkSheet),
        new LinearLayout.LayoutParams(dp(42), dp(42)));
    controls.addView(
        icon(
            "lock",
            "Lock controls",
            () -> {
              locked = true;
              invoke(a, "setLocked", true);
              render();
            }),
        new LinearLayout.LayoutParams(dp(42), dp(42)));
    controls.addView(
        icon("more", "Video tools", this::tools), new LinearLayout.LayoutParams(dp(42), dp(42)));
    bottom.addView(controls);
    root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
    show(true);
  }

  void control(LinearLayout r, String label, Runnable action) {
    TextView b = text(label, 11, ink);
    b.setGravity(Gravity.CENTER);
    b.setMinHeight(dp(48));
    b.setFocusable(true);
    b.setContentDescription(label);
    b.setOnClickListener(v -> action.run());
    r.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
  }

  void show(boolean on) {
    visible = on;
    handler.removeCallbacks(fade);
    if (top != null) top.setVisibility(on ? View.VISIBLE : View.GONE);
    if (bottom != null) bottom.setVisibility(on ? View.VISIBLE : View.GONE);
    View t = root.findViewWithTag("transport");
    if (t != null) t.setVisibility(on ? View.VISIBLE : View.GONE);
    fadeScheduled = false;
    if (on && Boolean.TRUE.equals(invoke(service(), "isPlaying"))) {
      fadeScheduled = true;
      handler.postDelayed(fade, 1000);
    }
  }

  void tickVideo() {
    handler.postDelayed(
        new Runnable() {
          public void run() {
            if (a.isDestroyed() || a.isFinishing()) return;
            suppress();
            Object s = service();
            Object media = invoke(s, "getCurrentMediaWrapper");
            String mediaKey = str(invoke(media, "getUri")) + ":" + str(invoke(media, "getId"));
            if (!mediaKey.equals(lastScaledMedia)) {
              lastScaledMedia = mediaKey;
              defaultScaleApplied = false;
            }
            if (!defaultScaleApplied) fillVideo();
            if (titleView != null) update(titleView, title(media));
            if (seek != null && !seeking) {
              seek.setMax((int) Math.min(Integer.MAX_VALUE, number(invoke(s, "getLength"))));
              seek.setProgress((int) number(invoke(s, "getTime")));
              update(
                  clock,
                  time(number(invoke(s, "getTime")))
                      + " / "
                      + time(number(invoke(s, "getLength"))));
              update(center, Boolean.TRUE.equals(invoke(s, "isPlaying")) ? "Ⅱ" : "▶");
            }
            // The service may start after the first render; still auto-hide on playback.
            if (visible && !fadeScheduled && !seeking && !seekingGesture && !scalingGesture
                && Boolean.TRUE.equals(invoke(s, "isPlaying"))) show(true);
            handler.postDelayed(this, 500);
          }
        },
        500);
  }

  void jump(long delta) {
    Object playback = service();
    seekTo(VideoSeek.target(
        number(invoke(playback, "getTime")), number(invoke(playback, "getLength")), delta));
    show(true);
  }

  boolean gesture(MotionEvent e) {
    if (locked) return true;
    tapDetector.onTouchEvent(e);
    int action = e.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      downX = e.getX();
      downY = e.getY();
      initialTime = number(invoke(service(), "getTime"));
      initialVolume = volume.get();
      initialBrightness = a.getWindow().getAttributes().screenBrightness;
      if (initialBrightness < 0) initialBrightness = .5f;
      seekingGesture = false;
      scalingGesture = false;
      handler.removeCallbacks(fade);
      fadeScheduled = false;
      return true;
    }
    if (action == MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount() >= 2) {
      scalingGesture = true;
      initialSpan = span(e);
      Object player = invoke(service(), "getMediaplayer");
      float current = (float) number(invoke(player, "getScale"));
      initialScale = current > 0 ? current : 1f;
      handler.removeCallbacks(fade);
      return true;
    }
    if (action == MotionEvent.ACTION_POINTER_UP) return true;
    if (action == MotionEvent.ACTION_MOVE) {
      if (scalingGesture && e.getPointerCount() >= 2) {
        float ratio = span(e) / Math.max(1f, initialSpan);
        invoke(service(), "setVideoAspectRatio", (Object) null);
        invoke(service(), "setVideoScale", Math.max(.25f, Math.min(4f, initialScale * ratio)));
        defaultScaleApplied = true;
        return true;
      }
      if (scalingGesture) return true;
      float dx = e.getX() - downX, dy = downY - e.getY();
      if (prefs.getBoolean("enable_swipe_seek", true)
          && Math.abs(dx) > dp(30)
          && Math.abs(dx) > Math.abs(dy) * 1.4) {
        if (!seekingGesture) {
          seekingGesture = true;
          show(false);
        }
        seekTo(VideoSeek.target(initialTime, number(invoke(service(), "getLength")),
            (long) (dx / Math.max(1, root.getWidth()) * 120000)));
        long position = number(invoke(service(), "getTime"));
        indicator("Seek · " + time(position) + " / " + time(number(invoke(service(), "getLength"))));
      } else if (Math.abs(dy) > dp(25)) {
        float change = dy / root.getHeight();
        if (downX < root.getWidth() / 2f && prefs.getBoolean("enable_brightness_gesture", true)) {
          WindowManager.LayoutParams p = a.getWindow().getAttributes();
          p.screenBrightness = Math.max(.01f, Math.min(1, initialBrightness + change * 2));
          a.getWindow().setAttributes(p);
          indicator("Brightness · " + Math.round(p.screenBrightness * 100) + "%");
        } else if (downX >= root.getWidth() / 2f
            && prefs.getBoolean("enable_volume_gesture", true)) {
          volume.set(initialVolume + (int) (change * volume.max() * 2));
          indicator("Volume · " + Math.round(volume.get() * 100f / volume.max()) + "%");
        }
      }
      return true;
    }
    if (action == MotionEvent.ACTION_UP) {
      if (seekingGesture) {
        seekingGesture = false;
        show(false);
        return true;
      }
      if (scalingGesture) {
        scalingGesture = false;
        show(false);
        return true;
      }
      // Single taps are delayed until Android rules out a double tap.
      show(visible);
      return true;
    }
    if (action == MotionEvent.ACTION_CANCEL) show(visible);
    return true;
  }

  float span(MotionEvent e) {
    float dx = e.getX(0) - e.getX(1), dy = e.getY(0) - e.getY(1);
    return (float) Math.hypot(dx, dy);
  }

  void fillVideo() {
    Object s = service();
    if (s == null || invoke(s, "getMediaplayer") == null) return;
    invoke(service(), "setVideoAspectRatio", (Object) null);
    invoke(service(), "setVideoScale", 0f);
    String mode = prefs.getString("premiere_video_size", "Best fit");
    if ("16:9".equals(mode) || "4:3".equals(mode)) {
      invoke(service(), "setVideoAspectRatio", mode);
      defaultScaleApplied = true;
    } else if ("Original size".equals(mode)) {
      invoke(service(), "setVideoScale", 1f);
      defaultScaleApplied = true;
    } else {
      defaultScaleApplied = surfaceScale("Fill screen".equals(mode)
          ? "SURFACE_FIT_SCREEN" : "SURFACE_BEST_FIT");
    }
  }

  boolean surfaceScale(String name) {
    Object mode = invoke(clazz("org.videolan.libvlc.MediaPlayer$ScaleType"), "valueOf", name);
    Object resize = invoke(a, "getResizeDelegate");
    invoke(resize, "setVideoScale", mode);
    return resize != null && mode != null;
  }

  void indicator(String label) {
    View previous = root.findViewWithTag("gesture");
    if (previous != null) root.removeView(previous);
    TextView t = text(label, 14, ink);
    t.setTag("gesture");
    t.setPadding(dp(20), dp(16), dp(20), dp(16));
    t.setBackground(surface(panel, 10, true));
    FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
    p.topMargin = dp(100);
    root.addView(t, p);
    handler.postDelayed(() -> root.removeView(t), 1000);
  }

  void tools() {
    LinearLayout c = column();
    Dialog d = sheet("Playback options", c);
    String[] labels = {
      "Audio & subtitles",
      "Playback speed",
      "Synchronization",
      "Jump to time",
      "Chapters",
      "Bookmarks",
      "Sleep timer",
      "Equalizer",
      "Cast to a device",
      "Picture in picture",
      "A–B repeat",
      "Video size",
      "Playback information",
      "Take screenshot"
    };
    Runnable[] actions = {
      this::tracksSheet,
      this::speed,
      this::sync,
      this::jumpTime,
      this::chapters,
      this::bookmarkSheet,
      this::sleepTimer,
      () -> {
        UI helper = new UI(a);
        helper.palette();
        helper.equalizerDialog();
      },
      this::cast,
      this::popup,
      this::repeat,
      this::sizes,
      this::info,
      () -> invoke(a, "takeScreenshot")
    };
    for (int i = 0; i < labels.length; i += 3) {
      LinearLayout row = row();
      row.setGravity(Gravity.TOP);
      for (int j = i; j < Math.min(labels.length, i + 3); j++) {
        Runnable act = actions[j];
        LinearLayout tile = column();
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(4), dp(10), dp(4), dp(10));
        Premiere.Icon glyph =
            new Premiere.Icon(
                a,
                new String[] {
                      "audio",
                      "play",
                      "audio",
                      "next",
                      "list",
                      "bookmark",
                      "lock",
                      "audio",
                      "network",
                      "video",
                      "video",
                      "video",
                      "more",
                      "video"
                    }
                    [j],
                orange);
        tile.addView(glyph, new LinearLayout.LayoutParams(dp(32), dp(32)));
        gap(tile, 10);
        TextView label = text(labels[j], 10, ink);
        label.setGravity(Gravity.CENTER);
        tile.addView(label);
        tile.setContentDescription(labels[j]);
        tile.setOnClickListener(
            v -> {
              d.dismiss();
              act.run();
            });
        row.addView(tile, new LinearLayout.LayoutParams(0, dp(100), 1));
      }
      c.addView(row);
      gap(c, 8);
    }
  }

  void tracksSheet() {
    LinearLayout c = column();
    Dialog d = sheet("Audio & subtitles", c);
    c.addView(label("Audio"));
    for (Object track : array(invoke(service(), "getAudioTracks"))) {
      String id = str(invoke(track, "getId"));
      destination(
          c,
          str(invoke(track, "getName")),
          id.equals(str(invoke(service(), "getAudioTrack"))) ? "Selected" : "",
          null,
          () -> {
            invoke(service(), "setAudioTrack", id);
            d.dismiss();
          });
    }
    c.addView(label("Subtitles"));
    destination(
        c,
        "Off",
        "Disable subtitles",
        null,
        () -> {
          invoke(service(), "setSpuTrack", "-1");
          d.dismiss();
        });
    for (Object track : array(invoke(service(), "getSpuTracks"))) {
      String id = str(invoke(track, "getId"));
      destination(
          c,
          str(invoke(track, "getName")),
          id.equals(str(invoke(service(), "getSpuTrack"))) ? "Selected" : "",
          null,
          () -> {
            invoke(service(), "setSpuTrack", id);
            d.dismiss();
          });
    }
    destination(
        c,
        "Choose subtitle file",
        "Open SRT, VTT, ASS or SSA",
        null,
        () -> {
          d.dismiss();
          a.startActivityForResult(
              new Intent(Intent.ACTION_OPEN_DOCUMENT)
                  .setType("*/*")
                  .addCategory(Intent.CATEGORY_OPENABLE),
              771);
        });
    destination(
        c,
        "Download subtitles",
        "Search OpenSubtitles",
        null,
        () -> {
          d.dismiss();
          downloadSubtitles();
        });
  }

  void downloadSubtitles() {
    try {
      Object delegate = invoke(a, "getOverlayDelegate");
      java.lang.reflect.Method method = delegate.getClass().getDeclaredMethod("downloadSubtitles");
      method.setAccessible(true);
      method.invoke(delegate);
    } catch (Exception e) {
      android.util.Log.e("Premiere", "Subtitle download", e);
      toast("Subtitle search could not be opened");
    }
  }

  void speed() {
    LinearLayout c = column();
    Dialog d = sheet("Playback speed", c);
    TextView value =
        text(
            String.format(
                Locale.ROOT, "%.2f×", ((Number) invoke(service(), "getRate")).floatValue()),
            32,
            ink);
    value.setGravity(Gravity.CENTER);
    c.addView(value);
    SeekBar bar = new SeekBar(a);
    bar.setMax(175);
    bar.setProgress(Math.round(((Number) invoke(service(), "getRate")).floatValue() * 100) - 25);
    bar.setProgressTintList(android.content.res.ColorStateList.valueOf(orange));
    bar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar b, int p, boolean u) {
            float rate = (p + 25) / 100f;
            value.setText(String.format(Locale.ROOT, "%.2f×", rate));
            if (u) invoke(service(), "setRate", rate, true);
          }

          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {}
        });
    c.addView(bar);
    gap(c, 12);
    for (float rate : new float[] {.5f, .75f, 1f, 1.25f, 1.5f, 2f})
      destination(
          c,
          rate + "×",
          rate == 1 ? "Normal speed" : "",
          null,
          () -> {
            invoke(service(), "setRate", rate, true);
            d.dismiss();
          });
  }

  void sync() {
    LinearLayout c = column();
    sheet("Synchronization", c);
    for (String kind : new String[] {"Audio", "Subtitles"}) {
      c.addView(label(kind + " delay"));
      String getter = kind.equals("Audio") ? "getAudioDelay" : "getSpuDelay",
          setter = kind.equals("Audio") ? "setAudioDelay" : "setSpuDelay";
      TextView value = text(number(invoke(service(), getter)) / 1000 + " ms", 28, ink);
      value.setGravity(Gravity.CENTER);
      c.addView(value);
      paired(
          c,
          "−50 ms",
          () -> {
            invoke(service(), setter, number(invoke(service(), getter)) - 50000);
            value.setText(number(invoke(service(), getter)) / 1000 + " ms");
          },
          "+50 ms",
          () -> {
            invoke(service(), setter, number(invoke(service(), getter)) + 50000);
            value.setText(number(invoke(service(), getter)) / 1000 + " ms");
          });
      gap(c, 12);
    }
    c.addView(
        button(
            "Reset delays",
            () -> {
              invoke(service(), "setAudioDelay", 0L);
              invoke(service(), "setSpuDelay", 0L);
            },
            false));
  }

  void jumpTime() {
    LinearLayout c = column();
    EditText t = field(c, "Hours : minutes : seconds", "00:00:00");
    Dialog d = sheet("Jump to time", c);
    gap(c, 20);
    c.addView(
        button(
            "Go to time",
            () -> {
              try {
                String[] pieces = t.getText().toString().split(":");
                long seconds = 0;
                for (String p : pieces) seconds = seconds * 60 + Long.parseLong(p);
                seekTo(seconds * 1000);
                d.dismiss();
              } catch (Exception e) {
                t.setError("Use hours:minutes:seconds");
              }
            },
            true));
  }

  void chapters() {
    LinearLayout c = column();
    Dialog d = sheet("Chapters", c);
    Object[] chapters = array(invoke(service(), "getChapters", -1));
    if (chapters.length == 0) c.addView(text("This video has no chapters.", 14, muted));
    for (int i = 0; i < chapters.length; i++) {
      Object ch = chapters[i];
      final int index = i;
      String name = "Chapter " + (i + 1);
      try {
        name = str(ch.getClass().getField("name").get(ch));
      } catch (Exception e) {
      }
      destination(
          c,
          name,
          "",
          null,
          () -> {
            invoke(service(), "setChapterIdx", index);
            d.dismiss();
          });
    }
  }

  void bookmarkSheet() {
    LinearLayout c = column();
    Dialog d = sheet("Bookmarks", c);
    Object media = invoke(service(), "getCurrentMediaWrapper");
    for (Object b : array(invoke(media, "getBookmarks"))) {
      long p = number(invoke(b, "getTime"));
      destination(
          c,
          title(b).isEmpty() ? "Saved at " + time(p) : title(b),
          time(p),
          "bookmark",
          () -> {
            seekTo(p);
            d.dismiss();
          });
    }
    gap(c, 12);
    c.addView(
        button(
            "+ Save this moment",
            () -> {
              Object saved = invoke(media, "addBookmark", number(invoke(service(), "getTime")));
              if (saved != null)
                invoke(saved, "setName", "Moment at " + time(number(invoke(service(), "getTime"))));
              d.dismiss();
              bookmarkSheet();
            },
            true));
  }

  void cast() {
    LinearLayout c = column();
    Dialog d = sheet("Cast to a device", c);
    Object dataset =
        invoke(singleton("org.videolan.vlc.RendererDelegate", "INSTANCE"), "getRenderers");
    Object[] devices = array(invoke(dataset, "getValue"));
    if (devices.length == 0) {
      c.addView(text("Looking for devices…", 20, ink));
      gap(c, 14);
      c.addView(text("Connect your phone and receiver to the same Wi-Fi network.", 14, muted));
    }
    for (Object device : devices) {
      String name = "";
      try {
        name = str(device.getClass().getField("name").get(device));
      } catch (Exception e) {
        name = str(invoke(device, "getName"));
      }
      destination(
          c,
          name,
          "Available on your network",
          "network",
          () -> {
            invoke(service(), "setRenderer", device);
            d.dismiss();
          });
    }
    gap(c, 18);
    c.addView(
        button(
            "Refresh devices",
            () -> {
              d.dismiss();
              cast();
            },
            false));
    destination(
        c,
        "Play on this device",
        "Disconnect receiver",
        null,
        () -> {
          invoke(service(), "setRenderer", (Object) null);
          d.dismiss();
        });
  }

  void popup() {
    if (Build.VERSION.SDK_INT >= 26) {
      try {
        Object media = invoke(service(), "getCurrentMediaWrapper");
        int width = (int) number(invoke(media, "getWidth"));
        int height = (int) number(invoke(media, "getHeight"));
        if (width <= 0 || height <= 0) { width = 16; height = 9; }
        a.enterPictureInPictureMode(
            new PictureInPictureParams.Builder()
                .setAspectRatio(new android.util.Rational(width, height))
                .build());
        show(false);
      } catch (Exception e) {
        toast("Allow picture in picture in Android settings");
      }
    }
  }

  void repeat() {
    LinearLayout c = column();
    Dialog d = sheet("A–B repeat", c);
    Object manager = invoke(service(), "getPlaylistManager"),
        media = invoke(service(), "getCurrentMediaWrapper");
    c.addView(text("Repeat a passage by setting its start and end while it plays.", 14, muted));
    gap(c, 18);
    c.addView(
        button(
            "Set point A / B here",
            () -> {
              invoke(manager, "setABRepeatValue", media, number(invoke(service(), "getTime")));
              toast("Repeat point saved");
            },
            true));
    gap(c, 12);
    c.addView(button("Enable / disable repeat", () -> invoke(manager, "toggleABRepeat"), false));
    gap(c, 12);
    c.addView(
        button(
            "Clear repeat points",
            () -> {
              invoke(manager, "clearABRepeat");
              d.dismiss();
            },
            false));
  }

  void sizes() {
    LinearLayout c = column();
    Dialog d = sheet("Video size", c);
    String[] names = {"Best fit", "Fill screen", "16:9", "4:3", "Original size"};
    String selected = prefs.getString("premiere_video_size", "Best fit");
    for (String name : names)
      destination(
          c,
          name,
          name.equals(selected) ? "Selected" : "",
          null,
          () -> {
            prefs.edit().putString("premiere_video_size", name).apply();
            defaultScaleApplied = false;
            fillVideo();
            if (sizeLabel != null) sizeLabel.setText(name);
            indicator("Video size · " + name);
            d.dismiss();
          });
  }

  void info() {
    LinearLayout c = column();
    Object media = invoke(service(), "getCurrentMediaWrapper");
    c.addView(text(title(media), 22, ink));
    gap(c, 18);
    destination(c, "Video", format(media), null, () -> {});
    destination(c, "Duration", time(number(invoke(service(), "getLength"))), null, () -> {});
    destination(c, "Audio tracks", str(invoke(service(), "getAudioTracksCount")), null, () -> {});
    destination(c, "Subtitles", str(invoke(service(), "getSpuTracksCount")), null, () -> {});
    destination(c, "Source", str(invoke(media, "getUri")), null, () -> {});
    sheet("Playback information", c);
  }

  static final class AudioManagerHolder {
    final android.media.AudioManager audio;

    AudioManagerHolder(Context c) {
      audio = (android.media.AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    }

    int get() {
      return audio.getStreamVolume(3);
    }

    int max() {
      return audio.getStreamMaxVolume(3);
    }

    void set(int value) {
      audio.setStreamVolume(3, Math.max(0, Math.min(max(), value)), 0);
    }
  }
}
