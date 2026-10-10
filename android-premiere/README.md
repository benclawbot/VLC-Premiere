# Android implementation

This directory contains the native Premiere phone interface and the scripts used to rebuild it over the VLC 3.7.1 Android engine. The presentation includes video and audio libraries, media artwork, search, playback, navigation, and settings while retaining VLC's media services and decoder libraries.

- [Design coverage](COVERAGE.md)
- [Artwork behavior and limitations](ARTWORK-IMPLEMENTATION.md)
- [Build and handset verification](VERIFICATION.md)
- [Dependency provenance](DEPENDENCIES.json)

From the repository root, run `python3 android-premiere/scripts/fetch_dependencies.py` and then `bash android-premiere/scripts/build.sh`. Requirements: Python 3, Java 17 (`JAVA_HOME`, a JDK in `tools/jdk`, or Java on `PATH`), Apktool 3.0.3 (`APKTOOL_JAR` or `tools/apktool.jar`), and Android API 30's `android.jar` at `tools/android.jar`. The dependency fetch downloads checksum-verified VLC APKs and Android build-tools, but does not install Apktool or the Android platform jar. Preparation reuses a root decoded smali tree when available; otherwise it verifies and decodes the fetched ARM64 APK into `baseline/original-arm64-decoded/`. See the root [README](../README.md) for the complete build and signing details. Downloaded tools, decoded baseline, work files, signing material, and handset evidence are kept in ignored directories.
