# DarwinBridge

DarwinBridge is an experimental macOS-to-iPadOS compatibility layer prototype targeting native ARM64 Mach-O applications.

## Milestone 0.1 — passed on physical iPad

- ARM64 Mach-O parsing
- segment mapping for inspection
- framework dependency classification
- SwiftUI file importer
- physical-device mapping test passed

## Milestone 0.2 — chained dyld groundwork

Implemented:

- LC_DYLD_CHAINED_FIXUPS discovery
- dyld chained-fixups header validation
- imports formats 1/2/3
- starts-in-image / starts-in-segment parsing
- DYLD_CHAINED_PTR_64 chain walking
- DYLD_CHAINED_PTR_64_OFFSET chain walking
- rebase/bind action planning
- native host symbol probing through a SymbolBroker
- explicit detection of unsupported ARM64e/PAC pointer formats
- synthetic CI smoke tests
- UI diagnostics for imports, fixups and unresolved bindings

Not enabled yet:

- writing rebased/bound pointers into mapped guest memory
- ARM64e pointer authentication reconstruction
- guest code execution

Those remain gated intentionally until address-space translation and page protections are correct.

## Next milestone

Milestone 0.3 will create a contiguous guest virtual-address mapping, apply supported rebases/binds, finalize segment protections, and add an execution-capability probe before any guest entry point is called.
