# Changelog

## 2.0.0 (unreleased)

- Settings adds custom bindings for hardware keys, reader tap zones and swipes. Existing page controls keep their defaults on upgrade.
- The quick panel checks device capabilities and connected audio outputs when opened.

- Home adds a customisable bento grid with reading, calendar, clock, moon and device tiles. Long-press to arrange tiles across pages. Home also hosts Android widgets with a redraw limit and supports portable JSON layouts. Existing installations keep their selected Home style and three-part dock.
- The Library adds measured cover grids and lists. Reading stats combine daily minutes, a week chart and a monthly calendar.
- The Folio interface adapts to monochrome e-ink, colour e-ink and LCD/OLED. Display type, text size and interface size can be adjusted separately. LCD and OLED users can follow the system theme.
- EPUB and TXT reading gains Literata, redesigned contents and text controls, and position-preserving pagination when layout or typography changes.
- The optional Ink dot provides refresh controls over other apps. The opt-in Ink Shell accessibility service adds panel shortcuts and per-app dot visibility.
- Clean screen and Ink dot taps request a strong full refresh (GC 98) on Boox, matching the periodic page clean. Manual cleans fall back to a black-and-white flash when the native request is unavailable on e-ink.
- Drag the Ink dot anywhere within the screen. Its position survives restarts and reboots. Tapping still cleans the screen and holding opens its menu.
- Sleep artwork uses the panel dimensions. Select the generated image in the device’s screensaver settings. Boox users need to repeat this when the image changes.
- The debug package installs as **Ink Home dev** alongside the signed release, with separate data and permissions.
