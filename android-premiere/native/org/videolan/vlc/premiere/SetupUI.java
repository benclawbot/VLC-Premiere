package org.videolan.vlc.premiere;

import static org.videolan.vlc.premiere.Premiere.*;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;

/** Keeps VLC's permission requests, scan selection and remote setup state machine. */
final class SetupUI extends Premiere.UI {
  final boolean remote;
  String step = "";
  Object model;
  final Set<String> folderSelection = new LinkedHashSet<>();
  final Set<String> indexedFolders = new LinkedHashSet<>();

  SetupUI(Activity a) {
    super(a);
    remote = a.getClass().getName().contains("RemoteAccess");
  }

  Object model() {
    try {
      Method m = a.getClass().getDeclaredMethod("getViewModel");
      m.setAccessible(true);
      return m.invoke(a);
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  void install(Bundle b) {
    model = model();
    ViewGroup content = a.findViewById(android.R.id.content);
    old = content.getChildAt(0);
    old.setVisibility(View.GONE);
    root = new android.widget.FrameLayout(a);
    content.addView(root, new ViewGroup.LayoutParams(-1, -1));
    fitWindow(root);
    step = str(invoke(model, "getCurrentFragment"));
    render();
    poll();
  }

  void poll() {
    handler.postDelayed(
        new Runnable() {
          public void run() {
            if (a.isDestroyed() || a.isFinishing()) return;
            String now = str(invoke(model, "getCurrentFragment"));
            if (!now.equals(step)) {
              step = now;
              render();
            }
            handler.postDelayed(this, 300);
          }
        },
        300);
  }

  @Override
  void refresh() {
    if (root != null) {
      step = str(invoke(model, "getCurrentFragment"));
      render();
    }
  }

  @Override
  boolean back() {
    return false;
  }

  void next() {
    if (step.equals("NO_PERMISSION")) {
      invoke(model, "setScanStorages", false);
      Premiere.afterSetup = "pick-file";
      invoke(a, "onDone");
      return;
    }
    invoke(a, "onNext");
    handler.postDelayed(this::refresh, 150);
  }

  @Override
  void render() {
    palette();
    root.removeAllViews();
    root.setBackgroundColor(bg);
    base = column();
    base.setPadding(dp(24), dp(28), dp(24), dp(26));
    root.addView(base, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout brand = row();
    brand.addView(
        new Premiere.Icon(a, "cone", orange), new LinearLayout.LayoutParams(dp(28), dp(30)));
    TextView mark = text("VLC · PREMIERE", 12, ink);
    mark.setLetterSpacing(.12f);
    brand.addView(mark, new LinearLayout.LayoutParams(0, -2, 1));
    TextView skip = text("Skip", 12, muted);
    skip.setPadding(dp(12), dp(10), 0, dp(10));
    skip.setOnClickListener(v -> invoke(a, "onDone"));
    brand.addView(skip);
    base.addView(brand);
    ScrollView scroll = new ScrollView(a);
    body = column();
    scroll.addView(body);
    base.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    gap(body, 46);
    String name, copy, action = "Continue";
    if (remote) {
      switch (step) {
        case "HOW":
          name = "A little more freedom.";
          copy = "Play, pause, browse, and transfer media from a connected browser.";
          break;
        case "CONTENT":
          name = "You choose what to share.";
          copy = "Keep control over the media and actions available remotely.";
          break;
        case "SSL":
          name = "A private connection.";
          copy = "Remote access uses an encrypted connection to protect your media on the network.";
          break;
        case "OTP":
          name = "Pair with a one-time code.";
          copy = "Enter the code shown on your phone in the browser you want to connect.";
          action = "Done";
          break;
        default:
          name = "VLC, from your browser.";
          copy =
              "Browse your library and control playback from another device on the same network.";
      }
    } else {
      switch (step) {
        case "THEME":
          name = "Set the mood.";
          copy = "Choose an appearance that feels right. You can change it in Settings.";
          action = "Start watching";
          break;
        case "ASK_PERMISSION":
          name = "Make room for your library.";
          copy = "Let VLC find videos and music on your device. You choose which folders to scan.";
          action = "Allow media access";
          break;
        case "SCAN":
          name = "Find your favorites.";
          copy = "Scan your media automatically, or choose the folders you want in your library.";
          break;
        case "NOTIFICATION_PERMISSION":
          name = "Keep the controls close.";
          copy =
              "Playback notifications let you pause, skip, and return to your media from the lock"
                  + " screen.";
          action = "Allow notifications";
          break;
        case "NO_PERMISSION":
          name = "Start with a single file.";
          copy =
              "You can still open a file or network stream. Media access can be granted whenever"
                  + " you are ready.";
          action = "Open a file";
          break;
        default:
          name = "Your media. Your world.";
          copy =
              "Films, music, and everything in between. All the files you love, beautifully in one"
                  + " place.";
          action = "Get started";
      }
    }
    TextView title = text(name, 36, ink);
    title.setTypeface(android.graphics.Typeface.create("sans-serif", 0));
    title.setMaxLines(3);
    body.addView(title);
    gap(body, 20);
    TextView intro = text(copy, 14, muted);
    intro.setLineSpacing(dp(5), 1);
    body.addView(intro);
    gap(body, 30);
    if (step.equals("THEME")) {
      LinearLayout themes = row();
      String[] labels = {"Dark", "Light"};
      int[] values = {2, 1};
      for (int i = 0; i < 2; i++) {
        final int value = values[i];
        LinearLayout tile = column();
        tile.setPadding(dp(12), dp(15), dp(12), dp(15));
        tile.setBackground(surface(i == 0 ? 0xff211e1b : 0xfff4efe7, 8, true));
        Premiere.Icon cone = new Premiere.Icon(a, "cone", orange);
        tile.addView(cone, new LinearLayout.LayoutParams(dp(28), dp(32)));
        gap(tile, 18);
        TextView sample = text("Your library", 18, i == 0 ? ink : 0xff29241f);
        tile.addView(sample);
        gap(tile, 12);
        View preview = new View(a);
        preview.setBackground(surface(0xffbd8054, 5, false));
        tile.addView(preview, new LinearLayout.LayoutParams(-1, dp(50)));
        gap(tile, 16);
        tile.addView(
            text(
                labels[i] + (number(invoke(model, "getTheme")) == value ? "  ✓" : ""),
                13,
                i == 0 ? ink : 0xff29241f));
        tile.setOnClickListener(
            v -> {
              invoke(model, "setTheme", value);
              prefs.edit().putString("app_theme", String.valueOf(value)).apply();
              render();
            });
        tile.setContentDescription("Use " + labels[i] + " theme");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.rightMargin = dp(10);
        themes.addView(tile, p);
      }
      body.addView(themes);
      gap(body, 18);
      body.addView(
          button(
              "Follow system appearance",
              () -> {
                invoke(model, "setTheme", -1);
                prefs.edit().putString("app_theme", "-1").apply();
                render();
              },
              false));
    } else if (step.equals("SCAN")) {
      Switch auto = new Switch(a);
      auto.setText("Scan automatically");
      auto.setTextColor(ink);
      auto.setChecked(Boolean.TRUE.equals(invoke(model, "getScanStorages")));
      auto.setPadding(dp(14), dp(18), dp(14), dp(18));
      auto.setBackground(surface(panel, 8, true));
      auto.setOnCheckedChangeListener((b, on) -> invoke(model, "setScanStorages", on));
      body.addView(auto);
      gap(body, 14);
      body.addView(button("Choose folders", this::chooseFolders, false));
    } else if (remote && step.equals("CONTENT")) {
      int labelsId =
          a.getResources()
              .getIdentifier("remote_access_content_entries", "array", a.getPackageName());
      int valuesId =
          a.getResources()
              .getIdentifier("remote_access_content_values", "array", a.getPackageName());
      String[] labels = a.getResources().getStringArray(labelsId),
          values = a.getResources().getStringArray(valuesId);
      final String key = "remote_access_medialibrary_content";
      Set<String> chosen =
          new HashSet<>(prefs.getStringSet(key, new HashSet<>(Arrays.asList(values))));
      for (int i = 0; i < Math.min(labels.length, values.length); i++) {
        String value = values[i];
        CheckBox box = new CheckBox(a);
        box.setText(labels[i]);
        box.setTextColor(ink);
        box.setPadding(0, dp(12), 0, dp(12));
        box.setChecked(chosen.contains(value));
        box.setOnCheckedChangeListener(
            (v, on) -> {
              if (on) chosen.add(value);
              else chosen.remove(value);
              prefs.edit().putStringSet(key, new HashSet<>(chosen)).apply();
            });
        body.addView(box);
      }
      toggle(
          body,
          "Playback control",
          "Allow play, pause, and seeking",
          "remote_access_playback_control",
          true);
      toggle(
          body,
          "File browsing",
          "Access folders directly",
          "remote_access_file_browser_content",
          false);
    } else if (step.equals("ASK_PERMISSION")) {
      LinearLayout kinds = row();
      for (String kind : new String[] {"Videos", "Audio"}) {
        LinearLayout card = column();
        card.setPadding(dp(18), dp(22), dp(18), dp(22));
        card.setBackground(surface(panel, 8, true));
        card.addView(
            new Premiere.Icon(a, kind.equals("Videos") ? "video" : "audio", orange),
            new LinearLayout.LayoutParams(dp(38), dp(42)));
        gap(card, 14);
        card.addView(text(kind, 17, ink));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.rightMargin = dp(10);
        kinds.addView(card, p);
      }
      body.addView(kinds);
    } else if (step.equals("NO_PERMISSION")) {
      body.addView(
          button(
              "Open a stream",
              () -> {
                invoke(model, "setScanStorages", false);
                Premiere.afterSetup = "stream";
                invoke(a, "onDone");
              },
              false));
    } else if (remote) {
      LinearLayout illustration = row();
      illustration.setGravity(Gravity.CENTER);
      illustration.addView(
          new Premiere.Icon(a, "video", orange), new LinearLayout.LayoutParams(dp(100), dp(130)));
      illustration.addView(text("···", 32, muted));
      illustration.addView(
          new Premiere.Icon(
              a, step.equals("OTP") || step.equals("SSL") ? "lock" : "network", orange),
          new LinearLayout.LayoutParams(dp(100), dp(130)));
      body.addView(illustration);
    } else {
      LinearLayout welcome = row();
      for (int i = 0; i < 3; i++) {
        ImageView panelView = new ImageView(a);
        panelView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        panelView.setBackground(surface(panel, 5, false));
        panelView.setClipToOutline(true);
        try {
          panelView.setImageBitmap(
              BitmapFactory.decodeStream(
                  a.getAssets()
                      .open(
                          "premiere/"
                              + new String[] {"golden-hour", "alpine", "coast"}[i]
                              + ".jpg")));
        } catch (Exception e) {
        }
        panelView.setRotation(i == 1 ? 4 : -5);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(i == 1 ? 175 : 145), 1);
        p.rightMargin = dp(8);
        welcome.addView(panelView, p);
      }
      body.addView(welcome);
    }
    gap(body, 20);
    LinearLayout footer = column();
    TextView dots = text("●  ·  ·  ·  ·", 12, orange);
    dots.setGravity(Gravity.CENTER);
    footer.addView(dots);
    gap(footer, 18);
    footer.addView(button(action, this::next, true));
    base.addView(footer);
  }

  void chooseFolders() {
    folderSelection.clear();
    indexedFolders.clear();
    Object roots = invoke(library(), "getFoldersList");
    if (roots instanceof String[])
      for (String root : (String[]) roots) {
        String path = Uri.parse(root).getScheme() == null ? root : Uri.parse(root).getPath();
        if (path != null) {
          String normalized = new File(path).getAbsolutePath();
          indexedFolders.add(normalized);
          folderSelection.add(normalized);
        }
      }
    Object parsingService = singleton("org.videolan.vlc.MediaParsingService", "Companion");
    Object preselected = invoke(parsingService, "getPreselectedStorages");
    if (preselected instanceof Iterable)
      for (Object root : (Iterable<?>) preselected)
        if (root != null) folderSelection.add(new File(String.valueOf(root)).getAbsolutePath());
    showFolderLevel(Environment.getExternalStorageDirectory());
  }

  void showFolderLevel(File directory) {
    File[] entries = directory.listFiles(File::isDirectory);
    if (entries == null) entries = new File[0];
    Arrays.sort(entries, (x, y) -> x.getName().compareToIgnoreCase(y.getName()));
    final File[] folders = entries;
    LinearLayout list = column();
    list.setPadding(dp(20), dp(8), dp(20), dp(8));
    TextView location = text(directory.getAbsolutePath(), 12, muted);
    location.setMaxLines(2);
    list.addView(location);
    gap(list, 8);
    if (folders.length == 0) list.addView(text("No folders here", 14, muted));
    for (File folder : folders) {
      String path = folder.getAbsolutePath();
      boolean included = false;
      boolean partial = false;
      for (String root : indexedFolders)
        if (path.equals(root) || path.startsWith(root + File.separator)) included = true;
        else if (root.startsWith(path + File.separator)) partial = true;
      final boolean alreadyIncluded = included;
      final boolean partlyIncluded = partial;
      boolean selected = folderSelection.contains(path);
      CheckBox check = new CheckBox(a);
      check.setText(
          folder.getName()
              + (alreadyIncluded
                  ? "  ·  In library"
                  : partlyIncluded ? "  ·  Partly in library" : selected ? "  ·  Selected" : ""));
      check.setTextColor(alreadyIncluded || partlyIncluded ? orange : ink);
      check.setChecked(selected);
      check.setOnCheckedChangeListener(
          (button, checked) -> {
            if (checked) folderSelection.add(path);
            else folderSelection.remove(path);
            button.setText(
                folder.getName()
                    + (alreadyIncluded
                        ? "  ·  In library"
                        : partlyIncluded
                            ? "  ·  Partly in library"
                            : checked ? "  ·  Selected" : "  ·  Not in library"));
            button.setTextColor(alreadyIncluded || partlyIncluded ? orange : ink);
          });
      LinearLayout row = row();
      row.addView(check, new LinearLayout.LayoutParams(0, -2, 1));
      TextView open = text("›", 24, muted);
      open.setGravity(Gravity.CENTER);
      open.setOnClickListener(v -> showFolderLevel(folder));
      row.addView(open, new LinearLayout.LayoutParams(dp(44), dp(48)));
      list.addView(row);
    }
    ScrollView scroll = new ScrollView(a);
    scroll.addView(list);
    AlertDialog dialog =
        new AlertDialog.Builder(a)
            .setTitle("Choose folders")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add selected", (d, which) -> addSelectedFolders())
            .create();
    dialog.setOnShowListener(
        ignored -> {
          Button add = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
          add.setTextColor(orange);
          dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(muted);
        });
    dialog.show();
  }

  void addSelectedFolders() {
    if (folderSelection.isEmpty()) {
      Toast.makeText(a, "Select at least one folder", Toast.LENGTH_SHORT).show();
      return;
    }
    Object parsingService = singleton("org.videolan.vlc.MediaParsingService", "Companion");
    Object preselected = invoke(parsingService, "getPreselectedStorages");
    if (preselected instanceof List) {
      List<?> paths = (List<?>) preselected;
      for (String path : folderSelection)
        if (!paths.contains(path)) ((List<String>) paths).add(path);
      invoke(model, "setScanStorages", true);
      handler.postDelayed(this::refresh, 100);
      Toast.makeText(a, "Selected folders will be scanned when setup finishes", Toast.LENGTH_SHORT)
          .show();
    } else {
      Toast.makeText(a, "Folder selection is unavailable", Toast.LENGTH_SHORT).show();
    }
  }
}
