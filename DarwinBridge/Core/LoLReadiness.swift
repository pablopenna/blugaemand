import Foundation

struct LoLReadinessReport {
    let tier: String
    let blockers: [String]
    let requiredBridges: [String]
    let notes: [String]
}

struct LoLReadinessAnalyzer {
    static func analyze(image: MachOImageInfo,
                        fixupPlan: FixupPlan?,
                        stage9: Stage9ReadinessReport?) -> LoLReadinessReport {
        var blockers: [String] = []
        var bridges: [String] = []
        var notes: [String] = []

        if !image.isArm64 { blockers.append("ARM64 macOS executable required") }
        if image.encrypted { blockers.append("Encrypted Mach-O") }
        if image.entryOffset == nil { blockers.append("LC_MAIN unavailable") }
        if (fixupPlan?.unresolvedBindCount ?? 0) > 0 {
            blockers.append("Unresolved imported symbols")
        }

        let assessments = image.dependencies.map(FrameworkShimRegistry.assess)
        for item in assessments {
            switch item.disposition {
            case .shim:
                bridges.append(item.replacement ?? item.dependency.path)
            case .partial:
                bridges.append(item.replacement ?? item.dependency.path)
            case .blocked:
                blockers.append(item.dependency.path)
            case .unknown:
                blockers.append("Unclassified dependency: \(item.dependency.path)")
            case .native:
                break
            }
        }

        bridges.append(contentsOf: [
            "Objective-C runtime compatibility",
            "Foundation/CoreFoundation compatibility",
            "pthread/TLS and process startup",
            "filesystem + bundle path virtualization",
            "AppKit/Cocoa facade",
            "Metal graphics compatibility",
            "input/window/event translation"
        ])

        var seen = Set<String>()
        bridges = bridges.filter { seen.insert($0).inserted }

        if stage9?.readyForCLIBringUp == true {
            notes.append("Lightweight CLI dependency surface is ready.")
        }
        notes.append("League bring-up should proceed by importing the actual ARM64 client executable and classifying its dependency surface before adding shims.")

        let tier: String
        if !blockers.isEmpty {
            tier = "BLOCKED"
        } else if stage9?.readyForCLIBringUp == true {
            tier = "FOUNDATION READY"
        } else {
            tier = "ANALYSIS"
        }

        return LoLReadinessReport(tier: tier,
                                  blockers: blockers,
                                  requiredBridges: bridges,
                                  notes: notes)
    }
}
