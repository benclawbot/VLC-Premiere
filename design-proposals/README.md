# VLC for Android — four visual proposals

Four alternative visual directions for the decoded VLC Android app, plus a source-grounded screen walkthrough. The gallery provides a portrait library and a landscape playback proposal for each. It is a standalone visual prototype; the original manifest, resources, Java and Smali files are unchanged. Playback is simulated. Navigation, casting and file opening show explicit prototype feedback rather than performing native actions.

## Review

From `/home/tom/Downloads/Software/VLC-source-rebuild`:

```sh
python3 -m http.server 8765 --bind 127.0.0.1 --directory design-proposals
```

Open [the local gallery](http://127.0.0.1:8765/). The server serves only the design-proposals folder. Premiere image export needs the local server to embed its styles and assets. No packages, external fonts, CDN or build step are required.

- Switch **Library / Playback** to compare screens.
- Use the corner focus button to enlarge one direction; **All four** restores comparison.
- Search visible sample media; Collection also offers documentary and short-film filters.
- Click artwork to preview that item. Playback supports simulated play/pause, ten-second skips, keyboard seeking, subtitles and speed selection.
- **Save image** renders the current review as a PNG; **Download PNG** saves it.

The [Premiere · every screen](premiere/) presents 109 designed Android phone screens and meaningful states across nine areas. Search and filter screens, explore each phone, navigate between flows, and export a bounded PNG board. Native source references are listed in [coverage.json](premiere/coverage.json). The saved [Premiere overview](premiere/previews/overview.png) shows six representative interfaces; all 109 are visible in the interactive gallery.

Saved comparison boards: [Library](previews/library.png) and [Playback](previews/playback.png).

## Directions and references

| Direction | Visual approach | Inspiration | Best fit / trade-off |
|---|---|---|---|
| Cinema | Charcoal, amber, a large resume card and artwork | [Infuse](https://firecore.com/infuse) | Film libraries with artwork; fewer items per screen |
| Quiet | Ivory, clean sans typography, thin dividers, file rows and a separate playback dock | [IINA](https://www.iina.io/) | Everyday local files; less immersive library atmosphere |
| Collection | Navy, mint, plum collection tiles and compact shelves | [Plex library view](https://support.plex.tv/articles/200392126-using-the-library-view/) | Organized collections; an extra grouping step |
| Studio | Graphite, teal selection, monospaced filenames, metadata and an orange playhead | [DaVinci Resolve](https://www.blackmagicdesign.com/products/davinciresolve/edit) | Detailed playback control; higher information density |

These are interpretations of interface principles, not copies of those applications. Studio remains a player and does not add an editing workflow. The library titles, filenames and playback metadata are fictional samples.

## Source grounding

- `../res/menu/bottom_navigation.xml`: Video, Audio, Browse, Playlists and More.
- `../res/layout/video_grid.xml` and `video_grid_card.xml`: searchable media library, artwork, duration, resolution and progress.
- `../res/layout/player_hud.xml`: playback controls and seeking.
- `../README.md`: decoded Android package identity and reconstruction limitations.

## Design tokens

Each `.device` theme defines `--bg`, `--fg`, `--muted`, `--accent`, `--line` and `--soft` in `styles.css`.

| Direction | Background | Accent | Additional color |
|---|---|---|---|
| Cinema | `#171717` | `#F46B32` | `#E7DDCC` |
| Quiet | `#F8F6F1` | `#B84E24` | `#23221F` |
| Collection | `#141C2B` | `#80E1C0` | `#AB91C7` |
| Studio | `#191B1E` | `#55C7BC` | `#ED875D` |

The proposal themes are deliberately fixed for comparison, regardless of OS light/dark preference. Gallery typography uses system sans and Georgia. Controls are inline SVG; the cone mark is an original SVG illustration. Portrait libraries use 300 × 620 CSS-pixel frames on wide screens; landscape frames are 16:9. Narrow screens stack proposals. Reduced motion is respected.

## Local artwork

Six illustrative photographs are stored in `assets/`. Source image-service URLs (not photographer credit or photo-page identifiers):

- `golden-hour.jpg`: https://images.unsplash.com/photo-1470252649378-9c29740c9fa8
- `alpine.jpg`: https://images.unsplash.com/photo-1500530855697-b586d89ba3ee
- `coast.jpg`: https://images.unsplash.com/photo-1518837695005-2083093ee35b
- `night-sky.jpg`: https://images.unsplash.com/photo-1519608487953-e999c86e7455
- `desert.jpg`: https://images.unsplash.com/photo-1500534623283-312aade485b7
- `forest.jpg`: https://images.unsplash.com/photo-1511497584788-876760111969

Filenames are illustrative asset keys, not factual descriptions of every photo. Photographer attribution was not recovered during download; establish it before using these images in a released product.

## Verification — 2026-10-09

The orchestrator inspected the final rendered UI and ran these checks independently of worker claims:

- `node --check app.js`: passed.
- HTML local references, six artwork files and HTTP status for all nine runtime resources: passed.
- Four landscape ratios: 1.778, each 656 pixels wide at the tested desktop viewport.
- In all four styles: play/pause, speed, subtitle toggle, ten-second skip and ArrowRight seeking worked; seeking advanced from 38:14 to 38:25 after the skip plus key press.
- Search matched Blue Between; all four empty searches showed an empty state. Collection documentary filter selected the two documentary samples.
- Focus/restore and navigation feedback: passed.
- At a 320-pixel viewport, the initial toolbar overflow was fixed; the final document width was 305 pixels (including the browser scrollbar allowance), with no horizontal overflow. A 390-pixel playback layout also passed.
- Final browser console: no warnings or errors.
- Both PNG boards were downloaded, saved in `previews/` and visually inspected.

No native Android build, APK modification or real media playback is part of these proposals.
