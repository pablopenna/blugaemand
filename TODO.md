# TODO

Living backlog. Update as work lands — tick items off, move things between iterations, and add what
turns up along the way.

---

## Iteration 1 — Classic HID gamepad, static layout, Windows host

- [x] Gradle scaffolding (AGP 8.13.2, Kotlin 2.0.21, Compose BOM 2024.09.00, minSdk 28)
- [x] `GamepadState`, `GamepadProfile` seam, `GenericHidProfile` descriptor + encoder
- [x] `HidGamepadService`: profile proxy, SDP registration, callbacks, 100 Hz coalesced send loop
- [x] Normalised layout data, `ResolvedLayout`, `TouchRouter`
- [x] Compose pad with multitouch, connection bar, immersive landscape
- [x] 36 JVM unit tests; lint clean
- [x] Pixel-art launcher icon with derived monochrome layer for themed icons
- [x] **Verified on a physical phone** — Xiaomi 25080RABDC, Android 16 / HyperOS. The ROM does
      include the HID Device profile (`bluetooth.profile.hid.device.enabled: true`)
- [x] **Verified end-to-end against a Linux host** — enumerates as `BLUETOOTH HID v0.00 Gamepad`,
      creates `js0`; all 13 buttons and all 8 axes land on the intended evdev codes (see below)
- [x] **Verify on Windows** — pair, then check every control in `joy.cpl` → Properties
- [x] Tune the default layout against a real thumb once it has been held in landscape
- [x] Investigated the trailing descriptor byte — unavoidable, see Known constraints

## Iteration 2 — Configurable layouts

- [x] **Quick switcher** — the ☰ Menu pill's *Layouts* page lists `Layouts.ALL` and ticks the
      active one. New built-ins appear in it by being added to `ALL`, and inherit the layout-sanity
      tests for free
- [x] **One file per layout** — `input/layouts/`, with `Layouts.ALL` as the catalog. `XBOX_LAYOUT`
      derives its geometry from `DEFAULT_LAYOUT`, so tuning a position moves both
- [x] **Two presentations** — `LayoutStyle` is `Colors` (drawn shapes and labels, in the layout's
      own resting and pressed colours) or `Images` (a picture per control, from an `ArtPack`). A
      layout is in exactly one. See the README for what the modes share
- [x] **Art packs are their own thing** — `ArtPack` is `ControlId → Glyph`, named once on
      `LayoutStyle.Images` rather than written onto every `ControlSpec`, so a layout in image mode
      is geometry plus a pack. Built-ins live in `input/art/`; a new face plate is a pack file and
      a three-line layout
- [x] **Ships Default, Xbox and PS5.** The PS5 plate is Kenney's PlayStation prompts, laid out the
      way a DualSense is — D-pad up mirroring the face diamond and enlarged, left stick down level
      with the right — over derived geometry everywhere else. Its guide button has no art in the
      pack and falls back to a drawn *PS* shape
- [x] **Serialise `GamepadLayout`** (kotlinx.serialization). The data model needed no changes: a
      user layout is a variable-length list of `ControlSpec`s under a name, which is what this
      always was. An image layout writes one pack id rather than a picture per control, resolved
      through the new `ArtPacks` catalog; an id that is not installed throws rather than degrading.
      Colours are `#AARRGGBB`. Everything is wrapped in `{"version": 1, "layouts": [...]}` — always
      a list, so sharing one layout and saving the whole library are the same shape
- [x] **Persist with DataStore.** `data/LayoutStore` keeps the user's layouts and their choice of
      one. `DEFAULT_LAYOUT` is the fallback rather than a seeded row: the built-ins are compiled in,
      so first run writes nothing. Stored JSON that will not parse is reported as an empty library
      and **left where it is** — it is still the only copy of someone's work
- [x] **Remember the menu's layout choice across launches** — falls out of the above
- [x] **Editor screen** — drag to move, pinch to resize, snap-to-grid, and the two colours from a
      preset palette. `LayoutEdits` holds all of the arithmetic as plain Kotlin, so the editor is
      tested on the JVM and `EditorScreen` is only gestures. **Built-ins are read-only**; you make
      your own from empty or as a copy, and `LayoutLibrary.isEditable` is the one place that line
      is drawn
- [x] **Add / remove controls**, with `missingButtons()` surfaced as a caption in the editor.
      **Adding is pick-then-tap**: nothing is created until the drop point is known, with a preview
      following the finger, because a control's default position is almost never the wanted one. Any
      control can be placed **more than once** — the id says what a control does, its index says
      which one it is
- [x] **Control groups** — `ControlGroups.ALL` places several at once: the face diamond, a
      four-button D-pad, the centre three, and each shoulder pair side by side or stacked. A
      placement shortcut only; once dropped the members are ordinary controls, so a layout stays a
      flat list and the editor keeps one selection model
- [x] **A D-pad can be four separate buttons** as well as one cross, both sending one hat.
      `Hat.of` was already there and unused, so the router folds the held arms through it
- [ ] Editor extras that did not make the first cut: undo, moving a group as a unit after it has
      been placed, nudging a selection with arrows, and a real colour picker rather than twelve
      presets
- [x] **More built-in layouts** — **Switch** and **Steam Deck** both ship. As predicted, the
      plumbing was the easy part and Nintendo's A/B/X/Y crossing was the part to think about: it
      swaps *both* pairs, so the key drawn A sends what an Xbox pad's B sends. Unlike Xbox and PS5
      these two are **authored in full rather than derived** — the Pro Controller leans both lower
      controls inboard, the Deck rides high because its trackpads own the lower third, and a derived
      geometry would have made them the default pad with different pictures on it
- [ ] Further plates, now that the shape is a pack file and a layout file: GameCube, Wii U and the
      Switch 2 are all in the same Kenney download
- [ ] D-pad glyph lighting the direction being pushed rather than the whole cross — the directional
      art exists, but `drawControl` is told only whether the control is held, not which way. The
      four-button D-pad needs the same art and does not have it either: each arm is one control, so
      *it* knows its direction, but no `ControlIcon` names a single arm yet. One conversion run
      covers both
- [ ] Import / export layouts as JSON so they can be shared. The format exists and is versioned;
      what is left is the file picker and the share sheet. Two things to decide there: what to do
      with an incoming id that collides with a local layout (copies already take a fresh UUID, so
      only a re-import of the same file can), and how to report a refusal — decoding is
      all-or-nothing, so a missing art pack fails the whole file
- [ ] User-supplied art, which is the one change still queued for the format. It cannot go through
      `R.drawable` at all — it needs a file loaded at runtime, turning `ControlIcon` into a sealed
      `Builtin | File`, which is what `version` in the saved file is there for. `ArtPack` is the
      seam it arrives through: a hand-made layout names its own pictures by carrying its own pack
- [ ] Portrait layout variant — this is the real fix for the Android 16 orientation opt-out in
      `AndroidManifest.xml`, which stops working at targetSdk 37

## Iteration 3 — More hosts

- [ ] Verify against Android / Android TV (should map to `KEYCODE_BUTTON_*` unchanged)
- [x] Verify against Linux (`/dev/input/js0`) — done on x86 Ubuntu; Raspberry Pi still untried
- [ ] Verify against macOS
- [ ] Profile picker in the UI once there is more than one `GamepadProfile`
- [ ] Per-host default layout, remembered per bonded device

## Iteration 4 — Nintendo Switch

- [ ] Dedicated `GamepadProfile` with the report layout the Switch expects
- [ ] Adapter rename via the existing `requiredAdapterName` hook, restoring the old name on exit
- [ ] Document what the VID/PID limitation means in practice — Android's
      `BluetoothHidDeviceAppSdpSettings` exposes no vendor or product ID, so hosts that fingerprint
      controllers that way cannot be fully impersonated

## Polish backlog

Unordered; pull from here whenever.

- [ ] Analog triggers driven by slide distance or touch pressure (the descriptor already carries the
      full 0..255 range; only `TouchRouter` needs changing)
- [ ] Haptic feedback on button press, with a sensitivity setting
- [ ] Turbo / autofire and macro buttons
- [ ] Motion controls from the device IMU
- [ ] Measure end-to-end latency and revisit the 10 ms send interval with real numbers
- [ ] Adjustable control opacity and a few pad themes
- [ ] Handle host-initiated output reports if any target ever sends rumble or LED data
- [ ] Reconnect automatically to the last host on launch
- [ ] Instrumented test for the service's registration lifecycle
- [ ] Release signing config and a CI workflow

---

## Known constraints

Not bugs, and not fixable — recorded so they do not get rediscovered.

- **No XInput on Windows.** Bluetooth HID cannot reach XInput; the pad is a DirectInput device.
- **HID's X/Y are not Xbox's X/Y.** `BTN_X` aliases `BTN_NORTH` and `BTN_Y` aliases `BTN_WEST`, so
  the Xbox layout puts its Y key on `GamepadButton.WEST` and its X key on `NORTH`. Every art pack
  has to make the same decision for its own face plate — `PLAYSTATION_ART` puts triangle on `WEST`
  because triangle sits where Y does, not because both are named after a compass point. See Design
  decisions in the README.
- **No BLE.** Android blocks apps from registering the HID service UUID on its GATT server, so
  HID-over-GATT is unavailable.
- **No VID/PID control.** See Iteration 4.
- **Some OEM builds omit the HID Device profile** entirely. The app detects and reports this.
- **Hosts cache the report descriptor at pair time.** Reconnecting does not pick up a changed
  descriptor — the pairing has to be removed and redone on both ends. Worth remembering for every
  future profile change, since the symptom is a descriptor edit that appears to do nothing.
- **A trailing `0x00` is appended to the report descriptor in transit.** Confirmed by diffing
  `/sys/bus/hid/devices/*/report_descriptor` against `GenericHidProfile.descriptor`: our 93 bytes
  arrive byte-for-byte intact, with one extra `0x00` on the end. It is added by Android's SDP
  encoding or by BlueZ — not by us. Linux decodes it as a Main item with tag 0, logs
  `unknown main item tag 0x0`, skips it, and parses everything else correctly.

  Tested and unavoidable. The even-length theory was wrong: padding the descriptor to 94 bytes and
  re-pairing produced a 95-byte descriptor on the host, still with one trailing zero. It is an
  unconditional terminator, independent of our length, so the app cannot influence it. Since it
  applies to every app using `BluetoothHidDevice`, hosts in practice cope. Nothing to do.
