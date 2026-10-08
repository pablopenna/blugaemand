import Foundation
import UIKit
import ObjectiveC.runtime
import CoreGraphics

// Stage 21X-B: batch compatibility surface generated from Riot's current macOS payload.
// Desktop Objective-C classes are intentionally inert NSObject facades until a UIKit-backed
// semantic implementation is required. This prevents dependency discovery from regressing
// to crash-by-crash symbol patching.

private final class DBDesktopObjectShim: NSObject {}

private var dbObjectClass: AnyClass { DBDesktopObjectShim.self }

private var dbGlobalSlots: [String: UnsafeMutableRawPointer] = [:]
private let dbGlobalLock = NSLock()

@_cdecl("DBGestaltShim")
public func DBGestaltShim(_ selector: UInt32, _ response: UnsafeMutablePointer<Int32>?) -> Int32 {
    response?.pointee = 0
    return 0
}

@_cdecl("DBDyldStubBinderShim")
public func DBDyldStubBinderShim() {}

@_cdecl("DBNoopIntShim")
public func DBNoopIntShim() -> Int32 { 0 }

@_cdecl("DBNoopUIntShim")
public func DBNoopUIntShim() -> UInt32 { 0 }

@_cdecl("DBNoopPointerShim")
public func DBNoopPointerShim() -> UnsafeMutableRawPointer? { nil }

@_cdecl("DBNoopDoubleShim")
public func DBNoopDoubleShim() -> Double { 0 }

struct LoLRuntimeShimRegistry {
    // Full AppKit/Desktop class set observed across the 26 harvested Mach-O images.
    // Foundation classes that exist natively are resolved by dlsym before reaching this table.
    private static let desktopClasses: Set<String> = [
        "NSATSTypesetter","NSAccessibilityElement","NSAccessibilityRemoteUIElement","NSAlert",
        "NSAnimation","NSAnimationContext","NSAppearance","NSAppleEventDescriptor","NSAppleEventManager",
        "NSApplication","NSBezierPath","NSBitmapImageRep","NSBox","NSButton","NSButtonCell",
        "NSCandidateListTouchBarItem","NSCell","NSColor","NSColorPanel","NSColorSampler","NSColorSpace",
        "NSComboBox","NSControl","NSCursor","NSCustomTouchBarItem","NSDistributedNotificationCenter",
        "NSDraggingItem","NSEvent","NSFont","NSFontDescriptor","NSFontManager","NSGraphicsContext",
        "NSGroupTouchBarItem","NSImage","NSImageView","NSLayoutManager","NSMatrix","NSMenu","NSMenuItem",
        "NSNetService","NSNetServiceBrowser","NSNextStepFrame","NSNib","NSOpenGLContext","NSOpenGLPixelFormat",
        "NSOpenPanel","NSPasteboard","NSPasteboardItem","NSPathControl","NSPopUpButton","NSPopUpButtonCell",
        "NSPopover","NSPrintInfo","NSPrintPanel","NSResponder","NSRunningApplication","NSSavePanel","NSScreen",
        "NSScriptCommand","NSScrollView","NSScroller","NSSegmentedControl","NSShadow","NSSharingService",
        "NSSharingServicePicker","NSSpeechSynthesizer","NSSpellChecker","NSStatusBar","NSTabView","NSTableColumn",
        "NSTableView","NSTask","NSTextAttachment","NSTextAttachmentCell","NSTextContainer","NSTextField",
        "NSTextInputContext","NSTextStorage","NSThemeFrame","NSTitlebarAccessoryViewController","NSToolbar",
        "NSTouchBar","NSTrackingArea","NSUniqueIDSpecifier","NSUserNotification","NSUserNotificationCenter",
        "NSView","NSViewController","NSWindow","NSWindowController","NSWorkspace","NSWorkspaceOpenConfiguration",
        "NSXPCConnection","NSXPCInterface","SBApplication",
        "IOBluetoothDevice","IOBluetoothDeviceInquiry","IOBluetoothHostController","IOBluetoothL2CAPChannel",
        "IOBluetoothRFCOMMChannel","IOBluetoothSDPServiceRecord","IOBluetoothSDPUUID",
        "SCContentFilter","SCShareableContent","SCStream","SCStreamConfiguration"
    ]

    private static let desktopMetaclasses: Set<String> = [
        "NSATSTypesetter","NSAccessibilityElement","NSAnimation","NSApplication","NSCursor","NSNextStepFrame",
        "NSResponder","NSSpeechSynthesizer","NSTableView","NSTextAttachmentCell","NSThemeFrame",
        "NSTitlebarAccessoryViewController","NSTrackingArea","NSView","NSViewController","NSWindow","NSWindowController"
    ]

    private static let stringGlobals: [String: String] = [
        "_NSCalibratedRGBColorSpace":"NSCalibratedRGBColorSpace",
        "_NSDeviceRGBColorSpace":"NSDeviceRGBColorSpace",
        "_NSEventTrackingRunLoopMode":"NSEventTrackingRunLoopMode",
        "_NSModalPanelRunLoopMode":"NSModalPanelRunLoopMode",
        "_NSDefaultRunLoopMode":"NSDefaultRunLoopMode",
        "_NSApplicationDidBecomeActiveNotification":"NSApplicationDidBecomeActiveNotification",
        "_NSImageNameCaution":"NSImageNameCaution",
        "_NSImageNameInfo":"NSImageNameInfo",
        "_NSImageNameStopProgressFreestandingTemplate":"NSImageNameStopProgressFreestandingTemplate",
        "_NSMarkedClauseSegmentAttributeName":"NSMarkedClauseSegmentAttributeName",
        "_NSPasteboardTypeString":"public.utf8-plain-text",
        "_NSTextInputReplacementRangeAttributeName":"NSTextInputReplacementRangeAttributeName"
    ]

    private static let zeroStructGlobals: Set<String> = ["_NSZeroRect", "_NSDeviceSize"]

    // Functions in the harvested payload whose desktop behavior is optional for launch/preflight.
    // They get a stable inert C ABI target rather than remaining unresolved.
    private static let noOpFunctions: Set<String> = [
        "_CGAssociateMouseAndMouseCursorPosition","_CGDisplayCapture","_CGDisplayRelease",
        "_CGDisplaySetDisplayMode","_CGReleaseAllDisplays","_CGSetLocalEventsSuppressionInterval",
        "_CGWarpMouseCursorPosition","_IOHIDGetParameter","_IOHIDSetParameter"
    ]

    static func pointer(for symbol: String) -> UnsafeMutableRawPointer? {
        if symbol == "_Gestalt" {
            return unsafeBitCast(DBGestaltShim as @convention(c) (UInt32, UnsafeMutablePointer<Int32>?) -> Int32,
                                 to: UnsafeMutableRawPointer.self)
        }
        if symbol == "dyld_stub_binder" {
            return unsafeBitCast(DBDyldStubBinderShim as @convention(c) () -> Void,
                                 to: UnsafeMutableRawPointer.self)
        }

        if symbol == "_NSApp" {
            return objectSlot(symbol, object: UIApplication.shared)
        }
        if let value = stringGlobals[symbol] {
            return objectSlot(symbol, object: value as NSString)
        }
        if zeroStructGlobals.contains(symbol) {
            return zeroSlot(symbol, bytes: 64)
        }

        if symbol.hasPrefix("_OBJC_CLASS_$_") {
            let name = String(symbol.dropFirst("_OBJC_CLASS_$_".count))
            if desktopClasses.contains(name) {
                return unsafeBitCast(dbObjectClass, to: UnsafeMutableRawPointer.self)
            }
        }
        if symbol.hasPrefix("_OBJC_METACLASS_$_") {
            let name = String(symbol.dropFirst("_OBJC_METACLASS_$_".count))
            if desktopMetaclasses.contains(name), let meta: AnyClass = object_getClass(dbObjectClass) {
                return unsafeBitCast(meta, to: UnsafeMutableRawPointer.self)
            }
        }

        if noOpFunctions.contains(symbol) {
            return unsafeBitCast(DBNoopIntShim as @convention(c) () -> Int32,
                                 to: UnsafeMutableRawPointer.self)
        }
        return nil
    }

    static func isIntraImageWeak(_ symbol: DeepImportedSymbol) -> Bool {
        symbol.libraryOrdinal == 0 || symbol.dependencyPath == "self" || symbol.weak
    }

    private static func objectSlot(_ key: String, object: AnyObject) -> UnsafeMutableRawPointer? {
        dbGlobalLock.lock(); defer { dbGlobalLock.unlock() }
        if let slot = dbGlobalSlots[key] { return slot }
        let retained = Unmanaged.passRetained(object).toOpaque()
        let slot = UnsafeMutableRawPointer.allocate(byteCount: MemoryLayout<UInt>.size,
                                                    alignment: MemoryLayout<UInt>.alignment)
        slot.storeBytes(of: UInt(bitPattern: retained), as: UInt.self)
        dbGlobalSlots[key] = slot
        return slot
    }

    private static func zeroSlot(_ key: String, bytes: Int) -> UnsafeMutableRawPointer? {
        dbGlobalLock.lock(); defer { dbGlobalLock.unlock() }
        if let slot = dbGlobalSlots[key] { return slot }
        let slot = UnsafeMutableRawPointer.allocate(byteCount: bytes, alignment: 16)
        slot.initializeMemory(as: UInt8.self, repeating: 0, count: bytes)
        dbGlobalSlots[key] = slot
        return slot
    }
}

struct LoLRuntimeShimValidation {
    let runtimeResolved: Int
    let weakIntraImage: Int
    let remaining: [String]
    var totalCovered: Int { runtimeResolved + weakIntraImage }
}

struct LoLRuntimeShimValidator {
    static func validate(scan: DeepSymbolScanReport) -> LoLRuntimeShimValidation {
        var runtime = 0
        var weak = 0
        var remaining: [String] = []
        for item in scan.imports where !item.hostResolved {
            if LoLRuntimeShimRegistry.pointer(for: item.name) != nil {
                runtime += 1
            } else if LoLRuntimeShimRegistry.isIntraImageWeak(item) {
                weak += 1
            } else {
                remaining.append(item.name)
            }
        }
        return LoLRuntimeShimValidation(runtimeResolved: runtime,
                                        weakIntraImage: weak,
                                        remaining: remaining.sorted())
    }
}
