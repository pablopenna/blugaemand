# Blugaemand

Turns an Android phone into a real Bluetooth gamepad. The host sees a standard HID controller — no
driver, no companion app, no root.

**Current state:** a static Xbox-style layout (two sticks, D-pad, ABXY, four shoulder controls, the
three centre buttons, two stick clicks). Verified end-to-end against a **Linux** host; Windows is
the stated target but is **not yet tested**. Configurable layouts and additional hosts are planned.

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
| `input/` | yes | `ControlSpec`, `GamepadLayout`, `ResolvedLayout`, `TouchRouter` |
| `ui/` | no | `GamepadScreen`, `ControlRenderers`, `ConnectionBar`, `theme/` |

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

- `ControlSpec` / `GamepadLayout` — a layout as plain data in normalised coordinates. `GamepadLayout`
  is a `List<ControlSpec>`, nothing more.
- `ResolvedLayout` — converts a layout to pixels once per size change. Both the renderer and
  hit-testing read from it, so what is drawn is exactly what is touchable.
- `TouchRouter` — owns `pointerId → control` bindings and produces a `GamepadState`.

**`ui/`** — `GamepadScreen` takes the layout as a parameter and never reads a global, which is what
makes the planned layout editor a matter of passing a different instance.

### The two seams

Everything on the roadmap goes through one of these:

- **`GamepadProfile`** — supporting a fussier host (the Switch) means adding an implementation, not
  threading special cases through the service.
- **`GamepadLayout`** — making layouts user-editable means serialising it and building an editor.

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

Dependency versions are pinned deliberately (AGP 8.13.2, Kotlin 2.0.21, Compose BOM 2024.09.00) to
what resolves from the local Gradle cache. Lint's "newer version available" family is disabled in
`app/build.gradle.kts` because it is pure noise here.

### Tests

`app/src/test/` covers the encoder, descriptor structure, and touch routing on the JVM:

- `GenericHidProfileTest` — exact report bytes, per-button bit positions, every hat direction, axis
  clamping, and a walk of the descriptor's item stream verifying it is structurally sound and
  declares exactly 9 bytes.
- `TouchRouterTest` — pointer binding and release, multitouch independence, stick normalisation and
  circular clamping, D-pad sectors, and layout sanity (no overlaps, every button reachable).

---

## Pairing

1. Launch the app and grant the nearby-devices permission. The status pill should read
   **Ready to pair**. If it says *Not supported on this phone*, stop — see Troubleshooting.
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

## Troubleshooting

| Symptom | Cause |
|---|---|
| *Not supported on this phone* | Some manufacturer builds ship without the HID Device profile. Check with `adb shell getprop bluetooth.profile.hid.device.enabled`. No workaround; the app detects it rather than failing silently. |
| *Permission needed* | The nearby-devices permission is not granted. The panel offers **Grant permission**. |
| *Bluetooth is off* | The adapter is disabled. Apps cannot enable it themselves — `BluetoothAdapter.enable()` has been a no-op for ordinary apps since Android 13 — so the panel offers **Turn on Bluetooth**, which raises the system prompt. |
| Pairs but never connects | Almost always one of the two pairing gotchas above. Remove the pairing on the host and redo it with the app already showing **Ready to pair**. |
| Buttons stick down after switching apps | Should not happen — the pad sends a neutral report on focus loss and on opening the panel. If you see it, it is a bug. |
| Changed the HID descriptor but the host still sees the old one | Hosts cache the SDP record at pair time, and reconnecting does not refresh it. Remove the pairing on **both** ends and pair again. On Linux, `bluetoothctl remove <mac>` clears BlueZ's cache in `/var/lib/bluetooth/<adapter>/cache/`. |
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

**The development phone is a Xiaomi (HyperOS), which restricts adb in three ways:**

| Blocked | Consequence |
|---|---|
| `adb install` without *Install via USB* | `INSTALL_FAILED_USER_RESTRICTED`. Enable it in Developer options; `pm install` from the shell does not get around it. |
| `pm grant` | `SecurityException`. Runtime permissions must be granted by tapping the dialog. |
| `input tap` / input injection | `INJECT_EVENTS` denied, so the UI cannot be driven from a script. Anything requiring a touch has to be done by hand. |

**Verification techniques that have proved useful:**

- `/proc/bus/input/devices` capability bitmaps semantically validate the descriptor with no root and
  no button pressing — they show exactly which `BTN_*` and `ABS_*` codes the kernel derived.
- `/sys/bus/hid/devices/<id>/report_descriptor` is world-readable and lets you diff the descriptor
  the host received against the bytes in `GenericHidProfile`.
- `/dev/input/js0` is world-readable; the `js_event` struct is
  `{u32 time, s16 value, u8 type, u8 number}`, and `type & 0x80` marks the synthetic startup state.
- `art/generate-launcher-icons.py` doubles as an example of validating assets programmatically.

**Traps already hit once, worth not repeating:**

- **Cleanup after a suspension point in a keyed `pointerInput` does not run** when the key changes —
  and a gesture whose success changes that key cancels itself. Put anything that must happen in a
  `finally`, or drive it from a `LaunchedEffect`. This caused two bugs in the hold-to-open pill.
- **`Modifier.then(...)` crashes lint** (`SuspiciousModifierThenDetector`). Restructure rather than
  suppress; a single `pointerInput` handling both branches was cleaner anyway.
- **Adaptive icons need the `-v26` qualifier.** Lint's `ObsoleteSdkInt` suggests folding
  `mipmap-anydpi-v26` into `mipmap-anydpi`, but AAPT2 then fails to link. Suppressed in
  `app/lint.xml`.

**The launcher icon** is pixel art. Source is `art/icon.aseprite`; export to `art/icon.png`
(54×54, RGBA) and run `python3 art/generate-launcher-icons.py`. The 54×54 canvas is not arbitrary —
it is the convenient size that upscales to all five adaptive-icon buckets (108/162/216/324/432 px)
on whole pixels, so every export is nearest-neighbour and the pixel grid stays square. Pre-scaling
also avoids Android's bilinear filtering, applied both when decoding into a mismatched density
bucket and again when drawing. The monochrome layer is *derived*, not copied: themed icons use only
the alpha channel, and the art's alpha is a solid square.
