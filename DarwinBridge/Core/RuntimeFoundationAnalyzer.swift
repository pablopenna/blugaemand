import Foundation
import Darwin

struct RuntimeFoundationItem: Identifiable {
    let id = UUID()
    let name: String
    let ready: Bool
    let detail: String
}

struct RuntimeFoundationReport {
    let items: [RuntimeFoundationItem]
    let bridgeReport: BridgeCompatibilityReport
    let stage9: Stage9ReadinessReport
    let overallReady: Bool
    let readyCount: Int
    let totalCount: Int
}

struct RuntimeFoundationAnalyzer {
    static func analyze(image: MachOImageInfo,
                        fixupPlan: FixupPlan?) -> RuntimeFoundationReport {
        let bridgeReport = BridgeCompatibilityAnalyzer.probe()
        let stage9 = Stage9ReadinessAnalyzer.analyze(image: image, fixupPlan: fixupPlan)

        var items: [RuntimeFoundationItem] = bridgeReport.probes.map {
            RuntimeFoundationItem(
                name: $0.name,
                ready: $0.ready,
                detail: "\($0.resolvedSymbols.count)/\($0.requiredSymbols.count) required symbols"
            )
        }

        let tempReady = probeSandboxFilesystem()
        items.append(RuntimeFoundationItem(
            name: "Sandbox filesystem",
            ready: tempReady,
            detail: tempReady ? "create/write/read/remove passed" : "sandbox file round-trip failed"
        ))

        let bundleReady = !Bundle.main.bundlePath.isEmpty &&
                          FileManager.default.fileExists(atPath: Bundle.main.bundlePath)
        items.append(RuntimeFoundationItem(
            name: "Bundle path virtualization base",
            ready: bundleReady,
            detail: Bundle.main.bundlePath
        ))

        let metalReady = probeMetalRuntime()
        items.append(RuntimeFoundationItem(
            name: "Metal runtime surface",
            ready: metalReady,
            detail: metalReady ? "Metal framework + default device entry point available" : "Metal runtime probe failed"
        ))

        let localeReady = !Locale.current.identifier.isEmpty &&
                          !TimeZone.current.identifier.isEmpty
        items.append(RuntimeFoundationItem(
            name: "Locale/time environment",
            ready: localeReady,
            detail: "\(Locale.current.identifier) / \(TimeZone.current.identifier)"
        ))

        let processReady = ProcessInfo.processInfo.activeProcessorCount > 0
        items.append(RuntimeFoundationItem(
            name: "Process environment",
            ready: processReady,
            detail: "\(ProcessInfo.processInfo.activeProcessorCount) logical CPU(s)"
        ))

        let readyCount = items.filter(\.ready).count
        let overall = stage9.readyForCLIBringUp &&
                      readyCount == items.count

        return RuntimeFoundationReport(
            items: items,
            bridgeReport: bridgeReport,
            stage9: stage9,
            overallReady: overall,
            readyCount: readyCount,
            totalCount: items.count
        )
    }

    private static func probeSandboxFilesystem() -> Bool {
        let fm = FileManager.default
        let base = fm.temporaryDirectory
        let url = base.appendingPathComponent("darwinbridge-stage10-probe")
        let payload = Data("DarwinBridge".utf8)

        do {
            try payload.write(to: url, options: .atomic)
            let roundTrip = try Data(contentsOf: url)
            try? fm.removeItem(at: url)
            return roundTrip == payload
        } catch {
            try? fm.removeItem(at: url)
            return false
        }
    }

    private static func probeMetalRuntime() -> Bool {
        guard let handle = dlopen("/System/Library/Frameworks/Metal.framework/Metal", RTLD_NOW) else {
            return false
        }
        defer { dlclose(handle) }
        return dlsym(handle, "MTLCreateSystemDefaultDevice") != nil
    }
}
