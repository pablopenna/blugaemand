# Blugaemand

Turns an Android phone into a real Bluetooth gamepad. The host sees a standard HID controller — no
driver, no companion app, no root.

**Current state:** an Xbox-style pad (two sticks, D-pad, ABXY, four shoulder controls, the three
centre buttons, two stick clicks), offered in three presentations — **Default**, drawn as shapes and
labels, and **Xbox** and **PS5**, drawn with console button art. **You can also make your own**,
from empty or as a copy of one of those, and move, resize, add and remove controls on it; layouts
and the choice of one are saved between launches. Verified end-to-end against a **Linux** host;
Windows is the stated target but is **not yet tested**. Two pills sit at the top edge, each opening
its panel on a 600 ms hold: the left one is connection status and pairing, the right one
(**☰ Menu**) picks or creates a layout, opens the editor, and quits.

**[TODO.md](TODO.md) is the live backlog** — what is done, what is next, and a *Known constraints*
section recording things that are permanently impossible so they do not get rediscovered. Read it
before planning any work here, and keep it updated as things land.

---

## How it works

Android has a first-party API for this: **`BluetoothHidDevice`** (API 28+,
`BluetoothProfile.HID_DEVICE`). The app registers an SDP record containing a raw HID report
descriptor, and then pushes input reports over **Bluetooth Classic HID**. No third-party library is
involved.

**Why not BLE?** The Bluetooth Low Energy equivalent is HID-over-GATT (HOGP), and it is a dead end
on Android: apps are blocked from registering the HID service UUID (`0x1812`) on the platform's
GATT server. Classic HID is the only route, and it is what almost every real controller uses
anyway.

This is what forces `minSdk 28` (Android 9).

### What the host sees

Report ID 1, nine bytes:

| Byte | Contents |
|-----:|----------|
| 0–1 | Left stick X, Y — `0..255`, centred at `128` |
| 2–3 | Right stick X, Y — `0..255`, centred at `128` |
| 4–5 | Left / right trigger — `0..255`, as Brake and Accelerator |
| 6 | D-pad hat in the low nibble (`0`=N clockwise to `7`=NW, `8`=centred) |
| 7–8 | Sixteen buttons |

Three decisions in there are worth knowing about, all in
[`GenericHidProfile.kt`](app/src/main/java/com/blugaemand/hid/GenericHidProfile.kt):

- **Triggers live on the Simulation Controls page** (`Brake` / `Accelerator`) rather than as generic
  axes. That is the convention DualShock and Xbox Bluetooth controllers follow, and it is what makes
  hosts map them to trigger axes without per-device configuration. Confirmed on Linux: they arrive
  as `ABS_BRAKE` / `ABS_GAS`.
- **Button numbering is not sequential** — it follows the order Linux's `hid-input` driver assigns
  to a gamepad collection (`BTN_SOUTH`, `BTN_EAST`, `BTN_C`, `BTN_NORTH`, …), which Android
  inherits. Windows numbers buttons positionally and does not care, so matching the convention costs
  nothing and means Linux and Android hosts label buttons correctly for free.
- **Face buttons are named by position** — `SOUTH`, `EAST`, `NORTH`, `WEST` — because the letters
  are not portable: Xbox puts Y north and X west, Nintendo swaps both pairs, and HID's legacy
  aliases (`BTN_X` for `BTN_NORTH`, `BTN_Y` for `BTN_WEST`) are a third arrangement again. Hosts
  report the aliases, so `DEFAULT_LAYOUT` puts its Y key on `WEST` and its X key on `NORTH`.
  **Anything keyed off a face button follows the label, not the slot** — that is why `XBOX_ART`
  pairs `GamepadButton.WEST` with the *Y* picture, and `PLAYSTATION_ART` pairs it with the
  triangle that sits in the same place on a DualSense.
- **The hat carries a null-state flag**, which is what lets a value above the logical maximum mean
  "centred". Without it the D-pad rests stuck pointing north.

### Known limitation: DirectInput, not XInput

On Windows the phone will enumerate as a **DirectInput** gamepad. It will **not** appear as an
XInput ("Xbox controller") device, because XInput is a kernel-driver interface that Bluetooth HID
cannot reach — no app can work around this. In practice:

- ✅ Steam, emulators, most game engines, anything reading raw HID or DirectInput
- ❌ The minority of games that speak only XInput

Steam's controller support will happily remap it if a game is XInput-only.

---

## Architecture

One idea shapes the whole codebase: **the parts that are hard to get right have no Android
dependencies**, so they can be tested on the JVM in milliseconds instead of on a phone paired to a
host. Only the service and the Compose layer touch the framework.

### Data flow

```
  finger on glass
        │
        ▼
  GamepadScreen ──── pointer events (Initial pass, all changes)
        │
        ▼
  TouchRouter ────── pointerId → control binding, hit-testing against ResolvedLayout
        │
        ▼
  GamepadState ───── immutable snapshot: 6 axes + hat + 16-bit button mask
        │
        ▼
  HidGamepadService  coalesces to ~100 Hz, drops unchanged reports
        │
        ▼
  GamepadProfile.encode() ── 9 raw bytes
        │
        ▼
  BluetoothHidDevice.sendReport()  →  host
```

### Modules

| Package | Android-free | Contents |
|---|---|---|
| `hid/` | mostly | `GamepadState`, `GamepadProfile`, `GenericHidProfile` are pure Kotlin; `HidGamepadService` is not |
| `input/` | yes | `ControlSpec`, `ControlIcon`, `ArtPack`, `GamepadLayout`, `LayoutStyle`, `LayoutJson`, `LayoutLibrary`, `LayoutEdits`, `ResolvedLayout`, `TouchRouter`, `art/`, `layouts/` |
| `data/` | no | `LayoutStore` — the only file outside `hid/` and `ui/` that touches Android |
| `ui/` | no | `GamepadScreen`, `EditorScreen`, `ControlRenderers`, `PadStyle`, `ControlIcons`, `TopBar`, `TopBarChrome`, `ConnectionBar`, `MenuBar`, `EditorBar`, `theme/` |

**`hid/`**

- `GamepadState` — immutable snapshot plus the `GamepadButton` enum that fixes HID button numbering.
- `GamepadProfile` — the host-compatibility seam. SDP metadata, report descriptor, `encode()`, and
  an optional `requiredAdapterName` hook.
- `GenericHidProfile` — the only implementation today. The descriptor is written as commented raw
  bytes; treat it as the most safety-critical file in the repo.
- `HidStatus` — sealed UI state. `PermissionRequired` and `BluetoothOff` are deliberately separate,
  because they need different things from the user.
- `HidGamepadService` — foreground service owning the profile proxy, SDP registration, the
  `BluetoothHidDevice.Callback`, and the send loop.

**`input/`**

- `ControlSpec` / `GamepadLayout` — a layout as plain data in normalised coordinates: a
  `List<ControlSpec>` plus a `LayoutStyle`.
- `LayoutStyle` — which of the two presentations a layout uses, `Colors` or `Images`. A layout is in
  exactly one; see *Two presentations* below.
- `ControlIcon` — one picture from an art pack, as an enum of names. Deliberately not a drawable
  resource ID: those are reassigned every build, so a saved layout holding one would come back
  pointing at a different picture. That was a hypothetical when it was written and is now load-
  bearing. A name identifies a *picture*, not a role — naming roles instead
  (a `FACE_TOP` resolved against whichever pack is in play) would drop the per-platform prefixes and
  cost the ability to name one specific picture, which a layout mixing packs and the planned editor
  both need.
- `ArtPack` — `ControlId → Glyph` (an idle picture and an optional held one), which is how a layout
  in `Images` mode gets from a control to something to draw. Named once on the style rather than
  written onto every `ControlSpec`, so a layout stays geometry and the pack stays swappable. A pack
  need not be complete; see *Two presentations*.
- `art/` — the built-in packs, `XBOX_ART` and `PLAYSTATION_ART`. One file each, and the one place
  each console's face-button crossing is decided.
- `layouts/` — one file per built-in, plus `Layouts.ALL`, the catalog the menu lists. `XBOX_LAYOUT`
  and `PS5_LAYOUT` derive their geometry from `DEFAULT_LAYOUT` rather than copying it, so tuning a
  position moves all three; what is left in each file is which pack to draw with. `PS5_LAYOUT`
  restates the left cluster on top of that — see *Two presentations*. **The derivation happens at
  class-load, so it does not extend to user layouts:** a copy of Default that is then edited moves
  nothing but itself, which is what you want but is the opposite of what "moves all three" suggests.
- `ResolvedLayout` — converts a layout to pixels once per size change. The renderer, hit-testing
  *and the editor* all read from it, so what is drawn is exactly what is touchable and exactly what
  a drag moves. Untouched by the two modes: presentation never changes where a touch lands.
- `TouchRouter` — owns `pointerId → control` bindings and produces a `GamepadState`.
- `LayoutJson` — the saved format, and the two small serialisers it needs. See *Layouts* below.
- `LayoutLibrary` — the built-ins plus the user's own. Whether a layout can be edited is a fact
  about where it came from, not about the layout, so `GamepadLayout` carries no flag saying so;
  `isEditable` is the single place that line is drawn.
- `LayoutEdits` — every edit the editor makes, as arithmetic on plain data. This is where the
  editor is actually tested.

**`data/`**

- `LayoutStore` — a Preferences DataStore holding two keys: the user's layouts as JSON, and the id
  of the selected one. Preferences rather than a typed `DataStore<T>` because the payload is already
  one string. Stored JSON that will not parse is reported as an empty library and **left where it
  is**: it is still the only copy of work someone did, and overwriting it on the next save would
  destroy it with no way back.

**`ui/`**

- `GamepadScreen` takes the layout as a parameter and never reads a global. That is what lets the
  menu switch layouts by handing it a different instance, and it is the same trick the editor uses.
- `EditorScreen` — the pad with its wiring pulled out: same controls in the same places, but a
  finger moves one instead of pressing it. A screen of its own rather than a mode on `GamepadScreen`
  because the two share no input at all; the reuse is `drawControl`, one level down, which is where
  it belongs — the editor has to draw a control exactly as the pad will or it is not showing you
  what you are making.
- `PadStyle` — a `LayoutStyle` resolved for drawing: ARGB `Int`s become Compose `Color`s, and glyph
  names become `Painter`s. It exists because `painterResource` is a composable while the pad draws
  inside a `Canvas` lambda, which is not composition — painters have to be resolved up front.
- `ControlIcons` — `ControlIcon → R.drawable`, the only file in the app that mentions `R.drawable`.
- `TopBar` — the two pills pinned to the top edge and whichever panel is open below them. Panel
  state is one nullable `TopPanel`, not a boolean each, so "only one open at a time" is structural.
  The pills share a `Row` and the panels hang beneath it: side-by-side pill-and-panel columns would
  change width as a panel opened and slide the pills sideways every time.
- `TopBarChrome` — `HoldPill`, `PanelCard`, `PanelEntry` and `PanelCaption`: the shape, the rows and
  the gesture every pill and panel is built from. A second pill that reimplemented the hold would
  drift from the first; see the trap below for why that gesture is not worth writing twice, and the
  same goes for the rows now that two panels are lists of them.
- `ConnectionBar` — `ConnectionPill` and `ConnectionPanel`: status, pairing and reconnection.
- `MenuBar` — `MenuPill` and `MenuPanel`: picking a layout, making one, editing, and quitting. The
  panel's page state lives inside the composable, which is only composed while open, so the menu
  reopens on its root page without a reset that would visibly flip pages mid-close.
- `EditorBar` — the editor's own panel. Deliberately not a `HoldPill`: the hold exists so a stray
  tap cannot throw a panel up mid-game, and nothing being edited is connected to anything.

### Two presentations

A layout declares how it is drawn, and is in exactly one mode — the two are alternatives, not
layers, so nothing has to decide what a glyph on a coloured plate would mean.

| | `LayoutStyle.Colors` | `LayoutStyle.Images` |
|---|---|---|
| Built-in | **Default** | **Xbox**, **PS5** |
| Controls | drawn shapes, text labels | a picture per control, from an `ArtPack` |
| Colours | two ARGB values on the layout: resting and pressed | none — the art carries its own |
| Pressed | the fill changes | a second picture swaps in, if the pack has one |

Consequences worth knowing:

- **Geometry and hit-testing are shared.** `ResolvedLayout` and `TouchRouter` know nothing about
  either mode. In `Images` mode a wide control like a trigger keeps its full touch area while its
  glyph is drawn square and smaller — easier to hit than it looks, which is the right way round.
- **A pack is not only pictures.** `PS5_LAYOUT` also moves its left cluster: the D-pad goes up
  opposite the face diamond and the left stick drops to match the right, because that is where a
  DualSense puts them, and PlayStation symbols in Xbox positions fight the muscle memory that comes
  with them. The D-pad is much larger there too — it is the one control a thumb sweeps across
  rather than lands on, and it inherits the room the stick gives up. Its height is what pays for
  that size: a hair above the diamond's centre rather than level with it, which is the last place
  it can sit before its touch square meets the stick's. Everything else still derives from
  `DEFAULT_LAYOUT`.
- **Thumbsticks stay drawn in both modes.** No static picture can show a knob displaced from centre,
  so an art pack's picture of a stick would be a picture of a control that no longer moves.
- **A control with one picture does not animate.** `Glyph.pressed` is optional and the renderer
  falls back to the idle one, so a pack that ships only one state degrades quietly rather than
  flickering. `Glyph.idle` is not optional, which makes a pressed-only control unrepresentable
  rather than something to test for.
- **Anything the pack does not name falls back to its shape and label**, which is what stops a gap
  in a pack rendering a hole. The live example is the PS button: Kenney draws the Xbox logo but not
  Sony's, so `PS5_LAYOUT` leaves `GUIDE` out of the pack and relabels it *PS*. Lending it the mute
  or touchpad glyph would put a different button's picture on the one that sends `GUIDE`.
- Colours are plain ARGB `Int`s in `input/`, not Compose `Color`s. That is what keeps the package
  free of `androidx` imports — the thing that lets its tests run on the JVM and will let
  `GamepadLayout` serialise without a custom serialiser. `PadStyle` converts at the `ui/` boundary.

Only the two fills that change with press state belong to the layout. Strokes, labels, the stick
well and the canvas stay in `PadColors`: they are the pad's chrome rather than the layout's
identity, and a layout free to recolour its strokes is a layout free to make itself invisible.

### Layouts, built-in and user-made

**The built-ins are read-only.** `DEFAULT_LAYOUT`, `XBOX_LAYOUT` and `PS5_LAYOUT` are `val`s in the
source; you make your own from empty or as a copy, and edit that. `LayoutLibrary.isEditable` is the
only place that distinction is drawn, which is what keeps it from having to be remembered in every
screen — the menu offers *Edit layout* only when it is true, so there is no disabled row to explain.

A copy takes a fresh UUID rather than anything derived from its source, so a layout imported from
someone else can never land on top of one already here. It keeps everything else, art pack included:
a copy of the PS5 pad is a PlayStation-looking pad you can then rearrange.

**Editing is four operations** — move, resize, add, remove — plus the two colours and a rename. All
of the arithmetic is in `LayoutEdits`, which is plain Kotlin, so the editor is tested on the JVM and
`EditorScreen` is nothing but gestures. Three decisions in there are easy to undo by accident:

- **The grid is in pixels, off the layout unit.** Defined in normalised coordinates it would not be
  square — x divides by width and y by height, so on a 16:9 screen the cells would be nearly twice
  as wide as tall, and two controls both "on the grid" would not line up with each other. Sizes snap
  against the same pixel grid, which is why `Rect.width` — a fraction of *screen width*, where every
  other size is a fraction of the unit — is converted through its own reference rather than clamped
  and snapped in the wrong units.
- **Deltas accumulate across a gesture** and apply to the geometry as it was when the finger went
  down, not to the result of the previous frame. The other way round, with snapping on, a drag
  slower than half a grid step per frame rounds back to where it started every frame and the control
  never moves at all.
- **A move is clamped so the control stays wholly on screen**, not merely so its centre stays in
  `0..1`. Half a button hanging over the edge cannot be touched, and reads as a bug.

**A layout added by the editor lands where `DEFAULT_LAYOUT` has it**, size and label included, so
building an empty layout up one control at a time reconstructs the default pad instead of piling
everything in the middle. `L2` and `R2` are the only two of `ControlId.ALL` the default does not
place — it reaches the triggers through `ControlId.Trigger` — and they are the only two with a
fallback spec, derived rather than listed so that a control with no home fails at class-load.

User layouts are deliberately allowed to be **incomplete, empty or overlapping**. The layout-sanity
tests apply to `Layouts.ALL` only; `missingButtons()` is surfaced in the editor as a caption, which
is a warning and not an error. A pad with no Start button is a strange pad, but it is allowed to be
one.

### The saved format

`LayoutJson` writes `{"version": 1, "layouts": [...]}` — always a list, so saving the whole library
and sharing a single layout are the same shape and there is one version number to reason about.
`data/LayoutStore` keeps that string in a Preferences DataStore.

```json
{
  "version": 1,
  "layouts": [
    {
      "id": "8f3c…", "name": "My pad",
      "controls": [
        { "id": { "type": "button", "button": "WEST" },
          "shape": { "type": "circle", "centerX": 0.87, "centerY": 0.295, "radius": 0.072 },
          "label": "Y" }
      ],
      "style": { "type": "colors", "resting": "#FF262B36", "pressed": "#FF4C82F7" }
    }
  ]
}
```

- **The names in there are a compatibility surface, not an implementation detail.** Every
  `@SerialName`, every `GamepadButton` and `ControlId.Side` entry, every `ArtPack.id`: a layout on
  someone's phone names them. Renaming one silently repoints it. `LayoutSerializationTest` pins the
  lot against a golden file so that fails a test instead.
- Every polymorphic variant therefore names its own discriminator. Left to itself kotlinx writes the
  fully-qualified Kotlin name, and moving a file to another package would orphan every saved layout.
- **An image layout writes one pack id**, not a picture per control — otherwise a saved layout could
  disagree with the pack it claims to use. A pack that is not installed throws rather than degrading
  to colours mode: decoding is all-or-nothing, because a layout arriving as a different-looking pad
  than the one that was shared is the worse failure.
- **Colours are `#AARRGGBB`.** As numbers they would be large negative integers — a full alpha byte
  makes the `Int` negative — which is no use in a file meant to be shared and hand-edited. Reading
  is lenient in the two ways someone editing one by hand would expect: the `#` is optional, and six
  digits mean opaque.
- `encodeDefaults` is on, so a layout keeps the values it was saved with even if a default later
  moves. `ignoreUnknownKeys` is on too, so a field added by a newer build does not stop an older one
  reading the rest.

### The two seams

Everything on the roadmap goes through one of these:

- **`GamepadProfile`** — supporting a fussier host (the Switch) means adding an implementation, not
  threading special cases through the service.
- **`GamepadLayout`** — it needed no changes to become the saved format, because a user layout is a
  variable-length list of controls under a name and that is what it always was. The one change still
  queued is user-supplied art, which turns `ControlIcon` into a sealed `Builtin | File`; the version
  in the saved file is there for exactly that.

### Design decisions that are easy to undo by accident

- **Reports are coalesced, not sent per touch event.** Sending from the touch handler would put
  hundreds of reports per second on the L2CAP interrupt channel and show up as lag, not
  responsiveness. The send loop runs at 100 Hz on a dedicated max-priority thread and skips reports
  identical to the last one.
- **A pointer stays bound to the control it went down on** until release, even if it strays. Real
  gamepads behave that way, and it stops a stick being stolen mid-swing.
- **Sizes scale from `ResolvedLayout.unit`, not screen height.** `unit` is
  `min(height, width × 9/16)`. Sizing off height alone makes controls bloat on squarer screens while
  width-relative spacing does not, and neighbouring buttons collide at 4:3. A unit test enforces
  no overlaps at two aspect ratios.
- **`axisFromUnit` scales by 128 and clamps**, not by 127. A 0..255 range centred at 128 is not
  symmetric; scaling by 127 leaves full-left reading `1`, and a stick that never reaches its stop is
  worse than saturating the top 1/128th of travel.
- **The service binds before it starts.** A `connectedDevice` foreground service requires a
  Bluetooth runtime permission to be *granted*, not merely declared. The activity binds first (so
  the UI can show status with nothing granted) and only calls `startForegroundService` once the
  permission is held.

---

## Building

Requires JDK 17 and an Android SDK with platform 36. Point `local.properties` at your SDK:

```properties
sdk.dir=/path/to/android-sdk
```

```bash
./gradlew assembleDebug     # build
./gradlew test              # JVM unit tests, no device needed
./gradlew lintDebug         # lint — kept at zero issues
./gradlew installDebug      # install onto a connected phone
```

Dependency versions are pinned deliberately (AGP 8.13.2, Kotlin 2.0.21, Compose BOM 2024.09.00,
kotlinx.serialization 1.7.3, DataStore 1.1.1). Lint's "newer version available" family is disabled
in `app/build.gradle.kts` because it is pure noise here.

### Tests

`app/src/test/` covers the encoder, descriptor structure, touch routing, the saved format and every
edit the layout editor makes — all on the JVM:

- `GenericHidProfileTest` — exact report bytes, per-button bit positions, every hat direction, axis
  clamping, and a walk of the descriptor's item stream verifying it is structurally sound and
  declares exactly 9 bytes.
- `TouchRouterTest` — pointer binding and release, multitouch independence, stick normalisation and
  circular clamping, D-pad sectors, and layout sanity (no overlaps, every button reachable, unique
  ids). The sanity tests run over `Layouts.ALL`, so a new built-in is covered by adding it — and
  only over `Layouts.ALL`, since a user layout is entitled to be empty or to overlap.
- `LayoutArtTest` — invariants for layouts drawn with art: the pack covers every control bar a
  declared list of exceptions (the sticks, and the PS button), anything falling back to its shape
  still has a label to draw, and the face buttons show the picture for the position they are
  *labelled* for rather than the slot they drive. That last one is the guard on the X/Y crossing,
  and it now covers both packs — Xbox's Y and PlayStation's triangle both sit on `WEST`. Two
  invariants the earlier version tested are gone because the types made them unrepresentable: a
  pressed-only control (`Glyph.idle` is non-null) and a colours-mode layout carrying dead glyphs
  (only `Images` holds a pack). Nothing checks that a `ControlIcon` resolves to a real drawable —
  the mapping is an exhaustive `when` naming `R.drawable` constants, so both halves are already
  compile errors.
- `LayoutSerializationTest` — round trips, leniency and refusals for the saved format, and the
  **golden file**: one layout covering all four shapes, all four control kinds and both styles,
  compared literally. It is the guard on every name the format is made of, so a `@SerialName` or an
  enum entry cannot be renamed without a failing test. If it fails for a change that is genuinely
  wanted, the fix is a format version and a migration in `decodeLayouts`, not a new expected string.
- `LayoutLibraryTest` — the built-in/user line: no built-in is editable, `without` cannot reach one,
  saving replaces in place and keeps its position rather than shuffling the menu under a thumb.
- `LayoutEditsTest` — everything a drag, a pinch, an add and a remove do, on a 1000×500 surface that
  makes the layout unit exactly 500 and the grid step exactly 25. The round-trip test (move by
  `(dx, dy)`, then by `(-dx, -dy)`) is the one that catches an axis divided by the wrong dimension.

---

## Pairing

1. Launch the app and grant the nearby-devices permission. The status pill — the left of the two at
   the top — should read **Ready to pair**. If it says *Not supported on this phone*, stop — see
   Troubleshooting.
2. **Hold** the pill for ~600 ms — a blue bar sweeps across it — then **Make discoverable to pair**.
3. On the host, add a new Bluetooth device and pick the phone.
4. Accept the pairing code on both ends. The host then initiates the HID connection and the pill
   turns green.

Two gotchas that account for most first-time failures:

- **Register before pairing.** The class-of-device recorded at pair time is what tells the host this
  is a gamepad. If the app was not registered, the host files the phone as a phone and never opens
  the HID connection.
- **A phone previously paired as a phone must be removed first**, because the host caches the old
  service list.

### Verifying on Linux

The most informative host, because the kernel exposes exactly what it parsed:

```bash
grep -A9 'Name="<phone>"' /proc/bus/input/devices   # capability bitmaps
ls /dev/input/js*                                    # joystick node appears
hexdump -v -e '/1 "%02X "' /sys/bus/hid/devices/<id>/report_descriptor
dmesg | grep -i "BLUETOOTH HID"
```

A correct enumeration reports 8 axes and 16 buttons, with `ABS_X/Y`, `ABS_Z/RZ`, `ABS_GAS`,
`ABS_BRAKE`, `ABS_HAT0X/Y`, and buttons on `BTN_SOUTH`…`BTN_THUMBR`. At rest the sticks read `0`,
the triggers `-32767`, and **the hat `0`** — a hat resting at `-32767` means the null-state flag was
lost.

### Verifying on Windows

`Win+R` → `joy.cpl` → the device appears under Game Controllers. **Properties** gives a live axis
and button panel that reads the descriptor directly. Steam's controller configuration is a stricter
check still.

---

## Making a layout

The three built-ins cannot be changed — make your own instead:

1. **Hold** the ☰ Menu pill, then **Layouts → New layout**, and pick either **Empty** or **Copy of**
   whichever layout is showing. Either one is selected and opens the editor straight away.
2. **Drag** a control to move it, **pinch** it to resize. **Grid** toggles snapping, which applies to
   sizes as well as positions, so two buttons meant to match can be made to match.
3. **Add control** places anything not already on the pad, where the default layout has it.
   **Remove** takes out whatever is selected. A caption names any button left with no control — a
   warning, not an error.
4. **Colours** picks the resting and held fills, on a colours-mode layout. A layout copied from Xbox
   or PS5 draws its art's own, so it has none to pick.
5. **Done** goes back to the pad. Everything is saved as you go; **Delete layout** asks first,
   because there is no undo.

To reach the editor again later, select the layout in the menu — *Edit layout* appears on the root
page for anything you made.

---

## Troubleshooting

| Symptom | Cause |
|---|---|
| *Not supported on this phone* | Some manufacturer builds ship without the HID Device profile. Check with `adb shell getprop bluetooth.profile.hid.device.enabled`. No workaround; the app detects it rather than failing silently. |
| *Permission needed* | The nearby-devices permission is not granted. The panel offers **Grant permission**. |
| *Bluetooth is off* | The adapter is disabled. Apps cannot enable it themselves — `BluetoothAdapter.enable()` has been a no-op for ordinary apps since Android 13 — so the panel offers **Turn on Bluetooth**, which raises the system prompt. |
| Pairs but never connects | Almost always one of the two pairing gotchas above. Remove the pairing on the host and redo it with the app already showing **Ready to pair**. |
| Buttons stick down after switching apps | Should not happen — the pad sends a neutral report on focus loss and on opening the panel. If you see it, it is a bug. |
| Changed the HID descriptor but the host still sees the old one | Hosts cache the SDP record at pair time, and reconnecting does not refresh it. Remove the pairing on **both** ends and pair again. On Linux, `bluetoothctl remove <mac>` clears BlueZ's cache in `/var/lib/bluetooth/<adapter>/cache/`. |
| My layouts vanished after an update | Stored JSON the app cannot parse is reported as an empty library, but is **not** overwritten — `adb logcat -s Blugaemand` will say so. The file is still there under `files/datastore/`, so a fixed build can still read it. |
| *Edit layout* is not in the menu | The selected layout is a built-in, and those are read-only. **Layouts → New layout → Copy of…** gives you an editable one. |
| `unknown main item tag 0x0` in `dmesg` | Expected and harmless. One `0x00` is appended to the descriptor in transit regardless of its length; see *Known constraints* in TODO.md. |

To watch the app's own state transitions:

```bash
adb logcat -s Blugaemand
```

---

## Working on this

Context worth having before starting, especially for a fresh session.

**A physical Android 9+ phone is required for anything Bluetooth.** Emulators have no usable
Bluetooth radio. The encoder, descriptor and touch routing are deliberately Android-free so most
logic can be verified with `./gradlew test` instead.

**The development phone is a Xiaomi (HyperOS), which gates three adb capabilities behind Developer
options rather than removing them.** All three have been observed failing, and all three work with
the right toggles on — so a failure here is a setting, not a dead end:

| Needs | Symptom when off |
|---|---|
| *Install via USB* | `INSTALL_FAILED_USER_RESTRICTED`. `pm install` from the shell does not get around it. |
| *USB debugging (Security settings)* | `pm grant` throws a `SecurityException`, and `input tap` / `input swipe` are denied `INJECT_EVENTS`. This is the toggle that decides whether the UI can be driven from a script at all. |
| A signed-in Mi account | HyperOS asks for one before it will let the two above be enabled. |

**With input injection on, the whole UI is scriptable**, which is worth setting up before any work on
the pad or the editor — `adb shell input tap x y`, `input swipe x1 y1 x2 y2 ms` for drags and holds,
and `adb exec-out screencap -p > shot.png` to see the result. That is how the menu panel's clipping
was found and confirmed fixed. Coordinates are in physical pixels; `adb shell wm size` reports them
the wrong way round in landscape, so take `cur=` from `adb shell dumpsys window displays` instead.

**Verification techniques that have proved useful:**

- `/proc/bus/input/devices` capability bitmaps semantically validate the descriptor with no root and
  no button pressing — they show exactly which `BTN_*` and `ABS_*` codes the kernel derived.
- `/sys/bus/hid/devices/<id>/report_descriptor` is world-readable and lets you diff the descriptor
  the host received against the bytes in `GenericHidProfile`.
- `/dev/input/js0` is world-readable; the `js_event` struct is
  `{u32 time, s16 value, u8 type, u8 number}`, and `type & 0x80` marks the synthetic startup state.
- `art/icon/generate-launcher-icons.py` and `art/input/convert-input-art.py` both double as examples
  of validating assets programmatically — each refuses input it would silently mangle.

**Traps already hit once, worth not repeating:**

- **Cleanup after a suspension point in a keyed `pointerInput` does not run** when the key changes —
  and a gesture whose success changes that key cancels itself. Put anything that must happen in a
  `finally`, or drive it from a `LaunchedEffect`. This caused two bugs in the hold-to-open pill, and
  it is why `EditorScreen` keys its gesture on the surface size rather than on the `ResolvedLayout`
  it is editing: every frame of a drag builds a new one, so keying on that would cancel the drag
  doing the editing. Read the changing value through `rememberUpdatedState` instead.
- **`Modifier.then(...)` crashes lint** (`SuspiciousModifierThenDetector`). Restructure rather than
  suppress; a single `pointerInput` handling both branches was cleaner anyway.
- **Adaptive icons need the `-v26` qualifier.** Lint's `ObsoleteSdkInt` suggests folding
  `mipmap-anydpi-v26` into `mipmap-anydpi`, but AAPT2 then fails to link. Suppressed in
  `app/lint.xml`.
- **Top-level `val`s initialise in declaration order.** A layout that reads a table declared below
  it in the same file gets null. Kotlin catches that within a file; across files it would be a cycle
  and would deadlock at class-load instead. Less pressing now the packs live in `input/art/` and
  every layout reads one from another file, but worth remembering as both folders grow.

### Art assets

Each family lives in its own folder under `art/`, with a script that regenerates what ships.

**The launcher icon** is pixel art. Source is `art/icon/icon.aseprite`; export to `art/icon/icon.png`
(54×54, RGBA) and run `python3 art/icon/generate-launcher-icons.py`. The 54×54 canvas is not arbitrary —
it is the convenient size that upscales to all five adaptive-icon buckets (108/162/216/324/432 px)
on whole pixels, so every export is nearest-neighbour and the pixel grid stays square. Pre-scaling
also avoids Android's bilinear filtering, applied both when decoding into a mismatched density
bucket and again when drawing. The monochrome layer is *derived*, not copied: themed icons use only
the alpha channel, and the art's alpha is a solid square.

**The input prompts** are [Kenney's Input Prompts](https://kenney.nl/assets/input-prompts) 1.5A,
released under **CC0** — crediting Kenney is appreciated but not required. The Xbox and PlayStation
SVGs actually used live in `art/input/` alongside the licence, so the drawables are regenerable from
a clean checkout; run `python3 art/input/convert-input-art.py` after changing them. The pack itself
also covers Switch, Steam Deck and others, which is what makes a new face plate a pack file and a
three-line layout.

Not every button has art: the pack ships an Xbox logo but no PlayStation one, so the PS5 layout's
guide button falls back to a drawn shape rather than borrowing another button's picture. Expect the
same kind of gap in any pack, and prefer the fallback to a near-miss.

The conversion is a text transform, not an SVG renderer, and it can be because these files are
uniformly simple: a 64×64 canvas, one or two `<path>` elements, no strokes, a solid hex fill, and
path data using only `M`, `L` and `Q` — all three valid as `android:pathData` unchanged. That
simplicity is an assumption rather than a guarantee, so the script checks every file against it and
refuses anything it would silently mangle; a gradient or a transform would otherwise convert into a
drawable that renders wrong rather than not at all.

**Vectors are a preference, not a requirement.** `painterResource` returns a painter for a bitmap
just as readily, and neither `ControlIcons` nor the renderer knows the difference, so a PNG dropped
into `res/drawable-nodpi/` works with no code change. Use `nodpi` — the renderer already sizes every
glyph from the layout unit, so Android's density pre-scaling would be wasted work on top of ours.
Size floor is roughly 256 px: a face button renders ~155 px across on a 1080p landscape screen and
more on a tablet, which is also why the pack's 128 px raster set is not worth using.
