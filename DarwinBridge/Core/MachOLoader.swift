import Foundation
import Darwin

enum MachOLoaderError: Error, LocalizedError {
    case invalidImage
    case mappingFailed(String)
    case entryOutsideSegments

    var errorDescription: String? {
        switch self {
        case .invalidImage: return "Image cannot be mapped."
        case .mappingFailed(let name): return "Could not allocate memory for segment \(name)."
        case .entryOutsideSegments: return "LC_MAIN entry point does not resolve inside a mapped segment."
        }
    }
}

final class MappedMachOImage {
    struct Region {
        let segment: MachOSegment
        let base: UnsafeMutableRawPointer
        let size: Int
    }

    let regions: [Region]
    let entryAddress: UnsafeMutableRawPointer?

    init(regions: [Region], entryAddress: UnsafeMutableRawPointer?) {
        self.regions = regions
        self.entryAddress = entryAddress
    }

    deinit {
        for region in regions {
            munmap(region.base, region.size)
        }
    }
}

struct MachOLoader {
    static func mapForInspection(data: Data, image: MachOImageInfo) throws -> MappedMachOImage {
        guard image.isArm64, !image.segments.isEmpty else { throw MachOLoaderError.invalidImage }

        let pageSize = Int(getpagesize())
        var regions: [MappedMachOImage.Region] = []

        do {
            for segment in image.segments where segment.vmSize > 0 && segment.name != "__PAGEZERO" {
                guard segment.vmSize <= UInt64(Int.max), segment.fileSize <= UInt64(Int.max) else {
                    throw MachOLoaderError.mappingFailed(segment.name)
                }

                let requested = Int(segment.vmSize)
                let allocationSize = ((requested + pageSize - 1) / pageSize) * pageSize
                guard let ptr = mmap(nil, allocationSize, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANON, -1, 0),
                      ptr != MAP_FAILED else {
                    throw MachOLoaderError.mappingFailed(segment.name)
                }

                if segment.fileSize > 0 {
                    let start = Int(segment.fileOffset)
                    let count = Int(segment.fileSize)
                    data.withUnsafeBytes { raw in
                        if let source = raw.baseAddress?.advanced(by: start) {
                            memcpy(ptr, source, count)
                        }
                    }
                }

                regions.append(.init(segment: segment, base: ptr, size: allocationSize))
            }
        } catch {
            for region in regions {
                munmap(region.base, region.size)
            }
            throw error
        }

        var entry: UnsafeMutableRawPointer?
        if let entryOffset = image.entryOffset {
            guard let region = regions.first(where: {
                entryOffset >= $0.segment.fileOffset &&
                entryOffset < $0.segment.fileOffset + max($0.segment.fileSize, 1)
            }) else {
                for region in regions {
                    munmap(region.base, region.size)
                }
                throw MachOLoaderError.entryOutsideSegments
            }
            entry = region.base.advanced(by: Int(entryOffset - region.segment.fileOffset))
        }

        return MappedMachOImage(regions: regions, entryAddress: entry)
    }
}
