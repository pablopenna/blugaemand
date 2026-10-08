import Foundation

struct LoLDependencyBucket: Identifiable {
    let id = UUID()
    let name: String
    let disposition: String
    let count: Int
    let paths: [String]
}

struct LoLStage15Report {
    let candidate: Bool
    let architecture: String
    let fileSize: Int
    let segmentCount: Int
    let dependencyCount: Int
    let rpathCount: Int
    let unresolvedImports: Int?
    let importedSymbolCount: Int?
    let buckets: [LoLDependencyBucket]
    let blockers: [String]
    let warnings: [String]
    let nextActions: [String]
}

struct LoLStage15Analyzer {
    static func analyze(data: Data,
                        image: MachOImageInfo,
                        fixupPlan: FixupPlan?) -> LoLStage15Report {
        let assessments = image.dependencies.map(FrameworkShimRegistry.assess)
        let groups = Dictionary(grouping: assessments) { $0.disposition.rawValue }
        let buckets = groups.keys.sorted().map { key -> LoLDependencyBucket in
            let values = groups[key] ?? []
            return LoLDependencyBucket(
                name: key,
                disposition: key,
                count: values.count,
                paths: values.map { $0.dependency.path }.sorted()
            )
        }

        var blockers: [String] = []
        var warnings: [String] = []
        var next: [String] = []

        if !image.isArm64 {
            blockers.append("Client executable is not ARM64.")
        }
        if image.encrypted {
            blockers.append("Client Mach-O is encrypted.")
        }
        if image.entryOffset == nil {
            blockers.append("LC_MAIN is unavailable.")
        }

        let blocked = assessments.filter { $0.disposition == .blocked }
        let unknown = assessments.filter { $0.disposition == .unknown }
        let shim = assessments.filter { $0.disposition == .shim }
        let partial = assessments.filter { $0.disposition == .partial }

        blockers.append(contentsOf: blocked.map { $0.dependency.path })
        if !unknown.isEmpty {
            warnings.append("\(unknown.count) dependency/dependencies need classification.")
            next.append("Classify unknown frameworks/dylibs from the real client.")
        }
        if !shim.isEmpty {
            next.append("Expand shims for \(shim.count) desktop framework dependency/dependencies.")
        }
        if !partial.isEmpty {
            next.append("Validate symbol-level compatibility for \(partial.count) partial runtime dependency/dependencies.")
        }

        let unresolved = fixupPlan?.unresolvedBindCount
        if let unresolved, unresolved > 0 {
            warnings.append("\(unresolved) imported symbol(s) are unresolved.")
            next.append("Resolve the remaining imported symbols against iOS runtime or DarwinBridge shims.")
        }

        if image.dependencies.contains(where: { $0.path.lowercased().contains("metal") }) {
            next.append("Validate the client Metal resource/shader path.")
        }
        if image.dependencies.contains(where: { $0.path.lowercased().contains("appkit") }) {
            next.append("Map the client AppKit surface onto the UIKit facade.")
        }

        if next.isEmpty {
            next.append("Dependency surface has no static blocker; proceed to deeper bundle/resource inspection.")
        }

        let candidate = image.isArm64 &&
                        !image.encrypted &&
                        image.entryOffset != nil &&
                        blocked.isEmpty

        return LoLStage15Report(
            candidate: candidate,
            architecture: image.isArm64 ? "ARM64" : String(format: "0x%08X", image.cpuType),
            fileSize: data.count,
            segmentCount: image.segments.count,
            dependencyCount: image.dependencies.count,
            rpathCount: image.rpaths.count,
            unresolvedImports: unresolved,
            importedSymbolCount: fixupPlan?.info.imports.count,
            buckets: buckets,
            blockers: blockers,
            warnings: warnings,
            nextActions: next
        )
    }
}
