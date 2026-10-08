import Foundation

struct BridgeProbe: Identifiable {
    let id = UUID()
    let name: String
    let requiredSymbols: [String]
    let resolvedSymbols: [String]

    var ready: Bool { Set(requiredSymbols).isSubset(of: Set(resolvedSymbols)) }
}

struct BridgeCompatibilityReport {
    let probes: [BridgeProbe]

    var readyCount: Int { probes.filter(\.ready).count }
    var totalCount: Int { probes.count }
}

struct BridgeCompatibilityAnalyzer {
    static func probe() -> BridgeCompatibilityReport {
        let definitions: [(String, [String])] = [
            ("Objective-C runtime", [
                "objc_getClass",
                "objc_msgSend",
                "sel_registerName",
                "class_getMethodImplementation"
            ]),
            ("Foundation/CoreFoundation", [
                "CFRetain",
                "CFRelease",
                "CFStringCreateWithCString",
                "CFRunLoopGetMain"
            ]),
            ("pthread/process", [
                "pthread_create",
                "pthread_join",
                "pthread_mutex_lock",
                "pthread_mutex_unlock"
            ]),
            ("filesystem", [
                "open",
                "close",
                "read",
                "write",
                "stat"
            ]),
            ("dispatch", [
                "dispatch_async_f",
                "dispatch_get_main_queue"
            ])
        ]

        let probes = definitions.map { name, symbols in
            let resolved = symbols.filter { RuntimeCompatibility.address(of: $0) != nil }
            return BridgeProbe(name: name,
                               requiredSymbols: symbols,
                               resolvedSymbols: resolved)
        }

        return BridgeCompatibilityReport(probes: probes)
    }
}
