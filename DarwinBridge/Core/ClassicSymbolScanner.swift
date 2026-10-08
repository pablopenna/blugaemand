import Foundation
import Darwin

struct ClassicImportedSymbol: Identifiable {
    let id = UUID()
    let name: String
    let libraryOrdinal: Int32
    let dependencyPath: String?
    let weak: Bool
    let hostResolved: Bool
}

struct ClassicSymbolSurfaceReport {
    let source: String
    let imported: [ClassicImportedSymbol]
    let resolvedCount: Int
    let unresolvedCount: Int
    let symtabPresent: Bool
    let dysymtabPresent: Bool
}

enum ClassicSymbolScannerError: Error, LocalizedError {
    case malformed(String)

    var errorDescription: String? {
        switch self {
        case .malformed(let text): return "Symbol scan failed: \(text)"
        }
    }
}

struct ClassicSymbolScanner {
    private static let lcSymtab: UInt32 = 0x2
    private static let lcDysymtab: UInt32 = 0xB
    private static let nStab: UInt8 = 0xE0
    private static let nType: UInt8 = 0x0E
    private static let nUndef: UInt8 = 0x00
    private static let nWeakRef: UInt16 = 0x0040

    static func scan(data source: Data,
                     image: MachOImageInfo) throws -> ClassicSymbolSurfaceReport {
        let data = try MachOParser.preferredArm64Slice(source)
        guard data.count >= 32 else { throw ClassicSymbolScannerError.malformed("short Mach-O") }

        let ncmds = Int(try u32(data, 16))
        var cursor = 32

        var symoff: UInt32?
        var nsyms: UInt32?
        var stroff: UInt32?
        var strsize: UInt32?
        var iundefsym: UInt32?
        var nundefsym: UInt32?
        var hasDysymtab = false

        for _ in 0..<ncmds {
            guard cursor + 8 <= data.count else {
                throw ClassicSymbolScannerError.malformed("truncated load command")
            }
            let cmd = try u32(data, cursor)
            let size = Int(try u32(data, cursor + 4))
            guard size >= 8, cursor + size <= data.count else {
                throw ClassicSymbolScannerError.malformed("invalid load command size")
            }

            if cmd == lcSymtab {
                guard size >= 24 else { throw ClassicSymbolScannerError.malformed("short LC_SYMTAB") }
                symoff = try u32(data, cursor + 8)
                nsyms = try u32(data, cursor + 12)
                stroff = try u32(data, cursor + 16)
                strsize = try u32(data, cursor + 20)
            } else if cmd == lcDysymtab {
                guard size >= 80 else { throw ClassicSymbolScannerError.malformed("short LC_DYSYMTAB") }
                hasDysymtab = true
                iundefsym = try u32(data, cursor + 40)
                nundefsym = try u32(data, cursor + 44)
            }
            cursor += size
        }

        guard let symoff, let nsyms, let stroff, let strsize else {
            return ClassicSymbolSurfaceReport(source: "none",
                                              imported: [],
                                              resolvedCount: 0,
                                              unresolvedCount: 0,
                                              symtabPresent: false,
                                              dysymtabPresent: hasDysymtab)
        }

        let symEnd = UInt64(symoff) + UInt64(nsyms) * 16
        let strEnd = UInt64(stroff) + UInt64(strsize)
        guard symEnd <= UInt64(data.count), strEnd <= UInt64(data.count) else {
            throw ClassicSymbolScannerError.malformed("symbol/string table exceeds file bounds")
        }

        let rangeStart = Int(iundefsym ?? 0)
        let rangeCount = Int(nundefsym ?? nsyms)
        let rangeEnd = min(Int(nsyms), rangeStart + rangeCount)

        var imported: [ClassicImportedSymbol] = []
        if rangeStart < rangeEnd {
            for index in rangeStart..<rangeEnd {
                let base = Int(symoff) + index * 16
                let strx = try u32(data, base)
                let type = data[base + 4]
                let sect = data[base + 5]
                let desc = try u16(data, base + 6)
                let value = try u64(data, base + 8)

                guard (type & nStab) == 0,
                      (type & nType) == nUndef,
                      sect == 0,
                      value == 0,
                      strx < strsize else { continue }

                let name = cString(data, Int(stroff + strx), Int(stroff + strsize))
                guard !name.isEmpty else { continue }

                let ordinal = Int32((desc >> 8) & 0xFF)
                let dependency: String?
                if ordinal > 0 && image.dependencies.indices.contains(Int(ordinal - 1)) {
                    dependency = image.dependencies[Int(ordinal - 1)].path
                } else {
                    dependency = nil
                }

                let resolved = RuntimeCompatibility.address(of: name) != nil
                imported.append(ClassicImportedSymbol(name: name,
                                                      libraryOrdinal: ordinal,
                                                      dependencyPath: dependency,
                                                      weak: (desc & nWeakRef) != 0,
                                                      hostResolved: resolved))
            }
        }

        let deduped = Dictionary(grouping: imported, by: { "\($0.libraryOrdinal):\($0.name)" })
            .compactMap { $0.value.first }
            .sorted { $0.name < $1.name }
        let resolved = deduped.filter(\.hostResolved).count

        return ClassicSymbolSurfaceReport(source: hasDysymtab ? "LC_SYMTAB + LC_DYSYMTAB" : "LC_SYMTAB",
                                          imported: deduped,
                                          resolvedCount: resolved,
                                          unresolvedCount: deduped.count - resolved,
                                          symtabPresent: true,
                                          dysymtabPresent: hasDysymtab)
    }

    private static func u16(_ data: Data, _ offset: Int) throws -> UInt16 {
        guard offset + 2 <= data.count else { throw ClassicSymbolScannerError.malformed("read past EOF") }
        return UInt16(data[offset]) | (UInt16(data[offset + 1]) << 8)
    }

    private static func u32(_ data: Data, _ offset: Int) throws -> UInt32 {
        guard offset + 4 <= data.count else { throw ClassicSymbolScannerError.malformed("read past EOF") }
        return data[offset..<offset+4].enumerated().reduce(UInt32(0)) {
            $0 | (UInt32($1.element) << UInt32($1.offset * 8))
        }
    }

    private static func u64(_ data: Data, _ offset: Int) throws -> UInt64 {
        guard offset + 8 <= data.count else { throw ClassicSymbolScannerError.malformed("read past EOF") }
        return data[offset..<offset+8].enumerated().reduce(UInt64(0)) {
            $0 | (UInt64($1.element) << UInt64($1.offset * 8))
        }
    }

    private static func cString(_ data: Data, _ start: Int, _ end: Int) -> String {
        guard start >= 0, start < end, start < data.count else { return "" }
        let bytes = data[start..<min(end, data.count)]
        return String(bytes: bytes.prefix { $0 != 0 }, encoding: .utf8) ?? ""
    }
}
