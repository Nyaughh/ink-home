# Folio and the Ink shell

Sections 3 through 5 below reproduce the approved design plan (revision 2, 27 November 2026). The referenced mockups accompany the original plan.

## Build assets

Edit `design/tokens.json` and run `python tools/tokens.py` to regenerate the plain Java constants in `dev.inkhome.folio.core.Tokens`. Run `python tools/tokens.py --check` to verify the checked-in copy. Dimensions are in interface units unless their name or definition specifies pixels, milliseconds, a percentage, or a multiplier. Palette constants are opaque ARGB integers.

The three variable TTF assets include Latin, combining marks, punctuation and common book symbols. Android supplies fallback glyphs for other scripts. The Source Sans 3 subset uses the family name Folio Sans to respect Adobe's reserved font name. Literata includes upright and italic faces with weight and optical-size axes. Their licenses are bundled in `app/src/main/assets/fonts/OFL.txt`.

To regenerate the assets, install `fonttools==4.64.0` and run `python tools/subset_fonts.py`. The script downloads the original fonts and licenses from revision `5026800714d07231937d92eb4cceabf06e05d74e` of the Google Fonts repository. [Google Fonts sources](https://github.com/google/fonts/tree/5026800714d07231937d92eb4cceabf06e05d74e/ofl)

Two debug builds of `bf40f53` measured 300,167 bytes before adding the font assets and 1,084,019 bytes afterward. The three fonts and license add 783,852 bytes to that APK.

## 3. Design principles

1. **Ink and paper.** On e-ink the interface uses two tones. Images are the only greyscale. Nothing depends on a grey to be understood.
2. **Type carries the hierarchy.** Size and weight do the work that colour and shade do on a phone.
3. **Rules, not boxes.** Structure comes from alignment and lines. A box is rare and means "act on this".
4. **Every refresh is a cost.** Change the fewest pixels, choose the waveform for the content, and spend a full refresh deliberately.
5. **The result is the feedback.** A tap changes something visible where you tapped, or changes the screen. No intermediate pressed flash on e-ink.
6. **Quantity is length, state is fill.** Progress, minutes and levels are bars and counts. On and off are filled and outlined.
7. **Whole things only.** Pages end on item boundaries. Nothing is ever cut in half.
8. **One system, three displays.** The same components on mono e-ink, colour e-ink and LCD/OLED. Display class changes token values, never structure.
9. **Only what the hardware has.** Controls appear when the device can do the thing (already the app's rule; now applied everywhere).
10. **Calm.** Few things per screen, no badges demanding attention, no animation unless the user asks for it on a display that can show it.

---

## 4. The Folio design system

### 4.1 Structure

```
tokens (numbers)  ->  roles (meaning)  ->  components  ->  patterns  ->  screens
   4u, 1 px, #000     paper, ink, rule     ListRow, Sheet   paging, overlays   Home, Library
```

- **Tokens** are platform-neutral numbers kept in one file, `design/tokens.json`. The mockup stylesheet (`ui-redesign-mockups/mockups.css`) is a second, CSS implementation of the same tokens, which keeps the system honest about not depending on Android.
- **Roles** give tokens meaning (`ink`, `rule`, `title`). Components only ever use roles.
- **Display class** and **window class** select role values. Components don't check the device.
- The pure parts (scales, colour roles, grid maths, pagination, refresh budget) are plain Java with no `android.*` imports, so they are unit-testable and would carry to another platform. The Android layer only draws.

### 4.2 Display classes and capabilities

| Class | Examples | What changes |
|---|---|---|
| `MONO_EINK` | C69, Kindle-class 6 to 7 inch readers, reMarkable-class 10 inch note tablets | Two-tone palette, no pressed state, paging, refresh control, heavier text weights |
| `COLOR_EINK` | Kaleido-class and Gallery-class readers | As mono, plus colour covers and one accent colour in solid areas only |
| `EMISSIVE` | Phones and tablets (LCD, OLED) | Tonal palette, Paper and Night themes, scrolling by default, pressed state layer, optional subtle motion |

Kindle and reMarkable devices themselves do not run Android apps. "Kindle-class" and "reMarkable-class" here mean Android readers with the same kind of panel and size.

**Detection**, in order: the user's choice in Settings › Display & refresh › Display type; Onyx firmware present (`View.refreshScreen(int)` exists) means e-ink; a table of e-ink makers and models matched against `Build.MANUFACTURER` and `Build.MODEL` (Onyx, Bigme, Boyue/Meebook, Hisense ink phones, Supernote, PocketBook, Tolino, Nook, Xiaomi/Moaan and others; the list is from my own knowledge and needs checking per device); otherwise `EMISSIVE`. Colour is taken from the model table. Android has no official "is this e-ink" API, so the first-run setup shows the detected class and lets the user correct it in one tap.

**Capabilities** are probed one by one, never implied by class: `epdControl` (Onyx methods present), `regal`, `pageKeys`, `frontlight`, `warmLight`, `bluetooth`, `colour`, `speaker`, `rotation`, `hardwareKeyboard`.

### 4.3 Units, scaling and window classes

- **The unit is `u` = 1 dp × interface size.** Every dimension in this document is in `u`. Text sizes are also multiplied by the text size setting and Android's font scale.
- **Interface size** (existing setting): Compact 100%, Default 115%, Large 130%, Extra large 145%. Default is 115% on e-ink and 100% on LCD/OLED. **Text size** (existing): 90, 100, 115, 130%.
- On the C69 at the default, the screen is **497 × 672 u** and 1 u is 1.52 px.
- **Pixel snapping.** Every edge and stroke is rounded to whole device pixels. Strokes are defined in pixels, not `u`.

| Stroke | Definition | C69 | Use |
|---|---|---|---|
| `hairline` | max(1, floor(0.75 × density)) px | 1 px | Row separators, control outlines, cover edges |
| `rule` | 2 × hairline | 2 px | Section heads, bar and sheet edges, focus |
| `bar` | 4 u | 6 px | Progress fill, active tab and nav markers |

**Window classes** (by available width in `u`):

| Class | Width | Margin | Gutter | Layout | Typical |
|---|---|---|---|---|---|
| Compact | under 600 | 20 | 12 | One column | C69 portrait (497), phones (360 to 430) |
| Medium | 600 to 839 | 28 | 16 | One column, wider grids | C69 landscape (672), 7.8 inch readers |
| Expanded | 840 and up | 32 | 20 | Two columns or two panes | 10 inch note tablets, tablets |

The dock is the navigation on every window class. On expanded widths its menu opens as a 480 u panel above the chevron.

Content never assumes a column or row count. Grids compute `columns = floor((width − 2 × margin + gutter) / (minCell + gutter))`, and paged lists compute rows from the height left over. Minimum cells: book cover 96 u, app tile 88 u.

### 4.4 Colour roles

| Role | Mono e-ink | Colour e-ink | LCD/OLED Paper | LCD/OLED Night |
|---|---|---|---|---|
| `paper` | `#FFFFFF` | `#FFFFFF` | `#FAF9F5` | `#0E0E0D` |
| `ink` | `#000000` | `#000000` | `#1B1A18` | `#ECE9E2` |
| `ink-2` (secondary text) | `#000000` | `#000000` | `#55524C` | `#A8A49B` |
| `ink-3` (hints, disabled) | `#000000` | `#000000` | `#8A867E` | `#77736B` |
| `rule` (hairlines) | `#000000` | `#000000` | `#D9D5CC` | `#33312D` |
| `strong` (rules, outlines) | `#000000` | `#000000` | `#1B1A18` | `#ECE9E2` |
| `fill` (quiet container) | `#FFFFFF` | `#FFFFFF` | `#F0EEE8` | `#1B1A18` |
| `inverse` / `on-inverse` | `#000` / `#FFF` | `#000` / `#FFF` | `#1B1A18` / `#FAF9F5` | `#ECE9E2` / `#0E0E0D` |
| `accent` / `on-accent` | `#000` / `#FFF` | `#D2281A` / `#FFF` | `#C2391B` / `#FFF` | `#F0704F` / `#0E0E0D` |

Rules that go with the table:

- **On e-ink, secondary text is the same ink as primary text.** It is told apart by size, weight, italic or position. This is why the system is safe on a panel that crushes greys.
- **Accent** is the second ink, vermilion by default (the red of rubricated print). It is used for progress fills, chart bars that met the goal, the calendar's goal-met days and nothing else. On mono it resolves to black, so no meaning is ever carried by colour alone.
- **Colour e-ink:** text, icons and lines stay pure black, because coloured fine detail is drawn at half the resolution on Kaleido-class panels (E Ink lists 300 ppi black and white, 150 ppi colour). Accent only in solid areas at least 3 u thick. Covers in colour with a saturation and contrast lift. Accent alternatives: Cobalt `#1F4FBF`, Forest `#1F7A3A`, Ochre `#C98A00`.
- **Night theme** exists on e-ink too (white on black) as an explicit choice, with a note that it ghosts more and a tighter refresh budget.
- **Wallpaper** stays an optional Home backdrop, faded toward `paper` as today, in colour where the display has it.

### 4.5 Typography

**Families.** Source Sans 3 for the whole interface, headings included: humanist, narrow enough for a 497 u screen, sturdy in bold, with tabular figures. Literata (serif) only for book text in the reader and for the titles on generated covers. Both are variable fonts under the SIL Open Font License. Missing glyphs (Korean, Chinese, Cyrillic if not bundled) fall back to the system font automatically.

| Role | Size / line (u) | Weight on e-ink | Weight on LCD | Use |
|---|---|---|---|---|
| `display` | 38 / 40 | 700 | 700 | The clock (control panel, Minimal home) |
| `figure` | 30 / 34 | 700 | 700 | The one hero number on a data screen ("48 min today") |
| `title-lg` | 25 / 30 | 700 | 700 | Screen titles |
| `title` | 23 / 27 (28 / 33 on expanded) | 700 | 700 | Current book title |
| `title-md` | 20 / 24 | 700 | 700 | Sub-screen titles, month name |
| `title-sm` | 17 / 22 | 700 | 700 | Sheet titles, ledger values |
| `byline` | 15.5 / 21 | 500 | 400 | Authors |
| `body-lg` | 16 / 21 | 600 (500 for app names) | 500 | Primary line of a row |
| `body` | 15 / 20 | 500 | 400 | Sentences, descriptions, dock text |
| `meta` | 13 / 18 | 500 | 400 | Secondary line of a row, captions |
| `label` | 13.5 / 18 | 700 | 600 | Section heads |
| `reading` | user, default 19 sp, line 1.35 | 400 | 400 | Book text, in Literata or the user's choice |

All interface roles are Source Sans 3.

- **No capitals.** Nothing in the interface is set in all caps, and nothing is letter-spaced. Labels are sentence case and get their emphasis from weight.
- **Headings are bold sans**, tracked in by 0.01 em at 23 u and above.
- **Ink weight compensation:** e-ink renders strokes thinner, so interface text never goes below weight 500 there. Light weights are not used anywhere.
- **Minimum size on e-ink:** 11 u, and only for numbers inside charts and calendar cells. Running text is 13 u or more.
- **Numerals** are lining and tabular wherever they change or line up (clock, percentages, minutes, page counters).
- **Titles** wrap to a set number of lines and then ellipsize. Rows are one line each for primary and secondary text.
- **Copy:** sentence case. Labels are short nouns.

Foundations: type roles, spacing, strokes, colour roles and components (approved mockup file `ui-redesign-mockups/shots/foundations.png`).

### 4.6 Spacing, size and shape

- **Spacing scale (u):** 2, 4, 8, 12, 16, 20, 24, 32, 48. Nothing else.
- **Row heights (u):** 34 data row (Stats), 48 compact (sheet actions), 52 standard (apps, Home's Up next), 60 two-line with a description (settings), 72 book row.
- **Bars (u):** status strip 30, app bar 56, tabs 38, section head 36 (30 on data screens), pager folio 40, dock 52.
- **Touch targets:** at least 48 × 48 u of hit area for anything tappable, even when the drawn icon is 24 u.
- **Icon sizes (u):** 24 standard, 18 inline, 14 chevrons and chip marks.
- **Shape: rectangles and circles only.** Corner radius is 0 for every container and control. Circles are used for radios. Refresh edge has a rectangular outline. No rounded rectangles, no shadows, no gradients. This is the system's signature, and it draws pixel-exact on ink.
- **Elevation** is shown by a `rule` edge, never by shadow. On LCD/OLED overlays also get a 32% scrim. On e-ink there is no scrim, because a full-screen grey is the worst thing to ask of the panel.
- **Covers:** 7:10, 1 px ink edge, no radius, never stretched (centre-cropped). Generated covers (no image in the file) are typographic: format label, serif title, short rule, italic author.

### 4.7 Iconography

- One set, drawn in code on a 24 grid as today (`Icons.java`), redrawn with a 2 u stroke, square caps and mitred joins so lines land on whole pixels. A filled variant marks the current destination in the menu.
- About 45 glyphs: navigation (home, library, apps, stats, settings, search, back, forward, up, down, close, more), actions (plus, minus, check, filter, grid, list, star, refresh, contents, bookmark), status (wifi 0 to 4 and off, battery with level and charging, bell, bluetooth, quiet), system (tune, rotate, moon, power, switcher, volume, light), objects (book, folder, image, clock).
- Text is preferred over an icon whenever the meaning isn't obvious. The reader's text button is the letters "Aa".
- **App icons are the apps' own icons**, as today: drawn at 40 u in Favorites and 30 u in lists, in grey with a small contrast lift on mono e-ink and in their original colours where the display has colour. No plates, rings or masks. The App icons setting stays an on/off switch; off shows names only.

### 4.8 States and feedback

| State | E-ink | LCD/OLED |
|---|---|---|
| Pressed | Nothing is drawn. The action's result is the feedback. | 10% ink overlay, instant |
| Slow action (over 300 ms) | The tapped label changes at once ("Opening…"), no spinner | Same |
| Selected | Inverse fill (segmented, tiles), 3 u underline (tabs), a ruled frame (the current destination in the menu, choice tiles) | Same shapes |
| On / off | Filled / outlined | Same |
| Disabled | Removed where possible; otherwise outlined with a caption that says why. Never dimmed. | `ink-3` |
| Focus (keys, D-pad) | `rule` outline, 2 u outside the element | Same |
| Loading | Words in place ("Looking for books…"). No spinners, no skeleton shimmer. | Same |
| Error | A sentence where the content would be, with one action | Same |

### 4.9 Refresh model

The app describes what kind of change it is making. A small `Epd` adapter turns that into whatever the device supports.

| Intent | When | Onyx (verified constants, Appendix A) | Other e-ink | LCD/OLED |
|---|---|---|---|---|
| `CLEAN` | Leaving the reader, returning from another app, waking, the Clean screen action, and when the ghost budget runs out | `refreshScreen(98)`, full GC with a flash | Black then white flash on manual clean only | Nothing |
| `PAGE` | A list or text page turns; tabs switch | Regal (`setDefaultUpdateMode(6)` with `enableRegal(true)`) when supported, otherwise default (5) | Firmware default | Nothing |
| `IMAGE` | A page of covers appears | Default partial update, charged double against the ghost budget | Firmware default | Nothing |
| `FAST` | Typing in search, steppers, level bars | DU (`setDefaultUpdateMode(1)`) on that view while it changes, then one `PAGE` update when it settles | Firmware default | Nothing |

- **Ghost budget.** Each change adds to a counter (text page 1, image page 2, overlay open or close 1, doubled in Night theme). At a threshold (default 8, user-adjustable as today's "every N pages") the next idle moment gets a `CLEAN`. This generalises the reader's existing "full refresh every 8 pages" to the whole shell.
- **Change less.** Screens update the part that changed (a row, the page area, the status strip) and stop rebuilding the whole tree on every state change. The status strip only redraws when a value changes, as now.
- **Never redraw under typing** (kept). Search filters after a 250 ms pause.
- What each mode looks like on this panel can't be judged over adb. Phase 0 adds an Ink lab screen so Aarin can look at each mode and confirm or adjust this table.

### 4.10 Motion

None on e-ink, ever (kept). On LCD/OLED the default is also none. A "Motion: Off / Subtle" setting on emissive displays allows a 120 ms cross-fade between screens and for overlays. No slide, scale or bounce.

### 4.11 Input

- **Touch:** tap, long-press (400 ms, opens the item's action sheet) and swipe. Swipes are discrete: one swipe left or up is the next page, right or down is the previous page. Nothing follows the finger on e-ink, so nothing ghosts.
- **Page keys** turn pages everywhere, acting on key-up as the Boox firmware requires (kept), with the Reversed setting (kept). Volume keys do the same where page keys don't exist.
- **Home key on Home** cleans the screen (kept). **Back** closes the top overlay, then goes up, then to Home.
- **Keyboard and D-pad:** every component is focusable in reading order, arrows move, Enter activates.
- **Text entry:** the field stays above the keyboard, and the list below it filters in `FAST` mode.

### 4.12 Components

Each component is specified once. Sizes are in `u`. The mockups show all of them.

| Component | Anatomy and rules |
|---|---|
| **StatusStrip** | 30 high. Time (600, tabular) left. Right: notification count, Wi-Fi, battery percent and glyph, then a tune glyph that makes the affordance explicit. Hairline below. The whole strip opens the control panel. |
| **Dock** | 52 high, `rule` on top, on every screen and window class. Left: settings glyph (a home glyph while in Settings). Middle: today's reading ("Today 48 / 30 min", or "Today 48 min" without a goal), which opens Stats. Right: a chevron that opens and closes the menu. Same structure and behaviour as today. |
| **Menu** | A sheet that sits on the dock: two rows of four entries, icon over label (Home, Library, Stats, Apps, Search, Clean screen, Wi-Fi, Control panel). The current destination has a ruled frame. Tapping outside, Back or the chevron closes it. |
| **AppBar** | 56 high. `title-lg` left, optional count in `meta`, up to 3 icon actions right. Sub-screens: back arrow and `title-md`. |
| **Tabs** | 38 high text tabs, 22 apart, hairline under the row, active tab bold with a 3 u ink underline. Replaces the rounded segmented control for navigation between views. |
| **SectionHead** | `rule` on top, then a 36 high row (30 on data screens): `label` in bold sentence case left, optional inline pager or text action right. This is the main structural device. |
| **ListRow** | Leading (app icon 30, or cover), text block (`body-lg` and optional `meta`), trailing (value, chevron 14, or a control). Hairline between rows, none after the last. Heights from 4.6. |
| **BookRow / BookCell** | Row: cover 40 wide, title, meta, 3 u progress under the text, percent right. Cell: cover at column width, 3 u progress, 2-line title. |
| **Cover** | See 4.6. Decoded off the main thread, cached at the size drawn. |
| **Progress** | Hairline track, `bar` fill in `accent`, square ends. Sizes 3, 5 and 6 u. Optional percent in tabular figures. |
| **Button** | 44 high. Primary: inverse fill. Secondary: `rule` outline. Text button: underlined 600 weight. One primary per view at most. |
| **IconButton** | 24 glyph in a 48 target. No outline. |
| **Chip** | 30 high, hairline outline, label and a close mark. Used only for active filters. |
| **Switch** | 44 × 24 rectangle. Off: outlined with a solid square at the left. On: solid with a paper square at the right. |
| **Radio / Check** | 20 circle with a 10 dot / 20 square with a check. |
| **Segmented** | Joined rectangles in one hairline frame, 34 high, selected segment inverse. For 2 to 4 exclusive options set in place. |
| **Stepper** | Minus and plus buttons (38 × 34, hairline) around a tabular value with its unit. |
| **LevelBar** | A row of equal segments, filled up to the level. Tap a segment or the end buttons. The ink-friendly replacement for a slider. |
| **Pager** | Folio form: 40 high row above the dock, previous and next at the edges, "3 / 12" centred. Inline form: the same three parts at the right of a SectionHead. Shown only when there is more than one page. |
| **Sheet** | Paper panel with a `rule` edge. Bottom sheet on compact, centred dialog (max 480) on medium and expanded, top sheet for the control panel. Header: `title-sm` and a close button. Closes by tapping outside, Back, or close. Variants: ActionSheet, ChoiceSheet (radio rows), PromptSheet (text field), ConfirmSheet. |
| **Notice** | 42 high inverse strip above the dock: one sentence and an optional action ("Undo"). Appears and disappears instantly; gone after 4 seconds or the next tap. Replaces every Toast. |
| **Callout** | The one box in the system: `rule` outline, `title-sm`, one sentence, one primary button. For permissions and setup. |
| **EmptyState** | `title-sm`, one sentence, one text button, left-aligned where the content would start. No illustrations. |
| **Tile** | Control panel toggle: 54 high, icon, name and state. On is inverse. |
| **Ledger** | A 3-column table of label (`meta`) over value (`title-sm`), divided by hairlines, under a `rule`. About 46 high per row. Replaces metric cards. |
| **BarChart** | 92 high. Solid bars for days that met the goal, outlined bars for days that didn't, minutes printed on each, dashed goal line. |
| **MonthGrid** | Seven even columns of 40 high cells, 4 apart. Each cell: the date over the minutes read. Filled = goal met, hairline outline = some reading, plain = none. A 3 u bar under today. |
| **DataRow** | 34 high: name, an 84 wide bar showing its share, and a right-aligned tabular value. For ranked data such as Most read. |
| **Refresh edge** | A slim rectangular tab with a refresh glyph and a touch target of at least 48 dp. Full specification in 5.6. |

### 4.13 Patterns

- **Paging** is the default on e-ink, scrolling on LCD/OLED; the Long lists setting chooses either (kept). Three containers share one algorithm: PagedList (uniform rows), PagedGrid (computed columns and rows) and PagedFlow (mixed content such as Settings and Stats, with the existing "keep a heading with its group" rule from `MainActivity.java:2341`). Pages are computed before the first frame is drawn, as today. The folio row is laid out in two passes so appearing or disappearing never changes the page count.
- **Density on data screens.** Stats and other data screens are sized so a realistic amount of data fits on one page of the C69: one `figure` at most, ledger values in `title-sm`, 34 u data rows, 30 u section heads. When content outgrows the page it pages cleanly; a larger number never pushes a section off the screen.
- **Overlays:** one at a time. A sheet never opens another sheet; it replaces its own content.
- **Filters** live in one sheet (Sort, Show, View). Any non-default filter shows as a chip row under the tabs so hidden content is never a surprise.
- **Long-press** always opens the item's ActionSheet; the same actions are reachable from the item's detail view, so nothing is long-press only.
- **Permissions and setup** use Callouts in place, plus a first-run checklist (6.10).
- **Destructive actions** (delete collection, clear all) take effect at once and offer Undo in a Notice. No "are you sure" dialogs.
- **Dates and times** follow the Clock setting and the locale.

### 4.14 Accessibility and languages

- Contrast is maximal on e-ink by construction; LCD roles meet 4.5:1 for text (`ink-2` on `paper` is about 7:1; `ink-3` is for non-essential hints only).
- All components expose a content description and role to TalkBack. Custom-drawn views implement accessibility nodes.
- Interface and text size settings stack with Android's font scale. Layouts are tested at the largest combination.
- Strings move to `res/values/strings.xml` so the app can be translated. Layouts mirror for right-to-left.
- No information by colour alone (4.4), none by position alone in charts (values are printed).

---

## 5. The Ink shell: Ink Home as the device's own system

### 5.1 The idea

The same strip at the top, the same panel behind it, the same way home and the same sleep screen, wherever you are on the device. Boox becomes the firmware underneath, visible only where Android gives no way around it.

```
 asleep          awake, at home                      in any other app
┌──────────┐    ┌─────────────────────────┐         ┌─────────────────────────┐
│          │    │ StatusStrip  → Control  │         │ StatusStrip  → Control  │  same strip,
│  Sleep   │ →  │              panel      │   →     │              panel      │  same panel
│  screen  │    │ Home and the other      │         │      the app            │
│          │    │ screens, over the dock  │         │ Refresh edge · edge gestures │  same refresh,
└──────────┘    └─────────────────────────┘         └─────────────────────────┘  same way home
                     Switcher: recent books and apps, from the panel or the tab
```

### 5.2 Three levels, each opt-in

| Level | What the user does | What Ink Home takes over |
|---|---|---|
| **Launcher** (today's footing) | Chooses Ink Home as the home app and allows files, reading time and notifications | Everything inside Ink Home: strip, control panel, navigation, sheets, notices. Its own Open-with sheet, its own Switcher, a Device settings hub that links to Android's own pages and never to Boox's, Boox's shell apps tucked away by default, and the sleep picture written at the panel's real size. With one extra permission (display over other apps), Refresh edge. |
| **Shell** | Turns on "Ink shell" in Settings, which opens one Android screen to enable Ink Home's accessibility service | The Ink status strip and control panel over every app, Refresh edge with its full menu, an optional Ink bar (Back, Home, Switcher, Clean screen), and Sleep now. |
| **Owner** | Runs one command once from a computer: `adb shell dpm set-device-owner dev.inkhome/.shell.Owner` (the device must have no accounts, which is true of the C69) | Boox's status bar and control centre are switched off, the Ink sleep screen is installed as the system screensaver, Wi-Fi and Bluetooth toggle directly from the panel, Restart, Boox apps hidden system-wide (hidden, not uninstalled), Ink Home's own permissions granted without prompts, and updates installed without the Android installer screen. A "Leave Owner level" button in Settings undoes it. |

Launcher-level items use public Android APIs and carry no unknowns. **Everything listed for Shell and Owner is designed but not yet proven on Onyx's modified system.** Phase 0 has a spike for each (S3 to S7), and each feature has a fallback, so the plan does not depend on any of them working. On the C69 each of those steps waits for Aarin's OK at the moment it runs.

### 5.3 Surface by surface

| # | Surface | Today on the C69 | Replacement | Level | What still shows |
|---|---|---|---|---|---|
| 1 | Home | Ink Home | Ink Home, redesigned | Launcher | Nothing |
| 2 | Status bar at home | Ink Home's own | StatusStrip | Launcher | Nothing |
| 3 | Status bar in other apps | Boox bar | Ink StatusStrip drawn over it; blanked underneath at Owner level | Shell, Owner | Boox bar at Launcher level |
| 4 | Control centre | Boox control centre | Ink ControlPanel: connectivity, clean screen, rotation, quiet, sleep, volume, notifications, shortcuts | Launcher inside Ink Home; Shell everywhere; Boox's disabled at Owner | Below Owner level, a swipe from the top edge still opens Boox's |
| 5 | Notifications | Boox list and pop-ups | The panel's list (exists today). "Quiet" stops pop-ups; notifications collect in the panel. | Launcher | Pop-ups when Quiet is off |
| 6 | Recent apps | Boox recents | Ink Switcher: recent books and apps from usage data, opened from the panel, the Ink bar or Apps › Recent | Launcher | Boox recents if the user triggers Boox's own gesture |
| 7 | Back and Home | Boox edge gestures, which draw nothing | Kept as the default. Optional Ink bar, and Refresh edge's shortcut row. | Shell | Nothing visible |
| 8 | Sleep screen | Boox screensaver showing a picture the user set by hand | SleepScreen drawn by Ink Home. Launcher: the picture file as now, correct size, new layout. Owner: installed as the system screensaver and kept current automatically. | Launcher, Owner | The manual "set as screensaver" step below Owner level |
| 9 | Power menu | Boox dialog | Panel offers Sleep now (Shell) and Restart (Owner) | Shell, Owner | Boox's dialog on a long power press; power off |
| 10 | Open with | Android chooser | Ink ChoiceSheet listing the apps that can open the file | Launcher | Nothing |
| 11 | Settings | Boox settings and Android Settings | Ink Settings › Device is the hub. Links open Android's own pages. | Launcher | Android Settings pages (plain Android, not Boox) |
| 12 | Boox's shell apps (its launcher and Navigation Ball) | Listed in Apps; the Onyx launcher is seeded as a favourite (`MainActivity.java:1787`) | On Onyx devices these two start hidden, grouped as "Boox apps" under Apps › Hidden, and are no longer seeded as favourites. Boox's ordinary apps (Dictionary, Calculator, BooxDrop and so on) stay where they are. | Launcher; system-wide at Owner | Nothing unless the user shows them |
| 13 | Dialogs and toasts inside Ink Home | Android's | Sheets and Notices | Launcher | Nothing |
| 14 | Refresh handling | Ink Home's manual clean; Boox's E-Ink Centre per app | Folio refresh model inside Ink Home; Refresh edge for a full refresh from anywhere (5.6) | Launcher with the overlay permission, or Shell | Other apps' own refresh modes remain Boox's unless spike S7 finds a way to set them. Linked from Display & refresh as "Per-app refresh". |
| 19 | Floating control | Boox's Navigation Ball (its service is enabled on the C69; the ball wasn't on screen when I looked) | Refresh edge (5.6) | Launcher with the overlay permission, or Shell | The Boox ball only if the user keeps it on |
| 15 | Wi-Fi network list | Android's panel | Direct toggle at Owner level; the network list stays Android's | Owner | Android's network list |
| 16 | Keyboard | Onyx keyboard | Not replaced in this plan. Any installed keyboard can be chosen. A Folio keyboard is a possible later project. | | Onyx keyboard |
| 17 | Permission prompts and the app installer | Android's | Cannot be replaced. Owner level removes most of them for Ink Home itself. | Owner | Android's prompts |
| 18 | Boot logo and animation | Boox's | Cannot be changed without modifying the system | | Boox boot |

### 5.4 Shell rules

- The strip, panel, dot, bar and sleep screen use the same components and tokens as Home. They are not a separate theme.
- There is always one visible way home.
- Ink Home's own surfaces never say "Boox" except under Device and "Boox apps". The device is "this reader".
- Each level is described in plain words before it is turned on: what Ink Home will be able to do, why, and how to turn it off. The accessibility service is used only to draw the strip, dot and bar, to send Back, Home and Sleep, and to notice app switches and page-key presses for Auto clean. It reads no screen content, and the code says so in one obvious place.
- Every shell feature degrades one level at a time. If the overlay can't be drawn, the app is exactly today's launcher.

### 5.5 Keeping it portable (cheaply)

No Linux port is planned. The design stays independent of Android where that costs almost nothing:

- `design/tokens.json` is the source of truth, and the CSS mockups already consume the same values.
- The pure core (4.1) has no Android imports.
- Each system dependency sits behind a small interface with one Android implementation: `Epd` (refresh), `SystemStatus` (time, battery, network), `Connectivity`, `PowerControl`, `AppCatalog`, `Notifications`, `SleepSurface`.
- Component specs in 4.12 are written as geometry and behaviour, not as Android view classes.

### 5.6 Refresh edge

Refresh edge is a slim rectangular tab that can float over other apps. Its ink-and-paper outline remains visible over dark or light content. The touch target extends beyond the visible tab so a reader can tap or drag it comfortably.

**Placement**

- The tab starts docked to the side at mid-height. The user can drag it along the edge or into the usable screen area.
- The position is remembered per rotation.
- Its touch target stays inside the screen bounds after a drag, rotation or resize.
- Per app it can **Show**, **Tuck** or **Hide**. Defaults: Tuck in the built-in reader, in apps that count as reading (the existing list in Settings › Reading) and on Ink Home's own screens, so it never covers text or controls; Show everywhere else. The per-app choice needs Shell level; with the overlay permission alone the choice is global.
- Hidden on the sleep screen and in full-screen video.

**Moving and hiding**

- *Drag* picks up the tab and moves it with the finger. Releasing saves its position.
- Choose *Tuck to edge* in the menu to park it at the nearest side. Drag the tab to bring the tab back.
- *Hide* from its menu ("Hide here", "Tuck to edge"), turn it off in Settings › System shell › Refresh edge, or toggle it from a control panel tile.

**Tap and hold**

- **Tap: one full refresh of the whole screen** (the `CLEAN` intent in 4.9). The flash is the feedback, so the tab draws no pressed state.
- **Hold (400 ms): the refresh menu**, a 252 u panel beside the tab:

| Entry | What it does | Needs |
|---|---|---|
| Clean screen now | The same full refresh as a tap, here so it can be found | Either permission |
| Mode in this app: Normal · Text · Fast | Text is Regal (fewer flashes on pages of text). Fast is a two-level mode for scrolling and typing. | Only if spike S7 finds a way for Ink Home to set another app's update mode on Onyx. Otherwise this row is a link to Boox's own per-app refresh settings. |
| Auto clean: Off · On app switch · Every N page turns | Cleans without being asked. Default: On app switch. | Shell level (it counts app switches and page-key presses; it reads no screen content) |
| Back · Home · Switcher · Control panel | The shortcuts people use the Boox ball for | Shell level |
| Tuck to edge · Hide here · Settings | | |

- Double-tap is not used: telling it apart from a single tap would delay every refresh.

**Living with the Boox navigation ball**

- One floating control at a time. When Refresh edge is turned on while Boox's ball service is enabled, setup says so and offers to open Boox's Navigation Ball settings so the user can switch the ball off. Below Owner level Ink Home does not change that setting itself. At Owner level it can hide the Navigation Ball app, with Aarin's OK.
- If the user keeps both, the tab starts on the left edge and its shortcut row is off, so the two don't duplicate each other.
- With the shortcut row on, the tab covers what the ball is used for (refresh, back, home, recents).

**Permission**

| Route | What the user grants | What the tab can do |
|---|---|---|
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | One switch on an Android settings screen | Float over other apps, tap for a full refresh, move, tuck, hide. Available at Launcher level. A small service keeps it alive while other apps are in front; its notification is filtered out of Ink's own list. |
| Ink Home's accessibility service (Shell level) | The Shell switch | Everything above without the separate overlay permission, plus per-app Show, Tuck and Hide, Auto clean, the shortcut row, and staying above the keyboard. |

Recommendation: offer the tab with the lighter permission, and let the Shell service host it when Shell is on. **On the C69, granting either permission needs Aarin's OK at the moment it happens.**

**Unknowns (spike S7).** Whether `refreshScreen(98)` called from an overlay window cleans the whole screen on Onyx (expected, since the call asks the system for a full update, but not proven). Whether the overlay stays alive in the background. Whether an ordinary app can change another app's update mode. On e-ink devices without Onyx's interface, the tab falls back to a brief black-then-white full-screen flash drawn by the overlay itself.
