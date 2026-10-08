import Foundation

struct LoLCompatibilityBucket: Identifiable {
    let id = UUID()
    let dependency: String
    let total: Int
    let resolved: Int
    let unresolved: Int
    let unresolvedSymbols: [String]
}

struct LoLCompatibilityPlan {
    let totalImports: Int
    let resolvedImports: Int
    let unresolvedImports: Int
    let coveragePercent: Int
    let selfUnresolved: Int
    let appKitUnresolved: Int
    let scriptingBridgeUnresolved: Int
    let coreServicesUnresolved: Int
    let otherUnresolved: Int
    let buckets: [LoLCompatibilityBucket]
    let priorities: [String]
}

struct LoLCompatibilityPlanner {
    static func make(from scan: DeepSymbolScanReport) -> LoLCompatibilityPlan {
        let groups = Dictionary(grouping: scan.imports) { item in
            item.dependencyPath ?? "self/unknown"
        }

        let buckets = groups.map { path, values -> LoLCompatibilityBucket in
            let unresolved = values.filter { !$0.hostResolved }
            return LoLCompatibilityBucket(
                dependency: path,
                total: values.count,
                resolved: values.count - unresolved.count,
                unresolved: unresolved.count,
                unresolvedSymbols: unresolved.map(\.name).sorted()
            )
        }.sorted {
            if $0.unresolved == $1.unresolved { return $0.dependency < $1.dependency }
            return $0.unresolved > $1.unresolved
        }

        func count(_ needle: String) -> Int {
            scan.imports.filter {
                !$0.hostResolved && ($0.dependencyPath ?? "").lowercased().contains(needle)
            }.count
        }

        let selfCount = scan.imports.filter {
            !$0.hostResolved && (($0.dependencyPath ?? "").lowercased() == "self" ||
                                 ($0.dependencyPath ?? "").isEmpty)
        }.count
        let appKit = count("appkit.framework")
        let scripting = count("scriptingbridge.framework")
        let coreServices = count("coreservices.framework")
        let known = selfCount + appKit + scripting + coreServices
        let other = max(0, scan.unresolvedCount - known)

        var priorities: [String] = []
        if selfCount > 0 {
            priorities.append("Treat self/weak C++ symbols as intra-image definitions before generating host shims.")
        }
        if appKit > 0 {
            priorities.append("Expand AppKit/UIKit facade for the exact unresolved AppKit classes/globals used by the client.")
        }
        if scripting > 0 {
            priorities.append("Provide a limited ScriptingBridge compatibility surface for the client-visible classes.")
        }
        if coreServices > 0 {
            priorities.append("Map CoreServices calls onto Foundation/UTType/FileManager equivalents.")
        }
        if scan.imports.contains(where: { !$0.hostResolved && $0.name == "dyld_stub_binder" }) {
            priorities.append("Route dyld_stub_binder through the DarwinBridge symbol/binding path.")
        }
        if other > 0 {
            priorities.append("Resolve the remaining \(other) symbols by dependency bucket.")
        }

        let coverage = scan.imports.isEmpty ? 0 : Int((Double(scan.resolvedCount) / Double(scan.imports.count) * 100.0).rounded())

        return LoLCompatibilityPlan(
            totalImports: scan.imports.count,
            resolvedImports: scan.resolvedCount,
            unresolvedImports: scan.unresolvedCount,
            coveragePercent: coverage,
            selfUnresolved: selfCount,
            appKitUnresolved: appKit,
            scriptingBridgeUnresolved: scripting,
            coreServicesUnresolved: coreServices,
            otherUnresolved: other,
            buckets: buckets,
            priorities: priorities
        )
    }
}
