#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD="$ROOT/build"
APP="$BUILD/Payload/DarwinBridge.app"
OBJ="$BUILD/obj"
PLUGIN_SRC="$BUILD/livecontainer-plugin/DarwinBridgeLCPlugin.dylib"
PLUGIN_STAGING="$(mktemp -d)"
trap 'rm -rf "$PLUGIN_STAGING"' EXIT

# Preserve the plugin because build.sh recreates the main build directory.
if [ -f "$PLUGIN_SRC" ]; then
  cp "$BUILD/livecontainer-plugin/"*.dylib "$PLUGIN_STAGING/"
fi

rm -rf "$BUILD"
mkdir -p "$APP" "$OBJ"

SDK="$(xcrun --sdk iphoneos --show-sdk-path)"
SWIFTC="$(xcrun --sdk iphoneos --find swiftc)"
CLANG="$(xcrun --sdk iphoneos --find clang)"

"$CLANG" -target arm64-apple-ios17.0 -isysroot "$SDK"   -c "$ROOT/Core/JIT26Protocol.S" -o "$OBJ/JIT26Protocol.o"

"$CLANG" -target arm64-apple-ios17.0 -isysroot "$SDK"   -c "$ROOT/Core/JIT26Allocator.c" -o "$OBJ/JIT26Allocator.o"

"$CLANG" -target arm64-apple-ios17.0 -isysroot "$SDK"   -c "$ROOT/Core/RuntimeBridge.c" -o "$OBJ/RuntimeBridge.o"

"$SWIFTC"   -sdk "$SDK"   -target arm64-apple-ios17.0   -parse-as-library   -O   "$ROOT"/Core/*.swift   "$ROOT"/App/*.swift   "$OBJ/JIT26Protocol.o"   "$OBJ/JIT26Allocator.o"   "$OBJ/RuntimeBridge.o"   -framework SwiftUI   -framework UniformTypeIdentifiers   -framework Foundation   -framework UIKit   -o "$APP/DarwinBridge"

cp "$ROOT/Info.plist" "$APP/Info.plist"
cp "$ROOT/JIT/darwinbridge-universal.js" "$APP/darwinbridge-universal.js"

PLUGIN="$PLUGIN_STAGING/DarwinBridgeLCPlugin.dylib"
if [ -f "$PLUGIN" ]; then
  mkdir -p "$APP/DarwinBridgeRuntime"
  cp "$PLUGIN_STAGING/"*.dylib "$APP/DarwinBridgeRuntime/"
  # Preserve the historical resource location for existing diagnostics.
  cp "$PLUGIN" "$APP/DarwinBridgeLCPlugin.dylib"
else
  echo "error: DarwinBridgeLCPlugin.dylib was not built before build.sh" >&2
  exit 1
fi

if command -v codesign >/dev/null 2>&1; then
  codesign --force --sign -     --entitlements "$ROOT/DarwinBridge.entitlements"     "$APP/DarwinBridge"
fi

(
  cd "$BUILD"
  /usr/bin/zip -qry DarwinBridge-unsigned.ipa Payload
)

echo "$BUILD/DarwinBridge-unsigned.ipa"
