import Foundation
import Darwin

struct Stage11To14Item: Identifiable {
    let id = UUID()
    let stage: Int
    let name: String
    let ready: Bool
    let detail: String
}

struct Stage11To14Report {
    let items: [Stage11To14Item]
    var stage11Ready: Bool { items.filter { $0.stage == 11 }.allSatisfy(\.ready) }
    var stage12Ready: Bool { items.filter { $0.stage == 12 }.allSatisfy(\.ready) }
    var stage13Ready: Bool { items.filter { $0.stage == 13 }.allSatisfy(\.ready) }
    var stage14Ready: Bool { items.filter { $0.stage == 14 }.allSatisfy(\.ready) }
    var readyCount: Int { items.filter(\.ready).count }
}

struct Stage11To14Analyzer {
    static func analyze(image: MachOImageInfo,
                        fixupPlan: FixupPlan?) -> Stage11To14Report {
        var items: [Stage11To14Item] = []

        let objc = ["objc_getClass","objc_msgSend","sel_registerName","class_getMethodImplementation"]
        let cf = ["CFRetain","CFRelease","CFStringCreateWithCString","CFRunLoopGetMain"]
        let thread = ["pthread_create","pthread_join","pthread_mutex_lock","pthread_mutex_unlock"]
        items.append(check(stage: 11, name: "Objective-C runtime", symbols: objc))
        items.append(check(stage: 11, name: "CoreFoundation + run loop", symbols: cf))
        items.append(check(stage: 11, name: "pthread/TLS foundation", symbols: thread))
        items.append(Stage11To14Item(stage: 11,
                                     name: "CLI dependency foundation",
                                     ready: Stage9ReadinessAnalyzer.analyze(image: image, fixupPlan: fixupPlan).readyForCLIBringUp,
                                     detail: "Mach-O dependency/fixup surface"))

        let uiKit = frameworkAvailable("/System/Library/Frameworks/UIKit.framework/UIKit")
        let quartz = frameworkAvailable("/System/Library/Frameworks/QuartzCore.framework/QuartzCore")
        let coreGraphics = frameworkAvailable("/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics")
        items.append(Stage11To14Item(stage: 12, name: "UIKit host", ready: uiKit, detail: "AppKit facade host"))
        items.append(Stage11To14Item(stage: 12, name: "QuartzCore", ready: quartz, detail: "layer/window composition"))
        items.append(Stage11To14Item(stage: 12, name: "CoreGraphics", ready: coreGraphics, detail: "2D graphics surface"))

        let metal = symbolInFramework("/System/Library/Frameworks/Metal.framework/Metal", "MTLCreateSystemDefaultDevice")
        let metalKit = frameworkAvailable("/System/Library/Frameworks/MetalKit.framework/MetalKit")
        let shaderCompilerSurface = metal
        items.append(Stage11To14Item(stage: 13, name: "Metal device runtime", ready: metal, detail: "MTLCreateSystemDefaultDevice"))
        items.append(Stage11To14Item(stage: 13, name: "MetalKit host", ready: metalKit, detail: "drawable/view integration"))
        items.append(Stage11To14Item(stage: 13, name: "Shader runtime surface", ready: shaderCompilerSurface, detail: "Metal runtime available for later shader translation"))

        let bundle = Bundle.main.bundleURL
        let resources = Bundle.main.resourceURL
        let bundleReadable = FileManager.default.isReadableFile(atPath: bundle.path)
        items.append(Stage11To14Item(stage: 14, name: "Bundle root", ready: bundleReadable, detail: bundle.path))
        items.append(Stage11To14Item(stage: 14, name: "Resource root", ready: resources != nil, detail: resources?.path ?? "missing"))
        items.append(Stage11To14Item(stage: 14,
                                     name: "ARM64 Mach-O candidate",
                                     ready: image.isArm64 && !image.encrypted && image.entryOffset != nil,
                                     detail: image.isArm64 ? "ARM64 executable candidate" : "unsupported architecture"))
        items.append(Stage11To14Item(stage: 14,
                                     name: "Dependency classification",
                                     ready: CompatibilityAnalyzer.analyze(image).blockers.isEmpty,
                                     detail: "\(image.dependencies.count) linked dependency/dependencies"))

        return Stage11To14Report(items: items)
    }

    private static func check(stage: Int, name: String, symbols: [String]) -> Stage11To14Item {
        let resolved = symbols.filter { RuntimeCompatibility.address(of: $0) != nil }
        return Stage11To14Item(stage: stage,
                               name: name,
                               ready: resolved.count == symbols.count,
                               detail: "\(resolved.count)/\(symbols.count) symbols")
    }

    private static func frameworkAvailable(_ path: String) -> Bool {
        guard let h = dlopen(path, RTLD_LAZY) else { return false }
        dlclose(h)
        return true
    }

    private static func symbolInFramework(_ path: String, _ symbol: String) -> Bool {
        guard let h = dlopen(path, RTLD_LAZY) else { return false }
        defer { dlclose(h) }
        return dlsym(h, symbol) != nil
    }
}
