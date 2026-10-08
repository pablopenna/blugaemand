import Foundation

enum MachOParserError: Error, LocalizedError {
    case tooSmall
    case unsupportedMagic(UInt32)
    case malformed(String)

    var errorDescription: String? {
        switch self {
        case .tooSmall: return "File is too small to be a 64-bit Mach-O image."
        case .unsupportedMagic(let value): return String(format: "Unsupported Mach-O magic 0x%08X", value)
        case .malformed(let reason): return "Malformed Mach-O: \(reason)"
        }
    }
}

struct MachOParser {
    static let mhMagic64: UInt32 = 0xFEEDFACF
    static let fatMagicBE: UInt32 = 0xCAFEBABE
    static let fatMagic64BE: UInt32 = 0xCAFEBABF
    static let cpuTypeArm64: UInt32 = 0x0100000C
    static let lcSegment64: UInt32 = 0x19
    static let lcLoadDylib: UInt32 = 0x0C
    static let lcLoadWeakDylib: UInt32 = 0x80000018
    static let lcReexportDylib: UInt32 = 0x8000001F
    static let lcLoadUpwardDylib: UInt32 = 0x80000023
    static let lcRpath: UInt32 = 0x8000001C
    static let lcMain: UInt32 = 0x80000028
    static let lcBuildVersion: UInt32 = 0x32
    static let lcEncryptionInfo64: UInt32 = 0x2C
    static let lcDyldChainedFixups: UInt32 = 0x80000034

    static func preferredArm64Slice(_ data: Data) throws -> Data {
        guard data.count >= 4 else { throw MachOParserError.tooSmall }

        let firstBE = try be32(data, 0)
        guard firstBE == fatMagicBE || firstBE == fatMagic64BE else {
            return data
        }

        guard data.count >= 8 else {
            throw MachOParserError.malformed("truncated universal Mach-O header")
        }

        let count = Int(try be32(data, 4))
        let is64 = firstBE == fatMagic64BE
        let recordSize = is64 ? 32 : 20
        let tableEnd = 8 + count * recordSize
        guard count > 0, tableEnd <= data.count else {
            throw MachOParserError.malformed("invalid universal Mach-O architecture table")
        }

        for index in 0..<count {
            let base = 8 + index * recordSize
            let cpu = try be32(data, base)
            guard cpu == cpuTypeArm64 else { continue }

            let sliceOffset: UInt64
            let sliceSize: UInt64
            if is64 {
                sliceOffset = try be64(data, base + 8)
                sliceSize = try be64(data, base + 16)
            } else {
                sliceOffset = UInt64(try be32(data, base + 8))
                sliceSize = UInt64(try be32(data, base + 12))
            }

            let end = sliceOffset.addingReportingOverflow(sliceSize)
            guard !end.overflow,
                  end.partialValue <= UInt64(data.count),
                  sliceOffset <= UInt64(Int.max),
                  sliceSize <= UInt64(Int.max) else {
                throw MachOParserError.malformed("ARM64 universal slice exceeds file bounds")
            }

            return Data(data[Int(sliceOffset)..<Int(end.partialValue)])
        }

        throw MachOParserError.malformed("universal Mach-O does not contain an ARM64 slice")
    }

    static func parse(_ data: Data) throws -> MachOImageInfo {
        let data = try preferredArm64Slice(data)
        guard data.count >= 32 else { throw MachOParserError.tooSmall }
        let magic = try u32(data, 0)
        guard magic == mhMagic64 else { throw MachOParserError.unsupportedMagic(magic) }

        let cpuType = try i32(data, 4)
        let cpuSubtype = try i32(data, 8)
        let fileType = try u32(data, 12)
        let ncmds = try u32(data, 16)
        let sizeofcmds = try u32(data, 20)
        let flags = try u32(data, 24)

        guard 32 + Int(sizeofcmds) <= data.count else {
            throw MachOParserError.malformed("load command table exceeds file size")
        }

        var segments: [MachOSegment] = []
        var dependencies: [MachODependency] = []
        var rpaths: [String] = []
        var entryOffset: UInt64?
        var platform: UInt32?
        var minimumOS: String?
        var sdk: String?
        var encrypted = false
        var chainedFixups: LinkeditDataLocation?

        var offset = 32
        for _ in 0..<ncmds {
            guard offset + 8 <= data.count else { throw MachOParserError.malformed("truncated load command") }
            let cmd = try u32(data, offset)
            let cmdsize = Int(try u32(data, offset + 4))
            guard cmdsize >= 8, offset + cmdsize <= data.count else {
                throw MachOParserError.malformed("invalid load command size")
            }

            switch cmd {
            case lcSegment64:
                guard cmdsize >= 72 else { throw MachOParserError.malformed("short LC_SEGMENT_64") }
                segments.append(MachOSegment(
                    name: fixedCString(data, offset + 8, 16),
                    vmAddress: try u64(data, offset + 24),
                    vmSize: try u64(data, offset + 32),
                    fileOffset: try u64(data, offset + 40),
                    fileSize: try u64(data, offset + 48),
                    maxProtection: try i32(data, offset + 56),
                    initialProtection: try i32(data, offset + 60)
                ))
            case lcLoadDylib, lcLoadWeakDylib, lcReexportDylib, lcLoadUpwardDylib:
                guard cmdsize >= 24 else { throw MachOParserError.malformed("short dylib command") }
                let nameOffset = Int(try u32(data, offset + 8))
                guard nameOffset >= 8, nameOffset < cmdsize else {
                    throw MachOParserError.malformed("invalid dylib name offset")
                }
                let path = cString(data, offset + nameOffset, offset + cmdsize)
                dependencies.append(MachODependency(path: path,
                                                     weak: cmd == lcLoadWeakDylib,
                                                     reexported: cmd == lcReexportDylib))
            case lcRpath:
                guard cmdsize >= 12 else { throw MachOParserError.malformed("short LC_RPATH") }
                let pathOffset = Int(try u32(data, offset + 8))
                if pathOffset >= 8 && pathOffset < cmdsize {
                    rpaths.append(cString(data, offset + pathOffset, offset + cmdsize))
                }
            case lcMain:
                guard cmdsize >= 24 else { throw MachOParserError.malformed("short LC_MAIN") }
                entryOffset = try u64(data, offset + 8)
            case lcBuildVersion:
                guard cmdsize >= 24 else { throw MachOParserError.malformed("short LC_BUILD_VERSION") }
                platform = try u32(data, offset + 8)
                minimumOS = versionString(try u32(data, offset + 12))
                sdk = versionString(try u32(data, offset + 16))
            case lcEncryptionInfo64:
                guard cmdsize >= 24 else { throw MachOParserError.malformed("short LC_ENCRYPTION_INFO_64") }
                encrypted = (try u32(data, offset + 16)) != 0
            case lcDyldChainedFixups:
                guard cmdsize >= 16 else { throw MachOParserError.malformed("short LC_DYLD_CHAINED_FIXUPS") }
                let dataOffset = try u32(data, offset + 8)
                let dataSize = try u32(data, offset + 12)
                guard UInt64(dataOffset) + UInt64(dataSize) <= UInt64(data.count) else {
                    throw MachOParserError.malformed("LC_DYLD_CHAINED_FIXUPS points outside file")
                }
                chainedFixups = LinkeditDataLocation(fileOffset: dataOffset, size: dataSize)
            default:
                break
            }
            offset += cmdsize
        }

        for segment in segments where segment.fileSize > 0 {
            let end = segment.fileOffset.addingReportingOverflow(segment.fileSize)
            guard !end.overflow, end.partialValue <= UInt64(data.count) else {
                throw MachOParserError.malformed("segment \(segment.name) exceeds file bounds")
            }
        }

        return MachOImageInfo(cpuType: cpuType,
                              cpuSubtype: cpuSubtype,
                              fileType: fileType,
                              commands: ncmds,
                              flags: flags,
                              entryOffset: entryOffset,
                              platform: platform,
                              minimumOS: minimumOS,
                              sdk: sdk,
                              encrypted: encrypted,
                              segments: segments,
                              dependencies: dependencies,
                              rpaths: rpaths,
                              chainedFixups: chainedFixups)
    }

    private static func versionString(_ value: UInt32) -> String {
        "\((value >> 16) & 0xFFFF).\((value >> 8) & 0xFF).\(value & 0xFF)"
    }

    private static func fixedCString(_ data: Data, _ offset: Int, _ length: Int) -> String {
        let bytes = data[offset..<min(offset + length, data.count)]
        let prefix = bytes.prefix { $0 != 0 }
        return String(bytes: prefix, encoding: .utf8) ?? ""
    }

    private static func cString(_ data: Data, _ start: Int, _ end: Int) -> String {
        guard start < end, start < data.count else { return "" }
        let boundedEnd = min(end, data.count)
        let bytes = data[start..<boundedEnd]
        let prefix = bytes.prefix { $0 != 0 }
        return String(bytes: prefix, encoding: .utf8) ?? ""
    }

    private static func be32(_ data: Data, _ offset: Int) throws -> UInt32 {
        guard offset + 4 <= data.count else { throw MachOParserError.malformed("read past EOF") }
        return (UInt32(data[offset]) << 24) |
               (UInt32(data[offset + 1]) << 16) |
               (UInt32(data[offset + 2]) << 8) |
               UInt32(data[offset + 3])
    }

    private static func be64(_ data: Data, _ offset: Int) throws -> UInt64 {
        guard offset + 8 <= data.count else { throw MachOParserError.malformed("read past EOF") }
        var value: UInt64 = 0
        for i in 0..<8 {
            value = (value << 8) | UInt64(data[offset + i])
        }
        return value
    }

    private static func u32(_ data: Data, _ offset: Int) throws -> UInt32 {
        guard offset + 4 <= data.count else { throw MachOParserError.malformed("read past EOF") }
        return data[offset..<offset+4].enumerated().reduce(UInt32(0)) { result, item in
            result | (UInt32(item.element) << UInt32(item.offset * 8))
        }
    }

    private static func i32(_ data: Data, _ offset: Int) throws -> Int32 {
        Int32(bitPattern: try u32(data, offset))
    }

    private static func u64(_ data: Data, _ offset: Int) throws -> UInt64 {
        guard offset + 8 <= data.count else { throw MachOParserError.malformed("read past EOF") }
        return data[offset..<offset+8].enumerated().reduce(UInt64(0)) { result, item in
            result | (UInt64(item.element) << UInt64(item.offset * 8))
        }
    }
}
