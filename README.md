# Blugaemand

Turns an Android phone into a real Bluetooth gamepad. The host sees a standard HID controller — no
driver, no companion app, no root.

**Current state:** an Xbox-style pad (two sticks, D-pad, ABXY, four shoulder controls, the three
centre buttons, two stick clicks), offered in eight presentations — **Default**, drawn as shapes and
labels, and **Xbox**, **PS5**, **Switch**, **Switch 2**, **Steam Deck**, **Wii U** and
**GameCube**, drawn with each console's button art and laid out the way that console lays its
buttons out. **You can also make your own**,
from empty or as a copy of one of those, and move, resize, add and remove controls on it — including
whole arrangements dropped as **one control**, so a face diamond is a single plate that sends A, B,
X or Y depending on where you touch it. Triggers are binary by default and analog per control if
you want the slide, and a stick is either fixed where it is drawn or **dynamic** — an area where
the stick appears under your thumb wherever it lands. Layouts and the choice of one are saved between launches. 
Verified end-to-end against **Linux** and **Windows**.
Two pills sit at the top edge, each opening
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
| `input/` | yes | `ControlSpec`, `ControlIcon`, `ArtPack`, `GamepadLayout`, `LayoutStyle`, `LayoutJson`, `LayoutLibrary`, `LayoutEdits`, `Placement`, `ControlGroups`, `ResolvedLayout`, `TouchRouter`, `art/`, `layouts/` |
| `data/` | no | `LayoutStore` — the only file outside `hid/` and `ui/` that touches Android |
| `ui/` | mostly | `GamepadScreen`, `EditorScreen`, `ControlRenderers`, `PadStyle`, `ControlIcons`, `TopBar`, `TopBarChrome`, `ConnectionBar`, `MenuBar`, `EditorBar`, `ColorPicker`, `theme/`; `ColorMath` is plain Kotlin |

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
- `art/` — the built-in packs: `XBOX_ART`, `PLAYSTATION_ART`, `SWITCH_ART`, `STEAM_DECK_ART`, plus
  `ArtPacks.ALL`, the catalog a saved layout's pack id resolves against. One file each, and the one
  place each console's face-button crossing is decided.
- `layouts/` — one file per built-in, plus `Layouts.ALL`, the catalog the menu lists. `XBOX_LAYOUT`
  derives its geometry from `DEFAULT_LAYOUT` rather than copying it, so tuning a position moves
  both; what is left in its file is which pack to draw with. `PS5_LAYOUT` derives too but restates
  its left cluster — see *Two presentations*. `SWITCH_LAYOUT` and `STEAM_DECK_LAYOUT` are authored
  in full, because on those two the arrangement is the point; see *The five built-in plates*.
  **The derivation happens at class-load, so it does not extend to user layouts:** a copy of Default
  that is then edited moves nothing but itself, which is what you want but is the opposite of what
  "moves both" suggests.
- `ResolvedLayout` — converts a layout to pixels once per size change. The renderer, hit-testing
  *and the editor* all read from it, so what is drawn is exactly what is touchable and exactly what
  a drag moves. Untouched by the two modes: presentation never changes where a touch lands. **One
  control breaks that invariant on purpose** — a dynamic stick is a rectangle you touch with a
  stick drawn somewhere inside it; see *Two kinds of thumbstick*.
- `TouchRouter` — owns `pointerId → control` bindings and produces a `GamepadState`. A binding
  keeps the point it went down at, which is what an analog trigger's pull is measured from and where
  a dynamic stick appears, and the surface it is on, which is what tells the pull which way is
  inwards; see *Analog triggers* and *Two kinds of thumbstick* below.
- `LayoutJson` — the saved format, and the two small serialisers it needs. See *Layouts* below.
- `LayoutLibrary` — the built-ins plus the user's own. Whether a layout can be edited is a fact
  about where it came from, not about the layout, so `GamepadLayout` carries no flag saying so;
  `isEditable` is the single place that line is drawn.
- `LayoutEdits` — every edit the editor makes, as arithmetic on plain data. This is where the
  editor is actually tested.
- `Placement` / `ControlGroups` — controls waiting to be dropped, positioned relative to the point
  they will land on, and the built-in arrangements of several at once. `ControlGroups.clustered`
  restates one of those arrangements as a single control; see *A cluster* below.

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
- `TopBarChrome` — `HoldPill`, `TapPill`, `PillRow`, `Modifier.pillSurface`, `PanelCard`,
  `PanelEntry`, `PanelBack` and `PanelCaption`: the shape, the rows and the gestures every pill and
  panel is built from. A second pill that reimplemented the hold would drift from the first; see the trap
  below for why that gesture is not worth writing twice, and the same goes for the rows now that
  three panels are lists of them. The pill's look is a `Modifier` rather than something `PillRow`
  applies itself, because it has to come *before* whatever gesture or decoration the caller adds or
  the fill paints over it — `HoldPill`'s progress bar is drawn by exactly such a decoration, and
  wrapping instead would have needed `Modifier.then`, which crashes lint.
**A sub-page's first row says *Back*, not where you are.** `PanelBack` is that row, shared by both
panels. The page's name is on the row you tapped to get here and its contents say the rest, so
spending the one row that is a *button* on repeating the title made the button the least informative
thing on the panel. **Red is spent on one thing** — `OverlayColors.Destructive`, on *Delete layout*
and the *Delete* that confirms it, against `Confirm` on *Done*. Those two are the whole of the
colour the chrome carries: a second red row is what would stop the first one meaning anything.

***Done* is a pill, not a panel row.** It is the way out of the editor and belongs to neither scope
the panel and the head bar are split by — and it is taken more often than anything on the panel,
which made a row you have to open a menu to reach the wrong shape for it.

- `ConnectionBar` — `ConnectionPill` and `ConnectionPanel`: status, pairing and reconnection.
- `MenuBar` — `MenuPill` and `MenuPanel`: picking a layout, making one, editing, and quitting. The
  panel's page state lives inside the composable, which is only composed while open, so the menu
  reopens on its root page without a reset that would visibly flip pages mid-close.
- `ColorPicker` — a saturation/value square over a hue bar, plus the two rows that pick which of a
  layout's colours it is aimed at. `ColorMath` beside it is the ARGB↔HSV conversion, with no Compose
  and no Android in it so it is tested on the JVM like the rest of the editor's arithmetic.
- `EditorBar` — the editor's own pill and panel. **Collapsed by default**, because an always-open
  panel covers the top centre and makes the controls under it unselectable. Built on `TapPill`
  rather than `HoldPill`: the hold exists so a stray tap cannot throw a panel up mid-game, and
  nothing being edited is connected to anything, so it would be a tax with nothing bought by it.
  **Split by what an option acts on**: the ☰ Layout panel is the layout — what is on it, how it
  looks, what it is called — while everything scoped to the selected control is a pill in the head
  bar beside it. `SelectionPills` holds those: the trigger and stick modes, *Ungroup* for a plate,
  and *✕ Remove*, each shown only when it applies, alongside `NudgePad`. Mixing the two scopes in
  one list asked the reader to check what each row applied to before tapping it, and the head bar is
  where the selection was already worked on — and stays reachable with the panel shut.

### Two presentations

A layout declares how it is drawn, and is in exactly one mode — the two are alternatives, not
layers, so nothing has to decide what a glyph on a coloured plate would mean.

| | `LayoutStyle.Colors` | `LayoutStyle.Images` |
|---|---|---|
| Built-in | **Default** | **Xbox**, **PS5** |
| Controls | drawn shapes, text labels | a picture per control, from an `ArtPack` |
| Colours | two ARGB values on the layout: resting and pressed | none — the art carries its own |
| Pressed | the fill changes | a second picture swaps in, if the pack has one |

**Which mode a layout is in is a choice you make in the editor**, on *Appearance*: the page lists
*Shapes and colours* and every pack in `ArtPacks.ALL`, and the colour picker sits under a rule below
it while colours mode is the one in force. One row rather than the two it replaces, and
unconditional — the old *Colours* row disappeared the moment a layout took a pack, which is exactly
when someone goes looking for the way back off it.

**The picker is a real one**, not the twelve presets it started as: a saturation/value square over a
hue bar, with *At rest* and *Held* as rows above that pick which of the two it is adjusting. One
picker and a choice of target rather than a picker each, because a 240 dp panel on a landscape phone
has room for one square. Two things about it are load-bearing:

- **It holds a hue of its own.** Black and every grey have no hue at all, so a colour read back out
  of the layout at the bottom of the square reports hue zero — and dragging the brightness down and
  up again would silently turn a blue pad red. The picker is therefore keyed on which colour it is
  editing, so switching targets rebuilds it rather than carrying one colour's hue to the other.
- **Alpha is carried through, never picked.** A translucent control is a real thing to want and a
  separate one — see the opacity item on the backlog — and doing it here would mean the colour
  picker quietly deciding how see-through someone's pad is.

`ArtPack` carries a display `name` beside its `id` for that list. The id is a slug and a
compatibility surface (`steamdeck`), so it is the wrong thing to show a person and deriving one from
the other gets *Steam Deck* wrong. The saved format is unaffected — `ArtPackSerializer` writes the id
and nothing else.

Leaving a pack restores **the colours the layout last had**, held by `MainActivity` for as long as
the editor is open. An image layout genuinely has no colours saved — the two styles are alternatives,
not layers — so without that, a look at the Xbox art and back would hand over the defaults instead of
the two colours someone picked. It is deliberately not persisted: keeping it would mean changing the
format for a convenience.

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
- **A stick is three circles, and the middle one does the 3D.** Under the cap sits a smaller, pale
  grey shaft that travels in the same direction at half the cap's distance. At rest the cap covers it
  completely; near the edge of the well it leans out on the near side, which reads as a stick tilted
  away from the thumb. Two constants in `ControlRenderers` say it — `SHAFT_RADIUS` and
  `SHAFT_TRAVEL` — and nothing about the geometry, the axes or the layout format changes: it is the
  same cap position drawn twice.
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

Only the two fills that change with press state belong to the layout — the stick's cap included, so
a thumbstick wears the same two colours as every button. Strokes, the stick well and shaft and the
canvas stay in `PadColors`: they are the pad's chrome rather than the layout's identity, and a
layout free to recolour its strokes is a layout free to make itself invisible.

**A label is not a layout colour either, and it is not a fixed one.** `labelOn` picks black or white
by the WCAG contrast ratio against whichever fill the control is currently drawn in, crossing over
at a relative luminance of about 0.18. One grey could not stay readable across a palette that runs
from near-black to near-white, and asking a layout to name a label colour would let it name an
unreadable one.

### The eight built-in plates

| | Geometry | Face plate |
|---|---|---|
| **Default** | authored; the others start here | drawn shapes and letters |
| **Xbox** | derived from Default, unchanged | Xbox prompts |
| **PS5** | derived, with the left cluster restated | PlayStation prompts |
| **Switch** | authored in full | Switch prompts |
| **Switch 2** | derived from Switch, unchanged | Switch prompts, triggers redrawn |
| **Steam Deck** | authored in full | Steam Deck prompts |
| **Wii U** | authored in full | Wii U prompts |
| **GameCube** | authored in full | GameCube prompts |

Two are derived, and for the same reason: a Series pad genuinely *is* the default arrangement, and a
Switch 2 Pro Controller genuinely is a Pro Controller — same sticks, same diamond, same D-pad, same
shoulder row. What the newer Nintendo pad adds is a C button and two back paddles, and the profile
has no slot for either, so restating fifteen controls would only give the two plates somewhere to
drift apart. The rest are authored, because a derived geometry would have made them the same pad
with different pictures on it:

- **PS5** puts the D-pad opposite the diamond and drops the left stick level with the right, because
  PlayStation has never used the offset arrangement.
- **Switch** leans both lower controls inboard. A Pro Controller's D-pad and right stick sit
  noticeably closer to the middle than the stick and diamond above them, so the clusters splay
  outwards as they rise — that lean is the most recognisable thing about its front. Minus and Plus
  are a high, wide pair with Home centred below, which is Nintendo's own arrangement rather than the
  row of three everyone else uses.
- **Steam Deck** rides high. The lower third of each side of the real thing is trackpad, so the
  sticks, D-pad and diamond are all pushed up and outboard, the diamond is tighter than the shared
  one, and the centre is spread — View and Options high and wide apart, the Steam button alone and
  low.
- **Wii U** puts *both sticks in a row along the top* and both clusters in a row underneath, level
  rather than splayed. Every other plate here pairs a stick with a cluster on each side; this one
  does not, which is why it looks wrong to anyone expecting a Switch pad and right to anyone who
  owned one.
- **GameCube** is the only plate whose face buttons are not a diamond at all: a large central A with
  B, X and Y as smaller satellites around it, and the C-stick tucked underneath rather than sitting
  as a peer of the left stick.

Three things none of these plates can show: the Deck's trackpads and back paddles, the Switch 2's C
button and paddles, and the fact that neither a Pro Controller nor a GameCube pad has L3/R3 buttons
at all — on a Pro Controller you press the sticks, and a GameCube's do not click. The stick clicks go
along the bottom edge on all of them, out of the way, because there is no authentic place for them.

**The GameCube plate is where a pack is most visibly incomplete**, and deliberately so: five of its
controls draw as plain shapes because the 2001 pad has no such button. It has no Select, no Home and
no stick clicks, and its shoulder row is three buttons — L, R and Z — for the four slots the profile
declares. Each keeps a neutral label rather than borrowing another button's prompt.

**The Nintendo plates are where the face-button crossing bites hardest.** Every pack keys its glyph
to the position on the diamond, and Nintendo swaps *both* pairs relative to Xbox:

| Position | Slot | Xbox | Nintendo |
|---|---|---|---|
| top | `WEST` | Y | **X** |
| left | `NORTH` | X | **Y** |
| right | `EAST` | B | **A** |
| bottom | `SOUTH` | A | **B** |

So **the key drawn A sends the same button an Xbox pad's B sends**. That is not a bug — it is the
swap every Switch owner already lives with, and matching the printed letter instead would move the
button under the thumb rather than the picture on it. It applies to the Switch, Switch 2 and Wii U
plates alike; the Wii U is where Nintendo started doing it.

Two plates are exempt, for opposite reasons. Valve kept Microsoft's arrangement, so **Steam Deck**
agrees everywhere. And the **GameCube** predates the swap — its printed letters land on the slots
that report them, so the key drawn A really does send A. That makes it the one Nintendo plate where
copying the Switch table would be wrong in all four positions.

### Layouts, built-in and user-made

**The built-ins are read-only.** `DEFAULT_LAYOUT`, `XBOX_LAYOUT` and `PS5_LAYOUT` are `val`s in the
source; you make your own from empty or as a copy, and edit that. `LayoutLibrary.isEditable` is the
only place that distinction is drawn, which is what keeps it from having to be remembered in every
screen — the menu offers *Edit layout* only when it is true, so there is no disabled row to explain.

A copy takes a fresh UUID rather than anything derived from its source, so a layout imported from
someone else can never land on top of one already here. It keeps everything else, art pack included:
a copy of the PS5 pad is a PlayStation-looking pad you can then rearrange.

**The menu draws the same line the code does.** `LayoutLibrary.builtIn` and `.user` are the two
halves of `.all`, and the Layouts page renders them as two runs separated by a rule, each row marked
with a Kenney joystick — plain for what ships, red for what you made. That is also why `MenuPanel`
takes the whole `LayoutLibrary` rather than a list of layouts: a flat list cannot say which half a
row is in, and having it means *Edit layout* asks `isEditable` where it is used instead of being told
the answer from two screens up. The rule is suppressed when you have made nothing, or a fresh install
shows a line with an empty half under it.

**Editing is four operations** — move, resize, add, remove — plus the two colours and a rename. A
nudge is a move by an exact amount rather than a fifth operation: the arrows call `movedControl` with
`nudgeStep` for a delta, so everything a drag is clamped and snapped by applies to them unchanged.
A handle drag is a resize by an exact amount, and stands in the same relation to a pinch.
All
of the arithmetic is in `LayoutEdits` and `Placement`, which are plain Kotlin, so the editor is
tested on the JVM and `EditorScreen` is nothing but gestures.

**A control's identity, for editing, is its index in `layout.controls` — not its `ControlId`.** The
same id may appear more than once: two A buttons, one under each thumb, is a reasonable pad. So the
id says what a control *does* and the index says which one it *is*. Anything about a particular
control keys on the index (selection, moving, resizing, the pressed highlight); anything about the
button it drives keys on the id (what the host is sent, which glyph an art pack supplies). Getting
that backwards is not a compile error — it looks like pressing one A button lighting both.

**Adding is pick, then tap where it goes.** A control's default position is almost never the wanted
one, so nothing is created until the drop point is known, and a preview of what is coming follows
the finger. `Placement` is the shape of that: controls positioned relative to the point they will be
dropped on, which makes a single control a placement of one and lets groups reuse the whole path.

**A control group goes down one of two ways.** `ControlGroups.ALL` holds seven arrangements — the
face diamond, a four-arm D-pad, the centre three, and each shoulder pair side by side or stacked —
most derived from `DEFAULT_LAYOUT` so tuning it moves them. The editor's *Add control group* page
has an **As one control** switch that picks which way the chosen one lands:

- **Clustered**, through `ControlGroups.clustered`, and **the default**. One control that has
  several buttons on it. What you want when the arrangement is the point — see below — which is
  almost always why a group is being placed rather than four controls one at a time. *Ungroup* is
  one tap away for when it is not.
- **Loose**, the original. The members are ordinary controls the moment they are dropped: nothing
  records that they arrived together and nothing in the saved layout says so. What you want when the
  arrangement is a starting point to be tuned.

The switch is held by `MainActivity` alongside **Grid**, not by the panel that shows it. The panel is
destroyed every time it closes — which placing something does — so state living there would reset
after every drop, and laying out a pad of plates would mean setting it again for each one. Both are
settings about how the editor works rather than about the thing being placed.

### A cluster: one control with several buttons on it

`ControlId.Dpad` was already a control that resolves a touch to an output from *where inside itself*
it landed. `ControlSpec.Shape.Cluster` generalises that: a plate of members, one entry in
`layout.controls`, one thing to select, move, resize and delete.

**Which member a touch means: the one whose own area it is in, else the nearest centre.** Nearest
centre alone splits the plate at the midpoint between two members and ignores how big either is — on
the centre cluster, where Home is larger than the buttons beside it, that line falls inside Home's
own glyph and touching the edge of the picture would send Back. Falling back to nearest afterwards
is what leaves the plate with no dead spots in it, so a thumb anywhere on it sends something and can
roll from one member to the next without lifting, exactly as it can across a cross. The member is
re-read on every pointer event, not fixed at touch-down, which is what makes the roll work; two
thumbs on one plate are two bindings and hold two buttons.

**Everything inside a plate is a fraction of the layout unit** — the members' offsets from its centre
as well as their sizes, including a member `Rect.width`. At top level a position is a fraction of
screen width or height and a rectangle's width a fraction of screen width, and both exist for reasons
that stop at the plate's edge: a layout should spread with the screen, and a shoulder button should
stretch the top of it. A plate is a compact object whose parts have to hold their arrangement. In
screen fractions a face diamond's vertical gap is 1.74 radii on 16:9 and 2.31 on 4:3 — it stretches,
exactly as a loose group does. In unit fractions it is rigid on every screen, and resizing becomes
one factor applied to every number every member has, whatever mixture of shapes they are.
`ControlGroups.clustered` does the conversion, which is one constant: the built-in geometry is
authored against 16:9, so widths stretch by `16/9` going in and heights pass through.

**Members live on the shape, not on the id.** `ControlId.Cluster` is a marker and carries nothing.
Everything in the app that asks how big a control is or where its parts are asks its shape —
`movedTo`, `withCenter`, `scaledBy`, `clampedOnScreen`, `ResolvedLayout`, `drawControl` — and putting
the members on the id would make extent a function of the id instead. `ControlId.Cluster` is
deliberately absent from `ControlId.ALL`: there is no such thing as *the* cluster, only a particular
arrangement, so it has no default spec and cannot appear on the *Add control* page.

**Art needed nothing adding.** A plate draws as its members, and a member carries an ordinary
`ControlId` — which is exactly what `ArtPack.glyphs` is keyed by. Every pack already has a picture
for every button on a plate, pressed art included, and will for a plate nobody has thought of yet.
Two existing details make that free and are worth not undoing: `rememberPadStyle` resolves every
`ControlIcon` regardless of what the layout holds, and `ArtPack.glyph` is a map lookup, so a plate's
own id simply misses and falls through to drawing the members.

Members are plain buttons, triggers and D-pad arms; a `require` in the shape's constructor — which
runs on deserialise too, so a hand-edited file is caught — rejects anything else and rejects an empty
plate. A `Stick` is out because a stick's cap is positioned through `stickTouch`, which is keyed by
top-level control; a nested plate and a one-piece cross are out because neither adds anything a flat
list of members does not. `decodeLayouts` converts those `require` failures into
`SerializationException`, which is what it promises to throw and what `LayoutStore` catches — without
that, a bad stored file would crash rather than report an empty library.

**Ungroup is the way back out.** A member is not separately selectable, by design — one plate, one
selection — so *Ungroup* on a selected plate replaces it with its members, each left exactly where
the plate was drawing it and converted back out of unit fractions on the way. It is also why the
plate does not need a second selection model inside it: anyone who wants to tune one button ungroups,
moves it, and is back to controls that behave like every other control.

Decisions in there that are easy to undo by accident:

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
- **A group snaps as a unit.** The drop point is snapped once and the members keep their offsets
  from it; snapping each member separately would pull the arrangement out of shape. Clamping is the
  one thing that can distort a group, and only at the very edge.
- **A group centres on its bounding box, not on the average of its positions.** With an odd member
  out — the centre three, where two are small and one is not — an average drifts towards the
  crowded side and the group lands beside the thumb rather than under it.
- **The corner handles keep the aspect ratio and the edge handles do not.** That is the whole
  reason for having eight rather than four: a pinch can only scale uniformly, so a shoulder button
  could not be made longer without also being made taller. Only a `Rect` and a dynamic stick's area
  have a size per axis to stretch — everything else is one radius — and `scalesPerAxis` is the one
  place that is decided; an edge handle on a round control scales it whole rather than doing nothing.
- **A handle anchors the opposite edge, and everything else about one exists to keep that true.**
  Dragging the right edge right widens the control rightwards instead of growing it about its
  centre, which is what makes a handle feel like it is holding the edge it is drawn on. So the size
  is worked out from where the dragged edge ends up (`draggedHalfExtent`), never from a factor
  applied to the whole: half the extent changes by *half* the delta, and a handle that changes it by
  all of the delta moves the edge at twice the speed of the thumb — which is what a control
  "changing shape under the finger" actually is.
- **Snapping lands the dragged edge on the grid, not the size.** Rounding the size instead jumps the
  control the moment a handle is touched, and jumps it to a multiple of the step, which is nowhere
  near where the edge was — a 288 px trigger became 270 px on the first pixel of the drag. The edge
  is also what has to line up with another control's, which is what the grid is for.
- **Growth stops at the glass.** The dragged edge is held inside the surface, so a control grows
  until it reaches the screen and then stops, with the anchored edge still still. Doing it any later
  — letting the size run and relying on the on-screen clamp afterwards — would shove the whole
  control back and take the anchored edge with it.
- **There is no maximum size.** There was, and it was wrong twice over: a number picked against the
  biggest thing that shipped is a guess about layouts nobody has made yet, and it made the two
  limits asymmetric for no reason a user could see. A control that cannot be shrunk past being
  touchable is obvious; a control that stops growing half way across the screen is a bug.
  `MIN_CONTROL_EXTENT` stays, and it is the only one.
- **A plate scales by one factor, floored by its smallest member.** The floor exists so nothing
  becomes too small to grab hold of, and what a thumb aims at is a button, not the plate — so the
  smallest member is the one that decides, since it is the first to reach it. Snapping applies to the plate's own extent once, not to each member — rounding them
  separately is what would pull the arrangement out of shape — and a resized plate is pulled back on
  screen afterwards, because it grows about its centre by half a plate rather than half a radius.
- **Clamping on screen takes the whole `ControlSpec`, not the bare shape.** A plate's extent comes
  from the members it carries, so the id and the shape have to arrive together.

**A control arrives at the size and label `DEFAULT_LAYOUT` gives it** — only the position is chosen,
which is what stops a fresh control turning up as an unlabelled speck. Six of `ControlId.ALL` are not
on the default pad and carry a fallback spec instead: `L2` and `R2`, whose analog halves it reaches
through `ControlId.Trigger`, and the four D-pad arms. The fallbacks are derived from `ControlId.ALL`
rather than listed, so a control added to that list with no home fails at class-load rather than the
first time someone tries to add it.

### Two kinds of D-pad

A layout can have either, and both send the host the same thing — one hat.

| | `ControlId.Dpad` | four `ControlId.DpadButton`s |
|---|---|---|
| On screen | one cross | four separate controls, put where you like |
| Diagonals | roll the thumb across it | hold two arms |
| Resolved by | sector, from where in the cross the touch landed | `Hat.of(up, down, left, right)` |
| Drawn held | the pushed arm lights, in image mode | the arm itself lights |

The four-button form costs almost nothing: `Hat.of` was already in `hid/GamepadState.kt`, already
tested for diagonals and opposing-press cancellation, and unused. The hat has always been computed
in `TouchRouter`, so neither the descriptor nor the encoder knows the difference.

Two consequences worth keeping:

- **The hat is settled after the whole binding loop, not inside it.** One arm on its own says nothing
  about the value — it takes all of them together, which is also what makes opposing arms cancel
  rather than the last one read winning.
- **A held cross beats the arms.** A layout carrying both is one where the cross is the deliberate
  control, and a stray arm should not override a thumb already on it.

**The one-piece cross lights the arm being pushed.** `TouchRouter.dpadPush` answers with the same
`Hat` the host is being sent — the same arithmetic, not a second opinion about where the sector
boundaries fall — and `ArtPack.dpadArms` maps four of those onto pictures. Kenney draws the four
cardinals and no diagonals, so a diagonal falls back to the cross lit whole: honest about being
pushed without claiming a direction the art cannot show. A thumb in the dead zone draws the *resting*
cross, because it is touching the control and sending nothing.

**The arms still draw as shapes with arrow labels, and that is now a finding rather than a gap.**
Every D-pad picture in the pack is a *whole cross* — unlit, one arm lit, one axis lit, all lit — and
there is no picture of an arm on its own. That is exactly what makes those pictures right for the
one-piece cross, where the cross *is* the control, and wrong for an arm placed on its own: a
four-button D-pad would wear four complete crosses, and a four-arm plate would draw a cross made of
crosses. So the arm pictures live in `dpadArms`, keyed by direction, rather than in `glyphs` under
`ControlId.DpadButton` where a loose arm and every plate member would pick them up. Per-arm art
needs art of an arm, which this pack does not have.

User layouts are deliberately allowed to be **incomplete, empty or overlapping**. The layout-sanity
tests apply to `Layouts.ALL` only; `missingButtons()` is surfaced in the editor as a caption, which
is a warning and not an error. A pad with no Start button is a strange pad, but it is allowed to be
one.

### Analog triggers

A trigger sends a value, not a press, and **each one is set to one of two `TriggerMode`s** —
binary or progressive — by the row the editor shows when it is selected.

**Binary is the default, and the simple one: `255` while a finger is on it, `0` when there is
not.** What a trigger did before it was analog at all, and the default because most games only ask
whether the trigger is down; for those, a value that has to be aimed is worse than a tap. A pad
should behave like a pad before it behaves like a pedal, so the slide is the thing you opt into on
the triggers that want it. Nothing about where the finger goes matters, so a binary trigger can be
tucked anywhere a button can. **Every built-in layout is binary**, none of them naming a mode at
all — they take the default, so this is one constant rather than a line repeated eight times.

The mode lives on `ControlSpec`, not on `ControlId.Trigger`. An id is what a control *is*, and it is
compared as one all over the app — `withControlAdded` counts copies by it, `missingButtons`
subtracts by it, the editor's add page lists by it — so a mode carried there would make a binary ZR
and a progressive ZR two different controls to every one of those. It is also per control and not
per layout, because the two triggers on a pad are not obliged to agree: a progressive accelerator
and a digital handbrake is one pad. `LayoutEdits.withTriggerMode` sets it, and sets it on *every*
trigger of a plate — a plate is one thing to select, so it has to be one thing to set.

**Progressive is the interesting one**, and the rest of this section is about it. Its value comes
from **where the finger has slid to** since it went down.

- **A touch rests in the middle** — `128`, `GamepadState.TRIGGER_TOUCH_REST` — because a trigger is
  slid *both* ways and has to start with room in either. It is also comfortably over
  `GenericHidProfile`'s digital threshold of 32, so a plain tap still asserts `L2` / `R2` for hosts
  that only read buttons.
- **Which way means *more* depends on the axis**, and both axes measure from the same place:
  whichever screen edge the finger is nearer along the axis in play. **Sideways, in towards the
  middle of the screen raises it** and back out lowers it. **Up or down, out towards the nearer edge
  raises it** and back in lowers it. So a ZR up in the right-hand corner — where the built-in
  layouts put it, at `(0.91, 0.08)` — is raised by sliding *left* or *up* and lowered by sliding
  *right* or *down*, and a trigger placed along the bottom is raised by sliding *down* instead.
  Neither is a fixed compass direction, because a layout may put a trigger anywhere and the rule has
  to read the same wherever it lands. That the two senses come out opposite is deliberate: it is a
  decision about feel — the thumb draws in off the side of the glass and pushes out over the top of
  it — and nothing else depends on them agreeing.
- **One axis at a time.** Whichever component of the drag is the larger is the one that counts, and
  the other is ignored outright rather than added in. Summing them would make a diagonal do
  something neither of its parts does, and a trigger in a corner — with an edge above it *and* an
  edge beside it — has two directions that plainly mean "less" and no sensible way to combine them.
  The axis is re-read on every event, so a drag that turns a corner changes which one it answers on.
  An exactly diagonal drag counts as vertical; it has to be one of the two and it has to be the
  same every time.
- **The travel available is capped by the room there is.** The nominal throw from rest to either
  rail is `TRIGGER_TRAVEL_SPANS` of the control's shorter way across — the same measure `drawGlyph`
  sizes a picture by, and for the same reason: it is the one extent a control of any shape has that
  is not distorted by how wide it is drawn. But a trigger sits against an edge, and towards that
  edge there may be only a few dozen pixels of glass before the finger runs out of screen, so **each
  direction takes the smaller of the nominal throw and the distance to the edge that way**. The
  floor and the ceiling are therefore both always reachable, however tightly a layout tucks a
  trigger into a corner. The cost is that whichever of the two runs at an edge is touchier than the
  other — the built-in shoulder triggers sit 8% of the screen height from the top, which is roughly
  a third of their nominal throw, so their ceiling comes up fast. Reachability is the thing that
  cannot be given up; if the top of the range wants a longer pull, the fix is to move the trigger
  further down the layout.
- **The touched range is `1..255`, not `0..255`.** A finger on a trigger it has run down to the
  floor is not the same thing as no finger, and `0` is the value every host reads as released —
  reserving it is what makes "touched, at rest" sayable. `GamepadState.TRIGGER_TOUCH_MIN` is that
  floor and `triggerFromUnit` maps onto it; a released trigger never goes through there, it is
  `AXIS_MIN` by resting default.
- **The pull is measured from the touch-down point, not from the control.** Where within a trigger
  a finger happens to land is not something anyone aims, so it must not be an input to the value —
  two touches on opposite edges of the same trigger both start at rest. The room either side is
  measured from that same anchor, so one point answers both which edge is nearer and how much
  glass is left that way.

**While a finger is on one, the value is drawn in a pill just clear of the control** — below it,
unless the pill would fall off the bottom of the glass, in which case above. `TouchRouter.triggerValue`
is what the renderer asks, and it is the same number the host is being sent, computed the same way:
a read-out disagreeing with the report would be worse than no read-out. It answers for a trigger
reached as a plate member too, positioned against the plate, since the plate is what the binding is
on. **A binary trigger has none**: `triggerValue` answers null for one however hard it is held. It
sends one number and only that number, so a pill pinned at `255` adds nothing to a control that has
already lit up, and a number that never moves reads as a broken analog trigger rather than a switch.

**A saved layout carries `"triggerMode"` on every control**, like `"label"`, and it is defaulted
rather than required. That is deliberately *not* a format version bump: a layout that does not name
a mode comes back binary, and a layout that does loads on a build that predates the setting as the
progressive trigger that build only knew how to be — `ignoreUnknownKeys` skips the field. Bumping
the version would instead have made old files unreadable until a migration was written, to buy
nothing. The default is a behaviour, not a compatibility promise: it moved from progressive to
binary once there were two modes to choose between, and moving it again would re-answer for every
file that stays quiet about it. Files written by this build are not quiet — `encodeDefaults` puts
the key on every control — so what a change to the default reaches is hand-written layouts and
anything saved before the setting existed.

All of this is `TouchRouter`, `LayoutEdits` and the renderer. The descriptor already declared the
full range and the encoder already forwarded it, so neither changed.

### Two kinds of thumbstick

A stick is set to one of two `StickMode`s — fixed or dynamic — by the row the editor shows when one
is selected. **Fixed is what a stick has always been**: it sits where it is drawn, and the thumb has
to find it. **Dynamic is a rectangular area with no stick drawn in it.** A touch anywhere inside
makes one appear at that point reading `0,0`, the finger drags it off centre from there, and lifting
takes it away again. It is the answer to the thing a fixed stick is bad at — a thumb hunting for the
stick before it can push it, in the dark, mid-game.

The mode lives on `ControlSpec` and not on `ControlId.Stick`, for exactly the reasons the trigger's
does: an id is compared as identity all over the app, so a mode carried there would make a fixed
left stick and a dynamic left stick two different controls to `withControlAdded`, `missingButtons`
and the editor's add page. Per control rather than per layout, too — a dynamic left stick to walk
with and a fixed right stick to aim with is one pad.

**The anchor was already there.** A `Binding` keeps the point the finger went down at, because that
is what an analog trigger's pull is measured from. A dynamic stick is the same idea on both axes:
the offset is measured about the touch-down point instead of about the control's centre. That is the
whole of the routing change.

- **It breaks "what is drawn is exactly what is touchable"**, which is the invariant `ResolvedLayout`
  exists to hold — and it breaks it the honest way. **The area is the control**: `contains` and
  `extentX`/`extentY` answer with the rectangle, so hit-testing, dragging, the on-screen clamp and
  the editor's selection ring are all about the thing on the glass. The stick inside it is a
  transient the renderer places from the router, the way the trigger read-out already is.
- **The renderer is told where the base is, not just how far the cap has moved.** `stickTouch` hands
  back a `StickTouch` — base in pixels, offset as a -1..1 pair — because a dynamic stick has to say
  where its centre *is*. Same rule as `triggerValue`: one answer, from the binding, so the picture
  cannot disagree with what the host is being sent. A fixed stick answers there too, about its own
  centre, which is what leaves the renderer with one case instead of two.
- **The anchor does not move.** Once a stick has spawned it is centred on that point until the
  finger lifts. Past the radius the value simply clamps, so a finger that keeps going holds full
  deflection and comes back to a centre that is exactly where it left it — which is what a stick
  under a thumb is: something you push against, not something you tow. **The area is for spawning
  only** — it decides where a stick may be *started* and has no say over anything after that, so
  the finger is free to leave it, which is the rule that already lets a thumb roll off a button
  without releasing it.
- **The area loses to anything drawn on top of it.** A Start button inside the rectangle is a Start
  button: `hitTest` gives the touch to any non-area control containing the point, however far its
  centre is, and only falls back to the areas when nothing else was touched. This makes the area the
  first control *meant* to overlap others, so the *no built-in layout has overlapping controls* test
  skips pairs involving one.
- **One stick per area: first finger wins, the second is ignored.** Not queued, not stacked — a
  second stick out of the same area would fight the first for the same two axes. So `down` refuses
  an area that already has a pointer on it, which is a new thing for it to refuse; every other
  control takes as many fingers as land on it. A finger refused that way binds to nothing at all,
  exactly as a touch on bare glass does.
- **A small dead zone at the anchor**, `TouchRouter.DYNAMIC_DEAD_ZONE`, 12% of the radius, with the
  rest of the throw stretched over what is left so the value climbs from zero at its edge rather than
  jumping to it. The anchor is wherever the thumb happened to land rather than anywhere it aimed, so
  without one every spawn would start with a few pixels of drift on both axes. A constant and not a
  field on the shape, unlike the D-pad's: it compensates for a thumb, which is the same everywhere,
  not for a layout's taste in sizes. **The fixed stick gets none** — it has never had one, and its
  centre is a place you can feel.

**The throw and the area are sized separately**, and both live on `ControlSpec.Shape.Stick`: the
`radius` is how far the stick travels, `areaWidth` and `areaHeight` are the rectangle it may be
spawned in — measured like a `Rect`'s, against the screen and against the layout unit respectively.
A pinch — or a handle drag — on a dynamic stick resizes **the area**, since that is what is drawn
and what is touched;
growing the region a stick can be started in should not cost a longer sweep to push it. Which leaves
the throw tuned by pinching the stick as a fixed one and switching back — nothing is lost across the
switch, in either direction. An area may be made as large as the screen, which is what one is for —
half the pad is a perfectly reasonable answer for a region. The floor is shared with every other
control: a thumb misses either.

**An empty area still draws its outline**, faint, on the pad as much as in the editor. It is the one
control with nothing of its own to draw, and an invisible one is indistinguishable from a layout that
has lost a control.

**A saved layout carries `"stickMode"` on every control** and `"areaWidth"` / `"areaHeight"` on every
stick, defaulted, on the same bargain the trigger mode struck: a layout saved before any of this
existed comes back as the fixed stick it was, a newer file loads on an older build as a fixed stick
too, and no format version bump is needed either way.

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
          "label": "Y", "triggerMode": "PROGRESSIVE" }
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

- **`GamepadProfile`** — supporting a fussier host means adding an implementation, not threading
  special cases through the service. The Switch was the intended proof of that and turned out to be
  impossible for an unrelated reason: it identifies controllers by USB vendor and product ID, and
  Android gives an app no way to publish one. Measured against a real console — see *Known
  constraints* in [TODO.md](TODO.md). The seam is unaffected; the host is simply unreachable.
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

### CI

`.github/workflows/build.yml` runs the same three commands the section above documents — `test`,
then `lint` and `assemble` for one variant — on every push and pull request to `main`, and on demand
from the Actions tab. The APK is uploaded as a build artifact, so a phone can be fed a build without
a toolchain on the machine holding it; the test and lint reports go up too, and on failure as well,
which is the point of having them.

**Running it by hand takes a Debug/Release choice.** Automatic runs are always Debug — `inputs` is
null outside a manual dispatch, and the fallback in the *Resolve build type* step is what makes one
workflow serve both. The variant is a `choice` input rather than free text, so the value that ends
up spliced into a Gradle task name can only ever be one of two.

**Release comes out unsigned** until there is a signing config (it is the open half of the backlog
item this landed under): `assembleRelease` produces `app-release-unsigned.apk`, which is fine for
inspection and useless for installing. Sign it by hand, or use Debug. The runner's Android SDK
arrives with its licences accepted, so AGP fetches platform 36 itself and no `local.properties` is
written.

### Tests

`app/src/test/` covers the encoder, descriptor structure, touch routing, the saved format and every
edit the layout editor makes — all on the JVM:

- `GenericHidProfileTest` — exact report bytes, per-button bit positions, every hat direction, axis
  clamping, and a walk of the descriptor's item stream verifying it is structurally sound and
  declares exactly 9 bytes.
- `TouchRouterTest` — pointer binding and release, multitouch independence, stick normalisation and
  circular clamping, dynamic sticks (where one appears, the dead zone at the anchor, the base
  following the finger, the area losing to what is drawn on it and taking one finger at a time),
  D-pad sectors, and layout sanity (no overlaps, every button reachable, unique ids). The sanity tests run over `Layouts.ALL`, so a new built-in is covered by adding it — and
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
- `ColorMathTest` — the colour picker's arithmetic: every corner of the RGB cube round trips through
  hue/saturation/value unchanged, alpha is carried rather than picked, hue wraps at both ends while
  saturation and value clamp, and — stated as a test rather than found as a bug — *hue is lost on
  the way to a grey*, which is why the picker holds one of its own.
- `LayoutEditsTest` — everything a drag, a pinch, a handle, a nudge, an add and a remove do, on a 1000×500
  surface that makes the layout unit exactly 500 and the grid step exactly 25. The round-trip tests
  (move by `(dx, dy)`, then by `(-dx, -dy)`) catch an axis divided by the wrong dimension,
  and several exist only to hold the line that controls are addressed by index — *moving one copy
  leaves the other where it is* is the one that fails if that slips.
- `PlacementTest` — that something lands centred on the point it was dropped on, that a group keeps
  its arrangement wherever it goes and snaps as a unit, that nothing ends up part-way off screen,
  and that the preview shows exactly what placing would add. A preview that disagreed with the
  result would be worse than no preview, since it is what the finger is aiming with.
- `ControlGroupsTest` — the catalog's invariants: distinct names, more than one control each, no
  duplicates within a group, nothing stacked on anything else, and every group centred on the
  origin. Most groups derive from `DEFAULT_LAYOUT`, so these also fail if a control is renamed or
  dropped from the default pad, rather than at the moment someone taps the group that wanted it.
  Every group is also checked clustered, the load-bearing one being that on 16:9 — the aspect the
  geometry is authored against — a plate puts its members in exactly the pixels the loose group
  would. Off 16:9 the two deliberately part company, which is the point of the unit fractions.
- Cluster behaviour is tested where the rest of that behaviour lives rather than in a file of its
  own: routing and the no-dead-spots rule in `TouchRouterTest`, moving, scaling and ungrouping in
  `LayoutEditsTest` — including that a plate resolves to the same arrangement on 16:9 and on 4:3,
  which is the one thing a loose group cannot do — dropping and clamping in `PlacementTest`, the
  nested shape and its `require`s in `LayoutSerializationTest`, and that every pack already has art
  for every member in `LayoutArtTest`.

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

- **Register before pairing.** What tells the host this is a gamepad is the **HID service record**,
  and the host caches the service list at pair time. If the app was not registered there was no
  such record to find, and the host never opens the HID connection.

  Not the class of device, despite the obvious guess: that belongs to the adapter and stays
  *Phone* whatever the app does — `subclass` only reaches SDP attribute `0x0202`. Measured, while
  registered and advertising, in the Iteration 4 work.
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
2. The **☰ Layout** pill opens the menu and closes again, so the pad underneath stays reachable.
   It holds what applies to the layout as a whole; what applies to the control you selected sits as
   pills beside it, in the head bar, where they stay reachable with the menu shut.
3. **Drag** a control to move it, **pinch** it to resize, or drag one of the **eight arrows** that
   appear around whatever is selected. Each holds the edge it is on: the opposite side stays where
   it is and the one you are dragging follows your finger, until it reaches the edge of the screen.
   The four on the corners keep the control's proportions; the four on the edges stretch that side
   alone, so a shoulder button can be made longer without being made taller. Anything drawn as a
   circle — a button, a D-pad, a stick — has one size and no second axis, so its side arrows grow it
   whole. Nothing has a maximum size. **Grid** toggles snapping, which applies to
   sizes as well as positions, so two buttons meant to match can be made to match. The **◀▲▼▶
   arrows** beside the pill move whatever is selected one step at a time — a whole grid cell with
   snapping on, a fifth of one with it off, which is finer than a thumb can place anything.
4. **Add control** then **tap the pad where it should go** — a preview follows your finger until you
   lift. Any control can be added more than once. **Add control group** does the same for several at
   a time: the face diamond, a four-button D-pad, the centre three, or a shoulder pair side by side
   or stacked.
5. **As one control**, on the group page, is **on**: the arrangement lands as a *single* control —
   the face diamond as one plate that sends A, B, X or Y depending on where you touch it. It moves,
   resizes and deletes as one, keeps its shape on any screen, and rolling a thumb across it changes
   button without lifting. **Ungroup**, beside the arrows while a plate is selected, breaks it back
   into separate controls, each where the plate was drawing it; turning the switch **off** before
   placing does the same thing up front, dropping ordinary controls that move separately.
6. **✕ Remove** in the head bar takes out whatever is selected. A caption on the menu names any button left
   with no control — a warning, not an error.
7. **Trigger**, a head-bar pill, appears only while a trigger is selected — or a plate with one on
   it — and switches that trigger between **binary** (fully pulled while touched, like a button) and **progressive**
   (rests halfway, slide to pull it). Binary is what every trigger starts as, including on the
   built-in pads. Each trigger is set on its own, so one pad can have both.
8. **Stick**, likewise, appears only while a thumbstick is selected, and switches it between
   **fixed** — where it is drawn, resize it to change its throw — and **dynamic**, an area where the stick appears under
   your thumb wherever it lands inside it and vanishes when you lift. Resizing a dynamic stick
   changes the *area*, which can be made much bigger than any button and is one of the two things
   that stretches per axis; its throw is whatever size it had as a fixed stick, so switch back,
   resize, and switch again to change that. Anything you place
   inside the area still works normally — a touch on it presses it and spawns no stick.
9. **Appearance** picks how the pad is drawn: *Shapes and colours*, or one of the seven art packs.
   In colours mode a picker sits below the rule — tap *At rest* or *Held* to say which fill it is
   adjusting, then drag on the square and the hue bar; in image mode the art carries its own colours,
   so there is nothing there to pick. Going back to shapes returns the colours you had.
10. **✓ Done**, the green pill at the head of the bar, goes back to the pad. Everything is saved as you go;
   **Delete layout**, the one row in red, asks first, because there is no undo. Every sub-page
   opens with **‹ Back**.

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

**`art/input/` is flat, and Kenney's download is not.** Its art is keyed on the console folder, so
`Nintendo Switch` and `Nintendo Switch 2` each ship a `switch_button_zl.svg` — different pictures
under one name, and copying both in would silently leave whichever landed second. Only the four
redrawn Switch 2 triggers are carried across, renamed `switch2_*` on the way in. That rename is the
one place a file here does not match its name upstream, and it is worth checking for whenever art
from a new folder is added.

Two of those files are **chrome rather than control art**: `generic_joystick` and
`generic_joystick_red` mark the two halves of the menu's layout list. They deliberately do **not**
become `ControlIcon` entries — that enum is a saved-layout compatibility surface, and a menu icon has
no business being renameable only at the cost of orphaning someone's layouts. They are referenced as
`R.drawable` from `MenuBar` instead, which is why `ControlIcons` is described as the only place that
maps a `ControlIcon` to a drawable rather than the only place that mentions `R.drawable`.

Not every button has art: the pack ships an Xbox logo but no PlayStation one, so the PS5 layout's
guide button falls back to a drawn shape rather than borrowing another button's picture. The Switch
and Steam Deck sets do have their guide buttons — Home and Steam — so on those two nothing falls
back but the sticks. Expect the same kind of gap in any pack, and prefer the fallback to a near-miss.

Seven families ship: Xbox, PlayStation, Switch, Switch 2, Steam Deck, Wii U and GameCube. Most of
the Nintendo and Valve sets have no coloured face buttons — they draw them in one colour, as the
real pads are — so the pressed picture is the solid fill rather than a second hue. The GameCube is
the exception among them, because its A and B really are green and red, and those two take a
colour-fill pressed picture the way Xbox's do.

Stick clicks are the other uneven part. Only the Switch and Steam Deck sets draw a pressed stick, so
`SWITCH_LS` and `DECK_LS` pair a picture of the stick with a picture of it pressed, which says the
same thing; the Wii U set has no pressed stick and the GameCube pad has no click at all, so on both
of those plates L3 and R3 fall back to their labels.

**Switch 2 is a pack stated as a difference, not a copy.** Of the 112 pictures across Kenney's two
Switch folders, 105 are byte-identical files — only the triggers were redrawn, plus the new C, GL
and GR buttons. So `SWITCH2_ART` is `SWITCH_ART`'s map with two entries replaced, and owns four
`ControlIcon` names rather than thirty. The same split as `PS_`/`PS5_`, pushed one step further:
there the two pads differed in more than a picture, and here they do not.

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
