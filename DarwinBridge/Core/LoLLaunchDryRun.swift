import Foundation

struct LoLLaunchImportResolution: Identifiable {
    let id = UUID()
    let symbol: String
    let dependency: String
    let source: String
    let resolved: Bool
    let strategy: String
}

struct LoLLaunchDryRunReport {
    let ready: Bool
    let totalImports: Int
    let resolvedImports: Int
    let unresolvedImports: Int
    let executableSegments: Int
    let writableSegments: Int
    let readableSegments: Int
    let entryOffset: UInt64?
    let importResolutions: [LoLLaunchImportResolution]
    let checkpoints: [String]
    let blockers: [String]
}

struct LoLLaunchDryRunAnalyzer {
    static func analyze(data: Data,
                        image: MachOImageInfo,
                        deepScan: DeepSymbolScanReport?,
                        runtimeValidation: LoLRuntimeShimValidation?) -> LoLLaunchDryRunReport {
        var blockers: [String] = []
        var checkpoints: [String] = []
        var resolutions: [LoLLaunchImportResolution] = []

        guard let deepScan else {
            return LoLLaunchDryRunReport(
                ready: false,
                totalImports: 0,
                resolvedImports: 0,
                unresolvedImports: 0,
                executableSegments: 0,
                writableSegments: 0,
                readableSegments: 0,
                entryOffset: image.entryOffset,
                importResolutions: [],
                checkpoints: [],
                blockers: ["Stage 15C deep scan has not been run."]
            )
        }

        for item in deepScan.imports {
            if item.hostResolved {
                resolutions.append(.init(
                    symbol: item.name,
                    dependency: item.dependencyPath ?? "unknown",
                    source: item.source,
                    resolved: true,
                    strategy: "host runtime"
                ))
                continue
            }

            if LoLRuntimeShimRegistry.pointer(for: item.name) != nil {
                resolutions.append(.init(
                    symbol: item.name,
                    dependency: item.dependencyPath ?? "unknown",
                    source: item.source,
                    resolved: true,
                    strategy: "DarwinBridge runtime shim"
                ))
                continue
            }

            if LoLRuntimeShimRegistry.isIntraImageWeak(item) {
                resolutions.append(.init(
                    symbol: item.name,
                    dependency: item.dependencyPath ?? "self",
                    source: item.source,
                    resolved: true,
                    strategy: "intra-image / weak"
                ))
                continue
            }

            resolutions.append(.init(
                symbol: item.name,
                dependency: item.dependencyPath ?? "unknown",
                source: item.source,
                resolved: false,
                strategy: "unresolved"
            ))
        }

        let unresolved = resolutions.filter { !$0.resolved }
        if !unresolved.isEmpty {
            blockers.append("\(unresolved.count) imports remain unresolved.")
        }

        if runtimeValidation?.remaining.isEmpty == true {
            checkpoints.append("LoL runtime shim validation is READY.")
        } else {
            blockers.append("Stage 16C runtime shim validation is not READY.")
        }

        let execSegments = image.segments.filter { ($0.initialProtection & 0x4) != 0 }.count
        let writeSegments = image.segments.filter { ($0.initialProtection & 0x2) != 0 }.count
        let readSegments = image.segments.filter { ($0.initialProtection & 0x1) != 0 }.count

        if execSegments == 0 {
            blockers.append("No executable segment was found.")
        } else {
            checkpoints.append("\(execSegments) executable segment(s) identified.")
        }

        if let entry = image.entryOffset {
            checkpoints.append("LC_MAIN entry offset = " + String(format: "0x%llX", entry))
        } else {
            blockers.append("LC_MAIN is missing.")
        }

        if image.isArm64 {
            checkpoints.append("ARM64 client slice selected.")
        } else {
            blockers.append("Client is not ARM64.")
        }

        if image.encrypted {
            blockers.append("Client Mach-O is encrypted.")
        } else {
            checkpoints.append("Client image is unencrypted.")
        }

        checkpoints.append("\(resolutions.count - unresolved.count)/\(resolutions.count) imported symbols have a launch-time resolution strategy.")

        return LoLLaunchDryRunReport(
            ready: blockers.isEmpty,
            totalImports: resolutions.count,
            resolvedImports: resolutions.count - unresolved.count,
            unresolvedImports: unresolved.count,
            executableSegments: execSegments,
            writableSegments: writeSegments,
            readableSegments: readSegments,
            entryOffset: image.entryOffset,
            importResolutions: resolutions,
            checkpoints: checkpoints,
            blockers: blockers
        )
    }
}
