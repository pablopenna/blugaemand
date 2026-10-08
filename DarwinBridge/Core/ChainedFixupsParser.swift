import Foundation

enum ChainedFixupsError: Error, LocalizedError {
    case missingCommand
    case malformed(String)
    case unsupportedImportFormat(UInt32)

    var errorDescription: String? {
        switch self {
        case .missingCommand:
            return "LC_DYLD_CHAINED_FIXUPS is not present."
        case .malformed(let reason):
            return "Malformed chained fixups: \(reason)"
        case .unsupportedImportFormat(let format):
            return "Unsupported chained import format \(format)."
        }
    }
}

struct ChainedFixupsParser {
    private static let pageStartNone: UInt16 = 0xFFFF
    private static let pageStartMulti: UInt16 = 0x8000

    static func parse(data: Data, image: MachOImageInfo) throws -> ChainedFixupsInfo {
        guard let location = image.chainedFixups else { throw ChainedFixupsError.missingCommand }
        let base = Int(location.fileOffset)
        let end = base + Int(location.size)
        guard base >= 0, end <= data.count, Int(location.size) >= 28 else {
            throw ChainedFixupsError.malformed("fixups payload is out of bounds")
        }

        let version = try u32(data, base)
        let startsOffset = Int(try u32(data, base + 4))
        let importsOffset = Int(try u32(data, base + 8))
        let symbolsOffset = Int(try u32(data, base + 12))
        let importsCount = Int(try u32(data, base + 16))
        let importsFormatRaw = try u32(data, base + 20)
        guard let importsFormat = ChainedImportFormat(rawValue: importsFormatRaw) else {
            throw ChainedFixupsError.unsupportedImportFormat(importsFormatRaw)
        }

        guard startsOffset >= 0, importsOffset >= 0, symbolsOffset >= 0,
              base + startsOffset < end,
              base + importsOffset <= end,
              base + symbolsOffset <= end else {
            throw ChainedFixupsError.malformed("header offsets exceed payload")
        }

        let imports = try parseImports(data: data,
                                       base: base,
                                       end: end,
                                       importsOffset: importsOffset,
                                       symbolsOffset: symbolsOffset,
                                       count: importsCount,
                                       format: importsFormat)

        let result = try parseStarts(data: data,
                                     image: image,
                                     base: base,
                                     end: end,
                                     startsOffset: startsOffset)

        return ChainedFixupsInfo(version: version,
                                 importsFormat: importsFormat,
                                 imports: imports,
                                 fixups: result.fixups,
                                 unsupportedPointerFormats: result.unsupported)
    }

    private static func parseImports(data: Data,
                                     base: Int,
                                     end: Int,
                                     importsOffset: Int,
                                     symbolsOffset: Int,
                                     count: Int,
                                     format: ChainedImportFormat) throws -> [ChainedImport] {
        var imports: [ChainedImport] = []
        let stride: Int
        switch format {
        case .basic: stride = 4
        case .addend: stride = 8
        case .addend64: stride = 16
        }

        guard count == 0 || (base + importsOffset + count * stride <= end) else {
            throw ChainedFixupsError.malformed("import table exceeds payload")
        }

        for index in 0..<count {
            let offset = base + importsOffset + index * stride
            switch format {
            case .basic, .addend:
                let raw = try u32(data, offset)
                let ordinal = Int32(Int8(bitPattern: UInt8(raw & 0xFF)))
                let weak = ((raw >> 8) & 1) != 0
                let nameOffset = Int((raw >> 9) & 0x7FFFFF)
                let addend: Int64 = format == .addend ? Int64(try i32(data, offset + 4)) : 0
                let name = try symbolName(data: data,
                                          start: base + symbolsOffset + nameOffset,
                                          end: end)
                imports.append(ChainedImport(libraryOrdinal: ordinal,
                                             weak: weak,
                                             name: name,
                                             addend: addend))
            case .addend64:
                let raw = try u64(data, offset)
                let ordinalBits = UInt16(raw & 0xFFFF)
                let ordinal = Int32(Int16(bitPattern: ordinalBits))
                let weak = ((raw >> 16) & 1) != 0
                let nameOffset = Int((raw >> 32) & 0xFFFFFFFF)
                let addend = try i64(data, offset + 8)
                let name = try symbolName(data: data,
                                          start: base + symbolsOffset + nameOffset,
                                          end: end)
                imports.append(ChainedImport(libraryOrdinal: ordinal,
                                             weak: weak,
                                             name: name,
                                             addend: addend))
            }
        }
        return imports
    }

    private static func parseStarts(data: Data,
                                    image: MachOImageInfo,
                                    base: Int,
                                    end: Int,
                                    startsOffset: Int) throws -> (fixups: [ChainedFixup], unsupported: Set<UInt16>) {
        let startsBase = base + startsOffset
        let segCount = Int(try u32(data, startsBase))
        guard startsBase + 4 + segCount * 4 <= end else {
            throw ChainedFixupsError.malformed("starts-in-image table exceeds payload")
        }

        var fixups: [ChainedFixup] = []
        var unsupported = Set<UInt16>()

        for segIndex in 0..<segCount {
            let relative = Int(try u32(data, startsBase + 4 + segIndex * 4))
            if relative == 0 { continue }
            guard segIndex < image.segments.count else {
                throw ChainedFixupsError.malformed("starts table references missing segment \(segIndex)")
            }

            let segmentStarts = startsBase + relative
            guard segmentStarts + 22 <= end else {
                throw ChainedFixupsError.malformed("segment starts header exceeds payload")
            }

            let size = Int(try u32(data, segmentStarts))
            let pageSize = UInt64(try u16(data, segmentStarts + 4))
            let pointerFormatRaw = try u16(data, segmentStarts + 6)
            let pageCount = Int(try u16(data, segmentStarts + 20))
            guard size >= 22 + pageCount * 2,
                  segmentStarts + size <= end else {
                throw ChainedFixupsError.malformed("segment starts array exceeds payload")
            }

            guard let pointerFormat = ChainedPointerFormat(rawValue: pointerFormatRaw),
                  pointerFormat.isStage1Supported else {
                unsupported.insert(pointerFormatRaw)
                continue
            }

            let segment = image.segments[segIndex]
            for pageIndex in 0..<pageCount {
                let pageStart = try u16(data, segmentStarts + 22 + pageIndex * 2)
                if pageStart == pageStartNone { continue }
                if (pageStart & pageStartMulti) != 0 {
                    throw ChainedFixupsError.malformed("multi-start pages are not supported yet")
                }

                var chainOffset = UInt64(pageStart)
                var safety = 0
                while true {
                    let fileOffset = segment.fileOffset + UInt64(pageIndex) * pageSize + chainOffset
                    guard fileOffset + 8 <= UInt64(data.count) else {
                        throw ChainedFixupsError.malformed("chain pointer exceeds file")
                    }

                    let raw = try u64(data, Int(fileOffset))
                    let bind = (raw >> 63) != 0
                    let next = (raw >> 51) & 0xFFF
                    let action: FixupAction

                    if bind {
                        let ordinal = Int(raw & 0x00FF_FFFF)
                        let rawAddend = Int8(bitPattern: UInt8((raw >> 24) & 0xFF))
                        action = .bind(importIndex: ordinal, addend: Int64(rawAddend))
                    } else {
                        let target = raw & 0x0000_000F_FFFF_FFFF
                        let high8 = (raw >> 36) & 0xFF
                        let reconstructed = target | (high8 << 56)
                        action = .rebase(target: reconstructed,
                                         targetIsImageOffset: pointerFormat == .ptr64Offset)
                    }

                    fixups.append(ChainedFixup(segmentIndex: segIndex,
                                              segmentName: segment.name,
                                              fileOffset: fileOffset,
                                              guestVMAddress: segment.vmAddress + UInt64(pageIndex) * pageSize + chainOffset,
                                              pointerFormat: pointerFormat,
                                              rawValue: raw,
                                              action: action))

                    if next == 0 { break }
                    chainOffset += next * 4
                    safety += 1
                    if safety > 65_536 {
                        throw ChainedFixupsError.malformed("chain did not terminate")
                    }
                }
            }
        }

        return (fixups, unsupported)
    }

    private static func symbolName(data: Data, start: Int, end: Int) throws -> String {
        guard start >= 0, start < end, start < data.count else {
            throw ChainedFixupsError.malformed("symbol name offset exceeds payload")
        }
        let upper = min(end, data.count)
        var cursor = start
        while cursor < upper && data[cursor] != 0 { cursor += 1 }
        guard cursor < upper else {
            throw ChainedFixupsError.malformed("unterminated symbol name")
        }
        return String(bytes: data[start..<cursor], encoding: .utf8) ?? ""
    }

    private static func u16(_ data: Data, _ offset: Int) throws -> UInt16 {
        guard offset + 2 <= data.count else {
            throw ChainedFixupsError.malformed("read past EOF")
        }
        return UInt16(data[offset]) | (UInt16(data[offset + 1]) << 8)
    }

    private static func u32(_ data: Data, _ offset: Int) throws -> UInt32 {
        guard offset + 4 <= data.count else {
            throw ChainedFixupsError.malformed("read past EOF")
        }
        return data[offset..<offset+4].enumerated().reduce(UInt32(0)) { result, item in
            result | (UInt32(item.element) << UInt32(item.offset * 8))
        }
    }

    private static func i32(_ data: Data, _ offset: Int) throws -> Int32 {
        Int32(bitPattern: try u32(data, offset))
    }

    private static func u64(_ data: Data, _ offset: Int) throws -> UInt64 {
        guard offset + 8 <= data.count else {
            throw ChainedFixupsError.malformed("read past EOF")
        }
        return data[offset..<offset+8].enumerated().reduce(UInt64(0)) { result, item in
            result | (UInt64(item.element) << UInt64(item.offset * 8))
        }
    }

    private static func i64(_ data: Data, _ offset: Int) throws -> Int64 {
        Int64(bitPattern: try u64(data, offset))
    }
}
