import Foundation

enum DesktopShimKind: String {
    case nativeForward = "native-forward"
    case appKitToUIKit = "appkit-uikit"
    case unsupported = "unsupported"
}

struct DesktopSymbolShim: Identifiable {
    let id = UUID()
    let macSymbol: String
    let hostSymbol: String?
    let kind: DesktopShimKind
    let note: String
}

struct DesktopShimRegistry {
    static let foundationAndCore: [DesktopSymbolShim] = [
        DesktopSymbolShim(macSymbol: "CFStringCreateWithCString",
                          hostSymbol: "CFStringCreateWithCString",
                          kind: .nativeForward,
                          note: "CoreFoundation ABI candidate; validate symbol-by-symbol."),
        DesktopSymbolShim(macSymbol: "CFRelease",
                          hostSymbol: "CFRelease",
                          kind: .nativeForward,
                          note: "CoreFoundation ownership primitive."),
        DesktopSymbolShim(macSymbol: "objc_msgSend",
                          hostSymbol: "objc_msgSend",
                          kind: .nativeForward,
                          note: "Objective-C runtime entry point."),
        DesktopSymbolShim(macSymbol: "NSLog",
                          hostSymbol: "NSLog",
                          kind: .nativeForward,
                          note: "Foundation logging entry point.")
    ]

    static let appKitGroundwork: [DesktopSymbolShim] = [
        DesktopSymbolShim(macSymbol: "NSApplication",
                          hostSymbol: "UIApplication",
                          kind: .appKitToUIKit,
                          note: "Requires object-level facade, not direct ABI aliasing."),
        DesktopSymbolShim(macSymbol: "NSWindow",
                          hostSymbol: "UIWindow",
                          kind: .appKitToUIKit,
                          note: "Window lifecycle and coordinate semantics require translation."),
        DesktopSymbolShim(macSymbol: "NSView",
                          hostSymbol: "UIView",
                          kind: .appKitToUIKit,
                          note: "View hierarchy candidate for UIKit-backed shim."),
        DesktopSymbolShim(macSymbol: "NSEvent",
                          hostSymbol: "UIEvent",
                          kind: .appKitToUIKit,
                          note: "Keyboard/pointer translation will be handled separately.")
    ]

    static var all: [DesktopSymbolShim] {
        foundationAndCore + appKitGroundwork
    }
}
