# Cinema, four ways

A static comparison page with four refinements of the existing Cinema direction:

- **Premiere** keeps the warm charcoal and VLC orange, with a generous resume feature, clear sans typography and a poster shelf.
- **Noir** lets a full-height artwork frame dominate, using near-black surfaces and a small floating up-next card.
- **Velvet** uses espresso and burgundy surfaces, amber accents, serif display text and an editorial portrait feature.
- **Panorama** gives landscape thumbnails and a two-column video shelf priority, with slate charcoal and warm gold.

Each variation includes a portrait library preview and a full landscape playback preview. The gallery reuses the six sample photographs and the Video, Audio, Browse, Playlists and More navigation labels from the parent proposal. Its local interactions are visual prototypes: search filters the displayed sample titles, tapping a title opens simulated playback, and playback controls update their visible state. Native VLC playback and system actions are outside this proposal.

## Open locally

From the parent `design-proposals` directory, serve the files with any static HTTP server, then open `/cinema/`. The page needs no packages, build step, external fonts or network resources. Artwork and the base proposal stylesheet are read from the parent directory. PNG export fetches both stylesheets and the local images, so use HTTP rather than opening the file directly.

Use the Library / Playback controls to compare orientations. Use a card's focus control to enlarge one variation and **All four** to restore the comparison. **Save image** prepares a self-contained PNG for the current view, named `cinema-library-four-variations.png` or `cinema-playback-four-variations.png` when downloaded.

## Palette rationale and trade-offs

| Variation | Palette | Rationale and trade-off |
|---|---|---|
| Premiere | Charcoal `#171615`, VLC orange `#F46B32`, warm ivory `#E7DDCC` | Familiar balance and a clear action color; the feature consumes library space. |
| Noir | Black `#090909`, VLC orange `#F46B32`, muted sand `#D0C4B4` | Maximum artwork immersion and restrained chrome; less information is visible at once. |
| Velvet | Espresso `#211714`, amber `#D79551`, parchment `#E8D8C8` | A small cinema-program mood; serif titles are slower to scan. |
| Panorama | Slate `#202528`, warm gold `#D7AA69`, pale stone `#E8E0D3` | More visual browsing for travel and documentary media; small titles have less emphasis. |

All four use system fonts and local assets. The cone mark is an inline SVG. The separate `../index.html` gallery and its original four directions remain available.
