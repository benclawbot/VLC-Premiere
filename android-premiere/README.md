# Android implementation

This directory contains the native Premiere phone interface and the scripts used to rebuild it over the VLC 3.7.1 Android engine. The presentation includes video and audio libraries, media artwork, search, playback, navigation, and settings while retaining VLC's media services and decoder libraries.

- [Design coverage](COVERAGE.md)
- [Artwork behavior and limitations](ARTWORK-IMPLEMENTATION.md)
- [Build and handset verification](VERIFICATION.md)
- [Dependency provenance](DEPENDENCIES.json)

From the repository root, run `python3 android-premiere/scripts/fetch_dependencies.py` and then `bash android-premiere/scripts/build.sh`. Requirements: Python 3, Java 17 (`JAVA_HOME`, a JDK in `tools/jdk`, or Java on `PATH`), Apktool 3.0.3 (`APKTOOL_JAR` or `tools/apktool.jar`), and Android API 30's `android.jar` at `tools/android.jar`. The dependency fetch downloads checksum-verified VLC APKs and Android build-tools, but does not install Apktool or the Android platform jar. Preparation reuses a root decoded smali tree when available; otherwise it verifies and decodes the fetched ARM64 APK into `baseline/original-arm64-decoded/`. See the root [README](../README.md) for the complete build and signing details. Downloaded tools, decoded baseline, work files, signing material, and handset evidence are kept in ignored directories.

## Production signing and release acceptance

The default local and pull-request builds use a **development** certificate. Never
publish their CI test packages as production releases: the CI keystore is not a
stable upgrade identity across clean runners. `build/artifacts.json` records
`signing_mode` and each APK's certificate SHA-256.

For an authorized release-candidate build, provide four environment variables:
`PREMIERE_RELEASE_KEYSTORE` (path to an existing keystore),
`PREMIERE_RELEASE_PASSWORD_FILE` (path to an existing password file),
`PREMIERE_RELEASE_ALIAS`, and `PREMIERE_RELEASE_CERT_SHA256` (pinned certificate
SHA-256). The build fails if any is missing or the signed certificate differs.
Keep all signing materials outside version control; never expose them in PR jobs.

After the release-signed APKs have been built, install **those exact files** on
the target Android handset and emulator. Retain device logs and acceptance
evidence for the scenarios in [issue #5](https://github.com/benclawbot/VLC-Premiere/issues/5).
Publish the matching signed ARM64/Universal APKs and `SHA256SUMS` only after
all hardware scenarios pass. This repository does not automatically publish
release packages from CI, and signature/checksum validation alone does not
satisfy device acceptance.
