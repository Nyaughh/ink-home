# Changelog

## 2.0.0 (unreleased)

- Settings adds custom bindings for hardware keys, reader tap zones and swipes. Existing page controls keep their defaults on upgrade.
- The quick panel checks device capabilities and connected audio outputs when opened.

- Settings › Home lets you arrange sections on one Home screen, change borders and density, and choose the layout and count for favourite apps. Existing book layouts and data are preserved. Saved dev grids migrate to ordered sections, including hosted Android widgets.
- The Library adds measured cover grids and lists. Reading stats combine daily minutes, a week chart and a monthly calendar.
- The Folio interface adapts to monochrome e-ink, colour e-ink and LCD/OLED. Display type, text size and interface size can be adjusted separately. LCD and OLED users can follow the system theme.
- EPUB and TXT reading gains Literata, redesigned contents and text controls, and position-preserving pagination when layout or typography changes.
- Refresh edge starts as a slim tab halfway down the screen edge. Tap to clean, hold for its menu, or drag the tab and release to dock it to the nearest side. The new position survives restarts and reboots. Ink Shell supports per-app visibility.
- Clean screen and Refresh edge request a strong full refresh (GC 98) on Boox, matching the periodic page clean. Manual cleans can fall back to a black-and-white flash.
- Sleep artwork uses the panel dimensions. Select the generated image in the device’s screensaver settings. Boox users need to repeat this when the image changes.
- The debug package installs as **Ink Home dev** alongside the signed release, with separate data and permissions.
