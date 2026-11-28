# C69 verification

The baseline debug APK from bf40f530950d6120fee2ae928796507dea47f866 is 300,167 bytes. The connected C69 reports API 30, 758 × 1024 pixels and 212 dpi. The release package is dev.inkhome.

The integrated Phase 0 debug APK is 1,262,843 bytes (+962,676 bytes including the lab, tokens and fonts). Its merged manifest identifies dev.inkhome.dev, its independent reader task and its own file authority.

| Spike | Evidence and outstanding test |
| --- | --- |
| S1 · Waveforms | Aarin confirmed a visible blackout and successful clean using GC 98. The comparative physical results for GU 2, Regal 6, inverse-fill ghosting and recommended cadence are pending. |
| S2 · Reflection | The target-35 debug package invoked refreshScreen(98) from app and accessibility overlays on the C69. Other signatures and update modes require individual verification. |
| S3 · Accessibility overlay | The top-center strip attached at y=0 over NeoReader. Taps reached the debug service in Chromium and Android Settings. Four clean calls returned. Whether a full-width overlay can fully conceal the Boox status bar is unverified. |
| S4 · Standby | The configured secure component was DeskClock. This firmware provides no dreams shell command implementation. Its ordinary standby picture remains the manual sleep-image workflow. |
| S5 · Owner | No physical owner provision or policy change. Reversible removal was not demonstrated on the C69. Status-bar suppression, hiding apps and silent updates remain unverified. |
| S6 · Page keys | No key events recorded during the service test. Physical presses and navigation on a loaded page were not confirmed. Production does not filter page keys in other apps. |
| S7 · Floating refresh control (Phase 5 prototype) | Verified native full clean, move, tuck, return to foreground and complete service shutdown as detailed below. Cross-app refresh mode changes were not demonstrated. The Boox service was detected, but detection cannot establish whether its own ball is visible. |

The approved side-by-side install succeeded. ActivityManager reported dev.inkhome.dev/dev.inkhome.MainActivity as resumed on the C69. No grants or settings changes were made.

The Phase 4 Home capture showed a real EPUB cover and the Korean author name.
Before the Phase 5 overlay test, both the package and UID queries for
SYSTEM_ALERT_WINDOW returned “No operations” and “Default mode: default”.
The browser and reader packages on this C69 are org.chromium.chrome and
com.onyx.kreader.

S7 on the Phase 5 build: the application overlay stayed visible in Chromium and
NeoReader. Android Settings reported mForceHideNonSystemOverlayWindow=true
and hid the dot; the dot returned in Chromium. Moving across edges, tucking,
and pulling back out worked. One tap at 16:21:44.847 returned from the Onyx
refreshScreen(98) method. Aarin observed that the screen cleaned successfully
and moving or tucking left no trail. The blackout during the clean was distracting.
Disabling the dot removed its window and foreground service. The package and
UID overlay modes were both verified back at default after testing.
