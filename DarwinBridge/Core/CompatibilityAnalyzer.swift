import Foundation

struct CompatibilityAnalyzer {
    static func analyze(_ image: MachOImageInfo) -> CompatibilityReport {
        let assessments = image.dependencies.map(FrameworkShimRegistry.assess)
        var blockers: [String] = []
        var warnings: [String] = []
        var score = 100

        if !image.isArm64 {
            blockers.append("Guest binary is not ARM64. This prototype intentionally avoids CPU emulation.")
            score -= 60
        }
        if image.encrypted {
            blockers.append("Mach-O is encrypted (cryptid != 0); decrypted code is required before manual mapping.")
            score -= 40
        }
        if image.entryOffset == nil {
            warnings.append("LC_MAIN was not found. Entry resolution will require LC_UNIXTHREAD or another startup path.")
            score -= 10
        }
        if let platform = image.platform, platform != 1 {
            warnings.append("LC_BUILD_VERSION platform is \(platform), not macOS (1).")
            score -= 10
        }

        for assessment in assessments {
            switch assessment.disposition {
            case .blocked:
                blockers.append("\(assessment.dependency.path): \(assessment.note)")
                score -= 18
            case .shim:
                warnings.append("\(assessment.dependency.path): shim required")
                score -= 8
            case .partial:
                warnings.append("\(assessment.dependency.path): partial compatibility")
                score -= 4
            case .unknown:
                warnings.append("\(assessment.dependency.path): unknown dependency")
                score -= 5
            case .native:
                break
            }
        }

        score = max(0, min(100, score))
        return CompatibilityReport(score: score,
                                   executableCandidate: blockers.isEmpty && image.isArm64 && !image.encrypted,
                                   blockers: blockers,
                                   warnings: warnings,
                                   assessments: assessments)
    }
}
