# Premiere native presentation

The selected reference remains `design-proposals/premiere/`, with 109 phone screen/state designs. This rebuild replaces the primary screen compositions with Android views; it retains VLC’s application, library, playback service, decoder surface, preference controllers, permissions and remote server.

| Area | Native Premiere presentation | Functional connection |
|---|---|---|
| Video | Large library heading, resume artwork/progress, recent shelf, list/collections/details/search/selection | Actual VLC Medialibrary data and MediaUtils playback |
| Music | Artists/albums/tracks/genres, details, artwork player, queue/bookmarks | Actual media tags, artwork, playback service and queue |
| Browse | Storage cards, free space, folder lists, network/server/stream forms | Local files, SAF, VLC network discovery and stream playback |
| Playlists | Layered artwork, counts, create/detail/edit/add/duplicates | Persistent Medialibrary playlists and media IDs |
| More/settings | Grouped cards, search, theme choices and preference panels | Original AndroidX Preference instances and listeners |
| Playback | Portrait/landscape HUD, track/speed/delay/seek/bookmark/sleep/cast/size tools | Original SurfaceView and PlaybackService; Android picture in picture |
| Equalizer | Presets, enable switch, preamp, bands and custom preset names | VLC EqualizerViewModel and Room repository; saved state survives restart |
| First run | Welcome artwork, permissions, scanning, theme and restricted-access states | Original onboarding model, OS permission requests and completion |
| Remote | Introduction, control, encryption, pairing, content choices and server status | Original onboarding model, server and pairing controllers |
| Auxiliary screens | PIN/OTP, contributors/licenses/support, debug logs | Bound original fields/listeners retained within Premiere headings and panels |

`implementation-coverage.json` accounts for every reference ID: 94 native compositions, 13 styled original VLC controller states, and two Android system surfaces (file selection and picture in picture). The 13 retained controller states are subtitle download/results, delete confirmation, network sign-in/question, external-storage and notification permission prompts, replace-queue/resume prompts, what’s new, license detail, optional-feature warning, and connecting progress. Their bindings and original actions are preserved; they use the shared Premiere resource theme. This is not a claim that every conditional state has been exercised or that all 109 layouts are pixel-identical to the browser reference.

Actual screenshots are in `previews/`; the gallery contains native screenshots, not recreated browser screens. Validation media is only in the owned emulator and ignored `build/fixtures`, not in the APK. The three first-run decorative photographs are packaged as UI assets.

## Build and checks

`bash android-premiere/scripts/build.sh` reconstructs an isolated work tree, applies shared resources and lifecycle hooks, compiles the native Java presentation into `classes7.dex`, validates resource bindings/IDs, builds and signs universal and ARM64 APKs. The source manifest is checked against both source files and packaged DEX before signing, preventing stale APKs from being presented after a failed compile. See `VERIFICATION.md` for the tested scope and limits.
