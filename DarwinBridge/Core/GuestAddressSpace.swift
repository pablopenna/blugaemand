import Foundation
import Darwin

enum GuestAddressSpaceError: Error, LocalizedError {
    case invalidLayout
    case allocationFailed
    case segmentOutOfRange(String)
    case fileRangeOutOfRange(String)
    case fixupOutOfRange
    case unresolvedImport(Int)

    var errorDescription: String? {
        switch self {
        case .invalidLayout: return "Guest address-space layout is invalid."
        case .allocationFailed: return "Could not allocate guest address space."
        case .segmentOutOfRange(let name): return "Segment \(name) is outside the guest address space."
        case .fileRangeOutOfRange(let name): return "Segment \(name) references bytes outside the Mach-O."
        case .fixupOutOfRange: return "A fixup location is outside guest memory."
        case .unresolvedImport(let index): return "Import \(index) is unresolved."
        }
    }
}

final class GuestAddressSpace {
    let base: UnsafeMutableRawPointer
    let size: Int
    let guestBase: UInt64
    let guestEnd: UInt64

    init(data: Data, image: MachOImageInfo) throws {
        guard let preferred = image.preferredImageBase else {
            throw GuestAddressSpaceError.invalidLayout
        }

        let segments = image.segments.filter { $0.name != "__PAGEZERO" && $0.vmSize > 0 }
        guard !segments.isEmpty else { throw GuestAddressSpaceError.invalidLayout }

        var end = preferred
        for segment in segments {
            end = max(end, segment.vmAddress + segment.vmSize)
        }

        let page = UInt64(getpagesize())
        let span = end - preferred
        let rounded = ((span + page - 1) / page) * page
        guard rounded > 0, rounded <= UInt64(Int.max) else {
            throw GuestAddressSpaceError.invalidLayout
        }

        guard let ptr = mmap(nil,
                             Int(rounded),
                             PROT_READ | PROT_WRITE,
                             MAP_PRIVATE | MAP_ANON,
                             -1,
                             0),
              ptr != MAP_FAILED else {
            throw GuestAddressSpaceError.allocationFailed
        }

        base = ptr
        size = Int(rounded)
        guestBase = preferred
        guestEnd = end

        do {
            for segment in segments {
                try copy(segment: segment, from: data)
            }
        } catch {
            munmap(base, size)
            throw error
        }
    }

    deinit {
        munmap(base, size)
    }

    func hostPointer(for guestAddress: UInt64, byteCount: Int = 1) -> UnsafeMutableRawPointer? {
        guard guestAddress >= guestBase else { return nil }
        let offset = guestAddress - guestBase
        guard offset <= UInt64(size),
              byteCount >= 0,
              UInt64(byteCount) <= UInt64(size) - offset else {
            return nil
        }
        return base.advanced(by: Int(offset))
    }

    private func copy(segment: MachOSegment, from data: Data) throws {
        guard segment.vmAddress >= guestBase else {
            throw GuestAddressSpaceError.segmentOutOfRange(segment.name)
        }
        let hostOffset = segment.vmAddress - guestBase
        guard hostOffset <= UInt64(size),
              segment.vmSize <= UInt64(size) - hostOffset else {
            throw GuestAddressSpaceError.segmentOutOfRange(segment.name)
        }

        if segment.fileSize == 0 { return }
        guard segment.fileOffset + segment.fileSize <= UInt64(data.count),
              segment.fileSize <= segment.vmSize else {
            throw GuestAddressSpaceError.fileRangeOutOfRange(segment.name)
        }

        data.withUnsafeBytes { raw in
            if let source = raw.baseAddress?.advanced(by: Int(segment.fileOffset)) {
                memcpy(base.advanced(by: Int(hostOffset)), source, Int(segment.fileSize))
            }
        }
    }
}

struct AppliedFixups {
    let rebases: Int
    let binds: Int
}

struct FixupApplier {
    static func apply(plan: FixupPlan, to space: GuestAddressSpace) throws -> AppliedFixups {
        var rebases = 0
        var binds = 0

        for fixup in plan.info.fixups {
            guard let slot = space.hostPointer(for: fixup.guestVMAddress, byteCount: 8) else {
                throw GuestAddressSpaceError.fixupOutOfRange
            }

            let value: UInt64
            switch fixup.action {
            case .rebase(let target, let isImageOffset):
                let guestTarget = isImageOffset ? space.guestBase + target : target
                guard let pointer = space.hostPointer(for: guestTarget) else {
                    throw GuestAddressSpaceError.fixupOutOfRange
                }
                value = UInt64(UInt(bitPattern: pointer))
                rebases += 1

            case .bind(let importIndex, let chainAddend):
                guard plan.resolutions.indices.contains(importIndex),
                      plan.info.imports.indices.contains(importIndex),
                      let symbol = plan.resolutions[importIndex].address else {
                    throw GuestAddressSpaceError.unresolvedImport(importIndex)
                }

                let combined = Int64(bitPattern: symbol)
                    &+ plan.info.imports[importIndex].addend
                    &+ chainAddend
                value = UInt64(bitPattern: combined)
                binds += 1
            }

            slot.storeBytes(of: value.littleEndian, as: UInt64.self)
        }

        return AppliedFixups(rebases: rebases, binds: binds)
    }
}
