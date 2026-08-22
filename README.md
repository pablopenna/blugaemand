# Blugaemand

Turns an Android phone into a real Bluetooth gamepad. The host sees a standard HID controller — no
driver, no companion app, no root. Verified end-to-end against **Linux** and **Windows**.

**Current state:** an Xbox-style pad (two sticks, D-pad, ABXY, four shoulder controls, three centre
buttons, two stick clicks) in eight presentations — **Default** (drawn shapes and labels) plus
**Xbox**, **PS5**, **Switch**, **Switch 2**, **Steam Deck**, **Wii U** and **GameCube** (each
console's button art, laid out the way that console lays its buttons out). You can make your own
from empty or as a copy: move, resize, add and remove controls, including whole arrangements dropped
as **one control**. Triggers are binary by default, analog per control. A stick is fixed or
**dynamic** (appears under your thumb). Pads can be **faded** to 25%, themed, and driven by the
phone's gyroscope (**motion aiming**, off by default). Layouts and the choice of one persist.

Two pills sit at the top edge, each opening its panel on a 600 ms hold: connection status and
pairing on the left, **☰ Menu** on the right (layouts, editor, motion, About, quit).

**[TODO.md](TODO.md) is the live backlog** — what is done, what is next, and a *Known constraints*
section recording what is permanently impossible so it does not get rediscovered. Read it before
planning work, and keep it updated as things land.

---

## Working on this

### Build and test

Requires JDK 17 and an Android SDK with platform 36. Point `local.properties` at your SDK:

```properties
sdk.dir=/path/to/android-sdk
```

```bash
./gradlew assembleDebug     # build
./gradlew test              # JVM unit tests, no device needed
./gradlew lintDebug         # lint — kept at zero issues
./gradlew installDebug      # install onto a connected phone
adb logcat -s Blugaemand    # the app's own state transitions
```

Dependency versions are pinned deliberately (AGP 8.13.2, Kotlin 2.0.21, Compose BOM 2024.09.00,
kotlinx.serialization 1.7.3, DataStore 1.1.1); lint's "newer version available" family is disabled
in `app/build.gradle.kts` as pure noise.

Releases only need `versionName` bumped in `app/build.gradle.kts` — **☰ Menu → About** shows it via
`BuildConfig.VERSION_NAME`.

### Signing a release

Copy `keystore.properties.sample` to `keystore.properties` (gitignored, along with `*.jks`) and fill
in the four values; `assembleRelease` then produces `app-release.apk` instead of
`app-release-unsigned.apk`. Create the key first:

```bash
keytool -genkeypair -v -keystore blugaemand-release.jks -alias blugaemand \
        -keyalg RSA -keysize 4096 -validity 10000
```

- **All four values or none.** A partial set fails the build naming what is missing, rather than
  quietly handing back an unsigned APK — which is the failure that is only noticed at install time.
- **The environment is the other route**, which is what CI uses: `BLUGAEMAND_STOREFILE`,
  `BLUGAEMAND_STOREPASSWORD`, `BLUGAEMAND_KEYALIAS`, `BLUGAEMAND_KEYPASSWORD`. The properties file
  wins where both are set.
- `storeFile` is resolved against the repo root, so a checkout copied to another machine reads the
  same; an absolute path passes through unchanged.
- **Keep the key off the repo and backed up.** A lost key means the app can never be updated in
  place, only reinstalled under a new identity.
- Signing is v2 (APK Signature Scheme v2), which is all an API 28+ install needs.

### CI

`.github/workflows/build.yml` runs the same three commands — `test`, then `lint` and `assemble` for
one variant — on every push and PR to `main`, and on demand. The APK, test and lint reports are
uploaded as artifacts, on failure too, so a phone can be fed a build without a toolchain.

- Manual runs take a Debug/Release `choice` input; automatic runs fall back to Debug in the
  *Resolve build type* step, which is what makes one workflow serve both.
- **Release signs when the four `RELEASE_*` repository secrets are set** — `RELEASE_KEYSTORE_BASE64`
  (the `.jks`, base64-encoded), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
  `RELEASE_KEY_PASSWORD`. Without them it comes out unsigned, which is what a fork's pull request
  gets: secrets are not exposed to those.
- The runner's SDK arrives with licences accepted, so no `local.properties` is written.

### Working with a phone

**A physical Android 9+ phone is required for anything Bluetooth** — emulators have no usable radio.
The encoder, descriptor, touch routing and layout arithmetic are deliberately Android-free, so most
logic is verified with `./gradlew test` instead.

**The development phone is a Xiaomi (HyperOS)**, which gates three adb capabilities behind Developer
options rather than removing them. All three have been seen failing, and all three work with the
right toggles on — a failure here is a setting, not a dead end:

| Needs | Symptom when off |
|---|---|
| *Install via USB* | `INSTALL_FAILED_USER_RESTRICTED`. `pm install` from the shell does not get around it. |
| *USB debugging (Security settings)* | `pm grant` throws `SecurityException`; `input tap` / `input swipe` are denied `INJECT_EVENTS`. This decides whether the UI is scriptable at all. |
| A signed-in Mi account | HyperOS asks for one before the two above can be enabled. |

**With input injection on the whole UI is scriptable**, worth setting up before any work on the pad
or editor: `adb shell input tap x y`, `input swipe x1 y1 x2 y2 ms` for drags and holds, and
`adb exec-out screencap -p > shot.png`. Coordinates are physical pixels; `adb shell wm size` reports
them the wrong way round in landscape, so take `cur=` from `adb shell dumpsys window displays`.

Verification techniques that have proved useful:

- `/proc/bus/input/devices` capability bitmaps validate the descriptor semantically — no root, no
  button pressing; they show which `BTN_*` and `ABS_*` codes the kernel derived.
- `/sys/bus/hid/devices/<id>/report_descriptor` is world-readable: diff what the host received
  against the bytes in `GenericHidProfile`.
- `/dev/input/js0` is world-readable; `js_event` is `{u32 time, s16 value, u8 type, u8 number}`, and
  `type & 0x80` marks the synthetic startup state.
- Both asset scripts refuse input they would silently mangle — examples of validating assets
  programmatically.

### Traps already hit once

- **Cleanup after a suspension point in a keyed `pointerInput` does not run** when the key changes —
  and a gesture whose success changes that key cancels itself. Put anything that must happen in a
  `finally`, or drive it from a `LaunchedEffect`. Two bugs in the hold-to-open pill came from this,
  and it is why `EditorScreen` keys its gesture on the surface size rather than the `ResolvedLayout`
  it edits (every drag frame builds a new one). Read changing values via `rememberUpdatedState`.
- **`Modifier.then(...)` crashes lint** (`SuspiciousModifierThenDetector`). Restructure rather than
  suppress.
- **Adaptive icons need the `-v26` qualifier.** Lint's `ObsoleteSdkInt` suggests folding
  `mipmap-anydpi-v26` into `mipmap-anydpi`; AAPT2 then fails to link. Suppressed in `app/lint.xml`.
- **`painterResource` cannot load an `adaptive-icon` XML** — the About page draws
  `@mipmap/ic_launcher_foreground` over `@color/ic_launcher_background` instead.
- **Top-level `val`s initialise in declaration order.** A layout reading a table declared below it in
  the same file gets null. Kotlin catches that within a file; across files it would be a cycle and
  deadlock at class-load.

### Tests

`app/src/test/` covers the encoder, descriptor, routing, saved format and every edit the editor
makes — all on the JVM.

- `GenericHidProfileTest` — exact report bytes, per-button bit positions, every hat direction, axis
  clamping, and a walk of the descriptor's item stream verifying it declares exactly 9 bytes.
- `TouchRouterTest` — pointer binding and release, multitouch independence, stick normalisation and
  circular clamping, dynamic sticks (spawn point, anchor dead zone, base following the finger, area
  losing to what is drawn on it, one finger at a time), D-pad sectors, cluster routing and the
  no-dead-spots rule, and layout sanity (no overlaps, every button reachable, unique ids). Sanity
  runs over `Layouts.ALL` only — a user layout may be empty or overlapping.
- `LayoutArtTest` — the pack covers every control bar a declared exception list, anything falling
  back to its shape still has a label, and face buttons show the picture for the position they are
  *labelled* for rather than the slot they drive (the guard on the X/Y crossing). A test pins that
  no pack answers for a `DpadButton`; see *Two kinds of D-pad*.
- `LayoutSerializationTest` — round trips, leniency, refusals, the nested cluster shape and its
  `require`s, and a **golden file** compared literally: the guard on every name the format is made
  of. If it fails for a change that is genuinely wanted, the fix is a format version and a migration
  in `decodeLayouts`, not a new expected string.
- `LayoutEditsTest` — every drag, pinch, handle, nudge, add, remove and ungroup, on a 1000×500
  surface making the layout unit exactly 500 and the grid step 25. Round-trip tests catch an axis
  divided by the wrong dimension; *moving one copy leaves the other where it is* holds the line that
  controls are addressed by index. Includes that a plate resolves identically on 16:9 and 4:3.
- `PlacementTest` — something lands centred on the point it was dropped on, a group keeps its
  arrangement and snaps as a unit, nothing ends up part-way off screen, and the preview shows
  exactly what placing would add.
- `ControlGroupsTest` — distinct names, more than one control each, no duplicates, nothing stacked,
  every group centred on the origin, and each checked clustered. Most groups derive from
  `DEFAULT_LAYOUT`, so these fail if a control is renamed or dropped from the default pad.
- `LayoutLibraryTest` — no built-in is editable, `without` cannot reach one, saving replaces in
  place rather than shuffling the menu under a thumb.
- `MotionTest` — which way each turn aims, that the *other* landscape aims the same way, that roll
  aims at nothing, the round dead zone, clamping by length, and that an aim adds to a thumb's input
  without wrapping past the rail.
- `ColorMathTest` — every corner of the RGB cube round trips through HSV, alpha is carried not
  picked, hue wraps while saturation and value clamp, and — as a test rather than a bug — *hue is
  lost on the way to a grey*, which is why the picker holds one of its own.
- `LatencyProbeTest` — nearest-rank percentiles, the ring buffer, and a partly filled buffer
  summarising only what is in it rather than counting untouched zeros as instant reports.
- `PadThemesTest` — distinct names, opaque colours, and a measured luminance gap between resting and
  pressed.

### Art assets

Each family lives under `art/` with a script that regenerates what ships.

**The launcher icon** is pixel art. Export `art/icon/icon.aseprite` to `art/icon/icon.png` (54×54,
RGBA) and run `python3 art/icon/generate-launcher-icons.py`. 54×54 upscales to all five adaptive-icon
buckets (108/162/216/324/432 px) on whole pixels, so every export is nearest-neighbour and the grid
stays square; pre-scaling also avoids Android's bilinear filtering. The monochrome layer is
*derived*, not copied — themed icons use only the alpha channel, and the art's alpha is a solid
square.

**The input prompts** are [Kenney's Input Prompts](https://kenney.nl/assets/input-prompts) 1.5A,
**CC0**. The SVGs actually used live in `art/input/` beside the licence, so drawables are
regenerable from a clean checkout: `python3 art/input/convert-input-art.py`.

- **`art/input/` is flat and Kenney's download is not.** `Nintendo Switch` and `Nintendo Switch 2`
  each ship a `switch_button_zl.svg` — different pictures under one name. Only the four redrawn
  Switch 2 triggers are carried across, renamed `switch2_*` on the way in. That is the one place a
  file here does not match its name upstream; check for it whenever art from a new folder is added.
- **The conversion is a text transform, not an SVG renderer**, and can be because these files are
  uniformly simple: 64×64, one or two `<path>`s, no strokes, a solid hex fill, and only `M`, `L`, `Q`
  in the path data — all valid as `android:pathData` unchanged. The script checks every file against
  that and refuses anything it would mangle.
- `generic_joystick` and `generic_joystick_red` are **chrome, not control art**: they mark the two
  halves of the menu's layout list and are referenced as `R.drawable` from `MenuBar`. They
  deliberately do not become `ControlIcon` entries — that enum is a saved-layout compatibility
  surface. Hence `ControlIcons` is the only place that maps a `ControlIcon` to a drawable, not the
  only place mentioning `R.drawable`.
- **Not every button has art**, and a fallback beats a near-miss. No PlayStation logo ships, so the
  PS5 guide button draws as a shape. Most Nintendo and Valve sets draw face buttons in one colour,
  so the pressed picture is a solid fill; GameCube's A and B really are green and red. Only Switch
  and Steam Deck draw a pressed stick.
- **Vectors are a preference, not a requirement.** A PNG in `res/drawable-nodpi/` works with no code
  change — use `nodpi`, since the renderer already sizes every glyph from the layout unit. Size
  floor is ~256 px (a face button renders ~155 px on a 1080p landscape screen), which is why
  Kenney's 128 px raster set is not worth using.

---

## How it works

Android's **`BluetoothHidDevice`** (API 28+) registers an SDP record containing a raw HID report
descriptor, then pushes input reports over **Bluetooth Classic HID**. No third-party library. This
is what forces `minSdk 28`.

**Why not BLE?** HID-over-GATT is a dead end on Android: apps cannot register the HID service UUID
(`0x1812`) on the platform's GATT server. Classic HID is what almost every real controller uses.

### What the host sees

Report ID 1, nine bytes:

| Byte | Contents |
|-----:|----------|
| 0–1 | Left stick X, Y — `0..255`, centred at `128` |
| 2–3 | Right stick X, Y — `0..255`, centred at `128` |
| 4–5 | Left / right trigger — `0..255`, as Brake and Accelerator |
| 6 | D-pad hat in the low nibble (`0`=N clockwise to `7`=NW, `8`=centred) |
| 7–8 | Sixteen buttons |

Decisions in [`GenericHidProfile.kt`](app/src/main/java/com/blugaemand/hid/GenericHidProfile.kt)
worth knowing:

- **Triggers live on the Simulation Controls page** (`Brake` / `Accelerator`) rather than as generic
  axes — the convention DualShock and Xbox Bluetooth controllers follow, and what makes hosts map
  them without configuration. On Linux they arrive as `ABS_BRAKE` / `ABS_GAS`.
- **Button numbering is not sequential** — it follows the order Linux's `hid-input` assigns to a
  gamepad collection (`BTN_SOUTH`, `BTN_EAST`, `BTN_C`, `BTN_NORTH`, …), which Android inherits.
  Windows numbers positionally and does not care, so matching costs nothing.
- **Face buttons are named by position** — `SOUTH`, `EAST`, `NORTH`, `WEST` — because letters are
  not portable: Xbox puts Y north and X west, Nintendo swaps both pairs, and HID's legacy aliases
  (`BTN_X` for `BTN_NORTH`) are a third arrangement. Hosts report the aliases, so `DEFAULT_LAYOUT`
  puts Y on `WEST` and X on `NORTH`. **Anything keyed off a face button follows the label, not the
  slot** — `XBOX_ART` pairs `WEST` with the *Y* picture, `PLAYSTATION_ART` with the triangle.
- **The hat carries a null-state flag**, which lets a value above the logical maximum mean
  "centred". Without it the D-pad rests stuck pointing north.

### Known limitation: DirectInput, not XInput

On Windows the phone enumerates as a **DirectInput** gamepad, never XInput — that is a kernel-driver
interface Bluetooth HID cannot reach, and no app can work around it. Steam, emulators, most engines
and anything reading raw HID work; XInput-only games do not, though Steam's controller support will
remap it.

---

## Architecture

One idea shapes the codebase: **the parts that are hard to get right have no Android dependencies**,
so they are tested on the JVM in milliseconds instead of on a phone paired to a host. Only the
service and the Compose layer touch the framework.

```
  finger on glass                      phone turning in the hand
        │                                        │
        ▼                                        ▼
  GamepadScreen ──── pointer events        MotionSensor ──── gyroscope, rad/s
        │            (Initial pass)              │
        ▼                                        ▼
  TouchRouter ────── pointerId → control   MotionSettings.aimOf() ── a stick offset
        │            binding, hit-tested          │
        ▼            against ResolvedLayout       │
  GamepadState ───── 6 axes + hat + 16-bit mask   │
        │                                         │
        └──────────────► withAim() ◄──────────────┘
                            │
                            ▼
              HidGamepadService  sends on change, no faster than 100 Hz,
                            │    drops reports identical to the last
                            ▼
              GamepadProfile.encode() ── 9 raw bytes
                            │
                            ▼
              BluetoothHidDevice.sendReport()  →  host
```

The two sources combine in `MainActivity`, not in either of them: fingers arrive on a pointer event
and rotation on a sensor callback, so whichever arrives has to send the pair.

### Modules

| Package | Android-free | Contents |
|---|---|---|
| `hid/` | mostly | `GamepadState`, `GamepadProfile`, `GenericHidProfile` are pure Kotlin; `HidGamepadService` is not |
| `input/` | yes | `ControlSpec`, `ControlIcon`, `ArtPack`, `GamepadLayout`, `LayoutStyle`, `PadThemes`, `LayoutJson`, `LayoutLibrary`, `LayoutEdits`, `Placement`, `ControlGroups`, `ResolvedLayout`, `TouchRouter`, `art/`, `layouts/` |
| `motion/` | mostly | `Motion` (settings and the gyro-to-stick mapping) is pure Kotlin; `MotionSensor` is not |
| `data/` | no | `LayoutStore`, `SettingsStore` |
| `ui/` | mostly | the Compose layer; `ColorMath` is plain Kotlin |

**`hid/`**

- `GamepadState` — immutable snapshot plus the `GamepadButton` enum fixing HID button numbering.
- `GamepadProfile` — the host-compatibility seam: SDP metadata, report descriptor, `encode()`, and
  an optional `requiredAdapterName` hook.
- `GenericHidProfile` — the only implementation. The descriptor is commented raw bytes; treat it as
  the most safety-critical file in the repo.
- `HidStatus` — sealed UI state. `PermissionRequired` and `BluetoothOff` are separate because they
  need different things from the user.
- `HidGamepadService` — foreground service owning the profile proxy, SDP registration, the callback
  and the send loop.

**`input/`**

- `ControlSpec` / `GamepadLayout` — a layout as plain data in normalised coordinates: a
  `List<ControlSpec>` plus a `LayoutStyle`.
- `LayoutStyle` — `Colors` or `Images`; a layout is in exactly one.
- `PadThemes` — six ready-made resting/pressed pairs, `Slate` first.
- `ControlIcon` — one picture from a pack, as an enum of names. Deliberately **not** a drawable
  resource ID: those are reassigned every build, so a saved layout holding one would come back
  pointing at a different picture. A name identifies a *picture*, not a role — naming roles would
  cost the ability to name one specific picture, which layouts mixing packs need.
- `ArtPack` — `ControlId → Glyph` (idle picture plus optional held one), named once on the style
  rather than on every `ControlSpec`. A pack need not be complete. Carries a display `name` beside
  its slug `id`; the format writes only the id.
- `art/` — the built-in packs plus `ArtPacks.ALL`, the catalog a saved pack id resolves against. One
  file each, and the one place each console's face-button crossing is decided.
- `layouts/` — one file per built-in plus `Layouts.ALL`, the catalog the menu lists. **Derivation
  happens at class-load, so it does not extend to user layouts** — a copy that is then edited moves
  nothing but itself.
- `ResolvedLayout` — converts a layout to pixels once per size change. Renderer, hit-testing *and*
  the editor all read from it, so what is drawn is exactly what is touchable and exactly what a drag
  moves. A dynamic stick breaks that on purpose; see *Two kinds of thumbstick*.
- `TouchRouter` — owns `pointerId → control` bindings and produces a `GamepadState`. A binding keeps
  the point it went down at (what a trigger's pull is measured from, and where a dynamic stick
  appears) and the surface it is on.
- `LayoutJson` — the saved format and its two small serialisers.
- `LayoutLibrary` — built-ins plus the user's own. Editability is a fact about where a layout came
  from, not about the layout, so `GamepadLayout` carries no flag; `isEditable` is the one place that
  line is drawn.
- `LayoutEdits` — every edit as arithmetic on plain data. Where the editor is actually tested.
- `Placement` / `ControlGroups` — controls waiting to be dropped, positioned relative to the point
  they will land on, and the built-in arrangements of several at once.

**`motion/`** — `Motion` is `MotionSettings` plus `aimOf`, the gyro-to-stick mapping, plain Kotlin.
`MotionSensor` registers the gyroscope only while motion is on, reporting on a sensor thread.

**`data/`**

- `LayoutStore` — a Preferences DataStore holding two keys: the user's layouts as JSON and the id of
  the selected one. Preferences rather than a typed `DataStore<T>` because the payload is already
  one string. **Stored JSON that will not parse is reported as an empty library and left where it
  is** — it is still the only copy of someone's work.
- `SettingsStore` — a second DataStore, for settings about the app and phone rather than a layout.
  A separate file because DataStore allows one instance per file and two delegates over one name
  crashes.

**`ui/`**

- `GamepadScreen` takes the layout as a parameter and never reads a global — that is what lets the
  menu switch layouts by handing it a different instance.
- `EditorScreen` — the pad with its wiring pulled out. A screen of its own rather than a mode,
  because the two share no input at all; the reuse is `drawControl` one level down, which is where
  it belongs.
- `PadStyle` — a `LayoutStyle` resolved for drawing (ARGB `Int`s → `Color`, glyph names →
  `Painter`), because `painterResource` is a composable while the pad draws inside a `Canvas`
  lambda. **`drawGlyph` rasterises every picture at one fixed square and lets the canvas scale it**:
  a vector painter re-renders when its requested size changes and applies that a frame late, so two
  controls sharing an icon at different sizes each drew the other's picture.
- `ControlIcons` — `ControlIcon → R.drawable`, the only file mapping the two.
- `TopBar` — the two pills and whichever panel is open below. Panel state is one nullable
  `TopPanel`, not a boolean each, so "only one open at a time" is structural.
- `TopBarChrome` — `HoldPill`, `TapPill`, `PillRow`, `Modifier.pillSurface`, `PanelCard`,
  `PanelEntry`, `PanelBack`, `PanelDivider`, `PanelCaption`: the shape, rows and gestures every pill
  and panel is built from. The pill's look is a `Modifier` because it must come *before* whatever
  gesture or decoration the caller adds, or the fill paints over it.
- `ConnectionBar` — status, pairing and reconnection.
- `MenuBar` — picking a layout, making one, editing, *About*, quitting. Page state lives inside the
  composable, which is only composed while open, so the menu reopens on its root page without a
  reset that would visibly flip pages mid-close. *About* reads the launcher icon, `app_name` and
  `BuildConfig.VERSION_NAME` from what the build already states, plus a row opening the repository.
- `EditorBar` — the editor's pill and panel, **collapsed by default** (an always-open panel covers
  the top centre and makes controls under it unselectable) and built on `TapPill` (nothing being
  edited is connected to anything, so the hold would be a tax). **Split by what an option acts on**:
  the ☰ Layout panel is the layout; anything scoped to the selection is a pill in the head bar
  (`SelectionPills` — trigger and stick modes, *Ungroup*, *✕ Remove*, plus `NudgePad`).

Chrome conventions: **a sub-page's first row says *Back***, not where you are — the page's name is
on the row you tapped to get here. ***Done* is a pill, not a panel row** — it is taken more often
than anything on the panel. **Red is spent on one thing**, `OverlayColors.Destructive`, on *Delete
layout* and its confirmation; a second red row is what would stop the first meaning anything.

### Design decisions that are easy to undo by accident

- **Reports are rate-capped, not sent per touch event.** The send loop waits on a change, sends, then
  holds for `MIN_SEND_GAP_MS` (10 ms) on a dedicated max-priority thread, skipping reports identical
  to the last. **The cap is applied after a send rather than by polling a timer**: a poll makes every
  change wait for the next tick, costing an isolated press half the interval on average with no
  traffic to coalesce it with.
- **A pointer stays bound to the control it went down on** until release, even if it strays. Real
  gamepads behave that way, and it stops a stick being stolen mid-swing.
- **Sizes scale from `ResolvedLayout.unit`**, `min(height, width × 9/16)`, not screen height. Sizing
  off height alone bloats controls on squarer screens while width-relative spacing does not, and
  neighbours collide at 4:3. A unit test enforces no overlaps at two aspect ratios.
- **`axisFromUnit` scales by 128 and clamps**, not 127. A 0..255 range centred at 128 is not
  symmetric; by 127, full-left reads `1`, and a stick that never reaches its stop is worse than
  saturating the top 1/128th of travel.
- **The service binds before it starts.** A `connectedDevice` foreground service requires a Bluetooth
  runtime permission to be *granted*, not merely declared, so the activity binds first (so the UI can
  show status with nothing granted) and calls `startForegroundService` only once it is held.
- **Colours are plain ARGB `Int`s in `input/`**, not Compose `Color`s — that is what keeps the
  package free of `androidx` imports. `PadStyle` converts at the `ui/` boundary.

### The two seams

- **`GamepadProfile`** — a fussier host means a new implementation, not special cases in the service.
  The Switch was the intended proof and turned out impossible for an unrelated reason: it identifies
  controllers by USB vendor and product ID, which Android gives an app no way to publish. See *Known
  constraints* in [TODO.md](TODO.md).
- **`GamepadLayout`** — it needed no changes to become the saved format. The one change still queued
  is user-supplied art, which turns `ControlIcon` into a sealed `Builtin | File`; the format's
  `version` exists for exactly that.

---

## Layouts and the pad

### Two presentations

A layout declares how it is drawn and is in exactly one mode — alternatives, not layers, so nothing
has to decide what a glyph on a coloured plate would mean.

| | `LayoutStyle.Colors` | `LayoutStyle.Images` |
|---|---|---|
| Controls | drawn shapes, text labels | a picture per control, from an `ArtPack` |
| Colours | two ARGB values: resting and pressed | none — the art carries its own |
| Pressed | the fill changes | a second picture swaps in, if the pack has one |

The mode is chosen in the editor on *Appearance*, which lists *Shapes and colours* and every pack in
`ArtPacks.ALL`, with the colour picker under a rule while colours mode is in force. Unconditional,
where the old *Colours* row vanished the moment a layout took a pack — exactly when someone goes
looking for the way back off it. **Leaving a pack restores the colours the layout last had**, held
by `MainActivity` while the editor is open and deliberately not persisted.

**The colour picker** is a saturation/value square over a hue bar, with *At rest* and *Held* as rows
choosing which fill it adjusts — one picker, because a 240 dp panel has room for one square. Two
load-bearing details: it **holds a hue of its own** (black and every grey have none, so a colour read
back from the bottom of the square reports hue zero and a blue pad would silently turn red), and
**alpha is carried through, never picked** (see *Opacity and themes*).

Consequences worth knowing:

- **Geometry and hit-testing are shared.** `ResolvedLayout` and `TouchRouter` know nothing about
  either mode. In `Images` mode a wide control keeps its full touch area while its glyph is drawn
  square and smaller — easier to hit than it looks, which is the right way round.
- **A pack is not only pictures.** `PS5_LAYOUT` also moves its left cluster, because symbols in the
  wrong positions fight the muscle memory that comes with them.
- **Thumbsticks stay drawn in both modes** — no static picture can show a knob displaced from centre.
  A stick is three circles: under the cap a smaller pale shaft travels the same direction at half the
  distance, which reads as a stick tilted away from the thumb (`SHAFT_RADIUS`, `SHAFT_TRAVEL`).
- **A control with one picture does not animate.** `Glyph.pressed` is optional and falls back to the
  idle one; `Glyph.idle` is not, which makes a pressed-only control unrepresentable rather than
  something to test for.
- **Anything the pack does not name falls back to its shape and label**, which stops a gap rendering
  a hole. Lending a neighbour's glyph would put a different button's picture on this one.
- **Only the two fills that change with press state belong to the layout.** Strokes, the stick well
  and shaft, and the canvas stay in `PadColors`: a layout free to recolour its strokes is a layout
  free to make itself invisible.
- **A label is not a layout colour and not a fixed one.** `labelOn` picks black or white by WCAG
  contrast against the current fill, crossing over near 0.18 relative luminance.

### The eight built-in plates

| | Geometry | Face plate |
|---|---|---|
| **Default** | authored; the others start here | drawn shapes and letters |
| **Xbox** | derived from Default, unchanged | Xbox prompts |
| **PS5** | derived, left cluster restated | PlayStation prompts |
| **Switch** | authored in full | Switch prompts |
| **Switch 2** | derived from Switch, unchanged | Switch prompts, triggers redrawn |
| **Steam Deck** | authored in full | Steam Deck prompts |
| **Wii U** | authored in full | Wii U prompts |
| **GameCube** | authored in full | GameCube prompts |

Two derive because a Series pad genuinely *is* the default arrangement and a Switch 2 Pro Controller
genuinely is a Pro Controller; what the newer Nintendo pad adds (C, back paddles) has no slot in the
profile. The rest are authored, because deriving would make them the same pad with different
pictures on it:

- **PS5** puts the D-pad opposite the diamond and drops the left stick level with the right. The
  D-pad is much larger — the one control a thumb sweeps across — and sits a hair above the diamond's
  centre, the last place it can go before its touch square meets the stick's.
- **Switch** leans both lower controls inboard, so the clusters splay outwards as they rise; Minus
  and Plus are a high, wide pair with Home centred below.
- **Steam Deck** rides high and outboard — the lower third of each side is trackpad — with a tighter
  diamond and a spread centre.
- **Wii U** puts *both sticks in a row along the top* and both clusters in a row underneath.
- **GameCube** is the only plate without a diamond: a large central A with B, X and Y as satellites,
  and the C-stick tucked underneath.

Three things no plate can show: the Deck's trackpads and paddles, the Switch 2's C button and
paddles, and that neither a Pro Controller nor a GameCube pad has L3/R3 at all. Stick clicks go
along the bottom edge, out of the way, because there is no authentic place for them. **The GameCube
plate is where a pack is most visibly incomplete** — five controls draw as plain shapes with neutral
labels, because the 2001 pad has no such button.

**The Nintendo plates are where the face-button crossing bites hardest.** Every pack keys its glyph
to the position on the diamond, and Nintendo swaps *both* pairs:

| Position | Slot | Xbox | Nintendo |
|---|---|---|---|
| top | `WEST` | Y | **X** |
| left | `NORTH` | X | **Y** |
| right | `EAST` | B | **A** |
| bottom | `SOUTH` | A | **B** |

So **the key drawn A sends what an Xbox pad's B sends** — the swap every Switch owner already lives
with; matching the printed letter instead would move the button under the thumb rather than the
picture on it. Two plates are exempt for opposite reasons: Valve kept Microsoft's arrangement, and
GameCube predates the swap, making it the one Nintendo plate where copying the Switch table would be
wrong in all four positions.

### Built-in and user-made

**The built-ins are read-only** — `val`s in the source. You make your own from empty or as a copy,
and `LayoutLibrary.isEditable` is the only place that line is drawn, so the menu offers *Edit layout*
only when it is true and there is no disabled row to explain. A copy takes a fresh UUID, so an
imported layout can never land on top of one already here; it keeps everything else, art pack
included.

The Layouts page renders `builtIn` and `user` as two runs separated by a rule, each row marked with a
Kenney joystick — plain for what ships, red for yours. That is why `MenuPanel` takes the whole
`LayoutLibrary`: a flat list cannot say which half a row is in. The rule is suppressed when you have
made nothing.

User layouts are deliberately allowed to be **incomplete, empty or overlapping**. The sanity tests
apply to `Layouts.ALL` only; `missingButtons()` is a caption in the editor — a warning, not an error.

### Editing

Four operations — move, resize, add, remove — plus two colours and a rename. A nudge is a move by an
exact amount and a handle drag a resize by one, so everything a drag is clamped and snapped by
applies unchanged. All the arithmetic is in `LayoutEdits` and `Placement`; `EditorScreen` is nothing
but gestures.

**A control's identity, for editing, is its index in `layout.controls` — not its `ControlId`.** The
same id may appear more than once. The id says what a control *does*, the index which one it *is*:
selection, moving, resizing and the pressed highlight key on the index; what the host is sent and
which glyph is drawn key on the id. Getting it backwards is not a compile error — it looks like one
A button lighting both.

**Adding is pick, then tap where it goes**, with a preview following the finger, because a default
position is almost never the wanted one. `Placement` is that shape. **A control arrives at the size
and label `DEFAULT_LAYOUT` gives it** — only the position is chosen. Six ids are not on the default
pad and carry fallback specs derived from `ControlId.ALL` rather than listed, so a new id with no
home fails at class-load rather than the first time someone adds it.

Decisions that are easy to undo by accident:

- **The grid is in pixels, off the layout unit.** In normalised coordinates cells would not be square
  (x divides by width, y by height), so two controls both "on the grid" would not line up. Sizes snap
  against the same pixel grid, which is why `Rect.width` — a fraction of *screen width* — is
  converted through its own reference.
- **Deltas accumulate across a gesture** and apply to the geometry as it was at touch-down. Otherwise
  a drag slower than half a grid step per frame rounds back every frame and never moves.
- **A move is clamped so the control stays wholly on screen**, not merely so its centre stays in
  `0..1`.
- **A group snaps as a unit** — the drop point is snapped once and members keep their offsets — and
  **centres on its bounding box**, not the average of its positions, which with an odd member out
  drifts towards the crowded side.
- **Corner handles keep the aspect ratio, edge handles do not.** That is the whole reason for eight
  rather than four: a pinch can only scale uniformly. Only a `Rect` and a dynamic stick's area have a
  size per axis; `scalesPerAxis` is where that is decided, and an edge handle on a round control
  scales it whole rather than doing nothing.
- **A handle anchors the opposite edge.** The size comes from where the dragged edge ends up
  (`draggedHalfExtent`), so half the extent changes by *half* the delta — changing it by the whole
  delta moves the edge at twice the speed of the thumb.
- **Snapping lands the dragged edge on the grid, not the size.** Rounding the size jumped a 288 px
  trigger to 270 px on the first pixel of a drag, and the edge is what has to line up with another
  control's.
- **Growth stops at the glass.** The dragged edge is held inside the surface; letting the size run
  and relying on the on-screen clamp afterwards shoves the whole control back, anchored edge and all.
- **There is no maximum size.** A ceiling picked against the biggest built-in is a guess about
  layouts nobody has made, and made the two limits asymmetric for no reason a user could see.
  `MIN_CONTROL_EXTENT` stays and is the only one.
- **Clamping on screen takes the whole `ControlSpec`, not the bare shape** — a plate's extent comes
  from its members, so id and shape have to arrive together.

### Overlapping controls all press together

`hitTestAll` answers with **every** control under a touch point, nearest centre first, and `down`
binds the finger to all of them. Stacking was always representable, and the nearest centre taking the
touch made the control underneath unreachable with nothing to say why. Each binding holds until the
finger lifts, and a second finger binds again rather than stealing. **The editor still selects one**
— `hitTest` is `hitTestAll` taking the nearest; dragging two controls with one finger is not an edit
anyone means to make.

### A cluster: one control with several buttons on it

`ControlId.Dpad` was already a control resolving a touch by *where inside itself* it landed.
`ControlSpec.Shape.Cluster` generalises it: a plate of members, one entry in `layout.controls`, one
thing to select, move, resize and delete.

- **Which member a touch means: the one whose own area it is in, else the nearest centre.** Nearest
  alone splits at the midpoint between centres and ignores size — on the centre cluster that line
  falls inside Home's own glyph. The fallback leaves no dead spots, so a thumb can roll from member
  to member without lifting. The member is re-read on every pointer event, not fixed at touch-down.
- **Everything inside a plate is a fraction of the layout unit**, offsets and member `Rect.width`
  included. In screen fractions a face diamond's vertical gap is 1.74 radii on 16:9 and 2.31 on 4:3;
  in unit fractions it is rigid, and resizing is one factor applied to every number. The built-in
  geometry is authored against 16:9, so `ControlGroups.clustered` stretches widths by `16/9` going in
  and passes heights through.
- **Members live on the shape, not on the id.** Everything asking how big a control is asks its
  shape. `ControlId.Cluster` is a marker carrying nothing, and is deliberately absent from
  `ControlId.ALL` — there is no such thing as *the* cluster, only a particular arrangement.
- **Art needed nothing adding.** A plate draws as its members and a member carries an ordinary
  `ControlId`, which is what `ArtPack.glyphs` is keyed by. `rememberPadStyle` resolves every icon
  regardless of the layout, and `ArtPack.glyph` is a map lookup, so a plate's own id simply misses.
- Members are plain buttons, triggers and D-pad arms; a `require` in the constructor — which runs on
  deserialise, so a hand-edited file is caught — rejects anything else and an empty plate. A `Stick`
  is out because a cap is positioned through `stickTouch`, keyed by top-level control.
  `decodeLayouts` turns those failures into `SerializationException`, which is what `LayoutStore`
  catches; without it a bad stored file would crash rather than report an empty library.
- **A plate scales by one factor, floored by its smallest member** — what a thumb aims at is a
  button, so the first member to reach the floor decides. Snapping applies to the plate's extent
  once, and a resized plate is pulled back on screen because it grows about its centre by half a
  plate rather than half a radius.
- **Ungroup is the way back out.** A member is not separately selectable, by design; *Ungroup*
  replaces the plate with its members exactly where it was drawing them.

**A group can also go down loose**, through the *As one control* switch on the group page (**on** by
default, since the arrangement is almost always why a group is being placed). Loose members are
ordinary controls the moment they are dropped and nothing records that they arrived together. The
switch is held by `MainActivity` alongside **Grid**, not by the panel — the panel is destroyed every
time it closes, which placing something does.

### Two kinds of D-pad

Both send the host one hat.

| | `ControlId.Dpad` | four `ControlId.DpadButton`s |
|---|---|---|
| On screen | one cross | four separate controls, put where you like |
| Diagonals | roll the thumb across it | hold two arms |
| Resolved by | sector, from where in the cross the touch landed | `Hat.of(up, down, left, right)` |

The four-button form cost almost nothing: `Hat.of` was already in `hid/GamepadState.kt`, tested and
unused, and the hat has always been computed in `TouchRouter`.

- **The hat is settled after the whole binding loop**, not inside it — one arm says nothing about the
  value, and that is also what makes opposing arms cancel rather than last-read-wins.
- **A held cross beats the arms**: a layout carrying both is one where the cross is the deliberate
  control.
- **The one-piece cross lights the arm being pushed.** `TouchRouter.dpadPush` answers with the same
  `Hat` the host is sent — the same arithmetic, not a second opinion about sector boundaries — and
  `ArtPack.dpadArms` maps four of those to pictures. Kenney draws no diagonals, so a diagonal falls
  back to the cross lit whole; a thumb in the dead zone draws the *resting* cross.
- **The arms draw as shapes with arrow labels, and that is a finding rather than a gap.** Every
  D-pad picture in the pack is a *whole cross*, which is what makes them right for the one-piece
  cross and wrong for an arm placed alone — a four-button D-pad would wear four complete crosses, and
  a four-arm plate a cross made of crosses. So arm pictures live in `dpadArms`, not in `glyphs` under
  `ControlId.DpadButton`. Per-arm art needs art of an arm, which this pack does not have.

### Analog triggers

A trigger sends a value, not a press, and each is set to one of two `TriggerMode`s by a head-bar pill.

**Binary is the default**: `255` while touched, `0` otherwise — what a trigger did before it was
analog. Most games only ask whether the trigger is down, so a pad should behave like a pad before it
behaves like a pedal. Nothing about where the finger goes matters, so a binary trigger can be tucked
anywhere. **Every built-in is binary**, none naming a mode.

The mode lives on `ControlSpec`, not `ControlId.Trigger`: an id is compared as identity all over the
app (`withControlAdded`, `missingButtons`, the add page), so a mode there would make a binary ZR and
a progressive ZR different controls. Per control, not per layout — a progressive accelerator and a
digital handbrake is one pad. `LayoutEdits.withTriggerMode` sets it on *every* trigger of a plate.

**Progressive** takes its value from where the finger has slid to since it went down:

- **A touch rests in the middle** (`128`, `TRIGGER_TOUCH_REST`) because a trigger is slid both ways.
  That is comfortably over `GenericHidProfile`'s digital threshold of 32, so a tap still asserts
  `L2`/`R2` for hosts that only read buttons.
- **Which way means *more* depends on the axis**, measured from whichever screen edge the finger is
  nearer along the axis in play: **sideways, in towards the middle raises it; up or down, out towards
  the nearer edge raises it.** So a ZR at `(0.91, 0.08)` is raised by sliding left or up, and a
  trigger along the bottom by sliding down. Neither is a fixed compass direction. The two senses come
  out opposite deliberately — the thumb draws in off the side of the glass and pushes out over the
  top of it.
- **One axis at a time**, whichever component of the drag is larger, re-read on every event. Summing
  would make a diagonal do something neither part does, and a trigger in a corner has two directions
  that plainly mean "less". An exactly diagonal drag counts as vertical.
- **Travel is capped by the room there is.** The nominal throw is `TRIGGER_TRAVEL_SPANS` of the
  control's shorter way across, but each direction takes the smaller of that and the distance to the
  edge — so both rails stay reachable however tightly a layout tucks a trigger into a corner. The
  cost is that whichever runs at an edge is touchier; if the top of the range wants a longer pull,
  move the trigger down the layout.
- **The touched range is `1..255`.** `0` is what every host reads as released, so reserving it makes
  "touched, at rest" sayable.
- **The pull is measured from the touch-down point**, not from the control: where within a trigger a
  finger lands is not something anyone aims. The same anchor answers which edge is nearer and how
  much glass is left that way.
- **The value is drawn in a pill just clear of the control** while a finger is on it — below unless
  that would fall off the glass. `TouchRouter.triggerValue` is the same number the host is sent,
  computed the same way. **A binary trigger has none**: a pill pinned at `255` adds nothing, and a
  number that never moves reads as a broken analog trigger.

### Two kinds of thumbstick

**Fixed** sits where it is drawn. **Dynamic is a rectangular area with no stick in it**: a touch
anywhere inside makes one appear at that point reading `0,0`, the finger drags from there, lifting
takes it away. It answers what a fixed stick is bad at — a thumb hunting for the stick in the dark,
mid-game. `StickMode` lives on `ControlSpec` for the same reasons the trigger's does.

**The anchor was already there** — a `Binding` keeps the touch-down point for the trigger's pull, and
a dynamic stick is that idea on both axes. That is the whole routing change.

- **It breaks "what is drawn is exactly what is touchable" the honest way.** **The area is the
  control**: `contains` and `extentX`/`extentY` answer with the rectangle, so hit-testing, dragging,
  the on-screen clamp and the selection ring are all about the thing on the glass. The stick inside
  is a transient the renderer places from the router, as the trigger read-out already is.
- **The renderer is told where the base is**, not just the offset: `stickTouch` returns a
  `StickTouch` (base in pixels, offset as -1..1). A fixed stick answers there too, about its own
  centre, which leaves the renderer one case instead of two.
- **The anchor does not move.** Past the radius the value clamps, so a finger that keeps going holds
  full deflection and comes back to exactly the centre it left. **The area is for spawning only** —
  the finger is free to leave it, the same rule that lets a thumb roll off a button.
- **The area loses to anything drawn on top of it.** `hitTestAll` gives the touch to non-area
  controls containing the point and only falls back to areas when nothing else was touched. That
  keeps areas out of the stacking rule, and makes them the first control *meant* to overlap others —
  so the no-overlaps test skips pairs involving one.
- **One stick per area: first finger wins, the second is ignored.** Not queued — a second stick from
  the same area would fight the first for the same two axes. A finger refused that way binds to
  nothing, exactly as a touch on bare glass does.
- **A small dead zone at the anchor** (`DYNAMIC_DEAD_ZONE`, 12% of the radius) with the rest of the
  throw stretched over what is left, because the anchor is wherever the thumb landed rather than
  anywhere it aimed. A constant, not a field: it compensates for a thumb, not for a layout's taste in
  sizes. **The fixed stick gets none** — its centre is a place you can feel.
- **Throw and area are sized separately** on `Shape.Stick`: `radius` is travel, `areaWidth`/
  `areaHeight` the spawn rectangle. A pinch or handle drag on a dynamic stick resizes **the area** —
  a bigger spawn region should not cost a longer sweep — so the throw is tuned by switching to fixed,
  resizing, and switching back. Nothing is lost across the switch either way.
- **An empty area still draws its outline**, faint, on the pad as much as in the editor: it is the
  one control with nothing of its own to draw, and an invisible one is indistinguishable from a lost
  control.

### Motion aiming

The gyroscope drives one of the thumbsticks. Off by default; **☰ Menu → Motion** sets which stick,
sensitivity and vertical inversion.

**It is not a motion axis on the wire, on purpose.** Gyro axes in the descriptor would produce axes
no host maps to anything. What games read is a thumbstick, which is also what every desktop gyro-aim
setup does — useful the moment it is switched on rather than after a host-side driver that does not
exist.

**Rate, not angle.** Deflection is proportional to how fast the phone is turning. Integrating to an
angle needs a resting pose and drifts within a minute; a rate mapping self-centres the instant the
phone stops, needs no calibration, and is what a stick pushed and released does anyway. Full
deflection is `MotionAim.FULL_SCALE_RATE`, 4 rad/s (~230°/s).

- **Added to the stick, not substituted for it** — the stick makes the turn, the phone the last few
  degrees.
- **The screen's rotation is part of the mapping.** The sensor frame is fixed to the phone's
  *natural* orientation, so `sensorLandscape` is read per event rather than cached; a pad that aims
  backwards when picked up the other way round is unusable rather than merely wrong.
- **Roll is ignored** — turning the phone in the plane of its own screen points the barrel where it
  already was.
- **The dead zone is a turn rate** (`DEAD_ZONE_RATE`) and **round rather than square**: what is
  rejected is tremor and gyroscope bias, which is a speed and not a pair of speeds. Without it a
  phone on a table walks the stick off centre.
- **Clamped by length, not per axis**, so a fast diagonal swing stays on its diagonal.
- **It stops while the pad is not the thing on screen**, derived from what is being shown rather than
  released at each of the five places that cover the pad — the gyroscope only had to be forgotten at
  one of them to be left aiming.
- **App-wide, not part of a layout**: it is a setting about the phone and the hands holding it.

A phone with no gyroscope says so on the page rather than hiding the row.

### Opacity and themes

**Opacity** is `GamepadLayout.opacity`, six steps from solid to `MIN_OPACITY` (25%), so a game
underneath shows through.

- **On the layout, not on the `LayoutStyle`** — it means the same in both modes, and a copy on each
  would be silently lost every time someone tried a pack and came back.
- **One layer for the whole pad**, via `withOpacity`, not an alpha per colour: per-colour alpha lets a
  face plate show through the stick drawn over it. The layer is skipped at full opacity, since it
  costs an offscreen buffer per frame.
- **The floor is not zero** — a pad faded to nothing is a blank screen that still takes touches.
- **The editor fades the pad, not its own furniture.** Grid, selection ring and handles stay solid;
  a faint pad is exactly when they are most needed.

**Themes** are `PadThemes.ALL`, six pairs offered above the picker in colours mode. The picker can
make any colour at all, which is the problem — a resting fill and a pressed fill that obviously is
not it take a few goes to find. Tapping one applies it and stays on the page. Each row is swatched in
its *pressed* colour, the one that tells them apart.

### The saved format

`LayoutJson` writes `{"version": 1, "layouts": [...]}` — always a list, so saving the library and
sharing one layout are the same shape with one version number to reason about.

```json
{
  "version": 1,
  "layouts": [
    {
      "id": "8f3c…", "name": "My pad",
      "controls": [
        { "id": { "type": "button", "button": "WEST" },
          "shape": { "type": "circle", "centerX": 0.87, "centerY": 0.295, "radius": 0.072 },
          "label": "Y", "triggerMode": "PROGRESSIVE" }
      ],
      "style": { "type": "colors", "resting": "#FF262B36", "pressed": "#FF4C82F7" },
      "opacity": 1.0
    }
  ]
}
```

- **The names in there are a compatibility surface.** Every `@SerialName`, `GamepadButton`,
  `ControlId.Side` and `ArtPack.id`: a layout on someone's phone names them, so renaming one silently
  repoints it. `LayoutSerializationTest` pins the lot against a golden file.
- **Every polymorphic variant names its own discriminator** — left to itself kotlinx writes the
  fully-qualified Kotlin name, and moving a file to another package would orphan every saved layout.
- **An image layout writes one pack id**, not a picture per control. A pack that is not installed
  throws rather than degrading: decoding is all-or-nothing, because a layout arriving as a
  different-looking pad is the worse failure.
- **Colours are `#AARRGGBB`.** As numbers they would be large negative integers. Reading is lenient
  the two ways someone hand-editing would expect: the `#` is optional and six digits mean opaque.
- **New fields are defaulted rather than versioned.** `triggerMode`, `stickMode`, `areaWidth`,
  `areaHeight` and `opacity` all came in this way: an older file comes back as the pad it was, a
  newer file loads on an older build (`ignoreUnknownKeys`), and no migration is needed. A default is
  a behaviour, not a compatibility promise — `encodeDefaults` puts every key on every control, so
  changing one reaches only hand-written and pre-setting files. Opacity is clamped when read into a
  `PadStyle` rather than refused when parsed: a hand-edited `0` is a mistake to correct.
- **An edit is drawn from the draft it produced, not from the store reading it back.** A drag writes
  per frame and a write is a round trip, so `MainActivity` holds the newest edit in a `draft` state
  and drops it once the store agrees. Writes go through one `MutableStateFlow` with a single
  collector — separate coroutines reach DataStore in scheduling order, so an older frame could land
  last and snap a control back to a size it had already left. A `StateFlow` also conflates the frames
  that pile up during a save.

---

## Latency

`LatencyProbe` measures **the part of input lag the app controls**, logged every ten seconds while
connected and once more when the connection ends:

```
I Blugaemand: latency: 412 reports, waiting 0.31/1.94/9.87 ms, sending 0.12/0.4/2.1 ms (median/p95/worst)
```

**Those numbers are made up** — the line shows the shape of the output. Nothing has been measured on
hardware yet.

- **waiting** — from a state change being recorded to a send being attempted. What the rate cap
  costs, and the number `MIN_SEND_GAP_MS` should be argued about with.
- **sending** — how long `sendReport` itself took. Measured separately so a pump tuned against what
  is really L2CAP back-pressure is not tuned against the wrong thing.

Percentiles are nearest-rank over a rolling window of 1024 reports: a session-long mean would be
dominated by whatever the pad was doing an hour ago, and an interpolated percentile invents a value
between two real samples.

**What it does not measure** is everything past `sendReport` — radio, host stack, HID driver, the
game's polling. Measuring those needs the phone and a host in the same room:

1. **Host-side arrival.** `evtest /dev/input/eventN` timestamps every event. Clocks are not
   synchronised, so the useful figure is the *distribution* of gaps over a few hundred presses, which
   is where retransmissions show up.
2. **Glass to pixel.** A 240 fps phone camera (±4 ms) on the pad and the host's screen together,
   counting frames. Crude, and the only end-to-end number.
3. **Then revisit the cap.** Whether 100 Hz is the right ceiling at all — Bluetooth Classic HID with
   a 7.5 ms interval cannot deliver much more, and a lower cap may cost nothing measurable while
   saving power.

---

## Using it

### Pairing

1. Launch the app and grant the nearby-devices permission. The left pill should read **Ready to
   pair**. If it says *Not supported on this phone*, stop — see Troubleshooting.
2. **Hold** the pill for ~600 ms (a blue bar sweeps across it), then **Make discoverable to pair**.
3. On the host, add a new Bluetooth device and pick the phone.
4. Accept the pairing code on both ends. The host opens the HID connection and the pill turns green.

Two gotchas account for most first-time failures:

- **Register before pairing.** What tells the host this is a gamepad is the **HID service record**,
  and the host caches the service list at pair time. Not the class of device, despite the obvious
  guess: that belongs to the adapter and stays *Phone* whatever the app does — `subclass` only
  reaches SDP attribute `0x0202`. Measured while registered and advertising.
- **A phone previously paired as a phone must be removed first**, for the same caching reason.

**Verifying on Linux** — the most informative host, because the kernel exposes what it parsed:

```bash
grep -A9 'Name="<phone>"' /proc/bus/input/devices   # capability bitmaps
ls /dev/input/js*                                   # joystick node appears
hexdump -v -e '/1 "%02X "' /sys/bus/hid/devices/<id>/report_descriptor
dmesg | grep -i "BLUETOOTH HID"
```

A correct enumeration reports 8 axes and 16 buttons, with `ABS_X/Y`, `ABS_Z/RZ`, `ABS_GAS`,
`ABS_BRAKE`, `ABS_HAT0X/Y`, and buttons on `BTN_SOUTH`…`BTN_THUMBR`. At rest the sticks read `0`,
the triggers `-32767`, and **the hat `0`** — a hat resting at `-32767` means the null-state flag was
lost.

**Verifying on Windows** — `Win+R` → `joy.cpl` → Properties gives a live axis and button panel that
reads the descriptor directly. Steam's controller configuration is a stricter check still.

### Making a layout

The built-ins cannot be changed — make your own instead.

1. **Hold** the ☰ Menu pill, then **Layouts → New layout** → **Empty** or **Copy of** whichever
   layout is showing. Either is selected and opens the editor straight away.
2. The **☰ Layout** pill opens and closes, so the pad underneath stays reachable. It holds what
   applies to the layout; what applies to the selected control sits as pills beside it in the head
   bar, reachable with the menu shut.
3. **Drag** a control to move it, **pinch** to resize, or drag one of the **eight arrows** around the
   selection. Each holds the edge it is on until it reaches the screen; corners keep the proportions,
   edges stretch that side alone. Anything drawn as a circle has one size, so its side arrows grow it
   whole. Nothing has a maximum size. **Grid** toggles snapping, for sizes as well as positions. The
   **◀▲▼▶ arrows** move the selection a whole grid cell with snapping on, a fifth of one with it off.
4. **Add control**, then **tap the pad where it should go** — a preview follows your finger. Any
   control can be added more than once. **Add control group** does the same for the face diamond, a
   four-button D-pad, the centre three, or a shoulder pair side by side or stacked.
5. **As one control**, on the group page, is **on**: the arrangement lands as a *single* control that
   moves, resizes and deletes as one, keeps its shape on any screen, and lets a thumb roll between
   buttons without lifting. **Ungroup** breaks it back apart; turning the switch off before placing
   does the same up front.
6. **✕ Remove** takes out the selection. A caption names any button left with no control — a warning,
   not an error.
7. **Trigger**, a head-bar pill, appears while a trigger (or a plate with one) is selected and
   switches it between **binary** and **progressive**. Each trigger is set on its own.
8. **Stick**, likewise, switches between **fixed** — resize it to change its throw — and **dynamic**,
   an area the stick appears in under your thumb. Resizing a dynamic stick changes the *area*, so to
   change its throw switch to fixed, resize, and switch back. Anything placed inside the area still
   works normally.
9. **Appearance** picks *Shapes and colours* or one of the seven art packs. In colours mode, tap *At
   rest* or *Held* to say which fill the picker adjusts; **Themes** above it is six ready-made pairs.
   Going back to shapes returns the colours you had.
10. **Opacity**, the first row on *Appearance*, fades the whole pad in six steps. The editor's grid,
   ring and handles stay solid however faint the pad is.
11. **✓ Done**, the green pill, goes back to the pad. Everything is saved as you go; **Delete
   layout**, the one row in red, asks first, because there is no undo. Every sub-page opens with
   **‹ Back**.

To reach the editor later, select the layout in the menu — *Edit layout* appears for anything you
made. Motion aiming is not part of a layout: it is on **☰ Menu → Motion**. The app's name, icon and
version are on **☰ Menu → About**.

### Troubleshooting

| Symptom | Cause |
|---|---|
| *Not supported on this phone* | Some manufacturer builds ship without the HID Device profile. Check with `adb shell getprop bluetooth.profile.hid.device.enabled`. No workaround; the app detects it rather than failing silently. |
| *Permission needed* | The nearby-devices permission is not granted. The panel offers **Grant permission**. |
| *Bluetooth is off* | Apps cannot enable the adapter themselves — `BluetoothAdapter.enable()` has been a no-op since Android 13 — so the panel raises the system prompt. |
| Pairs but never connects | Almost always one of the two pairing gotchas above. Remove the pairing on the host and redo it with the app already showing **Ready to pair**. |
| Buttons stick down after switching apps | Should not happen — the pad sends a neutral report on focus loss and on opening a panel. If you see it, it is a bug. |
| Changed the descriptor but the host sees the old one | Hosts cache the SDP record at pair time. Remove the pairing on **both** ends and pair again; on Linux `bluetoothctl remove <mac>` clears BlueZ's cache. |
| My layouts vanished after an update | Unparseable JSON is reported as an empty library but **not** overwritten — `adb logcat -s Blugaemand` will say so, and the file is still under `files/datastore/`. |
| *Edit layout* is not in the menu | The selected layout is a built-in. **Layouts → New layout → Copy of…** gives you an editable one. |
| `unknown main item tag 0x0` in `dmesg` | Expected and harmless. One `0x00` is appended to the descriptor in transit; see *Known constraints* in TODO.md. |
