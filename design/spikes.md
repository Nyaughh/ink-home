# C69 verification

The baseline debug APK from bf40f530950d6120fee2ae928796507dea47f866 is 300,167 bytes. The connected C69 reports API 30, 758 × 1024 pixels and 212 dpi. The release package is dev.inkhome.

The integrated Phase 0 debug APK is 1,262,843 bytes (+962,676 bytes including the lab, tokens and fonts). Its merged manifest identifies dev.inkhome.dev, its independent reader task and its own file authority.

| Spike | Evidence and outstanding test |
| --- | --- |
| S1 · Waveforms | Physical observation pending. Compare line grids, inverse fills, cover and grey ramp in Ink lab. A framebuffer capture cannot measure ghosting. |
| S2 · Reflection | The planning probe found the Onyx methods in the firmware. Runtime discovery and invocation from the target-35 debug package still need testing. |
| S3 · Accessibility overlay | Stacking and full-panel cleaning from an accessibility window require a service test in another app. |
| S4 · Standby | The current secure screensaver component is Google's DeskClock. Whether Boox uses Android dreams at standby is unverified. |
| S5 · Owner | Onyx status-bar suppression, app hiding and silent updates remain unverified. |
| S6 · Page keys | Delivery to an accessibility service while another app has focus remains unverified. |
| S7 · Ink dot | The enabled accessibility service list contains com.onyx.floatingbutton/.service.FloatButtonAccessibilityService. This identifies the Boox service, but cannot confirm whether its ball is visible. Cross-app cleaning, persistence and mode changes require overlay testing. |

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
refreshScreen(98) method. The physical extent of the clean requires observation.
Disabling the dot removed its window and foreground service. The package and
UID overlay modes were both verified back at default after testing.
