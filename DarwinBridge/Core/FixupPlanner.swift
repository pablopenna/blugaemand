import Foundation

struct FixupPlan {
    let info: ChainedFixupsInfo
    let resolutions: [SymbolResolution]
    let supportedFixupCount: Int
    let unresolvedBindCount: Int
    let canApplyStage1: Bool
    let notes: [String]
}

struct FixupPlanner {
    static func make(data: Data, image: MachOImageInfo) throws -> FixupPlan {
        let info = try ChainedFixupsParser.parse(data: data, image: image)
        let resolutions = SymbolBroker.resolve(imports: info.imports, image: image)

        var unresolved = 0
        var notes: [String] = []

        for fixup in info.fixups {
            if case .bind(let importIndex, _) = fixup.action {
                guard info.imports.indices.contains(importIndex) else {
                    unresolved += 1
                    continue
                }
                if resolutions[importIndex].address == nil {
                    unresolved += 1
                }
            }
        }

        if !info.unsupportedPointerFormats.isEmpty {
            let formats = info.unsupportedPointerFormats
                .sorted()
                .map(String.init)
                .joined(separator: ", ")
            notes.append("Unsupported pointer formats present: \(formats)")
        }
        if unresolved > 0 {
            notes.append("\(unresolved) bind fixups cannot yet resolve to host symbols.")
        }
        if info.fixups.isEmpty {
            notes.append("No chained pointer locations were found.")
        }

        return FixupPlan(info: info,
                         resolutions: resolutions,
                         supportedFixupCount: info.fixups.count,
                         unresolvedBindCount: unresolved,
                         canApplyStage1: info.unsupportedPointerFormats.isEmpty && unresolved == 0,
                         notes: notes)
    }
}
