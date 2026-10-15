package org.videolan.vlc.premiere;

import static org.videolan.vlc.premiere.Premiere.*;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.text.TextUtils;
import java.util.Locale;
import java.util.Arrays;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

/**
 * AndroidX RecyclerView/ListAdapter media surfaces backed by VLC's bundled AndroidX runtime.
 * Only visible media cells are created; DiffUtil retains identity and scroll anchors.
 * Keep this outside the screen ScrollView: nested scrolling defeats virtualization.
 */
final class VirtualLibrary {
  private VirtualLibrary() {}

  static boolean handles(Premiere.UI ui) {
    return "videos".equals(ui.screen)
        || ("audio".equals(ui.screen) && "Tracks".equals(ui.audioTab))
        || (("album".equals(ui.screen) || "artist".equals(ui.screen)
            || "genre".equals(ui.screen)) && ui.detailTracks.containsKey(ui.item));
  }

  /** Choose an identity from the media library, not the row index or artwork cache key. */
  static String itemKey(Object media) {
    if (media == null) return "";
    String uri = Premiere.str(Premiere.invoke(media, "getUri"));
    if (!uri.isEmpty() && !"null".equals(uri)) return "uri:" + uri;
    long id = Premiere.number(Premiere.invoke(media, "getId"));
    if (id > 0) return "id:" + id;
    // Fallback for media records that have not received a library ID yet.
    return "meta:" + ArtworkMatch.itemIdentity(
        "", "", Premiere.title(media), Premiere.str(Premiere.invoke(media, "getDate")),
        Premiere.number(Premiere.invoke(media, "getLength")));
  }

  static void render(Premiere.UI ui, int savedPosition, int savedTop, String anchorKey) {
    boolean videos = "videos".equals(ui.screen);
    boolean musicTracks = "audio".equals(ui.screen);
    Object[] items = videos ? ui.filteredVideos()
        : musicTracks ? ui.audios : ui.detailTracks.get(ui.item);
    if (items == null) items = new Object[0];
    final Object[] visibleItems = items;
    boolean posters = videos && !ui.prefs.getBoolean("premiere_video_list", false);

    ui.body = ui.column();
    ui.body.setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(12));
    ui.base.addView(ui.body, new LinearLayout.LayoutParams(-1, -2));

    if (videos) {
      ui.heading("All videos", items.length == ui.videos.length
          ? PremiereLocale.plural(ui.a, "premiere_video_count", items.length, items.length)
              + " · " + PremiereLocale.text(ui.a, "On this device")
          : PremiereLocale.plural(ui.a, "premiere_video_filtered",
              items.length, items.length, ui.videos.length));
    } else if (!musicTracks) {
      ui.heading(Premiere.title(ui.item), PremiereLocale.plural(ui.a, "premiere_track_count", items.length, items.length)
          + " · " + PremiereLocale.text(ui.a, "On this device"));
      ui.body.addView(ui.art(ui.item, 210),
          new LinearLayout.LayoutParams(-1, ui.dp(210)));
      ui.gap(ui.body, 12);
      ui.paired(ui.body, "▶ Play all", () -> ui.playList(visibleItems, false),
          "Shuffle", () -> ui.playList(visibleItems, true));
    } else {
      ui.heading("Your music", PremiereLocale.plural(ui.a, "premiere_track_count",
          ui.audios.length, ui.audios.length) + " · " + PremiereLocale.text(ui.a, "On this device"));
      LinearLayout tabs = ui.row();
      for (String name : new String[]{"Artists", "Albums", "Tracks", "Genres"}) {
        TextView tab = ui.text(name, 12, name.equals(ui.audioTab) ? ui.orange : ui.muted);
        tab.setGravity(Gravity.CENTER);
        tab.setMinHeight(ui.dp(48));
        tab.setContentDescription(PremiereLocale.text(ui.a, name));
        tab.setSelected(name.equals(ui.audioTab));
        tab.setFocusable(true);
        tab.setAccessibilityDelegate(new View.AccessibilityDelegate() {
          @Override public void onInitializeAccessibilityNodeInfo(
              View host, android.view.accessibility.AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName("android.widget.Button");
            info.setSelected(name.equals(ui.audioTab));
          }
        });
        tab.setOnClickListener(view -> {
          ui.audioTab = name;
          ui.categoryLimit = 40;
          ui.resetScrollOnRender = true;
          ui.render();
        });
        tabs.addView(tab, new LinearLayout.LayoutParams(0, ui.dp(48), 1));
      }
      ui.body.addView(tabs);
      ui.gap(ui.body, 8);
      ui.paired(ui.body, "▶ Play all", () -> ui.playList(visibleItems, false),
          "Shuffle", () -> ui.playList(visibleItems, true));
    }

    if (items.length == 0) {
      TextView hint = ui.text(videos
          ? "No videos match this filter. Change the filter in Display options."
          : "No music found. Add a music folder in Browse.", 14, ui.muted);
      hint.setPadding(ui.dp(20), ui.dp(22), ui.dp(20), ui.dp(22));
      ui.base.addView(hint, new LinearLayout.LayoutParams(-1, 0, 1));
      return;
    }

    MediaRecyclerView list = new MediaRecyclerView(ui.a, posters);
    list.setId(View.generateViewId());
    list.setBackgroundColor(ui.bg);
    list.setVerticalScrollBarEnabled(true);
    list.setClipToPadding(false);
    list.setPadding(ui.dp(20), 0, ui.dp(20), ui.dp(8));
    list.setAdapter(new MediaAdapter(ui, items, posters));
    ui.virtualList = list;
    ui.base.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    if (savedPosition > 0 || savedTop != 0 || (anchorKey != null && !anchorKey.isEmpty())) {
      int position = LibraryPosition.restore(savedPosition, list.getCount(), anchorKey,
          row -> itemKey(visibleItems[row]));
      list.post(() -> {
        if (ui.virtualList == list && list.isAttachedToWindow()) {
          list.setSelectionFromTop(position, savedTop);
        }
      });
    }
  }

  /** Bridge viewport state into Premiere's existing navigation stack. */
  static final class MediaRecyclerView extends RecyclerView {
    final LinearLayoutManager linearManager;
    MediaRecyclerView(android.content.Context context, boolean posters) {
      super(context);
      linearManager = posters ? new GridLayoutManager(context, 2)
          : new LinearLayoutManager(context);
      setLayoutManager(linearManager);
      setItemAnimator(null); // Refreshes must not jump or animate an offscreen anchor.
      setHasFixedSize(false); // Titles vary with font scaling and translations.
    }

    @Override public MediaAdapter getAdapter() {
      return (MediaAdapter) super.getAdapter();
    }

    int getCount() { return getAdapter() == null ? 0 : getAdapter().getItemCount(); }

    int getFirstVisiblePosition() {
      return Math.max(0, linearManager.findFirstVisibleItemPosition());
    }

    /** Obtain the offset from the same adapter row used as the anchor. */
    int getFirstVisibleOffset() {
      int first = linearManager.findFirstVisibleItemPosition();
      View child = first < 0 ? null : linearManager.findViewByPosition(first);
      return child == null ? 0 : child.getTop();
    }

    int getPositionForView(View view) {
      RecyclerView.ViewHolder holder = findContainingViewHolder(view);
      return holder == null ? -1 : holder.getAdapterPosition();
    }

    void setSelectionFromTop(int row, int offset) {
      linearManager.scrollToPositionWithOffset(row, offset);
    }
  }

  static final class MediaAdapter extends ListAdapter<Object, MediaHolder> {
    private static final DiffUtil.ItemCallback<Object> MEDIA_DIFF =
        new DiffUtil.ItemCallback<Object>() {
          @Override public boolean areItemsTheSame(Object oldItem, Object newItem) {
            return itemKey(oldItem).equals(itemKey(newItem));
          }
          @Override public boolean areContentsTheSame(Object oldItem, Object newItem) {
            return Premiere.title(oldItem).equals(Premiere.title(newItem))
                && Premiere.number(Premiere.invoke(oldItem, "getLength"))
                    == Premiere.number(Premiere.invoke(newItem, "getLength"))
                && Premiere.str(Premiere.invoke(oldItem, "getArtistName"))
                    .equals(Premiere.str(Premiere.invoke(newItem, "getArtistName")));
          }
        };
    final Premiere.UI ui;
    final boolean poster;
    MediaAdapter(Premiere.UI ui, Object[] media, boolean poster) {
      super(MEDIA_DIFF);
      this.ui = ui;
      this.poster = poster;
      setHasStableIds(true);
      submitList(Arrays.asList(media));
    }

    @Override public Object getItem(int index) { return super.getItem(index); }

    @Override public long getItemId(int index) {
      return LibraryPosition.stableId(itemKey(getItem(index)));
    }

    @Override public MediaHolder onCreateViewHolder(ViewGroup parent, int type) {
      MediaCell cell = new MediaCell(ui, poster);
      cell.view.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
      return new MediaHolder(cell);
    }

    @Override public void onBindViewHolder(MediaHolder holder, int index) {
      holder.cell.bind(getItem(index));
    }
  }

  static final class MediaHolder extends RecyclerView.ViewHolder {
    final MediaCell cell;
    MediaHolder(MediaCell cell) {
      super(cell.view);
      this.cell = cell;
    }
  }

  private static final class MediaCell {
    final Premiere.UI ui;
    final boolean poster;
    final LinearLayout view;
    final ImageView image;
    final TextView name, description;
    final View more;
    final TextView play;

    MediaCell(Premiere.UI ui, boolean poster) {
      this.ui = ui;
      this.poster = poster;
      view = ui.row();
      view.setOrientation(poster ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
      view.setGravity(poster ? Gravity.TOP : Gravity.CENTER_VERTICAL);
      view.setPadding(poster ? ui.dp(3) : 0, ui.dp(7),
          poster ? ui.dp(3) : 0, ui.dp(7));
      view.setFocusable(true);
      view.setTag(LibraryPosition.FOCUS_CARD);
      image = new ImageView(ui.a);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      image.setBackgroundColor(ui.panel);
      // The actionable card already speaks the title; separate artwork focus duplicates it.
      image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      if (poster) {
        int side = (ui.a.getResources().getDisplayMetrics().widthPixels
            - ui.dp(56)) / 2;
        int height = Math.max(ui.dp(128), Math.round(side * 1.5f));
        view.addView(image, new LinearLayout.LayoutParams(-1, height));
      } else {
        view.addView(image, new LinearLayout.LayoutParams(ui.dp(64), ui.dp(58)));
      }

      LinearLayout copy = ui.column();
      copy.setPadding(poster ? 0 : ui.dp(12), poster ? ui.dp(9) : 0,
          ui.dp(4), poster ? 0 : ui.dp(1));
      name = ui.text("", poster ? 13 : 14, ui.ink);
      name.setMaxLines(2);
      name.setEllipsize(TextUtils.TruncateAt.END);
      copy.addView(name);
      ui.gap(copy, poster ? 5 : 7);
      description = ui.text("", 11, ui.muted);
      description.setMaxLines(2);
      description.setEllipsize(TextUtils.TruncateAt.END);
      copy.addView(description);
      if (poster) {
        view.addView(copy, new LinearLayout.LayoutParams(-1, -2));
      } else {
        view.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
      }

      more = ui.icon("more", "Media actions", () -> {});
      more.setTag(LibraryPosition.FOCUS_ACTIONS);
      if (poster) {
        LinearLayout actions = ui.row();
        play = ui.text("▶ Play", 12, ui.orange);
        play.setFocusable(true);
        play.setTag(LibraryPosition.FOCUS_PLAY);
        play.setGravity(Gravity.CENTER_VERTICAL);
        play.setMinHeight(ui.dp(48));
        actions.addView(play, new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        actions.addView(more, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        view.addView(actions);
      } else {
        play = null;
        view.addView(more, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
      }
    }

    void bind(Object media) {
      String title = Premiere.title(media);
      boolean video = Premiere.number(Premiere.invoke(media, "getType")) == 0;
      name.setText(poster ? ArtworkMatch.videoTitle(title) : title);
      String artist = Premiere.str(Premiere.invoke(media, "getArtistName"));
      description.setText(poster
          ? ui.format(media)
          : (artist.isEmpty() ? ui.format(media) : artist)
              + " · " + Premiere.time(Premiere.number(Premiere.invoke(media, "getLength"))));

      image.setImageDrawable(null);
      // Decoration is intentionally excluded from the accessibility traversal.
      String fingerprint = ArtworkMatch.itemIdentity(
          Premiere.str(Premiere.invoke(media, "getUri")),
          Premiere.str(Premiere.invoke(media, "getId")),
          Premiere.str(Premiere.invoke(media, "getTitle")),
          Premiere.str(Premiere.invoke(media, "getDate")),
          Premiere.number(Premiere.invoke(media, "getLength")));
      Artwork.resolve(ui.a.getApplicationContext(), media, fingerprint, image);

      view.setContentDescription(PremiereLocale.text(ui.a, video ? "▶ Play" : "Play audio")
          + " " + title);
      view.setOnClickListener(v -> ui.play(media));
      view.setOnLongClickListener(v -> { ui.actions(media); return true; });
      more.setContentDescription(PremiereLocale.text(ui.a, "Media actions") + ": " + title);
      more.setOnClickListener(v -> ui.actions(media));
      if (play != null) {
        play.setContentDescription(PremiereLocale.text(ui.a, "▶ Play") + " " + title);
        play.setOnClickListener(v -> ui.play(media));
      }
    }
  }
}
