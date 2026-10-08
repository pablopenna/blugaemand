import Foundation

// Stage 21X-B policy: resolve the whole harvested compatibility surface in one pass.
// Coverage is derived from the runtime registry plus dependency buckets, not a hand-maintained symbol allowlist.
struct LoLShimCoverageItem: Identifiable {
    let id = UUID()
    let symbol: String
    let category: String
    let strategy: String
    let covered: Bool
}

struct LoLShimCoverageReport {
    let items: [LoLShimCoverageItem]
    let coveredCount: Int
    let totalCount: Int
    let projectedCoveragePercent: Int
    let remaining: [String]
}

struct LoLShimCoverageAnalyzer {
    static func analyze(plan: LoLCompatibilityPlan,
                        scan: DeepSymbolScanReport) -> LoLShimCoverageReport {
        let unresolved = scan.imports.filter { !$0.hostResolved }
        let items: [LoLShimCoverageItem] = unresolved.map { item in
            if LoLRuntimeShimRegistry.pointer(for: item.name) != nil {
                return LoLShimCoverageItem(symbol: item.name,
                                           category: item.dependencyPath ?? "batch shim",
                                           strategy: "Stage 21X harvested batch compatibility registry",
                                           covered: true)
            }

            if LoLRuntimeShimRegistry.isIntraImageWeak(item) {
                return LoLShimCoverageItem(symbol: item.name,
                                           category: "self/weak",
                                           strategy: "intra-image / weak resolution",
                                           covered: true)
            }

            let path = item.dependencyPath ?? "unknown"
            let bucket = plan.buckets.first { $0.dependency == path }
            let strategy: String
            if bucket != nil {
                strategy = "dependency bucket classified; no concrete runtime target yet"
            } else {
                strategy = "unresolved after batch classification"
            }

            return LoLShimCoverageItem(symbol: item.name,
                                       category: path,
                                       strategy: strategy,
                                       covered: false)
        }

        let covered = items.filter { $0.covered }.count
        let projectedResolved = scan.resolvedCount + covered
        let projected = scan.imports.isEmpty ? 0 :
            Int((Double(projectedResolved) / Double(scan.imports.count) * 100.0).rounded())
        let remaining = items.filter { !$0.covered }.map { $0.symbol }.sorted()

        return LoLShimCoverageReport(items: items,
                                     coveredCount: covered,
                                     totalCount: items.count,
                                     projectedCoveragePercent: projected,
                                     remaining: remaining)
    }
}
