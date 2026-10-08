import Foundation

struct LoLRuntimeEvent: Identifiable {
    let id = UUID()
    let timestamp: Date
    let phase: String
    let detail: String
}

final class LoLRuntimeEventLog: ObservableObject {
    static let shared = LoLRuntimeEventLog()

    @Published private(set) var events: [LoLRuntimeEvent] = []

    func record(_ phase: String, _ detail: String) {
        events.append(LoLRuntimeEvent(timestamp: Date(), phase: phase, detail: detail))
        if events.count > 200 {
            events.removeFirst(events.count - 200)
        }
    }

    func clear() {
        events.removeAll()
    }
}

@_cdecl("DBLoLRuntimeCheckpoint")
public func DBLoLRuntimeCheckpoint(_ phase: UnsafePointer<CChar>?,
                                   _ detail: UnsafePointer<CChar>?) {
    let p = phase.map { String(cString: $0) } ?? "unknown"
    let d = detail.map { String(cString: $0) } ?? ""
    DispatchQueue.main.async {
        LoLRuntimeEventLog.shared.record(p, d)
    }
}

struct LoLExternalHandoffReadiness {
    let ready: Bool
    let expectedEntry: UInt64?
    let importCount: Int
    let runtimeShimCount: Int
    let note: String
}

struct LoLExternalHandoffObserver {
    static func prepare(image: MachOImageInfo,
                        deepScan: DeepSymbolScanReport?,
                        runtimeValidation: LoLRuntimeShimValidation?,
                        backend: LiveContainerBackendReport?) -> LoLExternalHandoffReadiness {
        let ready = backend?.compatible == true &&
                    runtimeValidation?.remaining.isEmpty == true &&
                    (deepScan?.imports.count ?? 0) > 0 &&
                    image.entryOffset != nil

        LoLRuntimeEventLog.shared.clear()
        LoLRuntimeEventLog.shared.record("handoff-prep", ready ? "READY" : "BLOCKED")
        LoLRuntimeEventLog.shared.record("imports", "\(deepScan?.imports.count ?? 0)")
        LoLRuntimeEventLog.shared.record("runtime-shims", "\(runtimeValidation?.totalCovered ?? 0)")
        if let entry = image.entryOffset {
            LoLRuntimeEventLog.shared.record("expected-lc-main", String(format: "0x%llX", entry))
        }

        return LoLExternalHandoffReadiness(
            ready: ready,
            expectedEntry: image.entryOffset,
            importCount: deepScan?.imports.count ?? 0,
            runtimeShimCount: runtimeValidation?.totalCovered ?? 0,
            note: ready
                ? "Static handoff plan prepared. No external guest instruction or initializer has been observed."
                : "One or more prerequisites for external handoff observation are missing."
        )
    }
}
