# Active implementation target

The user selected **Premiere** from the Cinema proposals and requested its implementation in the native VLC Android app, followed by a rebuilt APK. The canonical reference is `design-proposals/premiere/`, including the 109 screen/state inventory in `coverage.json`. No alternative direction is pending.

The first rebuilt APK was rejected because it recolored VLC's original screen compositions. Its build/playback checks do not prove completion of the requested design. The correction must change the actual native screen hierarchy and composition.

## Visual acceptance

- Video: Premiere masthead, large “Your library” heading and real media count, search field, continue-watching artwork hero with real progress, recently-added artwork shelf, restrained five-destination navigation.
- Audio: the four music tabs, artwork grids and readable track rows, artist/album/genre details, large album artwork and centered title/transport in Now playing, separate queue.
- Browse: grouped device, network and quick-access destinations; consistent folder rows and server/stream forms.
- Playlists: layered artwork cards, actual item counts, create/detail/edit actions.
- More/settings: grouped destinations and preference panels, consistent heading, spacing and real persisted values.
- Playback: portrait/landscape picture-first layout, restrained transport, controls and tool sheets matching the proposal.
- First run/remote/dialogs: the corresponding compositions and functional native actions; runtime-generated content replaces illustrative proposal values.

Existing binding IDs must remain correct where used. Keeping the original view hierarchy is not a design requirement. Invented library data, bundled proposal media, static galleries, and WebView simulations cannot satisfy this target.

## Verification required

Compile and sign both APK variants, install the Universal variant on the owned emulator, inspect native screenshots against the selected reference, exercise real video/audio playback, browse real files, create and revisit a playlist, test search and persisted settings, inspect crash logs. Track remaining coverage honestly by the original screen IDs. Do not report full completion while original compositions remain in the requested flows.

Validation media lives only in the owned emulator and ignored build fixtures; it is not packaged into the app.
