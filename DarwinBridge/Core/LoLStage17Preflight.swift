import Foundation

struct LoLStage17Checkpoint: Identifiable {
    let id = UUID()
    let name: String
    let passed: Bool
    let detail: String
}

struct LoLStage17Preflight {
    let ready: Bool
    let checkpoints: [LoLStage17Checkpoint]
    let firstBlocker: String?
}

struct LoLStage17PreflightAnalyzer {
    static func analyze(data: Data,
                        image: MachOImageInfo,
                        deepScan: DeepSymbolScanReport?,
                        runtimeValidation: LoLRuntimeShimValidation?) -> LoLStage17Preflight {
        var points: [LoLStage17Checkpoint] = []

        points.append(.init(name: "ARM64 client",
                            passed: image.isArm64,
                            detail: image.isArm64 ? "ARM64 slice selected" : "unsupported CPU"))
        points.append(.init(name: "Unencrypted image",
                            passed: !image.encrypted,
                            detail: image.encrypted ? "cryptid != 0" : "loadable image"))
        points.append(.init(name: "LC_MAIN",
                            passed: image.entryOffset != nil,
                            detail: image.entryOffset.map { String(format: "0x%llX", $0) } ?? "missing"))
        points.append(.init(name: "__TEXT executable range",
                            passed: image.segments.contains { $0.name == "__TEXT" && $0.fileSize > 0 },
                            detail: "real client text segment"))
        points.append(.init(name: "Dependency classification",
                            passed: CompatibilityAnalyzer.analyze(image).blockers.isEmpty,
                            detail: "\(image.dependencies.count) linked dependencies"))

        if let deepScan {
            points.append(.init(name: "Maximum-depth import scan",
                                passed: deepScan.imports.count > 0,
                                detail: "\(deepScan.imports.count) merged imports"))
            points.append(.init(name: "Native host resolution",
                                passed: deepScan.resolvedCount > 0,
                                detail: "\(deepScan.resolvedCount)/\(deepScan.imports.count) direct host symbols"))
        } else {
            points.append(.init(name: "Maximum-depth import scan",
                                passed: false,
                                detail: "run Stage 15C first"))
        }

        if let runtimeValidation {
            points.append(.init(name: "LoL runtime shim surface",
                                passed: runtimeValidation.remaining.isEmpty,
                                detail: "\(runtimeValidation.totalCovered) non-native imports covered"))
        } else {
            points.append(.init(name: "LoL runtime shim surface",
                                passed: false,
                                detail: "run Stage 16C first"))
        }

        let bundleReadable = FileManager.default.isReadableFile(atPath: Bundle.main.bundlePath)
        points.append(.init(name: "Host bundle/resources",
                            passed: bundleReadable,
                            detail: Bundle.main.bundlePath))

        let ready = points.allSatisfy(\.passed)
        return LoLStage17Preflight(ready: ready,
                                   checkpoints: points,
                                   firstBlocker: points.first(where: { !$0.passed })?.name)
    }
}
