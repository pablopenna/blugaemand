#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${DB_RUNTIME_OUT:-$ROOT/build/livecontainer-plugin}"
mkdir -p "$OUT"
SDK_NAME="${DB_RUNTIME_SDK:-iphoneos}"
SDK="$(xcrun --sdk "$SDK_NAME" --show-sdk-path)"
CLANG="$(xcrun --sdk "$SDK_NAME" --find clang)"
COMMON=(-target "${DB_RUNTIME_TARGET:-arm64-apple-ios17.0}" -isysroot "$SDK" -dynamiclib -current_version 1.0 -compatibility_version 1.0 -Wl,-headerpad,0x400)
"$CLANG" "${COMMON[@]}" "$ROOT/LiveContainerPlugin/DBBootstrap.c" \
  -install_name @loader_path/DBBootstrap.dylib -o "$OUT/DBBootstrap.dylib"
# LC_LOAD_DYLIB is NOT a re-export. These LC_REEXPORT_DYLIB commands preserve
# native symbol addresses, ObjC class objects, metaclasses and CFString isa.
REEXPORTS=()
for framework in Foundation CoreFoundation UIKit CFNetwork Security CoreGraphics CoreText AVFoundation SystemConfiguration Metal; do
  REEXPORTS+=(-Wl,-reexport_framework,"$framework")
done
"$CLANG" "${COMMON[@]}" -fobjc-arc -fblocks \
  "$ROOT/LiveContainerPlugin/DarwinBridgeLCPlugin.m" "${REEXPORTS[@]}" \
  -Wl,-alias_list,"$ROOT/LiveContainerPlugin/class-aliases.txt" \
  -Wl,-needed_library,"$OUT/DBBootstrap.dylib" \
  -install_name @loader_path/DarwinBridgeLCPlugin.dylib -o "$OUT/DarwinBridgeLCPlugin.dylib"
for name in AppKit CoreServices Cocoa ScriptingBridge DiskArbitration IOKit AVFoundation CFNetwork CoreFoundation CoreGraphics CoreText Foundation Security SystemConfiguration; do
  "$CLANG" "${COMMON[@]}" "$ROOT/LiveContainerPlugin/DBForwarder.c" \
    -Wl,-reexport_library,"$OUT/DarwinBridgeLCPlugin.dylib" \
    -install_name "@rpath/DB${name}.dylib" -o "$OUT/DB${name}.dylib"
done
# Nothing mutates these binaries after this signing operation.
for dylib in "$OUT"/*.dylib; do
  codesign --force --sign - "$dylib"
  codesign --verify --strict "$dylib"
done
echo "$OUT"
