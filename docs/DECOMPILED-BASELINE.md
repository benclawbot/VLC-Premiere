# VLC.apk — source code reconstruction

**Input:** `Downloads/VLC.apk` (application/octet-stream, 48.2 MB, SHA-256
`17054edc3ff9fde999dc6c5cacfdef3a95185494e5ff6f6ecebde75f209c2484`)
**Deliverable:** reconstructed (decompiled + de-dexed) Java source for the app's own code, the full decoded Smali tree, decoded resources, the decoded manifest, and a findings report — all under `VLC-source-rebuild/`.

## Toolchain used (all installed to /home/tom/re/work)
| Step | Tool | Version/notes |
|---|---|---|
| JVM | Temurin JDK 17 (jre) | `jdk-17.0.11+9` |
| APK decode (manifest, resources, Smali) | apktool | 3.0.3 |
| dex → jar | dex2jar (`dex-tools-v2.4`) | v2.4 |
| jar → Java source | CFR | 0.152 |
| Optional quick manifest view | stdlib zipfile | — |

No emulator, no APK launch, no network content — pure static analysis of the
shipment artifact.

## Layout of `VLC-source-rebuild/`
```
VLC-source-rebuild/
├── src/                          ← reconstructed Java source of the app (2,233 files)
│   └── org/videolan/...           (television, moviepedia, libvlc, vlc.gui,
│       vlc.remoteaccessserver,
│       vlc.viewmodels, tools,
│       resources.opensubtitles, ...)
├── smali/                        ← apktool-decoded Smali (6 dex → 32,207 .smali files)
├── res/  assets/  unknown/       ← decoded Android resources & assets
├── meta/
│   ├── AndroidManifest.xml        ← decoded manifest (69 KB, XML)
│   ├── apktool.yml                ← minSdk 30 / targetSdk 36 / version 3.7.1 / code 13070109
│   └── apk-info.txt               ← SHA-256 + size of the input APK
├── libs/                         ← third-party libraries as dex2jar .jar snapshots
│   ├── classes.bin.jar  classes2.bin.jar  classes3.bin.jar  classes4.bin.jar
│   ├── classes5.bin.jar classes6.bin.jar
└── README.md                      ← this report
```

## APK identity (from decoded manifest)
- Package: `org.videolan.vlc` | Label: `VLC` / `VLC for Android`
- Version: `3.7.1` (code `13070109`)
- SDK: minSdk 30 · targetSdk 36 · compileSdk 36
- installLocation `auto`, logo `@mipmap/ic_launcher`
- Launcher: `org.videolan.vlc.StartActivity` (exported; MAIN action with LAUNCHER + LEANBACK_LAUNCHER categories) — supports phone and Android-TV launchers.

## Decorated components (40 activity classes)
- **TV UI** (`org.videolan.television.ui`): MainTvActivity, OnboardingActivity,
  DetailsActivity, MediaScrapingTvActivity, TVActivity, VerticalGridActivity,
  MediaListActivity, SearchActivity, AudioPlayerActivity, PreferencesActivity,
  AboutActivity, ColorPickerActivity, ConfirmationTvActivity, MediaScrapingTvshowDetailsActivity
- **Phone GUI** (`org.videolan.vlc.gui`): MainActivity, LibrariesActivity,
  SecondaryActivity, InfoActivity, HeaderMediaListActivity, SearchActivity,
  EqualizerSettingsActivity, VideoPlayerActivity, AuthorsActivity, BetaWelcomeActivity,
  DebugLogActivity, DialogActivity, FeedbackActivity, FilePickerActivity,
  OnboardingActivity, PinCodeActivity, OTPCodeActivity, MediaListActivity,
  PreferencesActivity, PreferenceSearchActivity, MiniPlayerConfigureActivity,
  VideoBrowserFragment, AudioMediaSwitcher, EqualizerViewModel, RendererLiveData
- **Remote access** (`org.videolan.vlc.remoteaccessserver`):
  RemoteAccessShareActivity, RemoteAccessOnboardingActivity, RemoteAccessService,
  RemoteAccessServer GUI
- **Movie Pedia** (`org.videolan.moviepedia`): MediaScrapingActivity,
  MediaScrapingTvActivity (shows/episodes info scraping)
- **Widgets/other**: MiniPlayerConfigureActivity, StoragesMonitor, TvReceiver,
  VLCAppWidgetProvider(Black/White), ArtworkProvider, FileProvider,
  MediaParsingService, RecommendationsService, PlaybackService,
  PreviewVideoInputService, DebugLogService, RemoteAccessService

## Decorated components — services/receivers/providers
- Services (7): `DebugLogService`, `MediaParsingService`, `PlaybackService`,
  `PreviewVideoInputService`, `RecommendationsService`,
  `RemoteAccessService`, `androidx.room.MultiInstanceInvalidationService`
- Receivers (8): `StoragesMonitor`, `TvReceiver`, `MiniPlayerAppWidgetProvider`,
  `VLCAppWidgetProviderBlack`, `VLCAppWidgetProviderWhite`, `MediaButtonReceiver`
  (androidx), `CarAppNotificationReceiver` (androidx), `ProfileInstallReceiver`
  (androidx)
- Providers (6): `TVSearchProvider`, `FileProvider`, `ArtworkProvider`,
  `InitializationProvider` (androidx.startup), `CarAppConnectionProvider`,
  `androidx.core.content.FileProvider`

## Permissions (29)
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
`FOREGROUND_SERVICE_DATA_SYNC`, `BLUETOOTH_CONNECT`, `READ_LOGS`,
`MANAGE_EXTERNAL_STORAGE`, `VIBRATE`, `WRITE_SETTINGS`, `READ_EXTERNAL_STORAGE`,
`WRITE_EXTERNAL_STORAGE`, `INTERNET`, `RECEIVE_BOOT_COMPLETED`, `RECORD_AUDIO`,
`ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE`, `WAKE_LOCK`,
`MODIFY_AUDIO_SETTINGS`, `BLUETOOTH`, `SYSTEM_ALERT_WINDOW`,
`READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO`, `READ_MEDIA_IMAGES`, `POST_NOTIFICATIONS`,
`org.videolan.vlc.permission.READ_EXTENSION_DATA`,
`com.android.providers.tv.permission.READ_EPG_DATA`,
`com.android.providers.tv.permission.WRITE_EPG_DATA`,
`org.videolan.vlc.MEDIA`,
`org.videolan.vlc.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
(+ 18 implicit requests), `READ_LOGS`, `MANAGE_EXTERNAL_STORAGE` — heavy storage +
foreground-media-service + media-projection surface.

## Feature inventory (evidence from strings / resources / manifest)
- **Media playback**: full VLC stack — ffmpeg family components, `dav1d 1.5.3`
  (AV1), `libx264`/`libx265` (H.264/HEVC), libmp3lame, libvorbis, libtheora,
  librawvideo, libwebp, libflac, libfreetype, libiconv, libspeex, libsndfile,
  etc. (`strings.xml` versions; `res/values/dav1d_version`, `libdash`, `libx264`,
  `libavcodec`, ...)
- **Screen casting**: Chromecast with per-session conversion quality setting
  (`casting_conversion_quality_title/summary` in 10+ localizations),
  `androidx.car.app` automotive slice (minCarApiLevel 3, `automotive_app_desc`),
  `android.software.leanback` TV feature, `android.hardware.microphone`
  (TV voice search), `android.hardware.bluetooth` (27 file hits: A2DP/Audio).
- **Screen/device**: `android.hardware.touchscreen` (optional), `android.hardware.microphone`
  (optional), `android.allow_multiple_resumed_activities`, Samsung/LGE
  multi-window metadata (`com.samsung.android.sdk.multiwindow.enable`,
  `com.lge.support.SPLIT_WINDOW`), `android.max_aspect 3.0`.
- **Sound**: equalizer UI + settings (`EqualizerSettingsActivity`), audio ducking/
  boost prefs (`audio_ducking_*`, `audio_boost_*`), audio delay, noise reduction
  strings (`noise`, `reduce`), headset detection.
- **Streaming/network**: `ORG` network streams, `READ_EXTENSION_DATA` permission,
  `opensubtitles` subtitle fetching (`org.videolan.resources.opensubtitles`
  classes), libdash, `androidx.media`, `androidx.media.session` (media buttons),
  `androidx.car.app` notifications, `MediaScrapingService` (libraries/epg).
- **Settings & UX**: preferences (material3 theme, night mode), onboarding,
  pin/OTP unlock, beta welcome, debug log service, libraries screen, authors,
  feedback, app lifecycle (ProcessLifecycleInitializer), profile install,
  emoji2, searchable (`searchable.xml`), shortcuts (`shortcuts.xml`),
  app widgets (black/white/mini), slideshow, workmanager background jobs.
- **i18n**: 40+ localized string pools (en, de, fr, es, it, pt, ru, ja, zh-CN,
  ko, nl, pl, sv, tr, ar, fa, ...).
- **Third-party libs recovered from the bytecode** (in `libs/`):
  androidx (appcompat/constraintlayout/room/media/startup/emoji2/lifecycle/
  profileinstaller/car/security), Kotlin stdlib + `kotlinx.coroutines`,
  `okhttp3`/`okio`, `retrofit2` (classes6), `com.google.android.play`
  core, `org.bouncycastle` (post-quantum crypto classes), `org.webmproject`,
  `org.xmlpull`, `org.json`, `org.w3c.dom`, `org.luckymine`
  (media-library stub), `tv.danmaku` (video-render stub), commons-* tooling,
  and proprietary VLC shell packages (`org.videolan.television`,
  `org.videolan.moviepedia`, `org.videolan.vlc`, `org.videolan.vlc.libvlc`,
  `org.videolan.vlc.tools`, `org.videolan.vlc.wizard`, ...).

## Verification
- Decompile chain exercised end-to-end: `apktool d` → `dex2jar` (6 dex files)
  → CFR 0.152 with package filter `org.videolan`, yielding 37 + 3 + 543 + 1650 =
  **2,233 Java files**. This is the observed file count, not a successful Java/Android build.
- No emulator; the APK was not launched. The reconstructed Java is a lossy
  bytecode decompilation, not the original Kotlin/Java source; names, structure,
  comments, and some language-level constructs cannot be recovered exactly.

## Caveats
- `classes.dex` of the APK holds the **support libraries** (androidx, kotlin,
  android framework slices); the app code itself lives across
  `classes3..6.dex` (and a few classes in `classes4.dex`).
- Third-party library source was **not** decompiled — the `.jar` snapshots in
  `libs/` are the reversible bytecode; the canonical sources are available in
  any AOSP/SDK.
- No native `.so` files are present in this APK archive (verified by listing
  the APK members). This only establishes that the package carries no `.so`
  entries; it does not establish how all playback functionality is implemented.
