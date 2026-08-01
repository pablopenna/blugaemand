# Blugaemand

Turns an Android phone into a real Bluetooth gamepad. The host sees a standard HID controller — no
driver, no companion app, no root.

Current state: a static Xbox-style layout (two sticks, D-pad, ABXY, four shoulder controls, the
three centre buttons, two stick clicks), verified against Windows. Configurable layouts and
additional hosts are planned; see [TODO.md](TODO.md).

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

Two decisions in there are worth knowing about, both in
[`GenericHidProfile.kt`](app/src/main/java/com/blugaemand/hid/GenericHidProfile.kt):

- **Triggers live on the Simulation Controls page** (`Brake` / `Accelerator`) rather than as generic
  axes. That is the convention DualShock and Xbox Bluetooth controllers follow, and it is what makes
  Windows and Android map them to trigger axes without per-device configuration.
- **Button numbering is not sequential** — it follows the order Linux's `hid-input` driver assigns
  to a gamepad collection (`BTN_SOUTH`, `BTN_EAST`, `BTN_C`, `BTN_NORTH`, …), which Android
  inherits. Windows numbers buttons positionally and does not care, so matching the convention costs
  nothing today and means Linux and Android hosts label the buttons correctly for free later.

### Known limitation: DirectInput, not XInput

On Windows the phone enumerates as a **DirectInput** gamepad. It will **not** appear as an XInput
("Xbox controller") device, because XInput is a kernel-driver interface that Bluetooth HID cannot
reach — no app can work around this. In practice:

- ✅ Steam, emulators, most game engines, anything reading raw HID or DirectInput
- ❌ The minority of games that speak only XInput

Steam's controller support will happily remap it if a game is XInput-only.

---

## Building

Requires JDK 17 and an Android SDK with platform 36. Point `local.properties` at your SDK:

```properties
sdk.dir=/path/to/android-sdk
```

```bash
./gradlew assembleDebug     # build
./gradlew test              # JVM unit tests, no device needed
./gradlew lintDebug         # lint
./gradlew installDebug      # install onto a connected phone
```

The encoder, descriptor and touch routing have no Android dependencies and are covered by plain JUnit
tests, so most logic is verifiable without hardware.

---

## Pairing with Windows

1. Launch the app and grant the nearby-devices permission. The status pill at the top should read
   **Ready to pair**. If it says *Not supported on this phone*, stop here — see Troubleshooting.
2. Tap the pill, then **Make discoverable to pair**.
3. On Windows: **Settings → Bluetooth & devices → Add device → Bluetooth**, and pick the phone.
4. Accept the pairing code on both ends. Windows then initiates the HID connection and the pill
   turns green.

Two gotchas that account for most first-time failures:

- **Register before pairing.** The class-of-device Windows records at pair time is what tells it
  this is a gamepad. If the app was not registered, Windows files the phone as a phone and never
  opens the HID connection. Remove the device on the Windows side and pair again with the app
  running.
- **A phone previously paired as a phone must be removed first.** Windows caches the old service
  list, so re-pairing from scratch is the fix.

### Verifying it works

Press `Win+R`, run `joy.cpl` → the device appears under Game Controllers. Open **Properties** for a
live axis and button panel: every stick, trigger, D-pad direction and button should move its
indicator. That panel is reading the descriptor directly, so if it looks right, the wire format is
right.

For a stricter check, open Steam's controller configuration — it validates descriptors more
aggressively than `joy.cpl` does.

---

## Troubleshooting

| Symptom | Cause |
|---|---|
| *Not supported on this phone* | Some manufacturer Android builds ship without the HID Device profile even on supported versions. There is no workaround; the app detects this rather than failing silently. |
| *Bluetooth off or not permitted* | Turn Bluetooth on, grant the nearby-devices permission, then tap **Retry**. |
| Pairs but never connects | Almost always one of the two gotchas above. Remove the pairing on the host and redo it with the app already showing **Ready to pair**. |
| Buttons stick down after switching apps | Should not happen — the pad sends a neutral report on focus loss. If you see it, it is a bug worth reporting. |

To watch reports leave the phone without any host involved:

```bash
adb logcat -s Blugaemand
```

---

## Layout of the code

```
hid/     GamepadState, the GamepadProfile interface, GenericHidProfile, HidGamepadService
input/   ControlSpec / GamepadLayout (normalised data), ResolvedLayout (pixels), TouchRouter
ui/      GamepadScreen (canvas + multitouch), ControlRenderers, ConnectionBar
```

The launcher icon is pixel art. Source lives in `art/icon.aseprite`; export it to `art/icon.png`
(54×54, RGBA) and run `python3 art/generate-launcher-icons.py` to regenerate the density assets.

The 54×54 canvas is not arbitrary: it is the only convenient size that upscales to all five
adaptive-icon buckets (108/162/216/324/432 px) on whole pixels, so every export can be nearest
-neighbour and the pixel grid stays square. Pre-scaling also avoids Android's own bilinear filtering,
which it applies both when decoding a bitmap into a mismatched density bucket and again when drawing
it.

Two seams carry the planned work:

- **`GamepadProfile`** is everything host-specific — SDP metadata, descriptor, report encoding.
  Supporting a fussier host means adding an implementation, not threading special cases through the
  service.
- **`GamepadLayout`** is plain data in normalised coordinates. Making layouts user-editable means
  serialising it and building an editor; `GamepadScreen` already takes the layout as a parameter and
  never reaches for a global.
