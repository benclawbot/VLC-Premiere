# Android implementation

This directory contains the native Premiere phone interface and the scripts used to rebuild it over the VLC 3.7.1 Android engine. The presentation includes video and audio libraries, media artwork, search, playback, navigation, and settings while retaining VLC's media services and decoder libraries.

- [Design coverage](COVERAGE.md)
- [Artwork behavior and limitations](ARTWORK-IMPLEMENTATION.md)
- [Build and handset verification](VERIFICATION.md)
- [Dependency provenance](DEPENDENCIES.json)

From the repository root, run `python3 android-premiere/scripts/fetch_dependencies.py` and then `bash android-premiere/scripts/build.sh`. Requirements: Python 3, Java 17 (`JAVA_HOME`, a JDK in `tools/jdk`, or Java on `PATH`), Apktool 3.0.3 (`APKTOOL_JAR` or `tools/apktool.jar`), and Android API 30's `android.jar` at `tools/android.jar`. The dependency fetch downloads checksum-verified VLC APKs and Android build-tools, but does not install Apktool or the Android platform jar. Preparation reuses a root decoded smali tree when available; otherwise it verifies and decodes the fetched ARM64 APK into `baseline/original-arm64-decoded/`. See the root [README](../README.md) for the complete build and signing details. Downloaded tools, decoded baseline, work files, signing material, and handset evidence are kept in ignored directories.

## In-place upgrades, TMDB credentials and poster retention

Android updates require **the exact same package name and compatible signing identity**
as the APK already installed, plus a non-decreasing versionCode. Première keeps
`org.videolan.vlc`; the next versionCode is incremented to `13070110`.

The release workflow no longer publishes a fresh CI-generated development signer.
For a marked `[publish-premiere]` push, configure these repository **Actions secrets**:
`PREMIERE_RELEASE_KEYSTORE_B64` (base64 of the *existing* release keystore),
`PREMIERE_RELEASE_PASSWORD`, `PREMIERE_RELEASE_ALIAS`,
`PREMIERE_RELEASE_CERT_SHA256` (pinned SHA-256 of the signing certificate),
and `PREMIERE_RELEASE_GITHUB_TOKEN` (the classic PAT needed by the existing
publication issue gate). The workflow aborts if any is absent, if the certificate
does not match the most recent published APK, if the package changes, or if the
new versionCode is not greater. It also requires a completed Android 16 emulator
run before release. Never use a brand-new keystore for an upgrade release.

**Legacy limitation:** Prior GitHub Actions releases signed with ephemeral
development keys may be impossible to update in-place if the original private
key has been lost. Android will not allow changing the signer while preserving
app-private data. A signing check can prevent future incompatible releases, but
cannot retroactively make those old APKs upgradeable. Do not tell users to uninstall
to fix an update error; that deletes local TMDB credentials and artwork. A
separate, user-controlled migration/export workflow would be required for a
different-signer reinstall.

For matching-signer updates the saved TMDB read token remains in
`noBackupFilesDir/premiere_tmdb_token`, settings remain in private preferences,
and already matched video posters/music covers are migrated from the old
evictable cache to `noBackupFilesDir/premiere-art`. Both online lookup providers
and offline cached images continue to work. Uninstalling or clearing data
removes this information.

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
all hardware scenarios pass. Marked master commits can publish only after pinned release signing, the
publication issue gate, prior-release certificate/version compatibility checks,
and CI Android 16 acceptance. Signature/checksum validation alone does not
satisfy physical-device acceptance.

## Two-stage production APK signing

Build and sign **release candidates** with `PREMIERE_RELEASE_STAGE=candidate`
(the default). Install those exact production-certificate-signed candidate APKs
and complete issue #5's device, accessibility and certificate acceptance before
closing the issue.

After all acceptance issues are closed, use
`PREMIERE_RELEASE_STAGE=publication` when signing the final APKs. This invokes
the fail-closed owner-wide issue gate before writing signed APKs. Use
`PREMIERE_RELEASE_GITHUB_TOKEN` set to a **classic personal access token**
owned by `benclawbot` with full `repo` scope, including private repositories.
Fine-grained and GitHub App tokens are intentionally rejected because their
repository selection cannot prove full owner coverage.

Only publication-stage APKs may be distributed. For a marked master commit,
the release workflow uses publication-stage signing and verifies the latest
release's APK signing identity before publishing. Manual uploads are not
protected by CI, so never publish a candidate-stage APK.
Reverify the final publication-stage artifacts and their certificate/checksums
before manual distribution. Do not expose the token in logs or CI.
