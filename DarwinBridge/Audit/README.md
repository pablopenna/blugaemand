# Stage 21G loader audit

## Device evidence received 2026-10-07

The new device log proves that DBBootstrap ran, the compatibility plugin reached
`plugin-constructor-complete`, and its atexit handler ran. The loaded-image snapshot
contains `LeagueOfLegends`, all 14 distinct forwarders, DBBootstrap and one plugin.
`NSApplication sharedApplication` is called by our constructor; that line is not
evidence of the guest application lifecycle. `NORMAL EXIT` is an atexit marker,
not proof of a successful application startup. There is no NSApplication.run
checkpoint, guest UUID, source hash or exit status in this log. Sanitized findings
and the submitted file's hash are in `device-20261007-evidence.json`.

A separate screenshot reports LiveContainer's guest main returning 252. The
source of that message is immediately after its call to the LC_MAIN entrypoint.
The screenshot and log have no shared run identifier, so correlating them to one
invocation remains a user-provided context rather than a measured association.

The 14 forwarding-library names **and their order** match the official EUW Mac
installer's `RiotClientServices` executable. The official ZIP downloaded from
`https://lol.secure.dyn.riotcdn.net/channels/public/x/installer/current/live.euw.zip`
has SHA-256 `4f02590ef8a1bd41ad77c6c5a07d33029220a2e421d7bb4dee31653ef17a9181`;
its ARM64 slice is 18,330,112 bytes, UUID
`5e0c030d-a1b8-3b20-854d-8479b43b0a2c`, SHA-256
`199f80081c87b9dd0f3a996ea85dbbdedd0680d14dc2f003f90d1ef9f29da782`.
This is a strong installer fingerprint, not a verified match to the device UUID.
Neither the harvested LeagueClient nor game executable has this dependency graph.

Disassembly of that pinned official binary shows LC_MAIN at offset `0x9d99bc`
calling startup at `0x9d49d0`. The startup code sets return register w23 to 252
at offset `0x9d519c` after the system.yaml load-failure diagnostic, and at
`0x9d51c4` after the missing-publisher diagnostic. There are also two later 252
paths. Therefore 252 is not uniquely diagnostic of one failed operation. These
are static file offsets, not measured device PCs. The official app includes
`Contents/Resources/system.yaml`, themes, localization and fonts; the old
executable-only packager omitted all of these and renamed the input unconditionally.

`installer_startup_audit.py` checks the official ZIP and ARM64 hashes, disassembles
the exit sites, tests packager rejection under both original and renamed filenames,
and runs a resource-less native copy under a restricted macOS sandbox. Its uploaded
report records the actual native exit result; no Riot binaries or assets are
published. The rolling download URL is hash-guarded and will fail if Riot replaces
the source, rather than silently comparing a different version.

The packager now rejects this installer/bootstrap by content and includes
`DarwinBridge-package.json` in diagnostic packages with original filename, ARM64
hash, UUID, entry offset and dependencies. Unknown binaries are explicitly
unclassified. This prevents installer input from being presented as a complete
LoL client; it does not implement a complete Riot bundle port. Inspecting the
actual installed IPA is still required to resolve the device's exact input identity
and distinguish the individual startup-failure branches. No additional API shims
or guessed startup arguments were added.

## Original pre-initializer failure

The pre-initializer blocker is invalid **native runtime identity**, not a missing
shim symbol. The plugin downloaded from CI artifact **11436883293**, run
**37516140814**, commit `4b192bfe604e05203b40dc04d51798954f7cd660`, contains 78
invalid class/metaclass/constant-string identity exports.

`baseline-evidence.json` records independently parsed final bytes:

- `DBCompatObject.superclass` rebases to `0x11e20`, the zero-filled
  `_OBJC_CLASS_$_NSObject` variable, not an Objective-C class object.
- The first `__cfstring` object's isa rebases to `0x11fe8`, the zero-filled
  `___CFConstantStringClassReference` array.
- The plugin **does** have an initializer: `__TEXT,__init_offsets` -> `0x62f4`.
  Checking only `__mod_init_func` would incorrectly miss it.
- That downloaded plugin has valid ad-hoc code-page hashes. Simulating the old
  packager's install-name rename invalidates code page zero. This is a simulation
  of the alias mutation, not inspection of the user's installed guest IPA.
- The old linker emitted ordinary dependencies, **no LC_REEXPORT_DYLIB**. Comments
  in the packager claiming that native frameworks were re-exported were false.

An exported `Class` variable supplies the address of a pointer-sized cell. Dyld's
ObjC class bind expects the class object itself at that address. Assigning the
cell in a later constructor cannot fix this extra indirection or a superclass
that libobjc reads before that constructor. Similarly, zero-filled arrays cannot
replace the native constant-string class.

The macOS runtime regression compiles and actually dlopens a minimal reproduction
of that bad superclass. CI run 37519931058 observed **SIGSEGV (-11), no constructor
marker**. The version using a native Foundation re-export loaded successfully and
printed `CONSTRUCTOR_REACHED`. This demonstrates the failure mechanism on Apple's
runtime; it does not identify a device PC without a trace.

## Correction

- Remove fabricated native class/metaclass cells and constant-string anchors.
- Use linker `LC_REEXPORT_DYLIB` for native frameworks. Remove shadowed native CF
  singleton variables, preserving native object identity.
- Build small, separately identified **forwarding dylibs** for each original
  library ordinal. They re-export one implementation, without duplicating classes.
- Build `DBBootstrap.dylib` as C + libSystem only. Log to stderr and the guest's
  HOME/Documents using POSIX calls, without Foundation string objects.
- Copy all pre-signed dylibs byte for byte. Remove the stale Riot signature
  command from the changed executable; retain all section/file offsets. Destination
  signing must happen after LiveContainer's own modifications.
- Keep bootstrap injection bounded by section, segment, symbol and linkedit
  regions. Refuse insufficient/occupied padding rather than shifting payload.
- Declare ABI 1 for redirected DarwinBridge library commands and dylib IDs.
- Fix debugger signal continuation: `vCont;Cxx:thread;c`, not single-step `Sxx`.
  Only recognized protocol BRKs advance PC; unknown/fatal traps are preserved.

The host `MachOLoader`, `SymbolBroker`, dry-run, readiness and event-log stages
are analysis/planning for this IPA route. They do not execute inside the guest
loaded by LiveContainer. Resolving a host address does not validate an exported
plugin ABI or prove a guest instruction. Readiness text now says this explicitly,
and the planned entry offset is labeled `expected-lc-main` rather than a runtime event.

## Real execution chain (pinned upstream; installed version unknown)

Audited LiveContainer commit `4dbe0f9a626de801184a42c0be8d2cb105058e3d`:

1. `CFBundleExecutable` selects the guest file. `LCAppInfo.m` patches it during
   install/update, converts MH_EXECUTE to MH_DYLIB, modifies PAGEZERO/flags, adds an
   ID and TweakLoader, and retains LC_MAIN. It may disable duplicated dylib commands;
   distinct forwarding paths avoid that ordinal hazard.
2. With a certificate and signing enabled it invokes ZSign after mutation. The
   `dontSign`, no-certificate and 32-bit branches skip signing. On iOS 26+ the
   bootstrap explicitly requires JIT-less certificate setup. JIT is not proof of
   a valid guest signature. CI validates ad-hoc integrity, not device trust.
3. `LCBootstrap.m` changes bundle/executable path and guest HOME, invokes
   `dlopen_nolock(... RTLD_LAZY|RTLD_GLOBAL|RTLD_FIRST)`, then looks up LC_MAIN and
   calls it. Constructors complete as part of dlopen; LC_MAIN has not run if image
   loading/ObjC registration fails.
4. That path expects the guest to start UIApplicationMain. A desktop NSRunLoop
   does not establish an iOS UIApplication lifecycle, and a queued overlay cannot
   prove startup when no UIWindowScene exists. The existing NSApplication shim is
   still incomplete; Stage 21G makes no playable-game/UI claim.

CI compiles the exact pinned upstream `LCPatchExecSlice` plus its helpers and runs
it on the final fixture binary, then checks ordinals, sections, LC_MAIN and re-signs.
This establishes behavior for the pinned source only, not the installed build.

## iOS runtime execution test

`run_ios_probe.py` builds the full compatibility source for the available iOS
simulator, installs a native UIKit probe and loads the forwarding dylibs. The test
requires native NSObject/NSString pointer identity through three distinct aliases,
one shared implementation address, both bootstrap and plugin constructor log
markers, and successful UIApplicationMain startup. Its logs and simulator/runtime
identity are uploaded with the audit. Run 37521172666 reached both constructors,
the file logger, heartbeat and UIKit main on arm64 iOS 26.2. Its log also exposed
three pre-existing runtime-name collisions (NSColor, NSFont, NSTask). These existing
shims now use distinct DBShim runtime names, with Mach-O aliases pointing directly
to their real class/metaclass objects. The integration test checks those identities
and rejects duplicate-class warnings. Dynamic NSClassFromString lookup of the
original names still resolves to iOS classes; full AppKit behavior is not claimed.
This is simulator execution, not iPad proof.

## Final artifact validation and limits

The same Swift packager used by the app is invoked on a real compiled ARM64 macOS
fixture with AppKit/Foundation/CFNetwork dependencies. Its resulting IPA is
extracted and inspected, all included Mach-Os are checked with file/otool/nm/vtool,
then signed and checked with `codesign --verify --strict` and independent code-page
hash verification. Section metadata/content and bind/fixup blobs are compared to
the original. The bootstrap path must resolve, its initializer must point into
executable text, forwarders cannot define ObjC classes, and the native class
identities must remain undefined imports. The fixture is **not the Riot client**.

The pinned official Riot game binary is separately downloaded and inspected. The
minimal executable-only package omits RiotGamesApi/RPatch/mvg and full resources;
LDAP/OpenGL/ApplicationServices also require a real port. That full game is not an
approved candidate. The minimal packager now refuses unresolved relative payload
dependencies or unconverted desktop framework paths instead of emitting a broken IPA. The previous 514-import file cannot be assumed identical to
this release. A manifest's combined fat-architecture import count is not evidence
that every ARM64 import is covered.

The installed `DarwinBridge-LoL-first-run.ipa`, installed LiveContainer build and
debugger trace have not been supplied. The newer in-process log is documented above.
No exact last device instruction is claimed.
A lack of log alone cannot distinguish mapping, binding, ObjC registration,
initializer entry, or failure inside the logger.

## Single next device experiment

Apply `DarwinBridge/JIT/darwinbridge-universal.js` to the **currently installed**
black-screen LoL guest, replacing the old script. Keep the IPA unchanged for this
experiment. Enable its normal JIT launch. Export the debugger log containing
`[DB21G ...]` after termination (or the last output if it remains running).

The script logs the initial attach, timestamps, stop packets, PC/x0/x1/x16/LR,
nearby instruction bytes, thread list, dyld state, loaded-image paths/UUIDs and exit.
Where debugserver supports hardware breakpoints it observes the dyld image
notification and arms one-shot initializer/LC_MAIN breakpoints. Unsupported packet
or breakpoint responses are logged as unavailable, not treated as proof of a
boundary. This path does not depend on the faulty guest plugin or its constructors.
A synchronous continue cannot sample a running hang on a timer; the last logged
boundary remains the last proven boundary, not a diagnosis of all later execution.

The corrected DarwinBridge packager is built by the same CI run, but installing it
is not necessary for the first experiment. Do not call the fixture or the packager
IPA a verified full League of Legends port.
