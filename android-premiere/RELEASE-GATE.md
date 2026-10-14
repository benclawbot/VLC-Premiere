# Production APK release gate

The APK built in ordinary CI is development-signed and is **not a public release**.

Production signing in `android-premiere/scripts/sign.py` now calls
`release_gate.require_no_open_issues`. The gate fails closed unless an
authorized `PREMIERE_RELEASE_GITHUB_TOKEN` can query GitHub and receive a
**complete response with zero open issues** across repositories owned by
`benclawbot`. Any open issue, unavailable GitHub API, missing token or
incomplete result blocks production signing.

The production token must have permission to see all owned repositories and
their issues (including private repos); a narrow default GitHub Actions
repository token is **not** enough to prove private repositories have no open
issues. Never commit or print a release token.

This only gates the repository's production-signing path. There is no
automatic public release workflow here, and this check is **not a substitute**
for physical handset testing, manual TalkBack/RTL acceptance, or verifying
the final APK and production certificate before any publication. Re-check
open issues immediately before any external/manual APK publication as well.
