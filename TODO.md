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
- [x] **A group can be one control** — `ControlSpec.Shape.Cluster`, offered as an *As one control*
      switch on the group page. `ControlId.Dpad` was already a control resolving a touch to an
      output from where inside itself it landed, so this generalises it rather than inventing
      anything, and every one of the seven arrangements clusters — the shoulder pairs were the ones
      that made a plate have to be more than a diamond of circles. The member a touch means is the
      one whose own area it is in, else the nearest centre: pure nearest splits at the midpoint
      between centres and would put the line inside the middle of the centre cluster's Home glyph.
      **Art needed no change at all** — a plate draws as its members and a member carries an
      ordinary `ControlId`, which is what `ArtPack` is keyed by. The part worth remembering is that
      everything inside a plate is a fraction of the layout unit, offsets included: in screen
      fractions a diamond stretches vertically off 16:9 exactly as a loose group does, and in unit
      fractions it is rigid and resizing collapses to one factor. *Ungroup* is the way back out
- [x] **A D-pad can be four separate buttons** as well as one cross, both sending one hat.
      `Hat.of` was already there and unused, so the router folds the held arms through it
- [x] **Pick the art pack from the editor** — the *Colours* page becomes *Appearance*: *Shapes and
      colours* plus every pack in `ArtPacks.ALL`, with the swatches under a rule while colours mode
      is in force. Unconditional now, where the old row vanished the moment a layout took a pack.
      `ArtPack` gained a display `name` for the list; the format writes the id and is unaffected.
      Leaving a pack restores the colours the layout last had, held for as long as the editor is
      open rather than saved
- [x] **The layout menu shows its two halves** — `LayoutLibrary.builtIn` and `.user` rendered as two
      runs separated by a rule, each row marked with a Kenney joystick, plain for built-in and red
      for yours. `MenuPanel` takes the whole library rather than a list, which is also what let
      `canEdit` stop being threaded down from `MainActivity`. The shared `PanelDivider` is used
      again under the group page's *As one control* switch
- [x] **Editor extras** — the two that were worth having. **Nudge arrows** sit beside the ☰ Edit
      pill whenever something is selected, and move it by a whole grid cell with snapping on or a
      fifth of one with it off; `ResolvedLayout.nudgeStep` is the one new piece of arithmetic and it
      is derived from `gridStep` rather than stated. They are *beside* the pill and not on a panel
      page because the panel covers the part of the pad the control being nudged may well be under.
      **A real colour picker** replaces the twelve presets: a saturation/value square over a hue bar,
      with the two colours as rows above it that pick which one it is aimed at — one picker rather
      than two, since a 240 dp panel has room for one square. The conversions are plain Kotlin in
      `ui/ColorMath.kt` and JVM-tested; the picker holds a hue of its own, because black and every
      grey have none and a colour dragged to the bottom of the square would otherwise come back red.
      **Undo was dropped on purpose** — coalescing it is the whole job (a drag is one step, not one
      per frame) and that is a stack, a rule about when to clear it, and a hook on every edit site,
      for actions that are all one tap to reverse by hand. *Moving a group as a unit after it has
      been placed* was already off this list — a cluster is that, and more directly than a second
      selection model over loose controls would have been
- [x] **More built-in layouts** — **Switch** and **Steam Deck** both ship. As predicted, the
      plumbing was the easy part and Nintendo's A/B/X/Y crossing was the part to think about: it
      swaps *both* pairs, so the key drawn A sends what an Xbox pad's B sends. Unlike Xbox and PS5
      these two are **authored in full rather than derived** — the Pro Controller leans both lower
      controls inboard, the Deck rides high because its trackpads own the lower third, and a derived
      geometry would have made them the default pad with different pictures on it
- [x] **Further plates — GameCube, Wii U and Switch 2 all ship.** The prediction held: a plate is a
      pack file and a layout file, and nothing outside those two and the icon enum had to change.
      Three things worth keeping from doing it:

      - **The Switch 2 is a pack stated as a difference.** Of the 112 pictures across Kenney's two
        Switch folders, **105 are byte-identical files** — only ZL and ZR were redrawn, plus the new
        C, GL and GR, which the profile has no slot for. So `SWITCH2_ART` is `SWITCH_ART.glyphs`
        with two entries replaced and owns four `ControlIcon` names rather than thirty, and
        `SWITCH2_LAYOUT` derives from `SWITCH_LAYOUT` outright. The `PS_`/`PS5_` split, pushed one
        step further: there the two pads differed in more than a picture
      - **`art/input/` is flat and Kenney's download is not.** Both Switch folders ship a
        `switch_button_zl.svg`, and they are *different pictures*, so copying both in would have
        left whichever landed second. The four redrawn triggers came in renamed `switch2_*` — the
        one place a file here does not match its name upstream. Worth checking for whenever art
        from a new console folder is added
      - **`LayoutSerializationTest` used `gamecube` as its example of a pack that is not
        installed**, which stopped being an example the moment the plate landed. It now says
        `tomato`. Nothing named after a console was ever safe there
- [ ] Further plates still in the same download, if they are ever wanted: Steam Controller, Steam
      Frame, Nintendo Wii, Meta Quest, Valve Index and Playdate. None was skipped for a reason —
      the three named above were simply the three the backlog asked for
- [x] **The one-piece cross lights the arm being pushed.** `TouchRouter.dpadPush` returns the same
      `Hat` the host is being sent — the same arithmetic rather than a second opinion about the
      sector boundaries, since a cross lighting an arm it is not sending would be worse than one
      lighting all four — and `ArtPack.dpadArms` maps a direction to a picture. Two answers Kenney's
      art cannot give directly are settled in `ArtPack` rather than left to each caller: a
      **diagonal** has no picture and falls back to the cross lit whole, and the **dead zone** draws
      the *resting* cross, because a thumb there is touching the control and sending nothing
- [ ] **The four-button D-pad and the four-arm plate still draw arrows, and the reason is not the
      one this item assumed.** The premise above was that the directional art exists and only needed
      wiring. It does not: **every D-pad picture Kenney ships is a whole cross** — unlit, one arm
      lit, one axis lit, all lit — and there is no picture of an arm on its own, in any of the seven
      packs. That is what makes those pictures right for the one-piece cross, where the cross *is*
      the control, and wrong for an arm placed on its own:

      - a **four-button D-pad** would wear four complete crosses, one per arm
      - a **four-arm plate** — the case this item expected to be the one that worked — would draw a
        cross made of crosses, which is the worst of the three rather than the best

      So the arm pictures went into `ArtPack.dpadArms`, keyed by direction, rather than into
      `glyphs` under `ControlId.DpadButton` where a loose arm and every plate member would pick them
      up. **`LayoutArtTest`'s `filterNot` stays**, with the finding written beside it, and a test
      pins that no pack answers for a `DpadButton` so this cannot drift back. What is actually
      needed is art of an *arm*, which means drawing it rather than converting it — a much bigger
      job than a conversion run, and worth deciding on rather than assuming
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
- [ ] Profile picker in the UI once there is more than one `GamepadProfile` — **nothing forces this
      now**: the Switch profile was the second one, and Iteration 4 is closed. Every remaining host
      reads the descriptor, so `GenericHidProfile` serves all of them
- [ ] Per-host default layout, remembered per bonded device

## Iteration 4 — Nintendo Switch — **CLOSED, not possible**

**Stage 0 answered it: the console rejects the phone on its vendor and product IDs, which no
Android API can set.** It bonds and then unpairs without ever opening the HID channel, with every
other field in the SDP record matching a real Pro Controller byte for byte. The conclusion is in
*Known constraints*; Stages 1–3 below were never started and are kept only for the reasoning.

Everything under Stage 0 is a measurement against a real console and a real third-party pad, so it
is worth reading before anyone reopens this. Two of its findings contradict the obvious guesses —
class of device is not the gate, and the subcommand callback does fire.

**The Switch does not accept HID gamepads. It accepts controllers it recognises.** Every other host
on the roadmap reads the descriptor and believes it; this one wants a Pro Controller, which means a
vendor-defined report format and a subcommand handshake, not another descriptor. So this is
impersonation, and the first question is not how to build it but whether stock Android can reach the
starting line at all: `BluetoothHidDeviceAppSdpSettings` exposes name, description, provider,
subclass and descriptor, and **nothing that carries a vendor or product ID**. Stage 0 exists to find
that out cheaply, before any of the rest is written.

### Stage 0 — Does it get to the starting line?

Throwaway work. Every later stage is gated on it.

- [x] **Read the reference controller first.** Done, against a Manba pad in Switch mode from a
      BlueZ host. **It came out on the impersonation branch:** Nintendo's `057E:2009`, the name
      `Pro Controller`, and Nintendo's SDP strings — so impersonation is the price of entry and the
      Android gap is real. Everything it advertises:

      | Property | Value |
      |---|---|
      | Modalias | `usb:v057Ep2009d0001` — VID `0x057E` Nintendo, PID `0x2009` Pro Controller, ver `0x0001` |
      | Bluetooth name | `Pro Controller` while advertising (reverts to the vendor's own name once connected) |
      | Class of Device | `0x002508` — Peripheral / Gamepad |
      | SDP strings | name `Wireless Gamepad`, description `Gamepad`, provider `Nintendo` |
      | UUIDs | HID `0x1124`, PnP Information `0x1200` |
      | HID SDP attrs | subclass `0x08`, country `0x21`, parser `0x0111`, VirtualCable + ReconnectInitiate + BatteryPower + RemoteWake true, NormallyConnectable false, BootDevice false, supervision timeout `0x0C80` |

      **The descriptor did not come from `/sys/bus/hid/devices/<id>/report_descriptor`** as this
      plan assumed. BlueZ pairs and holds the ACL link but never forms a HIDP session, so no HID
      device is created and the pad never becomes an input device on Linux at all — which is also
      why Steam does not see it. SDP attribute `0x0206` carries the same bytes and is the better
      source anyway: it is what a host caches at pair time, upstream of the kernel. Read it with
      `sdptool browse --raw <addr>`. The 171 bytes, for Stage 2 to be written against:

      ```
      05 01 09 05 a1 01 06 01 ff 85 21 09 21 75 08 95 30 81 02 85 30 09 30 75 08 95 30 81 02
      85 31 09 31 75 08 96 69 01 81 02 85 32 09 32 75 08 96 69 01 81 02 85 33 09 33 75 08 96
      69 01 81 02 85 3f 05 09 19 01 29 10 15 00 25 01 75 01 95 10 81 02 05 01 09 39 15 00 25
      07 75 04 95 01 81 42 05 09 75 04 95 01 81 01 05 01 09 30 09 31 09 33 09 34 16 00 00 27
      ff ff 00 00 75 10 95 04 81 02 06 01 ff 85 01 09 01 75 08 95 30 91 02 85 10 09 10 75 08
      95 30 91 02 85 11 09 11 75 08 95 30 91 02 85 12 09 12 75 08 95 30 91 02 c0 00
      ```

      Decoded: one Game Pad application collection holding vendor-defined page `0xFF01`. Input
      reports `0x21` and `0x30` are 48 bytes, `0x31`/`0x32`/`0x33` are 361; output reports `0x01`,
      `0x10`, `0x11`, `0x12` are 48 each. **Report `0x3F` is the only one a driverless host can
      read** — 16 one-bit buttons, a 4-bit hat, 4 bits padding, then X/Y/Rx/Ry as 16-bit `0..65535`.
      Three things worth carrying into Stage 2: the hat uses the **same null-state flag** (`81 42`)
      `GenericHidProfile` does; there are **no trigger axes at all**, confirming the ZL/ZR-go-digital
      item below; and the descriptor **ends `c0 00`**, a trailing zero after End Collection, the same
      artefact *Known constraints* records for ours
- [x] **Tried it from *Change Grip/Order*. It is the middle outcome: pairs, then drops** — and the
      shape of the failure is more informative than the outcome. Measured with the probe on, the
      adapter named `Pro Controller` and the SDP strings reading `Wireless Gamepad` / `Gamepad` /
      `Nintendo`:

      - **The console finds the phone and initiates by itself.** No *Make discoverable* needed; the
        pairing dialog appeared unprompted, naming `Nintendo Switch` (`DC:68:EB:1A:2B:76`).
        **So the class-of-device theory was wrong** — major class *Phone* does not keep the phone
        out of the candidate list
      - **The bond completes.** Across thirteen attempts the state machine went `11` (bonding) →
        `12` (**bonded**, seven times) → `10` (none). It is torn down every time, and the console
        immediately retries, which is the loop the user sees
      - **The HID channel is never opened.** `HidGamepadService` logged no connection-state change
        at all through the whole run — the console bonds, decides against the device, and unpairs
        without ever reaching PSM 17. So `onInterruptData` firing is real but out of reach: the
        handshake never gets a chance to start

      **The rejection therefore happens after bonding and before HID, which means it is read out of
      SDP.** Two candidates, and only one of them is ours to change: the DeviceID record, which
      still says Xiaomi `038F:0000` because no API reaches it, or the HID report descriptor, which
      is `GenericHidProfile`'s Xbox-style one rather than a Pro Controller's

- [x] **Put the reference descriptor in. It changed nothing.** Attribute `0x0206` verified on the
      wire as 171 bytes identical to the reference pad's, trailing zero included — and the console
      behaved exactly as before: bonded twice, tore it down five times, never opened the HID
      channel. So the descriptor was not what it was reading

- [x] **Gate tripped. Iteration 4 is closed.** Every field we can set now matches a real
      Pro Controller and the console still rejects the phone before HID, which leaves the DeviceID
      record — and no API reaches it. The finding is in *Known constraints*; the rest of this
      section is kept as the record of how it was established, because it is a measurement rather
      than a guess and re-running it costs a console and an evening

      The build was ready as — `SwitchProbeProfile`
      and a *Switch probe — scratch* section at the bottom of the connection panel, verified
      end to end against a BlueZ host:
      - *Impersonate Pro Controller* swaps to `SwitchProbeProfile`, which is `GenericHidProfile`'s
        descriptor unchanged under a real pad's SDP identity — `Wireless Gamepad` / `Gamepad` /
        `Nintendo`, all three confirmed on the wire — and renames the adapter to `Pro Controller`.
        The previous name is restored when the profile is released
      - *Scan for a host to connect to* runs an inquiry and lists what it finds, so a console that
        has never been bonded can be **connected to rather than waited for**. This is the one
        untested way around a class-of-device filter: if the console accepts an inbound connection
        instead of only offering what its own inquiry returned, the filter never runs
      - Remember to *Make discoverable to pair* as well, since the passive direction needs it
- [x] **Probe deleted.** `SwitchProbeProfile`, the panel's probe section, `SwitchProbe`,
      `useProfile`, `startScan`/`stopScan`/`discovered` and the inquiry receiver are all gone —
      336 lines, and the findings above are the only thing that needed to survive. The
      `onInterruptData` override went with them: it proved the callback fires, which is recorded,
      and with the iteration closed nothing would have consumed it.

      **One thing was kept deliberately:** `BLUETOOTH_SCAN` still asserts
      `android:usesPermissionFlags="neverForLocation"`. It is true of this app, and it keeps it out
      of the location-permission association entirely — worth having on its own merits, quite apart
      from the inquiry that first needed it
- [x] **`onInterruptData` fires.** The one Stage 0 answer that came back *for* the iteration. The
      override is in `hidCallback` and logs only. Both probes landed:

      ```
      onInterruptData: reportId=0x01 data=de ad be ef
      onInterruptData: reportId=0x10 data=01 02 03
      ```

      Android splits the report ID out of the payload for us, and **`0x10` arrived even though the
      descriptor never declares it** — nothing is filtered against the descriptor, which is what a
      subcommand handshake needs. The phone answered on the same link with
      `a1 01 80 80 80 80 00 00 08 00 00`: HIDP DATA/INPUT, report 1, sticks centred, hat `0x08`

      **How to poke it, because none of this is obvious.** Be a HID host over raw L2CAP rather
      than going through `/dev/hidraw`, which is root-only: connect PSM 17 then 19 and send
      `0xA2 <reportId> <payload…>` (`0xA0` DATA | `0x02` OUTPUT). Two traps, both of which cost
      an hour here:
      - **The socket needs `BT_SECURITY_HIGH`** (`setsockopt(SOL_BLUETOOTH=274, BT_SECURITY=4, …)`)
        or the connect fails `EACCES` — as root too, so it reads like a capability problem and is
        not one. The kernel requires an authenticated link for the HID PSMs
      - **A one-sided bond fails silently.** The phone had dropped its bond while BlueZ still held
        a link key, so the phone refused authentication *without ever showing a pairing dialog*,
        and the connect timed out. `bluetoothctl remove <phone>` and pair again from the phone.
        Both sides must be clean; only the host side shows a prompt when they are not
- [x] Read back what Android actually published in SDP, from a Linux host. Done with
      `sdptool browse --raw <phone>` while the app was registered — BlueZ's cache under
      `/var/lib/bluetooth/` needs root and is stale anyway, and the live browse is the same data.
      Note the phone answers SDP only over a **BR/EDR** link: `bluetoothctl connect` picks LE and
      `sdptool` then fails with *No route to host*, so force the classic link first with
      `dbus-send --system --dest=org.bluez /org/bluez/hci0/dev_<addr> org.bluez.Device1.ConnectProfile string:00001124-0000-1000-8000-00805f9b34fb`.
      **Both answers are bad, and neither is fixable from the app:**

      - **There is a DeviceID record, and it is the phone's.** Record handle `0x10002`, not in the
        public browse group, so `sdptool browse` misses it and `sdptool search 0x1200` is needed:
        VendorIDSource `0x0001` (Bluetooth SIG), VendorID `0x038F` (Xiaomi), ProductID `0x0000`,
        Version `0x0000`. One system-wide record, published by the stack, with nothing in
        `BluetoothHidDeviceAppSdpSettings` reaching it. Even the **namespace** is wrong: the
        Pro Controller's is USB-sourced (`usb:v057Ep2009`), so a console matching on
        `usb:057E:2009` sees `bluetooth:038F:0000`
      - **`subclass` does not touch the Class of Device.** With the app registered and advertising
        the phone still inquires as `0x5A420C` — major **Phone**, minor Smartphone, BlueZ icon
        `phone`. The Pro Controller is `0x002508`, major **Peripheral**, minor **Gamepad**. The
        `subclass` byte lands only in SDP attribute `0x0202`, and CoD is the adapter's, set by the
        stack. If the console filters its inquiry results by CoD — which is how every *Change
        Grip/Order* screen appears to work — the phone is never a candidate, and no SDP field can
        change that

- [x] **`subclass` was wrong, and is now `0x08`.** Verified on the wire: attribute `0x0202` reads
      `UINT8 0x08`, and the phone still accepts both HID channels and answers with its usual
      `a1 01 80 80 80 80 00 00 08 00 00`. A Linux host still enumerates it unchanged — `js0`, all
      eight axes with the triggers on `ABS_BRAKE`/`ABS_GAS`, all sixteen buttons — so nothing
      regressed. As expected it changed nothing else either: the phone still inquires as
      `0x5A420C`, major Phone, so this only stops us contradicting ourselves in the one field we
      control. Whether the console cares is still open. The reasoning, for the record:
      the HID profile defines attribute `0x0202` as *the low byte of the Class of Device*, where
      the device type sits at bits 5-2, so the Pro Controller's `0x08` is `0b000010 << 2`, gamepad.
      `BluetoothHidDevice.SUBCLASS2_GAMEPAD` is `0x02` — the same nibble **unshifted** — and the
      stack writes it verbatim, so we published device type `0b0000`, *unspecified*. The
      neighbouring `SUBCLASS1_*` constants are positioned correctly for a CoD (`KEYBOARD` `0x40`,
      `MOUSE` `0x80`) and are meant to be OR'd with one of these, which only works if these are
      shifted too; they are not. **Do not use the `SUBCLASS2_*` constants here.** Windows and Linux
      never noticed the old value because both read the descriptor instead
- [x] **Gate.** If the Switch will not open a connection to a device with no Nintendo VID/PID, stop
      here: move the finding into *Known constraints*, replacing the "No VID/PID control. See
      Iteration 4." forward-reference with the answer, and close the iteration. The reference
      controller is what makes this a measurement rather than a guess

### Stage 1 — What the seam needs before a Switch profile fits through it — *never started*

- **An output-report hook on `GamepadProfile`** — `handleOutput(reportId, data): List<ByteArray>`
  or similar, defaulting to empty so `GenericHidProfile` is unaffected — with
  `HidGamepadService` routing `onInterruptData` and `onSetReport` into it. Replies go out on the
  existing `reportExecutor` rather than a thread of their own
- **Decide how a stateful profile lives behind an interface built for an `object`.** A Pro
  Controller starts in simple mode (`0x3F`) and only moves to full mode (`0x30`) when the host's
  subcommand says so, so `ProControllerProfile` is a `class` and `encode` stops being pure
- **Decide what `sendIfChanged` does when every report differs.** The standard report carries an
  incrementing timer byte, so the dedupe stops suppressing anything — either the profile declares
  whether dedupe applies or the send loop asks it. Worth recording the reasoning: coalescing is a
  deliberate decision the README documents, and this is the first thing that needs it off

### Stage 2 — The Pro Controller protocol — *never started*

- `hid/ProControllerProfile.kt` — vendor-defined descriptor, the `0x3F` simple and `0x30` full
  input reports. Pure Kotlin, JVM-tested beside `GenericHidProfileTest`. Written against the
  reference controller's dumped descriptor, and **Linux compatibility is explicitly not a goal
  here** — that is what frees this one to be a byte-for-byte impersonation instead of a
  compromise, and why the `hid-input` button order that shapes `GamepadButton` does not apply
- The subcommand handshake over output report `0x01`, each answered by a `0x21` report carrying
  an ACK: `0x02` device info, `0x03` input report mode, `0x08` shipment state, `0x30` player
  lights, `0x40` IMU enable, `0x48` vibration enable, `0x38` home light
- `0x10` SPI flash reads answered with plausible factory stick calibration and body colours. The
  console reads these before it will treat the pad as usable
- Out of scope and already on the polish backlog: motion, rumble. Also out: NFC, Joy-Con pairs

### Stage 3 — The app around it — *never started*

- **Profile picker in the UI.** The Iteration 3 item; a second profile is what finally forces it.
  `HidGamepadService.profile` is a hardcoded `GenericHidProfile` today
- **Adapter rename** through `requiredAdapterName`, restoring the previous name on exit. This
  renames the phone's Bluetooth adapter globally and visibly, so it needs a story for the app
  being killed mid-session, not only for a clean exit
- **`GamepadButton.CAPTURE` on HID 16.** 3, 6 and 16 are skipped to keep 1..15 aligned with
  `hid-input`'s order, and appending a 14th entry disturbs nothing — there is no entry after it
  to push out of place. It is sent on every profile: the number is read only by
  `GenericHidProfile`, since the Switch profile has its own bit layout, so masking it off there
  would be a special case in the most safety-critical file in the repo bought for nothing — and a
  control that visibly does nothing is worse for whoever placed it than one another host can bind
- **The two consequences of a 14th button**, neither avoided by Capture being a Switch control,
  because both are about the shared `GamepadButton` vocabulary rather than either profile:
  `missingButtons()` subtracts only `L2` and `R2`, so all five plates fail *every built-in layout
  exposes every button the profile declares* until it excuses Capture too; and `FALLBACK_SPECS`
  keys its position on `if (id.button == L2) … else …`, so a new button silently arrives stacked
  on R2's fallback unless given a place of its own
- `SWITCH_LAYOUT` places Capture, and `SWITCH_ART` gets a glyph if Kenney's pack has one — no
  capture SVG is in `art/input/` today. Without one it falls back to shape and label, which is
  already what the PS5 guide button does
- **ZL/ZR go out digital.** A Pro Controller's are, and its report has nowhere to put the analog
  range the pad and the generic descriptor carry, so the Switch profile thresholds them the way
  `TRIGGER_DIGITAL_THRESHOLD` already does for the digital companions

That was the plan, and Stage 0 killed it before any of it was written. The premise above — that the
question is whether Android's five SDP fields leave enough surface — was the right question, and the
answer is no: the console rejects the phone before the HID channel exists, so none of the protocol
work above would ever run. It is kept because it is a worked design, not because it is queued.

**Do not reopen this against stock Android.** Anything that changes the answer changes the project's
premise instead — root to set `bluetooth.device_id.*`, a Pi or ESP32 doing the radio, or USB gadget
mode. If one of those ever becomes acceptable, Stages 1–3 are the plan to pick back up.

### Traps found while building the Stage 0 probe

- **`startDiscovery()` returns `false`, silently**, if `BLUETOOTH_SCAN` is declared without
  `android:usesPermissionFlags="neverForLocation"` and the app has no `ACCESS_FINE_LOCATION`.
  Nothing throws and no permission prompt appears — the call just says no. The manifest now
  asserts the flag, which is honest: nothing here derives location
- **Cancelling an inquiry is asynchronous.** `cancelDiscovery()` immediately followed by
  `startDiscovery()` fails; the second call needs a moment, hence `CANCEL_DISCOVERY_SETTLE_MS`
- **`ACTION_FOUND` is registered `RECEIVER_EXPORTED`**, unlike the adapter-state receiver. It is a
  protected broadcast, so only the system can send it, and `NOT_EXPORTED` is a candidate for
  silently dropping it

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
- [x] **CI workflow** — `.github/workflows/build.yml` runs `test`, `lint` and `assemble` on every
      push and pull request to `main`, and uploads the APK as an artifact. A manual run picks Debug
      or Release; anything automatic is Debug
- [ ] Release signing config, so the workflow's Release APK is installable rather than unsigned

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
- **No VID/PID control, and it is what makes the Switch impossible.**
  `BluetoothHidDeviceAppSdpSettings` exposes name, description, provider, subclass and descriptor,
  and nothing that carries a vendor or product ID. The DeviceID record is the stack's: this phone
  publishes Bluetooth-sourced Xiaomi `038F:0000`, where a Pro Controller publishes USB-sourced
  `057E:2009`. There is no API, hidden or otherwise — the record and the class of device both
  belong to the adapter, not the app.

  **Measured against a real console, not inferred.** With the adapter renamed `Pro Controller`,
  the SDP strings reading `Wireless Gamepad` / `Gamepad` / `Nintendo`, subclass `0x08`, and a
  report descriptor byte-identical to a real pad's, a Switch in *Change Grip/Order*:
  finds the phone unprompted, **bonds successfully**, then tears the bond down and retries,
  **without ever opening the HID channel**. Bond states cycled `11 → 12 → 10` across two separate
  runs. Since the rejection lands after bonding and before HID, it is read from SDP; since every
  other field in that record now matches a real pad exactly, what is left is the DeviceID record.

  Two useful corrections to earlier guesses, both worth keeping: **class of device is not the
  gate** — the console offers the phone as a candidate despite major class *Phone* — and
  **`onInterruptData` does fire**, so the subcommand handshake is mechanically reachable. Neither
  helps, because the console never opens the channel they would run on.

  Only three things would change this, all outside the project's premise: root, to set the
  `bluetooth.device_id.*` and `class_of_device` system properties; a Bluetooth shim such as a Pi or
  ESP32 doing the radio, which is how every working Pro Controller emulator does it; or USB gadget
  mode, which also needs root. See the *Nintendo Switch* section for the full Stage 0 record.
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
