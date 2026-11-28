# Writing a Home widget

Home uses one canvas with configurable dimensions. Older layouts open on the original four-column, sixteen-row grid. Each instance in the HomeLayout document stores a stable key, component ID, variant, cell rectangle and string settings. Two widgets of the same type can have independent settings. The reading preset is an ordinary document. The status strip and chevron dock are shared application controls across Home, Library, Stats, Apps and Settings. The editor occupies the same content rectangle and uses the dock space for its actions. First-run setup and permission notices sit above both canvases.

## Add a component

1. Create a class in dev.inkhome that supplies a HomeComponent. ClockTile is a small example using BuiltinTile. Declare a stable ID, display name, non-empty variants and supported width and height spans. Keep IDs stable across upgrades. The first variant is the default. Declare allowed span pairs through supports(width, height). A square face, such as the reading goal, also centres its drawing and frame within a pixel-square area so rectangular grid cells never distort it.
2. Implement renderContent for the supplied instance. The shared HomeTileFrame measures the content area after applying the Folio rule and its inner padding. Use the supplied width and height in pixels to choose the number of items, text lines and icon sizes. A height of Integer.MAX_VALUE requests the natural content height. A bordered widget uses the frame as its top divider. The instance border override takes precedence over the default in Settings › Home grid. Both Home and the editing canvas call this renderer at the same size. The gallery calls it with preview enabled. Keep previews free of launches, subscriptions and new external allocations.
3. Expose optional settings as key-to-choice arrays and implement optionName for user-facing labels. Keep settings local to the instance. Widgets that bind external resources can implement configureNew and call ready when the binding succeeds. Use release for discarded resources. The restoration hooks let a module surface resources retained by an upgrade.
4. Add the component to HomeComponents.all() via the registry list. The editor derives its gallery, variants, settings and size choices from the declaration.

Full-width widgets in the reading preset can use the layout setting `heightMode=content`. The grid measures the component at its available width, divides its natural height across its rows and gives the other rows the remaining space. Resizing in the editor switches the instance to fixed grid sizing.

Each drag handle anchors the opposite corner. The same span rules apply to dragging and to the Width and Height steppers in the inspector. For a square face, stepping either dimension changes both. Tapping a widget opens a compact header outside the selection. Its info button opens the settings.

## Persistence

HomeLayout.load reads the versioned homeCanvasV1 preference. On upgrade it imports existing section choices and older grids. Save replaces the document as one preferences edit. Reading progress, library, app favourites and statistics use separate records. The editor works on a deep copy until Save. Reset replaces that draft with the reading preset.
