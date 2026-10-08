import Foundation

struct LoLDiagnosticCheckpoint: Identifiable {
    let id = UUID()
    let name: String
    let passed: Bool
    let detail: String
}

struct LoLRuntimeDiagnosticReport {
    let readyForHandoff: Bool
    let checkpoints: [LoLDiagnosticCheckpoint]
    let summary: String
}

struct LoLRuntimeDiagnostics {
    static func capture(data: Data,
                        image: MachOImageInfo,
                        deepScan: DeepSymbolScanReport?,
                        runtimeValidation: LoLRuntimeShimValidation?,
                        launchDryRun: LoLLaunchDryRunReport?) -> LoLRuntimeDiagnosticReport {
        var points: [LoLDiagnosticCheckpoint] = []

        points.append(.init(name: "Client image",
                            passed: image.isArm64 && !image.encrypted,
                            detail: "ARM64=\(image.isArm64) encrypted=\(image.encrypted) bytes=\(data.count)"))

        points.append(.init(name: "LC_MAIN",
                            passed: image.entryOffset != nil,
                            detail: image.entryOffset.map { String(format: "file offset 0x%llX", $0) } ?? "missing"))

        let exec = image.segments.filter { ($0.initialProtection & 0x4) != 0 }
        let writable = image.segments.filter { ($0.initialProtection & 0x2) != 0 }
        points.append(.init(name: "Segment layout",
                            passed: !exec.isEmpty,
                            detail: "\(image.segments.count) total / \(exec.count) executable / \(writable.count) writable"))

        if let deepScan {
            points.append(.init(name: "Deep import surface",
                                passed: deepScan.imports.count > 0,
                                detail: "\(deepScan.resolvedCount)/\(deepScan.imports.count) host-resolved; \(deepScan.unresolvedCount) require compatibility handling"))
        } else {
            points.append(.init(name: "Deep import surface",
                                passed: false,
                                detail: "Stage 15C result missing"))
        }

        if let runtimeValidation {
            points.append(.init(name: "Runtime shims",
                                passed: runtimeValidation.remaining.isEmpty,
                                detail: "\(runtimeValidation.totalCovered) compatibility imports covered; \(runtimeValidation.remaining.count) remaining"))
        } else {
            points.append(.init(name: "Runtime shims",
                                passed: false,
                                detail: "Stage 16C result missing"))
        }

        if let launchDryRun {
            points.append(.init(name: "Launch resolver",
                                passed: launchDryRun.ready,
                                detail: "\(launchDryRun.resolvedImports)/\(launchDryRun.totalImports) imports; \(launchDryRun.unresolvedImports) unresolved"))
        } else {
            points.append(.init(name: "Launch resolver",
                                passed: false,
                                detail: "Stage 17B result missing"))
        }

        let objcReady = ["objc_getClass", "objc_msgSend", "sel_registerName"].allSatisfy {
            RuntimeCompatibility.address(of: $0) != nil
        }
        points.append(.init(name: "Objective-C runtime",
                            passed: objcReady,
                            detail: objcReady ? "core Objective-C entry points available" : "one or more Objective-C entry points missing"))

        let metalReady = metalAvailable()
        points.append(.init(name: "Metal host",
                            passed: metalReady,
                            detail: metalReady ? "MTLCreateSystemDefaultDevice available" : "Metal device entry point unavailable"))

        let bundleReady = FileManager.default.isReadableFile(atPath: Bundle.main.bundlePath)
        points.append(.init(name: "Host bundle readability",
                            passed: bundleReady,
                            detail: Bundle.main.bundlePath))

        let ready = points.allSatisfy(\.passed)
        let summary = ready
            ? "Static prerequisites passed. Guest dependency loading, Objective-C registration, initializers and LC_MAIN execution remain unverified."
            : "Runtime diagnostics found a blocker before the execution handoff."

        return LoLRuntimeDiagnosticReport(readyForHandoff: ready,
                                          checkpoints: points,
                                          summary: summary)
    }

    private static func metalAvailable() -> Bool {
        guard let handle = dlopen("/System/Library/Frameworks/Metal.framework/Metal", RTLD_LAZY) else {
            return false
        }
        defer { dlclose(handle) }
        return dlsym(handle, "MTLCreateSystemDefaultDevice") != nil
    }
}
