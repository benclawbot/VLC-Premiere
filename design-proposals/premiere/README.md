# Premiere · every screen

Selected visual direction for VLC Android 3.7.1. Open http://127.0.0.1:8765/premiere/ while the proposal server is running. This is a static visual proposal, with illustrative media and simulated controls; it does not modify or rebuild the Android app.

109 designed screens and meaningful states:

| Area | Count |
| --- | ---: |
| Video | 8 |
| Audio | 10 |
| Browse | 9 |
| Playlists | 4 |
| Playback | 22 |
| More & settings | 25 |
| First run | 6 |
| Remote pairing | 7 |
| Dialogs | 18 |

Each screen has a distinct composition and relevant content. Playback includes landscape and portrait players, tracks, subtitle search/download, speed, synchronization, sleep timer, cast, equalizer, bookmarks, chapters, scaling and gesture/locked states. Settings include video/audio controls, subtitles, casting, remote access, parental PIN, Android Auto, widget preferences, diagnostics, permissions and app information. Context sheets appear over the related app screen.

Use category filters, screen search, or “Explore screen” to focus a phone. App navigation and linked actions move between screen designs. Previous/Next traverses the selected category/search results; navigations outside the selected filter remain visible in focus. Deep links use the screen ID in the URL hash, e.g. `#subtitle-tracks`. Preference switches, radio choices, sliders, player transport, timing steppers, equalizer reset and PIN entry are simulated. Other actions show explicit preview feedback. No media is played, permission granted, connection established, file removed, or message sent.

Save overview image exports six representative interfaces as an actual PNG design board. Save view image exports up to twelve cards of the current category or search, with an explicit count. Focus mode exports the selected screen. All 109 screens remain available in the interactive gallery. Each phone scrolls internally when content exceeds its frame.

`coverage.json` maps every card to an existing decoded native layout, menu, preference file, or class. It is an inventory of the phone proposal, not a claim to capture every runtime combination or every value in every preference dialog. Separate TV, car display, home-screen widget layouts and remote browser UI are outside the established Android phone proposal; phone-side Auto and widget settings are included. Android-owned permission prompts and pickers are represented through app-side education and selection designs.

Files: `index.html`, `styles.css`, `screens.js` (inventory), `app.js` (renderers and interactions), `coverage.json`. Photos are reused from `../assets/`. The original four directions and four Cinema variations remain available.
