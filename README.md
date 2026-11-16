# Vermont

Vermont turns an Android phone into a Bluetooth gamepad. Play with the touch controls, or connect a
physical game controller to the phone and use it as input. Vermont forwards the resulting gamepad
state to a separately paired host over Bluetooth Classic HID.

- **Physical controllers:** Android gamepads that report standard key and joystick events, including
  many Xbox, PlayStation and Nintendo controllers. Bluetooth controllers pair through Android
  Settings; USB controllers connect through a USB-OTG adapter. Compatibility depends on Android's
  device driver and reported mappings.
- **Touch controls:** eight built-in layouts, plus custom layouts with editable controls, themes,
  opacity, and optional motion aiming.
- **Bluetooth output:** a generic HID gamepad with two sticks, two triggers, a D-pad, and buttons.

Vermont does not emulate a console-specific Bluetooth identity. A Nintendo Switch console, for
example, will not accept it as an official Switch controller. On Windows, it appears as DirectInput,
not XInput; Steam can remap it for games that only support XInput.

The Android application ID is `com.bennytech.vermont`. Android treats this as a different app from
older `com.blugaemand` builds, and does not migrate their saved layouts or settings automatically.

## Use

1. Install Vermont on an Android 9+ phone with Bluetooth.
2. To use a physical controller, connect it to the phone over Bluetooth in Android Settings or
   connect it over USB with an OTG adapter. Keep Vermont open on its pad screen.
3. In Vermont, open the connection pill and choose **Make discoverable to pair**.
4. Pair the phone from the host's Bluetooth settings. The host sees Vermont as a gamepad.
5. Press buttons or move sticks on the phone or the connected controller; Vermont sends those
   inputs to the paired host.

The Bluetooth permission is for Vermont's outgoing HID gamepad connection. USB controllers are
handled through Android's input-device system; Vermont does not need to claim the USB device
directly. The app combines touch and controller input. Non-neutral controller stick axes take
priority over touch movement on the same axis; held buttons and D-pad directions combine.

Android and controller vendors do not all report buttons and axes identically. Vermont uses Android's
standard gamepad key codes and joystick axes, so a controller whose firmware or Android driver uses
nonstandard mappings may have missing or differently mapped controls. The app does not currently
show a separate controller-connected indicator.

### Layouts and editing

Open **Menu → Layouts** to choose a built-in pad: Default, Xbox, PS5, Switch, Switch 2, Steam Deck,
Wii U, or GameCube. Create a blank layout or copy a built-in to edit it. The editor supports moving
and resizing controls, adding or removing controls, and placing multi-control groups as one plate.
User layouts and the selected layout are saved on the phone.

Motion aiming is optional and uses the phone's gyroscope. It is off by default. Some built-in art
uses Kenney's Input Prompts, licensed CC0; the license is included in `art/input/`.

## Build and test

Requirements: JDK 17 and an Android SDK with platform 36. Create `local.properties` in the project
root and point it at the SDK:

```properties
sdk.dir=/path/to/android-sdk
```

```bash
./gradlew assembleDebug  # build a debug APK
./gradlew test           # JVM unit tests
./gradlew lintDebug      # Android lint
./gradlew installDebug   # install on a connected Android device
adb logcat -s Vermont    # app logs
```

A physical phone is needed to verify Bluetooth HID and controller input; emulators do not provide
the required Bluetooth hardware. The automated tests cover the HID report encoder, input-state
combination, touch routing, layouts, editing, serialization, and motion calculations.

## Release signing

Copy `keystore.properties.sample` to `keystore.properties` and fill in all four values, or set the
corresponding `VERMONT_STOREFILE`, `VERMONT_STOREPASSWORD`, `VERMONT_KEYALIAS`, and
`VERMONT_KEYPASSWORD` environment variables. A partial set fails the build; with no values, release
builds are unsigned. The old `BLUGAEMAND_*` environment variable names remain accepted for existing
build setups.

Create a key with:

```bash
keytool -genkeypair -v -keystore vermont-release.jks -alias vermont \
        -keyalg RSA -keysize 4096 -validity 10000
```

Keep the key out of the repository and back it up. Losing it prevents updating an installed release
under the same app identity.

GitHub Actions runs unit tests, lint, and an APK build for pushes and pull requests to `main`.
Release builds are signed only when all four `RELEASE_*` repository secrets are configured.

## Technical notes

- **Minimum Android version:** API 28. Vermont uses Android's `BluetoothHidDevice` API, which lets
  the phone act as a Bluetooth Classic HID peripheral.
- **Input:** Android delivers supported Bluetooth and USB gamepads as `KeyEvent` and joystick
  `MotionEvent` input. Vermont maps those standard events to the same `GamepadState` used by the
  touch router.
- **Output:** the HID report contains six axes, an eight-way hat switch, and sixteen button slots.
  Reports are coalesced and sent at up to 100 Hz.
- **No BLE HID:** Android does not let ordinary apps register the HID-over-GATT service on its
  Bluetooth GATT server.
- **No XInput or console impersonation:** Bluetooth HID cannot provide Windows XInput, and Android
  does not expose the vendor/product identity needed to impersonate an official Switch controller.

`TODO.md` is the project backlog, including measured platform constraints and remaining work.
