import Foundation

struct Stage9ReadinessReport {
    let readyForCLIBringUp: Bool
    let dependencyCount: Int
    let nativeCount: Int
    let shimCount: Int
    let partialCount: Int
    let blockedCount: Int
    let unknownCount: Int
    let unresolvedImports: Int
    let notes: [String]
}

struct Stage9ReadinessAnalyzer {
    static func analyze(image: MachOImageInfo, fixupPlan: FixupPlan?) -> Stage9ReadinessReport {
        let assessments = image.dependencies.map(FrameworkShimRegistry.assess)
        let native = assessments.filter { $0.disposition == .native }.count
        let shim = assessments.filter { $0.disposition == .shim }.count
        let partial = assessments.filter { $0.disposition == .partial }.count
        let blocked = assessments.filter { $0.disposition == .blocked }.count
        let unknown = assessments.filter { $0.disposition == .unknown }.count
        let unresolved = fixupPlan?.unresolvedBindCount ?? 0

        var notes: [String] = []
        if blocked > 0 { notes.append("\(blocked) desktop dependency/dependencies are currently blocked.") }
        if unknown > 0 { notes.append("\(unknown) dependency/dependencies still need classification.") }
        if unresolved > 0 { notes.append("\(unresolved) imported symbol(s) remain unresolved.") }
        if image.entryOffset == nil { notes.append("LC_MAIN is missing.") }
        if image.encrypted { notes.append("Encrypted Mach-O cannot enter the bring-up path.") }
        if !image.isArm64 { notes.append("CPU architecture is not ARM64.") }

        let ready = image.isArm64 &&
                    !image.encrypted &&
                    image.entryOffset != nil &&
                    blocked == 0 &&
                    unknown == 0 &&
                    unresolved == 0

        if ready {
            notes.append("Dependency surface is ready for the first lightweight macOS CLI bring-up.")
        }

        return Stage9ReadinessReport(
            readyForCLIBringUp: ready,
            dependencyCount: assessments.count,
            nativeCount: native,
            shimCount: shim,
            partialCount: partial,
            blockedCount: blocked,
            unknownCount: unknown,
            unresolvedImports: unresolved,
            notes: notes
        )
    }
}
