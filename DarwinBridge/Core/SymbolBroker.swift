import Foundation
import Darwin

struct SymbolBroker {
    static func resolve(imports: [ChainedImport],
                        image: MachOImageInfo) -> [SymbolResolution] {
        imports.map { item in
            let dependency = dependencyPath(for: item.libraryOrdinal, image: image)
            let host = lookup(item.name)
#if canImport(UIKit)
            let shim = host == nil ? LoLRuntimeShimRegistry.pointer(for: item.name) : nil
#else
            // Host-side parser smoke tests compile without UIKit/LoLRuntimeShims.
            let shim: UnsafeMutableRawPointer? = nil
#endif
            let address = host ?? shim
            return SymbolResolution(name: item.name,
                                    libraryOrdinal: item.libraryOrdinal,
                                    dependencyPath: dependency,
                                    address: address.map { UInt64(UInt(bitPattern: $0)) },
                                    source: host != nil ? "process-global" : (shim != nil ? "darwinbridge-batch-shim" : "unresolved"))
        }
    }

    private static func dependencyPath(for ordinal: Int32,
                                       image: MachOImageInfo) -> String? {
        guard ordinal > 0 else {
            switch ordinal {
            case 0: return "self"
            case -1: return "main executable"
            case -2: return "flat lookup"
            case -3: return "weak lookup"
            default: return nil
            }
        }
        let index = Int(ordinal - 1)
        guard image.dependencies.indices.contains(index) else { return nil }
        return image.dependencies[index].path
    }

    private static func lookup(_ symbol: String) -> UnsafeMutableRawPointer? {
        guard let processHandle = dlopen(nil, RTLD_NOW) else { return nil }
        defer { dlclose(processHandle) }
        if let exact = dlsym(processHandle, symbol) { return exact }
        if symbol.hasPrefix("_") { return dlsym(processHandle, String(symbol.dropFirst())) }
        return dlsym(processHandle, "_" + symbol)
    }
}
