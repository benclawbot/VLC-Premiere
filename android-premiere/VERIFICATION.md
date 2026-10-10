# Current release verification — 2026-10-11 UTC

The selected Premiere design and VLC engine remain in the packaged builds. Movie/TV network work now has its own queue, independent of music searches. Media callbacks are coalesced, unchanged library snapshots skip page reconstruction, and genuine refreshes retain the current scroll position and expanded page limit. Item identity excludes asynchronously generated artwork paths. The masked TMDB settings field saves atomically in private storage and enables movie lookup.

- 61 JVM matching/snapshot assertions and five Python probe fixtures passed. Native Java/D8 compilation passed before packaging. Both signed APKs passed v3 signature verification, ZIP integrity, native library counts and 16 KiB alignment. Native source and packaged DEX match the recorded manifest.
- The installed phone base APK was pulled and exactly matched the delivered ARM64 SHA-256. The private TMDB token remained enabled after force-stop/restart and an in-place APK update. It is excluded from repository and package payloads.
- On Nothing A001 / Android 16, all 337 currently scanned tracks expanded through limits 160, 240, 320 and 400. Each expansion retained the first visible row and its exact screen coordinates. Four five-second checkpoints retained the same position for 20 seconds. The complete list remained accessible.
- Actual movie posters and music covers appear in the release screenshots captured directly from the phone. See [Video](previews/phone/video-library.png), [movie catalogue](previews/phone/video-catalogue.png) and [Audio](previews/phone/audio-library.png), with [capture provenance](previews/phone/capture.json).
- Early phone automation was affected by ongoing touch scrolling and changes to the current screen. The final run used settled wheel input while the phone was free, then passed all assertions. These failed test attempts were inspected rather than treated as successful checks.
- Build setup now uses portable Java/Apktool discovery, committed artwork assets and a verified original-APK smali fallback. Syntax checks and real fresh-source preparation passed against the checksum-verified upstream APK, including its six DEX trees and ten existing patch targets. That check caught the startup class moving between DEX directories; the build now resolves it by package. A complete fresh-checkout APK build was not rerun for these setup-only changes.

[Machine-readable release verification](verification/2026-10-11.json). Detailed local handset reports remain in ignored `build/artwork-handset/`. Playback and navigation evidence below refers to its stated earlier builds; those sources are unchanged by the list/poster correction.

| Package | SHA-256 |
| --- | --- |
| VLC-Premiere-arm64.apk | `cd88f0a3f73e2798c31e4a021c2cf4258471ce6baa4fd9383148aa43f81e452f` |
| VLC-Premiere-Universal.apk | `956f76b16688dc812470f898c47cb5fc1a98af5950fcd67fbb739c3e0150e075` |

Native source aggregate: `2b180698b4e2a45b67dfa7900f1d0b49fd9d47bc37a44dded2fe6db481f194ff`. Native DEX: `2e94a316726876dfb464e10c6610b677eec40ac3c95a722c414926514be39f84`.

Updates preserve private preferences. Uninstalling or clearing app data removes the saved TMDB token. Obsidian memory remains unavailable (`Transport closed`); no vault filesystem bypass was used.

---

# Earlier build and handset verification — 2026-10-10

This update retains the selected Premiere UI and VLC engine. Dynamic artwork now uses MusicBrainz, Deezer and matching YouTube thumbnails, with fuzzy filename/tag clues, visible-card priority, independent provider fallbacks and local caching. Movie/TV posters retain TMDB matching.

- 56 JVM matching assertions and five Python probe fixtures pass. Full native Java compilation/D8, both signed APK signatures, ZIP integrity, native payload and alignment checks pass. A worker-generated parser brace error was caught by compilation, corrected, and the complete compilation passed before signing.
- Only Artwork.java, ArtworkMatch.java and the artwork descriptions/credits in Premiere.java changed in native sources relative to the session baseline. Playback, gestures, navigation and folder-selection code are retained.
- Installed this ARM64 update through ADB on the Nothing A001 / Android 16 phone, preserving app data. The pulled base APK exactly matches the delivered ARM64 SHA-256.
- Public YouTube search identified the actual source of `002 - Klangkuenstler ⧸ December 25 ⧸ 4pm-5pm.mp3`: `OT_MZ3JgNdk`. The on-phone thumbnail matched that original video image (mean absolute RGB error 6.42 after crop/scale) on the first observation, without using the artwork retry action. The adjacent Vinyl Only Set also displayed a source image. Logs show additional background YouTube matches; this does not assert full-library coverage.
- On this final build, the exact YouTube image survived force-stop/restart with music artwork downloads disabled (mean RGB pixel difference 2.18). Online lookups were restored afterward; the audio library returned to 345 tracks after removing the owned fixture. See `build/artwork-handset/youtube-cache-result.json`.
- The earlier limited coverage had two confirmed causes: VLC full filenames including .mp3 were incorrectly treated as conflicting title tags, and MusicBrainz produced HTTP 500/503/timeouts on the handset. The filename parser now handles these titles, and the other providers run independently.
- The fresh installation had no saved TMDB token. The user-authorized token was restored through the masked settings field, validated and stored only in private noBackupFilesDir. Actual Lanterns posters and DJ Shadow Endtroducing art were observed before this update. No credential was added to source or APK; executable/config payload scans pass.
- A newly generated MP3 named `997 - Massive Attack - Teardrp.mp3` had no embedded image or useful tags. After native library refresh, online artwork appeared without opening its card or pressing the artwork retry action. The earlier fallback chose representative artist art; on the final build it displayed a cover for a release containing Teardrop (Urban Moods). This validates non-exact title lookup, not the original source album. Only this owned fixture was removed afterward. This fixture was not discovered on the first startup, so this is evidence of automatic artwork after scanner discovery, not proof of every storage-notification path.

| Package | SHA-256 |
| --- | --- |
| VLC-Premiere-arm64.apk | `8b4e6f89f60ddcf2424dd24048abf77a5cacefa6e98a55127dcb3d3686079267` |
| VLC-Premiere-Universal.apk | `4c9a28acbd75c446d4fa43f0a90d5015a3a18349e953c277ff51bdcfae7c74ee` |

Native source aggregate: `dcc8b35f29625d16661b737a54542e3ed529e2058c4ee54e557edeacf8456791`. Native DEX: `ef0c6832e48b5e44adafa085c887f0be1e3c568cf032fe86cff6e279b2639ac2`.

Current evidence: `build/artwork-handset/youtube-phone-result.json`, `youtube-live-matches.png`, `installed-youtube-artifact.json` and `fuzzy-newfile-result.json`. Earlier playback/cache/storage reports below refer to their stated earlier builds. Public YouTube result markup can change or return consent pages; provider fallback is retained. Closest-match music art can represent a different release, especially title-only files. Obsidian memory remains unavailable (`Transport closed`); no vault filesystem bypass was used.

---

# Earlier handset verification — 2026-10-10

The signed APKs below supersede the earlier package hashes. Current implementation and limitations are recorded in [ARTWORK-IMPLEMENTATION.md](ARTWORK-IMPLEMENTATION.md).

- Dynamic movie/TV posters, album covers, filename + embedded-tag fuzzy matching, private on-device token settings and bounded local caching are implemented.
- Actual phone posters: Project Hail Mary, Spider-Man: Brand New Day, Lanterns. The untagged numbered DJ Shadow track resolves to Endtroducing artwork.
- Music was visible in Browse but excluded from the scanner. With Music included, Audio shows 345 tracks after restart. Add/remove validation gave 345 → 346 → 345 using only a temporary test fixture.
- Final-build MP4/MKV/MP3 advancement: 6.151 s / 6.200 s / 6.372 s before pause. Left-edge back from All videos passes.
- Cached posters and the square music cover survive force-stop/restart with online lookup disabled; settings were restored afterward.
- 29 native matching assertions, five offline probe fixtures, Java/D8 compilation and both signed package validations pass. Installed base APK/source/DEX equality and credential-exclusion scans pass.

Reports and screenshots: `build/artwork-handset/`. The previous playback/root-cause report is retained below as historical context.

```text
582d91c553b776a6986542ba8cd73a297b23d01a4c1e886779eb7b5945a1fade  VLC-Premiere-Universal.apk
050ea2f5a9801880e0868f2da1c03eaf141e39032b3d9c184b81254ec01d2761  VLC-Premiere-arm64.apk
```

Native source aggregate: `895e112773733562823141edda4be7f0b4fc92fe2caee391d68a993b28300f12`. Native DEX: `6e8b891e8f84e89a5742a7ba28db83095335ec0e8d32ee434c3f4bd89e9e630f`.

---

# Earlier playback and navigation handset verification — 2026-10-10

This section supersedes the archived emulator report below. The final ARM64 APK was rebuilt and installed in place on the user's Nothing A001 (Android 16, MediaTek MT6878); app data and media were retained.

## Playback diagnosis and correction

The native video gesture tutorial initializes a current tip, and `VideoPlayerActivity.onPlaying()` pauses when a tip is active. Premiere covered that tutorial, leaving playback permanently paused. The native audio/playlist tutorial similarly calls `PlaybackService.pause()` inside `AudioPlayerContainerActivity.showTipViewIfNeeded()`, even though its controls are covered. Both obsolete tutorial entry points are now skipped for this rebuilt presentation, without changing the user's tutorial-completion preferences. VLC's media engine, demuxers and decoder libraries remain packaged.

On the final installed APK, real files on the phone autoplayed: a 1080p H.264 MP4 movie, a 1080p MKV episode, a camera MP4, and MP3. The two downloaded videos were launched from Premiere's All videos rows; the camera/audio checks used VLC's exported VIEW handler. MP3 now plays immediately after the camera video in the same process. Tapping the actual MP3 in Browse → Music also passed: it advanced 5.300 seconds before pause, recorded in `build/handset-final/mp3-browse-result.json`. Screen captures show decoded movie/camera content and advancing UI time. A subsequent pause records actual progress (the MediaSession position while PLAYING is a state-change snapshot, not a continuously updated counter).

| Real file sample | Initial position | Position after pause | Advancement |
| --- | ---: | ---: | ---: |
| h264-mp4 | 0.000s | 5.152s | 5.152s |
| mkv | 0.000s | 5.199s | 5.199s |
| camera-mp4 | 30.595s | 35.897s | 5.302s |
| mp3 | 0.000s | 5.354s | 5.354s |

Evidence: `build/handset-final/playback-result.json`, `*-final-start.txt`, `*-final-playing.txt`, `*-final-paused.txt` and `*-final-frame0/1.png`. Reproduction driver: `build/handset_playback_check.py`.

## Navigation and menu loading

Premiere registers a lifecycle-owned AndroidX `OnBackPressedCallback` after the native callbacks, consumes its internal screen history, and delegates root/player back behavior to VLC. Patching the older `onBackPressed()` override alone was insufficient on Android 16; a narrow hook in MainActivity's native callback also failed on the phone and was replaced by the lifecycle callback.

Library/history/music category/detail queries now run off the UI thread; bottom navigation renders immediately, reflection method lists are cached, and large lists render in batches with Show more. Startup waits for medialibrary initialization before querying; a cold launch automatically shows the three scanned videos. Preference screens no longer trigger an unrelated media-library query.

Handset assertions and frame statistics are recorded in `build/handset-final/navigation-result.json`, `gesture-*.xml`, `menu-*.xml`, and `menu-gfxinfo.txt`. These verify left/right edge back from video lists, media information and nested video settings, and rendering of all five bottom destinations. This final menu session rendered 264 frames, with 10 marked janky by Android (3.79%) and a 95th-percentile frame time of 26 ms. Frame statistics describe this test session, not a controlled before/after benchmark or a large-library stress test.

## Build and installed artifact

- Java compilation and D8 passed; Apktool/AAPT2 reconstructed the complete package.
- All 2,029 resource XML files parsed. Binding signatures and 1,213 R$id references against 1,702 IDs passed validation.
- ARM64 and Universal APKs passed signature, ZIP integrity, native payload and alignment checks. Build log: `build/handset-complete-build.log`.
- The installed base APK was pulled back and compared by SHA-256 to the delivered ARM64 APK; the equality result is in `build/handset-final/installed-artifact.json`.

```text
2a8b51c75bdd3bdf317e9793a5c562d8bf798054023fb39c19b157b46087875f  VLC-Premiere-Universal.apk
0ed7210ecbd73aca1953079819e6309765d84acdfc82f6f8eac390d099b54c85  VLC-Premiere-arm64.apk
```

Native Java source aggregate: `4b19dfec15e076741e98a3fe91eb1294c2792c5812bf82ae3ce68e2a5b5c0913`. Native DEX: `f35afbaf0d8dc7749e6be44f32c44cea7dcbe3d350f72e93b0dc656e3f16feb3`. Native smali tutorial/back changes are assembled in classes6.dex by the complete rebuild.

## Failures resolved and limits

The first gesture test exited to the launcher; the lifecycle callback now passes the actual edge gestures. Early video tests opened a frozen/paused frame; skipping the covered native tutorial restored playback. An intermediate build played music only on a fresh external launch: bringing MainActivity forward or tapping a Music-folder file paused it at zero due to the covered audio tutorial. The final build skips that tutorial too and passes both the combined movie/camera/MP3 sequence and the normal Browse → Music launch. One test-driver regex assumed numeric Android playback states; it was corrected for Android 16's `PLAYING(3)` format before final checks.

VLC's broad format support is retained, but this is not a claim that every codec/container, corrupt file, subtitle provider, cast device or landscape layout was exercised on the handset. The existing automatic MediaTek software-decoding workaround remains, preserving explicit decoder settings; its battery/performance behavior has not been benchmarked. No media was deleted, no scan roots were changed, and playback was left paused after testing.

## Dynamic artwork investigation

`ARTWORK-RESEARCH.md` documents TMDB movie/TV posters and MusicBrainz/Cover Art Archive album covers, conservative filename/tag matching, storage-change reconciliation, provider queues, app-private disk cache and a Premiere poster-card layout. The local-only `research/artwork_probe.py` scans its runtime directory, extracts ffprobe tags, and produces lookup/cache identities. Five fixtures verify parsing, album-versus-track query identity, Unicode/ambiguity handling and files being added, changed and removed; two actual local videos were also probed. No provider requests were made.

Online artwork retrieval, disk artwork cache and portrait shelves are not implemented in this APK. TMDB requires an application key and attribution; the researched terms limit cache retention. MusicBrainz/CAA public retrieval needs no API key, but MusicBrainz requires an identifying User-Agent and one request per second.

Obsidian memory MCP was retried and returned `Transport closed`; no vault filesystem workaround or memory update was performed.

---

## Archived 2026-10-09 emulator report

The following is retained as historical evidence for the older artifacts and does not describe the current phone verification.

# VLC Premiere APK verification — 2026-10-09

The corrected APK uses native Premiere screen compositions over VLC 3.7.1. The initially rejected resource-only reskin is not the current download.

## Packaging

- Full isolated resource/smali reconstruction passed Apktool/AAPT2 assembly.
- Final Java presentation passed Java 8-target compilation with Java 17 and D8. Seven DEX files are packaged, including the source-matched native presentation.
- XML parsing passed for 2,029 resources. Binding signatures and 1,217 R$id field references were validated.
- Universal and ARM64 variants passed ZIP integrity, 16-KiB native payload alignment and APK signature verification. Universal contains 16 libraries for four ABIs; ARM64 contains four libraries.
- Python build/validation scripts pass py_compile.
- Universal installs successfully on the owned Android 11 x86_64 emulator. Its installed package checksum matches the delivered APK.

## Runtime checks

The test target is only emulator-5580. No personal phone was used. Media fixtures live on this emulator and are absent from the APK.

- A separate ephemeral profile completed welcome, actual Android all-files permission grant, scanning, theme selection and arrival at the empty native library.
- Video library shows actual media, decoder-generated artwork, metadata, recent media and saved resume progress.
- Real H.264/AAC video and MP3 audio playback run through the original VLC engine. Pause, video seek to 0:10, speed 1.25×, track selection, bookmarks and return-to-library resume progress were exercised.
- Artist/track layouts, artwork audio player and queue were captured from the APK. Browse displays real storage and folder data.
- A playlist was created, populated with two genuine media IDs and revisited with persisted counts, layered covers and edit controls.
- Original preference controllers render as grouped Premiere cards. Theme selection persists and renders both light and dark palettes through VLC’s actual restart flow.
- Equalizer enable and preset selection use VLC’s Room repository; the selected Classical preset survives process restart.
- Remote introduction, control, encryption/pairing explanations and shared-content choices were captured. Enabling the original server produces a functional native server-status screen with its actual addresses.
- The original PIN keypad and validation are retained. Creating and confirming a test PIN succeeded. With safe mode enabled, editing a playlist requested the PIN and returned to the edit screen only after successful validation.

## Limits and failures inspected

- Final lock/unlock controls passed. The orientation-setting check did not rotate the final video activity, so landscape rendering remains unverified.
- Not all 109 conditional proposal states were runtime-exercised. The per-state inventory records compiled presentation separately from observed rendering. Network server authentication, subtitle-provider results, real cast hardware, API 33+ notification permissions and rare error/warning states require their corresponding environment.
- Thirteen secondary controller states retain VLC’s view hierarchy with Premiere shared resources. This is not a claim of pixel-identical reconstruction of all 109 proposal cards.
- An early worker-added resource field caused a runtime linkage failure; that abandoned patch was removed, and a resource-field validator now guards the build. An equalizer edit briefly failed compilation; the error was fixed before packaging. Signing rejects a stale source/DEX manifest.
- Android’s accessibility dumper intermittently returned no root on animated remote-onboarding states. Those states were inspected with fresh device screenshots; stale UI bounds were not reused. Several test scripts stopped on assertions about an already-completed activity; subsequent checks used the observed current screen.

## Final artifacts

- `VLC-Premiere-Universal.apk`: 253,813,166 bytes; SHA-256 `5a5921114748cb37a4ef7295c75435aa3dd9fd31eec74ffef25a230f4d95abd6`.
- `VLC-Premiere-arm64.apk`: 99,753,584 bytes; SHA-256 `2db482ea9e2cbc12d672ccc54b5b065551872d8991984b13b73cdc957e98f95d`.

Native source aggregate SHA-256: `6a4df8fea578f9bb7306aa20cbe2cbc1071dd0f6751656e527199975dbb9f38e`.
Native DEX SHA-256: `0a71f54905706c0db73068bd67d284e1efb72997a7b53bc9e1f270b0c0807845`.

The APKs use the persistent local development certificate. They cannot update an official VLC installation in place. Android 11 or later is required.
