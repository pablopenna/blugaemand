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
- [x] 34 JVM unit tests; lint clean
- [ ] **Verify on a physical phone** — install, confirm the status reaches *Ready to pair*
- [ ] **Verify on Windows** — pair, then check every control in `joy.cpl` → Properties
- [ ] Tune the default layout against a real thumb once it has been held in landscape

## Iteration 2 — Configurable layouts

- [ ] Serialise `GamepadLayout` (kotlinx.serialization); the data model is already normalised for it
- [ ] Persist with DataStore; ship `XBOX_DEFAULT` as the seeded default
- [ ] Editor screen: drag to move, pinch to resize, snap-to-grid
- [ ] Multiple named layouts with a quick switcher
- [ ] Add / remove controls, and surface `missingButtons()` as a validation warning
- [ ] Import / export layouts as JSON so they can be shared
- [ ] Portrait layout variant — this is the real fix for the Android 16 orientation opt-out in
      `AndroidManifest.xml`, which stops working at targetSdk 37

## Iteration 3 — More hosts

- [ ] Verify against Android / Android TV (should map to `KEYCODE_BUTTON_*` unchanged)
- [ ] Verify against Linux / Raspberry Pi (`/dev/input/js0`, `jstest`, `evtest`)
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
- **No BLE.** Android blocks apps from registering the HID service UUID on its GATT server, so
  HID-over-GATT is unavailable.
- **No VID/PID control.** See Iteration 4.
- **Some OEM builds omit the HID Device profile** entirely. The app detects and reports this.
