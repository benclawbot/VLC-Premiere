package org.videolan.vlc.premiere;

import static org.videolan.vlc.premiere.Premiere.*;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Renders VLC's actual AndroidX preference model; its original listeners run. */
final class PreferencesUI extends Premiere.UI {
  Object controller;
  final Map<String, String> sections = new LinkedHashMap<>();

  PreferencesUI(Activity a) {
    super(a);
    sections.put("preferences_ui", "PreferencesUi");
    sections.put("preferences_video", "PreferencesVideo");
    sections.put("preferences_video_controls", "PreferencesVideoControls");
    sections.put("preferences_audio", "PreferencesAudio");
    sections.put("preferences_audio_controls", "PreferencesAudioControls");
    sections.put("preferences_subtitles", "PreferencesSubtitles");
    sections.put("preferences_casting", "PreferencesCasting");
    sections.put("preferences_remote_access", "PreferencesRemoteAccess");
    sections.put("preferences_android_auto", "PreferencesAndroidAuto");
    sections.put("preferences_adv", "PreferencesAdvanced");
    sections.put("preferences_optional", "PreferencesOptional");
    sections.put("preferences_parental_control", "PreferencesParentalControl");
    sections.put("preferences_widgets", "PreferencesWidgets");
    screen = "preferences";
    xml = a.getIntent().getStringExtra("premiere_preference_section");
    if (xml == null) xml = "preferences_ui";
  }

  @Override
  void refresh() {
    if (root != null && controller != null) render();
  }

  @Override
  void install(Bundle b) {
    super.install(b);
    openController(xml);
    watch();
  }

  void openController(String section) {
    String name = sections.get(section);
    if (name == null) return;
    try {
      Object manager = invoke(a, "getSupportFragmentManager");
      invoke(manager, "executePendingTransactions");
      Object fragment =
          Class.forName("org.videolan.vlc.gui.preferences." + name).getConstructor().newInstance();
      int id = a.getResources().getIdentifier("fragment_placeholder", "id", a.getPackageName());
      Object tx = invoke(manager, "beginTransaction");
      invoke(tx, "replace", id, fragment);
      invoke(tx, "commitNow");
      controller = fragment;
      xml = section;
      screen = "preferences";
      render();
    } catch (Exception e) {
      android.util.Log.e("Premiere", "Preference controller", e);
    }
  }

  void watch() {
    handler.postDelayed(
        new Runnable() {
          public void run() {
            if (a.isDestroyed() || a.isFinishing()) return;
            int id =
                a.getResources().getIdentifier("fragment_placeholder", "id", a.getPackageName());
            Object current = invoke(invoke(a, "getSupportFragmentManager"), "findFragmentById", id);
            if (current != null && current != controller) {
              history.push(new State(PreferencesUI.this));
              controller = current;
              for (Map.Entry<String, String> entry : sections.entrySet())
                if (current.getClass().getSimpleName().equals(entry.getValue()))
                  xml = entry.getKey();
              render();
            }
            handler.postDelayed(this, 300);
          }
        },
        300);
  }

  @Override
  void pref(String next) {
    history.push(new State(this));
    openController(next);
  }

  @Override
  boolean back() {
    if (history.isEmpty()) {
      a.finish();
      return true;
    }
    State state = history.pop();
    openController(state.xml);
    return true;
  }

  @Override
  void preferences() {
    if (controller == null) {
      heading("Settings", "Make VLC yours");
      return;
    }
    Object group = invoke(controller, "getPreferenceScreen");
    String title =
        xml.equals("preferences_parental_control")
            ? "Parental controls"
            : str(invoke(group, "getTitle"));
    heading(title.isEmpty() ? "Settings" : title, "Make VLC yours");
    renderGroup(group, body);
  }

  void renderGroup(Object group, LinearLayout parent) {
    boolean rootGroup = parent == body;
    LinearLayout currentCard = null;
    String currentLabel = "";
    int count = (int) number(invoke(group, "getPreferenceCount"));
    for (int i = 0; i < count; i++) {
      Object p = invoke(group, "getPreference", i);
      if (Boolean.FALSE.equals(invoke(p, "isVisible"))) continue;
      String name = str(invoke(p, "getTitle")),
          summary = str(invoke(p, "getSummary")),
          key = str(invoke(p, "getKey")),
          type = p.getClass().getSimpleName();
      if (name.isEmpty() || key.equals("tv_ui")) continue;
      if (key.equals("app_theme")) name = "Theme";
      else if (key.equals("set_locale")) name = "Language";
      LinearLayout target = parent;
      if (rootGroup && !type.contains("PreferenceCategory")) {
        String label =
            xml.equals("preferences_ui")
                ? (key.equals("app_theme") || key.equals("set_locale")
                    ? "Appearance"
                    : "Library & navigation")
                : xml.contains("subtitle")
                    ? "Appearance"
                    : xml.contains("audio")
                        ? "Audio playback"
                        : xml.contains("video") ? "Video playback" : "Preferences";
        if (currentCard == null || !label.equals(currentLabel)) {
          currentCard = group(label);
          currentLabel = label;
        }
        target = currentCard;
      }
      if (type.contains("PreferenceCategory")) {
        currentCard = null;
        LinearLayout card = group(name);
        renderGroup(p, card);
      } else if (type.contains("CheckBox") || type.contains("Switch")) {
        LinearLayout row = row();
        row.setPadding(0, dp(14), 0, dp(14));
        LinearLayout copy = column();
        copy.addView(text(name, 14, ink));
        if (!summary.isEmpty()) {
          gap(copy, 5);
          copy.addView(text(summary, 11, muted));
        }
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(a);
        sw.setChecked(Boolean.TRUE.equals(invoke(p, "isChecked")));
        sw.setEnabled(!Boolean.FALSE.equals(invoke(p, "isEnabled")));
        sw.setContentDescription(name);
        sw.setThumbTintList(android.content.res.ColorStateList.valueOf(orange));
        sw.setOnCheckedChangeListener(
            (v, on) -> {
              invoke(p, "performClick");
              handler.postDelayed(this::render, 200);
            });
        row.addView(sw);
        row.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
        target.addView(row);
        divider(target);
      } else if (type.equals("ListPreference")) {
        destination(target, name, summary, null, () -> list(p));
      } else if (type.equals("MultiSelectListPreference")) {
        destination(target, name, summary, null, () -> multi(p));
      } else if (type.equals("SeekBarPreference")) {
        target.addView(text(name, 14, ink));
        SeekBar b = new SeekBar(a);
        int min = (int) number(invoke(p, "getMin")), max = (int) number(invoke(p, "getMax"));
        b.setMax(Math.max(1, max - min));
        b.setProgress((int) number(invoke(p, "getValue")) - min);
        b.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {
              public void onProgressChanged(SeekBar b, int value, boolean user) {
                if (user && !Boolean.FALSE.equals(invoke(p, "callChangeListener", value + min)))
                  invoke(p, "setValue", value + min);
              }

              public void onStartTrackingTouch(SeekBar b) {}

              public void onStopTrackingTouch(SeekBar b) {}
            });
        target.addView(b);
      } else if (type.equals("EditTextPreference")) {
        destination(target, name, summary, null, () -> edit(p));
      } else {
        destination(
            target,
            name,
            summary,
            null,
            () -> {
              invoke(p, "performClick");
              handler.postDelayed(
                  () -> {
                    Object current =
                        invoke(
                            invoke(a, "getSupportFragmentManager"),
                            "findFragmentById",
                            a.getResources()
                                .getIdentifier("fragment_placeholder", "id", a.getPackageName()));
                    if (current == controller) render();
                  },
                  300);
            });
      }
    }
  }

  void divider(LinearLayout c) {
    View lineView = new View(a);
    lineView.setBackgroundColor(line);
    c.addView(lineView, new LinearLayout.LayoutParams(-1, dp(1)));
  }

  void list(Object preference) {
    Object[] labels = array(invoke(preference, "getEntries")),
        values = array(invoke(preference, "getEntryValues"));
    LinearLayout c = column();
    Dialog d = sheet(str(invoke(preference, "getTitle")), c);
    for (int i = 0; i < Math.min(labels.length, values.length); i++) {
      String value = str(values[i]);
      destination(
          c,
          str(labels[i]),
          value.equals(str(invoke(preference, "getValue"))) ? "Selected" : "",
          null,
          () -> {
            if (Boolean.FALSE.equals(invoke(preference, "callChangeListener", value))) return;
            invoke(preference, "setValue", value);
            d.dismiss();
            render();
          });
    }
  }

  void multi(Object preference) {
    Object[] labels = array(invoke(preference, "getEntries")),
        values = array(invoke(preference, "getEntryValues"));
    Object existing = invoke(preference, "getValues");
    Set<String> selected =
        existing instanceof Set ? new HashSet<>((Set<String>) existing) : new HashSet<>();
    LinearLayout c = column();
    Dialog d = sheet(str(invoke(preference, "getTitle")), c);
    for (int i = 0; i < Math.min(labels.length, values.length); i++) {
      String value = str(values[i]);
      CheckBox cb = new CheckBox(a);
      cb.setText(str(labels[i]));
      cb.setTextColor(ink);
      cb.setChecked(selected.contains(value));
      cb.setPadding(0, dp(12), 0, dp(12));
      cb.setOnCheckedChangeListener(
          (v, on) -> {
            if (on) selected.add(value);
            else selected.remove(value);
          });
      c.addView(cb);
    }
    gap(c, 20);
    c.addView(
        button(
            "Save choices",
            () -> {
              if (Boolean.FALSE.equals(invoke(preference, "callChangeListener", selected))) return;
              invoke(preference, "setValues", selected);
              d.dismiss();
              render();
            },
            true));
  }

  void edit(Object preference) {
    LinearLayout c = column();
    EditText input =
        field(c, str(invoke(preference, "getTitle")), str(invoke(preference, "getText")));
    Dialog d = sheet(str(invoke(preference, "getTitle")), c);
    gap(c, 20);
    c.addView(
        button(
            "Save",
            () -> {
              String value = input.getText().toString();
              if (Boolean.FALSE.equals(invoke(preference, "callChangeListener", value))) {
                input.setError("Check this value");
                return;
              }
              invoke(preference, "setText", value);
              d.dismiss();
              render();
            },
            true));
  }
}
