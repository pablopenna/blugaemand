import Foundation
import Darwin

@main
struct ParserSmoke {
    static func main() throws {
        try basicMachOSmoke()
        try universalArm64SliceSmoke()
        try chainedFixupsSmoke()
        try guestBindSmoke()
        try guestRebaseSmoke()
        print("all DarwinBridge 0.3 core smoke tests passed")
    }

    static func basicMachOSmoke() throws {
        var data = Data(repeating: 0, count: 32 + 72 + 24)

        func put32(_ offset: Int, _ value: UInt32) {
            for i in 0..<4 { data[offset + i] = UInt8((value >> UInt32(i * 8)) & 0xff) }
        }
        func put64(_ offset: Int, _ value: UInt64) {
            for i in 0..<8 { data[offset + i] = UInt8((value >> UInt64(i * 8)) & 0xff) }
        }

        put32(0, 0xFEEDFACF)
        put32(4, 0x0100000C)
        put32(12, 2)
        put32(16, 2)
        put32(20, 96)

        let seg = 32
        put32(seg, 0x19)
        put32(seg + 4, 72)
        for (i, b) in Array("__TEXT".utf8).enumerated() { data[seg + 8 + i] = b }
        put64(seg + 24, 0x100000000)
        put64(seg + 32, 0x1000)
        put64(seg + 40, 0)
        put64(seg + 48, UInt64(data.count))
        put32(seg + 56, 7)
        put32(seg + 60, 5)

        let main = seg + 72
        put32(main, 0x80000028)
        put32(main + 4, 24)
        put64(main + 8, 0x40)

        let image = try MachOParser.parse(data)
        precondition(image.isArm64)
        precondition(image.segments.count == 1)
        precondition(image.entryOffset == 0x40)
        precondition(image.chainedFixups == nil)
    }

    static func universalArm64SliceSmoke() throws {
        let thinSize = 32 + 72 + 24
        var thin = Data(repeating: 0, count: thinSize)

        func thin32(_ offset: Int, _ value: UInt32) {
            for i in 0..<4 { thin[offset + i] = UInt8((value >> UInt32(i * 8)) & 0xff) }
        }
        func thin64(_ offset: Int, _ value: UInt64) {
            for i in 0..<8 { thin[offset + i] = UInt8((value >> UInt64(i * 8)) & 0xff) }
        }

        thin32(0, 0xFEEDFACF)
        thin32(4, 0x0100000C)
        thin32(12, 2)
        thin32(16, 2)
        thin32(20, 96)
        let seg = 32
        thin32(seg, 0x19)
        thin32(seg + 4, 72)
        for (i, b) in Array("__TEXT".utf8).enumerated() { thin[seg + 8 + i] = b }
        thin64(seg + 24, 0x100000000)
        thin64(seg + 32, 0x1000)
        thin64(seg + 40, 0)
        thin64(seg + 48, UInt64(thin.count))
        thin32(seg + 56, 7)
        thin32(seg + 60, 5)
        let main = seg + 72
        thin32(main, 0x80000028)
        thin32(main + 4, 24)
        thin64(main + 8, 0x40)

        let sliceOffset = 0x100
        var fat = Data(repeating: 0, count: sliceOffset + thin.count)
        func be32(_ offset: Int, _ value: UInt32) {
            fat[offset] = UInt8((value >> 24) & 0xff)
            fat[offset + 1] = UInt8((value >> 16) & 0xff)
            fat[offset + 2] = UInt8((value >> 8) & 0xff)
            fat[offset + 3] = UInt8(value & 0xff)
        }
        be32(0, 0xCAFEBABE)
        be32(4, 1)
        be32(8, 0x0100000C)
        be32(12, 0)
        be32(16, UInt32(sliceOffset))
        be32(20, UInt32(thin.count))
        be32(24, 2)
        fat.replaceSubrange(sliceOffset..<(sliceOffset + thin.count), with: thin)

        let selected = try MachOParser.preferredArm64Slice(fat)
        precondition(selected == thin)
        let image = try MachOParser.parse(fat)
        precondition(image.isArm64)
        precondition(image.entryOffset == 0x40)
    }

    static func chainedFixupsSmoke() throws {
        let sample = makeChainedSample(pointerFormat: 2,
                                       pointerRawValue: UInt64(1) << 63,
                                       symbol: "_malloc")
        let image = try MachOParser.parse(sample)
        let info = try ChainedFixupsParser.parse(data: sample, image: image)

        precondition(info.imports.count == 1)
        precondition(info.imports[0].name == "_malloc")
        precondition(info.fixups.count == 1)
        precondition(info.unsupportedPointerFormats.isEmpty)
    }

    static func guestBindSmoke() throws {
        let sample = makeChainedSample(pointerFormat: 2,
                                       pointerRawValue: UInt64(1) << 63,
                                       symbol: "_malloc")
        let image = try MachOParser.parse(sample)
        let plan = try FixupPlanner.make(data: sample, image: image)
        precondition(plan.canApplyStage1)

        let space = try GuestAddressSpace(data: sample, image: image)
        let applied = try FixupApplier.apply(plan: plan, to: space)
        precondition(applied.binds == 1)
        precondition(applied.rebases == 0)

        guard let slot = space.hostPointer(for: 0x100000000, byteCount: 8) else {
            preconditionFailure("missing bind slot")
        }
        let written = slot.load(as: UInt64.self)
        precondition(written == plan.resolutions[0].address!)
    }

    static func guestRebaseSmoke() throws {
        let targetOffset: UInt64 = 0x20
        let sample = makeChainedSample(pointerFormat: 6,
                                       pointerRawValue: targetOffset,
                                       symbol: "_malloc")
        let image = try MachOParser.parse(sample)
        let plan = try FixupPlanner.make(data: sample, image: image)

        let space = try GuestAddressSpace(data: sample, image: image)
        let applied = try FixupApplier.apply(plan: plan, to: space)
        precondition(applied.rebases == 1)
        precondition(applied.binds == 0)

        guard let slot = space.hostPointer(for: 0x100000000, byteCount: 8),
              let expectedPtr = space.hostPointer(for: 0x100000020) else {
            preconditionFailure("missing rebase address")
        }

        let written = slot.load(as: UInt64.self)
        let expected = UInt64(UInt(bitPattern: expectedPtr))
        precondition(written == expected)
    }

    static func makeChainedSample(pointerFormat: UInt16,
                                  pointerRawValue: UInt64,
                                  symbol: String) -> Data {
        let segmentFileOffset = 192
        let segmentFileSize = 64
        let payloadOffset = 320
        let payloadSize = 96
        var data = Data(repeating: 0, count: payloadOffset + payloadSize)

        func put16(_ offset: Int, _ value: UInt16) {
            data[offset] = UInt8(value & 0xff)
            data[offset + 1] = UInt8((value >> 8) & 0xff)
        }
        func put32(_ offset: Int, _ value: UInt32) {
            for i in 0..<4 { data[offset + i] = UInt8((value >> UInt32(i * 8)) & 0xff) }
        }
        func put64(_ offset: Int, _ value: UInt64) {
            for i in 0..<8 { data[offset + i] = UInt8((value >> UInt64(i * 8)) & 0xff) }
        }

        put32(0, 0xFEEDFACF)
        put32(4, 0x0100000C)
        put32(12, 2)
        put32(16, 2)
        put32(20, 88)

        let seg = 32
        put32(seg, 0x19)
        put32(seg + 4, 72)
        for (i, b) in Array("__DATA".utf8).enumerated() { data[seg + 8 + i] = b }
        put64(seg + 24, 0x100000000)
        put64(seg + 32, 0x1000)
        put64(seg + 40, UInt64(segmentFileOffset))
        put64(seg + 48, UInt64(segmentFileSize))
        put32(seg + 56, 3)
        put32(seg + 60, 3)

        let chained = seg + 72
        put32(chained, 0x80000034)
        put32(chained + 4, 16)
        put32(chained + 8, UInt32(payloadOffset))
        put32(chained + 12, UInt32(payloadSize))

        put64(segmentFileOffset, pointerRawValue)

        let p = payloadOffset
        put32(p, 0)
        put32(p + 4, 28)
        put32(p + 8, 60)
        put32(p + 12, 64)
        put32(p + 16, 1)
        put32(p + 20, 1)
        put32(p + 24, 0)

        let starts = p + 28
        put32(starts, 1)
        put32(starts + 4, 8)

        let segmentStarts = starts + 8
        put32(segmentStarts, 24)
        put16(segmentStarts + 4, 4096)
        put16(segmentStarts + 6, pointerFormat)
        put64(segmentStarts + 8, 0)
        put32(segmentStarts + 16, 0)
        put16(segmentStarts + 20, 1)
        put16(segmentStarts + 22, 0)

        put32(p + 60, 0)
        let bytes = Array((symbol + "\0").utf8)
        for (i, byte) in bytes.enumerated() {
            data[p + 64 + i] = byte
        }

        return data
    }
}
