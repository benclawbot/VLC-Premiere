package org.videolan.vlc.premiere;

import static org.videolan.vlc.premiere.Premiere.*;

import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Premiere controls over VLC's persisted Room equalizers and playback model. */
final class EqualizerUI {
  final Premiere.UI ui;
  final LinearLayout content;
  Object model, observer, liveData;

  EqualizerUI(Premiere.UI ui, LinearLayout content) {
    this.ui = ui;
    this.content = content;
  }

  void start() {
    try {
      Object repo =
          invoke(
              singleton("org.videolan.vlc.repository.EqualizerRepository", "Companion"),
              "getInstance",
              ui.a);
      Class<?> type = clazz("org.videolan.vlc.viewmodels.EqualizerViewModel");
      for (Constructor<?> ctor : type.getConstructors()) {
        if (ctor.getParameterTypes().length == 2) {
          model = ctor.newInstance(ui.a, repo);
          break;
        }
      }
      invoke(model, "setCurrentEqualizerId", ui.prefs.getLong("current_equalizer_id", 1L));
      invoke(
          model,
          "setBandCount",
          (int) number(invoke(clazz("org.videolan.libvlc.MediaPlayer$Equalizer"), "getBandCount")));
      liveData = invoke(model, "getEqualizerEntries");
      Class<?> observerType = clazz("androidx.lifecycle.Observer");
      observer =
          Proxy.newProxyInstance(
              observerType.getClassLoader(),
              new Class<?>[] {observerType},
              (p, method, args) -> {
                if (method.getName().equals("onChanged")) {
                  if (array(args[0]).length > 0) {
                    invoke(model, "updateEqualizer");
                    draw();
                  }
                  return null;
                }
                if (method.getName().equals("hashCode")) return System.identityHashCode(p);
                if (method.getName().equals("equals")) return p == args[0];
                return "Premiere equalizer observer";
              });
      content.addOnAttachStateChangeListener(
          new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {}

            public void onViewDetachedFromWindow(View v) {
              invoke(liveData, "removeObserver", observer);
              // This model belongs to this panel rather than an activity ViewModelStore.
              Object scope =
                  invoke(clazz("androidx.lifecycle.ViewModelKt"), "getViewModelScope", model);
              Object job =
                  invoke(
                      invoke(scope, "getCoroutineContext"),
                      "get",
                      singleton("kotlinx.coroutines.Job", "Key"));
              invoke(job, "cancel", (Object) null);
            }
          });
      content.addView(ui.text("Loading your presets…", 14, ui.muted));
      invoke(liveData, "observe", ui.a, observer);
    } catch (Exception e) {
      android.util.Log.e("Premiere", "Equalizer model", e);
      content.addView(
          ui.button(
              "Open equalizer",
              () -> ui.legacy("org.videolan.vlc.gui.EqualizerSettingsActivity"),
              true));
    }
  }

  void draw() {
    Object current = invoke(model, "getCurrentEqualizer");
    if (current == null) return;
    Object entry = invoke(current, "getEqualizerEntry");
    Object eq = invoke(current, "getEqualizer");
    boolean custom = number(invoke(entry, "getPresetIndex")) == -1;
    content.removeAllViews();
    Switch enabled = new Switch(ui.a);
    enabled.setText("Equalizer enabled");
    enabled.setTextColor(ui.ink);
    enabled.setChecked(ui.prefs.getBoolean("equalizer_enabled", false));
    enabled.setOnCheckedChangeListener(
        (b, on) -> {
          ui.prefs.edit().putBoolean("equalizer_enabled", on).apply();
          invoke(model, "updateEqualizer");
          draw();
        });
    content.addView(enabled);
    ui.gap(content, 16);
    content.addView(ui.text(str(invoke(entry, "getName")), 22, ui.ink));
    ui.gap(content, 12);
    SeekBar preamp = new SeekBar(ui.a);
    content.addView(ui.text("Preamp", 12, ui.muted));
    slider(
        preamp,
        (float) ((Number) invoke(entry, "getPreamp")).doubleValue(),
        custom,
        value -> invoke(model, "updateCurrentPreamp", ui.a, value));
    content.addView(preamp);
    HorizontalScrollView scroll = new HorizontalScrollView(ui.a);
    scroll.setHorizontalScrollBarEnabled(false);
    LinearLayout bands = ui.row();
    int n =
        (int) number(invoke(clazz("org.videolan.libvlc.MediaPlayer$Equalizer"), "getBandCount"));
    for (int i = 0; i < n; i++) {
      final int band = i;
      float value = ((Number) invoke(eq, "getAmp", i)).floatValue();
      LinearLayout cell = ui.column();
      cell.setGravity(Gravity.CENTER);
      TextView gain = ui.text(Math.round(value) + " dB", 10, ui.muted);
      cell.addView(gain);
      SeekBar slider = new SeekBar(ui.a);
      slider.setContentDescription("Equalizer band " + (i + 1));
      slider.setRotation(-90);
      slider(
          slider,
          value,
          custom,
          chosen -> {
            ArrayList<Object> values = new ArrayList<>();
            try {
              for (int j = 0; j < n; j++) {
                float amp = j == band ? chosen : ((Number) invoke(eq, "getAmp", j)).floatValue();
                values.add(
                    clazz("org.videolan.vlc.mediadb.models.EqualizerBand")
                        .getConstructor(int.class, float.class)
                        .newInstance(j, amp));
              }
              invoke(model, "updateEqualizerBands", ui.a, values);
            } catch (Exception e) {
              android.util.Log.e("Premiere", "Equalizer band", e);
            }
          });
      FrameLayout rail = new FrameLayout(ui.a);
      rail.addView(slider, new FrameLayout.LayoutParams(ui.dp(135), ui.dp(40), Gravity.CENTER));
      cell.addView(rail, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(145)));
      int hz =
          (int)
              number(
                  invoke(
                      clazz("org.videolan.libvlc.MediaPlayer$Equalizer"), "getBandFrequency", i));
      cell.addView(ui.text(hz >= 1000 ? hz / 1000 + "k" : hz + "", 10, ui.muted));
      bands.addView(cell, new LinearLayout.LayoutParams(ui.dp(48), -2));
    }
    scroll.addView(bands);
    content.addView(scroll);
    ui.gap(content, 18);
    content.addView(
        ui.button(
            "Customize this preset",
            () -> invoke(model, "createCustomEqualizer", ui.a, false),
            false));
    if (custom) {
      content.addView(
          ui.button(
              "Name this preset",
              () -> {
                LinearLayout form = ui.column();
                EditText name = ui.field(form, "Preset name", str(invoke(entry, "getName")));
                android.app.Dialog dialog = ui.sheet("Name preset", form);
                form.addView(
                    ui.button(
                        "Save",
                        () -> {
                          String chosen = name.getText().toString().trim();
                          if (chosen.isEmpty()) {
                            name.setError("Enter a name");
                            return;
                          }
                          invoke(model, "updateEqualizerName", ui.a, chosen);
                          dialog.dismiss();
                        },
                        true));
              },
              false));
    }
    ui.gap(content, 20);
    for (Object preset : array(invoke(liveData, "getValue"))) {
      Object presetEntry = invoke(preset, "getEqualizerEntry");
      long id = number(invoke(presetEntry, "getId"));
      ui.destination(
          content,
          str(invoke(presetEntry, "getName")),
          id == number(invoke(entry, "getId")) ? "Selected" : "",
          null,
          () -> {
            invoke(model, "setCurrentEqualizerId", id);
            invoke(model, "updateEqualizer");
            draw();
          });
    }
  }

  interface Gain {
    void apply(float value);
  }

  void slider(SeekBar slider, float value, boolean custom, Gain change) {
    slider.setMax(40);
    slider.setProgress(Math.round(value) + 20);
    slider.setEnabled(custom && ui.prefs.getBoolean("equalizer_enabled", false));
    slider.setProgressTintList(ColorStateList.valueOf(ui.orange));
    slider.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar bar, int progress, boolean user) {}

          public void onStartTrackingTouch(SeekBar bar) {}

          public void onStopTrackingTouch(SeekBar bar) {
            change.apply((float) (bar.getProgress() - 20));
          }
        });
  }
}
