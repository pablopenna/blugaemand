# DarwinBridge 21G: one external-loader test

1. Unzip this download. In the **currently installed black-screen LoL guest's**
   LiveContainer settings, replace its custom JIT launch script with
   `darwinbridge-universal.js` from this archive.
2. Launch the same guest with JIT through your normal debugger. For this test,
   keep the guest IPA unchanged so its failing execution chain can be identified.
3. Return the debugger's log beginning `[DB21G ...]`, through termination or its
   last available output. This is the debugger log, not DarwinBridge-runtime.log.

The log is emitted before any guest constructor. It records attach/stop PC and
registers, instruction bytes, image paths/UUIDs, dyld state and process exit.
Supported hardware breakpoints mark image notification, bootstrap/plugin
initializer entry and guest LC_MAIN. Failed/unsupported diagnostic requests are
reported explicitly. No unknown breakpoint is skipped; normal signal continuation
resumes other threads too.

This is an observation test, not a promise of gameplay. The repository contains a
corrected runtime/packager and a binary audit with a reproduced pre-constructor
Objective-C ABI failure. Without the exact installed IPA or device trace, the
last executed iPad instruction is not yet established.

Detailed evidence: DarwinBridge/Audit/README.md on feat/darwinbridge-macho-loader.
