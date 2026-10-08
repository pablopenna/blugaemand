import Foundation

struct DeepImportedSymbol: Identifiable, Hashable {
    let id = UUID()
    let name: String
    let libraryOrdinal: Int32
    let dependencyPath: String?
    let weak: Bool
    let source: String
    let hostResolved: Bool
}

struct DeepSymbolScanReport {
    let sources: [String]
    let imports: [DeepImportedSymbol]
    let resolvedCount: Int
    let unresolvedCount: Int
    let chainedImportCount: Int
    let classicImportCount: Int
    let bindImportCount: Int
    let weakBindImportCount: Int
    let lazyBindImportCount: Int
    let exportCount: Int
    let notes: [String]
}

enum DeepSymbolScannerError: Error, LocalizedError {
    case malformed(String)

    var errorDescription: String? {
        switch self {
        case .malformed(let text): return "Deep symbol scan failed: \(text)"
        }
    }
}

struct DeepSymbolScanner {
    private static let lcDyldInfo: UInt32 = 0x22
    private static let lcDyldInfoOnly: UInt32 = 0x80000022

    private static let bindOpcodeMask: UInt8 = 0xF0
    private static let bindImmMask: UInt8 = 0x0F
    private static let opDone: UInt8 = 0x00
    private static let opSetDylibOrdinalImm: UInt8 = 0x10
    private static let opSetDylibOrdinalULEB: UInt8 = 0x20
    private static let opSetDylibSpecialImm: UInt8 = 0x30
    private static let opSetSymbolTrailingFlagsImm: UInt8 = 0x40
    private static let opSetTypeImm: UInt8 = 0x50
    private static let opSetAddendSLEB: UInt8 = 0x60
    private static let opSetSegmentAndOffsetULEB: UInt8 = 0x70
    private static let opAddAddrULEB: UInt8 = 0x80
    private static let opDoBind: UInt8 = 0x90
    private static let opDoBindAddAddrULEB: UInt8 = 0xA0
    private static let opDoBindAddAddrImmScaled: UInt8 = 0xB0
    private static let opDoBindULEBTimesSkippingULEB: UInt8 = 0xC0

    static func scan(data source: Data,
                     image: MachOImageInfo) throws -> DeepSymbolScanReport {
        let data = try MachOParser.preferredArm64Slice(source)

        var all: [DeepImportedSymbol] = []
        var sources: [String] = []
        var notes: [String] = []

        let classic = try ClassicSymbolScanner.scan(data: data, image: image)
        if classic.symtabPresent {
            sources.append(classic.source)
            all.append(contentsOf: classic.imported.map {
                DeepImportedSymbol(name: $0.name,
                                   libraryOrdinal: $0.libraryOrdinal,
                                   dependencyPath: $0.dependencyPath,
                                   weak: $0.weak,
                                   source: "classic",
                                   hostResolved: $0.hostResolved)
            })
        }

        var chainedCount = 0
        if image.chainedFixups != nil {
            do {
                let chained = try ChainedFixupsParser.parse(data: data, image: image)
                chainedCount = chained.imports.count
                if !chained.imports.isEmpty {
                    sources.append("LC_DYLD_CHAINED_FIXUPS")
                    for item in chained.imports {
                        let dep = dependencyPath(for: item.libraryOrdinal, image: image)
                        all.append(DeepImportedSymbol(name: item.name,
                                                      libraryOrdinal: item.libraryOrdinal,
                                                      dependencyPath: dep,
                                                      weak: item.weak,
                                                      source: "chained-fixups",
                                                      hostResolved: RuntimeCompatibility.address(of: item.name) != nil))
                    }
                }
            } catch {
                notes.append("Chained fixups scan: \(error.localizedDescription)")
            }
        }

        let dyld = try dyldInfoLocations(data)
        var bindCount = 0
        var weakCount = 0
        var lazyCount = 0
        var exportCount = 0

        if let bind = dyld.bind, bind.size > 0 {
            let values = try parseBindStream(data: data, location: bind, image: image, source: "dyld-bind")
            bindCount = values.count
            all.append(contentsOf: values)
            sources.append("LC_DYLD_INFO bind")
        }

        if let weak = dyld.weakBind, weak.size > 0 {
            let values = try parseBindStream(data: data, location: weak, image: image, source: "dyld-weak-bind")
            weakCount = values.count
            all.append(contentsOf: values)
            sources.append("LC_DYLD_INFO weak_bind")
        }

        if let lazy = dyld.lazyBind, lazy.size > 0 {
            let values = try parseBindStream(data: data, location: lazy, image: image, source: "dyld-lazy-bind")
            lazyCount = values.count
            all.append(contentsOf: values)
            sources.append("LC_DYLD_INFO lazy_bind")
        }

        if let exports = dyld.export, exports.size > 0 {
            exportCount = (try? countExportTrie(data: data, location: exports)) ?? 0
            sources.append("LC_DYLD_INFO export")
        }

        let merged = merge(all)
        let resolved = merged.filter(\.hostResolved).count

        if merged.isEmpty {
            notes.append("No imported symbols were found in classic tables, chained fixups, or LC_DYLD_INFO bind streams.")
        } else {
            notes.append("Merged and de-duplicated imports from every supported Mach-O metadata source.")
        }

        return DeepSymbolScanReport(sources: Array(Set(sources)).sorted(),
                                    imports: merged,
                                    resolvedCount: resolved,
                                    unresolvedCount: merged.count - resolved,
                                    chainedImportCount: chainedCount,
                                    classicImportCount: classic.imported.count,
                                    bindImportCount: bindCount,
                                    weakBindImportCount: weakCount,
                                    lazyBindImportCount: lazyCount,
                                    exportCount: exportCount,
                                    notes: notes)
    }

    private struct Location {
        let offset: Int
        let size: Int
    }

    private struct DyldLocations {
        let bind: Location?
        let weakBind: Location?
        let lazyBind: Location?
        let export: Location?
    }

    private static func dyldInfoLocations(_ data: Data) throws -> DyldLocations {
        guard data.count >= 32 else { throw DeepSymbolScannerError.malformed("short Mach-O") }
        let ncmds = Int(try u32(data, 16))
        var cursor = 32

        for _ in 0..<ncmds {
            guard cursor + 8 <= data.count else { throw DeepSymbolScannerError.malformed("truncated load command") }
            let cmd = try u32(data, cursor)
            let size = Int(try u32(data, cursor + 4))
            guard size >= 8, cursor + size <= data.count else { throw DeepSymbolScannerError.malformed("invalid load command") }

            if cmd == lcDyldInfo || cmd == lcDyldInfoOnly {
                guard size >= 48 else { throw DeepSymbolScannerError.malformed("short LC_DYLD_INFO") }
                let bind = try location(data, cursor + 16, cursor + 20)
                let weak = try location(data, cursor + 24, cursor + 28)
                let lazy = try location(data, cursor + 32, cursor + 36)
                let export = try location(data, cursor + 40, cursor + 44)
                return DyldLocations(bind: bind, weakBind: weak, lazyBind: lazy, export: export)
            }
            cursor += size
        }
        return DyldLocations(bind: nil, weakBind: nil, lazyBind: nil, export: nil)
    }

    private static func location(_ data: Data, _ offField: Int, _ sizeField: Int) throws -> Location? {
        let off = Int(try u32(data, offField))
        let size = Int(try u32(data, sizeField))
        if size == 0 { return nil }
        guard off >= 0, size >= 0, off + size <= data.count else {
            throw DeepSymbolScannerError.malformed("dyld metadata range exceeds file")
        }
        return Location(offset: off, size: size)
    }

    private static func parseBindStream(data: Data,
                                        location: Location,
                                        image: MachOImageInfo,
                                        source: String) throws -> [DeepImportedSymbol] {
        let end = location.offset + location.size
        var cursor = location.offset
        var ordinal: Int32 = 0
        var symbol: String?
        var weak = false
        var result: [DeepImportedSymbol] = []
        let pointerSize = 8

        func appendCurrent() {
            guard let symbol, !symbol.isEmpty else { return }
            let dep = dependencyPath(for: ordinal, image: image)
            result.append(DeepImportedSymbol(name: symbol,
                                             libraryOrdinal: ordinal,
                                             dependencyPath: dep,
                                             weak: weak,
                                             source: source,
                                             hostResolved: RuntimeCompatibility.address(of: symbol) != nil))
        }

        while cursor < end {
            let byte = data[cursor]
            cursor += 1
            let opcode = byte & bindOpcodeMask
            let imm = byte & bindImmMask

            switch opcode {
            case opDone:
                symbol = nil
                weak = false

            case opSetDylibOrdinalImm:
                ordinal = Int32(imm)

            case opSetDylibOrdinalULEB:
                ordinal = Int32(try readULEB(data, &cursor, end))

            case opSetDylibSpecialImm:
                if imm == 0 {
                    ordinal = 0
                } else {
                    let signed = Int8(bitPattern: imm | 0xF0)
                    ordinal = Int32(signed)
                }

            case opSetSymbolTrailingFlagsImm:
                let start = cursor
                while cursor < end && data[cursor] != 0 { cursor += 1 }
                guard cursor < end else { throw DeepSymbolScannerError.malformed("unterminated bind symbol") }
                symbol = String(bytes: data[start..<cursor], encoding: .utf8) ?? ""
                cursor += 1
                weak = (imm & 0x1) != 0

            case opSetTypeImm:
                break

            case opSetAddendSLEB:
                _ = try readSLEB(data, &cursor, end)

            case opSetSegmentAndOffsetULEB:
                _ = try readULEB(data, &cursor, end)

            case opAddAddrULEB:
                _ = try readULEB(data, &cursor, end)

            case opDoBind:
                appendCurrent()

            case opDoBindAddAddrULEB:
                appendCurrent()
                _ = try readULEB(data, &cursor, end)

            case opDoBindAddAddrImmScaled:
                appendCurrent()
                _ = pointerSize * Int(imm)

            case opDoBindULEBTimesSkippingULEB:
                let count = Int(try readULEB(data, &cursor, end))
                _ = try readULEB(data, &cursor, end)
                if count > 0 {
                    for _ in 0..<count { appendCurrent() }
                }

            default:
                throw DeepSymbolScannerError.malformed(String(format: "unsupported bind opcode 0x%02X", opcode))
            }
        }
        return result
    }

    private static func countExportTrie(data: Data, location: Location) throws -> Int {
        var visited = Set<Int>()
        var count = 0

        func walk(_ nodeOffset: Int) throws {
            let absolute = location.offset + nodeOffset
            guard nodeOffset >= 0, absolute < location.offset + location.size else {
                throw DeepSymbolScannerError.malformed("export trie node out of bounds")
            }
            if visited.contains(nodeOffset) { return }
            visited.insert(nodeOffset)

            var cursor = absolute
            let end = location.offset + location.size
            let terminalSize = Int(try readULEB(data, &cursor, end))
            if terminalSize > 0 {
                count += 1
                cursor += terminalSize
                guard cursor <= end else { throw DeepSymbolScannerError.malformed("export terminal exceeds trie") }
            }
            guard cursor < end else { return }
            let childCount = Int(data[cursor])
            cursor += 1

            for _ in 0..<childCount {
                while cursor < end && data[cursor] != 0 { cursor += 1 }
                guard cursor < end else { throw DeepSymbolScannerError.malformed("unterminated export edge") }
                cursor += 1
                let childOffset = Int(try readULEB(data, &cursor, end))
                try walk(childOffset)
            }
        }

        try walk(0)
        return count
    }

    private static func merge(_ imports: [DeepImportedSymbol]) -> [DeepImportedSymbol] {
        var map: [String: DeepImportedSymbol] = [:]
        for item in imports {
            let key = "\(item.libraryOrdinal):\(item.name)"
            if let current = map[key] {
                map[key] = DeepImportedSymbol(name: item.name,
                                              libraryOrdinal: item.libraryOrdinal,
                                              dependencyPath: item.dependencyPath ?? current.dependencyPath,
                                              weak: item.weak || current.weak,
                                              source: current.source + "+" + item.source,
                                              hostResolved: item.hostResolved || current.hostResolved)
            } else {
                map[key] = item
            }
        }
        return map.values.sorted {
            if $0.libraryOrdinal == $1.libraryOrdinal { return $0.name < $1.name }
            return $0.libraryOrdinal < $1.libraryOrdinal
        }
    }

    private static func dependencyPath(for ordinal: Int32, image: MachOImageInfo) -> String? {
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
        return image.dependencies.indices.contains(index) ? image.dependencies[index].path : nil
    }

    private static func readULEB(_ data: Data, _ cursor: inout Int, _ end: Int) throws -> UInt64 {
        var result: UInt64 = 0
        var bit = 0
        while cursor < end {
            let byte = data[cursor]
            cursor += 1
            result |= UInt64(byte & 0x7F) << UInt64(bit)
            if (byte & 0x80) == 0 { return result }
            bit += 7
            if bit > 63 { throw DeepSymbolScannerError.malformed("ULEB128 overflow") }
        }
        throw DeepSymbolScannerError.malformed("truncated ULEB128")
    }

    private static func readSLEB(_ data: Data, _ cursor: inout Int, _ end: Int) throws -> Int64 {
        var result: Int64 = 0
        var bit = 0
        var byte: UInt8 = 0
        repeat {
            guard cursor < end else { throw DeepSymbolScannerError.malformed("truncated SLEB128") }
            byte = data[cursor]
            cursor += 1
            result |= Int64(byte & 0x7F) << Int64(bit)
            bit += 7
        } while (byte & 0x80) != 0 && bit < 64

        if bit < 64 && (byte & 0x40) != 0 {
            result |= -(1 << Int64(bit))
        }
        return result
    }

    private static func u32(_ data: Data, _ offset: Int) throws -> UInt32 {
        guard offset + 4 <= data.count else { throw DeepSymbolScannerError.malformed("read past EOF") }
        return data[offset..<offset+4].enumerated().reduce(UInt32(0)) {
            $0 | (UInt32($1.element) << UInt32($1.offset * 8))
        }
    }
}
