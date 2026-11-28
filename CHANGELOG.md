# Changelog

## 2.0.0 (2026-11-28)

- Settings adds custom bindings for hardware keys, reader tap zones and swipes. Existing page controls keep their defaults on upgrade.
- The quick panel checks device capabilities and connected audio outputs when opened.
- Wide Favourite apps widgets reserve room for app names. Older Home layouts retain their border choices, and widget previews have padding inside the frame. The Wi-Fi quick tile shows the connected signal level.

- Drag Home widgets to move them or drag any selected corner to resize. Tap a widget for its compact header, then open the info button for width and height, styles and other options. The grouped widget picker follows the pages preference and includes a square reading-goal ring. Settings › Home grid controls the grid size and default widget borders.
- Home gains a one-screen widget editor with live previews, discrete movement and resizing, component-specific variants and a resettable reading preset. Existing section settings and Android widget bindings migrate into the layout.
- The Library adds measured cover grids and lists. Reading stats combine daily minutes, a week chart and a monthly calendar.
- The Folio interface adapts to monochrome e-ink, colour e-ink and LCD/OLED. Display type, text size and interface size can be adjusted separately. LCD and OLED users can follow the system theme.
- EPUB and TXT reading gains Literata, redesigned contents and text controls, and position-preserving pagination when layout or typography changes.
- Refresh edge starts tucked halfway down the screen edge. Tap to clean, hold for its menu, or drag the tab and release to tuck it against the nearest side. Saved positions carry over for each rotation, as do per-app Hide choices. Ink Shell supports per-app visibility.
- Clean screen and Refresh edge request a strong full refresh (GC 98) on Boox, matching the periodic page clean. Manual cleans can fall back to a black-and-white flash.
- Sleep artwork uses the panel dimensions. Select the generated image in the device’s screensaver settings. Boox users need to repeat this when the image changes.
- The debug package installs as **Ink Home dev** alongside the signed release, with separate data and permissions.
