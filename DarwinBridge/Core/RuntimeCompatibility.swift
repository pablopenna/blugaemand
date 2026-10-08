import Foundation
import Darwin

struct RuntimeSymbolProbe: Identifiable {
    let id = UUID()
    let symbol: String
    let address: UInt64?
    let category: String

    var resolved: Bool { address != nil }
}

struct RuntimeCompatibilityReport {
    let probes: [RuntimeSymbolProbe]

    var resolvedCount: Int { probes.filter(\.resolved).count }
    var totalCount: Int { probes.count }
    var coreReady: Bool {
        let required = Set(["malloc", "free", "strlen", "memcpy", "memset"])
        let resolved = Set(probes.filter(\.resolved).map(\.symbol))
        return required.isSubset(of: resolved)
    }
}

struct RuntimeCompatibility {
    static let coreSymbols: [(String, String)] = [
        ("malloc", "libc"),
        ("free", "libc"),
        ("strlen", "libc"),
        ("memcpy", "libc"),
        ("memset", "libc"),
        ("calloc", "libc"),
        ("realloc", "libc"),
        ("pthread_create", "pthread"),
        ("pthread_join", "pthread"),
        ("dispatch_async_f", "libdispatch"),
        ("getenv", "libc"),
        ("setenv", "libc")
    ]

    static func probeCoreRuntime() -> RuntimeCompatibilityReport {
        let probes = coreSymbols.map { name, category in
            RuntimeSymbolProbe(symbol: name,
                               address: address(of: name),
                               category: category)
        }
        return RuntimeCompatibilityReport(probes: probes)
    }

    static func address(of symbol: String) -> UInt64? {
        guard let handle = dlopen(nil, RTLD_NOW) else { return nil }
        defer { dlclose(handle) }

        let candidates: [String]
        if symbol.hasPrefix("_") {
            candidates = [symbol, String(symbol.dropFirst())]
        } else {
            candidates = [symbol, "_" + symbol]
        }

        for candidate in candidates {
            if let pointer = dlsym(handle, candidate) {
                return UInt64(UInt(bitPattern: pointer))
            }
        }
        return nil
    }

    static func pointer(to symbol: String) -> UnsafeMutableRawPointer? {
        guard let value = address(of: symbol) else { return nil }
        return UnsafeMutableRawPointer(bitPattern: UInt(value))
    }
}
