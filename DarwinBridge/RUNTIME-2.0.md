# DarwinBridge 2.0 Runtime Contract

This is an **incremental compatibility runtime**, not a copy of macOS and not a claim that League of Legends runs on iPadOS.

## Required execution components

| Layer | Implementation requirement | Gate |
|---|---|---|
| ARM64 Mach-O | Validate image type, load commands, slices, segment ranges, LC_MAIN, encryption and code signatures | parser smoke + package audit |
| dyld compatibility | Dependency graph, @rpath/@loader_path, ordinals, weak/reexport/upward loads, chained fixups | fixture package verification |
| Native Darwin | POSIX/libSystem, pthreads, dispatch, sockets and filesystem mapping | host runtime probes |
| Objective-C | Preserve real class/metaclass identities; no arbitrary Class data aliases | objc loader regression |
| Foundation/CF | ABI-safe forwarding to iOS equivalents where verified | native export audit |
| AppKit | UIKit window/event lifecycle implementation, not placeholder no-op methods | iOS simulator runtime probe |
| Graphics | Metal feature/format negotiation; CEF/ANGLE dependencies when present | future graphics integration test |
| Process/IPC | child process policy, sandbox constraints, XPC/IPC semantics | future subprocess fixture |
| Riot/CEF payload | Complete official bundle and resource dependency closure | Harvester inventory + bundle closure audit |
| JIT | debugger attach, signal handling, executable memory, entry transfer | JIT protocol regression |

## Hard release gates

A green compilation result is **not** sufficient evidence of application compatibility.
A standalone executable is **not** the complete Riot client.
Missing @rpath dependencies are blocking until bundled or explicitly mapped.
Any unknown ABI or absent resource must be reported, never silently marked supported.

The existing first-run IPA is a **diagnostic fixture**. It must not be represented as a playable League build.

## Workflow

1. Build core loader and plugin.
2. Run Mach-O/Objective-C/JIT regression tests.
3. Build the fixture using the actual Swift packager.
4. Extract the IPA and inspect the final binaries, ordinals, initializer and signatures.
5. Run the iOS simulator runtime probe.
6. Check official Riot payload dependency closure.
7. Only label a release as a full-game candidate when all checks pass.

See `DarwinBridge/Tools/verify_package.py`, `DarwinBridge/Tools/audit_runtime.py`, and the `DarwinBridge iOS prototype` GitHub Actions workflow.
