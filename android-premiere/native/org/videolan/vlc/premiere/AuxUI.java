package org.videolan.vlc.premiere;

import static org.videolan.vlc.premiere.Premiere.*;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/**
 * Composes existing bound views so PIN validation, OTPs, licenses and support retain VLC's logic.
 */
final class AuxUI extends Premiere.UI {
  final Map<String, View> bound = new HashMap<>();
  final String kind;

  AuxUI(Activity a) {
    super(a);
    kind = a.getClass().getSimpleName();
    screen = "aux";
  }

  @Override
  void install(Bundle b) {
    String[] ids = {
      "start_log",
      "stop_log",
      "copy_to_clipboard",
      "save_to_file",
      "clear_log",
      "log_list",
      "equalizers",
      "overwrite_container",
      "authors_list",
      "licenses",
      "pin_code_reason",
      "pin_code_title",
      "pin_code_parent1",
      "pin_code_parent2",
      "pin_code_parent3",
      "pin_code_parent4",
      "next_button",
      "cancel_button",
      "sucess_title",
      "imageView18",
      "keyboard_grid",
      "pin_group",
      "sucess_group",
      "title",
      "title2",
      "code1",
      "code2",
      "code3",
      "code4",
      "code5",
      "code6",
      "server_status",
      "status_button",
      "links_grid",
      "remote_access_qr_code",
      "connection_list",
      "feedback_forum_card",
      "read_doc_card",
      "email_support_card",
      "rate_card"
    };
    for (String id : ids) {
      View v = a.findViewById(a.getResources().getIdentifier(id, "id", a.getPackageName()));
      if (v != null) bound.put(id, v);
    }
    ViewGroup content = a.findViewById(android.R.id.content);
    old = content.getChildAt(0);
    old.setVisibility(View.GONE);
    root = new FrameLayout(a);
    content.addView(root, new ViewGroup.LayoutParams(-1, -1));
    fitWindow(root);
    render();
    if (kind.equals("OTPCodeActivity")) retryDigits(0);
  }

  void retryDigits(int attempt) {
    if (bound.containsKey("code1") || a.isDestroyed() || attempt > 20) return;
    handler.postDelayed(
        () -> {
          for (String id :
              new String[] {
                "code1", "code2", "code3", "code4", "code5", "code6", "cancel_button"
              }) {
            View v = a.findViewById(a.getResources().getIdentifier(id, "id", a.getPackageName()));
            if (v != null) bound.put(id, v);
          }
          if (bound.containsKey("code1")) render();
          else retryDigits(attempt + 1);
        },
        150);
  }

  @Override
  void refresh() {
    if (root != null) render();
  }

  @Override
  boolean back() {
    a.finish();
    return true;
  }

  View take(String id) {
    View v = bound.get(id);
    if (v != null && v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    return v;
  }

  void append(LinearLayout to, String id, int height) {
    View v = take(id);
    if (v == null) return;
    if (v instanceof TextView) {
      ((TextView) v).setTextColor(ink);
      ((TextView) v).setTextSize(id.contains("title") ? 24 : 14);
    }
    to.addView(v, new LinearLayout.LayoutParams(-1, height < 0 ? height : dp(height)));
  }

  @Override
  void render() {
    palette();
    root.removeAllViews();
    root.setBackgroundColor(bg);
    base = column();
    base.setPadding(dp(20), dp(12), dp(20), dp(20));
    root.addView(base, new FrameLayout.LayoutParams(-1, -1));
    body = column();
    base.addView(body, new LinearLayout.LayoutParams(-1, -2));
    String title =
        kind.equals("AuthorsActivity")
            ? "Contributors"
            : kind.equals("LibrariesActivity")
                ? "Open source licenses"
                : kind.equals("PinCodeActivity")
                    ? "Parental controls"
                    : kind.equals("OTPCodeActivity")
                        ? "Pair a browser"
                        : kind.equals("RemoteAccessShareActivity")
                            ? "Remote access"
                            : kind.equals("DebugLogActivity")
                                ? "Debug logs"
                                : kind.equals("EqualizerSettingsActivity")
                                    ? "Equalizer presets"
                                    : "Feedback & support";
    heading(
        title,
        kind.equals("OTPCodeActivity")
            ? "A one-time connection"
            : kind.equals("RemoteAccessShareActivity")
                ? "Your device, securely connected"
                : "VLC · VideoLAN");
    if (kind.equals("DebugLogActivity")) {
      LinearLayout actions = column();
      actions.setPadding(dp(16), dp(16), dp(16), dp(16));
      actions.setBackground(surface(panel, 8, true));
      for (String id :
          new String[] {
            "start_log", "stop_log", "copy_to_clipboard", "save_to_file", "clear_log"
          }) {
        append(actions, id, 48);
        gap(actions, 8);
      }
      base.addView(actions);
      View list = take("log_list");
      if (list != null) base.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
      return;
    }
    if (kind.equals("EqualizerSettingsActivity")) {
      ScrollView sc = new ScrollView(a);
      sc.setVerticalScrollBarEnabled(false);
      LinearLayout controls = column();
      sc.addView(controls);
      base.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
      new EqualizerUI(this, controls).start();
      return;
    }
    if (kind.equals("AuthorsActivity") || kind.equals("LibrariesActivity")) {
      View list = take(kind.equals("AuthorsActivity") ? "authors_list" : "licenses");
      if (list != null) {
        list.setBackgroundColor(bg);
        list.setPadding(0, 0, 0, dp(16));
        base.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
      }
      return;
    }
    ScrollView scroll = new ScrollView(a);
    LinearLayout content = column();
    scroll.addView(content);
    base.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (kind.equals("OTPCodeActivity")) {
      gap(content, 44);
      content.addView(text("Enter this code in your browser.", 22, ink));
      gap(content, 18);
      content.addView(text("Confirm the connection only on a device you trust.", 14, muted));
      gap(content, 36);
      LinearLayout code = row();
      for (int i = 1; i <= 6; i++) {
        View digit = take("code" + i);
        if (digit != null) {
          digit.setBackground(surface(panel, 6, true));
          if (digit instanceof TextView) {
            ((TextView) digit).setTextColor(orange);
            ((TextView) digit).setTextSize(30);
          }
          LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(64), 1);
          p.rightMargin = dp(6);
          code.addView(digit, p);
        }
      }
      content.addView(code);
      gap(content, 30);
      append(content, "cancel_button", 48);
    } else if (kind.equals("PinCodeActivity")) {
      gap(content, 26);
      append(content, "pin_code_reason", -2);
      gap(content, 18);
      append(content, "pin_code_title", -2);
      gap(content, 30);
      LinearLayout pin = row();
      for (int i = 1; i <= 4; i++) {
        View input = take("pin_code_parent" + i);
        if (input != null) {
          LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(72), 1);
          p.rightMargin = dp(8);
          pin.addView(input, p);
        }
      }
      content.addView(pin);
      gap(content, 12);
      append(content, "keyboard_grid", 220);
      gap(content, 12);
      append(content, "sucess_title", -2);
      append(content, "imageView18", 100);
      gap(content, 24);
      append(content, "next_button", 48);
      gap(content, 12);
      append(content, "cancel_button", 48);
      handler.postDelayed(
          new Runnable() {
            public void run() {
              if (a.isDestroyed()) return;
              View group = bound.get("pin_group");
              if (group != null) {
                pin.setVisibility(group.getVisibility());
                View keyboard = bound.get("keyboard_grid");
                if (keyboard != null) keyboard.setVisibility(group.getVisibility());
                View reason = bound.get("pin_code_reason"), title = bound.get("pin_code_title");
                if (reason != null) reason.setVisibility(group.getVisibility());
                if (title != null) title.setVisibility(group.getVisibility());
              }
              View success = bound.get("sucess_group");
              if (success != null)
                for (String id : new String[] {"sucess_title", "imageView18"}) {
                  View v = bound.get(id);
                  if (v != null) v.setVisibility(success.getVisibility());
                }
              handler.postDelayed(this, 300);
            }
          },
          300);
    } else if (kind.equals("RemoteAccessShareActivity")) {
      content.addView(label("Server status"));
      LinearLayout card = column();
      card.setPadding(dp(16), dp(16), dp(16), dp(16));
      card.setBackground(surface(panel, 8, true));
      append(card, "server_status", -2);
      gap(card, 16);
      append(card, "status_button", 48);
      content.addView(card);
      gap(content, 24);
      content.addView(label("Connect a browser"));
      append(content, "links_grid", -2);
      gap(content, 18);
      append(content, "remote_access_qr_code", 180);
      gap(content, 24);
      content.addView(label("Paired devices"));
      append(content, "connection_list", -2);
    } else {
      for (String id :
          new String[] {
            "feedback_forum_card", "read_doc_card", "email_support_card", "rate_card"
          }) {
        View card = take(id);
        if (card != null) {
          content.addView(card, new LinearLayout.LayoutParams(-1, -2));
          gap(content, 18);
        }
      }
      if (content.getChildCount() == 0) {
        View original = takeContent(old);
        if (original != null) content.addView(original, new LinearLayout.LayoutParams(-1, -2));
      }
    }
  }

  View takeContent(View view) {
    if (view instanceof ViewGroup) {
      ViewGroup parent = (ViewGroup) view;
      for (int i = 0; i < parent.getChildCount(); i++) {
        View child = parent.getChildAt(i);
        if (child instanceof ScrollView
            || child.getClass().getName().contains("NestedScrollView")) {
          parent.removeView(child);
          return child;
        }
        View nested = takeContent(child);
        if (nested != null) return nested;
      }
    }
    return null;
  }
}
