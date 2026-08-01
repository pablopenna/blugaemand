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
- [ ] **Verify on Windows** — pair, then check every control in `joy.cpl` → Properties
- [ ] Tune the default layout against a real thumb once it has been held in landscape
- [x] Investigated the trailing descriptor byte — unavoidable, see Known constraints

## Iteration 2 — Configurable layouts

- [x] **Quick switcher** — the ☰ Menu pill's *Layouts* page lists `Layouts.ALL` and ticks the
      active one. New built-ins appear in it by being added to `ALL`, and inherit the layout-sanity
      tests for free
- [x] **One file per layout** — `input/layouts/`, with `Layouts.ALL` as the catalog. `XBOX_LAYOUT`
      derives its geometry from `DEFAULT_LAYOUT`, so tuning a position moves both
- [x] **Two presentations** — `LayoutStyle` is `Colors` (drawn shapes and labels, in the layout's
      own resting and pressed colours) or `Images` (a glyph per control, from an art pack). A layout
      is in exactly one. Ships **Default** and **Xbox**; see the README for what the modes share
- [ ] Serialise `GamepadLayout` (kotlinx.serialization); the data model is already normalised for
      it, and `ControlIcon` is an enum of names rather than resource IDs for this reason
- [ ] Persist with DataStore; ship `DEFAULT_LAYOUT` as the seeded default
- [ ] Remember the menu's layout choice across launches — it is session-only until the above lands
- [ ] Editor screen: drag to move, pinch to resize, snap-to-grid. Picking a layout's two colours
      belongs here too — they are data already, just not editable
- [ ] More built-in layouts to switch between; a Nintendo-style face plate is the obvious first one.
      The art for PlayStation, Switch, Steam Deck and others is in the same Kenney pack, so an
      image-mode layout is a geometry table plus a glyph table
- [ ] D-pad glyph lighting the direction being pushed rather than the whole cross — the directional
      art exists, but `drawControl` is told only whether the control is held, not which way
- [ ] Add / remove controls, and surface `missingButtons()` as a validation warning
- [ ] Import / export layouts as JSON so they can be shared. User-supplied art cannot go through
      `R.drawable` at all — it needs a file loaded at runtime, turning `ControlSpec.icon` into a
      sealed `Builtin | File`. The enum is the seam that would grow along
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
  the Xbox layout puts its Y key on `GamepadButton.WEST` and its X key on `NORTH`. Every future
  layout has to make the same decision for its own face plate. See Design decisions in the README.
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
